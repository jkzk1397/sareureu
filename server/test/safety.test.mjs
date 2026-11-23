import assert from 'node:assert/strict';
import { readdirSync, readFileSync } from 'node:fs';
import { test } from 'node:test';
import { bundle, readBannedWords } from '../scripts/bundle-banned-words.mjs';
import { limits } from '../src/limits.mjs';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-16T12:00:00.000Z');
const minuteMs = 60_000;
const dayMs = 86_400_000;

async function join(call, user, nickname, version = 2) {
    assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: version, nickname })).status, 204);
}

test('금지 표현 목록은 docs/05_AI_SPEC.md 7절에서 온다(다르면 node scripts/bundle-banned-words.mjs)', () => {
    assert.equal(readFileSync(new URL('../src/banned-words.mjs', import.meta.url), 'utf8'), bundle());
    const words = readBannedWords();
    assert.ok(words.length >= 30);
    for (const word of ['중독', '정신 차려', 'ㅅㅂ', '니 엄마']) assert.ok(words.includes(word), word);
});

test('신고는 서버에 남고, 서로 다른 3명에게 신고되면 잔소리 보내기가 멈춘다', async (t) => {
    const { call, sql, befriend, requestWith } = await testServer(t, now);
    for (const [user, nickname] of [['bad', '나쁜이'], ['a', '하나'], ['b', '둘'], ['c', '셋'], ['x', '남']]) {
        await join(call, user, nickname);
    }
    const id = (user) => sql.prepare('SELECT public_id FROM sharing_consent WHERE user_id = ?').get(user).public_id;
    for (const friend of ['a', 'b', 'c']) assert.equal((await befriend('bad', friend)).status, 201);
    for (const user of ['a', 'b', 'c']) await call('PUT', '/api/v1/presence', user, { watching: true });
    const report = (from, body) => call('POST', '/api/v1/reports', from, body);

    // 모양: 대상은 친구 공개 ID나 받은 요청 ID 중 하나, 사유는 정해진 종류, 문장은 40자까지.
    assert.equal((await report('a', { target: id('bad') })).status, 400);
    assert.equal((await report('a', { target: id('bad'), reason: 'SPAM' })).status, 400);
    assert.equal((await report('a', { target: id('bad'), requestId: id('bad'), reason: 'POKE' })).status, 400);
    assert.equal((await report('a', { target: id('bad'), reason: 'POKE', text: '가'.repeat(41) })).status, 400);
    assert.equal((await report('a', { target: id('bad'), reason: 'POKE', phone: '010' })).status, 400);
    // 친구도 차단한 사람도 아닌 상대, 나 자신은 신고할 수 없다(공개 ID를 아무거나 넣어 신고하지 못하게).
    assert.equal((await report('x', { target: id('bad'), reason: 'OTHER' })).status, 404);
    assert.equal((await report('a', { target: id('a'), reason: 'OTHER' })).status, 404);
    assert.equal((await report('nobody', { target: id('bad'), reason: 'OTHER' })).status, 403);

    assert.equal((await call('POST', '/api/v1/pokes', 'bad', { to: id('a'), text: '자라' })).status, 201);
    assert.equal((await report('a', { target: id('bad'), reason: 'POKE', text: ' 기분 나쁜\n말 ' })).status, 204);
    const row = sql.prepare('SELECT * FROM report').get();
    assert.equal(row.reporter, 'a');
    assert.equal(row.target, 'bad');
    assert.equal(row.reason, 'POKE');
    assert.equal(row.text, '기분 나쁜 말');
    assert.equal(row.created_at_ms, now);

    // 같은 사람이 여러 번 신고해도 한 명으로 센다. 두 명까지는 보낼 수 있다.
    assert.equal((await report('a', { target: id('bad'), reason: 'HARASSMENT' })).status, 204);
    assert.equal((await report('b', { target: id('bad'), reason: 'NICKNAME' })).status, 204);
    assert.equal((await call('POST', '/api/v1/pokes', 'bad', { to: id('b'), text: '자라' })).status, 201);
    // 세 번째 사람: 차단한 뒤에도 신고할 수 있다.
    assert.equal((await call('PUT', `/api/v1/blocks/${id('bad')}`, 'c')).status, 204);
    assert.equal((await report('c', { target: id('bad'), reason: 'OTHER' })).status, 204);
    const stopped = await call('POST', '/api/v1/pokes', 'bad', { to: id('a'), text: '자라' });
    assert.equal(stopped.status, 403);
    assert.equal(stopped.body.error, 'suspended');
    // 신고한 사람에게는 결과를 알리지 않는다(204뿐). 신고당한 사람도 받기와 다른 기능은 그대로다.
    assert.equal((await call('GET', '/api/v1/friends', 'bad')).status, 200);

    // 받은 연결 요청도 신고할 수 있다(닉네임만 보이는 단계).
    const request = (await requestWith('x', 'bad')).body.request.id;
    assert.equal((await report('x', { requestId: request, reason: 'NICKNAME' })).status, 204);
    assert.equal((await report('a', { requestId: request, reason: 'NICKNAME' })).status, 404);
});

test('신고는 하루 10번, 180일 지난 신고는 cron이 지운다', async (t) => {
    const { call, sql, worker, env, befriend } = await testServer(t, now);
    await join(call, 'a', '하나');
    await join(call, 'b', '둘');
    assert.equal((await befriend('a', 'b')).status, 201);
    const target = sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'b'").get().public_id;
    for (let i = 0; i < limits.reportsPerDay; i++) {
        assert.equal((await call('POST', '/api/v1/reports', 'a', { target, reason: 'OTHER' })).status, 204);
    }
    const limited = await call('POST', '/api/v1/reports', 'a', { target, reason: 'OTHER' });
    assert.equal(limited.status, 429);
    assert.equal(limited.headers.get('Retry-After'), '86400');

    // 계정은 살아 있고(버려진 계정 정리에 걸리지 않고) 신고만 오래됐을 때.
    sql.prepare('UPDATE sharing_consent SET granted_at_ms = ?').run(now + limits.reportRetentionMs);
    await worker.scheduled({ scheduledTime: now + limits.reportRetentionMs - 1 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM report').get().n, 10);
    await worker.scheduled({ scheduledTime: now + limits.reportRetentionMs }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM report').get().n, 0);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM rate_event').get().n, 0);
});

test('폰 시계가 5분까지 앞서도 받고, 그보다 크면 clock_skew로 구분해 준다', async (t) => {
    const { call, sql } = await testServer(t, now);
    await join(call, 'alice', '민지');
    const standing = { reductionRate: 0.2, coverage: null, validDays: 3, streakDays: 1 };
    const daily = { shortformMinutes: 30, coverage: null, timeZone: 'Asia/Seoul' };

    const ahead = now + limits.clockSkewMs;
    assert.equal((await call('PUT', '/api/v1/standing', 'alice', { ...standing, measuredAtMs: ahead })).status, 204);
    assert.equal((await call('PUT', '/api/v1/daily/2026-11-16', 'alice', { ...daily, measuredAtMs: ahead })).status, 204);
    for (const [path, body] of [['/api/v1/standing', standing], ['/api/v1/daily/2026-11-16', daily]]) {
        const far = await call('PUT', path, 'alice', { ...body, measuredAtMs: ahead + 1 });
        assert.equal(far.status, 400);
        assert.deepEqual(far.body, { error: 'clock_skew' });
    }
    // 모양이 틀린 것은 여전히 다른 오류다.
    assert.equal((await call('PUT', '/api/v1/standing', 'alice', { ...standing, measuredAtMs: 'soon' })).body.error,
        'invalid_standing');

    // 폰 시계가 조금 늦은 경우: 동의 직후 잰 값이 서버의 동의 시각보다 앞서 보여도 5분까지는 받는다.
    await join(call, 'bob', '준호');
    const behind = now - 2 * minuteMs;
    assert.equal((await call('PUT', '/api/v1/standing', 'bob', { ...standing, measuredAtMs: behind })).status, 204);
    // 동의 전에 잰 값(5분보다 더 이전)은 여전히 받지 않는다.
    await join(call, 'carol', '하은');
    const before = now - limits.clockSkewMs - 1;
    assert.equal((await call('PUT', '/api/v1/standing', 'carol', { ...standing, measuredAtMs: before })).status, 403);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM standing').get().n, 2);
});

test('60일 동안 업로드가 없는 계정은 cron이 동의 행과 딸린 기록을 지운다', async (t) => {
    const { call, sql, worker, env, befriend } = await testServer(t, now);
    for (const [user, nickname] of [['gone', '떠난이'], ['active', '쓰는이'], ['daily', '합계만'], ['fresh', '새내기']]) {
        await join(call, user, nickname);
    }
    assert.equal((await befriend('gone', 'active')).status, 201);
    await call('POST', '/api/v1/invites', 'gone');
    const standing = { reductionRate: 0.2, coverage: null, validDays: 3, streakDays: 1 };
    assert.equal((await call('PUT', '/api/v1/standing', 'gone', { ...standing, measuredAtMs: now })).status, 204);

    // 59일째: 아직 그대로.
    await worker.scheduled({ scheduledTime: now + 59 * dayMs }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM sharing_consent').get().n, 4);

    // 61일째: 그사이 'active'는 랭킹 숫자를, 'daily'는 하루 합계를 올렸고, 'fresh'는 방금 동의했다.
    const later = now + 61 * dayMs;
    Date.now.mock.mockImplementation(() => later);
    assert.equal((await call('PUT', '/api/v1/standing', 'active', { ...standing, measuredAtMs: later })).status, 204);
    assert.equal((await call('PUT', '/api/v1/daily/2027-01-15', 'daily', {
        shortformMinutes: 10, coverage: null, measuredAtMs: later, timeZone: 'Asia/Seoul',
    })).status, 204);
    assert.equal((await call('DELETE', '/api/v1/sharing', 'fresh')).status, 204);
    await join(call, 'fresh', '새내기');
    await worker.scheduled({ scheduledTime: later }, env);

    const left = sql.prepare('SELECT user_id FROM sharing_consent ORDER BY user_id').all().map((r) => r.user_id);
    assert.deepEqual(left, ['active', 'daily', 'fresh']);
    // 딸린 기록(친구 연결, 랭킹 숫자, 초대)도 함께 사라진다.
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friendship').get().n, 0);
    assert.equal(sql.prepare("SELECT COUNT(*) AS n FROM standing WHERE user_id = 'gone'").get().n, 0);
    assert.deepEqual((await call('GET', '/api/v1/friends', 'active')).body.friends, []);
});

test('IP 기준 제한은 바인딩이 있을 때만, 정해진 경로에만 걸리고 실패하면 통과시킨다', async (t) => {
    const { call, env } = await testServer(t, now);
    await join(call, 'alice', '민지');
    const ip = { 'CF-Connecting-IP': '203.0.113.7' };
    const keys = [];
    env.IP_LIMITER = { limit: async ({ key }) => { keys.push(key); return { success: false }; } };

    const blocked = await call('POST', '/api/v1/invites/ABCDEFGH/requests', 'alice', undefined, ip);
    assert.equal(blocked.status, 429);
    assert.equal(blocked.body.error, 'too_many_requests');
    assert.equal(blocked.headers.get('Retry-After'), '60');
    assert.deepEqual(keys, ['203.0.113.7']);
    assert.equal((await call('PUT', '/api/v1/sharing', 'bob', { consentVersion: 2, nickname: '준호' }, ip)).status, 429);
    assert.equal((await call('POST', '/api/v1/pokes', 'alice', { to: 'x', text: 'y' }, ip)).status, 429);
    assert.equal((await call('POST', '/api/v1/reports', 'alice', {}, ip)).status, 429);
    // 걸지 않는 경로: 친구 목록, 시청 신호, 잔소리 받기, 동의 철회.
    assert.equal((await call('GET', '/api/v1/friends', 'alice', undefined, ip)).status, 200);
    assert.equal((await call('PUT', '/api/v1/presence', 'alice', { watching: false }, ip)).status, 200);
    assert.equal((await call('GET', '/api/v1/pokes', 'alice', undefined, ip)).status, 200);
    // IP를 모르거나 바인딩이 오류를 내면 통과시킨다(계정 기준 한도는 그대로 동작).
    assert.equal((await call('POST', '/api/v1/invites/ABCDEFGH/requests', 'alice')).status, 404);
    env.IP_LIMITER = { limit: async () => { throw new Error('unavailable'); } };
    assert.equal((await call('POST', '/api/v1/invites/ABCDEFGH/requests', 'alice', undefined, ip)).status, 404);
    env.IP_LIMITER = { limit: async () => ({ success: true }) };
    assert.equal((await call('POST', '/api/v1/invites/ABCDEFGH/requests', 'alice', undefined, ip)).status, 404);
});

test('서버 코드는 console에 아무것도 찍지 않는다(닉네임·잔소리·토큰·코드가 로그에 남지 않게)', () => {
    const dir = new URL('../src/', import.meta.url);
    for (const file of readdirSync(dir).filter((name) => name.endsWith('.mjs'))) {
        assert.ok(!/console\s*\./.test(readFileSync(new URL(file, dir), 'utf8')), file);
    }
});
