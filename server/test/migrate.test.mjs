import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DatabaseSync } from 'node:sqlite';
import { test } from 'node:test';
import { bundle } from '../scripts/bundle-migrations.mjs';
import { ensureMigrated, resetMigrationState, statements } from '../src/migrate.mjs';
import { d1 } from './helpers.mjs';

test('src/migrations.mjs가 migrations/*.sql과 같다(다르면 node scripts/bundle-migrations.mjs)', () => {
    assert.equal(readFileSync(new URL('../src/migrations.mjs', import.meta.url), 'utf8'), bundle());
});

test('SQL 문장 나누기: 주석 줄을 빼고 세미콜론으로', () => {
    assert.deepEqual(statements('-- 설명; 무시\nCREATE TABLE a (x INTEGER);\n\nCREATE INDEX i ON a (x);\n'),
        ['CREATE TABLE a (x INTEGER)', 'CREATE INDEX i ON a (x)']);
});

test('지금 배포된 서버처럼 첫 표만 있고 기록 표가 없어도 나머지를 한 번만 적용한다', async () => {
    const sql = new DatabaseSync(':memory:');
    sql.exec(readFileSync(new URL('../migrations/0001_daily_watch.sql', import.meta.url), 'utf8'));
    sql.prepare(`INSERT INTO sharing_consent (user_id, version, granted_at_ms) VALUES ('old', 1, 1)`).run();
    const db = d1(sql);

    resetMigrationState();
    await ensureMigrated(db);
    const names = sql.prepare('SELECT name FROM d1_migrations ORDER BY name').all().map((r) => r.name);
    assert.deepEqual(names, ['0001_daily_watch.sql', '0002_sharing_profile.sql', '0003_friends.sql',
        '0004_pokes.sql', '0005_peer_stats.sql', '0006_streak_presence.sql', '0007_friend_requests.sql']);
    for (const table of ['standing', 'invite', 'friendship', 'block', 'poke', 'peer_stat', 'presence',
        'friend_request', 'rate_event', 'idempotency', 'report']) {
        assert.ok(sql.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").get(table), table);
    }
    // 있던 행은 그대로.
    assert.equal(sql.prepare('SELECT user_id FROM sharing_consent').get().user_id, 'old');

    // 다시 해도(새 Worker 인스턴스) 겹치지 않는다.
    resetMigrationState();
    await ensureMigrated(db);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM d1_migrations').get().n, 7);
    sql.close();
});

test('0007: 배포된 서버에 살아 있던 초대 코드·한도 기록 표를 새 모양으로 바꾼다(다른 표의 행은 그대로)', async () => {
    const sql = new DatabaseSync(':memory:');
    const dir = new URL('../migrations/', import.meta.url);
    const before = ['0001_daily_watch.sql', '0002_sharing_profile.sql', '0003_friends.sql', '0004_pokes.sql',
        '0005_peer_stats.sql', '0006_streak_presence.sql'];
    for (const file of before) sql.exec(readFileSync(new URL(file, dir), 'utf8'));
    sql.exec(`
        INSERT INTO sharing_consent (user_id, version, granted_at_ms, public_id, nickname) VALUES
            ('a', 2, 1, 'pa', '민지'), ('b', 2, 1, 'pb', '준호');
        INSERT INTO invite (code, user_id, expires_at_ms) VALUES ('ABCDEF', 'a', 9999999999999);
        INSERT INTO friendship (user_a, user_b, created_at_ms) VALUES ('a', 'b', 1);
        INSERT INTO poke (id, from_user, to_user, text, created_at_ms, expires_at_ms) VALUES ('p', 'a', 'b', '자라', 1, 2);
        INSERT INTO poke_sent (from_user, sent_at_ms) VALUES ('a', 1);
    `);
    sql.exec(`CREATE TABLE d1_migrations (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT UNIQUE,
        applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL)`);
    for (const file of before) sql.prepare('INSERT INTO d1_migrations (name) VALUES (?)').run(file);

    resetMigrationState();
    await ensureMigrated(d1(sql));
    // 옛 코드는 버리고(해시로 바꿀 수 없다), 친구 연결과 잔소리는 그대로.
    assert.deepEqual(Object.keys(sql.prepare("SELECT * FROM pragma_table_info('invite')").all()
        .reduce((o, c) => ({ ...o, [c.name]: 1 }), {})).sort(), ['code_hash', 'expires_at_ms', 'user_id']);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM invite').get().n, 0);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM friendship').get().n, 1);
    assert.equal(sql.prepare('SELECT text FROM poke').get().text, '자라');
    assert.equal(sql.prepare("SELECT 1 AS gone FROM sqlite_master WHERE name = 'poke_sent'").get(), undefined);
    sql.close();
});

test('빈 데이터베이스면 첫 표부터 모두 만든다', async () => {
    const sql = new DatabaseSync(':memory:');
    resetMigrationState();
    await ensureMigrated(d1(sql));
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM d1_migrations').get().n, 7);
    assert.ok(sql.prepare("SELECT 1 FROM sqlite_master WHERE name = 'daily_watch'").get());
    sql.close();
});
