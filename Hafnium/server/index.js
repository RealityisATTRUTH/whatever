var __defProp = Object.defineProperty;
var __name = (target, value) => __defProp(target, "name", { value, configurable: true });

// src/index.js
import { DurableObject } from "cloudflare:workers";

// src/mojang-keys.json
var mojang_keys_default = {
  fetched: "2026-11-03",
  profilePropertyKeys: [
    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAra4Y2wu3rWEW7cDTDRRd4IvUD140Y12SaG3k4V3UwT/pDnnX5itOcYiZA0qf4VCpJDp2PifOL+Pr/ph/G9/6ZoIxkBeGENo+S7i9BqizJy9cmZocpyx+RkZaw9+frCGNLuYLrxziNWiXFACJSg2mHACR7+6NkGN8d/16/3PxMnvGSyLT7JKGUgqj1Q3oW7k+NLXR9sw6oRELOcnUvZVa2bcglv8vlcyPqqnBhydLfHI85Z5WnIYZviZ3Bb4dv5Fme726BGOtEY7kz40RfiwjT3xYKYKPJUS3/crPX6eugmWyrWdddKaePrW88bp17Z5NIStlJ5KJJk4coha8O+P7onDqmbHwLqPTeR51njkgZ+DJWT6fz8ku9OWQn6I/FxqN14iYIghDJijmKvEwsI7FJ5X2ttPXEvBYLmpj2j0lQQcUIqH7hkiZ+mCW0GYawJgbAeNAraM9sP+76MyAGITtAsXv1IQmah+7OeDJOToG2Kb1Dl0Va+HiP9MPpcnO7kbn6dqAyhNvRNmHnsUOiEcLhW9Rk7xz87IBV/cGKbUDgxu8cYY0P512DWt5+Jmr8W10FDFdLmkJt1taWxNxApM2CiFPCimk02koyLZDW9nqpWNw6qS/TOYPdz438qEuamtYUJ+u6WhBjK8xAJEAt5k3gDKX+nlTiG3N6se09D62fS8CAwEAAQ=="
  ],
  playerCertificateKeys: [
    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
    "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ=="
  ]
};

// src/verify.js
var enc = new TextEncoder();
var CHALLENGE_PREFIX = "hafnium-login:";
var PROFILE_MAX_AGE_MS = 15 * 60 * 1e3;
var keyCache = /* @__PURE__ */ new Map();
function bytes(base64) {
  const bin = atob(base64);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}
__name(bytes, "bytes");
async function rsaKey(base64Der, hash) {
  const id = hash + ":" + base64Der;
  if (!keyCache.has(id)) {
    keyCache.set(id, crypto.subtle.importKey(
      "spki",
      bytes(base64Der),
      { name: "RSASSA-PKCS1-v1_5", hash },
      false,
      ["verify"]
    ));
  }
  return keyCache.get(id);
}
__name(rsaKey, "rsaKey");
async function signedByAny(keys, signature, data) {
  for (const k of keys) {
    try {
      if (await crypto.subtle.verify("RSASSA-PKCS1-v1_5", await rsaKey(k, "SHA-1"), signature, data)) return true;
    } catch {
    }
  }
  return false;
}
__name(signedByAny, "signedByAny");
function certificatePayload(uuidHex, expiresAt, publicKeyDer) {
  const out = new Uint8Array(24 + publicKeyDer.length);
  for (let i = 0; i < 16; i++) out[i] = parseInt(uuidHex.substr(i * 2, 2), 16);
  new DataView(out.buffer).setBigInt64(16, BigInt(expiresAt), false);
  out.set(publicKeyDer, 24);
  return out;
}
__name(certificatePayload, "certificatePayload");
async function verifyProfile(t, keys, now = Date.now(), maxAge = PROFILE_MAX_AGE_MS) {
  if (!t || typeof t.value !== "string" || typeof t.signature !== "string") throw new Error("no_profile");
  if (!await signedByAny(keys.profilePropertyKeys, bytes(t.signature), enc.encode(t.value))) throw new Error("profile_invalid");
  let profile;
  try {
    profile = JSON.parse(new TextDecoder().decode(bytes(t.value)));
  } catch {
    throw new Error("profile_invalid");
  }
  if (typeof profile.profileName !== "string" || !/^[A-Za-z0-9_]{1,16}$/.test(profile.profileName)) throw new Error("profile_invalid");
  if (typeof profile.timestamp !== "number" || Math.abs(now - profile.timestamp) > maxAge) throw new Error("profile_old");
  return profile;
}
__name(verifyProfile, "verifyProfile");
async function verifyLogin(msg, challenge, keys, now = Date.now()) {
  const uuid = msg.uuid;
  if (typeof uuid !== "string" || !/^[0-9a-f]{32}$/.test(uuid)) throw new Error("bad_uuid");
  const profile = await verifyProfile(msg.textures, keys, now);
  if (profile.profileId !== uuid) throw new Error("profile_mismatch");
  if (typeof msg.publicKey !== "string" || typeof msg.keySignature !== "string" || typeof msg.expiresAt !== "number") {
    throw new Error("no_certificate");
  }
  if (msg.expiresAt <= now) throw new Error("certificate_expired");
  const publicKeyDer = bytes(msg.publicKey);
  const payload = certificatePayload(uuid, msg.expiresAt, publicKeyDer);
  if (!await signedByAny(keys.playerCertificateKeys, bytes(msg.keySignature), payload)) throw new Error("certificate_invalid");
  if (typeof msg.signature !== "string") throw new Error("signature_invalid");
  const playerKey = await crypto.subtle.importKey("spki", publicKeyDer, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
  const ok = await crypto.subtle.verify("RSASSA-PKCS1-v1_5", playerKey, bytes(msg.signature), enc.encode(CHALLENGE_PREFIX + challenge));
  if (!ok) throw new Error("signature_invalid");
  return { uuid, name: profile.profileName };
}
__name(verifyLogin, "verifyLogin");
// Sign-in for players without a chat certificate (No Chat Reports, or UK accounts
// without Microsoft's age check). The game tells Mojang it joined a "server" named
// after the challenge, the same way joining any Minecraft server works. Mojang blocks
// Cloudflare, so the check goes through the sign-in helper: a small program on another
// computer that keeps a connection open to this server and asks Mojang for it.
async function verifyJoin(msg, name, challenge, ask) {
  const uuid = msg.uuid;
  if (typeof uuid !== "string" || !/^[0-9a-f]{32}$/.test(uuid)) throw new Error("bad_uuid");
  if (typeof name !== "string" || !/^[A-Za-z0-9_]{1,16}$/.test(name)) throw new Error("bad_name");
  const digest = await crypto.subtle.digest("SHA-1", enc.encode(CHALLENGE_PREFIX + challenge));
  const serverId = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
  const answer = await ask(name, serverId);
  if (answer.status === 204) throw new Error("not_joined");
  if (answer.status !== 200 || !answer.profile) throw new Error("verifier_failed");
  const profile = answer.profile;
  if (profile.id !== uuid || typeof profile.name !== "string") throw new Error("profile_mismatch");
  return { uuid, name: profile.name };
}
__name(verifyJoin, "verifyJoin");
function sameText(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}
__name(sameText, "sameText");

// src/images.js
async function inflate(bytes2) {
  const stream = new Blob([bytes2]).stream().pipeThrough(new DecompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}
__name(inflate, "inflate");
async function deflate(bytes2) {
  const stream = new Blob([bytes2]).stream().pipeThrough(new CompressionStream("deflate"));
  return new Uint8Array(await new Response(stream).arrayBuffer());
}
__name(deflate, "deflate");
async function gunzip(bytes2, limit) {
  const reader = new Blob([bytes2]).stream().pipeThrough(new DecompressionStream("gzip")).getReader();
  const parts = [];
  let total = 0;
  for (; ; ) {
    const { done, value } = await reader.read();
    if (done) break;
    total += value.length;
    if (total > limit) throw new Error("too_big_unpacked");
    parts.push(value);
  }
  const out = new Uint8Array(total);
  let at = 0;
  for (const p of parts) {
    out.set(p, at);
    at += p.length;
  }
  return out;
}
__name(gunzip, "gunzip");
async function decodePng(bytes2) {
  const sig = [137, 80, 78, 71, 13, 10, 26, 10];
  if (bytes2.length < 33 || sig.some((v, i) => bytes2[i] !== v)) return null;
  const view = new DataView(bytes2.buffer, bytes2.byteOffset, bytes2.byteLength);
  let pos = 8, w = 0, h = 0, depth = 0, color = 0;
  const idat = [];
  let palette = null, trns = null;
  while (pos + 8 <= bytes2.length) {
    const len = view.getUint32(pos);
    const type = String.fromCharCode(...bytes2.subarray(pos + 4, pos + 8));
    const data = bytes2.subarray(pos + 8, pos + 8 + len);
    if (type === "IHDR") {
      w = view.getUint32(pos + 8);
      h = view.getUint32(pos + 12);
      depth = bytes2[pos + 16];
      color = bytes2[pos + 17];
      if (bytes2[pos + 20] !== 0) return null;
    } else if (type === "PLTE") palette = data;
    else if (type === "tRNS") trns = data;
    else if (type === "IDAT") idat.push(data);
    else if (type === "IEND") break;
    pos += 12 + len;
  }
  if (depth !== 8 || w > 1024 || h > 1024 || !w || !h) return null;
  const channels = { 2: 3, 6: 4, 3: 1, 0: 1, 4: 2 }[color];
  if (!channels) return null;
  const joined = new Uint8Array(idat.reduce((n, d) => n + d.length, 0));
  let at = 0;
  for (const d of idat) {
    joined.set(d, at);
    at += d.length;
  }
  const raw = await inflate(joined);
  const stride = w * channels;
  if (raw.length < (stride + 1) * h) return null;
  const px = new Uint8Array(stride * h);
  for (let y = 0; y < h; y++) {
    const f = raw[y * (stride + 1)];
    const line = raw.subarray(y * (stride + 1) + 1, (y + 1) * (stride + 1));
    for (let x = 0; x < stride; x++) {
      const a = x >= channels ? px[y * stride + x - channels] : 0;
      const b = y > 0 ? px[(y - 1) * stride + x] : 0;
      const c = x >= channels && y > 0 ? px[(y - 1) * stride + x - channels] : 0;
      let v = line[x];
      if (f === 1) v += a;
      else if (f === 2) v += b;
      else if (f === 3) v += a + b >> 1;
      else if (f === 4) {
        const p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        v += pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
      }
      px[y * stride + x] = v & 255;
    }
  }
  const rgba = new Uint8Array(w * h * 4);
  for (let i = 0; i < w * h; i++) {
    if (color === 6) rgba.set(px.subarray(i * 4, i * 4 + 4), i * 4);
    else if (color === 2) {
      rgba.set(px.subarray(i * 3, i * 3 + 3), i * 4);
      rgba[i * 4 + 3] = 255;
    } else if (color === 3) {
      const k = px[i];
      if (!palette || k * 3 + 2 >= palette.length) return null;
      rgba[i * 4] = palette[k * 3];
      rgba[i * 4 + 1] = palette[k * 3 + 1];
      rgba[i * 4 + 2] = palette[k * 3 + 2];
      rgba[i * 4 + 3] = trns && k < trns.length ? trns[k] : 255;
    } else if (color === 0) {
      rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = px[i];
      rgba[i * 4 + 3] = 255;
    } else if (color === 4) {
      rgba[i * 4] = rgba[i * 4 + 1] = rgba[i * 4 + 2] = px[i * 2];
      rgba[i * 4 + 3] = px[i * 2 + 1];
    }
  }
  return { w, h, rgba };
}
__name(decodePng, "decodePng");
var CRC = (() => {
  const t = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 3988292384 ^ c >>> 1 : c >>> 1;
    t[n] = c >>> 0;
  }
  return t;
})();
function crc32(bytes2) {
  let c = 4294967295;
  for (const b of bytes2) c = CRC[(c ^ b) & 255] ^ c >>> 8;
  return (c ^ 4294967295) >>> 0;
}
__name(crc32, "crc32");
function chunk(type, data) {
  const out = new Uint8Array(12 + data.length);
  const v = new DataView(out.buffer);
  v.setUint32(0, data.length);
  for (let i = 0; i < 4; i++) out[4 + i] = type.charCodeAt(i);
  out.set(data, 8);
  v.setUint32(8 + data.length, crc32(out.subarray(4, 8 + data.length)));
  return out;
}
__name(chunk, "chunk");
async function encodePng(w, h, rgba) {
  const raw = new Uint8Array((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) raw.set(rgba.subarray(y * w * 4, (y + 1) * w * 4), y * (w * 4 + 1) + 1);
  const ihdr = new Uint8Array(13);
  const v = new DataView(ihdr.buffer);
  v.setUint32(0, w);
  v.setUint32(4, h);
  ihdr[8] = 8;
  ihdr[9] = 6;
  const parts = [new Uint8Array([137, 80, 78, 71, 13, 10, 26, 10]), chunk("IHDR", ihdr), chunk("IDAT", await deflate(raw)), chunk("IEND", new Uint8Array(0))];
  const out = new Uint8Array(parts.reduce((n, p) => n + p.length, 0));
  let at = 0;
  for (const p of parts) {
    out.set(p, at);
    at += p.length;
  }
  return out;
}
__name(encodePng, "encodePng");
function drawModel(model, tex) {
  const S = 96;
  const W = S * 4, H = S;
  const out = new Uint8Array(W * H * 4);
  for (let i = 0; i < W * H; i++) out.set([92, 92, 102, 255], i * 4);
  let min = [Infinity, Infinity, Infinity], max = [-Infinity, -Infinity, -Infinity];
  for (const c of model.cubes) for (let a = 0; a < 3; a++) {
    min[a] = Math.min(min[a], c.from[a], c.to[a]);
    max[a] = Math.max(max[a], c.from[a], c.to[a]);
  }
  const span = Math.max(max[0] - min[0], max[1] - min[1], max[2] - min[2], 1);
  const scale = (S - 8) / span;
  const views = [
    { face: "north", across: /* @__PURE__ */ __name((p) => p[0], "across"), depth: /* @__PURE__ */ __name((p) => p[2], "depth") },
    // the front
    { face: "east", across: /* @__PURE__ */ __name((p) => p[2], "across"), depth: /* @__PURE__ */ __name((p) => -p[0], "depth") },
    // the player's left side
    { face: "south", across: /* @__PURE__ */ __name((p) => -p[0], "across"), depth: /* @__PURE__ */ __name((p) => -p[2], "depth") }
    // the back
  ];
  views.forEach((view, vi) => {
    const zbuf = new Float32Array(S * S).fill(Infinity);
    const xs = model.cubes.flatMap((c) => [view.across(c.from), view.across(c.to)]);
    const midA = (Math.min(...xs) + Math.max(...xs)) / 2, midY = (min[1] + max[1]) / 2;
    for (const c of model.cubes) {
      const uv = c.faces && c.faces[view.face];
      if (!uv) continue;
      const a0 = view.across(c.from), a1 = view.across(c.to);
      const left = Math.min(a0, a1), right = Math.max(a0, a1);
      const top = Math.max(c.from[1], c.to[1]), bottom = Math.min(c.from[1], c.to[1]);
      const depth = Math.min(view.depth(c.from), view.depth(c.to));
      const sx0 = Math.round(S / 2 + (left - midA) * scale), sx1 = Math.round(S / 2 + (right - midA) * scale);
      const sy0 = Math.round(S / 2 - (top - midY) * scale), sy1 = Math.round(S / 2 - (bottom - midY) * scale);
      for (let y = Math.max(0, sy0); y < Math.min(S, sy1); y++) {
        for (let x = Math.max(0, sx0); x < Math.min(S, sx1); x++) {
          if (depth >= zbuf[y * S + x]) continue;
          const fu = (x - sx0 + 0.5) / Math.max(1, sx1 - sx0), fv = (y - sy0 + 0.5) / Math.max(1, sy1 - sy0);
          const tu = Math.floor((uv[0] + (uv[2] - uv[0]) * fu) * tex.w / model.tw);
          const tv = Math.floor((uv[1] + (uv[3] - uv[1]) * fv) * tex.h / model.th);
          if (tu < 0 || tv < 0 || tu >= tex.w || tv >= tex.h) continue;
          const t = (tv * tex.w + tu) * 4;
          if (tex.rgba[t + 3] < 16) continue;
          zbuf[y * S + x] = depth;
          out.set([tex.rgba[t], tex.rgba[t + 1], tex.rgba[t + 2], 255], (y * W + vi * S + x) * 4);
        }
      }
    }
  });
  const ts = Math.min((S - 8) / tex.w, (S - 8) / tex.h);
  for (let y = 0; y < Math.floor(tex.h * ts); y++) for (let x = 0; x < Math.floor(tex.w * ts); x++) {
    const t = (Math.floor(y / ts) * tex.w + Math.floor(x / ts)) * 4;
    if (tex.rgba[t + 3] < 16) continue;
    out.set([tex.rgba[t], tex.rgba[t + 1], tex.rgba[t + 2], 255], ((y + 4) * W + 3 * S + 4 + x) * 4);
  }
  return { w: W, h: H, rgba: out };
}
__name(drawModel, "drawModel");
function upscale(img, n) {
  const w = img.w * n, h = img.h * n, out = new Uint8Array(w * h * 4);
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const s = (Math.floor(y / n) * img.w + Math.floor(x / n)) * 4;
    out.set(img.rgba.subarray(s, s + 4), (y * w + x) * 4);
  }
  return { w, h, rgba: out };
}
__name(upscale, "upscale");

// src/check.js
var MODEL = "@cf/meta/llama-3.2-11b-vision-instruct";
function toBase64(bytes2) {
  let s = "";
  for (let i = 0; i < bytes2.length; i += 32768) s += String.fromCharCode(...bytes2.subarray(i, i + 32768));
  return btoa(s);
}
__name(toBase64, "toBase64");
async function ask(env, png, name, what) {
  const question = `This picture shows a ${what} that a player uploaded to a Minecraft mod used by players of all ages, including children. It is named "${name.replace(/"/g, "'")}". Look at the picture and the name. Reply with exactly one word: SAFE if both are fine for all ages, or UNSAFE if either shows or suggests nudity, sexual content, graphic gore, hate symbols, slurs, drugs or bullying.`;
  return env.AI.run(MODEL, {
    messages: [{ role: "user", content: question }],
    image: "data:image/png;base64," + toBase64(png),
    max_tokens: 5,
    temperature: 0
  });
}
__name(ask, "ask");
async function safetyCheck(env, png, name, what) {
  const testing = env.ALLOW_DEV_LOGIN === "true";
  if (!env.AI) return testing ? null : "check_unavailable";
  let res;
  try {
    res = await ask(env, png, name, what);
  } catch (e) {
    if (String(e && e.message).toLowerCase().includes("agree")) {
      try {
        await env.AI.run(MODEL, { prompt: "agree" });
        res = await ask(env, png, name, what);
      } catch (e2) {
        console.error("Safety check failed", e2);
        return testing ? null : "check_unavailable";
      }
    } else {
      console.error("Safety check failed", e);
      return testing ? null : "check_unavailable";
    }
  }
  const answer = String(res && (res.response ?? res.result?.response) || "").trim().toUpperCase();
  if (answer.startsWith("SAFE")) return null;
  if (answer.includes("UNSAFE")) return "not_allowed";
  console.log("Unclear safety answer:", answer);
  return "check_unsure";
}
__name(safetyCheck, "safetyCheck");

// src/index.js
var MAX_TEXT = 500;
var MAX_FRAME = 1e5;
var MAILBOX_DAYS = 30;
var MAILBOX_LIMIT = 200;
var MAX_FRIENDS = 300;
var RATE_WINDOW_MS = 1e4;
var RATE_LIMIT = 20;
var NAME = /^[A-Za-z0-9_]{1,16}$/;
var HASH = /^[0-9a-f]{64}$/;
var MAX_CAPE_BYTES = 64 * 1024;
var UPLOADS_PER_DAY = 10;
var REPORTS_TO_HIDE = 3;
var GALLERY_PAGE = 20;
var MAX_MODEL_UNPACKED = 512 * 1024;
var MAX_CUBES = 256;
var SLOTS = ["head", "back"];
var UUID = /^[0-9a-f]{32}$/;
var index_default = {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === "/") {
      return new Response("Hafnium server is running.\n", { headers: { "content-type": "text/plain" } });
    }
    const cosmetic = url.pathname.match(/^\/cosmetic\/([0-9a-f]{64})(\.png|\.bin|\.preview\.png)$/);
    if (cosmetic && request.method === "GET") {
      const cache = caches.default;
      const hit = await cache.match(request);
      if (hit) return hit;
      const res = await env.HUB.get(env.HUB.idFromName("main")).fetch(request);
      if (res.status === 200) await cache.put(request, res.clone());
      return res;
    }
    if (url.pathname === "/connect") {
      if (request.headers.get("Upgrade") !== "websocket") {
        return new Response("Connect with a WebSocket.\n", { status: 426 });
      }
      const hub = env.HUB.get(env.HUB.idFromName("main"));
      return hub.fetch(request);
    }
    return new Response("Not found.\n", { status: 404 });
  }
};
var Hub = class extends DurableObject {
  static {
    __name(this, "Hub");
  }
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.recent = /* @__PURE__ */ new Map();
    this.sql.exec(`CREATE TABLE IF NOT EXISTS players (
      uuid TEXT PRIMARY KEY, name TEXT NOT NULL, name_lower TEXT NOT NULL, last_seen INTEGER NOT NULL)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS players_name ON players (name_lower)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS friends (a TEXT NOT NULL, b TEXT NOT NULL, since INTEGER NOT NULL, PRIMARY KEY (a, b))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS requests (from_uuid TEXT NOT NULL, to_uuid TEXT NOT NULL, sent_at INTEGER NOT NULL, PRIMARY KEY (from_uuid, to_uuid))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS blocks (blocker TEXT NOT NULL, blocked TEXT NOT NULL, PRIMARY KEY (blocker, blocked))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS mailbox (
      id INTEGER PRIMARY KEY AUTOINCREMENT, from_uuid TEXT NOT NULL, to_uuid TEXT NOT NULL,
      text TEXT NOT NULL, sent_at INTEGER NOT NULL, stored_at INTEGER NOT NULL)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS mailbox_to ON mailbox (to_uuid)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS hosts (
      code TEXT PRIMARY KEY, uuid TEXT NOT NULL UNIQUE, world TEXT NOT NULL, visibility TEXT NOT NULL,
      address TEXT NOT NULL, started_at INTEGER NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS cosmetics (
      hash TEXT PRIMARY KEY, kind TEXT NOT NULL, name TEXT NOT NULL, creator TEXT NOT NULL,
      created_at INTEGER NOT NULL, size INTEGER NOT NULL, hidden INTEGER NOT NULL DEFAULT 0)`);
    this.sql.exec(`CREATE INDEX IF NOT EXISTS cosmetics_new ON cosmetics (created_at)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS cosmetic_data (hash TEXT PRIMARY KEY, data BLOB NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS equipped (uuid TEXT PRIMARY KEY, cape TEXT)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS equipped_skin (uuid TEXT PRIMARY KEY, skin TEXT NOT NULL)`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS equipped_model (uuid TEXT NOT NULL, slot TEXT NOT NULL, model TEXT NOT NULL, PRIMARY KEY (uuid, slot))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS cosmetic_preview (hash TEXT PRIMARY KEY, data BLOB NOT NULL)`);
    const cols = this.sql.exec("PRAGMA table_info(cosmetics)").toArray().map((c) => c.name);
    if (!cols.includes("variant")) this.sql.exec("ALTER TABLE cosmetics ADD COLUMN variant TEXT NOT NULL DEFAULT ''");
    this.sql.exec(`CREATE TABLE IF NOT EXISTS reports (hash TEXT NOT NULL, reporter TEXT NOT NULL, reason TEXT NOT NULL, at INTEGER NOT NULL, PRIMARY KEY (hash, reporter))`);
  }
  // A game connects. The connection can sleep between messages, so it costs almost nothing while idle.
  async fetch(request) {
    const cosmetic = new URL(request.url).pathname.match(/^\/cosmetic\/([0-9a-f]{64})(\.png|\.bin|\.preview\.png)$/);
    if (cosmetic) {
      const table = cosmetic[2] === ".preview.png" ? "cosmetic_preview" : "cosmetic_data";
      const row = this.sql.exec(
        `SELECT d.data FROM ${table} d JOIN cosmetics c ON c.hash = d.hash WHERE d.hash = ? AND c.hidden = 0`,
        cosmetic[1]
      ).toArray()[0];
      if (!row) return new Response("Not found.\n", { status: 404 });
      const type = cosmetic[2] === ".bin" ? "application/gzip" : "image/png";
      return new Response(row.data, { headers: { "content-type": type, "cache-control": "public, max-age=3600" } });
    }
    const pair = new WebSocketPair();
    this.ctx.acceptWebSocket(pair[1]);
    pair[1].serializeAttachment({ state: "new" });
    return new Response(null, { status: 101, webSocket: pair[0] });
  }
  async webSocketMessage(ws, raw) {
    if (typeof raw !== "string" || raw.length > MAX_FRAME) return this.fail(ws, "too_big");
    let msg;
    try {
      msg = JSON.parse(raw);
    } catch {
      return this.fail(ws, "bad_json");
    }
    if (!msg || typeof msg.type !== "string") return this.fail(ws, "bad_message");
    const me = ws.deserializeAttachment() || { state: "new" };
    try {
      if (msg.type === "verifier_hello") return this.verifierHello(ws, msg);
      if (me.state === "verifier") return this.verifierMessage(msg);
      if (msg.type === "hello") return this.hello(ws, me, msg);
      if (msg.type === "login") return await this.login(ws, me, msg);
      if (me.state !== "ready") return this.fail(ws, "not_logged_in");
      if (!this.allowed(me.uuid)) return this.fail(ws, "slow_down");
      switch (msg.type) {
        case "friends":
          return this.sendFriends(ws, me.uuid);
        case "friend_request":
          return this.friendRequest(ws, me, msg);
        case "friend_accept":
          return this.friendAccept(ws, me, msg);
        case "friend_decline":
          return this.friendDecline(ws, me, msg);
        case "friend_remove":
          return this.friendRemove(ws, me, msg);
        case "block":
          return this.block(ws, me, msg);
        case "unblock":
          return this.unblock(ws, me, msg);
        case "message":
          return this.message(ws, me, msg);
        case "invite":
          return this.invite(ws, me, msg);
        case "host_start":
          return this.hostStart(ws, me, msg);
        case "host_stop":
          return this.hostStop(ws, me);
        case "join":
          return this.join(ws, me, msg);
        case "upload_cape":
          return await this.uploadCape(ws, me, msg);
        case "upload_skin":
          return await this.uploadSkin(ws, me, msg);
        case "upload_model":
          return await this.uploadModel(ws, me, msg);
        case "gallery":
          return this.gallery(ws, me, msg);
        case "my_cosmetics":
          return this.mine(ws, me);
        case "equip":
          return this.equip(ws, me, msg);
        case "wearing":
          return this.wearing(ws, me, msg);
        case "report":
          return this.report(ws, me, msg);
        case "delete_cosmetic":
          return this.deleteCosmetic(ws, me, msg);
        case "admin_reports":
          return this.adminReports(ws, me);
        case "admin_unhide":
          return this.adminUnhide(ws, me, msg);
        default:
          return this.fail(ws, "unknown_type");
      }
    } catch (e) {
      console.error("Error handling", msg.type, e);
      return this.fail(ws, "server_error");
    }
  }
  async webSocketClose(ws) {
    const me = ws.deserializeAttachment();
    if (me && me.state === "ready" && !this.isOnline(me.uuid, ws)) {
      this.sql.exec("UPDATE players SET last_seen = ? WHERE uuid = ?", Date.now(), me.uuid);
      this.endHosting(me.uuid, me.name);
      this.tellFriends(me.uuid, { type: "presence", uuid: me.uuid, name: me.name, online: false });
    }
  }
  async webSocketError(ws) {
    await this.webSocketClose(ws);
  }
  // --- The sign-in helper -------------------------------------------------------
  //
  // The helper connects like a game and proves itself with VERIFIER_SECRET. Then this
  // server can ask it whether a player really joined, which Mojang won't answer for Cloudflare.
  verifierHello(ws, msg) {
    const secret = this.env.VERIFIER_SECRET;
    if (typeof secret !== "string" || secret.length < 16 || typeof msg.secret !== "string" || !sameText(msg.secret, secret)) {
      this.send(ws, { type: "error", reason: "bad_secret" });
      ws.close(1008, "bad secret");
      return;
    }
    ws.serializeAttachment({ state: "verifier" });
    this.send(ws, { type: "verifier_ready" });
  }
  verifierMessage(msg) {
    if (msg.type !== "verified" || typeof msg.id !== "string" || !this.pending) return;
    const done = this.pending.get(msg.id);
    if (done) done({ status: Number(msg.status) || 0, profile: msg.profile && typeof msg.profile === "object" ? msg.profile : null });
  }
  askVerifier(username, serverId) {
    const helpers = this.ctx.getWebSockets().filter((s) => {
      const a = s.deserializeAttachment();
      return a && a.state === "verifier";
    });
    if (helpers.length === 0) return Promise.reject(new Error("no_verifier"));
    if (!this.pending) this.pending = /* @__PURE__ */ new Map();
    const id = [...crypto.getRandomValues(new Uint8Array(8))].map((b) => b.toString(16).padStart(2, "0")).join("");
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error("verifier_failed"));
      }, 1e4);
      this.pending.set(id, (answer) => {
        clearTimeout(timer);
        this.pending.delete(id);
        resolve(answer);
      });
      this.send(helpers[helpers.length - 1], { type: "verify", id, username, serverId });
    });
  }
  // --- Logging in -------------------------------------------------------------
  //
  // The server sends a random challenge. The game answers with its Mojang-signed
  // chat certificate and profile, and signs the challenge. verify.js checks it all
  // with Mojang's public keys, so this server never has to call Mojang, which blocks
  // Cloudflare. The player's Mojang token never comes here.
  hello(ws, me, msg) {
    if (typeof msg.name !== "string" || !NAME.test(msg.name)) return this.fail(ws, "bad_name");
    const bytes2 = crypto.getRandomValues(new Uint8Array(10));
    const serverId = [...bytes2].map((b) => b.toString(16).padStart(2, "0")).join("");
    ws.serializeAttachment({ state: "challenge", name: msg.name, serverId });
    this.send(ws, { type: "challenge", serverId, challenge: serverId });
  }
  async login(ws, me, msg) {
    if (me.state !== "challenge") return this.fail(ws, "say_hello_first");
    const testing = this.env.ALLOW_DEV_LOGIN === "true";
    let who;
    if (msg.dev === true && testing) {
      who = { uuid: validUuid(msg.uuid) ? msg.uuid : devUuid(me.name), name: me.name };
    } else {
      const keys = testing && this.env.TEST_MOJANG_KEY ? {
        profilePropertyKeys: [...mojang_keys_default.profilePropertyKeys, this.env.TEST_MOJANG_KEY],
        playerCertificateKeys: [...mojang_keys_default.playerCertificateKeys, this.env.TEST_MOJANG_KEY]
      } : mojang_keys_default;
      try {
        who = msg.method === "join" ? await verifyJoin(msg, me.name, me.serverId, (n, id) => this.askVerifier(n, id)) : await verifyLogin(msg, me.serverId, keys);
      } catch (e) {
        ws.serializeAttachment({ state: "new" });
        this.send(ws, { type: "error", reason: "login_failed", detail: String(e.message || e) });
        return;
      }
    }
    const uuid = who.uuid;
    const name = who.name;
    const now = Date.now();
    this.sql.exec(
      `INSERT INTO players (uuid, name, name_lower, last_seen) VALUES (?, ?, ?, ?)
       ON CONFLICT (uuid) DO UPDATE SET name = excluded.name, name_lower = excluded.name_lower, last_seen = excluded.last_seen`,
      uuid,
      name,
      name.toLowerCase(),
      now
    );
    const wasOnline = this.isOnline(uuid, ws);
    ws.serializeAttachment({ state: "ready", uuid, name });
    this.send(ws, { type: "welcome", uuid, name });
    this.sendFriends(ws, uuid);
    this.deliverMailbox(ws, uuid);
    this.send(ws, { type: "equipped", cape: this.capeOf(uuid), skin: this.skinOf(uuid), models: this.modelsOf(uuid), admin: this.isAdmin(uuid) });
    if (!wasOnline) this.tellFriends(uuid, { type: "presence", uuid, name, online: true });
  }
  // --- Friends --------------------------------------------------------------------
  sendFriends(ws, uuid) {
    const friends = this.sql.exec(
      `SELECT p.uuid, p.name, p.last_seen FROM friends f JOIN players p ON p.uuid = f.b WHERE f.a = ? ORDER BY p.name_lower`,
      uuid
    ).toArray().map((r) => ({
      uuid: r.uuid,
      name: r.name,
      online: this.isOnline(r.uuid),
      lastSeen: r.last_seen,
      hosting: this.hostingOf(r.uuid)
    }));
    const incoming = this.sql.exec(
      `SELECT p.uuid, p.name, r.sent_at FROM requests r JOIN players p ON p.uuid = r.from_uuid WHERE r.to_uuid = ?`,
      uuid
    ).toArray().map((r) => ({ uuid: r.uuid, name: r.name, sentAt: r.sent_at }));
    const outgoing = this.sql.exec(
      `SELECT p.uuid, p.name, r.sent_at FROM requests r JOIN players p ON p.uuid = r.to_uuid WHERE r.from_uuid = ?`,
      uuid
    ).toArray().map((r) => ({ uuid: r.uuid, name: r.name, sentAt: r.sent_at }));
    const blocked = this.sql.exec(
      `SELECT p.uuid, p.name FROM blocks b JOIN players p ON p.uuid = b.blocked WHERE b.blocker = ?`,
      uuid
    ).toArray().map((r) => ({ uuid: r.uuid, name: r.name }));
    this.send(ws, { type: "friends", friends, incoming, outgoing, blocked });
  }
  friendRequest(ws, me, msg) {
    if (typeof msg.name !== "string" || !NAME.test(msg.name)) return this.fail(ws, "bad_name");
    const target = this.sql.exec("SELECT uuid, name FROM players WHERE name_lower = ?", msg.name.toLowerCase()).toArray()[0];
    if (!target) return this.fail(ws, "not_found");
    if (target.uuid === me.uuid) return this.fail(ws, "thats_you");
    if (this.areFriends(me.uuid, target.uuid)) return this.fail(ws, "already_friends");
    if (this.blocks(me.uuid, target.uuid)) return this.fail(ws, "you_blocked_them");
    if (this.count("friends", "a", me.uuid) >= MAX_FRIENDS) return this.fail(ws, "too_many_friends");
    if (this.blocks(target.uuid, me.uuid)) return this.send(ws, { type: "ok", for: "friend_request" });
    const theirs = this.sql.exec("SELECT 1 FROM requests WHERE from_uuid = ? AND to_uuid = ?", target.uuid, me.uuid).toArray();
    if (theirs.length) return this.makeFriends(me, target);
    this.sql.exec("INSERT OR IGNORE INTO requests (from_uuid, to_uuid, sent_at) VALUES (?, ?, ?)", me.uuid, target.uuid, Date.now());
    this.send(ws, { type: "ok", for: "friend_request" });
    this.tellPlayer(target.uuid, { type: "friend_request", uuid: me.uuid, name: me.name });
  }
  friendAccept(ws, me, msg) {
    if (!validUuid(msg.uuid)) return this.fail(ws, "bad_uuid");
    const req = this.sql.exec("SELECT 1 FROM requests WHERE from_uuid = ? AND to_uuid = ?", msg.uuid, me.uuid).toArray();
    if (!req.length) return this.fail(ws, "no_request");
    const other = this.sql.exec("SELECT uuid, name FROM players WHERE uuid = ?", msg.uuid).toArray()[0];
    if (!other) return this.fail(ws, "not_found");
    this.makeFriends(me, other);
  }
  makeFriends(me, other) {
    const now = Date.now();
    this.sql.exec(
      "DELETE FROM requests WHERE (from_uuid = ? AND to_uuid = ?) OR (from_uuid = ? AND to_uuid = ?)",
      me.uuid,
      other.uuid,
      other.uuid,
      me.uuid
    );
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", me.uuid, other.uuid, now);
    this.sql.exec("INSERT OR IGNORE INTO friends (a, b, since) VALUES (?, ?, ?)", other.uuid, me.uuid, now);
    this.tellPlayer(me.uuid, { type: "friend_added", uuid: other.uuid, name: other.name, online: this.isOnline(other.uuid) });
    this.tellPlayer(other.uuid, { type: "friend_added", uuid: me.uuid, name: me.name, online: true });
  }
  friendDecline(ws, me, msg) {
    if (!validUuid(msg.uuid)) return this.fail(ws, "bad_uuid");
    this.sql.exec("DELETE FROM requests WHERE from_uuid = ? AND to_uuid = ?", msg.uuid, me.uuid);
    this.send(ws, { type: "ok", for: "friend_decline" });
  }
  friendRemove(ws, me, msg) {
    if (!validUuid(msg.uuid)) return this.fail(ws, "bad_uuid");
    this.unfriend(me.uuid, msg.uuid);
    this.send(ws, { type: "ok", for: "friend_remove" });
    this.tellPlayer(msg.uuid, { type: "friend_removed", uuid: me.uuid });
  }
  unfriend(a, b) {
    this.sql.exec("DELETE FROM friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)", a, b, b, a);
    this.sql.exec("DELETE FROM requests WHERE (from_uuid = ? AND to_uuid = ?) OR (from_uuid = ? AND to_uuid = ?)", a, b, b, a);
  }
  block(ws, me, msg) {
    if (!validUuid(msg.uuid) || msg.uuid === me.uuid) return this.fail(ws, "bad_uuid");
    const wereFriends = this.areFriends(me.uuid, msg.uuid);
    this.unfriend(me.uuid, msg.uuid);
    this.sql.exec("DELETE FROM mailbox WHERE from_uuid = ? AND to_uuid = ?", msg.uuid, me.uuid);
    this.sql.exec("INSERT OR IGNORE INTO blocks (blocker, blocked) VALUES (?, ?)", me.uuid, msg.uuid);
    this.send(ws, { type: "ok", for: "block" });
    if (wereFriends) this.tellPlayer(msg.uuid, { type: "friend_removed", uuid: me.uuid });
  }
  unblock(ws, me, msg) {
    if (!validUuid(msg.uuid)) return this.fail(ws, "bad_uuid");
    this.sql.exec("DELETE FROM blocks WHERE blocker = ? AND blocked = ?", me.uuid, msg.uuid);
    this.send(ws, { type: "ok", for: "unblock" });
  }
  // --- Chat and invites -------------------------------------------------------------
  message(ws, me, msg) {
    if (!validUuid(msg.to)) return this.fail(ws, "bad_uuid");
    if (typeof msg.text !== "string") return this.fail(ws, "bad_text");
    const text = msg.text.trim();
    if (!text.length || text.length > MAX_TEXT) return this.fail(ws, "bad_text");
    if (!this.areFriends(me.uuid, msg.to)) return this.fail(ws, "not_friends");
    const sentAt = Date.now();
    const out = { type: "message", from: me.uuid, name: me.name, text, sentAt };
    if (this.tellPlayer(msg.to, out)) {
      return this.send(ws, { type: "ok", for: "message", delivered: true, sentAt });
    }
    if (this.count("mailbox", "to_uuid", msg.to) >= MAILBOX_LIMIT) return this.fail(ws, "mailbox_full");
    this.sql.exec(
      "INSERT INTO mailbox (from_uuid, to_uuid, text, sent_at, stored_at) VALUES (?, ?, ?, ?, ?)",
      me.uuid,
      msg.to,
      text,
      sentAt,
      sentAt
    );
    this.send(ws, { type: "ok", for: "message", delivered: false, sentAt });
  }
  // Hands over everything waiting, then deletes it. Old messages are thrown away first.
  deliverMailbox(ws, uuid) {
    this.sql.exec("DELETE FROM mailbox WHERE stored_at < ?", Date.now() - MAILBOX_DAYS * 864e5);
    const waiting = this.sql.exec(
      `SELECT m.id, m.from_uuid, p.name, m.text, m.sent_at, m.stored_at FROM mailbox m
       LEFT JOIN players p ON p.uuid = m.from_uuid WHERE m.to_uuid = ? ORDER BY m.id`,
      uuid
    ).toArray();
    for (const m of waiting) {
      this.send(ws, { type: "message", from: m.from_uuid, name: m.name, text: m.text, sentAt: m.sent_at, storedAt: m.stored_at, waited: true });
    }
    if (waiting.length) this.sql.exec("DELETE FROM mailbox WHERE to_uuid = ? AND id <= ?", uuid, waiting[waiting.length - 1].id);
  }
  // A world invite only reaches a friend who is online. The address is the hosting address, like an e4mc one.
  invite(ws, me, msg) {
    if (!validUuid(msg.to)) return this.fail(ws, "bad_uuid");
    if (typeof msg.address !== "string" || !msg.address.length || msg.address.length > 255) return this.fail(ws, "bad_address");
    if (!this.areFriends(me.uuid, msg.to)) return this.fail(ws, "not_friends");
    const world = typeof msg.world === "string" ? msg.world.slice(0, 64) : null;
    const sent = this.tellPlayer(msg.to, { type: "invite", from: me.uuid, name: me.name, address: msg.address, world, sentAt: Date.now() });
    if (!sent) return this.fail(ws, "friend_offline");
    this.send(ws, { type: "ok", for: "invite" });
  }
  // --- Hosting -------------------------------------------------------------------------
  //
  // The host's game runs the world as a small server. It tells this server the
  // address friends can reach it at. This server makes a short code for it and
  // only hands the address to players who are allowed: friends for a private
  // world, anyone signed in for a public one. Blocked players never get it.
  hostStart(ws, me, msg) {
    const world = typeof msg.world === "string" ? msg.world.trim().slice(0, 48) : "";
    if (!world) return this.fail(ws, "bad_world");
    const visibility = msg.visibility === "public" ? "public" : "private";
    if (typeof msg.address !== "string" || !/^[A-Za-z0-9.\-:\[\]]{3,100}$/.test(msg.address)) return this.fail(ws, "bad_address");
    this.sql.exec("DELETE FROM hosts WHERE uuid = ?", me.uuid);
    let code;
    do {
      const alphabet = "abcdefghjkmnpqrstuvwxyz23456789";
      const rnd = crypto.getRandomValues(new Uint8Array(6));
      code = "hf-" + [...rnd].map((b) => alphabet[b % alphabet.length]).join("");
    } while (this.sql.exec("SELECT 1 FROM hosts WHERE code = ?", code).toArray().length);
    this.sql.exec(
      "INSERT INTO hosts (code, uuid, world, visibility, address, started_at) VALUES (?, ?, ?, ?, ?, ?)",
      code,
      me.uuid,
      world,
      visibility,
      msg.address,
      Date.now()
    );
    this.send(ws, { type: "hosting", code, world, visibility });
    this.tellFriends(me.uuid, { type: "host_update", uuid: me.uuid, name: me.name, hosting: { world, code, visibility } });
  }
  hostStop(ws, me) {
    this.endHosting(me.uuid, me.name);
    this.send(ws, { type: "ok", for: "host_stop" });
  }
  endHosting(uuid, name) {
    const had = this.sql.exec("SELECT 1 FROM hosts WHERE uuid = ?", uuid).toArray().length > 0;
    this.sql.exec("DELETE FROM hosts WHERE uuid = ?", uuid);
    if (had) this.tellFriends(uuid, { type: "host_update", uuid, name, hosting: null });
  }
  /** What a friend sees about someone's hosting: the world and its code, or null. */
  hostingOf(uuid) {
    const h = this.sql.exec("SELECT code, world, visibility FROM hosts WHERE uuid = ?", uuid).toArray()[0];
    return h ? { code: h.code, world: h.world, visibility: h.visibility } : null;
  }
  join(ws, me, msg) {
    let host;
    if (typeof msg.code === "string") {
      host = this.sql.exec("SELECT * FROM hosts WHERE code = ?", msg.code.trim().toLowerCase()).toArray()[0];
    } else if (validUuid(msg.uuid)) {
      host = this.sql.exec("SELECT * FROM hosts WHERE uuid = ?", msg.uuid).toArray()[0];
    }
    if (!host || !this.isOnline(host.uuid)) return this.fail(ws, "no_such_world");
    if (host.uuid === me.uuid) return this.fail(ws, "thats_your_world");
    if (this.blocks(host.uuid, me.uuid) || this.blocks(me.uuid, host.uuid)) return this.fail(ws, "no_such_world");
    if (host.visibility !== "public" && !this.areFriends(host.uuid, me.uuid)) return this.fail(ws, "no_such_world");
    const hostName = this.sql.exec("SELECT name FROM players WHERE uuid = ?", host.uuid).one().name;
    this.tellPlayer(host.uuid, { type: "join_notice", uuid: me.uuid, name: me.name });
    this.send(ws, { type: "join_info", code: host.code, world: host.world, address: host.address, host: hostName, hostUuid: host.uuid });
  }
  // --- Capes ---------------------------------------------------------------------------
  //
  // Anyone signed in can upload a cape picture. It goes straight into the public
  // gallery with the creator's name on it, and anyone can wear it. Every cape can
  // be reported. One that several players report is hidden until an admin checks
  // it, and admins can delete any cape. Creators can delete their own.
  isAdmin(uuid) {
    return (this.env.ADMIN_UUIDS || "").split(",").map((s) => s.trim()).includes(uuid);
  }
  capeOf(uuid) {
    const row = this.sql.exec(
      "SELECT e.cape FROM equipped e JOIN cosmetics c ON c.hash = e.cape WHERE e.uuid = ? AND c.hidden = 0",
      uuid
    ).toArray()[0];
    return row ? row.cape : null;
  }
  skinOf(uuid) {
    const row = this.sql.exec(
      "SELECT e.skin, c.variant FROM equipped_skin e JOIN cosmetics c ON c.hash = e.skin WHERE e.uuid = ? AND c.hidden = 0",
      uuid
    ).toArray()[0];
    return row ? { hash: row.skin, variant: row.variant === "slim" ? "slim" : "classic" } : null;
  }
  async uploadCape(ws, me, msg) {
    return this.upload(ws, me, msg, "cape", (w, h) => capeSizeOk(w, h), "wrong_cape_size", "");
  }
  async uploadSkin(ws, me, msg) {
    const variant = msg.variant === "slim" ? "slim" : "classic";
    return this.upload(ws, me, msg, "skin", (w, h) => skinSizeOk(w, h), "wrong_skin_size", variant);
  }
  async upload(ws, me, msg, kind, sizeOk, wrongSize, variant) {
    const name = typeof msg.name === "string" ? msg.name.trim().slice(0, 32) : "";
    if (!name) return this.fail(ws, "bad_cape_name");
    if (typeof msg.png !== "string" || msg.png.length > MAX_FRAME) return this.fail(ws, "cape_too_big");
    let bytes2;
    try {
      const bin = atob(msg.png);
      bytes2 = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) bytes2[i] = bin.charCodeAt(i);
    } catch {
      return this.fail(ws, "bad_cape");
    }
    if (bytes2.length > MAX_CAPE_BYTES) return this.fail(ws, "cape_too_big");
    const size = pngSize(bytes2);
    if (!size) return this.fail(ws, "not_a_png");
    if (!sizeOk(size.w, size.h)) return this.fail(ws, wrongSize);
    const decoded = await decodePng(bytes2);
    if (!decoded) return this.fail(ws, "not_a_png");
    const big = upscale(decoded, Math.max(1, Math.floor(256 / decoded.w)));
    const refused = await safetyCheck(this.env, await encodePng(big.w, big.h, big.rgba), name, kind === "skin" ? "player skin" : "player cape");
    if (refused) return this.fail(ws, refused);
    const recent = this.sql.exec(
      "SELECT COUNT(*) AS n FROM cosmetics WHERE creator = ? AND created_at > ?",
      me.uuid,
      Date.now() - 864e5
    ).one().n;
    if (recent >= UPLOADS_PER_DAY) return this.fail(ws, "too_many_uploads");
    const digest = await crypto.subtle.digest("SHA-256", bytes2);
    const hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
    const existing = this.sql.exec("SELECT hidden FROM cosmetics WHERE hash = ?", hash).toArray()[0];
    if (existing && existing.hidden) return this.fail(ws, "cape_removed");
    if (!existing) {
      this.sql.exec("INSERT INTO cosmetic_data (hash, data) VALUES (?, ?)", hash, bytes2.buffer);
      this.sql.exec(
        "INSERT INTO cosmetics (hash, kind, name, creator, created_at, size, variant) VALUES (?, ?, ?, ?, ?, ?, ?)",
        hash,
        kind,
        name,
        me.uuid,
        Date.now(),
        bytes2.length,
        variant
      );
    }
    this.send(ws, { type: "uploaded", hash, name, kind });
  }
  /** What 3D models a player wears, by slot. */
  modelsOf(uuid) {
    const out = { head: null, back: null };
    for (const r of this.sql.exec(
      "SELECT e.slot, e.model FROM equipped_model e JOIN cosmetics c ON c.hash = e.model WHERE e.uuid = ? AND c.hidden = 0",
      uuid
    ).toArray()) {
      out[r.slot] = r.model;
    }
    return out;
  }
  /**
   * A 3D model from Blockbench, already shrunk by the game: gzip-packed JSON
   * with the cubes and the texture. It's checked for size and shape, drawn
   * into a preview picture, and that picture goes through the safety check.
   */
  async uploadModel(ws, me, msg) {
    const name = typeof msg.name === "string" ? msg.name.trim().slice(0, 32) : "";
    if (!name) return this.fail(ws, "bad_cape_name");
    if (!SLOTS.includes(msg.slot)) return this.fail(ws, "bad_slot");
    if (typeof msg.data !== "string" || msg.data.length > MAX_FRAME) return this.fail(ws, "model_too_big");
    let packed;
    try {
      const bin = atob(msg.data);
      packed = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) packed[i] = bin.charCodeAt(i);
    } catch {
      return this.fail(ws, "bad_model");
    }
    if (packed.length > MAX_CAPE_BYTES) return this.fail(ws, "model_too_big");
    let model;
    try {
      model = JSON.parse(new TextDecoder().decode(await gunzip(packed, MAX_MODEL_UNPACKED)));
    } catch (e) {
      return this.fail(ws, String(e.message) === "too_big_unpacked" ? "model_too_big" : "bad_model");
    }
    const problem = checkModel(model);
    if (problem) return this.fail(ws, problem);
    let texBytes;
    try {
      const bin = atob(model.texture);
      texBytes = new Uint8Array(bin.length);
      for (let i = 0; i < bin.length; i++) texBytes[i] = bin.charCodeAt(i);
    } catch {
      return this.fail(ws, "bad_model");
    }
    const tex = await decodePng(texBytes);
    if (!tex || tex.w > 256 || tex.h > 256) return this.fail(ws, "bad_model_texture");
    const recent = this.sql.exec(
      "SELECT COUNT(*) AS n FROM cosmetics WHERE creator = ? AND created_at > ?",
      me.uuid,
      Date.now() - 864e5
    ).one().n;
    if (recent >= UPLOADS_PER_DAY) return this.fail(ws, "too_many_uploads");
    const preview = drawModel(model, tex);
    const previewPng = await encodePng(preview.w, preview.h, preview.rgba);
    const refused = await safetyCheck(
      this.env,
      await encodePng(preview.w * 2, preview.h * 2, upscale(preview, 2).rgba),
      name,
      msg.slot === "head" ? "3D hat for a player's head, shown from the front, side and back, with its texture" : "3D cosmetic for a player's back, shown from the front, side and back, with its texture"
    );
    if (refused) return this.fail(ws, refused);
    const digest = await crypto.subtle.digest("SHA-256", packed);
    const hash = [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
    const existing = this.sql.exec("SELECT hidden FROM cosmetics WHERE hash = ?", hash).toArray()[0];
    if (existing && existing.hidden) return this.fail(ws, "cape_removed");
    if (!existing) {
      this.sql.exec("INSERT INTO cosmetic_data (hash, data) VALUES (?, ?)", hash, packed.buffer);
      this.sql.exec("INSERT INTO cosmetic_preview (hash, data) VALUES (?, ?)", hash, previewPng.buffer);
      this.sql.exec(
        "INSERT INTO cosmetics (hash, kind, name, creator, created_at, size, variant) VALUES (?, 'model', ?, ?, ?, ?, ?)",
        hash,
        name,
        me.uuid,
        Date.now(),
        packed.length,
        msg.slot
      );
    }
    this.send(ws, { type: "uploaded", hash, name, kind: "model" });
  }
  gallery(ws, me, msg) {
    const page = Number.isInteger(msg.page) && msg.page >= 0 ? Math.min(msg.page, 500) : 0;
    const kind = msg.kind === "skin" || msg.kind === "model" ? msg.kind : "cape";
    const rows = this.sql.exec(
      `SELECT c.hash, c.name, c.creator, p.name AS creator_name, c.created_at, c.kind, c.variant FROM cosmetics c
       LEFT JOIN players p ON p.uuid = c.creator WHERE c.hidden = 0 AND c.kind = ?
       ORDER BY c.created_at DESC LIMIT ? OFFSET ?`,
      kind,
      GALLERY_PAGE + 1,
      page * GALLERY_PAGE
    ).toArray();
    const items = rows.slice(0, GALLERY_PAGE).map((r) => ({
      hash: r.hash,
      name: r.name,
      creator: r.creator,
      creatorName: r.creator_name || "?",
      createdAt: r.created_at,
      kind: r.kind,
      variant: r.variant
    }));
    this.send(ws, { type: "gallery", kind, page, items, more: rows.length > GALLERY_PAGE });
  }
  mine(ws, me) {
    const items = this.sql.exec(
      "SELECT hash, name, created_at, hidden, kind, variant FROM cosmetics WHERE creator = ? ORDER BY created_at DESC",
      me.uuid
    ).toArray().map((r) => ({
      hash: r.hash,
      name: r.name,
      createdAt: r.created_at,
      hidden: !!r.hidden,
      kind: r.kind,
      variant: r.variant,
      creator: me.uuid
    }));
    this.send(ws, { type: "mine", items });
  }
  equip(ws, me, msg) {
    if ("model" in msg) {
      if (!SLOTS.includes(msg.slot)) return this.fail(ws, "bad_slot");
      if (msg.model === null) {
        this.sql.exec("DELETE FROM equipped_model WHERE uuid = ? AND slot = ?", me.uuid, msg.slot);
      } else {
        if (typeof msg.model !== "string" || !HASH.test(msg.model)) return this.fail(ws, "bad_cape");
        const row = this.sql.exec("SELECT variant FROM cosmetics WHERE hash = ? AND hidden = 0 AND kind = 'model'", msg.model).toArray()[0];
        if (!row) return this.fail(ws, "cape_removed");
        if (row.variant !== msg.slot) return this.fail(ws, "bad_slot");
        this.sql.exec(
          "INSERT INTO equipped_model (uuid, slot, model) VALUES (?, ?, ?) ON CONFLICT (uuid, slot) DO UPDATE SET model = excluded.model",
          me.uuid,
          msg.slot,
          msg.model
        );
      }
      return this.send(ws, { type: "equipped", cape: this.capeOf(me.uuid), skin: this.skinOf(me.uuid), models: this.modelsOf(me.uuid) });
    }
    if ("skin" in msg) {
      if (msg.skin === null) {
        this.sql.exec("DELETE FROM equipped_skin WHERE uuid = ?", me.uuid);
      } else {
        if (typeof msg.skin !== "string" || !HASH.test(msg.skin)) return this.fail(ws, "bad_cape");
        const ok2 = this.sql.exec("SELECT 1 FROM cosmetics WHERE hash = ? AND hidden = 0 AND kind = 'skin'", msg.skin).toArray().length;
        if (!ok2) return this.fail(ws, "cape_removed");
        this.sql.exec("INSERT INTO equipped_skin (uuid, skin) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET skin = excluded.skin", me.uuid, msg.skin);
      }
      return this.send(ws, { type: "equipped", cape: this.capeOf(me.uuid), skin: this.skinOf(me.uuid) });
    }
    if (msg.cape === null) {
      this.sql.exec("DELETE FROM equipped WHERE uuid = ?", me.uuid);
      return this.send(ws, { type: "equipped", cape: null, skin: this.skinOf(me.uuid) });
    }
    if (typeof msg.cape !== "string" || !HASH.test(msg.cape)) return this.fail(ws, "bad_cape");
    const ok = this.sql.exec("SELECT 1 FROM cosmetics WHERE hash = ? AND hidden = 0 AND kind = 'cape'", msg.cape).toArray().length;
    if (!ok) return this.fail(ws, "cape_removed");
    this.sql.exec("INSERT INTO equipped (uuid, cape) VALUES (?, ?) ON CONFLICT (uuid) DO UPDATE SET cape = excluded.cape", me.uuid, msg.cape);
    this.send(ws, { type: "equipped", cape: msg.cape, skin: this.skinOf(me.uuid) });
  }
  /** Which cape each of these players wears, for the players a game can see around it. */
  wearing(ws, me, msg) {
    if (!Array.isArray(msg.uuids)) return this.fail(ws, "bad_message");
    const capes = {};
    const skins = {};
    const models = {};
    for (const u of msg.uuids.slice(0, 100)) {
      if (!validUuid(u)) continue;
      capes[u] = this.capeOf(u);
      skins[u] = this.skinOf(u);
      models[u] = this.modelsOf(u);
    }
    this.send(ws, { type: "wearing", capes, skins, models });
  }
  report(ws, me, msg) {
    if (typeof msg.hash !== "string" || !HASH.test(msg.hash)) return this.fail(ws, "bad_cape");
    const reason = typeof msg.reason === "string" ? msg.reason.trim().slice(0, 200) : "";
    if (!this.sql.exec("SELECT 1 FROM cosmetics WHERE hash = ?", msg.hash).toArray().length) return this.fail(ws, "bad_cape");
    this.sql.exec("INSERT OR IGNORE INTO reports (hash, reporter, reason, at) VALUES (?, ?, ?, ?)", msg.hash, me.uuid, reason, Date.now());
    const n = this.sql.exec("SELECT COUNT(*) AS n FROM reports WHERE hash = ?", msg.hash).one().n;
    if (n >= REPORTS_TO_HIDE) this.sql.exec("UPDATE cosmetics SET hidden = 1 WHERE hash = ?", msg.hash);
    this.send(ws, { type: "ok", for: "report" });
  }
  deleteCosmetic(ws, me, msg) {
    if (typeof msg.hash !== "string" || !HASH.test(msg.hash)) return this.fail(ws, "bad_cape");
    const row = this.sql.exec("SELECT creator FROM cosmetics WHERE hash = ?", msg.hash).toArray()[0];
    if (!row) return this.fail(ws, "bad_cape");
    if (row.creator !== me.uuid && !this.isAdmin(me.uuid)) return this.fail(ws, "not_yours");
    this.sql.exec("DELETE FROM cosmetic_data WHERE hash = ?", msg.hash);
    this.sql.exec("DELETE FROM equipped WHERE cape = ?", msg.hash);
    this.sql.exec("DELETE FROM equipped_skin WHERE skin = ?", msg.hash);
    this.sql.exec("DELETE FROM equipped_model WHERE model = ?", msg.hash);
    this.sql.exec("DELETE FROM cosmetic_preview WHERE hash = ?", msg.hash);
    this.sql.exec("DELETE FROM reports WHERE hash = ?", msg.hash);
    this.sql.exec("UPDATE cosmetics SET hidden = 1 WHERE hash = ?", msg.hash);
    this.send(ws, { type: "ok", for: "delete_cosmetic" });
  }
  adminReports(ws, me) {
    if (!this.isAdmin(me.uuid)) return this.fail(ws, "not_admin");
    const items = this.sql.exec(
      `SELECT c.hash, c.name, c.hidden, p.name AS creator_name, COUNT(r.reporter) AS reports, GROUP_CONCAT(r.reason, ' | ') AS reasons
       FROM reports r JOIN cosmetics c ON c.hash = r.hash LEFT JOIN players p ON p.uuid = c.creator
       GROUP BY c.hash ORDER BY reports DESC LIMIT 50`
    ).toArray().map((r) => ({ hash: r.hash, name: r.name, hidden: !!r.hidden, creatorName: r.creator_name || "?", reports: r.reports, reasons: r.reasons || "" }));
    this.send(ws, { type: "admin_reports", items });
  }
  /** An admin decided a reported cape is fine: it comes back and its reports are cleared. */
  adminUnhide(ws, me, msg) {
    if (!this.isAdmin(me.uuid)) return this.fail(ws, "not_admin");
    if (typeof msg.hash !== "string" || !HASH.test(msg.hash)) return this.fail(ws, "bad_cape");
    if (!this.sql.exec("SELECT 1 FROM cosmetic_data WHERE hash = ?", msg.hash).toArray().length) return this.fail(ws, "bad_cape");
    this.sql.exec("DELETE FROM reports WHERE hash = ?", msg.hash);
    this.sql.exec("UPDATE cosmetics SET hidden = 0 WHERE hash = ?", msg.hash);
    this.send(ws, { type: "ok", for: "admin_unhide" });
  }
  // --- Helpers ------------------------------------------------------------------------
  areFriends(a, b) {
    return this.sql.exec("SELECT 1 FROM friends WHERE a = ? AND b = ?", a, b).toArray().length > 0;
  }
  blocks(blocker, blocked) {
    return this.sql.exec("SELECT 1 FROM blocks WHERE blocker = ? AND blocked = ?", blocker, blocked).toArray().length > 0;
  }
  count(table, column, value) {
    return this.sql.exec(`SELECT COUNT(*) AS n FROM ${table} WHERE ${column} = ?`, value).one().n;
  }
  socketsOf(uuid, except) {
    return this.ctx.getWebSockets().filter((s) => {
      if (s === except) return false;
      const a = s.deserializeAttachment();
      return a && a.state === "ready" && a.uuid === uuid;
    });
  }
  isOnline(uuid, except) {
    return this.socketsOf(uuid, except).length > 0;
  }
  /** Sends to every game the player has open. Returns false if they're offline. */
  tellPlayer(uuid, obj) {
    const sockets = this.socketsOf(uuid);
    for (const s of sockets) this.send(s, obj);
    return sockets.length > 0;
  }
  tellFriends(uuid, obj) {
    for (const r of this.sql.exec("SELECT b FROM friends WHERE a = ?", uuid).toArray()) this.tellPlayer(r.b, obj);
  }
  allowed(uuid) {
    const now = Date.now();
    const times = (this.recent.get(uuid) || []).filter((t) => now - t < RATE_WINDOW_MS);
    times.push(now);
    this.recent.set(uuid, times);
    return times.length <= RATE_LIMIT;
  }
  send(ws, obj) {
    try {
      ws.send(JSON.stringify(obj));
    } catch {
    }
  }
  fail(ws, reason) {
    this.send(ws, { type: "error", reason });
  }
};
function pngSize(b) {
  const sig = [137, 80, 78, 71, 13, 10, 26, 10];
  if (b.length < 33 || sig.some((v2, i) => b[i] !== v2)) return null;
  if (String.fromCharCode(b[12], b[13], b[14], b[15]) !== "IHDR") return null;
  const v = new DataView(b.buffer, b.byteOffset, b.byteLength);
  return { w: v.getUint32(16), h: v.getUint32(20) };
}
__name(pngSize, "pngSize");
function checkModel(m) {
  const num = /* @__PURE__ */ __name((v, lo, hi) => typeof v === "number" && Number.isFinite(v) && v >= lo && v <= hi, "num");
  const vec = /* @__PURE__ */ __name((v, lo, hi) => Array.isArray(v) && v.length === 3 && v.every((x) => num(x, lo, hi)), "vec");
  if (!m || m.v !== 1 || !Array.isArray(m.cubes) || typeof m.texture !== "string") return "bad_model";
  if (!m.cubes.length || m.cubes.length > MAX_CUBES) return "too_many_cubes";
  if (!num(m.tw, 1, 1024) || !num(m.th, 1, 1024)) return "bad_model";
  for (const c of m.cubes) {
    if (!vec(c.from, -48, 80) || !vec(c.to, -48, 80)) return "model_too_large";
    if (c.origin !== void 0 && !vec(c.origin, -48, 80)) return "model_too_large";
    if (c.rot !== void 0 && !vec(c.rot, -180, 180)) return "bad_model";
    if (!c.faces || typeof c.faces !== "object") return "bad_model";
    for (const [k, uv] of Object.entries(c.faces)) {
      if (!["north", "south", "east", "west", "up", "down"].includes(k)) return "bad_model";
      if (!Array.isArray(uv) || uv.length < 4 || !uv.slice(0, 4).every((x) => num(x, -1024, 2048))) return "bad_model";
    }
  }
  return null;
}
__name(checkModel, "checkModel");
function skinSizeOk(w, h) {
  return [1, 2, 4, 8].some((n) => w === 64 * n && h === 64 * n);
}
__name(skinSizeOk, "skinSizeOk");
function capeSizeOk(w, h) {
  return [1, 2, 4, 8].some((n) => w === 64 * n && h === 32 * n);
}
__name(capeSizeOk, "capeSizeOk");
function validUuid(v) {
  return typeof v === "string" && UUID.test(v);
}
__name(validUuid, "validUuid");
function devUuid(name) {
  let h = "";
  for (const c of name.toLowerCase().padEnd(16, "_")) h += c.charCodeAt(0).toString(16).padStart(2, "0");
  return ("00000000" + h).slice(0, 32);
}
__name(devUuid, "devUuid");
export {
  Hub,
  index_default as default
};
//# sourceMappingURL=index.js.map
