// 잔소리 보내기·받기(ADR-0012, ADR-0014). 지금 시청 중인 친구에게만 보낼 수 있고, 받는 쪽 앱이 '시청 중' 신호
// (presence.mjs)의 응답으로 몇 초 안에 받아 알림으로 띄운다. 푸시(FCM)는 쓰지 않는다.
// 서버도 글을 다듬고 금지 표현을 검사한다(앱을 거치지 않고 부를 수 있으므로). 글은 로그에 남기지 않는다.
import { error, json, readBody, tooMany } from './http.mjs';
import { limits } from './limits.mjs';
import { watchingWindowMs } from './presence.mjs';
import { retryAfterSec } from './ratelimit.mjs';
import { suspendedSql } from './reports.mjs';
import { cleanText, hasBannedWord, textLength } from './text.mjs';

const publicIdPattern = /^[0-9a-f-]{36}$/;
const idempotencyKeyPattern = /^[A-Za-z0-9-]{8,64}$/;
const ackIdPattern = /^[0-9a-f-]{1,64}$/;
const totalWindow = [{ windowMs: limits.dayMs, max: limits.pokesPerDay }];
const friendWindow = [{ windowMs: limits.dayMs, max: limits.pokesPerFriendPerDay }];

/** 같은 요청이 두 번 와도 한 번만 처리하려는 키(`Idempotency-Key`). 모양이 다르면 없는 것으로 본다. */
function idempotencyKey(request) {
    const key = request.headers.get('Idempotency-Key');
    return key && idempotencyKeyPattern.test(key) ? key : null;
}

async function sentBefore(db, userId, key) {
    if (!key) return null;
    return db.prepare('SELECT result FROM idempotency WHERE user_id = ? AND key = ?').bind(userId, key).first();
}

async function sendPoke({ request, db, userId, now }) {
    const body = await readBody(request);
    if (!body || Object.keys(body).some((key) => !['to', 'text'].includes(key)) ||
        typeof body.to !== 'string' || !publicIdPattern.test(body.to) || typeof body.text !== 'string') {
        return error('invalid_poke', 400);
    }
    const text = cleanText(body.text);
    if (text.length === 0 || textLength(text) > limits.pokeMaxLength) return error('invalid_poke', 400);
    if (hasBannedWord(text)) return error('banned_text', 400);

    const key = idempotencyKey(request);
    const before = await sentBefore(db, userId, key);
    if (before) return json({ id: before.result }, 201);

    const id = crypto.randomUUID();
    const since = now - limits.dayMs;
    // 친구 관계·시청 중·하루 한도(전체, 같은 친구)·신고 정지를 한 문장으로 확인하고 넣는다(동시에 와도 한도를 넘지 않는다).
    const insert = db.prepare(`
        INSERT INTO poke (id, from_user, to_user, text, created_at_ms, expires_at_ms)
        SELECT ?, ?, c.user_id, ?, ?, ? FROM sharing_consent c
        WHERE c.public_id = ? AND c.user_id <> ?
          AND EXISTS (SELECT 1 FROM friendship f WHERE (f.user_a = c.user_id AND f.user_b = ?)
              OR (f.user_a = ? AND f.user_b = c.user_id))
          AND EXISTS (SELECT 1 FROM presence p WHERE p.user_id = c.user_id AND p.last_seen_ms > ?)
          AND (SELECT COUNT(*) FROM rate_event WHERE user_id = ? AND kind = 'poke' AND at_ms > ?) < ?
          AND (SELECT COUNT(*) FROM rate_event WHERE user_id = ? AND kind = 'poke>' || c.user_id AND at_ms > ?) < ?
          AND NOT ${suspendedSql}
    `).bind(id, userId, text, now, now + limits.pokeTtlMs, body.to, userId, userId, userId, now - watchingWindowMs,
        userId, since, limits.pokesPerDay, userId, since, limits.pokesPerFriendPerDay,
        userId, now - limits.reportRetentionMs, limits.reportersToSuspend);
    const statements = [
        insert,
        db.prepare("INSERT INTO rate_event (user_id, kind, at_ms) SELECT from_user, 'poke', created_at_ms FROM poke WHERE id = ?")
            .bind(id),
        db.prepare(`
            INSERT INTO rate_event (user_id, kind, at_ms)
            SELECT from_user, 'poke>' || to_user, created_at_ms FROM poke WHERE id = ?
        `).bind(id),
        ...(key ? [db.prepare(`
            INSERT INTO idempotency (user_id, key, result, at_ms)
            SELECT from_user, ?, id, created_at_ms FROM poke WHERE id = ?
        `).bind(key, id)] : []),
        db.prepare('SELECT to_user AS toUser FROM poke WHERE id = ?').bind(id),
    ];
    let results;
    try {
        results = await db.batch(statements);
    } catch (failure) {
        // 같은 멱등 키의 요청이 동시에 와서 한쪽이 먼저 넣었다: 그쪽 결과를 돌려준다.
        const winner = await sentBefore(db, userId, key);
        if (winner) return json({ id: winner.result }, 201);
        throw failure;
    }
    if (results.at(-1).results.length) return json({ id }, 201);
    return whyNotSent(db, userId, body.to, now);
}

/** 보내지 못한 이유를 가른다(위 문장이 아무것도 넣지 않은 뒤의 읽기). */
async function whyNotSent(db, userId, toPublicId, now) {
    const to = await db.prepare('SELECT user_id FROM sharing_consent WHERE public_id = ?').bind(toPublicId).first();
    if (!to || to.user_id === userId) return error('not_friends', 403);
    const friends = await db.prepare(`
        SELECT 1 FROM friendship WHERE (user_a = ? AND user_b = ?) OR (user_a = ? AND user_b = ?)
    `).bind(to.user_id, userId, userId, to.user_id).first();
    // 차단하면 연결이 끊기므로 친구가 아니면 보낼 수 없다.
    if (!friends) return error('not_friends', 403);
    const suspended = await db.prepare(`SELECT ${suspendedSql} AS yes`)
        .bind(userId, now - limits.reportRetentionMs, limits.reportersToSuspend).first();
    if (suspended.yes) return error('suspended', 403);
    const watching = await db.prepare('SELECT 1 FROM presence WHERE user_id = ? AND last_seen_ms > ?')
        .bind(to.user_id, now - watchingWindowMs).first();
    if (!watching) return error('not_watching', 409);
    const wait = Math.max(
        await limitWait(db, userId, 'poke', now, totalWindow),
        await limitWait(db, userId, `poke>${to.user_id}`, now, friendWindow));
    return tooMany('daily_limit', wait);
}

async function limitWait(db, userId, kind, now, windows) {
    const { n } = await db.prepare('SELECT COUNT(*) AS n FROM rate_event WHERE user_id = ? AND kind = ? AND at_ms > ?')
        .bind(userId, kind, now - windows[0].windowMs).first();
    return n >= windows[0].max ? retryAfterSec(db, userId, kind, now, windows) : 0;
}

/** 받은 것을 확인하는 id 목록(모양이 맞는 것만, 한 번에 받는 개수까지). */
export function ackIds(value) {
    const list = Array.isArray(value) ? value : typeof value === 'string' ? value.split(',') : [];
    return list.filter((id) => typeof id === 'string' && ackIdPattern.test(id)).slice(0, limits.maxInbox);
}

/**
 * 나에게 온 잔소리를 꺼낸다(GET /pokes와 시청 중 신호 응답이 함께 쓴다).
 * - [keep]이 true(계약 번호 2 이상): 내려 줘도 지우지 않고, 앱이 받은 id를 [ack]로 보내면 그때 지운다.
 *   응답이 끊겨도 사라지지 않고, 확인 전까지는 다시 내려 준다(앱이 id로 중복을 거른다).
 * - false(이전 앱): 내려 주면 바로 지운다.
 * 만료된 것과 차단한 사람 것은 언제나 지운다.
 */
export async function takePokes(db, userId, now, { ack = [], keep = false } = {}) {
    const select = db.prepare(`
        SELECT p.id AS id, c.public_id AS fromId, c.nickname AS fromNickname, p.text AS text,
               p.created_at_ms AS createdAtMs, p.expires_at_ms AS expiresAtMs
        FROM poke p JOIN sharing_consent c ON c.user_id = p.from_user
        WHERE p.to_user = ? AND p.expires_at_ms > ?
          AND NOT EXISTS (SELECT 1 FROM block b WHERE b.blocker = p.to_user AND b.blocked = p.from_user)
        ORDER BY p.created_at_ms LIMIT ?
    `).bind(userId, now, limits.maxInbox);
    const drop = (ids) => db.prepare(`
        DELETE FROM poke WHERE to_user = ? AND (
            expires_at_ms <= ? OR id IN (SELECT value FROM json_each(?)) OR
            from_user IN (SELECT blocked FROM block WHERE blocker = ?))
    `).bind(userId, now, JSON.stringify(ids), userId);
    if (keep) {
        // 확인한 것을 먼저 지우고 남은 것을 내려 준다(한 묶음 = D1 요청 한 번).
        const [, pending] = await db.batch([drop(ack), select]);
        return pending.results;
    }
    const { results } = await select.all();
    await drop(results.map((p) => p.id)).run();
    return results;
}

async function receivePokes({ request, db, userId, now, appVersion }) {
    const ack = ackIds(new URL(request.url).searchParams.get('ack'));
    return json({ pokes: await takePokes(db, userId, now, { ack, keep: appVersion >= limits.ackFromVersion }) });
}

/** 잔소리 경로표(index.mjs가 붙인다). */
export const pokeRoutes = [
    { pattern: /^\/api\/v1\/pokes$/, methods: ['POST', 'GET'], ipLimit: 'POST',
        handle: (ctx) => (ctx.request.method === 'POST' ? sendPoke(ctx) : receivePokes(ctx)) },
];

/** cron: 만료된 잔소리 정리. */
export async function cleanPokes(db, now) {
    await db.prepare('DELETE FROM poke WHERE expires_at_ms <= ?').bind(now).run();
}
