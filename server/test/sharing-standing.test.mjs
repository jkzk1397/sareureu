import assert from 'node:assert/strict';
import { test } from 'node:test';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');

test('임시: 동의할 때 닉네임·연령대·신분만 받고 실명·연락처 같은 다른 칸은 거절한다', async (t) => {
    const { call, sql } = await testServer(t, now);
    const ok = { consentVersion: 1, nickname: ' 민지 ', ageBand: 'AGE_18_24', occupation: 'UNIVERSITY' };
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', { ...ok, phone: '010' })).status, 400);
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', { ...ok, nickname: '가'.repeat(13) })).status, 400);
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', { ...ok, ageBand: 'drop table' })).status, 400);
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', { ...ok, consentVersion: 2 })).status, 400);
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', ok)).status, 204);
    const first = sql.prepare('SELECT * FROM sharing_consent').get();
    assert.equal(first.nickname, '민지');
    assert.equal(first.age_band, 'AGE_18_24');
    assert.ok(first.public_id);

    // 다시 동의해도 공개 ID와 동의 시각은 그대로, 닉네임은 바뀐다.
    Date.now.mock.mockImplementation(() => now + 1000);
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', { ...ok, nickname: '민지2' })).status, 204);
    const again = sql.prepare('SELECT * FROM sharing_consent').get();
    assert.equal(again.public_id, first.public_id);
    assert.equal(again.granted_at_ms, first.granted_at_ms);
    assert.equal(again.nickname, '민지2');

    // 예전 앱처럼 동의 버전만 보내도 된다.
    assert.equal((await call('PUT', '/api/v1/sharing', 'bob', { consentVersion: 1 })).status, 204);
});

test('임시: 랭킹 숫자는 동의한 뒤에만 저장하고, 철회하면 함께 지운다', async (t) => {
    const { call, sql } = await testServer(t, now);
    const standing = { reductionRate: 0.23, coverage: 0.9, validDays: 6, measuredAtMs: now };
    assert.equal((await call('PUT', '/api/v1/standing', undefined, standing)).status, 401);
    assert.equal((await call('PUT', '/api/v1/standing', 'alice', standing)).status, 403);
    assert.equal((await call('PUT', '/api/v1/sharing', 'alice', { consentVersion: 1, nickname: '민지' })).status, 204);
    for (const bad of [
        { ...standing, reductionRate: 1.5 },
        { ...standing, validDays: 8 },
        { ...standing, minutes: 30 },
        { ...standing, measuredAtMs: now + 1 },
        { ...standing, measuredAtMs: now - 2 * 86_400_000 },
    ]) {
        assert.equal((await call('PUT', '/api/v1/standing', 'alice', bad)).status, 400);
    }
    assert.equal((await call('PUT', '/api/v1/standing', 'alice', standing)).status, 204);
    assert.equal((await call('PUT', '/api/v1/standing', 'alice', { ...standing, reductionRate: null, coverage: null,
        validDays: 0, measuredAtMs: now })).status, 204);
    assert.equal(sql.prepare('SELECT reduction_rate FROM standing').get().reduction_rate, 0.23);

    assert.equal((await call('DELETE', '/api/v1/sharing', 'alice')).status, 204);
    assert.deepEqual(sql.prepare('SELECT * FROM standing').all(), []);
    assert.equal((await call('PUT', '/api/v1/standing', 'alice', standing)).status, 403);
});
