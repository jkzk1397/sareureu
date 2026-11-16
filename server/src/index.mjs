import { verifyFirebaseUser } from './firebase-auth.mjs';
import { friendRoutes } from './friends.mjs';
import { error, noContent, readBody } from './http.mjs';
import { computePeerStats, peerRoutes } from './peers.mjs';
import { cleanPokes, pokeRoutes } from './pokes.mjs';

const consentVersion = 1;
const dayMs = 86_400_000;
const retentionDays = 365;

function utcDate(timestamp) {
    return new Date(timestamp).toISOString().slice(0, 10);
}

function logicalDate(timestamp, timeZone) {
    const parts = new Intl.DateTimeFormat('en-GB', {
        timeZone, year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', hourCycle: 'h23',
    }).formatToParts(timestamp);
    const { year, month, day, hour } = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
    const date = `${year}-${month}-${day}`;
    return Number(hour) < 4 ? utcDate(Date.parse(date) - dayMs) : date;
}

function validDaily(body, date, now) {
    if (!body || Object.keys(body).some((key) =>
        !['shortformMinutes', 'coverage', 'measuredAtMs', 'timeZone'].includes(key))) return false;
    const { shortformMinutes, coverage, measuredAtMs, timeZone } = body;
    if (!Number.isInteger(shortformMinutes) || shortformMinutes < 0 || shortformMinutes > 1500 ||
        (coverage !== null && (!Number.isFinite(coverage) || coverage < 0 || coverage > 1)) ||
        !Number.isSafeInteger(measuredAtMs) || measuredAtMs < now - retentionDays * dayMs || measuredAtMs > now ||
        typeof timeZone !== 'string' || timeZone.length === 0 || timeZone.length > 64) return false;

    const timestamp = Date.parse(date);
    if (!Number.isFinite(timestamp) || utcDate(timestamp) !== date || date < utcDate(now - retentionDays * dayMs)) {
        return false;
    }
    try {
        return date <= logicalDate(measuredAtMs, timeZone);
    } catch {
        return false;
    }
}

// 임시(5-2): 동의할 때 닉네임(친구에게 보임)과 연령대·신분(또래 그룹 집계용)을 함께 받는다. 실명·연락처는 받지 않는다.
const maxNickname = 12;
const enumName = /^[A-Z][A-Z0-9_]{0,31}$/;

function validSharing(body) {
    if (!body || body.consentVersion !== consentVersion || Object.keys(body).some((key) =>
        !['consentVersion', 'nickname', 'ageBand', 'occupation'].includes(key))) return false;
    const { nickname = null, ageBand = null, occupation = null } = body;
    if (nickname !== null && (typeof nickname !== 'string' || nickname.trim().length === 0 ||
        [...nickname.trim()].length > maxNickname)) return false;
    return [ageBand, occupation].every((value) => value === null || (typeof value === 'string' && enumName.test(value)));
}

async function updateSharing(request, db, userId, now) {
    if (request.method === 'DELETE') {
        // 동의 행을 지우면 일일 합계·랭킹 숫자 등 이 사용자의 행이 함께 지워진다(ON DELETE CASCADE).
        await db.prepare('DELETE FROM sharing_consent WHERE user_id = ?').bind(userId).run();
        return noContent();
    }
    const body = await readBody(request);
    if (!validSharing(body)) return error('invalid_consent', 400);
    const nickname = body.nickname?.trim() ?? null;
    await db.prepare(`
        INSERT INTO sharing_consent (user_id, version, granted_at_ms, public_id, nickname, age_band, occupation)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET
            granted_at_ms = CASE WHEN excluded.version > sharing_consent.version
                THEN excluded.granted_at_ms ELSE sharing_consent.granted_at_ms END,
            version = MAX(sharing_consent.version, excluded.version),
            public_id = COALESCE(sharing_consent.public_id, excluded.public_id),
            nickname = excluded.nickname,
            age_band = excluded.age_band,
            occupation = excluded.occupation
    `).bind(userId, consentVersion, now, crypto.randomUUID(), nickname, body.ageBand ?? null,
        body.occupation ?? null).run();
    return noContent();
}

function validStanding(body, now) {
    if (!body || Object.keys(body).some((key) =>
        !['reductionRate', 'coverage', 'validDays', 'measuredAtMs'].includes(key))) return false;
    const { reductionRate, coverage, validDays, measuredAtMs } = body;
    return (reductionRate === null || (Number.isFinite(reductionRate) && reductionRate >= -10 && reductionRate <= 1)) &&
        (coverage === null || (Number.isFinite(coverage) && coverage >= 0 && coverage <= 1)) &&
        Number.isInteger(validDays) && validDays >= 0 && validDays <= 7 &&
        Number.isSafeInteger(measuredAtMs) && measuredAtMs > now - dayMs && measuredAtMs <= now;
}

// 임시(5-2): 랭킹용 내 숫자. 계산은 앱(core:insights)이 하고 서버는 저장만 한다.
async function saveStanding(request, db, userId, now) {
    const body = await readBody(request);
    if (!validStanding(body, now)) return error('invalid_standing', 400);
    const { reductionRate, coverage, validDays, measuredAtMs } = body;
    const consent = db.prepare('SELECT user_id FROM sharing_consent WHERE user_id = ? AND granted_at_ms <= ?')
        .bind(userId, measuredAtMs);
    const upsert = db.prepare(`
        INSERT INTO standing (user_id, reduction_rate, coverage, valid_days, measured_at_ms)
        SELECT user_id, ?, ?, ?, ? FROM sharing_consent WHERE user_id = ? AND granted_at_ms <= ?
        ON CONFLICT (user_id) DO UPDATE SET
            reduction_rate = excluded.reduction_rate,
            coverage = excluded.coverage,
            valid_days = excluded.valid_days,
            measured_at_ms = excluded.measured_at_ms
        WHERE excluded.measured_at_ms > standing.measured_at_ms
    `).bind(reductionRate, coverage, validDays, measuredAtMs, userId, measuredAtMs);
    const [permission] = await db.batch([consent, upsert]);
    return permission.results.length ? noContent() : error('sharing_consent_required', 403);
}

async function saveDaily(request, db, userId, date, now) {
    const body = await readBody(request);
    if (!validDaily(body, date, now)) return error('invalid_daily_summary', 400);
    const { shortformMinutes, coverage, measuredAtMs } = body;

    const consent = db.prepare(`
        SELECT user_id FROM sharing_consent WHERE user_id = ? AND version = ? AND granted_at_ms <= ?
    `).bind(userId, consentVersion, measuredAtMs);
    const upsert = db.prepare(`
        INSERT INTO daily_watch (user_id, watch_date, shortform_minutes, coverage, measured_at_ms, saved_at_ms)
        SELECT user_id, ?, ?, ?, ?, ? FROM sharing_consent
        WHERE user_id = ? AND version = ? AND granted_at_ms <= ?
        ON CONFLICT (user_id, watch_date) DO UPDATE SET
            shortform_minutes = excluded.shortform_minutes,
            coverage = excluded.coverage,
            measured_at_ms = excluded.measured_at_ms,
            saved_at_ms = excluded.saved_at_ms
        WHERE excluded.measured_at_ms > daily_watch.measured_at_ms
    `).bind(date, shortformMinutes, coverage, measuredAtMs, now, userId, consentVersion, measuredAtMs);
    const [permission] = await db.batch([consent, upsert]);
    return permission.results.length ? noContent() : error('sharing_consent_required', 403);
}

// 경로표. "임시"라고 적힌 것은 5단계에 새로 넣은 것이라 팀원 서버가 바뀌면 달라질 수 있다.
const routes = [
    { pattern: /^\/api\/v1\/daily\/(\d{4}-\d{2}-\d{2})$/, methods: ['PUT'],
        handle: ({ request, db, userId, now, params }) => saveDaily(request, db, userId, params[0], now) },
    { pattern: /^\/api\/v1\/sharing$/, methods: ['PUT', 'DELETE'],
        handle: ({ request, db, userId, now }) => updateSharing(request, db, userId, now) },
    { pattern: /^\/api\/v1\/standing$/, methods: ['PUT'],
        handle: ({ request, db, userId, now }) => saveStanding(request, db, userId, now) },
    ...friendRoutes,
    ...pokeRoutes,
    ...peerRoutes,
];

export default {
    async fetch(request, env) {
        const path = new URL(request.url).pathname;
        try {
            if (path === '/health' && request.method === 'GET') {
                await env.DB.prepare('SELECT 1 FROM daily_watch LIMIT 1').first();
                return noContent();
            }
            const route = routes.map(({ pattern, methods, handle }) => ({ match: pattern.exec(path), methods, handle }))
                .find(({ match }) => match);
            if (!route) return error('not_found', 404);
            if (!route.methods.includes(request.method)) {
                return new Response(null, { status: 405, headers: { Allow: route.methods.join(', ') } });
            }
            if (!env.FIREBASE_PROJECT_ID) return error('firebase_not_configured', 503);

            const now = Date.now();
            const userId = await verifyFirebaseUser(request.headers.get('Authorization'), env.FIREBASE_PROJECT_ID, now);
            if (!userId) return error('invalid_token', 401);
            return await route.handle({ request, db: env.DB, userId, now, params: route.match.slice(1) });
        } catch {
            return error('service_unavailable', 503);
        }
    },

    async scheduled(controller, env) {
        const cutoff = utcDate(controller.scheduledTime - retentionDays * dayMs);
        await env.DB.prepare('DELETE FROM daily_watch WHERE watch_date < ?').bind(cutoff).run();
        // 임시(5-3): 시간이 지난 초대 코드 정리.
        await env.DB.prepare('DELETE FROM invite WHERE expires_at_ms <= ?').bind(controller.scheduledTime).run();
        // 임시(5-4): 만료된 잔소리 정리.
        await cleanPokes(env.DB, controller.scheduledTime);
        // 임시(5-5): 또래 그룹 집계 다시 계산(이미 있는 하루 한 번 cron).
        await computePeerStats(env.DB, controller.scheduledTime);
    },
};
