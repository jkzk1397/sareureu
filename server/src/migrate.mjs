// 서버가 스스로 D1 표를 맞춘다: 아직 적용하지 않은 migrations/*.sql을 순서대로 적용한다.
// 기록은 wrangler와 같은 d1_migrations 표에 남겨서, 나중에 `wrangler d1 migrations apply`를 해도 겹치지 않는다.
// 그래서 배포하는 사람이 Cloudflare에 따로 로그인해 마이그레이션을 돌리지 않아도 된다.
import { migrations } from './migrations.mjs';

let ready = null;

/** SQL 파일을 문장 단위로 나눈다(주석 줄 제거, 세미콜론 기준. 우리 SQL에는 문자열 안 세미콜론이 없다). */
export function statements(sql) {
    return sql.split('\n').filter((line) => !line.trim().startsWith('--')).join('\n')
        .split(';').map((s) => s.trim()).filter(Boolean);
}

async function applied(db) {
    await db.prepare(`CREATE TABLE IF NOT EXISTS d1_migrations (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT UNIQUE,
        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
    )`).bind().run();
    const { results } = await db.prepare('SELECT name FROM d1_migrations').bind().all();
    return new Set(results.map((r) => r.name));
}

async function tableExists(db, name) {
    return !!await db.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").bind(name).first();
}

async function run(db) {
    let done = await applied(db);
    // 첫 표를 wrangler 없이 손으로 만든 경우: 이미 있으면 적용한 것으로 친다.
    if (!done.has('0001_daily_watch.sql') && await tableExists(db, 'daily_watch')) {
        await db.prepare('INSERT OR IGNORE INTO d1_migrations (name) VALUES (?)').bind('0001_daily_watch.sql').run();
        done = await applied(db);
    }
    for (const { name, sql } of migrations) {
        if (done.has(name)) continue;
        try {
            // 한 파일은 한 번에(중간에 실패하면 전부 되돌린다). 이름이 UNIQUE라 두 곳에서 동시에 해도 한 번만 된다.
            await db.batch([
                ...statements(sql).map((s) => db.prepare(s).bind()),
                db.prepare('INSERT INTO d1_migrations (name) VALUES (?)').bind(name),
            ]);
        } catch (error) {
            if (!(await applied(db)).has(name)) throw error;
        }
    }
}

/** 요청을 처리하기 전에 한 번(Worker 인스턴스마다). 실패하면 다음 요청에서 다시 해 본다. */
export function ensureMigrated(db) {
    if (!ready) ready = run(db).catch((error) => {
        ready = null;
        throw error;
    });
    return ready;
}

/** 시험용: 다시 확인하게 한다. */
export function resetMigrationState() {
    ready = null;
}
