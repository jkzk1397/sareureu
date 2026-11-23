// 6단계: 초대 QR이 여는 안내 페이지(로그인 없이 GET /invite/{코드}).
// 안드로이드 브라우저는 intent:// 주소로 앱을 연다. 앱이 없으면 브라우저가 S.browser_fallback_url(설치 파일)로 간다.
// 앱은 sareureu://invite/{코드}를 받아 친구 탭에서 확인 창을 띄우고, 사용자가 누르면 연결을 요청한다(ADR-0014).
// 코드 외에는 아무것도 담지 않는다. 코드가 살아 있는지는 여기서 확인하지 않는다(모양만 본다).
import { codePattern } from './friends.mjs';

/** 초대 페이지 경로. */
export const invitePagePattern = /^\/invite\/([A-Za-z0-9]{1,16})$/;

/** 앱이 없을 때 받을 설치 파일. wrangler.toml의 APK_URL로 바꿀 수 있다. */
export const defaultApkUrl = 'https://github.com/jkzk1397/sareureu/releases/download/v0.13/sareureu-week13.apk';

const appPackage = 'com.capstone.pause';
const appScheme = 'sareureu';

function escapeHtml(text) {
    return text.replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
}

/** 설치 파일 주소: https만 받는다. */
function apkUrl(env) {
    const url = env?.APK_URL;
    return typeof url === 'string' && url.startsWith('https://') ? url : defaultApkUrl;
}

/** 앱을 여는 intent 주소(앱이 없으면 [apk]로). */
export function intentUrl(code, apk) {
    return `intent://invite/${code}#Intent;scheme=${appScheme};package=${appPackage};` +
        `S.browser_fallback_url=${encodeURIComponent(apk)};end`;
}

/** 안내 페이지. 코드가 틀리면 404 페이지. */
export function invitePage(rawCode, env) {
    const code = rawCode.toUpperCase();
    const apk = apkUrl(env);
    const valid = codePattern.test(code);
    const open = valid ? intentUrl(code, apk) : null;
    const body = valid ? `
    <h1>사르르 친구 초대</h1>
    <p>앱이 열리면 연결을 요청할지 물어봐. 친구가 수락하면 연결돼.</p>
    <p class="code" aria-label="초대 코드">${escapeHtml(code)}</p>
    <a class="button primary" href="${escapeHtml(open)}">앱에서 열기</a>
    <a class="button" href="${escapeHtml(apk)}">앱 설치하기 (APK)</a>
    <p class="hint">앱이 열리지 않으면 설치한 뒤 이 QR을 다시 찍거나, 친구 탭에 코드를 직접 넣어 줘.</p>` : `
    <h1>초대 코드를 찾을 수 없어</h1>
    <p>친구에게 새 QR을 받아 줘.</p>
    <a class="button" href="${escapeHtml(apk)}">앱 설치하기 (APK)</a>`;
    const redirect = valid ? `<script>
    if (/Android/i.test(navigator.userAgent)) location.replace(${JSON.stringify(open)});
    </script>` : '';
    const html = `<!doctype html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex">
<title>사르르 친구 초대</title>
<style>
  :root { color-scheme: light dark; --bg: #F3F3EE; --surface: #FFFFFF; --ink: #141416; --ink2: #55555E; --accent: #C6F24E; }
  @media (prefers-color-scheme: dark) { :root { --bg: #0E0E11; --surface: #1A1A1F; --ink: #F2F2EC; --ink2: #B4B4BD; } }
  body { margin: 0; background: var(--bg); color: var(--ink); font-family: system-ui, sans-serif; }
  main { max-width: 420px; margin: 0 auto; padding: 48px 16px; display: flex; flex-direction: column; gap: 16px; }
  h1 { font-size: 24px; margin: 0; }
  p { margin: 0; color: var(--ink2); line-height: 1.5; }
  .code { font-size: 32px; letter-spacing: 6px; color: var(--ink); background: var(--surface); padding: 16px;
    border-radius: 16px; text-align: center; font-variant-numeric: tabular-nums; }
  .button { display: block; text-align: center; padding: 14px; border-radius: 999px; text-decoration: none;
    color: var(--ink); border: 1px solid var(--ink2); min-height: 20px; }
  .button.primary { background: var(--accent); color: #141416; border-color: var(--accent); font-weight: 600; }
  .hint { font-size: 14px; }
</style>
</head>
<body>
<main>${body}
</main>
${redirect}
</body>
</html>
`;
    return new Response(html, {
        status: valid ? 200 : 404,
        headers: {
            'Content-Type': 'text/html; charset=utf-8',
            'Cache-Control': 'no-store',
            'Referrer-Policy': 'no-referrer',
            'Content-Security-Policy': "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'",
        },
    });
}
