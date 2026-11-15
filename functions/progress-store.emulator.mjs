// Destructive fixtures are restricted to a localhost demo emulator; never run against live Firebase.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { uploadValidatedProgress } from "./progress-store.js";

const projectId = process.env.GCLOUD_PROJECT || "demo-naholosy";
const host = process.env.FIRESTORE_EMULATOR_HOST || "";
if (!projectId.startsWith("demo-") || !/^(127\.0\.0\.1|localhost):\d+$/.test(host)) throw new Error("Local demo emulator required.");
const catalog = new Set(JSON.parse(readFileSync(new URL("./progress-catalog.json", import.meta.url), "utf8")));
const [key, key2] = catalog;
const app = initializeApp({ projectId }, "progress-server-tests");
const db = getFirestore(app);
const uid = `validated-${Date.now()}`;
const user = db.doc(`users/${uid}`);
const word = user.collection("epochs").doc("0").collection("wordProgress").doc(key);
const fixture = (deviceId, correct, wordKey = key) => ({ schemaVersion: 2, wordKey, box: 2, status: "learning",
  dueAt: 1000, correctCount: correct, wrongCount: 0, lapseCount: 0, lastReviewedAt: 100, updatedAt: 100,
  revision: correct, deviceId, counters: { [deviceId]: { correct, wrong: 0, lapses: 0 } } });
const input = (value, generation = 0) => ({ generation, progress: [value] });
try {
  await user.set({ schemaVersion: 2, generation: 0, version: 0, migrationCompleted: true,
    lastResetId: "", createdAt: Timestamp.now(), updatedAt: Timestamp.now() });
  await assert.rejects(uploadValidatedProgress(db, null, input(fixture("a", 1)), catalog), { code: "unauthenticated" });
  const first = await uploadValidatedProgress(db, uid, input(fixture("a", 1)), catalog);
  assert.equal(first.beforeVersion, 0); assert.equal(first.profile.version, 1);
  const replay = await uploadValidatedProgress(db, uid, input(fixture("a", 1)), catalog);
  assert.equal(replay.profile.version, 1);
  await Promise.all([uploadValidatedProgress(db, uid, input(fixture("a", 2)), catalog),
    uploadValidatedProgress(db, uid, input(fixture("b", 3)), catalog)]);
  assert.equal((await word.get()).get("correctCount"), 5);
  console.log("PASS server transaction, replay and concurrent merge");

  const before = (await user.get()).get("version");
  const bad = fixture("a", 2, key2); bad.counters.other = "invalid";
  await assert.rejects(uploadValidatedProgress(db, uid, { generation: 0, progress: [fixture("a", 4), bad] }, catalog), { code: "invalid-argument" });
  assert.equal((await user.get()).get("version"), before);
  assert.equal((await word.get()).get("correctCount"), 5);
  console.log("PASS malformed batch cannot partially write or advance version");

  await word.update({ counters: { bad: "invalid" } });
  await assert.rejects(uploadValidatedProgress(db, uid, input(fixture("a", 6)), catalog),
    (error) => error.details?.reason === "corrupt-progress");
  assert.deepEqual((await word.get()).get("counters"), { bad: "invalid" });
  console.log("PASS corrupted stored record is preserved for recovery");

  await user.update({ generation: 1, version: before + 1 });
  await assert.rejects(uploadValidatedProgress(db, uid, input(fixture("a", 8)), catalog),
    (error) => error.details?.reason === "generation-changed");
  const secondEpoch = await uploadValidatedProgress(db, uid, input(fixture("a", 1), 1), catalog);
  assert.equal(secondEpoch.profile.generation, 1);
  await assert.rejects(uploadValidatedProgress(db, "different-user", input(fixture("a", 2)), catalog), { code: "failed-precondition" });
  console.log("PASS stale generation and missing other profile cannot write");

  await db.doc(`progressRateLimits/${uid}`).set({ windowStart: Date.now(), count: 60 });
  await assert.rejects(uploadValidatedProgress(db, uid, input(fixture("a", 2), 1), catalog), { code: "resource-exhausted" });
  console.log("PASS per-user request limit");

  // Late migration requests must not resurrect progress after reset or after completion.
  const skippedAfterReset = await uploadValidatedProgress(db, uid, { ...input(fixture("legacy", 50)), migration: true }, catalog);
  assert.equal(skippedAfterReset.profile.generation, 1);
  assert.deepEqual(skippedAfterReset.progress, []);
  await user.update({ generation: 0, migrationCompleted: true }); // Test fixture only.
  const skippedCompleted = await uploadValidatedProgress(db, uid, { ...input(fixture("legacy", 50)), migration: true }, catalog);
  assert.deepEqual(skippedCompleted.progress, []);
  await user.update({ migrationCompleted: false });
  await db.doc(`progressRateLimits/${uid}`).delete();
  const legacyBatch = { generation: 0, progress: [fixture("legacy", 4, key2)], migration: true };
  const migrated = await uploadValidatedProgress(db, uid, legacyBatch, catalog);
  assert.equal(migrated.progress[0].correctCount, 4);
  const repeatedMigration = await uploadValidatedProgress(db, uid, legacyBatch, catalog);
  assert.equal(repeatedMigration.profile.version, migrated.profile.version);
  console.log("PASS legacy migration, retry and reset/completion fences");
} finally {
  await db.recursiveDelete(user);
  await db.doc(`progressRateLimits/${uid}`).delete();
  await db.terminate();
  await deleteApp(app);
}
