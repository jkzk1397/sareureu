import { verifyFirebaseUser } from './firebase-auth.mjs';

const consentVersion = 1;
const dayMs = 86_400_000;
const retentionDays = 365;
const maxBodyBytes = 2048;

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

function error(code, status) {
    return Response.json({ error: code }, { status, headers: { 'Cache-Control': 'no-store' } });
}

function noContent() {
    return new Response(null, { status: 204, headers: { 'Cache-Control': 'no-store' } });
}

async function readBody(request) {
    if (!request.body || request.headers.get('Content-Type')?.split(';')[0].trim() !== 'application/json') {
        return null;
    }
    const reader = request.body.getReader();
    const bytes = new Uint8Array(maxBodyBytes);
    let length = 0;
    try {
        for (;;) {
            const { value, done } = await reader.read();
            if (done) break;
            if (length + value.length > bytes.length) {
                await reader.cancel();
                return null;
            }
            bytes.set(value, length);
            length += value.length;
        }
        const body = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes.subarray(0, length)));
        return body && typeof body === 'object' && !Array.isArray(body) ? body : null;
    } catch {
        return null;
    } finally {
        reader.releaseLock();
    }
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

async function updateSharing(request, db, userId, now) {
    if (request.method === 'DELETE') {
        await db.prepare('DELETE FROM sharing_consent WHERE user_id = ?').bind(userId).run();
        return noContent();
    }
    const body = await readBody(request);
    if (body?.consentVersion !== consentVersion || Object.keys(body).length !== 1) {
        return error('invalid_consent', 400);
    }
    await db.prepare(`
        INSERT INTO sharing_consent (user_id, version, granted_at_ms) VALUES (?, ?, ?)
        ON CONFLICT (user_id) DO UPDATE SET version = excluded.version, granted_at_ms = excluded.granted_at_ms
        WHERE excluded.version > sharing_consent.version
    `).bind(userId, consentVersion, now).run();
    return noContent();
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

export default {
    async fetch(request, env) {
        const path = new URL(request.url).pathname;
        try {
            if (path === '/health' && request.method === 'GET') {
                await env.DB.prepare('SELECT 1 FROM daily_watch LIMIT 1').first();
                return noContent();
            }
            const daily = /^\/api\/v1\/daily\/(\d{4}-\d{2}-\d{2})$/.exec(path);
            const sharing = path === '/api/v1/sharing';
            if (!daily && !sharing) return error('not_found', 404);
            const methods = sharing ? ['PUT', 'DELETE'] : ['PUT'];
            if (!methods.includes(request.method)) {
                return new Response(null, { status: 405, headers: { Allow: methods.join(', ') } });
            }
            if (!env.FIREBASE_PROJECT_ID) return error('firebase_not_configured', 503);

            const now = Date.now();
            const userId = await verifyFirebaseUser(request.headers.get('Authorization'), env.FIREBASE_PROJECT_ID, now);
            if (!userId) return error('invalid_token', 401);
            if (sharing) return await updateSharing(request, env.DB, userId, now);
            return await saveDaily(request, env.DB, userId, daily[1], now);
        } catch {
            return error('service_unavailable', 503);
        }
    },

    async scheduled(controller, env) {
        const cutoff = utcDate(controller.scheduledTime - retentionDays * dayMs);
        await env.DB.prepare('DELETE FROM daily_watch WHERE watch_date < ?').bind(cutoff).run();
    },
};
