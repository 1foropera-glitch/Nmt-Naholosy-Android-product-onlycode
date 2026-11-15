import test from "node:test";
import assert from "node:assert/strict";
import { assertPurchaseOwner, isActivePurchase } from "./purchase-policy.js";
test("only a purchased, non-consumed, non-refunded product grants entitlement", () => {
  assert.equal(isActivePurchase({ purchaseState: 0, consumptionState: 0 }), true);
  for (const state of [1, 2, undefined]) assert.equal(isActivePurchase({ purchaseState: state }), false);
  assert.equal(isActivePurchase({ purchaseState: 0, consumptionState: 1 }), false);
  assert.equal(isActivePurchase({ purchaseState: 0, refundableQuantity: 0 }), false);
});
test("a token bound to an active account cannot be claimed by another account", () => {
  assert.throws(() => assertPurchaseOwner("b", "a"), /MISMATCH/);
  assert.doesNotThrow(() => assertPurchaseOwner("a", "a"));
});
test("an erased binding does not permanently lock a Play-verified purchase to the former UID", () => {
  assert.doesNotThrow(() => assertPurchaseOwner("recreated-uid", undefined));
});
