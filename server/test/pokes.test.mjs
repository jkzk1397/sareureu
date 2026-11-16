import assert from 'node:assert/strict';
import { test } from 'node:test';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');

test('임시: 잔소리는 친구에게만, 받으면 서버에서 지우고, 차단·철회·만료면 사라진다', async (t) => {
    const { call, sql, worker, env } = await testServer(t, now);
    for (const [user, nickname] of [['alice', '민지'], ['bob', '준호'], ['carol', '하은']]) {
        assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: 1, nickname })).status, 204);
    }
    const id = (user) => sql.prepare('SELECT public_id FROM sharing_consent WHERE user_id = ?').get(user).public_id;
    const invite = await call('POST', '/api/v1/invites', 'alice');
    assert.equal((await call('POST', `/api/v1/invites/${invite.body.code}/accept`, 'bob')).status, 201);

    const poke = (from, to, text) => call('POST', '/api/v1/pokes', from, { to: id(to), text });
    assert.equal((await poke('alice', 'carol', '자라')).status, 403);
    assert.equal((await poke('alice', 'alice', '자라')).status, 403);
    assert.equal((await poke('alice', 'bob', '   ')).status, 400);
    assert.equal((await poke('alice', 'bob', '가'.repeat(41))).status, 400);
    assert.equal((await call('POST', '/api/v1/pokes', 'alice', { to: id('bob'), text: '자라', phone: 1 })).status, 400);
    assert.equal((await poke('alice', 'bob', ' 내일 1교시잖아, 쇼츠 그만 ')).status, 201);

    const inbox = await call('GET', '/api/v1/pokes', 'bob');
    assert.equal(inbox.status, 200);
    assert.equal(inbox.body.pokes.length, 1);
    assert.equal(inbox.body.pokes[0].text, '내일 1교시잖아, 쇼츠 그만');
    assert.equal(inbox.body.pokes[0].fromNickname, '민지');
    assert.equal(inbox.body.pokes[0].expiresAtMs, now + 7 * 86_400_000);
    assert.ok(!JSON.stringify(inbox.body).includes('alice'));
    assert.deepEqual((await call('GET', '/api/v1/pokes', 'bob')).body.pokes, []);

    // 하루 10개까지.
    for (let i = 0; i < 9; i++) assert.equal((await poke('alice', 'bob', `한마디 ${i}`)).status, 201);
    assert.equal((await poke('alice', 'bob', '열한 번째')).status, 429);

    // 차단하면 연결이 끊겨 더 못 보내고, 쌓인 것도 내려 주지 않고 지운다.
    assert.equal((await call('PUT', `/api/v1/blocks/${id('alice')}`, 'bob')).status, 204);
    assert.equal((await poke('alice', 'bob', '안녕')).status, 403);
    assert.deepEqual((await call('GET', '/api/v1/pokes', 'bob')).body.pokes, []);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);

    // 다시 연결해서 보낸 것: 만료되면 cron이 지우고, 철회하면 함께 지워진다.
    await call('DELETE', `/api/v1/blocks/${id('alice')}`, 'bob');
    const again = await call('POST', '/api/v1/invites', 'bob');
    assert.equal((await call('POST', `/api/v1/invites/${again.body.code}/accept`, 'alice')).status, 201);
    Date.now.mock.mockImplementation(() => now + 86_400_001);
    assert.equal((await poke('alice', 'bob', '첫 번째')).status, 201);
    assert.equal((await poke('bob', 'alice', '두 번째')).status, 201);
    await worker.scheduled({ scheduledTime: now + 9 * 86_400_000 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
    assert.equal((await poke('alice', 'bob', '세 번째')).status, 201);
    assert.equal((await call('DELETE', '/api/v1/sharing', 'alice')).status, 204);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
});
