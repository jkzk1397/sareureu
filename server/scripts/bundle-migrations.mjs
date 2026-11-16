// migrations/*.sql을 src/migrations.mjs로 묶는다(서버가 스스로 표를 만들 때 쓴다).
// 마이그레이션을 추가하면 `node scripts/bundle-migrations.mjs`를 한 번 실행한다. 시험이 어긋나면 알려 준다.
import { readdirSync, readFileSync, writeFileSync } from 'node:fs';

export function bundle() {
    const dir = new URL('../migrations/', import.meta.url);
    const files = readdirSync(dir).filter((name) => name.endsWith('.sql')).sort();
    const entries = files.map((name) => `    { name: ${JSON.stringify(name)}, sql: ${JSON.stringify(readFileSync(new URL(name, dir), 'utf8'))} },`);
    return `// 자동 생성: node scripts/bundle-migrations.mjs (직접 고치지 않는다)\nexport const migrations = [\n${entries.join('\n')}\n];\n`;
}

if (process.argv[1]?.endsWith('bundle-migrations.mjs')) {
    writeFileSync(new URL('../src/migrations.mjs', import.meta.url), bundle());
}
