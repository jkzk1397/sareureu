import assert from 'node:assert/strict';
import { test } from 'node:test';
import { limits } from '../src/limits.mjs';
import { aggregate, percentile, tooBigAJump } from '../src/peers.mjs';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');

test('임시: 그룹 집계는 10명 이상만, 중앙값·상위 25%만 남긴다', () => {
    assert.equal(percentile([1, 2, 3, 4], 0.5), 2);
    assert.equal(percentile([1, 2, 3, 4], 0.75), 3);
    const rows = (n, band, base) => Array.from({ length: n }, (_, i) => ({ ageBand: band, occupation: 'UNIVERSITY', avg: base + i }));
    const stats = aggregate([...rows(10, 'AGE_18_24', 100), ...rows(9, 'AGE_25_29', 50)]);
    assert.deepEqual(stats, [{ ageBand: 'AGE_18_24', occupation: 'UNIVERSITY', n: 10, p50: 104, p75: 107 }]);
});

test('임시: cron이 동의한 사용자의 하루 합계로 계산하고, 개인별 값은 내려 주지 않는다', async (t) => {
    const { call, sql, worker, env } = await testServer(t, now);
    const date = (back) => new Date(now - back * 86_400_000).toISOString().slice(0, 10);
    // 12명: 10명은 유효한 날이 7일, 1명은 6일뿐이라 빠진다. 연령대가 없는 사람도 빠진다.
    for (let i = 0; i < 12; i++) {
        const user = `u${i}`;
        const profile = i === 11 ? {} : { ageBand: 'AGE_18_24', occupation: 'UNIVERSITY' };
        assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: 1, ...profile })).status, 204);
        const days = i === 10 ? 6 : 7;
        for (let d = 1; d <= days; d++) {
            sql.prepare(`INSERT INTO daily_watch (user_id, watch_date, shortform_minutes, coverage, measured_at_ms, saved_at_ms)
                VALUES (?, ?, ?, NULL, ?, ?)`).run(user, date(d), 60 + i * 10, now, now);
        }
    }
    assert.equal((await call('GET', '/api/v1/peers', 'stranger')).status, 403);
    assert.deepEqual((await call('GET', '/api/v1/peers', 'u0')).body.groups, []);

    await worker.scheduled({ scheduledTime: now }, env);
    const peers = await call('GET', '/api/v1/peers', 'u0');
    assert.equal(peers.status, 200);
    assert.deepEqual(peers.body.groups, [{ ageBand: 'AGE_18_24', occupation: 'UNIVERSITY', n: 10, p50: 100, p75: 130 }]);
    assert.equal(peers.body.computedAtMs, now);
    assert.ok(!JSON.stringify(peers.body).includes('u0'));

    // 기록된 비율이 낮은 날(감지를 꺼 둔 날)은 유효한 날로 세지 않는다: u0의 하루가 빠져 6일이 되면 9명이라 그룹이 사라진다.
    sql.prepare("UPDATE daily_watch SET coverage = 0.5 WHERE user_id = 'u0' AND watch_date = ?").run(date(1));
    await worker.scheduled({ scheduledTime: now + 500 }, env);
    assert.deepEqual((await call('GET', '/api/v1/peers', 'u0')).body.groups, []);
    sql.prepare("UPDATE daily_watch SET coverage = 0.9 WHERE user_id = 'u0' AND watch_date = ?").run(date(1));
    await worker.scheduled({ scheduledTime: now + 600 }, env);
    assert.equal((await call('GET', '/api/v1/peers', 'u0')).body.groups.length, 1);

    // 한 명이 철회해서 10명 미만이 되면 다음 계산부터 그룹이 사라진다.
    assert.equal((await call('DELETE', '/api/v1/sharing', 'u3')).status, 204);
    await worker.scheduled({ scheduledTime: now + 1000 }, env);
    assert.deepEqual((await call('GET', '/api/v1/peers', 'u0')).body.groups, []);
});

test('5-C: 그룹 값이 하루 사이 크게 바뀌면 그날은 갱신하지 않고, 3일 넘게 이어지면 받아들인다', async (t) => {
    const previous = { p50: 100, computedAtMs: now };
    assert.equal(tooBigAJump(null, { p50: 100 }, now), false);
    assert.equal(tooBigAJump(previous, { p50: 130 }, now + 86_400_000), false);
    assert.equal(tooBigAJump(previous, { p50: 131 }, now + 86_400_000), true);
    assert.equal(tooBigAJump(previous, { p50: 60 }, now + 86_400_000), true);
    // 작은 값은 15분까지는 흔들려도 된다.
    assert.equal(tooBigAJump({ p50: 20, computedAtMs: now }, { p50: 35 }, now + 1), false);
    assert.equal(tooBigAJump({ p50: 20, computedAtMs: now }, { p50: 36 }, now + 1), true);
    assert.equal(tooBigAJump(previous, { p50: 300 }, now + limits.peerHoldMs + 1), false);

    const { call, sql, worker, env } = await testServer(t, now);
    const insert = sql.prepare(`INSERT OR REPLACE INTO daily_watch
        (user_id, watch_date, shortform_minutes, coverage, measured_at_ms, saved_at_ms) VALUES (?, ?, ?, NULL, ?, ?)`);
    const date = (at, back) => new Date(at - back * 86_400_000).toISOString().slice(0, 10);
    for (let i = 0; i < 10; i++) {
        const body = { consentVersion: 1, ageBand: 'AGE_18_24', occupation: 'UNIVERSITY' };
        assert.equal((await call('PUT', '/api/v1/sharing', `u${i}`, body)).status, 204);
        for (let d = 1; d <= 7; d++) insert.run(`u${i}`, date(now, d), 100, now, now);
    }
    await worker.scheduled({ scheduledTime: now }, env);
    const group = async () => (await call('GET', '/api/v1/peers', 'u0')).body;
    assert.equal((await group()).groups[0].p50, 100);

    // 다음 날 모두가 갑자기 300분을 올렸다: 그날은 예전 값을 둔다.
    const day2 = now + 86_400_000;
    sql.exec('UPDATE daily_watch SET shortform_minutes = 300');
    await worker.scheduled({ scheduledTime: day2 }, env);
    assert.equal((await group()).groups[0].p50, 100);
    assert.equal((await group()).computedAtMs, now);
    // 3일 넘게 이어지면 실제 변화로 보고 받아들인다.
    const day5 = now + limits.peerHoldMs + 86_400_000;
    for (let i = 0; i < 10; i++) for (let d = 1; d <= 7; d++) insert.run(`u${i}`, date(day5, d), 300, day5, day5);
    await worker.scheduled({ scheduledTime: day5 }, env);
    assert.equal((await group()).groups[0].p50, 300);
    assert.equal((await group()).computedAtMs, day5);
});
