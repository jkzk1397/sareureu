import assert from 'node:assert/strict';
import { test } from 'node:test';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');

async function join(call, user, nickname, standing) {
    assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: 1, nickname })).status, 204);
    if (standing) assert.equal((await call('PUT', '/api/v1/standing', user, { ...standing, measuredAtMs: now })).status, 204);
}

test('임시: 초대 코드로 서로 연결하고 친구 숫자는 닉네임·감소율·기록된 비율·유효한 날만 보인다', async (t) => {
    const { call, sql } = await testServer(t, now);
    assert.equal((await call('POST', '/api/v1/invites', 'alice')).status, 403);
    await join(call, 'alice', '민지', { reductionRate: 0.3, coverage: 0.9, validDays: 7 });
    await join(call, 'bob', '준호', { reductionRate: 0.1, coverage: 0.85, validDays: 6 });

    const first = await call('POST', '/api/v1/invites', 'alice');
    assert.equal(first.status, 201);
    assert.match(first.body.code, /^[A-Z2-9]{6}$/);
    assert.equal(first.body.expiresAtMs, now + 86_400_000);
    // 새로 만들면 이전 코드는 못 쓴다.
    const invite = await call('POST', '/api/v1/invites', 'alice');
    assert.notEqual(invite.body.code, first.body.code);
    assert.equal((await call('POST', `/api/v1/invites/${first.body.code}/accept`, 'bob')).status, 404);

    assert.equal((await call('POST', `/api/v1/invites/${invite.body.code}/accept`, 'alice')).status, 409);
    assert.equal((await call('POST', `/api/v1/invites/${invite.body.code}/accept`, 'carol')).status, 403);
    const accepted = await call('POST', `/api/v1/invites/${invite.body.code.toLowerCase()}/accept`, 'bob');
    assert.equal(accepted.status, 201);
    assert.equal(accepted.body.friend.nickname, '민지');
    // 한 번 쓴 코드는 없어진다.
    assert.equal((await call('POST', `/api/v1/invites/${invite.body.code}/accept`, 'bob')).status, 404);

    const list = await call('GET', '/api/v1/friends', 'bob');
    assert.equal(list.status, 200);
    assert.deepEqual(Object.keys(list.body.friends[0]).sort(), ['coverage', 'id', 'nickname', 'reductionRate', 'validDays']);
    assert.equal(list.body.friends[0].nickname, '민지');
    assert.equal(list.body.friends[0].reductionRate, 0.3);
    assert.equal((await call('GET', '/api/v1/friends', 'alice')).body.friends[0].nickname, '준호');
    // 서버 행 ID(Firebase uid)는 내려 주지 않는다.
    assert.ok(!JSON.stringify(list.body).includes('alice'));

    const again = await call('POST', '/api/v1/invites', 'alice');
    assert.equal((await call('POST', `/api/v1/invites/${again.body.code}/accept`, 'bob')).status, 409);

    // 시간이 지난 코드는 못 쓴다(cron 정리는 아래 시험).
    Date.now.mock.mockImplementation(() => now + 86_400_001);
    assert.equal((await call('POST', `/api/v1/invites/${again.body.code}/accept`, 'carol')).status, 403);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 1);
});

test('임시: 끊기·차단·철회', async (t) => {
    const { call, sql, worker, env } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    const connect = async () => {
        const invite = await call('POST', '/api/v1/invites', 'alice');
        return call('POST', `/api/v1/invites/${invite.body.code}/accept`, 'bob');
    };
    const bobId = () => sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'bob'").get().public_id;
    const aliceId = () => sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'alice'").get().public_id;

    assert.equal((await connect()).status, 201);
    assert.equal((await call('DELETE', `/api/v1/friends/${bobId()}`, 'alice')).status, 204);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'bob')).body.friends, []);

    assert.equal((await connect()).status, 201);
    assert.equal((await call('PUT', `/api/v1/blocks/${aliceId()}`, 'bob')).status, 204);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'alice')).body.friends, []);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'bob')).body.blocked.map((f) => f.nickname), ['민지']);
    // 차단한 쪽·당한 쪽 모두 다시 연결할 수 없고, 누가 차단했는지는 알리지 않는다.
    const blocked = await connect();
    assert.equal(blocked.status, 409);
    assert.equal(blocked.body.error, 'unavailable');
    assert.equal((await call('DELETE', `/api/v1/blocks/${aliceId()}`, 'bob')).status, 204);
    assert.equal((await connect()).status, 201);

    // 철회하면 친구 연결·초대·차단이 모두 지워진다.
    await call('POST', '/api/v1/invites', 'alice');
    await call('PUT', `/api/v1/blocks/${aliceId()}`, 'bob');
    assert.equal((await call('DELETE', '/api/v1/sharing', 'bob')).status, 204);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friendship').get().n, 0);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM block').get().n, 0);
    assert.equal((await call('GET', '/api/v1/friends', 'bob')).status, 403);

    await worker.scheduled({ scheduledTime: now + 2 * 86_400_000 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 0);
});
