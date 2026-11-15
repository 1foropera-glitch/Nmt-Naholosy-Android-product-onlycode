import { spawnSync } from "node:child_process";
// Each child additionally verifies a demo project and localhost emulator endpoints.
for (const script of ["scripts/test-spark-rules.mjs", "scripts/test-firestore-rules.mjs", "functions/progress-store.emulator.mjs", "functions/account-deletion.emulator.mjs"]) {
  const result = spawnSync(process.execPath, [script], { stdio: "inherit" });
  if (result.error) throw result.error;
  if (result.status !== 0) process.exit(result.status ?? 1);
}
