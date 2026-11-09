import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import worker from '../src/index.mjs';

test('동의한 계정의 일일 합계만 저장하고 철회하면 삭제한다', async (t) => {
    const now = Date.parse('2026-11-09T18:59:00.000Z'); // 한국 시간 03:59
    t.mock.method(Date, 'now', () => now);
    const projectId = 'test-project';
    const sql = new DatabaseSync(':memory:');
    t.after(() => sql.close());
    sql.exec(readFileSync(new URL('../migrations/0001_daily_watch.sql', import.meta.url), 'utf8'));
    const db = {
        prepare(query) {
            const statement = sql.prepare(query);
            return {
                first: () => statement.get(),
                bind: (...values) => ({
                    run: () => statement.run(...values),
                    all: () => ({ results: statement.all(...values) }),
                }),
            };
        },
        batch([read, write]) {
            sql.exec('BEGIN');
            try {
                const result = [read.all(), write.run()];
                sql.exec('COMMIT');
                return result;
            } catch (error) {
                sql.exec('ROLLBACK');
                throw error;
            }
        },
    };

    const algorithm = { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
        publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' };
    const pair = await crypto.subtle.generateKey(algorithm, true, ['sign', 'verify']);
    const key = await crypto.subtle.exportKey('jwk', pair.publicKey);
    t.mock.method(globalThis, 'fetch', async () => Response.json({ keys: [{ ...key, kid: 'test' }] }, {
        headers: { 'Cache-Control': 'max-age=600' },
    }));
    const encode = (data) => Buffer.from(JSON.stringify(data)).toString('base64url');
    async function token(userId, override = {}) {
        const seconds = now / 1000;
        const claims = { aud: projectId, iss: `https://securetoken.google.com/${projectId}`, sub: userId,
            exp: seconds + 300, iat: seconds - 1, auth_time: seconds - 1, ...override };
        const data = `${encode({ alg: 'RS256', kid: 'test' })}.${encode(claims)}`;
        const signature = await crypto.subtle.sign(algorithm.name, pair.privateKey, Buffer.from(data));
        return `${data}.${Buffer.from(signature).toString('base64url')}`;
    }

    const alice = await token('alice');
    const bob = await token('bob');
    const wrongProject = await token('alice', { aud: 'other-project' });
    const expired = await token('alice', { exp: now / 1000 });
    const call = async (method, path, credential, body) => {
        const request = new Request(`https://worker.example${path}`, {
            method, body: JSON.stringify(body), headers: {
                Authorization: `Bearer ${credential}`, 'Content-Type': 'application/json',
            },
        });
        return (await worker.fetch(request, { DB: db, FIREBASE_PROJECT_ID: projectId })).status;
    };
    const daily = '/api/v1/daily/2026-11-09';
    const sharing = '/api/v1/sharing';
    const body = { shortformMinutes: 30, coverage: null, measuredAtMs: now, timeZone: 'Asia/Seoul' };
    const stored = () => sql.prepare('SELECT * FROM daily_watch ORDER BY user_id').all();
    assert.equal(await call('GET', '/health'), 204);
    for (const invalid of [wrongProject, expired, `${alice.split('.').slice(0, 2).join('.')}.dGVzdA`]) {
        assert.equal(await call('PUT', daily, invalid, body), 401);
    }
    assert.equal(await call('PUT', daily, alice, body), 403);
    assert.equal(await call('PUT', sharing, alice, { consentVersion: 1 }), 204);
    assert.equal(await call('PUT', daily, alice, { ...body, startedAt: now }), 400);
    assert.equal(await call('PUT', daily, alice, { ...body, coverage: 1.2 }), 400);
    assert.equal(await call('PUT', daily, alice, { ...body, measuredAtMs: now - 1 }), 403);
    assert.equal(await call('PUT', '/api/v1/daily/2026-11-10', alice, body), 400);
    assert.equal(await call('PUT', '/api/v1/daily/2026-02-30', alice, body), 400);
    assert.equal(await call('PUT', daily, alice, body), 204);
    assert.equal(await call('PUT', daily, alice, { ...body, shortformMinutes: 90 }), 204);
    assert.equal(await call('PUT', daily, bob, body), 403);
    assert.equal(await call('PUT', sharing, bob, { consentVersion: 1 }), 204);
    assert.equal(await call('PUT', daily, bob, { ...body, shortformMinutes: 10 }), 204);
    assert.deepEqual(stored().map((row) => [row.user_id, row.shortform_minutes, row.saved_at_ms]),
        [['alice', 30, now], ['bob', 10, now]]);

    Date.now.mock.mockImplementation(() => now + 60_001);
    assert.equal(await call('PUT', daily, alice, { ...body, shortformMinutes: 40, measuredAtMs: now + 60_000 }), 204);
    assert.equal(await call('PUT', daily, alice, body), 204);
    assert.equal(stored()[0].shortform_minutes, 40);
    assert.equal(await call('PUT', '/api/v1/daily/2026-11-10', alice, { ...body, measuredAtMs: now + 60_000 }), 204);

    assert.equal(await call('DELETE', sharing, alice), 204);
    assert.equal(await call('PUT', daily, alice, body), 403);
    assert.deepEqual(stored().map((row) => row.user_id), ['bob']);
    await worker.scheduled({ scheduledTime: now + 367 * 86_400_000 }, { DB: db });
    assert.deepEqual(stored(), []);
});
