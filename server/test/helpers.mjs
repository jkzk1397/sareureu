import { readdirSync, readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import worker from '../src/index.mjs';

/** 서버의 자동 마이그레이션(src/migrate.mjs)이 다시 적용하지 않게 wrangler와 같은 기록 표에 남긴다. */
export function markApplied(sql, files) {
    sql.exec(`CREATE TABLE IF NOT EXISTS d1_migrations (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE,
        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)`);
    for (const file of files) sql.prepare('INSERT OR IGNORE INTO d1_migrations (name) VALUES (?)').run(file);
}

/** migrations/의 SQL을 번호 순서대로 모두 적용한 메모리 SQLite. */
export function migratedSql() {
    const sql = new DatabaseSync(':memory:');
    const dir = new URL('../migrations/', import.meta.url);
    const files = readdirSync(dir).filter((name) => name.endsWith('.sql')).sort();
    for (const file of files) sql.exec(readFileSync(new URL(file, dir), 'utf8'));
    markApplied(sql, files);
    return sql;
}

/** D1 바인딩처럼 동작하는 얇은 감싸개(prepare·bind·first·run·all·batch). */
export function d1(sql) {
    // 실제 D1처럼 실행할 때 컴파일한다(같은 batch 안에서 앞 문장이 만든 칸을 뒤 문장이 쓸 수 있게).
    // 실제 D1처럼 결과에 meta.changes(이 문장이 바꾼 행 수)를 붙인다.
    const total = () => sql.prepare('SELECT total_changes() AS n').get().n;
    const all = (query, values) => {
        const before = total();
        const results = sql.prepare(query).all(...values);
        return { results, success: true, meta: { changes: total() - before } };
    };
    const bound = (query, values) => ({
        run: () => all(query, values),
        all: () => all(query, values),
        first: () => sql.prepare(query).get(...values) ?? null,
    });
    return {
        prepare(query) {
            return { ...bound(query, []), bind: (...values) => bound(query, values) };
        },
        batch(statements) {
            sql.exec('BEGIN');
            try {
                // D1 batch처럼 문장마다 { results, meta }를 돌려준다(쓰기 문장은 빈 배열).
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

    const env = { DB: db, FIREBASE_PROJECT_ID: projectId };

    /** 요청 하나. 응답 { status, body, headers }. [extra]는 더 붙일 헤더(예: X-App-Version). */
    async function call(method, path, userId, body, extra = {}) {
        const headers = { 'Content-Type': 'application/json', ...extra };
        if (userId) headers.Authorization = `Bearer ${await token(userId)}`;
        const request = new Request(`https://worker.example${path}`, {
            method, headers, body: body === undefined ? undefined : JSON.stringify(body),
        });
        const response = await worker.fetch(request, env);
        const text = await response.text();
        return { status: response.status, body: text ? JSON.parse(text) : null, headers: response.headers };
    }

    /** [owner]의 초대 코드로 [requester]가 연결을 요청한다. 응답 그대로. */
    async function requestWith(owner, requester) {
        const invite = await call('POST', '/api/v1/invites', owner);
        return call('POST', `/api/v1/invites/${invite.body.code}/requests`, requester);
    }

    /** 요청 → 수락까지 해서 [owner]와 [requester]를 친구로 만든다. 수락 응답을 돌려준다. */
    async function befriend(owner, requester) {
        const requested = await requestWith(owner, requester);
        if (requested.status !== 201) return requested;
        return call('POST', `/api/v1/friend-requests/${requested.body.request.id}/accept`, owner);
    }

    return { sql, db, call, worker, env, requestWith, befriend };
}
