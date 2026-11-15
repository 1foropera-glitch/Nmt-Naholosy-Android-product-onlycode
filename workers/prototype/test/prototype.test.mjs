import test from "node:test";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { Miniflare, convertV4MiniflareOptions } from "miniflare";
import { fixtures } from "../scripts/fixtures.mjs";
const secret = "a".repeat(64);
const fixture = fixtures();
const mf = new Miniflare(convertV4MiniflareOptions({ modules: true, scriptPath: fileURLToPath(new URL("../dist/index.js", import.meta.url)),
  compatibilityDate: "2026-07-30", bindings: { PROBE_TOKEN: secret }, outboundService: () => { throw new Error("Unexpected network call"); } }));
function post(path, body = fixture, key = secret) {
  return mf.dispatchFetch(`https://fixture.invalid${path}`, { method: "POST",
    headers: { "Content-Type": "application/json", Authorization: `Bearer ${key}` }, body: JSON.stringify(body) });
}
test("prototype does not offer production endpoints or unprotected probes", async () => {
  assert.equal((await (await mf.dispatchFetch("https://fixture.invalid/health")).json()).productionReady, false);
  for (const path of ["/verifyPremiumPurchase", "/requestAccountDeletion", "/syncWordProgress"]) {
    assert.equal((await post(path)).status, 404);
  }
  assert.equal((await post("/probe/auth", fixture, "b".repeat(64))).status, 401);
  assert.equal((await post("/probe/auth", fixture, "")).status, 401);
});
test("tampered or wrong-role fixture tokens and oversized body are rejected", async () => {
  assert.equal((await post("/probe/auth", { ...fixture, idToken: fixture.idToken.slice(0, -8) + "AAAAAAAA" })).status, 400);
  assert.equal((await post("/probe/auth", { ...fixture, appCheckToken: fixture.idToken })).status, 400);
  assert.equal((await post("/probe/auth", { ...fixture, pad: "x".repeat(9000) })).status, 400);
});
test("concurrent fixture key changes do not mix token verification keys", async () => {
  const responses = await Promise.all([fixture, fixtures(), fixture].map((body) => post("/probe/auth", body)));
  assert.deepEqual(responses.map((r) => r.status), [200, 200, 200]);
  await Promise.all(responses.map((r) => r.text()));
});
test("all bounded synthetic scenarios execute in workerd without external network access", async () => {
  for (const scenario of ["auth", "progress-small", "progress-20", "progress-max", "purchase", "deletion-page"]) {
    const response = await post(`/probe/${scenario}`, { ...fixture, coldKeys: true });
    assert.equal(response.status, 200, scenario);
    const body = await response.json();
    assert.equal(body.synthetic, true); assert.equal(body.cpuMeasured, false);
    if (scenario === "progress-max") { assert.equal(body.result.words, 20); assert.equal(body.result.writersPerWord, 512); assert.equal(body.result.correctTotal, 20480); }
    if (scenario === "purchase") { assert.equal(body.result.active, true); assert.equal(body.result.livePlayVerification, false); }
    if (scenario === "deletion-page") assert.equal(body.result.executed, false);
  }
});
test.after(async () => { await mf.dispose(); });
