import { FieldValue, Timestamp } from "firebase-admin/firestore";
import { HttpsError } from "firebase-functions/v2/https";
import { isDeepStrictEqual } from "node:util";
import { InvalidProgress, MAX_COUNT, validateProgress, validateUpload, mergeValidatedProgress } from "./progress-validation.js";
import { deletionInProgress } from "./account-deletion.js";

export function firestoreProgressToWire(data) {
  const converted = { ...data };
  for (const field of ["dueAt", "lastReviewedAt", "updatedAt"]) {
    if (field === "dueAt" && data[field] === null) continue;
    if (!(data[field] instanceof Timestamp)) throw new InvalidProgress("Invalid stored timestamp.");
    converted[field] = data[field].toMillis();
  }
  return converted;
}

function toFirestore(data) {
  const converted = { ...data };
  for (const field of ["dueAt", "lastReviewedAt", "updatedAt"]) {
    if (data[field] !== null) converted[field] = Timestamp.fromMillis(data[field]);
  }
  return converted;
}

/** UID is supplied exclusively by verified callable Auth, never by request.data. */
export async function uploadValidatedProgress(db, uid, input, catalog, now = Date.now()) {
  if (typeof uid !== "string" || !uid || uid.includes("/")) throw new HttpsError("unauthenticated", "Увійдіть через Google.");
  let batch;
  try { batch = validateUpload(input, catalog); }
  catch (error) {
    if (error instanceof InvalidProgress) throw new HttpsError("invalid-argument", "Некоректний формат прогресу.");
    throw error;
  }
  const user = db.doc(`users/${uid}`);
  const rate = db.doc(`progressRateLimits/${uid}`);
  const refs = batch.progress.map((item) => user.collection("epochs").doc(String(batch.generation))
    .collection("wordProgress").doc(item.wordKey));
  return db.runTransaction(async (tx) => {
    const [profileSnapshot, rateSnapshot, deletion, ...documents] = await tx.getAll(user, rate, db.doc(`accountDeletionJobs/${uid}`), ...refs);
    if (deletion.exists) throw deletionInProgress();
    const root = profileSnapshot.data();
    if (!root || root.schemaVersion !== 2 || ![root.generation, root.version].every((n) => Number.isInteger(n) && n >= 0 && n < MAX_COUNT)) {
      throw new HttpsError("failed-precondition", "Профіль потребує ініціалізації або оновлення.");
    }
    // A reset/completed migration fences off stale legacy batches, including retried requests.
    if (batch.migration && (root.generation !== 0 || root.migrationCompleted === true)) {
      return { beforeVersion: root.version, profile: { generation: root.generation, version: root.version,
        migrationCompleted: root.migrationCompleted === true }, progress: [] };
    }
    if (root.generation !== batch.generation) throw new HttpsError("failed-precondition", "Покоління прогресу змінилося.", { reason: "generation-changed" });
    const sameWindow = rateSnapshot.exists && now >= rateSnapshot.get("windowStart") && now - rateSnapshot.get("windowStart") < 60_000;
    const count = sameWindow ? rateSnapshot.get("count") : 0;
    if (count >= 60) throw new HttpsError("resource-exhausted", "Повторіть синхронізацію за хвилину.");
    let remote;
    try {
      remote = documents.map((doc) => doc.exists ? validateProgress(firestoreProgressToWire(doc.data()), catalog) : null);
      if (remote.some((item, i) => item && item.wordKey !== batch.progress[i].wordKey)) throw new InvalidProgress();
    } catch (error) {
      if (error instanceof InvalidProgress) throw new HttpsError("failed-precondition", "Хмарний запис потребує відновлення.", { reason: "corrupt-progress" });
      throw error;
    }
    let merged;
    try { merged = batch.progress.map((item, i) => mergeValidatedProgress(item, remote[i], catalog)); }
    catch (error) {
      if (error instanceof InvalidProgress) throw new HttpsError("failed-precondition", "Прогрес перевищив підтримуваний діапазон.", { reason: "progress-capacity" });
      throw error;
    }
    const changed = merged.map((item, i) => !isDeepStrictEqual(item, remote[i]));
    const version = root.version + (changed.some(Boolean) ? 1 : 0);
    changed.forEach((write, i) => { if (write) tx.set(refs[i], toFirestore(merged[i])); });
    if (version !== root.version) tx.update(user, { version, updatedAt: FieldValue.serverTimestamp() });
    tx.set(rate, { windowStart: sameWindow ? rateSnapshot.get("windowStart") : now, count: count + 1 });
    return { beforeVersion: root.version, profile: { generation: root.generation, version,
      migrationCompleted: root.migrationCompleted === true }, progress: merged };
  });
}
