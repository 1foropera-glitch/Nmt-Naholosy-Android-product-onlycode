// The active binding is exclusive. After deletion, a freshly Play-verified bearer token
// may be bound again. The original obfuscated UID is not the identity of the Play account
// and must not permanently lock a purchase to a deleted Firebase UID.
export function assertPurchaseOwner(uid, existingUid) {
  if (existingUid && existingUid !== uid) {
    throw new Error("PURCHASE_ACCOUNT_MISMATCH");
  }
}

export function isActivePurchase(purchase) {
  return purchase.purchaseState === 0 && purchase.consumptionState !== 1
    && (purchase.refundableQuantity === undefined || purchase.refundableQuantity > 0);
}
