import test from "node:test";
import assert from "node:assert/strict";
import { requireRecentGoogleAuth, receiptMatches, hash } from "./account-deletion.js";
const now = 1_800_000_000_000;
const auth = { uid: "user", token: { auth_time: now / 1000, firebase: { sign_in_provider: "google.com" } } };
test("deletion requires recent Google authentication, not a recent token refresh", () => {
  assert.doesNotThrow(() => requireRecentGoogleAuth(auth, now));
  for (const value of [null, { ...auth, token: { ...auth.token, auth_time: now / 1000 - 301 } },
    { ...auth, token: { ...auth.token, auth_time: now / 1000 + 61 } },
    { ...auth, token: { ...auth.token, firebase: { sign_in_provider: "password" } } }]) {
    assert.throws(() => requireRecentGoogleAuth(value, now));
  }
});
test("receipt recovery requires the exact secret; UID alone does not authorize status", () => {
  const receipt = "12345678-1234-4234-8234-123456789abc";
  assert.equal(receiptMatches(receipt, hash(receipt)), true);
  assert.equal(receiptMatches("12345678-1234-4234-8234-123456789abd", hash(receipt)), false);
  assert.equal(receiptMatches(undefined, hash(receipt)), false);
});
