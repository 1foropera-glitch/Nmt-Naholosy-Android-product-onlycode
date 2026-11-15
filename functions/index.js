import { createHash } from "node:crypto";
import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onMessagePublished } from "firebase-functions/v2/pubsub";
import { google } from "googleapis";
import { createPurchaseVerifier } from "./purchase-verifier.js";
import { getAuth } from "firebase-admin/auth";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { deletionInProgress, requestDeletion, processDeletion, deletionStatus,
  sweepDeletions } from "./account-deletion.js";
import { readFileSync } from "node:fs";
import { uploadValidatedProgress } from "./progress-store.js";

initializeApp();
const db = getFirestore();
const authService = getAuth();
const progressCatalog = new Set(JSON.parse(readFileSync(new URL("./progress-catalog.json", import.meta.url), "utf8")));
const packageName = "com.intervalninaholosy.nmt";
const productId = "premium_remove_ads";
const publisher = google.androidpublisher({
  version: "v3",
  auth: new google.auth.GoogleAuth({ scopes: ["https://www.googleapis.com/auth/androidpublisher"] }),
});
const tokenKey = (token) => createHash("sha256").update(token).digest("hex");
const verify = createPurchaseVerifier({ db, publisher, packageName, productId });

// All word-progress writes, including legacy migration, must use this validated path.
export const syncWordProgress = onCall({
  region: "europe-west1", enforceAppCheck: true, maxInstances: 10, timeoutSeconds: 30,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Увійдіть через Google.");
  return uploadValidatedProgress(db, request.auth.uid, request.data, progressCatalog);
});

async function limitVerification(uid) {
  const ref = db.doc(`billingRateLimits/${uid}`);
  await db.runTransaction(async (tx) => {
    const snapshot = await tx.get(ref);
    if ((await tx.get(db.doc(`accountDeletionJobs/${uid}`))).exists) throw deletionInProgress();
    const now = Date.now();
    const sameWindow = snapshot.exists && now - snapshot.get("windowStart") < 60_000;
    const count = sameWindow ? snapshot.get("count") : 0;
    if (count >= 10) throw new HttpsError("resource-exhausted", "Спробуйте перевірити покупку за хвилину.");
    tx.set(ref, { windowStart: sameWindow ? snapshot.get("windowStart") : now, count: count + 1 });
  });
}


export const verifyPremiumPurchase = onCall({
  region: "europe-west1", enforceAppCheck: true, maxInstances: 10,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Увійдіть через Google.");
  const token = request.data?.purchaseToken;
  if (typeof token !== "string" || token.length < 10 || token.length > 4096) {
    throw new HttpsError("invalid-argument", "Некоректні дані покупки.");
  }
  await limitVerification(request.auth.uid);
  return verify(request.auth.uid, token);
});

export const refreshPremiumEntitlement = onCall({
  region: "europe-west1", enforceAppCheck: true, maxInstances: 10,
}, async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "Увійдіть через Google.");
  const uid = request.auth.uid;
  await limitVerification(uid);
  const tokens = await db.collection("purchaseTokens").where("uid", "==", uid)
    .where("productId", "==", productId).where("active", "==", true).limit(20).get();
  for (const doc of tokens.docs) await verify(uid, doc.get("token"));
  const entitlement = await db.doc(`users/${uid}/entitlements/premium`).get();
  return { active: entitlement.exists && entitlement.get("active") === true };
});

// Configure Google Play RTDN to publish to this topic in the same Firebase project.
export const playPurchaseChanged = onMessagePublished({
  topic: "play-billing", region: "europe-west1", retry: true, maxInstances: 10,
}, async (event) => {
  const notification = event.data.message.json;
  if (notification.packageName !== packageName) return;
  const token = notification.oneTimeProductNotification?.purchaseToken
    ?? notification.voidedPurchaseNotification?.purchaseToken;
  if (!token) return;
  const owner = await db.doc(`purchaseTokens/${tokenKey(token)}`).get();
  if (owner.exists && !(await db.doc(`accountDeletionJobs/${owner.get("uid")}`).get()).exists) await verify(owner.get("uid"), token);
});

export const requestAccountDeletion = onCall({ region: "europe-west1", enforceAppCheck: true,
  maxInstances: 10, timeoutSeconds: 300 }, async (request) => {
  await requestDeletion(db, request.auth, request.data?.receipt);
  try { await processDeletion(db, authService, request.auth.uid); }
  catch { console.warn("Account deletion pending; scheduled retry required."); }
  return deletionStatus(db, request.auth.uid, request.data.receipt);
});

// A high-entropy client receipt permits status recovery after Auth deletion or a lost response.
export const accountDeletionStatus = onCall({ region: "europe-west1", enforceAppCheck: true,
  maxInstances: 10 }, async (request) => deletionStatus(db, request.data?.uid, request.data?.receipt));

export const maintainAccountDeletions = onSchedule({ schedule: "every 15 minutes", region: "europe-west1",
  maxInstances: 1, timeoutSeconds: 540, retryCount: 3 }, () => sweepDeletions(db, authService));
