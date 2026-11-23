// 신고(ADR-0014, Google Play 사용자 생성 콘텐츠 정책). 앱 안에서 친구·연결 요청·잔소리를 신고하면 여기 남는다.
// 운영자는 D1에서 본다(server/README.md "신고 보기"). 서로 다른 3명에게 신고된 계정은 잔소리 보내기가 멈춘다.
// 신고한 문장은 저장하지만 로그에는 남기지 않는다.
import { error, noContent, readBody, tooMany } from './http.mjs';
import { limits } from './limits.mjs';
import { take } from './ratelimit.mjs';
import { cleanText, textLength } from './text.mjs';

const publicIdPattern = /^[0-9a-f-]{36}$/;
/** 사유 종류: 잔소리 내용, 닉네임, 괴롭힘(반복 요청·잔소리), 그 밖. */
export const reportReasons = ['POKE', 'NICKNAME', 'HARASSMENT', 'OTHER'];

/** `(?, ?, ?)` = (계정, 이 시각 이후, 사람 수): 최근 신고한 사람이 서로 다른 N명 이상이면 참인 SQL 조각. */
export const suspendedSql =
    '((SELECT COUNT(DISTINCT reporter) FROM report WHERE target = ? AND created_at_ms > ?) >= ?)';

function validReport(body) {
    if (!body || Object.keys(body).some((key) => !['target', 'requestId', 'reason', 'text'].includes(key))) return false;
    const { target, requestId, reason, text } = body;
    // 대상은 친구 공개 ID나 받은 연결 요청 ID 중 하나만.
    if ((typeof target === 'string') === (typeof requestId === 'string')) return false;
    if (!publicIdPattern.test(target ?? requestId)) return false;
    return reportReasons.includes(reason) && (text === undefined || text === null || typeof text === 'string');
}

/** 신고할 수 있는 상대인지: 친구, 내가 차단한 사람, 나에게 연결 요청을 보낸 사람. 공개 ID를 아무거나 넣어 신고할 수 없다. */
async function targetOf(db, userId, body) {
    if (body.requestId) {
        const request = await db.prepare('SELECT from_user FROM friend_request WHERE id = ? AND to_user = ?')
            .bind(body.requestId, userId).first();
        return request?.from_user ?? null;
    }
    const row = await db.prepare(`
        SELECT c.user_id AS id FROM sharing_consent c
        WHERE c.public_id = ? AND c.user_id <> ? AND (
            EXISTS (SELECT 1 FROM friendship f WHERE (f.user_a = c.user_id AND f.user_b = ?)
                OR (f.user_a = ? AND f.user_b = c.user_id))
            OR EXISTS (SELECT 1 FROM block b WHERE b.blocker = ? AND b.blocked = c.user_id))
    `).bind(body.target, userId, userId, userId, userId).first();
    return row?.id ?? null;
}

async function createReport({ request, db, userId, now }) {
    const body = await readBody(request);
    if (!validReport(body)) return error('invalid_report', 400);
    const text = typeof body.text === 'string' ? cleanText(body.text) : '';
    if (textLength(text) > limits.pokeMaxLength) return error('invalid_report', 400);
    const me = await db.prepare('SELECT 1 FROM sharing_consent WHERE user_id = ?').bind(userId).first();
    if (!me) return error('sharing_consent_required', 403);
    const target = await targetOf(db, userId, body);
    if (!target) return error('not_found', 404);
    const gate = await take(db, userId, 'report', now, [{ windowMs: limits.dayMs, max: limits.reportsPerDay }]);
    if (!gate.ok) return tooMany('daily_limit', gate.retryAfterSec);
    await db.prepare('INSERT INTO report (id, reporter, target, reason, text, created_at_ms) VALUES (?, ?, ?, ?, ?, ?)')
        .bind(crypto.randomUUID(), userId, target, body.reason, text || null, now).run();
    // 누가 몇 번 신고됐는지는 신고한 사람에게 알리지 않는다.
    return noContent();
}

/** 신고 경로표(index.mjs가 붙인다). */
export const reportRoutes = [
    { pattern: /^\/api\/v1\/reports$/, methods: ['POST'], handle: createReport, ipLimit: true },
];

/** cron: 오래된 신고 정리. */
export async function cleanReports(db, now) {
    await db.prepare('DELETE FROM report WHERE created_at_ms <= ?').bind(now - limits.reportRetentionMs).run();
}
