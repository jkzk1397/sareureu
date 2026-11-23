import { verifyFirebaseUser } from './firebase-auth.mjs';
import { cleanInvites, friendRoutes } from './friends.mjs';
import { error, noContent, readBody, tooMany } from './http.mjs';
import { limits } from './limits.mjs';
import { ensureMigrated } from './migrate.mjs';
import { computePeerStats, peerRoutes } from './peers.mjs';
import { invitePage, invitePagePattern } from './invite-page.mjs';
import { cleanPokes, pokeRoutes } from './pokes.mjs';
import { cleanPresence, presenceRoutes } from './presence.mjs';
import { cleanRateEvents } from './ratelimit.mjs';
import { cleanReports, reportRoutes } from './reports.mjs';
import { cleanText, hasBannedWord, textLength } from './text.mjs';

// 동의 버전: 1 = 하루 합계·랭킹 숫자, 2 = 1 + 연속 일수·지금 시청 중(ADR-0012). 낮은 버전으로 되돌리지 않는다.
const consentVersions = [1, 2];
const dayMs = 86_400_000;
const retentionDays = 365;
const maxStreakDays = 3650;

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
        !Number.isSafeInteger(measuredAtMs) || measuredAtMs < now - retentionDays * dayMs ||
        measuredAtMs > now + limits.clockSkewMs || typeof timeZone !== 'string' || timeZone.length === 0 || timeZone.length > 64) return false;

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

/** 폰 시계가 서버보다 허용 범위(5분)를 넘게 앞서 있는지. 이때는 모양 오류와 구분해 clock_skew로 알린다. */
function clockAhead(body, now) {
    return Number.isSafeInteger(body?.measuredAtMs) && body.measuredAtMs > now + limits.clockSkewMs;
}

// 동의할 때 닉네임(친구에게 보임)과 연령대·신분(또래 그룹 집계용)을 함께 받는다. 실명·연락처는 받지 않는다.
const maxNickname = limits.nicknameMaxLength;
const enumName = /^[A-Z][A-Z0-9_]{0,31}$/;

function validSharing(body) {
    if (!body || !consentVersions.includes(body.consentVersion) || Object.keys(body).some((key) =>
        !['consentVersion', 'nickname', 'ageBand', 'occupation'].includes(key))) return false;
    const { nickname = null, ageBand = null, occupation = null } = body;
    if (nickname !== null && (typeof nickname !== 'string' || cleanText(nickname).length === 0 ||
        textLength(cleanText(nickname)) > maxNickname)) return false;
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
    // 닉네임은 친구와 연결 요청을 받는 사람에게 보인다: 보이지 않는 문자를 지우고 금지 표현을 막는다(ADR-0014).
    const nickname = typeof body.nickname === 'string' ? cleanText(body.nickname) : null;
    if (nickname !== null && hasBannedWord(nickname)) return error('invalid_nickname', 400);
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
    `).bind(userId, body.consentVersion, now, crypto.randomUUID(), nickname, body.ageBand ?? null,
        body.occupation ?? null).run();
    return noContent();
}

function validStanding(body, now) {
    if (!body || Object.keys(body).some((key) =>
        !['reductionRate', 'coverage', 'validDays', 'streakDays', 'measuredAtMs'].includes(key))) return false;
    const { reductionRate, coverage, validDays, streakDays = 0, measuredAtMs } = body;
    return Number.isInteger(streakDays) && streakDays >= 0 && streakDays <= maxStreakDays &&
        (reductionRate === null || (Number.isFinite(reductionRate) && reductionRate >= -10 && reductionRate <= 1)) &&
        (coverage === null || (Number.isFinite(coverage) && coverage >= 0 && coverage <= 1)) &&
        Number.isInteger(validDays) && validDays >= 0 && validDays <= 7 &&
        Number.isSafeInteger(measuredAtMs) && measuredAtMs > now - dayMs &&
        measuredAtMs <= now + limits.clockSkewMs;
}

// 임시(5-2): 랭킹용 내 숫자. 계산은 앱(core:insights)이 하고 서버는 저장만 한다.
// 6단계(ADR-0012): 목표 이하로 본 연속 일수(streakDays)를 더 받는다. 없으면 0.
async function saveStanding(request, db, userId, now) {
    const body = await readBody(request);
    if (clockAhead(body, now)) return error('clock_skew', 400);
    if (!validStanding(body, now)) return error('invalid_standing', 400);
    const { reductionRate, coverage, validDays, streakDays = 0, measuredAtMs } = body;
    // 동의 시각은 서버 시계, 잰 시각은 폰 시계라 허용 오차만큼 봐준다(폰이 조금 늦어도 동의 직후 업로드가 거절되지 않게).
    const measuredBy = measuredAtMs + limits.clockSkewMs;
    const consent = db.prepare('SELECT user_id FROM sharing_consent WHERE user_id = ? AND granted_at_ms <= ?')
        .bind(userId, measuredBy);
    const upsert = db.prepare(`
        INSERT INTO standing (user_id, reduction_rate, coverage, valid_days, streak_days, measured_at_ms)
        SELECT user_id, ?, ?, ?, ?, ? FROM sharing_consent WHERE user_id = ? AND granted_at_ms <= ?
        ON CONFLICT (user_id) DO UPDATE SET
            reduction_rate = excluded.reduction_rate,
            coverage = excluded.coverage,
            valid_days = excluded.valid_days,
            streak_days = excluded.streak_days,
            measured_at_ms = excluded.measured_at_ms
        WHERE excluded.measured_at_ms > standing.measured_at_ms
    `).bind(reductionRate, coverage, validDays, streakDays, measuredAtMs, userId, measuredBy);
    const [permission] = await db.batch([consent, upsert]);
    return permission.results.length ? noContent() : error('sharing_consent_required', 403);
}

async function saveDaily(request, db, userId, date, now) {
    const body = await readBody(request);
    if (clockAhead(body, now)) return error('clock_skew', 400);
    if (!validDaily(body, date, now)) return error('invalid_daily_summary', 400);
    const { shortformMinutes, coverage, measuredAtMs } = body;
    const measuredBy = measuredAtMs + limits.clockSkewMs;

    const consent = db.prepare(`
        SELECT user_id FROM sharing_consent WHERE user_id = ? AND version >= ? AND granted_at_ms <= ?
    `).bind(userId, consentVersions[0], measuredBy);
    const upsert = db.prepare(`
        INSERT INTO daily_watch (user_id, watch_date, shortform_minutes, coverage, measured_at_ms, saved_at_ms)
        SELECT user_id, ?, ?, ?, ?, ? FROM sharing_consent
        WHERE user_id = ? AND version >= ? AND granted_at_ms <= ?
        ON CONFLICT (user_id, watch_date) DO UPDATE SET
            shortform_minutes = excluded.shortform_minutes,
            coverage = excluded.coverage,
            measured_at_ms = excluded.measured_at_ms,
            saved_at_ms = excluded.saved_at_ms
        WHERE excluded.measured_at_ms > daily_watch.measured_at_ms
    `).bind(date, shortformMinutes, coverage, measuredAtMs, now, userId, consentVersions[0], measuredBy);
    const [permission] = await db.batch([consent, upsert]);
    return permission.results.length ? noContent() : error('sharing_consent_required', 403);
}

/** 앱이 X-App-Version으로 보내는 계약 번호. 없거나 이상하면 1(ADR-0014 이전 앱). */
function appVersionOf(request) {
    const value = request.headers.get('X-App-Version');
    return value && /^[0-9]{1,6}$/.test(value) ? Math.max(1, Number(value)) : 1;
}

/** 서버가 받는 최소 계약 번호. wrangler.toml의 MIN_APP_VERSION으로 올릴 수 있다(없으면 limits.mjs 값). */
function minAppVersion(env) {
    const configured = Number(env.MIN_APP_VERSION);
    return Number.isInteger(configured) && configured >= 1 ? configured : limits.minAppVersion;
}

/**
 * IP 기준 제한(ADR-0014). Workers Rate Limiting 바인딩(IP_LIMITER)이 있을 때만 센다. IP는 저장하지 않는다.
 * 바인딩이 없거나 실패하면 통과시킨다(계정 기준 한도는 그대로 동작한다).
 */
async function ipLimited(request, env, route) {
    const applies = route.ipLimit === true || route.ipLimit === request.method;
    if (!applies || !env.IP_LIMITER) return false;
    const ip = request.headers.get('CF-Connecting-IP');
    if (!ip) return false;
    try {
        const { success } = await env.IP_LIMITER.limit({ key: ip });
        return !success;
    } catch {
        return false;
    }
}

/** 60일 동안 업로드(랭킹 숫자·하루 합계)가 없는 계정의 동의 행을 지운다. 딸린 기록은 함께 지워진다. */
export async function cleanAbandoned(db, now) {
    const cutoff = now - limits.abandonedAfterMs;
    await db.prepare(`
        DELETE FROM sharing_consent WHERE granted_at_ms < ?
          AND NOT EXISTS (SELECT 1 FROM standing s WHERE s.user_id = sharing_consent.user_id AND s.measured_at_ms >= ?)
          AND NOT EXISTS (SELECT 1 FROM daily_watch d WHERE d.user_id = sharing_consent.user_id AND d.saved_at_ms >= ?)
    `).bind(cutoff, cutoff, cutoff).run();
}

// 경로표. ipLimit이 있는 경로는 IP 기준 제한도 받는다(true = 모든 메서드, 'POST' = 그 메서드만).
const routes = [
    { pattern: /^\/api\/v1\/daily\/(\d{4}-\d{2}-\d{2})$/, methods: ['PUT'],
        handle: ({ request, db, userId, now, params }) => saveDaily(request, db, userId, params[0], now) },
    { pattern: /^\/api\/v1\/sharing$/, methods: ['PUT', 'DELETE'], ipLimit: 'PUT',
        handle: ({ request, db, userId, now }) => updateSharing(request, db, userId, now) },
    { pattern: /^\/api\/v1\/standing$/, methods: ['PUT'],
        handle: ({ request, db, userId, now }) => saveStanding(request, db, userId, now) },
    ...friendRoutes,
    ...pokeRoutes,
    ...peerRoutes,
    ...presenceRoutes,
    ...reportRoutes,
];

export default {
    async fetch(request, env) {
        const path = new URL(request.url).pathname;
        try {
            await ensureMigrated(env.DB);
            if (path === '/health' && request.method === 'GET') {
                await env.DB.prepare('SELECT 1 FROM daily_watch LIMIT 1').first();
                return noContent();
            }
            // 6단계: 초대 QR이 여는 안내 페이지(로그인 없음). 앱이 있으면 앱을, 없으면 설치 파일을 연다.
            const invite = invitePagePattern.exec(path);
            if (invite && request.method === 'GET') return invitePage(invite[1], env);
            const route = routes.map((r) => ({ ...r, match: r.pattern.exec(path) })).find(({ match }) => match);
            if (!route) return error('not_found', 404);
            if (!route.methods.includes(request.method)) {
                return new Response(null, { status: 405, headers: { Allow: route.methods.join(', ') } });
            }
            if (!env.FIREBASE_PROJECT_ID) return error('firebase_not_configured', 503);
            // 계약 번호가 서버의 최소 번호보다 낮으면 앱을 새로 받으라고 알린다(나중에 계약을 바꿀 수 있게).
            const appVersion = appVersionOf(request);
            if (appVersion < minAppVersion(env)) return error('upgrade_required', 426);
            if (await ipLimited(request, env, route)) return tooMany('too_many_requests', limits.ipRetryAfterSec);

            const now = Date.now();
            const userId = await verifyFirebaseUser(request.headers.get('Authorization'), env.FIREBASE_PROJECT_ID, now);
            if (!userId) return error('invalid_token', 401);
            return await route.handle({
                request, db: env.DB, userId, now, appVersion, params: route.match.slice(1),
            });
        } catch {
            return error('service_unavailable', 503);
        }
    },

    async scheduled(controller, env) {
        await ensureMigrated(env.DB);
        const cutoff = utcDate(controller.scheduledTime - retentionDays * dayMs);
        await env.DB.prepare('DELETE FROM daily_watch WHERE watch_date < ?').bind(cutoff).run();
        // 버려진 계정(60일 동안 업로드 없음)을 먼저 지운다. 딸린 초대·요청·잔소리가 함께 지워진다.
        await cleanAbandoned(env.DB, controller.scheduledTime);
        // 시간이 지난 초대 코드·연결 요청·잔소리·한도 기록·신고 정리.
        await cleanInvites(env.DB, controller.scheduledTime);
        await cleanPokes(env.DB, controller.scheduledTime);
        await cleanRateEvents(env.DB, controller.scheduledTime, limits.dayMs);
        await cleanReports(env.DB, controller.scheduledTime);
        // 6단계: 오래된 '시청 중' 신호 정리.
        await cleanPresence(env.DB, controller.scheduledTime);
        // 임시(5-5): 또래 그룹 집계 다시 계산(이미 있는 하루 한 번 cron).
        await computePeerStats(env.DB, controller.scheduledTime);
    },
};
