import { readdirSync, readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import worker from '../src/index.mjs';

/** migrations/의 SQL을 번호 순서대로 모두 적용한 메모리 SQLite. */
export function migratedSql() {
    const sql = new DatabaseSync(':memory:');
    const dir = new URL('../migrations/', import.meta.url);
    for (const file of readdirSync(dir).filter((name) => name.endsWith('.sql')).sort()) {
        sql.exec(readFileSync(new URL(file, dir), 'utf8'));
    }
    return sql;
}

/** D1 바인딩처럼 동작하는 얇은 감싸개(prepare·bind·first·run·all·batch). */
export function d1(sql) {
    const bound = (statement, values) => ({
        run: () => statement.run(...values),
        all: () => ({ results: statement.all(...values) }),
        first: () => statement.get(...values) ?? null,
    });
    return {
        prepare(query) {
            const statement = sql.prepare(query);
            return { ...bound(statement, []), bind: (...values) => bound(statement, values) };
        },
        batch(statements) {
            sql.exec('BEGIN');
            try {
                // D1 batch처럼 문장마다 { results }를 돌려준다(쓰기 문장은 빈 배열).
                const result = statements.map((s) => s.all());
                sql.exec('COMMIT');
                return result;
            } catch (error) {
                sql.exec('ROLLBACK');
                throw error;
            }
        },
    };
}

// firebase-auth.mjs가 공개 키를 모듈 안에 캐시하므로 시험 파일 하나에서는 같은 키 쌍을 쓴다.
const algorithm = { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' };
const pair = await crypto.subtle.generateKey(algorithm, true, ['sign', 'verify']);
const key = await crypto.subtle.exportKey('jwk', pair.publicKey);

/** 시험용 서버: 가짜 Firebase 키로 토큰을 만들고 요청을 보낸다. */
export async function testServer(t, now) {
    t.mock.method(Date, 'now', () => now);
    const projectId = 'test-project';
    const sql = migratedSql();
    t.after(() => sql.close());
    const db = d1(sql);

    t.mock.method(globalThis, 'fetch', async () => Response.json({ keys: [{ ...key, kid: 'test' }] }, {
        headers: { 'Cache-Control': 'max-age=600' },
    }));
    const encode = (data) => Buffer.from(JSON.stringify(data)).toString('base64url');
    async function token(userId) {
        const seconds = Math.floor(Date.now() / 1000);
        const claims = { aud: projectId, iss: `https://securetoken.google.com/${projectId}`, sub: userId,
            exp: seconds + 300, iat: seconds - 1, auth_time: seconds - 1 };
        const data = `${encode({ alg: 'RS256', kid: 'test' })}.${encode(claims)}`;
        const signature = await crypto.subtle.sign(algorithm.name, pair.privateKey, Buffer.from(data));
        return `${data}.${Buffer.from(signature).toString('base64url')}`;
    }

    /** 요청 하나. 응답 { status, body }. */
    async function call(method, path, userId, body) {
        const headers = { 'Content-Type': 'application/json' };
        if (userId) headers.Authorization = `Bearer ${await token(userId)}`;
        const request = new Request(`https://worker.example${path}`, {
            method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        });
        const response = await worker.fetch(request, { DB: db, FIREBASE_PROJECT_ID: projectId });
        const text = await response.text();
        return { status: response.status, body: text ? JSON.parse(text) : null };
    }

    return { sql, db, call, worker, env: { DB: db, FIREBASE_PROJECT_ID: projectId } };
}
