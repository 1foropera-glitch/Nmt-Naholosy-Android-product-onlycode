import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { InvalidProgress, MAX_COUNT, validateProgress, validateUpload, mergeValidatedProgress } from "./progress-validation.js";

const catalog = new Set(JSON.parse(readFileSync(new URL("./progress-catalog.json", import.meta.url), "utf8")));
const [key, key2] = catalog;
export const fixture = (deviceId = "device-a", correct = 2, overrides = {}) => ({
  schemaVersion: 2, wordKey: key, box: 2, status: "learning", dueAt: 1000,
  correctCount: correct, wrongCount: 0, lapseCount: 0, lastReviewedAt: 100, updatedAt: 100,
  revision: correct, deviceId, counters: { [deviceId]: { correct, wrong: 0, lapses: 0 } }, ...overrides,
});

test("valid progress and bounded unique batch are accepted", () => {
  const value = fixture();
  assert.deepEqual(validateProgress(value, catalog), value);
  assert.equal(validateUpload({ generation: 0, progress: [value, fixture("b", 1, { wordKey: key2 })] }, catalog).progress.length, 2);
});

test("every counter is checked, including a malicious non-writer key", () => {
  for (const invalid of ["bad", null, [], { correct: -1, wrong: 0, lapses: 0 },
    { correct: 1.5, wrong: 0, lapses: 0 }, { correct: 2, wrong: 0 },
    { correct: 0, wrong: 0, lapses: 0, extra: true }]) {
    const value = fixture();
    value.counters["another-device"] = invalid;
    assert.throws(() => validateProgress(value, catalog), InvalidProgress);
  }
});

test("mismatched totals, prototype keys, overflow and excessive writers are rejected", () => {
  assert.throws(() => validateProgress(fixture("a", 2, { correctCount: 3 }), catalog), InvalidProgress);
  const bad = fixture();
  bad.counters = JSON.parse('{"__proto__":{"correct":2,"wrong":0,"lapses":0}}');
  assert.throws(() => validateProgress(bad, catalog), InvalidProgress);
  assert.throws(() => validateProgress(fixture("a", MAX_COUNT + 1), catalog), InvalidProgress);
  const counters = Object.fromEntries(Array.from({ length: 513 }, (_, i) => [`writer-${i}`, { correct: 0, wrong: 0, lapses: 0 }]));
  assert.throws(() => validateProgress(fixture("a", 0, { counters }), catalog), InvalidProgress);
});

test("schema, catalog, timestamps and scheduling invariant cannot be bypassed", () => {
  for (const patch of [{ schemaVersion: 3 }, { wordKey: "not-in-catalog" }, { box: 8 },
    { status: "learned" }, { dueAt: null }, { dueAt: NaN }, { dueAt: 253_402_300_800_000 },
    { revision: -1 }, { updatedAt: "100" }, { deviceId: " " }, { extra: true }]) {
    assert.throws(() => validateProgress(fixture("a", 2, patch), catalog), InvalidProgress);
  }
  assert.equal(validateProgress(fixture("a", 2, { box: 7, status: "learned", dueAt: null }), catalog).status, "learned");
});

test("empty, oversized, duplicate and UID-bearing payloads are rejected", () => {
  for (const input of [{ generation: 0, progress: [] }, { generation: -1, progress: [fixture()] },
    { generation: 0, progress: Array(21).fill(fixture()) }, { generation: 0, progress: [fixture(), fixture()] },
    { generation: 0, progress: [fixture()], uid: "other-user" }]) {
    assert.throws(() => validateUpload(input, catalog), InvalidProgress);
  }
});

test("migration flag must be boolean and only targets the original epoch", () => {
  assert.equal(validateUpload({ generation: 0, progress: [fixture()], migration: true }, catalog).migration, true);
  for (const input of [{ generation: 1, progress: [fixture()], migration: true },
    { generation: 0, progress: [fixture()], migration: "true" }]) {
    assert.throws(() => validateUpload(input, catalog), InvalidProgress);
  }
});

test("concurrent device counts merge without duplication and register ordering matches Android", () => {
  const a = fixture("device-a", 3, { revision: 4, updatedAt: 200, box: 3 });
  const b = fixture("device-b", 2, { revision: 4, updatedAt: 200, box: 4 });
  const merged = mergeValidatedProgress(a, b, catalog);
  assert.equal(merged.correctCount, 5);
  assert.equal(merged.box, 4);
  assert.equal(merged.deviceId, "device-b");
  assert.deepEqual(merged, mergeValidatedProgress(b, a, catalog));
  assert.deepEqual(merged, mergeValidatedProgress(a, merged, catalog));
  assert.deepEqual(a, mergeValidatedProgress(a, a, catalog));
  assert.equal(mergeValidatedProgress(a, fixture("c", 1, { revision: 5, updatedAt: 1, box: 5 }), catalog).box, 5);
});

test("merged counter overflow fails even when both inputs are valid", () => {
  assert.throws(() => mergeValidatedProgress(fixture("a", MAX_COUNT), fixture("b", 1), catalog), InvalidProgress);
});
