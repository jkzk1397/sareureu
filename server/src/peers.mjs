// 또래 비교(연령대·신분별 하루 평균 시청 분). 동의한 사용자의 최근 하루 합계로 사람마다 하루 평균을 내고,
// 그룹마다 사람 수·중앙값·상위 25%만 남긴다. 10명 미만 그룹은 내려 주지 않고, 개인별 값은 저장하지도 내려 주지도 않는다.
// 5-C 보호: 유효한 날이 7일 이상인 계정만 세고, 한 그룹의 값이 하루 사이 크게 바뀌면 그날은 갱신하지 않는다.
import { error, json } from './http.mjs';
import { limits } from './limits.mjs';

const dayMs = 86_400_000;
export const minGroup = 10;

function utcDate(timestamp) {
    return new Date(timestamp).toISOString().slice(0, 10);
}

/** 정렬된 값의 백분위(가장 가까운 순위). */
export function percentile(sorted, p) {
    const rank = Math.max(1, Math.ceil(p * sorted.length));
    return sorted[rank - 1];
}

/** 사람별 평균 목록 → 그룹 집계(10명 이상만). 순수 함수. */
export function aggregate(rows) {
    const groups = new Map();
    for (const { ageBand, occupation, avg } of rows) {
        const key = `${ageBand}|${occupation}`;
        if (!groups.has(key)) groups.set(key, { ageBand, occupation, values: [] });
        groups.get(key).values.push(avg);
    }
    return [...groups.values()]
        .filter(({ values }) => values.length >= minGroup)
        .map(({ ageBand, occupation, values }) => {
            const sorted = [...values].sort((a, b) => a - b);
            return {
                ageBand, occupation, n: sorted.length,
                p50: Math.round(percentile(sorted, 0.5)), p75: Math.round(percentile(sorted, 0.75)),
            };
        });
}

/**
 * 하루 사이에 너무 크게 바뀐 값인지(순수). 가짜 계정 몇 개나 잘못된 업로드가 그룹 값을 한 번에 흔들지 못하게,
 * 중앙값이 30%(적어도 15분) 넘게 바뀌면 그날은 예전 값을 둔다. 예전 값이 3일 넘게 묵었으면 새 값을 받아들인다
 * (실제로 바뀐 것일 수 있으므로).
 */
export function tooBigAJump(previous, next, now) {
    if (!previous || now - previous.computedAtMs > limits.peerHoldMs) return false;
    const allowed = Math.max(limits.peerJumpMinutes, previous.p50 * limits.peerJumpRatio);
    return Math.abs(next.p50 - previous.p50) > allowed;
}

/**
 * cron: 그룹 집계를 다시 계산한다. 읽는 양은 최근 14일 창의 행뿐이다(`daily_watch_by_date` 인덱스) — 전체를 다시 읽지 않는다.
 * 사용자 1명당 하루 약 30행(창 14일 × 인덱스·본문 + 동의 행)이라 D1 무료 읽기 한도(하루 500만 행)는 약 16만 명에서 찬다
 * (server/README.md "무료 한도"). 바뀐 그룹만 쓴다.
 */
export async function computePeerStats(db, now) {
    const from = utcDate(now - limits.peerWindowDays * dayMs);
    const [fresh, stored] = await db.batch([
        // 유효한 날(기록된 비율을 모르거나 80% 이상)이 7일 이상인 계정만 센다: 며칠만 쓴 계정이나 감지를 꺼 둔 날이 값을 흔들지 않게.
        db.prepare(`
            SELECT c.age_band AS ageBand, c.occupation AS occupation, AVG(d.shortform_minutes) AS avg
            FROM daily_watch d JOIN sharing_consent c ON c.user_id = d.user_id
            WHERE d.watch_date >= ? AND c.age_band IS NOT NULL AND c.occupation IS NOT NULL
              AND (d.coverage IS NULL OR d.coverage >= ?)
            GROUP BY d.user_id HAVING COUNT(*) >= ?
        `).bind(from, limits.peerMinCoverage, limits.peerMinDays),
        db.prepare('SELECT age_band AS ageBand, occupation, p50, computed_at_ms AS computedAtMs FROM peer_stat').bind(),
    ]);
    const stats = aggregate(fresh.results);
    const previous = new Map(stored.results.map((row) => [`${row.ageBand}|${row.occupation}`, row]));
    const keep = new Set(stats.map((s) => `${s.ageBand}|${s.occupation}`));
    const writes = [];
    // 10명 미만이 됐거나 사라진 그룹은 바로 지운다(사람 수 보호가 먼저다).
    for (const [key, row] of previous) {
        if (!keep.has(key)) {
            writes.push(db.prepare('DELETE FROM peer_stat WHERE age_band = ? AND occupation = ?')
                .bind(row.ageBand, row.occupation));
        }
    }
    for (const s of stats) {
        if (tooBigAJump(previous.get(`${s.ageBand}|${s.occupation}`), s, now)) continue;
        writes.push(db.prepare(`
            INSERT INTO peer_stat (age_band, occupation, n, p50, p75, computed_at_ms) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (age_band, occupation) DO UPDATE SET
                n = excluded.n, p50 = excluded.p50, p75 = excluded.p75, computed_at_ms = excluded.computed_at_ms
        `).bind(s.ageBand, s.occupation, s.n, s.p50, s.p75, now));
    }
    if (writes.length) await db.batch(writes);
    return stats.length;
}

async function listPeers({ db, userId }) {
    const me = await db.prepare('SELECT 1 FROM sharing_consent WHERE user_id = ?').bind(userId).first();
    if (!me) return error('sharing_consent_required', 403);
    const { results } = await db.prepare(`
        SELECT age_band AS ageBand, occupation, n, p50, p75, computed_at_ms AS computedAtMs
        FROM peer_stat WHERE n >= ? ORDER BY age_band, occupation
    `).bind(minGroup).all();
    return json({
        groups: results.map(({ computedAtMs, ...group }) => group),
        computedAtMs: results.length ? Math.max(...results.map((r) => r.computedAtMs)) : null,
    });
}

/** 또래 경로표(index.mjs가 붙인다). */
export const peerRoutes = [
    { pattern: /^\/api\/v1\/peers$/, methods: ['GET'], handle: listPeers },
];
