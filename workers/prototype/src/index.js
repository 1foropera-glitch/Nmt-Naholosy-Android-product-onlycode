import catalogKeys from "../../../functions/progress-catalog.json";
import { validateUpload, mergeValidatedProgress } from "../../../functions/progress-validation.js";
import { isActivePurchase } from "../../../functions/purchase-policy.js";
import { verifyFixturePair } from "./fixture-auth.js";

const catalog = new Set(catalogKeys);
const encoder = new TextEncoder();
export const SCENARIOS = ["auth", "progress-small", "progress-20", "progress-max", "purchase", "deletion-page"];
function json(data, status = 200) {
  return Response.json(data, { status, headers: { "Cache-Control": "no-store", "X-Content-Type-Options": "nosniff" } });
}
async function authorized(request, secret) {
  const supplied = request.headers.get("Authorization");
  if (!supplied || supplied.length !== 71 || !/^Bearer [a-f0-9]{64}$/.test(supplied)) return false;
  const [a, b] = await Promise.all([secret, supplied.slice(7)].map(async (s) =>
    new Uint8Array(await crypto.subtle.digest("SHA-256", encoder.encode(s)))));
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a[i] ^ b[i];
  return difference === 0;
}
async function boundedJson(request) {
  const reader = request.body?.getReader();
  if (!reader) throw new Error("Missing body");
  const chunks = []; let length = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      length += value.length;
      if (length > 8192) { await reader.cancel(); throw new Error("Body too large"); }
      chunks.push(value);
    }
  } finally { reader.releaseLock(); }
  const data = new Uint8Array(length); let at = 0;
  for (const chunk of chunks) { data.set(chunk, at); at += chunk.length; }
  return JSON.parse(new TextDecoder().decode(data));
}
function word(key, writers, remote) {
  const counters = Object.fromEntries(Array.from({ length: writers }, (_, n) =>
    [`device-${n}`, { correct: remote ? 2 : 1, wrong: 0, lapses: 0 }]));
  return { schemaVersion: 2, wordKey: key, box: 1, status: "learning", dueAt: 1800000000000,
    correctCount: writers * (remote ? 2 : 1), wrongCount: 0, lapseCount: 0,
    lastReviewedAt: 1790000000000, updatedAt: 1790000000000, revision: remote ? 2 : 1,
    deviceId: "device-0", counters };
}
function exercise(scenario) {
  if (scenario.startsWith("progress-")) {
    const count = scenario === "progress-small" ? 1 : 20;
    const writers = scenario === "progress-max" ? 512 : 2;
    // Includes synthetic generation + JSON parsing, so this is not an exact production CPU measurement.
    const wire = JSON.stringify({ generation: 0, progress: catalogKeys.slice(0, count).map((key) => word(key, writers, false)) });
    const batch = validateUpload(JSON.parse(wire), catalog);
    const merged = batch.progress.map((local) => mergeValidatedProgress(local, word(local.wordKey, writers, true), catalog));
    const serialized = JSON.stringify(merged);
    return { words: count, writersPerWord: writers, inputCharacters: wire.length,
      outputCharacters: serialized.length, correctTotal: merged.reduce((sum, w) => sum + w.correctCount, 0) };
  }
  if (scenario === "purchase") {
    return { active: isActivePurchase(JSON.parse('{"purchaseState":0,"consumptionState":0,"acknowledgementState":1}')),
      livePlayVerification: false };
  }
  if (scenario === "deletion-page") {
    // Build one bounded Firestore REST batch, never send it or delete anything.
    const writes = Array.from({ length: 20 }, (_, n) => ({ delete:
      `projects/demo-naholosy/databases/(default)/documents/users/synthetic/epochs/0/wordProgress/fixture-${n}` }));
    return { writes: writes.length, requestCharacters: JSON.stringify({ writes }).length, executed: false };
  }
  return { verifiedFixtureTokens: 2 };
}
export default {
  async fetch(request, env) {
    const path = new URL(request.url).pathname;
    if (path === "/health" && request.method === "GET") return json({ status: "prototype", version: 1, productionReady: false });
    const scenario = path.startsWith("/probe/") ? path.slice(7) : "";
    if (!SCENARIOS.includes(scenario)) return json({ error: "not-found" }, 404);
    if (request.method !== "POST") return json({ error: "method-not-allowed" }, 405);
    if (typeof env.PROBE_TOKEN !== "string" || !/^[a-f0-9]{64}$/.test(env.PROBE_TOKEN)) return json({ error: "probe-disabled" }, 503);
    if (!await authorized(request, env.PROBE_TOKEN)) return json({ error: "unauthorized" }, 401);
    if (!request.headers.get("Content-Type")?.startsWith("application/json")) return json({ error: "json-required" }, 415);
    let input;
    try { input = await boundedJson(request); }
    catch { return json({ error: "invalid-request" }, 400); }
    try { await verifyFixturePair(input); }
    catch { return json({ error: "invalid-fixture-auth" }, 400); }
    return json({ scenario, synthetic: true, coldKeys: input.coldKeys === true,
      cpuMeasured: false, result: exercise(scenario) });
  }
};
