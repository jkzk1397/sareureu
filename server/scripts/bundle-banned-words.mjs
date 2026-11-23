// docs/05_AI_SPEC.md 7절의 금지 표현 목록을 src/banned-words.mjs로 옮긴다(앱의 BannedWords와 같은 원본).
// 목록을 바꾸면 `node scripts/bundle-banned-words.mjs`를 한 번 실행한다. 시험이 어긋나면 알려 준다.
import { readFileSync, writeFileSync } from 'node:fs';

const start = '<!-- banned-words:start -->';
const end = '<!-- banned-words:end -->';

/** 문서에서 목록을 읽는다. 표시 사이의 "- 이름: 낱말, 낱말" 줄만 본다. */
export function readBannedWords(markdown = readFileSync(new URL('../../docs/05_AI_SPEC.md', import.meta.url), 'utf8')) {
    const from = markdown.indexOf(start);
    const to = markdown.indexOf(end);
    if (from < 0 || to < from) throw new Error('banned-words block not found in docs/05_AI_SPEC.md');
    return markdown.slice(from + start.length, to).split('\n')
        .filter((line) => line.trim().startsWith('- ') && line.includes(':'))
        .flatMap((line) => line.slice(line.indexOf(':') + 1).split(','))
        .map((word) => word.trim()).filter(Boolean);
}

export function bundle() {
    const words = readBannedWords();
    return '// 자동 생성: node scripts/bundle-banned-words.mjs (직접 고치지 않는다. 원본은 docs/05_AI_SPEC.md 7절)\n' +
        `export const bannedWords = ${JSON.stringify(words)};\n`;
}

if (process.argv[1]?.endsWith('bundle-banned-words.mjs')) {
    writeFileSync(new URL('../src/banned-words.mjs', import.meta.url), bundle());
}
