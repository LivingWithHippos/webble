// JavaScriptSandbox isolates only have plain ECMAScript built-ins: no Web APIs.
// The adblocker needs UTF-8 TextEncoder/TextDecoder, so provide minimal ones.

if (typeof globalThis.TextEncoder === 'undefined') {
  class TextEncoder {
    get encoding() {
      return 'utf-8';
    }

    encode(str = '') {
      const out = new Uint8Array(str.length * 3);
      return out.subarray(0, this.encodeInto(str, out).written);
    }

    encodeInto(str, dest) {
      let read = 0;
      let written = 0;
      const len = str.length;
      while (read < len) {
        let code = str.charCodeAt(read);
        let units = 1;
        if (code >= 0xd800 && code <= 0xdbff && read + 1 < len) {
          const next = str.charCodeAt(read + 1);
          if (next >= 0xdc00 && next <= 0xdfff) {
            code = 0x10000 + ((code - 0xd800) << 10) + (next - 0xdc00);
            units = 2;
          }
        }
        if (code >= 0xd800 && code <= 0xdfff) code = 0xfffd; // lone surrogate
        const size = code < 0x80 ? 1 : code < 0x800 ? 2 : code < 0x10000 ? 3 : 4;
        if (written + size > dest.length) break;
        if (size === 1) {
          dest[written++] = code;
        } else if (size === 2) {
          dest[written++] = 0xc0 | (code >> 6);
          dest[written++] = 0x80 | (code & 0x3f);
        } else if (size === 3) {
          dest[written++] = 0xe0 | (code >> 12);
          dest[written++] = 0x80 | ((code >> 6) & 0x3f);
          dest[written++] = 0x80 | (code & 0x3f);
        } else {
          dest[written++] = 0xf0 | (code >> 18);
          dest[written++] = 0x80 | ((code >> 12) & 0x3f);
          dest[written++] = 0x80 | ((code >> 6) & 0x3f);
          dest[written++] = 0x80 | (code & 0x3f);
        }
        read += units;
      }
      return { read, written };
    }
  }
  globalThis.TextEncoder = TextEncoder;
}

if (typeof globalThis.TextDecoder === 'undefined') {
  class TextDecoder {
    get encoding() {
      return 'utf-8';
    }

    decode(input) {
      if (input === undefined) return '';
      const bytes = input instanceof Uint8Array
        ? input
        : new Uint8Array(input.buffer || input, input.byteOffset || 0, input.byteLength);
      const chunks = [];
      let units = [];
      let i = 0;
      const len = bytes.length;
      while (i < len) {
        const b = bytes[i++];
        let code;
        if (b < 0x80) {
          code = b;
        } else if (b >= 0xc0 && b < 0xe0 && i < len) {
          code = ((b & 0x1f) << 6) | (bytes[i++] & 0x3f);
        } else if (b >= 0xe0 && b < 0xf0 && i + 1 < len) {
          code = ((b & 0x0f) << 12) | ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f);
        } else if (b >= 0xf0 && i + 2 < len) {
          code = ((b & 0x07) << 18) | ((bytes[i++] & 0x3f) << 12) |
            ((bytes[i++] & 0x3f) << 6) | (bytes[i++] & 0x3f);
        } else {
          code = 0xfffd;
        }
        if (code > 0xffff) {
          code -= 0x10000;
          units.push(0xd800 + (code >> 10), 0xdc00 + (code & 0x3ff));
        } else {
          units.push(code);
        }
        if (units.length >= 8192) {
          chunks.push(String.fromCharCode.apply(null, units));
          units = [];
        }
      }
      chunks.push(String.fromCharCode.apply(null, units));
      return chunks.join('');
    }
  }
  globalThis.TextDecoder = TextDecoder;
}
