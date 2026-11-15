import { readFile } from "node:fs/promises";

export const projectId = process.env.GCLOUD_PROJECT || "demo-naholosy";
const authHost = process.env.FIREBASE_AUTH_EMULATOR_HOST || "127.0.0.1:9099";
const firestoreHost = process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8080";
if (!projectId.startsWith("demo-") || ![authHost, firestoreHost].every((host) => /^(127\.0\.0\.1|localhost):\d+$/.test(host))) {
  throw new Error("These checks run only on localhost emulators with a demo- project.");
}
export const base = `http://${firestoreHost}/v1/projects/${projectId}/databases/(default)/documents`;
export const name = (path) => `projects/${projectId}/databases/(default)/documents/${path}`;
const words = JSON.parse(await readFile(new URL("../app/src/main/assets/words.json", import.meta.url), "utf8"));
export const wordKey = words[0].key;
export const time = new Date().toISOString();
export const profile = (generation = 0, version = 0, lastResetId = "") => ({
  schemaVersion: { integerValue: "2" }, generation: { integerValue: String(generation) },
  version: { integerValue: String(version) }, migrationCompleted: { booleanValue: true },
  lastResetId: { stringValue: lastResetId }, createdAt: { timestampValue: time }, updatedAt: { timestampValue: time },
});
export const progress = (key = wordKey) => ({
  schemaVersion: { integerValue: "2" }, wordKey: { stringValue: key }, box: { integerValue: "1" },
  status: { stringValue: "learning" }, dueAt: { timestampValue: time }, correctCount: { integerValue: "1" },
  wrongCount: { integerValue: "0" }, lapseCount: { integerValue: "0" }, lastReviewedAt: { timestampValue: time },
  updatedAt: { timestampValue: time }, revision: { integerValue: "1" }, deviceId: { stringValue: "test-device" },
  counters: { mapValue: { fields: { "test-device": { mapValue: { fields: {
    correct: { integerValue: "1" }, wrong: { integerValue: "0" }, lapses: { integerValue: "0" },
  } } } } } },
});
export async function createUser() {
  const response = await fetch(`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=fake-api-key`, {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ returnSecureToken: true }),
  });
  const body = await response.json();
  if (!response.ok) throw new Error(`Auth emulator: ${response.status}`);
  return { uid: body.localId, token: body.idToken };
}
export function request(method, path, token, fields) {
  return fetch(`${base}/${path}`, { method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: fields ? JSON.stringify({ fields }) : undefined });
}
export function commit(token, writes) {
  return fetch(`${base}:commit`, { method: "POST", headers: {
    "Content-Type": "application/json", Authorization: `Bearer ${token}`,
  }, body: JSON.stringify({ writes }) });
}
export async function expect(response, expected, label) {
  if (response.status !== expected) throw new Error(`${label}: expected ${expected}, got ${response.status}: ${await response.text()}`);
  console.log(`PASS ${label}`);
}
