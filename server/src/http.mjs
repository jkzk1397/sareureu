const maxBodyBytes = 2048;

/** 오류 응답 `{ "error": code }`. 캐시하지 않는다. */
export function error(code, status) {
    return Response.json({ error: code }, { status, headers: { 'Cache-Control': 'no-store' } });
}

/** 본문 없는 성공. */
export function noContent() {
    return new Response(null, { status: 204, headers: { 'Cache-Control': 'no-store' } });
}

/** JSON 성공 응답. */
export function json(data, status = 200) {
    return Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
}

/** JSON 객체 본문(2KB까지). 아니면 null. */
export async function readBody(request) {
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
