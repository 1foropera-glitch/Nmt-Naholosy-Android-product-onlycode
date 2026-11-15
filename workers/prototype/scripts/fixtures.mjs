import { generateKeyPairSync, createSign } from "node:crypto";
export function fixtures() {
  const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 2048 });
  const now = Math.floor(Date.now() / 1000);
  const encode = (data) => Buffer.from(JSON.stringify(data)).toString("base64url");
  function token(issuer) {
    const body = `${encode({ alg: "RS256", typ: "JWT" })}.${encode({ iss: issuer, aud: "naholosy-prototype",
      sub: "synthetic-user", iat: now, exp: now + 1800 })}`;
    return `${body}.${createSign("RSA-SHA256").update(body).sign(privateKey).toString("base64url")}`;
  }
  return { publicKey: publicKey.export({ format: "jwk" }), idToken: token("https://prototype.invalid/auth"),
    appCheckToken: token("https://prototype.invalid/app-check") };
}
