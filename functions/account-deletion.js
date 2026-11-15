import { createHash, randomUUID, timingSafeEqual } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";

// Operational receipt/fence only. Never retain purchase or Google-provider identity here.
export const DELETION_STATUS_RETENTION_MS = 30 * 24 * 60 * 60 * 1000;
export const hash = (text) => createHash("sha256").update(text).digest("hex");
export function requireRecentGoogleAuth(auth, now = Date.now()) {
  if (!auth?.uid) throw new HttpsError("unauthenticated", "Увійдіть через Google.");
  const at = auth.token?.auth_time;
  if (auth.token?.firebase?.sign_in_provider !== "google.com" || !Number.isInteger(at) ||
      now - at * 1000 > 300_000 || at * 1000 > now + 60_000) {
    throw new HttpsError("failed-precondition", "Повторно підтвердьте Google-вхід.", { reason: "recent-login-required" });
  }
}
export function validReceipt(value) { return typeof value === "string" && /^[a-f0-9-]{36}$/.test(value); }
export function receiptMatches(receipt, digest) {
  return validReceipt(receipt) && typeof digest === "string" && /^[a-f0-9]{64}$/.test(digest) &&
    timingSafeEqual(Buffer.from(hash(receipt), "hex"), Buffer.from(digest, "hex"));
}
export async function assertNoDeletion(db, uid) {
  if ((await db.doc(`accountDeletionJobs/${uid}`).get()).exists) throw deletionInProgress();
}
export const deletionInProgress = () => new HttpsError("failed-precondition", "Акаунт видаляється або вже видалений.", { reason: "account-deletion" });

export async function requestDeletion(db, auth, receipt, now = Date.now()) {
  requireRecentGoogleAuth(auth, now);
  if (!validReceipt(receipt)) throw new HttpsError("invalid-argument", "Некоректний запит.");
  const ref = db.doc(`accountDeletionJobs/${auth.uid}`);
  // Retry an already queued request even if Auth has already disabled/deleted this user.
  const previous = await ref.get();
  if (previous.exists) {
    if (!receiptMatches(receipt, previous.get("receiptHash"))) throw deletionInProgress();
    return { status: previous.get("status") };
  }
  return db.runTransaction(async (tx) => {
    const current = await tx.get(ref);
    if (current.exists) {
      if (!receiptMatches(receipt, current.get("receiptHash"))) throw deletionInProgress();
      return { status: current.get("status") };
    }
    tx.create(ref, { status: "pending", receiptHash: hash(receipt), requestedAt: Timestamp.fromMillis(now),
      leaseUntil: Timestamp.fromMillis(0), attempts: 0 });
    return { status: "pending" };
  });
}

export async function deletionStatus(db, uid, receipt) {
  if (typeof uid !== "string" || !uid || uid.length > 128 || uid.includes("/") || !validReceipt(receipt)) {
    throw new HttpsError("invalid-argument", "Некоректний запит.");
  }
  const job = await db.doc(`accountDeletionJobs/${uid}`).get();
  if (!job.exists || !receiptMatches(receipt, job.get("receiptHash"))) throw new HttpsError("not-found", "Запит не знайдено.");
  return { status: job.get("status") };
}

export async function processDeletion(db, authService, uid, now = Date.now()) {
  const ref = db.doc(`accountDeletionJobs/${uid}`);
  const lease = randomUUID();
  const job = await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    if (!snapshot.exists || snapshot.get("status") !== "pending" || snapshot.get("leaseUntil").toMillis() > now) return null;
    tx.update(ref, { lease, leaseUntil: Timestamp.fromMillis(now + 600_000), attempts: snapshot.get("attempts") + 1 });
    return snapshot.data();
  });
  if (!job) return false;
  const ignoreMissingUser = async (action) => {
    try { await action(); } catch (error) { if (error.code !== "auth/user-not-found") throw error; }
  };
  try {
    await ignoreMissingUser(() => authService.updateUser(uid, { disabled: true }));
    await ignoreMissingUser(() => authService.revokeRefreshTokens(uid));
    // Erase active purchase bindings without creating a retained claim/archive.
    // Transactions serialize this with purchase verification and refuse to delete a new owner's binding.
    for (;;) {
      const tokens = await db.collection("purchaseTokens").where("uid", "==", uid).limit(100).get();
      if (tokens.empty) break;
      await db.runTransaction(async (tx) => {
        const current = await tx.getAll(...tokens.docs.map((doc) => doc.ref));
        for (const doc of current) {
          if (!doc.exists || doc.get("uid") !== uid) continue;
          tx.delete(doc.ref);
        }
      });
    }
    await db.recursiveDelete(db.doc(`users/${uid}`));
    await db.doc(`billingRateLimits/${uid}`).delete();
    await db.doc(`progressRateLimits/${uid}`).delete();
    await ignoreMissingUser(() => authService.deleteUser(uid));
    const completedAt = Timestamp.now();
    const expiresAt = Timestamp.fromMillis(completedAt.toMillis() + DELETION_STATUS_RETENTION_MS);
    // Replace rather than merge: no provider identity or purchase metadata survives completion.
    await ref.set({ status: "completed", receiptHash: job.receiptHash, completedAt, expiresAt });
    return true;
  } catch (error) {
    // Keep the durable job for scheduled retry; never log user data or raw purchase tokens.
    await ref.update({ leaseUntil: Timestamp.fromMillis(0), lease: "" });
    throw error;
  }
}

export async function sweepDeletions(db, authService) {
  const pending = await db.collection("accountDeletionJobs").where("status", "==", "pending").limit(20).get();
  let failures = 0;
  for (const job of pending.docs) {
    try { await processDeletion(db, authService, job.id); } catch { failures++; }
  }
  for (const collection of ["accountDeletionJobs"]) {
    // Only completed jobs have expiresAt; unfinished work is never discarded by retention cleanup.
    for (let page = 0; page < 10; page++) {
      const expired = await db.collection(collection).where("expiresAt", "<=", Timestamp.now()).limit(200).get();
      if (expired.empty) break;
      const batch = db.batch(); expired.docs.forEach((doc) => batch.delete(doc.ref)); await batch.commit();
    }
  }
  if (failures) throw new Error(`${failures} account deletion job(s) need retry`);
}
