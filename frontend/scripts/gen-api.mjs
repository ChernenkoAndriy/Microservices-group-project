// Generates TypeScript types from the OpenAPI contracts (api-contracts/ is the source of truth).
// Output: src/api/generated/<service>.ts. Run: npm run gen:api
import { execFileSync } from 'node:child_process';
import { mkdirSync, existsSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const contracts = process.env.API_CONTRACTS_DIR ?? resolve(here, '..', '..', 'api-contracts');
const out = resolve(here, '..', 'src', 'api', 'generated');
const services = ['auth', 'catalog', 'library', 'streaming', 'recommendation', 'notification'];

if (!existsSync(contracts)) {
  console.log(`No contracts at ${contracts}; keeping the committed generated types.`);
  process.exit(0);
}
mkdirSync(out, { recursive: true });
const bin = resolve(here, '..', 'node_modules', 'openapi-typescript', 'bin', 'cli.js');
for (const s of services) {
  const input = join(contracts, `${s}-service`, 'openapi.yaml');
  execFileSync(process.execPath, [bin, input, '-o', join(out, `${s}.ts`)], { stdio: 'inherit' });
}
