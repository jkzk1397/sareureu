// 친구 연결(ADR-0014): 초대 코드 → 연결 요청 → 코드 주인이 수락. 끊기·차단·친구 목록.
// 친구에게는 닉네임·감소율·기록된 비율·유효한 날·연속 일수·시청 중만 내려 준다(ADR-0012). 순위는 앱이 매긴다.
// 수락 전에는 요청한 사람의 닉네임만 코드 주인에게 보이고, 주인의 정보는 요청한 사람에게 가지 않는다.
import { error, json, noContent, tooMany } from './http.mjs';
import { limits } from './limits.mjs';
import { watchingWindowMs } from './presence.mjs';
import { refund, take } from './ratelimit.mjs';

const { codeAlphabet, codeLength } = limits;
export const codePattern = new RegExp(`^[${codeAlphabet}]{${codeLength}}$`);
const publicIdPattern = /^[0-9a-f-]{36}$/;
const requestIdPattern = /^[0-9a-f-]{36}$/;
const dayWindow = (max) => [{ windowMs: limits.dayMs, max }];

/** 글자표에서 고르게 뽑은 코드. 바이트를 그냥 나머지로 줄이면 앞 글자가 더 자주 나오므로, 넘치는 값은 버린다. */
export function newCode() {
    const fair = 256 - (256 % codeAlphabet.length);
    let code = '';
    while (code.length < codeLength) {
        for (const byte of crypto.getRandomValues(new Uint8Array(codeLength * 2))) {
            if (byte < fair && code.length < codeLength) code += codeAlphabet[byte % codeAlphabet.length];
        }
    }
    return code;
}

/** 서버에는 코드 대신 이 해시만 둔다. */
export async function codeHash(code) {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`invite:${code}`));
    return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

async function consented(db, userId) {
    return db.prepare('SELECT user_id, public_id FROM sharing_consent WHERE user_id = ?').bind(userId).first();
}

async function userByPublicId(db, publicId) {
    if (!publicIdPattern.test(publicId)) return null;
    return db.prepare('SELECT user_id, nickname FROM sharing_consent WHERE public_id = ?').bind(publicId).first();
}

async function createInvite({ db, userId, now }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const gate = await take(db, userId, 'invite', now, dayWindow(limits.invitesPerDay));
    if (!gate.ok) return tooMany('daily_limit', gate.retryAfterSec);
    const expiresAtMs = now + limits.inviteTtlMs;
    for (let attempt = 0; attempt < 3; attempt++) {
        const code = newCode();
        try {
            // 사람마다 코드 하나: 새로 만들면 이전 코드는 없어진다. 코드는 이 응답으로만 알 수 있다(서버에는 해시만).
            await db.prepare(`
                INSERT INTO invite (code_hash, user_id, expires_at_ms) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE
                SET code_hash = excluded.code_hash, expires_at_ms = excluded.expires_at_ms
            `).bind(await codeHash(code), userId, expiresAtMs).run();
            return json({ code, expiresAtMs }, 201);
        } catch {
            // 다른 사람 코드와 겹쳤다(PRIMARY KEY). 다시 뽑는다.
        }
    }
    return error('service_unavailable', 503);
}

async function cancelInvite({ db, userId }) {
    await db.prepare('DELETE FROM invite WHERE user_id = ?').bind(userId).run();
    return noContent();
}

/**
 * 코드로 연결 요청. 요청 만들기와 코드 쓰기를 한 묶음(트랜잭션)으로 해서 코드는 한 번만 쓰인다.
 * 없는 코드·만료·차단·주인의 대기 요청이 가득 참은 모두 같은 404다(누구 코드인지, 차단됐는지 새지 않게).
 */
async function requestByCode({ db, userId, now, params }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    // 틀린 코드 한도: 먼저 한 번 세고, 틀린 코드가 아니었으면 돌려준다.
    const gate = await take(db, userId, 'code_try', now, limits.wrongCode);
    if (!gate.ok) return tooMany('too_many_attempts', gate.retryAfterSec);
    const code = params[0].toUpperCase();
    if (!codePattern.test(code)) return error('invite_not_found', 404);
    const hash = await codeHash(code);
    const max = limits.maxPendingRequests;
    const [, found] = await db.batch([
        db.prepare(`
            INSERT INTO friend_request (id, from_user, to_user, created_at_ms, expires_at_ms)
            SELECT ?, ?, i.user_id, ?, ? FROM invite i
            WHERE i.code_hash = ? AND i.expires_at_ms > ? AND i.user_id <> ?
              AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker = i.user_id AND b.blocked = ?)
                  OR (b.blocker = ? AND b.blocked = i.user_id))
              AND NOT EXISTS (SELECT 1 FROM friendship f WHERE (f.user_a = i.user_id AND f.user_b = ?)
                  OR (f.user_a = ? AND f.user_b = i.user_id))
              AND (SELECT COUNT(*) FROM friend_request r WHERE r.to_user = i.user_id AND r.expires_at_ms > ?
                  AND r.rejected_at_ms IS NULL) < ?
              AND (SELECT COUNT(*) FROM friend_request r WHERE r.from_user = ? AND r.expires_at_ms > ?) < ?
            ON CONFLICT (from_user, to_user) DO UPDATE
            SET created_at_ms = excluded.created_at_ms, expires_at_ms = excluded.expires_at_ms, rejected_at_ms = NULL
            WHERE friend_request.expires_at_ms <= excluded.created_at_ms
        `).bind(crypto.randomUUID(), userId, now, now + limits.requestTtlMs, hash, now, userId, userId, userId,
            userId, userId, now, max, userId, now, max),
        db.prepare(`
            SELECT r.id AS id, r.expires_at_ms AS expiresAtMs FROM invite i
            JOIN friend_request r ON r.to_user = i.user_id AND r.from_user = ?
            WHERE i.code_hash = ? AND i.expires_at_ms > ? AND r.expires_at_ms > ?
              AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker = i.user_id AND b.blocked = r.from_user)
                  OR (b.blocker = r.from_user AND b.blocked = i.user_id))
        `).bind(userId, hash, now, now),
        // 요청이 생겼으면(이미 있던 요청 포함) 코드는 쓴 것이다.
        db.prepare(`
            DELETE FROM invite WHERE code_hash = ? AND expires_at_ms > ? AND EXISTS (
                SELECT 1 FROM friend_request r
                WHERE r.to_user = invite.user_id AND r.from_user = ? AND r.expires_at_ms > ?
                  AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker = r.to_user AND b.blocked = r.from_user)
                      OR (b.blocker = r.from_user AND b.blocked = r.to_user)))
        `).bind(hash, now, userId, now),
    ]);
    const request = found.results[0];
    if (request) {
        await refund(db, userId, 'code_try', now);
        return json({ request }, 201);
    }
    // 여기부터는 이유를 가르는 읽기다(위 묶음이 이미 아무것도 바꾸지 않았다).
    const invite = await db.prepare('SELECT user_id FROM invite WHERE code_hash = ? AND expires_at_ms > ?')
        .bind(hash, now).first();
    if (!invite) return error('invite_not_found', 404);
    const owner = invite.user_id;
    const notAGuess = async (code, status) => {
        await refund(db, userId, 'code_try', now);
        return error(code, status);
    };
    if (owner === userId) return notAGuess('own_invite', 409);
    const friends = await db.prepare(`
        SELECT 1 FROM friendship WHERE (user_a = ? AND user_b = ?) OR (user_a = ? AND user_b = ?)
    `).bind(owner, userId, userId, owner).first();
    if (friends) return notAGuess('already_friends', 409);
    const mine = await db.prepare('SELECT COUNT(*) AS n FROM friend_request WHERE from_user = ? AND expires_at_ms > ?')
        .bind(userId, now).first();
    if (mine.n >= max) return notAGuess('too_many_requests', 409);
    // 차단 관계이거나 주인의 대기 요청이 가득 찼다: 알리지 않는다.
    return error('invite_not_found', 404);
}

async function listRequests({ db, userId, now }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const [received, sent] = await db.batch([
        // 받은 요청: 요청한 사람의 닉네임만.
        db.prepare(`
            SELECT r.id AS id, c.nickname AS fromNickname, r.created_at_ms AS createdAtMs,
                   r.expires_at_ms AS expiresAtMs
            FROM friend_request r JOIN sharing_consent c ON c.user_id = r.from_user
            WHERE r.to_user = ? AND r.rejected_at_ms IS NULL AND r.expires_at_ms > ?
            ORDER BY r.created_at_ms DESC LIMIT ?
        `).bind(userId, now, limits.maxPendingRequests),
        // 보낸 요청: 상대가 누구인지는 수락 전까지 주지 않는다. 거절된 것도 만료 전까지 그대로 보인다.
        db.prepare(`
            SELECT id, created_at_ms AS createdAtMs, expires_at_ms AS expiresAtMs FROM friend_request
            WHERE from_user = ? AND expires_at_ms > ? ORDER BY created_at_ms DESC LIMIT ?
        `).bind(userId, now, limits.maxPendingRequests),
    ]);
    return json({ received: received.results, sent: sent.results });
}

const pairExists = `EXISTS (SELECT 1 FROM friendship f
    WHERE f.user_a = MIN(r.from_user, r.to_user) AND f.user_b = MAX(r.from_user, r.to_user))`;

/** 수락. 친구 수 50명 확인과 연결을 한 문장으로 해서 동시에 수락해도 한도를 넘지 않는다. */
async function acceptRequest({ db, userId, now, params }) {
    const id = params[0];
    if (!requestIdPattern.test(id)) return error('request_not_found', 404);
    const [, accepted] = await db.batch([
        db.prepare(`
            INSERT INTO friendship (user_a, user_b, created_at_ms)
            SELECT MIN(r.from_user, r.to_user), MAX(r.from_user, r.to_user), ? FROM friend_request r
            WHERE r.id = ? AND r.to_user = ? AND r.rejected_at_ms IS NULL AND r.expires_at_ms > ?
              AND (SELECT COUNT(*) FROM friendship f WHERE f.user_a = r.to_user OR f.user_b = r.to_user) < ?
              AND (SELECT COUNT(*) FROM friendship f WHERE f.user_a = r.from_user OR f.user_b = r.from_user) < ?
              AND NOT EXISTS (SELECT 1 FROM block b WHERE (b.blocker = r.to_user AND b.blocked = r.from_user)
                  OR (b.blocker = r.from_user AND b.blocked = r.to_user))
            ON CONFLICT (user_a, user_b) DO NOTHING
        `).bind(now, id, userId, now, limits.maxFriends, limits.maxFriends),
        db.prepare(`
            SELECT c.public_id AS id, c.nickname AS nickname FROM friend_request r
            JOIN sharing_consent c ON c.user_id = r.from_user
            WHERE r.id = ? AND r.to_user = ? AND ${pairExists}
        `).bind(id, userId),
        // 연결됐으면 두 사람 사이의 요청(반대 방향 포함)을 지운다.
        db.prepare(`
            DELETE FROM friend_request AS r WHERE ${pairExists} AND (r.id = ? OR
                (r.to_user, r.from_user) IN (SELECT from_user, to_user FROM friend_request WHERE id = ? AND to_user = ?))
        `).bind(id, id, userId),
    ]);
    const friend = accepted.results[0];
    if (friend) return json({ friend }, 201);
    const request = await db.prepare(`
        SELECT from_user FROM friend_request
        WHERE id = ? AND to_user = ? AND rejected_at_ms IS NULL AND expires_at_ms > ?
    `).bind(id, userId, now).first();
    if (!request) return error('request_not_found', 404);
    const blocked = await db.prepare(`
        SELECT 1 FROM block WHERE (blocker = ? AND blocked = ?) OR (blocker = ? AND blocked = ?)
    `).bind(userId, request.from_user, request.from_user, userId).first();
    return blocked ? error('request_not_found', 404) : error('too_many_friends', 409);
}

/**
 * 거절(받은 사람) 또는 취소(보낸 사람). 거절은 지우지 않고 표시만 해서 상대에게 알리지 않는다.
 * `?block=1`이면 거절하면서 보낸 사람을 차단한다(닉네임만 보이는 단계에서도 막을 수 있게).
 */
async function closeRequest({ request, db, userId, now, params }) {
    const id = params[0];
    if (!requestIdPattern.test(id)) return noContent();
    const block = new URL(request.url).searchParams.get('block') === '1';
    await db.batch([
        ...(block ? [db.prepare(`
            INSERT OR IGNORE INTO block (blocker, blocked, created_at_ms)
            SELECT to_user, from_user, ? FROM friend_request WHERE id = ? AND to_user = ?
        `).bind(now, id, userId)] : []),
        db.prepare('UPDATE friend_request SET rejected_at_ms = ? WHERE id = ? AND to_user = ? AND rejected_at_ms IS NULL')
            .bind(now, id, userId),
        db.prepare('DELETE FROM friend_request WHERE id = ? AND from_user = ?').bind(id, userId),
    ]);
    return noContent();
}

async function listFriends({ db, userId, now }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const [list, blockedList] = await db.batch([
        db.prepare(`
            SELECT c.public_id AS id, c.nickname AS nickname, s.reduction_rate AS reductionRate,
                   s.coverage AS coverage, COALESCE(s.valid_days, 0) AS validDays,
                   COALESCE(s.streak_days, 0) AS streakDays,
                   CASE WHEN p.last_seen_ms > ? THEN 1 ELSE 0 END AS watching
            FROM friendship f
            JOIN sharing_consent c ON c.user_id = CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END
            LEFT JOIN standing s ON s.user_id = c.user_id
            LEFT JOIN presence p ON p.user_id = c.user_id
            WHERE f.user_a = ? OR f.user_b = ?
            ORDER BY c.nickname
        `).bind(now - watchingWindowMs, userId, userId, userId),
        db.prepare(`
            SELECT c.public_id AS id, c.nickname AS nickname FROM block b
            JOIN sharing_consent c ON c.user_id = b.blocked WHERE b.blocker = ? ORDER BY c.nickname
        `).bind(userId),
    ]);
    const friends = list.results.map((f) => ({ ...f, watching: f.watching === 1 }));
    return json({ friends, blocked: blockedList.results });
}

async function relationGate(db, userId, now) {
    const gate = await take(db, userId, 'relation', now, dayWindow(limits.relationChangesPerDay));
    return gate.ok ? null : tooMany('daily_limit', gate.retryAfterSec);
}

async function disconnect({ db, userId, now, params }) {
    const other = await userByPublicId(db, params[0]);
    if (other) {
        if (!await consented(db, userId)) return noContent();
        const limited = await relationGate(db, userId, now);
        if (limited) return limited;
        const [a, b] = userId < other.user_id ? [userId, other.user_id] : [other.user_id, userId];
        await db.prepare('DELETE FROM friendship WHERE user_a = ? AND user_b = ?').bind(a, b).run();
    }
    return noContent();
}

async function block({ request, db, userId, now, params }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const other = await userByPublicId(db, params[0]);
    if (!other || other.user_id === userId) return error('not_found', 404);
    const limited = await relationGate(db, userId, now);
    if (limited) return limited;
    if (request.method === 'DELETE') {
        // 차단을 풀어도 연결은 되살리지 않는다(다시 초대 코드로).
        await db.prepare('DELETE FROM block WHERE blocker = ? AND blocked = ?').bind(userId, other.user_id).run();
        return noContent();
    }
    const [a, b] = userId < other.user_id ? [userId, other.user_id] : [other.user_id, userId];
    await db.batch([
        db.prepare('INSERT OR IGNORE INTO block (blocker, blocked, created_at_ms) VALUES (?, ?, ?)')
            .bind(userId, other.user_id, now),
        db.prepare('DELETE FROM friendship WHERE user_a = ? AND user_b = ?').bind(a, b),
        // 서로 사이의 연결 요청과 아직 받지 않은 잔소리도 지운다.
        db.prepare(`
            DELETE FROM friend_request WHERE (from_user = ? AND to_user = ?) OR (from_user = ? AND to_user = ?)
        `).bind(userId, other.user_id, other.user_id, userId),
        db.prepare('DELETE FROM poke WHERE from_user = ? AND to_user = ?').bind(other.user_id, userId),
    ]);
    return noContent();
}

/** ADR-0014 이전 앱이 쓰던 '코드로 바로 연결'. 이제는 요청 뒤 수락이라 앱을 새로 받아야 한다. */
function legacyAccept() {
    return error('upgrade_required', 426);
}

/** 친구 경로표(index.mjs가 붙인다). `ipLimit`은 IP 기준 제한을 거는 경로. */
export const friendRoutes = [
    { pattern: /^\/api\/v1\/invites$/, methods: ['POST', 'DELETE'],
        handle: (ctx) => (ctx.request.method === 'POST' ? createInvite(ctx) : cancelInvite(ctx)) },
    { pattern: /^\/api\/v1\/invites\/([A-Za-z0-9]{1,16})\/requests$/, methods: ['POST'], handle: requestByCode,
        ipLimit: true },
    { pattern: /^\/api\/v1\/invites\/([A-Za-z0-9]{1,16})\/accept$/, methods: ['POST'], handle: legacyAccept },
    { pattern: /^\/api\/v1\/friend-requests$/, methods: ['GET'], handle: listRequests },
    { pattern: /^\/api\/v1\/friend-requests\/([0-9a-f-]{1,64})\/accept$/, methods: ['POST'], handle: acceptRequest },
    { pattern: /^\/api\/v1\/friend-requests\/([0-9a-f-]{1,64})$/, methods: ['DELETE'], handle: closeRequest },
    { pattern: /^\/api\/v1\/friends$/, methods: ['GET'], handle: listFriends },
    { pattern: /^\/api\/v1\/friends\/([0-9a-f-]{1,64})$/, methods: ['DELETE'], handle: disconnect },
    { pattern: /^\/api\/v1\/blocks\/([0-9a-f-]{1,64})$/, methods: ['PUT', 'DELETE'], handle: block },
];

/** cron: 시간이 지난 초대 코드와 연결 요청 정리. */
export async function cleanInvites(db, now) {
    await db.prepare('DELETE FROM invite WHERE expires_at_ms <= ?').bind(now).run();
    await db.prepare('DELETE FROM friend_request WHERE expires_at_ms <= ?').bind(now).run();
}
