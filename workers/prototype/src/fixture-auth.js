// BENCHMARK ONLY. The client supplies the public key for a synthetic identity.
// Never reuse this as Firebase/Auth/App Check verification in a production endpoint.
const encoder = new TextEncoder();
let cachedKey;
let cachedModulus;
function bytes(value) {
  if (typeof value !== "string" || !/^[A-Za-z0-9_-]+$/.test(value)) throw new Error("Invalid fixture token");
  return Uint8Array.from(atob(value.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0));
}
async function verify(token, key, issuer) {
  if (typeof token !== "string" || token.length > 2048) throw new Error("Invalid fixture token");
  const parts = token.split(".");
  if (parts.length !== 3) throw new Error("Invalid fixture token");
  const header = JSON.parse(new TextDecoder().decode(bytes(parts[0])));
  const claims = JSON.parse(new TextDecoder().decode(bytes(parts[1])));
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== "RS256" || header.typ !== "JWT" || claims.iss !== issuer
    || claims.aud !== "naholosy-prototype" || claims.sub !== "synthetic-user"
    || !Number.isInteger(claims.exp) || claims.exp <= now || claims.exp > now + 3600
    || !Number.isInteger(claims.iat) || claims.iat > now + 30) throw new Error("Invalid fixture claims");
  if (!await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, bytes(parts[2]), encoder.encode(`${parts[0]}.${parts[1]}`))) {
    throw new Error("Invalid fixture signature");
  }
}
export async function verifyFixturePair(input) {
  const jwk = input.publicKey;
  if (!jwk || jwk.kty !== "RSA" || jwk.e !== "AQAB" || typeof jwk.n !== "string"
    || jwk.n.length !== 342 || !/^[A-Za-z0-9_-]+$/.test(jwk.n) || jwk.d) throw new Error("Invalid fixture key");
  const cold = input.coldKeys === true;
  if (cold || !cachedKey || cachedModulus !== jwk.n) {
    cachedKey = await crypto.subtle.importKey("jwk", { kty: "RSA", e: jwk.e, n: jwk.n },
      { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    cachedModulus = jwk.n;
  }
  const key = cachedKey;
  await verify(input.idToken, key, "https://prototype.invalid/auth");
  await verify(input.appCheckToken, key, "https://prototype.invalid/app-check");
}
