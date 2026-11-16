// 임시(5-5): 또래 비교(연령대·신분별 하루 평균 시청 분). 팀원 서버가 바뀌면 달라질 수 있다(server/README.md).
// 동의한 사용자의 최근 7일 하루 합계로 사람마다 하루 평균을 내고, 그룹마다 중앙값·상위 25%만 남긴다.
// 10명 미만 그룹은 내려 주지 않고, 개인별 값은 저장하지도 내려 주지도 않는다.
import { error, json } from './http.mjs';

const dayMs = 86_400_000;
export const minGroup = 10;
const windowDays = 7;
const minDaysPerUser = 3;

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

/** cron: 그룹 집계를 다시 계산해서 통째로 바꾼다. */
export async function computePeerStats(db, now) {
    const from = utcDate(now - windowDays * dayMs);
    const { results } = await db.prepare(`
        SELECT c.age_band AS ageBand, c.occupation AS occupation, AVG(d.shortform_minutes) AS avg
        FROM daily_watch d JOIN sharing_consent c ON c.user_id = d.user_id
        WHERE d.watch_date >= ? AND c.age_band IS NOT NULL AND c.occupation IS NOT NULL
        GROUP BY d.user_id HAVING COUNT(*) >= ?
    `).bind(from, minDaysPerUser).all();
    const stats = aggregate(results);
    const clear = db.prepare('DELETE FROM peer_stat').bind();
    if (stats.length === 0) {
        await clear.run();
        return 0;
    }
    await db.batch([
        clear,
        ...stats.map((s) => db.prepare(`
            INSERT INTO peer_stat (age_band, occupation, n, p50, p75, computed_at_ms) VALUES (?, ?, ?, ?, ?, ?)
        `).bind(s.ageBand, s.occupation, s.n, s.p50, s.p75, now)),
    ]);
    return stats.length;
}

async function listPeers({ db, userId }) {
    const me = await db.prepare('SELECT 1 FROM sharing_consent WHERE user_id = ?').bind(userId).first();
    if (!me) return error('sharing_consent_required', 403);
    const { results } = await db.prepare(`
        SELECT age_band AS ageBand, occupation, n, p50, p75, computed_at_ms AS computedAtMs
        FROM peer_stat WHERE n >= ? ORDER BY age_band, occupation
    `).bind(minGroup).all();
    return json({ groups: results.map(({ computedAtMs, ...group }) => group),
        computedAtMs: results[0]?.computedAtMs ?? null });
}

/** 또래 경로표(index.mjs가 붙인다). */
export const peerRoutes = [
    { pattern: /^\/api\/v1\/peers$/, methods: ['GET'], handle: listPeers },
];
