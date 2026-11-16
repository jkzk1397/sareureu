// 임시(5-3): 친구 연결·끊기·차단·친구 목록(랭킹 숫자). 팀원 서버가 바뀌면 달라질 수 있다(server/README.md).
// 친구에게는 닉네임·감소율·기록된 비율·유효한 날만 내려 준다(ADR-0005). 순위 매기기·꼴찌 숨기기는 앱이 한다.
import { error, json, noContent } from './http.mjs';

const dayMs = 86_400_000;
const inviteTtlMs = dayMs;
const maxFriends = 50;
// 헷갈리는 글자(0·O·1·I·L)를 뺀 32글자.
const codeAlphabet = 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
const codeLength = 6;
const codePattern = new RegExp(`^[${codeAlphabet}]{${codeLength}}$`);
const publicIdPattern = /^[0-9a-f-]{36}$/;

function newCode() {
    const bytes = crypto.getRandomValues(new Uint8Array(codeLength));
    return [...bytes].map((b) => codeAlphabet[b % codeAlphabet.length]).join('');
}

function pair(x, y) {
    return x < y ? [x, y] : [y, x];
}

async function consented(db, userId) {
    return db.prepare('SELECT user_id, public_id FROM sharing_consent WHERE user_id = ?').bind(userId).first();
}

async function userByPublicId(db, publicId) {
    if (!publicIdPattern.test(publicId)) return null;
    return db.prepare('SELECT user_id, nickname FROM sharing_consent WHERE public_id = ?').bind(publicId).first();
}

async function blockedEitherWay(db, a, b) {
    return db.prepare('SELECT 1 FROM block WHERE (blocker = ? AND blocked = ?) OR (blocker = ? AND blocked = ?)')
        .bind(a, b, b, a).first();
}

async function createInvite({ db, userId, now }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const expiresAtMs = now + inviteTtlMs;
    for (let attempt = 0; attempt < 3; attempt++) {
        const code = newCode();
        try {
            // 사람마다 코드 하나: 새로 만들면 이전 코드는 없어진다.
            await db.prepare(`
                INSERT INTO invite (code, user_id, expires_at_ms) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET code = excluded.code, expires_at_ms = excluded.expires_at_ms
            `).bind(code, userId, expiresAtMs).run();
            return json({ code, expiresAtMs }, 201);
        } catch {
            // 다른 사람 코드와 겹쳤다(PRIMARY KEY). 다시 뽑는다.
        }
    }
    return error('service_unavailable', 503);
}

async function acceptInvite({ db, userId, now, params }) {
    const code = params[0].toUpperCase();
    if (!codePattern.test(code)) return error('invite_not_found', 404);
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const invite = await db.prepare('SELECT user_id FROM invite WHERE code = ? AND expires_at_ms > ?')
        .bind(code, now).first();
    if (!invite) return error('invite_not_found', 404);
    const owner = invite.user_id;
    if (owner === userId) return error('own_invite', 409);
    // 어느 쪽이 차단했는지는 알리지 않는다.
    if (await blockedEitherWay(db, owner, userId)) return error('unavailable', 409);
    const [a, b] = pair(owner, userId);
    if (await db.prepare('SELECT 1 FROM friendship WHERE user_a = ? AND user_b = ?').bind(a, b).first()) {
        return error('already_friends', 409);
    }
    const counts = await db.prepare(`
        SELECT (SELECT COUNT(*) FROM friendship WHERE user_a = ? OR user_b = ?) AS owner,
               (SELECT COUNT(*) FROM friendship WHERE user_a = ? OR user_b = ?) AS me
    `).bind(owner, owner, userId, userId).first();
    if (Math.max(counts.owner, counts.me) >= maxFriends) return error('too_many_friends', 409);

    await db.batch([
        db.prepare('INSERT INTO friendship (user_a, user_b, created_at_ms) VALUES (?, ?, ?)').bind(a, b, now),
        db.prepare('DELETE FROM invite WHERE code = ?').bind(code),
    ]);
    const friend = await db.prepare('SELECT public_id, nickname FROM sharing_consent WHERE user_id = ?')
        .bind(owner).first();
    return json({ friend: { id: friend.public_id, nickname: friend.nickname } }, 201);
}

async function listFriends({ db, userId }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const { results: friends } = await db.prepare(`
        SELECT c.public_id AS id, c.nickname AS nickname, s.reduction_rate AS reductionRate,
               s.coverage AS coverage, COALESCE(s.valid_days, 0) AS validDays
        FROM friendship f
        JOIN sharing_consent c ON c.user_id = CASE WHEN f.user_a = ? THEN f.user_b ELSE f.user_a END
        LEFT JOIN standing s ON s.user_id = c.user_id
        WHERE f.user_a = ? OR f.user_b = ?
        ORDER BY c.nickname
    `).bind(userId, userId, userId).all();
    const { results: blocked } = await db.prepare(`
        SELECT c.public_id AS id, c.nickname AS nickname FROM block b
        JOIN sharing_consent c ON c.user_id = b.blocked WHERE b.blocker = ? ORDER BY c.nickname
    `).bind(userId).all();
    return json({ friends, blocked });
}

async function disconnect({ db, userId, params }) {
    const other = await userByPublicId(db, params[0]);
    if (other) {
        const [a, b] = pair(userId, other.user_id);
        await db.prepare('DELETE FROM friendship WHERE user_a = ? AND user_b = ?').bind(a, b).run();
    }
    return noContent();
}

async function block({ request, db, userId, now, params }) {
    if (!await consented(db, userId)) return error('sharing_consent_required', 403);
    const other = await userByPublicId(db, params[0]);
    if (!other || other.user_id === userId) return error('not_found', 404);
    if (request.method === 'DELETE') {
        // 차단을 풀어도 연결은 되살리지 않는다(다시 초대 코드로).
        await db.prepare('DELETE FROM block WHERE blocker = ? AND blocked = ?').bind(userId, other.user_id).run();
        return noContent();
    }
    const [a, b] = pair(userId, other.user_id);
    await db.batch([
        db.prepare('INSERT OR IGNORE INTO block (blocker, blocked, created_at_ms) VALUES (?, ?, ?)')
            .bind(userId, other.user_id, now),
        db.prepare('DELETE FROM friendship WHERE user_a = ? AND user_b = ?').bind(a, b),
    ]);
    return noContent();
}

/** 친구 경로표(index.mjs가 붙인다). */
export const friendRoutes = [
    { pattern: /^\/api\/v1\/invites$/, methods: ['POST'], handle: createInvite },
    { pattern: /^\/api\/v1\/invites\/([A-Za-z0-9]{1,16})\/accept$/, methods: ['POST'], handle: acceptInvite },
    { pattern: /^\/api\/v1\/friends$/, methods: ['GET'], handle: listFriends },
    { pattern: /^\/api\/v1\/friends\/([0-9a-f-]{1,64})$/, methods: ['DELETE'], handle: disconnect },
    { pattern: /^\/api\/v1\/blocks\/([0-9a-f-]{1,64})$/, methods: ['PUT', 'DELETE'], handle: block },
];
