// Entry point bundled into app/src/main/assets/adblock/adblocker.js.
// Exposes a tiny string-in/string-out API to Kotlin (web/AdBlocker.kt) as `globalThis.webble`.
import './polyfills.js';
import { FiltersEngine, Request } from '@ghostery/adblocker';
import { parse } from 'tldts-experimental';

let engine = null;

function hostAndDomain(url) {
  const parsed = parse(url);
  return { hostname: parsed.hostname || '', domain: parsed.domain || '' };
}

globalThis.webble = {
  /** Loads a serialized engine (engine.bin). */
  load(buffer) {
    engine = FiltersEngine.deserialize(new Uint8Array(buffer));
    return 'ok';
  },

  /**
   * Returns "block", "allow" or "redirect:<content type>\n<body>". The body is base64 when the
   * content type ends with ";base64". (Not the library's dataUrl: it needs btoa, missing here.)
   */
  match(url, sourceUrl, type) {
    if (engine === null) return 'allow';
    const request = Request.fromRawDetails({ url, sourceUrl, type });
    const { match, redirect } = engine.match(request);
    if (redirect !== undefined) return 'redirect:' + redirect.contentType + '\n' + redirect.body;
    return match ? 'block' : 'allow';
  },

  /** CSS hiding ad slots on the page at [url], or "" when there is nothing to hide. */
  cosmetics(url) {
    if (engine === null) return '';
    const { hostname, domain } = hostAndDomain(url);
    if (!hostname) return '';
    const { styles } = engine.getCosmeticsFilters({
      url,
      hostname,
      domain,
      getBaseRules: true,
      getInjectionRules: false,
      getExtendedRules: false,
      getRulesFromDOM: false,
      getRulesFromHostname: true,
    });
    return styles || '';
  },
};
