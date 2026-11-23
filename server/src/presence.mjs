// 지금 시청 중(ADR-0012). 숏폼을 보는 동안 앱이 몇 초마다 `{"watching":true}`를 보내고, 멈추면 `false`를 보낸다.
// 서버는 마지막 신호 시각만 남긴다(앱·영상·시간은 받지 않는다). 최근 [watchingWindowMs] 안에 신호가 있으면 '시청 중'.
// 응답에 새 잔소리를 실어 보내서, 받는 사람이 FCM 없이 몇 초 안에 알림을 받는다.
// ADR-0014: 앱이 받은 잔소리 id를 다음 신호의 `ack`에 실어 보내면 그때 서버에서 지운다.
import { error, json, readBody } from './http.mjs';
import { limits } from './limits.mjs';
import { ackIds, takePokes } from './pokes.mjs';

/** 이 시간 안에 신호가 있으면 시청 중. */
export const watchingWindowMs = 5_000;
// 시청 중 표시는 동의 버전 2부터(ADR-0012).
const presenceConsentVersion = 2;

/** 최근 [watchingWindowMs] 안에 시청 신호가 있었는지. */
export async function isWatching(db, userId, now) {
    return !!await db.prepare('SELECT 1 FROM presence WHERE user_id = ? AND last_seen_ms > ?')
        .bind(userId, now - watchingWindowMs).first();
}

async function updatePresence({ request, db, userId, now, appVersion }) {
    const body = await readBody(request);
    if (!body || Object.keys(body).some((key) => !['watching', 'ack'].includes(key)) ||
        typeof body.watching !== 'boolean' || (body.ack !== undefined && !Array.isArray(body.ack))) {
        return error('invalid_presence', 400);
    }
    // 동의(버전 2 이상) 확인과 신호 저장을 한 문장으로: 동의가 없으면 아무것도 바뀌지 않는다.
    const write = body.watching ? db.prepare(`
        INSERT INTO presence (user_id, last_seen_ms)
        SELECT user_id, ? FROM sharing_consent WHERE user_id = ? AND version >= ?
        ON CONFLICT (user_id) DO UPDATE SET last_seen_ms = MAX(presence.last_seen_ms, excluded.last_seen_ms)
    `).bind(now, userId, presenceConsentVersion)
        : db.prepare('DELETE FROM presence WHERE user_id = ?').bind(userId);
    const [consent] = await db.batch([
        db.prepare('SELECT 1 FROM sharing_consent WHERE user_id = ? AND version >= ?').bind(userId, presenceConsentVersion),
        write,
    ]);
    if (!consent.results.length) return error('sharing_consent_required', 403);
    const pokes = await takePokes(db, userId, now, {
        ack: ackIds(body.ack), keep: appVersion >= limits.ackFromVersion,
    });
    return json({ pokes });
}

/** 시청 중 경로표(index.mjs가 붙인다). */
export const presenceRoutes = [
    { pattern: /^\/api\/v1\/presence$/, methods: ['PUT'], handle: updatePresence },
];

/** cron: 멈춤 신호 없이 끊긴(앱 종료·네트워크) 오래된 행 정리. */
export async function cleanPresence(db, now) {
    await db.prepare('DELETE FROM presence WHERE last_seen_ms <= ?').bind(now - watchingWindowMs).run();
}
