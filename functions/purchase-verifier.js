import { FieldValue } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { assertPurchaseOwner, isActivePurchase } from "./purchase-policy.js";
import { assertNoDeletion, deletionInProgress, hash as tokenKey } from "./account-deletion.js";

export function createPurchaseVerifier({ db, publisher, packageName, productId }) {
return async function verify(uid, token) {
  await assertNoDeletion(db, uid);
  const tokenRef = db.doc(`purchaseTokens/${tokenKey(token)}`);
  const existing = await tokenRef.get();
  if (existing.exists && existing.get("uid") !== uid) {
    throw new HttpsError("permission-denied", "Ця покупка належить іншому акаунту.");
  }
  const checkedAt = Date.now();
  let purchase;
  try {
    ({ data: purchase } = await publisher.purchases.products.get({ packageName, productId, token }));
  } catch (error) {
    const code = Number(error.code ?? error.response?.status);
    if (code === 404 || code === 410) purchase = { purchaseState: 1 };
    else throw new HttpsError("unavailable", "Google Play зараз недоступний.");
  }
  try { assertPurchaseOwner(uid, existing.get("uid")); }
  catch { throw new HttpsError("permission-denied", "Ця покупка належить іншому акаунту."); }
  const active = isActivePurchase(purchase);
  if (active && purchase.acknowledgementState === 0) {
    await publisher.purchases.products.acknowledge({ packageName, productId, token, requestBody: {} });
  }
  if (!active && !existing.exists) return { active: false };
  return db.runTransaction(async (tx) => {
    const current = await tx.get(tokenRef);
    const deletion = await tx.get(db.doc(`accountDeletionJobs/${uid}`));
    const owned = await tx.get(db.collection("purchaseTokens").where("uid", "==", uid).where("productId", "==", productId));
    if (deletion.exists) throw deletionInProgress();
    if (current.exists && current.get("uid") !== uid) {
      throw new HttpsError("permission-denied", "Ця покупка належить іншому акаунту.");
    }
    if (current.exists && current.get("checkedAt") > checkedAt) {
      return { active: owned.docs.some((doc) => doc.get("active") === true) };
    }
    tx.set(tokenRef, { uid, token, productId, active, checkedAt,
      updatedAt: FieldValue.serverTimestamp() });
    const entitled = active || owned.docs.some((doc) => doc.id !== tokenRef.id && doc.get("active") === true);
    tx.set(db.doc(`users/${uid}/entitlements/premium`), {
      active: entitled, productId, updatedAt: FieldValue.serverTimestamp(),
    });
    return { active: entitled };
  });
}
;
}
