import { readFileSync } from "node:fs";
import { initializeApp, deleteApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { uploadValidatedProgress } from "./progress-store.js";

const projectId = process.env.GCLOUD_PROJECT || "demo-naholosy";
const host = process.env.FIRESTORE_EMULATOR_HOST || "";
if (!projectId.startsWith("demo-") || !/^(127\.0\.0\.1|localhost):\d+$/.test(host)) throw new Error("Local demo emulator required.");
const catalog = new Set(JSON.parse(readFileSync(new URL("./progress-catalog.json", import.meta.url), "utf8")));
const app = initializeApp({ projectId }, "validated-progress-test-client");
const db = getFirestore(app);

export const serverProgress = (wordKey, deviceId = "test-device", correct = 1) => ({ schemaVersion: 2, wordKey,
  box: 1, status: "learning", dueAt: 1000, correctCount: correct, wrongCount: 0, lapseCount: 0,
  lastReviewedAt: 100, updatedAt: 100, revision: correct, deviceId,
  counters: { [deviceId]: { correct, wrong: 0, lapses: 0 } } });
// This bypasses callable transport solely for testing the server transaction; Auth is tested via REST rules.
export const uploadForEmulator = (uid, data) => uploadValidatedProgress(db, uid, data, catalog);
export async function closeEmulatorClient() { await db.terminate(); await deleteApp(app); }
