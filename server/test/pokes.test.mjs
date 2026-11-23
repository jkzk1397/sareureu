import assert from 'node:assert/strict';
import { test } from 'node:test';
import { limits } from '../src/limits.mjs';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');
const v2 = { 'X-App-Version': '2' };

async function setup(t, users) {
    const server = await testServer(t, now);
    const { call, sql } = server;
    for (const [user, nickname] of users) {
        assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: 2, nickname })).status, 204);
    }
    const id = (user) => sql.prepare('SELECT public_id FROM sharing_consent WHERE user_id = ?').get(user).public_id;
    // 지금 시청 중인 친구에게만 보낼 수 있다(presence.test.mjs). 여기서는 모두 보는 중으로 둔다.
    const watchAll = async () => {
        for (const [user] of users) {
            assert.equal((await call('PUT', '/api/v1/presence', user, { watching: true })).status, 200);
        }
    };
    const poke = (from, to, text, headers) => call('POST', '/api/v1/pokes', from, { to: id(to), text }, headers);
    return { ...server, id, watchAll, poke };
}

test('잔소리는 친구에게만, 옛 앱은 받으면 서버에서 지우고, 차단·철회·만료면 사라진다', async (t) => {
    const { call, sql, worker, env, befriend, id, watchAll, poke } =
        await setup(t, [['alice', '민지'], ['bob', '준호'], ['carol', '하은']]);
    await watchAll();
    assert.equal((await befriend('alice', 'bob')).status, 201);

    assert.equal((await poke('alice', 'carol', '자라')).status, 403);
    assert.equal((await poke('alice', 'alice', '자라')).status, 403);
    assert.equal((await poke('alice', 'bob', '   ')).status, 400);
    assert.equal((await poke('alice', 'bob', '가'.repeat(41))).status, 400);
    assert.equal((await call('POST', '/api/v1/pokes', 'alice', { to: id('bob'), text: '자라', phone: 1 })).status, 400);
    assert.equal((await poke('alice', 'bob', ' 내일 1교시잖아, 쇼츠 그만 ')).status, 201);

    // 계약 번호 1(헤더 없음)인 옛 앱: 내려 주면 바로 지운다.
    const inbox = await call('GET', '/api/v1/pokes', 'bob');
    assert.equal(inbox.status, 200);
    assert.equal(inbox.body.pokes.length, 1);
    assert.equal(inbox.body.pokes[0].text, '내일 1교시잖아, 쇼츠 그만');
    assert.equal(inbox.body.pokes[0].fromNickname, '민지');
    assert.equal(inbox.body.pokes[0].expiresAtMs, now + 7 * 86_400_000);
    assert.ok(!JSON.stringify(inbox.body).includes('alice'));
    assert.deepEqual((await call('GET', '/api/v1/pokes', 'bob')).body.pokes, []);

    // 차단하면 연결이 끊겨 더 못 보내고, 쌓인 것도 내려 주지 않고 지운다.
    assert.equal((await poke('alice', 'bob', '하나 더')).status, 201);
    assert.equal((await call('PUT', `/api/v1/blocks/${id('alice')}`, 'bob')).status, 204);
    assert.equal((await poke('alice', 'bob', '안녕')).status, 403);
    assert.deepEqual((await call('GET', '/api/v1/pokes', 'bob')).body.pokes, []);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);

    // 다시 연결해서 보낸 것: 만료되면 cron이 지우고, 철회하면 함께 지워진다.
    await call('DELETE', `/api/v1/blocks/${id('alice')}`, 'bob');
    assert.equal((await befriend('bob', 'alice')).status, 201);
    Date.now.mock.mockImplementation(() => now + 86_400_001);
    await watchAll();
    assert.equal((await poke('alice', 'bob', '첫 번째')).status, 201);
    assert.equal((await poke('bob', 'alice', '두 번째')).status, 201);
    await worker.scheduled({ scheduledTime: now + 9 * 86_400_000 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
    Date.now.mock.mockImplementation(() => now + 9 * 86_400_000);
    await watchAll();
    assert.equal((await poke('alice', 'bob', '세 번째')).status, 201);
    assert.equal((await call('DELETE', '/api/v1/sharing', 'alice')).status, 204);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
});

test('잔소리는 같은 친구에게 하루 3번, 전체 하루 10번: 넘으면 429와 Retry-After', async (t) => {
    const users = [['alice', '민지'], ['b1', '하나'], ['b2', '둘'], ['b3', '셋'], ['b4', '넷']];
    const { sql, befriend, watchAll, poke } = await setup(t, users);
    await watchAll();
    for (const friend of ['b1', 'b2', 'b3', 'b4']) assert.equal((await befriend('alice', friend)).status, 201);

    for (let i = 0; i < 3; i++) assert.equal((await poke('alice', 'b1', `한마디 ${i}`)).status, 201);
    const perFriend = await poke('alice', 'b1', '네 번째');
    assert.equal(perFriend.status, 429);
    assert.equal(perFriend.body.error, 'daily_limit');
    assert.equal(perFriend.headers.get('Retry-After'), '86400');
    // 다른 친구에게는 보낼 수 있다: 전체 10번까지.
    for (const friend of ['b2', 'b3']) {
        for (let i = 0; i < 3; i++) assert.equal((await poke('alice', friend, `한마디 ${i}`)).status, 201);
    }
    assert.equal((await poke('alice', 'b4', '열 번째')).status, 201);
    assert.equal((await poke('alice', 'b4', '열한 번째')).status, 429);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 10);

    // 받은 쪽이 가져가 지워도 한도는 그대로 센다. 하루가 지나면 다시 보낼 수 있다.
    sql.exec('DELETE FROM poke');
    assert.equal((await poke('alice', 'b4', '또')).status, 429);
    Date.now.mock.mockImplementation(() => now + 86_400_001);
    await watchAll();
    assert.equal((await poke('alice', 'b1', '다음 날')).status, 201);
});

test('잔소리를 동시에 여러 번 보내도 하루 한도를 넘지 않는다', async (t) => {
    const users = [['alice', '민지'], ['bob', '준호']];
    const { sql, befriend, watchAll, poke } = await setup(t, users);
    await watchAll();
    assert.equal((await befriend('alice', 'bob')).status, 201);
    const results = await Promise.all(Array.from({ length: 8 }, (_, i) => poke('alice', 'bob', `동시에 ${i}`)));
    assert.equal(results.filter((r) => r.status === 201).length, limits.pokesPerFriendPerDay);
    assert.equal(results.filter((r) => r.status === 429).length, 8 - limits.pokesPerFriendPerDay);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, limits.pokesPerFriendPerDay);
});

test('같은 멱등 키로 두 번 보내면 한 번만 처리된다', async (t) => {
    const { sql, befriend, watchAll, poke } = await setup(t, [['alice', '민지'], ['bob', '준호']]);
    await watchAll();
    assert.equal((await befriend('alice', 'bob')).status, 201);
    const key = { 'Idempotency-Key': crypto.randomUUID() };
    const first = await poke('alice', 'bob', '자라', key);
    const again = await poke('alice', 'bob', '자라', key);
    assert.equal(first.status, 201);
    assert.equal(again.status, 201);
    assert.equal(again.body.id, first.body.id);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 1);
    assert.equal(sql.prepare("SELECT COUNT(*) AS n FROM rate_event WHERE kind = 'poke'").get().n, 1);

    // 동시에 와도 한 번만. 받은 쪽이 가져간 뒤 다시 와도 새로 만들지 않는다.
    const other = { 'Idempotency-Key': crypto.randomUUID() };
    const both = await Promise.all([poke('alice', 'bob', '일어나', other), poke('alice', 'bob', '일어나', other)]);
    assert.deepEqual(both.map((r) => r.status), [201, 201]);
    assert.equal(both[0].body.id, both[1].body.id);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 2);
    sql.exec('DELETE FROM poke');
    assert.equal((await poke('alice', 'bob', '일어나', other)).body.id, both[0].body.id);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
});

test('새 앱(계약 번호 2)은 받았다고 확인해야 서버에서 지운다: 응답이 끊겨도 사라지지 않는다', async (t) => {
    const { call, sql, befriend, watchAll, poke } = await setup(t, [['alice', '민지'], ['bob', '준호']]);
    await watchAll();
    assert.equal((await befriend('alice', 'bob')).status, 201);
    assert.equal((await poke('alice', 'bob', '자라')).status, 201);
    assert.equal((await poke('alice', 'bob', '일어나')).status, 201);

    // 시청 신호 응답으로 받는다. 확인하기 전에는 다시 내려 준다(앱이 id로 중복을 거른다).
    const beat = (ack) => call('PUT', '/api/v1/presence', 'bob', ack ? { watching: true, ack } : { watching: true }, v2);
    const first = await beat();
    assert.deepEqual(first.body.pokes.map((p) => p.text), ['자라', '일어나']);
    assert.equal((await beat()).body.pokes.length, 2);
    assert.equal((await call('GET', '/api/v1/pokes', 'bob', undefined, v2)).body.pokes.length, 2);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 2);

    // 받은 id를 다음 신호에 실어 보내면 그때 지운다.
    const [one, two] = first.body.pokes.map((p) => p.id);
    assert.deepEqual((await beat([one])).body.pokes.map((p) => p.id), [two]);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 1);
    // GET에도 실을 수 있다. 남의 것이나 이상한 id는 무시한다.
    assert.equal((await call('GET', `/api/v1/pokes?ack=${two}`, 'alice', undefined, v2)).status, 200);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 1);
    assert.deepEqual((await call('GET', `/api/v1/pokes?ack=${two},x!y`, 'bob', undefined, v2)).body.pokes, []);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
    // ack 모양이 배열이 아니면 400.
    assert.equal((await call('PUT', '/api/v1/presence', 'bob', { watching: true, ack: 'a' }, v2)).status, 400);
});

test('서버도 글을 다듬고 금지 표현을 막는다: 보이지 않는 문자, 줄바꿈, 사이에 낀 빈칸·특수문자', async (t) => {
    const { call, befriend, watchAll, poke } = await setup(t, [['alice', '민지'], ['bob', '준호']]);
    await watchAll();
    assert.equal((await befriend('alice', 'bob')).status, 201);

    for (const text of ['정신 차려', '정.신_차-려', '한\u200B심해', '이 ㅅ ㅂ', '쓰 레 기야', 'ｓｈｏｒｔｓ 중독']) {
        const refused = await poke('alice', 'bob', text);
        assert.equal(refused.status, 400, text);
        assert.equal(refused.body.error, 'banned_text');
    }
    // 보이지 않는 문자만 있으면 빈 글이다.
    assert.equal((await poke('alice', 'bob', '\u200B\u2060\uFEFF')).status, 400);
    // 줄바꿈·제어 문자·폭 없는 문자는 지우고, 조합형 한글은 완성형(NFC)으로 저장한다.
    assert.equal((await poke('alice', 'bob', '  내일\n1교시\u0007야\u200D  ')).status, 201);
    assert.equal((await poke('alice', 'bob', '\u1112\u1161\u11AB')).status, 201);
    const inbox = (await call('GET', '/api/v1/pokes', 'bob')).body.pokes.map((p) => p.text);
    assert.deepEqual(inbox, ['내일 1교시야', '한']);

    // 닉네임도 같다.
    assert.equal((await call('PUT', '/api/v1/sharing', 'carol', { consentVersion: 2, nickname: '병.신' })).status, 400);
    assert.equal((await call('PUT', '/api/v1/sharing', 'carol', { consentVersion: 2, nickname: '\u200B' })).status, 400);
    assert.equal((await call('PUT', '/api/v1/sharing', 'carol', { consentVersion: 2, nickname: ' 하\u200B은\n' })).status, 204);
    assert.equal((await befriend('alice', 'carol')).body.friend.nickname, '하은');
});
