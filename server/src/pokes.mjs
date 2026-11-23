// 임시(5-4): 잔소리 맡기기·받기. 팀원 서버가 바뀌면 달라질 수 있다(server/README.md).
// 금지 표현 검사는 보내기 전에 앱이 한다(BannedWords). 서버는 길이·친구 관계·차단·하루 한도만 본다.
// 6단계(ADR-0012): 지금 시청 중인 친구에게만 보낼 수 있다. 받는 쪽 앱이 '시청 중' 신호(presence.mjs)의 응답으로
// 몇 초 안에 받아 알림으로 띄운다.
import { error, json, readBody } from './http.mjs';
import { isWatching } from './presence.mjs';

const dayMs = 86_400_000;
const pokeTtlMs = 7 * dayMs;
const maxLength = 40;
const maxPerDay = 10;
const maxInbox = 20;
const publicIdPattern = /^[0-9a-f-]{36}$/;

async function sendPoke({ request, db, userId, now }) {
    const body = await readBody(request);
    if (!body || Object.keys(body).some((key) => !['to', 'text'].includes(key)) ||
        typeof body.to !== 'string' || !publicIdPattern.test(body.to) || typeof body.text !== 'string') {
        return error('invalid_poke', 400);
    }
    const text = body.text.trim();
    if (text.length === 0 || [...text].length > maxLength) return error('invalid_poke', 400);

    const to = await db.prepare('SELECT user_id FROM sharing_consent WHERE public_id = ?').bind(body.to).first();
    if (!to || to.user_id === userId) return error('not_friends', 403);
    const [a, b] = userId < to.user_id ? [userId, to.user_id] : [to.user_id, userId];
    const friends = await db.prepare('SELECT 1 FROM friendship WHERE user_a = ? AND user_b = ?').bind(a, b).first();
    // 차단하면 연결이 끊기므로 친구가 아니면 보낼 수 없다.
    if (!friends) return error('not_friends', 403);
    if (!await isWatching(db, to.user_id, now)) return error('not_watching', 409);
    const sent = await db.prepare('SELECT COUNT(*) AS n FROM poke_sent WHERE from_user = ? AND sent_at_ms > ?')
        .bind(userId, now - dayMs).first();
    if (sent.n >= maxPerDay) return error('daily_limit', 429);

    const id = crypto.randomUUID();
    await db.batch([
        db.prepare(`
            INSERT INTO poke (id, from_user, to_user, text, created_at_ms, expires_at_ms) VALUES (?, ?, ?, ?, ?, ?)
        `).bind(id, userId, to.user_id, text, now, now + pokeTtlMs),
        db.prepare('INSERT INTO poke_sent (from_user, sent_at_ms) VALUES (?, ?)').bind(userId, now),
    ]);
    return json({ id }, 201);
}

/** 나에게 온 잔소리를 꺼내고 서버에서 지운다(GET /pokes와 시청 중 신호 응답이 함께 쓴다). */
export async function takePokes(db, userId, now) {
    const { results } = await db.prepare(`
        SELECT p.id AS id, c.public_id AS fromId, c.nickname AS fromNickname, p.text AS text,
               p.created_at_ms AS createdAtMs, p.expires_at_ms AS expiresAtMs
        FROM poke p JOIN sharing_consent c ON c.user_id = p.from_user
        WHERE p.to_user = ? AND p.expires_at_ms > ?
          AND NOT EXISTS (SELECT 1 FROM block b WHERE b.blocker = p.to_user AND b.blocked = p.from_user)
        ORDER BY p.created_at_ms LIMIT ?
    `).bind(userId, now, maxInbox).all();
    // 한 번 내려 주면 서버에서 지운다(받는 사람 폰에만 남는다). 차단한 사람 것과 만료된 것도 함께 지운다.
    await db.prepare(`
        DELETE FROM poke WHERE to_user = ? AND (
            expires_at_ms <= ? OR id IN (SELECT value FROM json_each(?)) OR
            from_user IN (SELECT blocked FROM block WHERE blocker = ?))
    `).bind(userId, now, JSON.stringify(results.map((p) => p.id)), userId).run();
    return results;
}

async function receivePokes({ db, userId, now }) {
    return json({ pokes: await takePokes(db, userId, now) });
}

/** 잔소리 경로표(index.mjs가 붙인다). */
export const pokeRoutes = [
    { pattern: /^\/api\/v1\/pokes$/, methods: ['POST', 'GET'],
        handle: (ctx) => (ctx.request.method === 'POST' ? sendPoke(ctx) : receivePokes(ctx)) },
];

/** cron: 만료된 잔소리 정리. */
export async function cleanPokes(db, now) {
    await db.prepare('DELETE FROM poke WHERE expires_at_ms <= ?').bind(now).run();
    await db.prepare('DELETE FROM poke_sent WHERE sent_at_ms <= ?').bind(now - dayMs).run();
}

