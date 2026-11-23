// 6단계(ADR-0012): 지금 시청 중. 숏폼을 보는 동안 앱이 몇 초마다 `{"watching":true}`를 보내고, 멈추면 `false`를 보낸다.
// 서버는 마지막 신호 시각만 남긴다(앱·영상·시간은 받지 않는다). 최근 5초 안에 신호가 있으면 '시청 중'.
// 응답에 새 잔소리를 실어 보내서, 받는 사람이 FCM 없이 몇 초 안에 알림을 받는다.
import { error, json, readBody } from './http.mjs';
import { takePokes } from './pokes.mjs';

/** 이 시간 안에 신호가 있으면 시청 중. */
export const watchingWindowMs = 5_000;
// 시청 중 표시는 동의 버전 2부터(ADR-0012).
const presenceConsentVersion = 2;

/** 최근 [watchingWindowMs] 안에 시청 신호가 있었는지. */
export async function isWatching(db, userId, now) {
    return !!await db.prepare('SELECT 1 FROM presence WHERE user_id = ? AND last_seen_ms > ?')
        .bind(userId, now - watchingWindowMs).first();
}

async function updatePresence({ request, db, userId, now }) {
    const body = await readBody(request);
    if (!body || Object.keys(body).some((key) => key !== 'watching') || typeof body.watching !== 'boolean') {
        return error('invalid_presence', 400);
    }
    const consent = await db.prepare('SELECT 1 FROM sharing_consent WHERE user_id = ? AND version >= ?')
        .bind(userId, presenceConsentVersion).first();
    if (!consent) return error('sharing_consent_required', 403);
    if (body.watching) {
        await db.prepare(`
            INSERT INTO presence (user_id, last_seen_ms) VALUES (?, ?)
            ON CONFLICT (user_id) DO UPDATE SET last_seen_ms = MAX(presence.last_seen_ms, excluded.last_seen_ms)
        `).bind(userId, now).run();
    } else {
        await db.prepare('DELETE FROM presence WHERE user_id = ?').bind(userId).run();
    }
    return json({ pokes: await takePokes(db, userId, now) });
}

/** 시청 중 경로표(index.mjs가 붙인다). */
export const presenceRoutes = [
    { pattern: /^\/api\/v1\/presence$/, methods: ['PUT'], handle: updatePresence },
];

/** cron: 멈춤 신호 없이 끊긴(앱 종료·네트워크) 오래된 행 정리. */
export async function cleanPresence(db, now) {
    await db.prepare('DELETE FROM presence WHERE last_seen_ms <= ?').bind(now - watchingWindowMs).run();
}
