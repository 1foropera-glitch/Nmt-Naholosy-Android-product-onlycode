import { performance } from "node:perf_hooks";
import { createUser, request, profile, wordKey } from "./emulator-client.mjs";
import { uploadForEmulator, serverProgress, closeEmulatorClient } from "../functions/emulator-progress-client.mjs";

const users = Number(process.env.USERS || 50);
const concurrency = Number(process.env.CONCURRENCY || 10);
if (!Number.isInteger(users) || users < 1 || users > 5000 || !Number.isInteger(concurrency) || concurrency < 1 || concurrency > 200) {
  throw new Error("USERS must be 1..5000; CONCURRENCY must be 1..200.");
}
const durations = [];
let next = 0;
let failures = 0;
await Promise.all(Array.from({ length: concurrency }, async () => {
  while (next++ < users) {
    const start = performance.now();
    try {
      const account = await createUser();
      const root = `users/${account.uid}`;
      const created = await request("PATCH", root, account.token, profile());
      if (!created.ok) throw new Error(`create ${created.status}`);
      await uploadForEmulator(account.uid, { generation: 0, progress: [serverProgress(wordKey)] });
      durations.push(performance.now() - start);
    } catch (error) { failures++; console.error(error.message); }
  }
}));
durations.sort((a, b) => a - b);
const percentile = (p) => durations[Math.min(durations.length - 1, Math.floor(durations.length * p))] ?? null;
await closeEmulatorClient();
console.log(JSON.stringify({ environment: "localhost emulator, direct server transaction; no callable HTTP/App Check", users, concurrency, failures,
  p50Ms: percentile(0.5), p95Ms: percentile(0.95), p99Ms: percentile(0.99) }, null, 2));
if (failures) process.exitCode = 1;
