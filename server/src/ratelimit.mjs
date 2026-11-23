// 계정 기준 한도. '세고 → 넣기'를 한 문장으로 해서 두 요청이 동시에 와도 한도를 넘지 않는다(ADR-0014).
// D1은 쓰기를 한 줄로 세우므로 INSERT … SELECT … WHERE (개수 < 한도)가 원자적이다.

/** 한도 조건 SQL 조각과 값. [windows] = [{ windowMs, max }, …]. */
export function underLimit(userId, kind, now, windows) {
    return {
        sql: windows.map(() =>
            '(SELECT COUNT(*) FROM rate_event WHERE user_id = ? AND kind = ? AND at_ms > ?) < ?').join(' AND '),
        binds: windows.flatMap(({ windowMs, max }) => [userId, kind, now - windowMs, max]),
    };
}

/** 한도가 풀릴 때까지 남은 초(올림, 1 이상). */
export async function retryAfterSec(db, userId, kind, now, windows) {
    let waitMs = 0;
    for (const { windowMs, max } of windows) {
        // 창 안에서 max번째로 최근 사건이 창을 벗어나야 자리가 난다.
        const row = await db.prepare(`
            SELECT at_ms FROM rate_event WHERE user_id = ? AND kind = ? AND at_ms > ?
            ORDER BY at_ms DESC LIMIT 1 OFFSET ?
        `).bind(userId, kind, now - windowMs, max - 1).first();
        if (row) waitMs = Math.max(waitMs, row.at_ms + windowMs - now);
    }
    return Math.max(1, Math.ceil(waitMs / 1000));
}

/**
 * 한 번 쓴다. 한도 안이면 사건을 적고 `{ ok: true }`, 넘었으면 적지 않고 `{ ok: false, retryAfterSec }`.
 */
export async function take(db, userId, kind, now, windows) {
    const guard = underLimit(userId, kind, now, windows);
    const { meta } = await db.prepare(`INSERT INTO rate_event (user_id, kind, at_ms) SELECT ?, ?, ? WHERE ${guard.sql}`)
        .bind(userId, kind, now, ...guard.binds).run();
    if (meta.changes > 0) return { ok: true };
    return { ok: false, retryAfterSec: await retryAfterSec(db, userId, kind, now, windows) };
}

/** [take]로 적은 사건 하나를 되돌린다(틀린 코드가 아니었을 때). */
export async function refund(db, userId, kind, now) {
    await db.prepare(`
        DELETE FROM rate_event WHERE rowid = (
            SELECT rowid FROM rate_event WHERE user_id = ? AND kind = ? AND at_ms = ? LIMIT 1)
    `).bind(userId, kind, now).run();
}

/** cron: 하루 지난 사건과 멱등 키 정리(가장 긴 창이 하루다). */
export async function cleanRateEvents(db, now, dayMs) {
    await db.prepare('DELETE FROM rate_event WHERE at_ms <= ?').bind(now - dayMs).run();
    await db.prepare('DELETE FROM idempotency WHERE at_ms <= ?').bind(now - dayMs).run();
}
