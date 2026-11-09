const firebaseKeysUrl = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const signatureAlgorithm = { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' };
let cachedKeys;

function decodeBase64Url(value) {
    return Uint8Array.from(atob(value.replaceAll('-', '+').replaceAll('_', '/')), (char) => char.charCodeAt(0));
}

async function getPublicKey(keyId, now) {
    if (!cachedKeys || cachedKeys.expiresAt <= now) {
        const response = await fetch(firebaseKeysUrl);
        if (!response.ok) throw new Error('firebase_keys_unavailable');
        const { keys } = await response.json();
        const maxAge = /\bmax-age=(\d+)/i.exec(response.headers.get('Cache-Control') ?? '');
        const age = Number(response.headers.get('Age') ?? 0);
        cachedKeys = { keys, expiresAt: now + Math.max(0, Number(maxAge?.[1] ?? 0) - age) };
    }
    return cachedKeys.keys.find((key) => key.kid === keyId && key.kty === 'RSA');
}

export async function verifyFirebaseUser(authorization, projectId, now) {
    if (!authorization || authorization.length > 8192) return null;
    const match = /^Bearer ([\w-]+)\.([\w-]+)\.([\w-]+)$/i.exec(authorization);
    if (!match) return null;

    const [, encodedHeader, encodedClaims, encodedSignature] = match;
    const decoder = new TextDecoder('utf-8', { fatal: true });
    let header, claims, signature;
    try {
        header = JSON.parse(decoder.decode(decodeBase64Url(encodedHeader)));
        claims = JSON.parse(decoder.decode(decodeBase64Url(encodedClaims)));
        signature = decodeBase64Url(encodedSignature);
    } catch {
        return null;
    }

    const nowSeconds = Math.floor(now / 1000);
    if (header?.alg !== 'RS256' || typeof header.kid !== 'string' ||
        claims?.aud !== projectId || claims.iss !== `https://securetoken.google.com/${projectId}` ||
        typeof claims.sub !== 'string' || claims.sub.length === 0 || claims.sub.length > 128 ||
        !Number.isInteger(claims.exp) || claims.exp <= nowSeconds ||
        !Number.isInteger(claims.iat) || claims.iat > nowSeconds ||
        !Number.isInteger(claims.auth_time) || claims.auth_time > nowSeconds) return null;

    const jwk = await getPublicKey(header.kid, nowSeconds);
    if (!jwk) return null;
    const key = await crypto.subtle.importKey('jwk', jwk, signatureAlgorithm, false, ['verify']);
    const signedData = new TextEncoder().encode(`${encodedHeader}.${encodedClaims}`);
    const valid = await crypto.subtle.verify(signatureAlgorithm.name, key, signature, signedData);
    return valid ? claims.sub : null;
}
