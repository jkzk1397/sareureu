// 사람이 쓴 글(닉네임·잔소리) 다듬기와 금지 표현 검사. 앱도 같은 검사를 하지만 서버를 직접 부를 수 있으니 서버도 한다.
// 금지 표현 목록 원본은 docs/05_AI_SPEC.md 7절이고, banned-words.mjs는 거기서 만든다(scripts/bundle-banned-words.mjs).
import { bannedWords } from './banned-words.mjs';

// 줄바꿈·탭은 빈칸으로.
const lineBreaks = new RegExp('[\t\n\v\f\r\u0085\u2028\u2029]', 'g');
// 제어 문자, 폭 없는 문자, 방향 바꿈 문자, 한글 채움 문자, 변형 선택자 등 눈에 보이지 않는 것.
const invisible = new RegExp('[\\u0000-\\u001F\\u007F-\\u009F\\u00AD\\u034F\\u061C\\u115F\\u1160\\u17B4\\u17B5' +
    '\\u180B-\\u180F\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\u3164\\uFE00-\\uFE0F\\uFEFF\\uFFA0\\uFFF9-\\uFFFB]' +
    '|\\uDB40[\\uDC00-\\uDDEF]', 'g');

/** 저장·전달용으로 다듬는다: NFC, 줄바꿈은 빈칸, 보이지 않는 문자 제거, 빈칸 여러 개는 하나, 앞뒤 빈칸 제거. */
export function cleanText(raw) {
    return raw.normalize('NFC').replace(lineBreaks, ' ').replace(invisible, '').replace(/\s+/g, ' ').trim();
}

/** 글자 수(유니코드 코드 포인트 기준). */
export function textLength(text) {
    return [...text].length;
}

// 비교용: 호환 정규화(전각·반각 등) 뒤 소문자, 글자와 숫자만 남긴다(빈칸·특수문자를 사이에 넣은 우회를 잡는다).
function comparable(text) {
    return text.normalize('NFKC').toLowerCase().replace(/[^\p{L}\p{N}]/gu, '');
}

const comparableWords = bannedWords.map(comparable).filter(Boolean);

/** 금지 표현이 들어 있는지. 단어 필터는 완벽하지 않아 신고·차단과 함께 쓴다(ADR-0014). */
export function hasBannedWord(text) {
    const target = comparable(text);
    return comparableWords.some((word) => target.includes(word));
}
