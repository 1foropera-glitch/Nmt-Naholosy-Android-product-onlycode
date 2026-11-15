// Wire timestamps are integer epoch milliseconds; counters match Android's Int range.
export const MAX_COUNT = 2_147_483_647;
const FIELDS = ["schemaVersion", "wordKey", "box", "status", "dueAt", "correctCount", "wrongCount",
  "lapseCount", "lastReviewedAt", "updatedAt", "revision", "deviceId", "counters"];
const COUNTS = ["correct", "wrong", "lapses"];
const TOTALS = ["correctCount", "wrongCount", "lapseCount"];
export class InvalidProgress extends Error {}
const insist = (condition) => { if (!condition) throw new InvalidProgress("Invalid progress data."); };
const object = (value) => value !== null && typeof value === "object" && !Array.isArray(value)
  && [Object.prototype, null].includes(Object.getPrototypeOf(value));
const exact = (value, keys) => object(value) && Object.keys(value).length === keys.length
  && keys.every((key) => Object.hasOwn(value, key));
const natural = (value) => Number.isInteger(value) && value >= 0 && value <= MAX_COUNT;
const identifier = (value) => typeof value === "string" && value.trim().length > 0 && value.length <= 128;
const timestamp = (value) => Number.isInteger(value) && value >= -62_135_596_800_000 && value <= 253_402_300_799_999;

export function validateProgress(value, catalog) {
  insist(exact(value, FIELDS));
  insist(value.schemaVersion === 2 && typeof value.wordKey === "string" && catalog.has(value.wordKey));
  insist(Number.isInteger(value.box) && value.box >= 1 && value.box <= 7);
  insist((value.status === "learning" && timestamp(value.dueAt))
    || (value.status === "learned" && value.box === 7 && value.dueAt === null));
  insist(timestamp(value.lastReviewedAt) && timestamp(value.updatedAt) && natural(value.revision));
  insist(identifier(value.deviceId) && TOTALS.every((key) => natural(value[key])));
  insist(object(value.counters) && Object.keys(value.counters).length <= 512);
  const totals = [0, 0, 0];
  const counters = Object.fromEntries(Object.entries(value.counters).sort(([a], [b]) => a < b ? -1 : a > b ? 1 : 0)
    .map(([key, counter]) => {
      insist(identifier(key) && !["__proto__", "constructor", "prototype"].includes(key));
      insist(exact(counter, COUNTS) && COUNTS.every((name) => natural(counter[name])));
      COUNTS.forEach((name, index) => { totals[index] += counter[name]; });
      return [key, Object.fromEntries(COUNTS.map((name) => [name, counter[name]]))];
    }));
  insist(TOTALS.every((name, index) => natural(totals[index]) && value[name] === totals[index]));
  return { ...value, counters };
}

export function validateUpload(value, catalog) {
  insist((exact(value, ["generation", "progress"]) ||
    (exact(value, ["generation", "progress", "migration"]) && typeof value.migration === "boolean")) && natural(value.generation));
  insist(!value.migration || value.generation === 0);
  insist(Array.isArray(value.progress) && value.progress.length >= 1 && value.progress.length <= 20);
  const progress = value.progress.map((item) => validateProgress(item, catalog));
  insist(new Set(progress.map((item) => item.wordKey)).size === progress.length);
  return { generation: value.generation, progress, migration: value.migration === true };
}

/** Matches Kotlin mergeProgress: scheduling register plus maximum per writer counter. */
export function mergeValidatedProgress(local, remote, catalog) {
  local = validateProgress(local, catalog);
  if (!remote) return local;
  remote = validateProgress(remote, catalog);
  insist(local.wordKey === remote.wordKey);
  const winner = local.revision > remote.revision ? local : local.revision < remote.revision ? remote
    : local.updatedAt > remote.updatedAt ? local : local.updatedAt < remote.updatedAt ? remote
      : local.deviceId >= remote.deviceId ? local : remote;
  const keys = [...new Set([...Object.keys(local.counters), ...Object.keys(remote.counters)])].sort();
  const counters = Object.fromEntries(keys.map((key) => [key, Object.fromEntries(COUNTS.map((field) => [field,
    Math.max(local.counters[key]?.[field] ?? 0, remote.counters[key]?.[field] ?? 0)]))]));
  const totals = Object.fromEntries(TOTALS.map((name, index) => [name,
    Object.values(counters).reduce((sum, counter) => sum + counter[COUNTS[index]], 0)]));
  // The union can overflow even when the individual documents passed validation.
  return validateProgress({ ...winner, ...totals, counters }, catalog);
}
