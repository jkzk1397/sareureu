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
        '0004_pokes.sql', '0005_peer_stats.sql']);
    for (const table of ['standing', 'invite', 'friendship', 'block', 'poke', 'poke_sent', 'peer_stat']) {
        assert.ok(sql.prepare("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?").get(table), table);
    }
    // 있던 행은 그대로.
    assert.equal(sql.prepare('SELECT user_id FROM sharing_consent').get().user_id, 'old');

    // 다시 해도(새 Worker 인스턴스) 겹치지 않는다.
    resetMigrationState();
    await ensureMigrated(db);
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM d1_migrations').get().n, 5);
    sql.close();
});

test('빈 데이터베이스면 첫 표부터 모두 만든다', async () => {
    const sql = new DatabaseSync(':memory:');
    resetMigrationState();
    await ensureMigrated(d1(sql));
    assert.equal(sql.prepare('SELECT COUNT(*) AS n FROM d1_migrations').get().n, 5);
    assert.ok(sql.prepare("SELECT 1 FROM sqlite_master WHERE name = 'daily_watch'").get());
    sql.close();
});
