import { createUser, request, commit, expect, name, profile, progress, wordKey, time } from "./emulator-client.mjs";
import { uploadForEmulator, serverProgress, closeEmulatorClient } from "../functions/emulator-progress-client.mjs";
try {
const owner = await createUser();
const stranger = await createUser();
const root = `users/${owner.uid}`;
const epoch = `${root}/epochs/0/wordProgress/${wordKey}`;
await expect(await request("GET", root), 403, "unauthenticated access denied");
await expect(await request("PATCH", root, owner.token, profile()), 200, "owner creates v2 profile");
await expect(await request("GET", root, stranger.token), 403, "other account cannot read profile");
await expect(await commit(owner.token, [
  { update: { name: name(root), fields: profile(0, 1) } },
  { update: { name: name(epoch), fields: progress() } },
]), 403, "legacy writes without a validated counter delta are denied");
await uploadForEmulator(owner.uid, { generation: 0, progress: [serverProgress(wordKey)] });
await expect(await request("GET", epoch, owner.token), 200, "owner can read server-validated progress");
await expect(await request("GET", epoch, stranger.token), 403, "other account cannot read word progress");
await expect(await request("PATCH", epoch, owner.token, progress()), 403, "unversioned snapshot overwrite denied");
await expect(await commit(owner.token, [
  { update: { name: name(root), fields: profile(0, 2) } },
  { update: { name: name(`${root}/epochs/0/wordProgress/unknown-word`), fields: progress("unknown-word") } },
]), 403, "unknown word cannot consume arbitrary documents");
await expect(await request("PATCH", `${root}/entitlements/premium`, owner.token, { active: { booleanValue: true } }), 403, "client cannot grant Premium");
await expect(await commit(owner.token, [
  { update: { name: name(root), fields: profile(1, 2, "reset-1") } },
  { update: { name: name(`${root}/resetRequests/reset-1`), fields: {
    generation: { integerValue: "1" }, createdAt: { timestampValue: new Date().toISOString() },
  } }, updateTransforms: [{ fieldPath: "createdAt", setToServerValue: "REQUEST_TIME" }] },
]), 200, "reset advances generation with immutable receipt");
await expect(await request("PATCH", `${root}/resetRequests/reset-1`, owner.token, {
  generation: { integerValue: "2" }, createdAt: { timestampValue: time },
}), 403, "reset receipt cannot be overwritten");
await expect(await request("GET", epoch, owner.token), 403, "retired generation is inaccessible");
await expect(await commit(owner.token, [
  { update: { name: name(root), fields: profile(1, 3, "reset-1") } },
  { update: { name: name(epoch), fields: progress() } },
]), 403, "stale device cannot resurrect retired progress");
await expect(await request("PATCH", root, owner.token, profile(0, 3, "reset-1")), 403, "generation cannot move backwards");
await expect(await request("PATCH", `${root}/wordProgress/${wordKey}`, owner.token, progress()), 403, "legacy writers are fenced off");
await expect(await request("GET", `progressRateLimits/${owner.uid}`, owner.token), 403, "server rate limits are private");
console.log("All validated-write rules checks passed.");
} finally { await closeEmulatorClient(); }
