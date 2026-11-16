import assert from 'node:assert/strict';
import { test } from 'node:test';
import { aggregate, percentile } from '../src/peers.mjs';
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
    // 11명: 10명은 3일 이상 기록, 1명은 2일뿐이라 빠진다. 연령대가 없는 사람도 빠진다.
    for (let i = 0; i < 12; i++) {
        const user = `u${i}`;
        const profile = i === 11 ? {} : { ageBand: 'AGE_18_24', occupation: 'UNIVERSITY' };
        assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: 1, ...profile })).status, 204);
        const days = i === 10 ? 2 : 3;
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

    // 한 명이 철회해서 10명 미만이 되면 다음 계산부터 그룹이 사라진다.
    assert.equal((await call('DELETE', '/api/v1/sharing', 'u3')).status, 204);
    await worker.scheduled({ scheduledTime: now + 1000 }, env);
    assert.deepEqual((await call('GET', '/api/v1/peers', 'u0')).body.groups, []);
});
