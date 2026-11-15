import { readFile } from "node:fs/promises";
import { performance } from "node:perf_hooks";
import { fixtures } from "./fixtures.mjs";
const [target, scenario = "auth", samplesArg = "5"] = process.argv.slice(2);
const scenarios = ["auth", "progress-small", "progress-20", "progress-max", "purchase", "deletion-page"];
const samples = Number(samplesArg);
if (!target || !scenarios.includes(scenario) || !Number.isInteger(samples) || samples < 1 || samples > 20) {
  throw new Error("Usage: npm run probe -- <base-url> <scenario> <1..20 samples>");
}
const url = new URL(target);
if (url.username || url.password || url.search || url.hash || url.pathname !== "/"
  || !(url.protocol === "https:" && /^naholosy-free-prototype\.[a-z0-9-]+\.workers\.dev$/.test(url.hostname)
    || url.protocol === "http:" && ["127.0.0.1", "localhost"].includes(url.hostname))) {
  throw new Error("Only localhost or the exact prototype workers.dev name is accepted; never send this secret to another host.");
}
const vars = await readFile(new URL("../.dev.vars", import.meta.url), "utf8");
const secret = vars.match(/^PROBE_TOKEN=([a-f0-9]{64})\s*$/m)?.[1];
if (!secret) throw new Error("Local PROBE_TOKEN missing. Run scripts/cloudflare-prototype.ps1 -Action Prepare.");
const fixture = fixtures();
console.log("Synthetic probe only. clientWallMs is NOT Cloudflare CPU time. No Firebase/Play data is accessed.");
for (let n = 0; n < samples; n++) {
  const start = performance.now();
  try {
    const response = await fetch(new URL(`/probe/${scenario}`, url), { method: "POST", redirect: "error",
      headers: { Authorization: `Bearer ${secret}`, "Content-Type": "application/json" },
      body: JSON.stringify({ ...fixture, coldKeys: n === 0 }), signal: AbortSignal.timeout(30000) });
    const body = await response.json().catch(() => ({}));
    console.log(JSON.stringify({ sample: n + 1, scenario, coldKeys: n === 0, status: response.status,
      clientWallMs: Math.round(performance.now() - start), result: body.result ?? null, cpuTimeMs: null }));
    if (!response.ok) { process.exitCode = 1; break; }
  } catch {
    console.log(JSON.stringify({ sample: n + 1, scenario, error: "request-failed", cpuTimeMs: null }));
    process.exitCode = 1; break;
  }
}
