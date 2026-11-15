// Local demo fixtures only. Play responses are simulated; this is not a live billing test.
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { getFirestore, Timestamp } from "firebase-admin/firestore";
import { readFileSync } from "node:fs";
import { requestDeletion, processDeletion, deletionStatus, sweepDeletions, hash } from "./account-deletion.js";
import { uploadValidatedProgress } from "./progress-store.js";
import { createPurchaseVerifier } from "./purchase-verifier.js";
import { createUser, request, profile, time } from "../scripts/emulator-client.mjs";

const projectId = process.env.GCLOUD_PROJECT || "demo-naholosy";
if (!projectId.startsWith("demo-") || ![process.env.FIRESTORE_EMULATOR_HOST, process.env.FIREBASE_AUTH_EMULATOR_HOST]
  .every((host) => /^(127\.0\.0\.1|localhost):\d+$/.test(host || ""))) throw new Error("Local demo emulators required");
const app = initializeApp({ projectId }, "deletion-server-tests");
const db = getFirestore(app), auth = getAuth(app);
const original = await createUser();
const uid = original.uid, nextUid = `restore-${randomUUID()}`, otherUid = `other-${randomUUID()}`;
const receipt = randomUUID(), token = `fixture-purchase-${randomUUID()}`, key = hash(token);
const user = db.doc(`users/${uid}`), job = db.doc(`accountDeletionJobs/${uid}`);
const tokenRef = db.doc(`purchaseTokens/${key}`);
const productId = "premium_remove_ads", packageName = "com.intervalninaholosy.nmt";
const catalog = new Set(JSON.parse(readFileSync(new URL("./progress-catalog.json", import.meta.url))));
const [wordKey] = catalog;
const word = { schemaVersion: 2, wordKey, box: 1, status: "learning", dueAt: 1000, correctCount: 1,
 wrongCount: 0, lapseCount: 0, lastReviewedAt: 100, updatedAt: 100, revision: 1, deviceId: "d", counters: { d: { correct: 1, wrong: 0, lapses: 0 } } };
const caller = (id) => ({ uid: id, token: { auth_time: Math.floor(Date.now() / 1000), firebase: { sign_in_provider: "google.com" } } });
const tokens = [token];
const claimsBefore = (await db.collection("deletedPurchaseClaims").get()).size;
let playCalls = 0, acknowledgements = 0;
const ownedPurchase = { purchaseState: 0, consumptionState: 0, acknowledgementState: 1, obfuscatedExternalAccountId: hash(uid) };
const publisherFor = (get) => ({ purchases: { products: {
  get,
  acknowledge: async (args) => { assert.equal(args.productId, productId); acknowledgements++; }
} } });
const verify = createPurchaseVerifier({ db, productId, packageName, publisher: publisherFor(async (args) => {
  assert.equal(args.packageName, packageName); assert.equal(args.productId, productId);
  playCalls++; return { data: ownedPurchase };
}) });
try {
  await user.set({ schemaVersion: 2, generation: 0, version: 0, migrationCompleted: true, lastResetId: "", createdAt: Timestamp.fromDate(new Date(time)), updatedAt: Timestamp.now() });
  await user.collection("epochs").doc("0").collection("wordProgress").doc(wordKey).set({ test: true });
  await user.collection("wordProgress").doc(wordKey).set({ test: true });
  await user.collection("resetRequests").doc("old-reset").set({ test: true });
  await user.collection("entitlements").doc("premium").set({ active: true });
  await tokenRef.set({ uid, token, productId, active: true, purchaseAccountFingerprint: hash(uid) });
  for (const path of ["billingRateLimits", "progressRateLimits"]) await db.doc(`${path}/${uid}`).set({ count: 1 });
  await auth.createUser({ uid: nextUid }); await auth.createUser({ uid: otherUid });
  await assert.rejects(verify(nextUid, token), { code: "permission-denied" });
  assert.equal(playCalls, 0);
  assert.equal((await request("PATCH", `users/${uid}`, original.token, profile())).status, 200);
  assert.equal((await requestDeletion(db, caller(uid), receipt)).status, "pending");
  assert.equal((await requestDeletion(db, caller(uid), receipt)).status, "pending");
  await assert.rejects(requestDeletion(db, caller(uid), randomUUID()), { code: "failed-precondition" });
  assert.equal((await request("PATCH", `users/${uid}`, original.token, profile())).status, 403);
  await assert.rejects(uploadValidatedProgress(db, uid, { generation: 0, progress: [word] }, catalog),
    (error) => error.details?.reason === "account-deletion");
  await assert.rejects(verify(uid, token), (error) => error.details?.reason === "account-deletion");
  console.log("PASS active purchase ownership, deletion receipt, rules and server write fences");

  await job.update({ leaseUntil: Timestamp.fromMillis(Date.now() + 600_000) });
  assert.equal(await processDeletion(db, auth, uid), false);
  assert.equal((await tokenRef.get()).exists, true);
  await job.update({ leaseUntil: Timestamp.fromMillis(0) });
  const failingAuth = { updateUser: (...a) => auth.updateUser(...a), revokeRefreshTokens: (...a) => auth.revokeRefreshTokens(...a),
    deleteUser: async () => { throw new Error("injected transient Auth failure"); } };
  await assert.rejects(processDeletion(db, failingAuth, uid));
  assert.equal((await job.get()).get("status"), "pending");
  assert.equal((await tokenRef.get()).exists, false);
  assert.equal((await db.collection("deletedPurchaseClaims").get()).size, claimsBefore);
  assert.equal(await processDeletion(db, auth, uid), true);
  assert.equal(await processDeletion(db, auth, uid), false);
  await assert.rejects(auth.getUser(uid), { code: "auth/user-not-found" });
  for (const path of [`users/${uid}`, `users/${uid}/epochs/0/wordProgress/${wordKey}`, `users/${uid}/wordProgress/${wordKey}`,
    `users/${uid}/resetRequests/old-reset`, `users/${uid}/entitlements/premium`, `billingRateLimits/${uid}`, `progressRateLimits/${uid}`]) {
    assert.equal((await db.doc(path).get()).exists, false, path);
  }
  assert.deepEqual(Object.keys((await job.get()).data()).sort(), ["completedAt", "expiresAt", "receiptHash", "status"]);
  assert.equal((await deletionStatus(db, uid, receipt)).status, "completed");
  await assert.rejects(deletionStatus(db, uid, randomUUID()), { code: "not-found" });
  console.log("PASS interruption recovery erases all profile/purchase data without a retained purchase claim");

  // Expiring the operational receipt cannot affect restore eligibility.
  await job.update({ expiresAt: Timestamp.fromMillis(Date.now() - 1) });
  await sweepDeletions(db, auth);
  assert.equal((await job.get()).exists, false);
  assert.equal((await verify(nextUid, token)).active, true);
  assert.equal((await tokenRef.get()).get("uid"), nextUid);
  assert.equal((await tokenRef.get()).get("purchaseAccountFingerprint"), undefined);
  assert.equal((await verify(nextUid, token)).active, true);
  assert.equal(playCalls, 2);
  assert.equal((await db.doc(`users/${nextUid}/entitlements/premium`).get()).get("active"), true);
  await assert.rejects(verify(otherUid, token), { code: "permission-denied" });
  assert.equal(playCalls, 2);
  console.log("PASS fresh Play verification restores after deletion/receipt expiry; original UID fingerprint is no longer a lock");

  for (const state of [{ purchaseState: 1 }, { purchaseState: 2 }, { ...ownedPurchase, refundableQuantity: 0 },
    { ...ownedPurchase, consumptionState: 1 }, {}]) {
    const invalid = `invalid-${randomUUID()}`; tokens.push(invalid);
    const check = createPurchaseVerifier({ db, productId, packageName, publisher: publisherFor(async () => ({ data: state })) });
    assert.equal((await check(otherUid, invalid)).active, false);
    assert.equal((await db.doc(`purchaseTokens/${hash(invalid)}`).get()).exists, false);
    assert.equal((await db.doc(`users/${otherUid}/entitlements/premium`).get()).exists, false);
  }
  const offlineToken = `offline-${randomUUID()}`; tokens.push(offlineToken);
  const unavailable = createPurchaseVerifier({ db, productId, packageName,
    publisher: publisherFor(async () => { throw Object.assign(new Error("network unavailable"), { code: 503 }); }) });
  await assert.rejects(unavailable(otherUid, offlineToken), { code: "unavailable" });
  assert.equal((await db.doc(`purchaseTokens/${hash(offlineToken)}`).get()).exists, false);
  assert.equal(acknowledgements, 0);
  console.log("PASS pending/refunded/consumed/unknown and unavailable Play do not grant or acknowledge Premium");

  // Both callers reach Play before either can claim the same unbound token.
  const concurrentToken = `concurrent-${randomUUID()}`; tokens.push(concurrentToken);
  let arrived = 0, release;
  const both = new Promise((resolve) => { release = resolve; });
  const concurrent = createPurchaseVerifier({ db, productId, packageName, publisher: publisherFor(async () => {
    if (++arrived === 2) release();
    await both; return { data: ownedPurchase };
  }) });
  const outcomes = await Promise.allSettled([concurrent(nextUid, concurrentToken), concurrent(otherUid, concurrentToken)]);
  assert.equal(outcomes.filter((r) => r.status === "fulfilled" && r.value.active).length, 1);
  assert.equal(outcomes.filter((r) => r.status === "rejected" && r.reason.code === "permission-denied").length, 1);
  console.log("PASS concurrent restores serialize to exactly one owner");

  const racingToken = `racing-${randomUUID()}`; tokens.push(racingToken);
  const racing = createPurchaseVerifier({ db, productId, packageName, publisher: publisherFor(async () => {
    await requestDeletion(db, caller(nextUid), randomUUID());
    return { data: ownedPurchase };
  }) });
  await assert.rejects(racing(nextUid, racingToken), (error) => error.details?.reason === "account-deletion");
  assert.equal((await db.doc(`purchaseTokens/${hash(racingToken)}`).get()).exists, false);
  await sweepDeletions(db, auth);
  assert.equal((await tokenRef.get()).exists, false);
  assert.equal((await db.doc(`users/${nextUid}/entitlements/premium`).get()).exists, false);
  assert.equal((await db.collection("deletedPurchaseClaims").get()).size, claimsBefore);
  console.log("PASS deletion racing with Play verification cannot recreate entitlement; scheduler completes pending work");

  const unacknowledged = createPurchaseVerifier({ db, productId, packageName,
    publisher: publisherFor(async () => ({ data: { ...ownedPurchase, acknowledgementState: 0 } })) });
  assert.equal((await unacknowledged(otherUid, token)).active, true);
  assert.equal(acknowledgements, 1);
  const refund = createPurchaseVerifier({ db, productId, packageName,
    publisher: publisherFor(async () => ({ data: { purchaseState: 1 } })) });
  for (const fixture of tokens.slice(1)) await db.doc(`purchaseTokens/${hash(fixture)}`).delete();
  assert.equal((await refund(otherUid, token)).active, false);
  assert.equal((await db.doc(`users/${otherUid}/entitlements/premium`).get()).get("active"), false);
  console.log("PASS valid purchase acknowledgement and refund revocation still work");
} finally {
  for (const id of [uid, nextUid, otherUid]) {
    await db.recursiveDelete(db.doc(`users/${id}`));
    for (const collection of ["accountDeletionJobs", "billingRateLimits", "progressRateLimits"]) await db.doc(`${collection}/${id}`).delete();
    try { await auth.deleteUser(id); } catch (error) { if (error.code !== "auth/user-not-found") throw error; }
  }
  for (const fixture of tokens) await db.doc(`purchaseTokens/${hash(fixture)}`).delete();
  await db.terminate(); await deleteApp(app);
}
