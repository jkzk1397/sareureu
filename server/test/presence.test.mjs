import assert from 'node:assert/strict';
import { test } from 'node:test';
import { invitePage } from '../src/invite-page.mjs';
import { testServer } from './helpers.mjs';

const now = Date.parse('2026-11-23T12:00:00.000Z');

async function join(call, user, nickname, version = 2) {
    assert.equal((await call('PUT', '/api/v1/sharing', user, { consentVersion: version, nickname })).status, 204);
}

async function befriend(call, a, b) {
    const invite = await call('POST', '/api/v1/invites', a);
    const requested = await call('POST', `/api/v1/invites/${invite.body.code}/requests`, b);
    assert.equal(requested.status, 201);
    assert.equal((await call('POST', `/api/v1/friend-requests/${requested.body.request.id}/accept`, a)).status, 201);
}

test('시청 중 신호는 동의 버전 2부터, 최근 40초 안 신호만 친구 목록에 시청 중으로 보인다', async (t) => {
    const { call, sql, worker, env } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    await join(call, 'old', '예전앱', 1);
    await befriend(call, 'alice', 'bob');

    // 예전 동의(버전 1)는 시청 중을 보낼 수 없다. 모양이 다르면 400.
    assert.equal((await call('PUT', '/api/v1/presence', 'old', { watching: true })).status, 403);
    assert.equal((await call('PUT', '/api/v1/presence', 'carol', { watching: true })).status, 403);
    assert.equal((await call('PUT', '/api/v1/presence', 'bob', { watching: 'yes' })).status, 400);
    assert.equal((await call('PUT', '/api/v1/presence', 'bob', { watching: true, app: 'x' })).status, 400);

    const watching = async () => (await call('GET', '/api/v1/friends', 'alice')).body.friends[0].watching;
    assert.equal(await watching(), false);
    const beat = await call('PUT', '/api/v1/presence', 'bob', { watching: true });
    assert.equal(beat.status, 200);
    assert.deepEqual(beat.body, { pokes: [] });
    assert.equal(await watching(), true);
    // 서버에는 마지막 신호 시각만 남는다.
    assert.deepEqual(Object.keys(sql.prepare('SELECT * FROM presence').get()).sort(), ['last_seen_ms', 'user_id']);

    // 신호를 두 번 놓쳐도(15초 간격, 39초 뒤) 시청 중이 이어지고, 40초 지나면 시청 중이 아니다. 신호가 다시 오면 다시 시청 중.
    Date.now.mock.mockImplementation(() => now + 39_999);
    assert.equal(await watching(), true);
    Date.now.mock.mockImplementation(() => now + 40_000);
    assert.equal(await watching(), false);
    await call('PUT', '/api/v1/presence', 'bob', { watching: true });
    assert.equal(await watching(), true);

    // 멈춤 신호면 바로 지운다.
    await call('PUT', '/api/v1/presence', 'bob', { watching: false });
    assert.equal(await watching(), false);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM presence').get().n, 0);

    // 멈춤 신호 없이 끊긴 행은 cron이 지우고, 철회하면 함께 지워진다.
    await call('PUT', '/api/v1/presence', 'bob', { watching: true });
    await worker.scheduled({ scheduledTime: now + 120_000 }, env);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM presence').get().n, 0);
    await call('PUT', '/api/v1/presence', 'bob', { watching: true });
    assert.equal((await call('DELETE', '/api/v1/sharing', 'bob')).status, 204);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM presence').get().n, 0);
});

test('6단계: 잔소리는 시청 중인 친구에게만 보내고, 받는 쪽은 다음 시청 신호 응답으로 받는다', async (t) => {
    const { call, sql } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    await befriend(call, 'alice', 'bob');
    const bobId = sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'bob'").get().public_id;
    const poke = (text) => call('POST', '/api/v1/pokes', 'alice', { to: bobId, text });

    assert.equal((await poke('자라')).status, 409);
    assert.equal((await poke('자라')).body.error, 'not_watching');
    await call('PUT', '/api/v1/presence', 'bob', { watching: true });
    assert.equal((await poke(' 쇼츠 그만 보고 자라 ')).status, 201);

    const beat = await call('PUT', '/api/v1/presence', 'bob', { watching: true });
    assert.equal(beat.body.pokes.length, 1);
    assert.equal(beat.body.pokes[0].text, '쇼츠 그만 보고 자라');
    assert.equal(beat.body.pokes[0].fromNickname, '민지');
    assert.ok(!JSON.stringify(beat.body).includes('alice'));
    // 한 번 내려 주면 지운다.
    assert.deepEqual((await call('PUT', '/api/v1/presence', 'bob', { watching: true })).body.pokes, []);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM poke').get().n, 0);
});

test('6단계: 연속 일수를 저장하고 친구에게 보인다(없으면 0, 범위 밖이면 400)', async (t) => {
    const { call } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    await befriend(call, 'alice', 'bob');
    const standing = { reductionRate: 0.2, coverage: null, validDays: 7, measuredAtMs: now };
    const friend = async () => (await call('GET', '/api/v1/friends', 'alice')).body.friends[0];

    assert.equal((await friend()).streakDays, 0);
    assert.equal((await call('PUT', '/api/v1/standing', 'bob', { ...standing, streakDays: -1 })).status, 400);
    assert.equal((await call('PUT', '/api/v1/standing', 'bob', { ...standing, streakDays: 1.5 })).status, 400);
    assert.equal((await call('PUT', '/api/v1/standing', 'bob', { ...standing, streakDays: 3651 })).status, 400);
    assert.equal((await call('PUT', '/api/v1/standing', 'bob', { ...standing, streakDays: 12 })).status, 204);
    assert.equal((await friend()).streakDays, 12);
    // 예전 앱처럼 streakDays 없이 보내면 0.
    Date.now.mock.mockImplementation(() => now + 1000);
    assert.equal((await call('PUT', '/api/v1/standing', 'bob', { ...standing, measuredAtMs: now + 1000 })).status, 204);
    assert.equal((await friend()).streakDays, 0);
});

test('6단계: 초대 QR 페이지는 로그인 없이 열리고, 앱을 열거나 설치 파일로 보낸다', async (t) => {
    const { worker, env } = await testServer(t, now);
    const response = await worker.fetch(new Request('https://worker.example/invite/k7p2qxm4'), env);
    assert.equal(response.status, 200);
    assert.match(response.headers.get('Content-Type'), /^text\/html/);
    const html = await response.text();
    const apk = 'https://github.com/jkzk1397/sareureu/releases/download/v0.13/sareureu-week13.apk';
    assert.ok(html.includes('K7P2QXM4'));
    assert.ok(html.includes('intent://invite/K7P2QXM4#Intent;scheme=sareureu;package=com.capstone.pause;'));
    // 바로 연결된다고 말하지 않는다(요청 뒤 수락, ADR-0014).
    assert.ok(html.includes('친구가 수락하면 연결돼'));
    assert.ok(html.includes(`S.browser_fallback_url=${encodeURIComponent(apk)}`));
    assert.ok(html.includes(`href="${apk}"`));

    // 헷갈리는 글자(0·O·1·I·L)가 섞인 코드는 없는 코드.
    const bad = await worker.fetch(new Request('https://worker.example/invite/K0P2QXM4'), env);
    assert.equal(bad.status, 404);
    // 예전 6자리 코드도 없는 코드.
    assert.equal((await worker.fetch(new Request('https://worker.example/invite/K7P2QX'), env)).status, 404);
    assert.ok(!(await bad.text()).includes('intent://'));
    assert.equal((await worker.fetch(new Request('https://worker.example/invite/K7P2QXM4', { method: 'POST' }), env))
        .status, 404);

    // 설치 파일 주소는 설정으로 바꿀 수 있지만 https만.
    assert.ok((await invitePage('K7P2QXM4', { APK_URL: 'https://example.com/a.apk' }).text())
        .includes('https://example.com/a.apk'));
    assert.ok((await invitePage('K7P2QXM4', { APK_URL: 'javascript:alert(1)' }).text()).includes(apk));
});

test('6-A: 시청 신호와 친구 목록은 D1 요청 한 번씩이다(새 앱), 옛 앱은 잔소리가 있을 때만 한 번 더', async (t) => {
    const { call, env, sql } = await testServer(t, now);
    await join(call, 'alice', '민지');
    await join(call, 'bob', '준호');
    await befriend(call, 'alice', 'bob');
    const bobId = sql.prepare("SELECT public_id FROM sharing_consent WHERE user_id = 'bob'").get().public_id;

    // D1을 부른 횟수를 센다(batch 한 번, 또는 묶음 밖의 문장 실행 한 번이 요청 한 번).
    let requests = 0;
    const db = env.DB;
    env.DB = {
        prepare(query) {
            const count = (statement) => ({
                ...statement,
                run: (...a) => { requests++; return statement.run(...a); },
                all: (...a) => { requests++; return statement.all(...a); },
                first: (...a) => { requests++; return statement.first(...a); },
            });
            const prepared = db.prepare(query);
            // 묶음에 들어갈 때는 원래 문장을 쓴다(묶음이 한 번으로 센다).
            const bound = (statement) => Object.assign(count(statement), { raw: statement });
            return Object.assign(bound(prepared), { bind: (...values) => bound(prepared.bind(...values)) });
        },
        batch(statements) {
            requests++;
            return db.batch(statements.map((s) => s.raw ?? s));
        },
    };
    const v2 = { 'X-App-Version': '2' };
    const measure = async (run) => {
        requests = 0;
        const response = await run();
        return [response.status, requests];
    };

    assert.deepEqual(await measure(() => call('PUT', '/api/v1/presence', 'bob', { watching: true }, v2)), [200, 1]);
    assert.deepEqual(await measure(() => call('GET', '/api/v1/friends', 'alice', undefined, v2)), [200, 1]);
    assert.deepEqual(await measure(() => call('PUT', '/api/v1/presence', 'carol', { watching: true }, v2)), [403, 1]);
    // 잔소리가 와 있어도, 확인(ack)을 실어도 한 번.
    assert.equal((await call('POST', '/api/v1/pokes', 'alice', { to: bobId, text: '자라' })).status, 201);
    const beat = await call('PUT', '/api/v1/presence', 'bob', { watching: true }, v2);
    assert.deepEqual(await measure(() =>
        call('PUT', '/api/v1/presence', 'bob', { watching: true, ack: [beat.body.pokes[0].id] }, v2)), [200, 1]);
    // 옛 앱(헤더 없음): 잔소리가 없으면 한 번, 있으면 지우느라 한 번 더.
    assert.deepEqual(await measure(() => call('PUT', '/api/v1/presence', 'bob', { watching: true })), [200, 1]);
    assert.equal((await call('POST', '/api/v1/pokes', 'alice', { to: bobId, text: '일어나' })).status, 201);
    assert.deepEqual(await measure(() => call('PUT', '/api/v1/presence', 'bob', { watching: true })), [200, 2]);
});
