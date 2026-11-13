// Builds the ad blocker shipped with Webble:
//  - adblocker.js: @ghostery/adblocker bundled for a bare JavaScriptSandbox isolate
//  - engine.bin:   Ghostery's prebuilt ads + tracking filters, serialized
// Run `npm install && npm run build`, then rebuild the app. Rerun to refresh the filter lists.
import { build } from 'esbuild';
import { FiltersEngine, Request } from '@ghostery/adblocker';
import { copyFileSync, mkdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '../../app/src/main/assets/adblock');
mkdirSync(out, { recursive: true });

await build({
  entryPoints: [join(here, 'entry.js')],
  outfile: join(out, 'adblocker.js'),
  bundle: true,
  format: 'iife',
  platform: 'neutral',
  mainFields: ['module', 'main'],
  target: 'es2020',
  minify: true,
  legalComments: 'none',
  banner: {
    js: '/* @ghostery/adblocker (MPL-2.0, see LICENSE) bundled for Webble by tools/adblock */',
  },
});

const engine = await FiltersEngine.fromPrebuiltAdsAndTracking(fetch);
writeFileSync(join(out, 'engine.bin'), engine.serialize());
copyFileSync(join(here, 'node_modules/@ghostery/adblocker/LICENSE'), join(out, 'LICENSE'));

// Smoke test in a context with only ECMAScript built-ins, like the Android sandbox.
const context = vm.createContext({});
vm.runInContext(readFileSync(join(out, 'adblocker.js'), 'utf8'), context);
const bin = readFileSync(join(out, 'engine.bin'));
context.buffer = bin.buffer.slice(bin.byteOffset, bin.byteOffset + bin.byteLength);
vm.runInContext('webble.load(buffer)', context);
const check = (expr, expected) => {
  const got = vm.runInContext(expr, context);
  const ok = expected instanceof RegExp ? expected.test(got) : got === expected;
  if (!ok) throw new Error(`${expr} returned ${String(got).slice(0, 80)}`);
};
check(`webble.match('https://www.googletagservices.com/tag/js/gpt.js', 'https://example.com/', 'script')`, /^(block|redirect:)/);
check(`webble.match('https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js', 'https://example.com/', 'script')`, /^(block|redirect:)/);
check(`webble.match('https://example.com/style.css', 'https://example.com/', 'stylesheet')`, 'allow');
check(`webble.match('https://upload.wikimedia.org/x.png', 'https://en.wikipedia.org/', 'image')`, 'allow');
check(`webble.match('https://www.google-analytics.com/analytics.js', 'https://example.com/', 'script')`, /^redirect:application\/javascript\n\(function/);
check(`typeof webble.cosmetics('https://www.example.com/')`, 'string');

const size = (f) => `${(statSync(join(out, f)).size / 1024).toFixed(0)} KiB`;
console.log(`adblocker.js ${size('adblocker.js')}, engine.bin ${size('engine.bin')}: smoke test passed`);
