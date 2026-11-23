import assert from 'node:assert/strict';
import { test } from 'node:test';
import { codeHash, codePattern, newCode } from '../src/friends.mjs';
import { limits } from '../src/limits.mjs';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');
const minuteMs = 60_000;

async function join(call, user, nickname, standing) {
    assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: 1, nickname })).status, 204);
    if (standing) assert.equal((await call('PUT', '/api/v1/standing', user, { ...standing, measuredAtMs: now })).status, 204);
}

test('초대 코드는 8자리이고 글자표에서 고르게 뽑으며, 서버에는 해시만 남는다', async (t) => {
    const { call, sql } = await testServer(t, now);
    assert.equal(limits.codeAlphabet.length, 31);
    for (const confusing of '0O1IL') assert.ok(!limits.codeAlphabet.includes(confusing));

    // 고르게: 31글자가 모두 나오고 가장 많이 나온 글자와 적게 나온 글자의 차이가 작다.
    const counts = new Map();
    for (let i = 0; i < 4000; i++) for (const c of newCode()) counts.set(c, (counts.get(c) ?? 0) + 1);
    assert.equal(counts.size, 31);
    const expected = 4000 * limits.codeLength / 31;
    for (const n of counts.values()) assert.ok(Math.abs(n - expected) < expected * 0.2, `${n} vs ${expected}`);

    assert.equal((await call('POST', '/api/v1/invites', 'alice')).status, 403);
    await join(call, 'alice', '민지');
    const invite = await call('POST', '/api/v1/invites', 'alice');
    assert.equal(invite.status, 201);
    assert.match(invite.body.code, /^[A-HJKMNP-Z2-9]{8}$/);
    assert.ok(codePattern.test(invite.body.code));
    assert.equal(invite.body.expiresAtMs, now + 86_400_000);
    const row = sql.prepare('SELECT * FROM invite').get();
    assert.deepEqual(Object.keys(row).sort(), ['code_hash', 'expires_at_ms', 'user_id']);
    assert.equal(row.code_hash, await codeHash(invite.body.code));
    assert.ok(!JSON.stringify(row).includes(invite.body.code));

    // 주인이 코드를 취소할 수 있다.
    assert.equal((await call('DELETE', '/api/v1/invites', 'alice')).status, 204);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 0);
});

test('연결은 두 단계: 코드로 요청만 생기고, 주인이 수락해야 친구가 된다', async (t) => {
    const { call, sql, requestWith } = await testServer(t, now);
    await join(call, 'alice', '민지', { reductionRate: 0.3, coverage: 0.9, validDays: 7 });
    await join(call, 'bob', '준호', { reductionRate: 0.1, coverage: 0.85, validDays: 6 });

    const first = await call('POST', '/api/v1/invites', 'alice');
    // 새로 만들면 이전 코드는 못 쓴다.
    const invite = await call('POST', '/api/v1/invites', 'alice');
    assert.notEqual(invite.body.code, first.body.code);
    assert.equal((await call('POST', `/api/v1/invites/${first.body.code}/requests`, 'bob')).status, 404);

    // 내 코드, 동의하지 않은 사람.
    const own = await call('POST', `/api/v1/invites/${invite.body.code}/requests`, 'alice');
    assert.equal(own.status, 409);
    assert.equal(own.body.error, 'own_invite');
    assert.equal((await call('POST', `/api/v1/invites/${invite.body.code}/requests`, 'carol')).status, 403);

    const requested = await call('POST', `/api/v1/invites/${invite.body.code.toLowerCase()}/requests`, 'bob');
    assert.equal(requested.status, 201);
    assert.deepEqual(Object.keys(requested.body.request).sort(), ['expiresAtMs', 'id']);
    assert.equal(requested.body.request.expiresAtMs, now + 7 * 86_400_000);
    // 요청한 사람에게는 수락 전까지 주인의 닉네임·숫자가 가지 않는다.
    assert.ok(!JSON.stringify(requested.body).includes('민지'));
    assert.deepEqual((await call('GET', '/api/v1/friends', 'bob')).body.friends, []);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'alice')).body.friends, []);
    // 한 번 쓴 코드는 없어진다.
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 0);
    assert.equal((await call('POST', `/api/v1/invites/${invite.body.code}/requests`, 'bob')).status, 404);

    // 주인에게는 요청한 사람의 닉네임만 보인다. 요청한 쪽 목록에는 상대가 누구인지 없다.
    const mine = await call('GET', '/api/v1/friend-requests', 'alice');
    assert.deepEqual(mine.body.sent, []);
    assert.deepEqual(Object.keys(mine.body.received[0]).sort(), ['createdAtMs', 'expiresAtMs', 'fromNickname', 'id']);
    assert.equal(mine.body.received[0].fromNickname, '준호');
    const theirs = await call('GET', '/api/v1/friend-requests', 'bob');
    assert.deepEqual(theirs.body.received, []);
    assert.deepEqual(Object.keys(theirs.body.sent[0]).sort(), ['createdAtMs', 'expiresAtMs', 'id']);
    assert.ok(!JSON.stringify(theirs.body).includes('민지'));

    // 요청한 사람이나 남은 수락할 수 없다.
    const id = requested.body.request.id;
    assert.equal((await call('POST', `/api/v1/friend-requests/${id}/accept`, 'bob')).status, 404);
    await join(call, 'carol', '하은');
    assert.equal((await call('POST', `/api/v1/friend-requests/${id}/accept`, 'carol')).status, 404);

    const accepted = await call('POST', `/api/v1/friend-requests/${id}/accept`, 'alice');
    assert.equal(accepted.status, 201);
    assert.equal(accepted.body.friend.nickname, '준호');
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friend_request').get().n, 0);

    const list = await call('GET', '/api/v1/friends', 'bob');
    assert.deepEqual(Object.keys(list.body.friends[0]).sort(),
        ['coverage', 'id', 'nickname', 'reductionRate', 'streakDays', 'validDays', 'watching']);
    assert.equal(list.body.friends[0].nickname, '민지');
    assert.equal(list.body.friends[0].reductionRate, 0.3);
    assert.equal((await call('GET', '/api/v1/friends', 'alice')).body.friends[0].nickname, '준호');
    // 서버 행 ID(Firebase uid)는 내려 주지 않는다.
    assert.ok(!JSON.stringify(list.body).includes('alice'));

    // 이미 친구.
    const again = await requestWith('alice', 'bob');
    assert.equal(again.status, 409);
    assert.equal(again.body.error, 'already_friends');

    // 시간이 지난 코드는 못 쓴다.
    const late = await call('POST', '/api/v1/invites', 'alice');
    Date.now.mock.mockImplementation(() => now + 86_400_001);
    assert.equal((await call('POST', `/api/v1/invites/${late.body.code}/requests`, 'carol')).status, 404);
});

test('거절은 상대에게 알리지 않고, 보낸 사람은 취소할 수 있으며, 요청은 7일 뒤 사라진다', async (t) => {
    const { call, sql, worker, env, requestWith } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    await join(call, 'carol', '하은');

    const fromBob = (await requestWith('alice', 'bob')).body.request.id;
    assert.equal((await call('DELETE', `/api/v1/friend-requests/${fromBob}`, 'alice')).status, 204);
    // 받은 쪽 목록에서는 사라지지만 보낸 쪽에는 그대로 '기다리는 중'으로 보인다.
    assert.deepEqual((await call('GET', '/api/v1/friend-requests', 'alice')).body.received, []);
    assert.equal((await call('GET', '/api/v1/friend-requests', 'bob')).body.sent.length, 1);
    // 거절한 요청은 수락할 수 없고, 같은 사람이 다시 코드를 넣어도 새 요청이 생기지 않는다(같은 응답).
    assert.equal((await call('POST', `/api/v1/friend-requests/${fromBob}/accept`, 'alice')).status, 404);
    const retry = await requestWith('alice', 'bob');
    assert.equal(retry.status, 201);
    assert.equal(retry.body.request.id, fromBob);
    assert.deepEqual((await call('GET', '/api/v1/friend-requests', 'alice')).body.received, []);

    // 보낸 사람이 취소하면 지워진다.
    const fromCarol = (await requestWith('alice', 'carol')).body.request.id;
    assert.equal((await call('DELETE', `/api/v1/friend-requests/${fromCarol}`, 'carol')).status, 204);
    assert.deepEqual((await call('GET', '/api/v1/friend-requests', 'alice')).body.received, []);
    assert.deepEqual((await call('GET', '/api/v1/friend-requests', 'carol')).body.sent, []);

    // 7일이 지나면 목록에서 빠지고 cron이 지운다. 그 뒤에는 다시 요청할 수 있다.
    const week = 7 * 86_400_000;
    Date.now.mock.mockImplementation(() => now + week + 1);
    assert.deepEqual((await call('GET', '/api/v1/friend-requests', 'bob')).body.sent, []);
    assert.equal((await call('POST', `/api/v1/friend-requests/${fromBob}/accept`, 'alice')).status, 404);
    await worker.scheduled({ scheduledTime: now + week + 1 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friend_request').get().n, 0);
    assert.equal((await requestWith('alice', 'bob')).status, 201);
    assert.equal((await call('GET', '/api/v1/friend-requests', 'alice')).body.received.length, 1);
});

test('없는 코드·만료된 코드·차단 관계·가득 찬 대기 요청은 모두 같은 응답이다', async (t) => {
    const { call, sql, requestWith } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    const id = (user) => sql.prepare('SELECT public_id FROM sharing_consent WHERE user_id = ?').get(user).public_id;
    const same = (response) => {
        assert.equal(response.status, 404);
        assert.deepEqual(response.body, { error: 'invite_not_found' });
    };

    same(await call('POST', '/api/v1/invites/ABCDEFGH/requests', 'bob'));
    // 글자표에 없는 글자나 길이가 다른 코드.
    same(await call('POST', '/api/v1/invites/ABCDEFG0/requests', 'bob'));
    same(await call('POST', '/api/v1/invites/ABCDEF/requests', 'bob'));

    // 차단한 쪽·당한 쪽 모두 요청할 수 없고, 누가 차단했는지는 알리지 않는다.
    assert.equal((await call('PUT', `/api/v1/blocks/${id('alice')}`, 'bob')).status, 204);
    same(await requestWith('alice', 'bob'));
    same(await requestWith('bob', 'alice'));
    // 차단 때문에 실패한 코드는 쓰이지 않고 남는다.
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 2);
    assert.equal((await call('DELETE', `/api/v1/blocks/${id('alice')}`, 'bob')).status, 204);
    assert.equal((await requestWith('alice', 'bob')).status, 201);

    // 주인의 대기 요청이 20개면 더 받지 않는다(이것도 같은 응답).
    await join(call, 'owner', '주인');
    const guest = sql.prepare('INSERT INTO sharing_consent (user_id, version, granted_at_ms, public_id, nickname) VALUES (?, 1, ?, ?, ?)');
    const pending = sql.prepare('INSERT INTO friend_request (id, from_user, to_user, created_at_ms, expires_at_ms) VALUES (?, ?, ?, ?, ?)');
    for (let i = 0; i < limits.maxPendingRequests - 1; i++) {
        guest.run(`guest${i}`, now, crypto.randomUUID(), `손님${i}`);
        pending.run(crypto.randomUUID(), `guest${i}`, 'owner', now, now + limits.requestTtlMs);
    }
    await join(call, 'last', '막내');
    assert.equal((await requestWith('owner', 'last')).status, 201);
    await join(call, 'late', '늦은이');
    same(await requestWith('owner', 'late'));
    assert.equal((await call('GET', '/api/v1/friend-requests', 'owner')).body.received.length, 20);
});

test('틀린 코드는 계정마다 10분에 5번, 하루 20번: 넘으면 429와 Retry-After', async (t) => {
    const { call, requestWith } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    const wrong = () => call('POST', '/api/v1/invites/ABCDEFGH/requests', 'bob');

    for (let i = 0; i < 5; i++) assert.equal((await wrong()).status, 404);
    const limited = await wrong();
    assert.equal(limited.status, 429);
    assert.equal(limited.body.error, 'too_many_attempts');
    assert.equal(limited.headers.get('Retry-After'), '600');
    // 한도에 걸린 동안에는 맞는 코드도 받지 않는다(맞는지 알려 주지 않는다).
    assert.equal((await requestWith('alice', 'bob')).status, 429);
    // 다른 계정은 영향이 없다.
    await join(call, 'carol', '하은');
    assert.equal((await call('POST', '/api/v1/invites/ABCDEFGH/requests', 'carol')).status, 404);

    // 10분이 지나면 다시 5번. 맞는 코드와 내 코드는 틀린 횟수로 세지 않는다.
    let clock = now;
    const advance = (ms) => {
        clock += ms;
        Date.now.mock.mockImplementation(() => clock);
    };
    advance(10 * minuteMs + 1);
    assert.equal((await requestWith('alice', 'bob')).status, 201);
    for (let round = 0; round < 3; round++) {
        if (round > 0) advance(10 * minuteMs + 1);
        for (let i = 0; i < 5; i++) assert.equal((await wrong()).status, 404, `round ${round} try ${i}`);
    }
    // 하루 20번을 다 썼다: 10분이 지나도 안 된다.
    advance(10 * minuteMs + 1);
    const daily = await wrong();
    assert.equal(daily.status, 429);
    assert.ok(Number(daily.headers.get('Retry-After')) > 20 * 60 * 60);
    advance(86_400_000);
    assert.equal((await wrong()).status, 404);
});

test('두 사람이 같은 코드를 동시에 넣어도 코드는 한 번만 쓰인다', async (t) => {
    const { call, sql } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    await join(call, 'carol', '하은');
    const invite = await call('POST', '/api/v1/invites', 'alice');
    const path = `/api/v1/invites/${invite.body.code}/requests`;
    const results = await Promise.all([call('POST', path, 'bob'), call('POST', path, 'carol')]);
    assert.deepEqual(results.map((r) => r.status).sort(), [201, 404]);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friend_request').get().n, 1);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 0);
});

test('친구는 50명까지: 동시에 수락해도 넘지 않는다', async (t) => {
    const { call, sql, requestWith } = await testServer(t, now);
    await join(call, 'owner', '주인');
    // 이미 49명인 주인.
    const insert = sql.prepare('INSERT INTO sharing_consent (user_id, version, granted_at_ms, public_id) VALUES (?, 1, ?, ?)');
    const link = sql.prepare('INSERT INTO friendship (user_a, user_b, created_at_ms) VALUES (?, ?, ?)');
    for (let i = 0; i < 49; i++) {
        insert.run(`f${i}`, now, crypto.randomUUID());
        link.run(`f${i}`, 'owner', now);
    }
    await join(call, 'bob', '준호');
    await join(call, 'carol', '하은');
    const fromBob = (await requestWith('owner', 'bob')).body.request.id;
    const fromCarol = (await requestWith('owner', 'carol')).body.request.id;
    const results = await Promise.all([
        call('POST', `/api/v1/friend-requests/${fromBob}/accept`, 'owner'),
        call('POST', `/api/v1/friend-requests/${fromCarol}/accept`, 'owner'),
    ]);
    assert.deepEqual(results.map((r) => r.status).sort(), [201, 409]);
    assert.equal(results.find((r) => r.status === 409).body.error, 'too_many_friends');
    const count = sql.prepare("SELECT COUNT(*) AS n FROM friendship WHERE user_a = 'owner' OR user_b = 'owner'").get().n;
    assert.equal(count, 50);
});

test('끊기·차단·철회', async (t) => {
    const { call, sql, worker, env, befriend, requestWith } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    const bobId = () => sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'bob'").get().public_id;
    const aliceId = () => sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'alice'").get().public_id;

    assert.equal((await befriend('alice', 'bob')).status, 201);
    assert.equal((await call('DELETE', `/api/v1/friends/${bobId()}`, 'alice')).status, 204);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'bob')).body.friends, []);

    assert.equal((await befriend('alice', 'bob')).status, 201);
    assert.equal((await call('PUT', `/api/v1/blocks/${aliceId()}`, 'bob')).status, 204);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'alice')).body.friends, []);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'bob')).body.blocked.map((f) => f.nickname), ['민지']);
    assert.equal((await befriend('alice', 'bob')).status, 404);
    assert.equal((await call('DELETE', `/api/v1/blocks/${aliceId()}`, 'bob')).status, 204);
    assert.equal((await befriend('alice', 'bob')).status, 201);

    // 받은 요청을 거절하면서 차단할 수 있다(닉네임만 보이는 단계에서도).
    await join(call, 'carol', '하은');
    const fromCarol = (await requestWith('alice', 'carol')).body.request.id;
    assert.equal((await call('DELETE', `/api/v1/friend-requests/${fromCarol}?block=1`, 'alice')).status, 204);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'alice')).body.blocked.map((f) => f.nickname), ['하은']);
    assert.equal((await requestWith('alice', 'carol')).status, 404);

    // 철회하면 친구 연결·초대·요청·차단이 모두 지워진다.
    await call('POST', '/api/v1/invites', 'alice');
    await call('PUT', `/api/v1/blocks/${aliceId()}`, 'bob');
    assert.equal((await call('DELETE', '/api/v1/sharing', 'bob')).status, 204);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friendship').get().n, 0);
    assert.equal(sql.prepare("SELECT COUNT(*) AS n FROM block WHERE blocker = 'bob'").get().n, 0);
    assert.equal((await call('GET', '/api/v1/friends', 'bob')).status, 403);

    await worker.scheduled({ scheduledTime: now + 2 * 86_400_000 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 0);
});

test('코드 만들기는 하루 10번, 차단과 끊기는 합쳐서 하루 50번', async (t) => {
    const { call, sql } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    for (let i = 0; i < 10; i++) assert.equal((await call('POST', '/api/v1/invites', 'alice')).status, 201);
    const limited = await call('POST', '/api/v1/invites', 'alice');
    assert.equal(limited.status, 429);
    assert.equal(limited.headers.get('Retry-After'), '86400');

    const bobId = sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'bob'").get().public_id;
    for (let i = 0; i < 25; i++) {
        assert.equal((await call('PUT', `/api/v1/blocks/${bobId}`, 'alice')).status, 204);
        assert.equal((await call('DELETE', `/api/v1/blocks/${bobId}`, 'alice')).status, 204);
    }
    assert.equal((await call('PUT', `/api/v1/blocks/${bobId}`, 'alice')).status, 429);
    assert.equal((await call('DELETE', `/api/v1/friends/${bobId}`, 'alice')).status, 429);
    // 모르는 상대 끊기는 세지 않고 조용히 204.
    assert.equal((await call('DELETE', `/api/v1/friends/${crypto.randomUUID()}`, 'alice')).status, 204);
});

test('옛 앱의 바로 연결 경로는 426으로 새 앱을 안내하고, 낮은 계약 번호도 426이다', async (t) => {
    const { call, env } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    const invite = await call('POST', '/api/v1/invites', 'alice');
    const legacy = await call('POST', `/api/v1/invites/${invite.body.code}/accept`, 'bob');
    assert.equal(legacy.status, 426);
    assert.deepEqual(legacy.body, { error: 'upgrade_required' });

    // 헤더가 없으면 1(지금 깔린 앱)로 보고 그대로 받는다. 이상한 값도 1.
    assert.equal((await call('GET', '/api/v1/friends', 'alice', undefined, { 'X-App-Version': '2' })).status, 200);
    assert.equal((await call('GET', '/api/v1/friends', 'alice', undefined, { 'X-App-Version': 'abc' })).status, 200);

    // 서버가 최소 번호를 올리면 그보다 낮은 앱은 426(로그인 확인보다 먼저).
    env.MIN_APP_VERSION = '3';
    const old = await call('GET', '/api/v1/friends', 'alice', undefined, { 'X-App-Version': '2' });
    assert.equal(old.status, 426);
    assert.deepEqual(old.body, { error: 'upgrade_required' });
    assert.equal((await call('GET', '/api/v1/friends', 'alice')).status, 426);
    assert.equal((await call('GET', '/api/v1/friends', 'alice', undefined, { 'X-App-Version': '3' })).status, 200);
});
