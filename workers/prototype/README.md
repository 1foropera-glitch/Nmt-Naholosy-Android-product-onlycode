# Workers Free prototype

Status 2026-11-08: live synthetic probes completed; the largest scenario exceeded the documented Free CPU budget. The deployed prototype was removed. The application now uses Variant A (Firebase Spark + client Play Billing). See [decision and results](../../docs/spark-migration-2026-11-08.md). The setup procedure below is retained for reproducibility, not an active deployment requirement.

Synthetic CPU feasibility probe, **not the application backend**. It cannot sync real data, validate real Firebase identities, grant Premium or delete accounts. Do not point Android at it.

Uses the app's real progress validator/merge functions on generated fixtures. The RSA verifier deliberately accepts a client-supplied test public key; it must never be reused as production authentication. Probe access itself requires a separate random `PROBE_TOKEN` secret. The synthetic purchase scenario checks local purchase-state logic only; it does not prove live Google Play integration.

## Local setup (PowerShell, repository root)

```powershell
npm.cmd ci --prefix workers/prototype
.\scripts\cloudflare-prototype.ps1 -Action Prepare
.\scripts\cloudflare-prototype.ps1 -Action Check
```

Wrangler and its matching Miniflare runtime are pinned in package-lock.json. `Check` bundles with `deploy --dry-run` and runs workerd integration tests. Local timings are **not** Cloudflare CPU measurements. No code is deployed by `Check`.

`Prepare` generates an ignored `.dev.vars` file and does not print the secret. Re-running preserves it. Workspace-local OAuth config and Wrangler logs live under ignored `.local-verification/cloudflare-local/`; keep that directory private. Do not upload it or `.dev.vars` to source control or chat.

## Cloudflare authorization

```powershell
.\scripts\cloudflare-prototype.ps1 -Action Login
.\scripts\cloudflare-prototype.ps1 -Action Status
```

The browser requests only `account:read`, `user:read`, `workers_scripts:write`, `workers_tail:read` and the automatically added `offline_access`. The script keeps its config environment overrides scoped to that invocation. Use the wrapper for subsequent auth operations so it finds the same local credentials. No domain or billing changes are performed.

Before any deployment, confirm the selected account has **Workers Free**. A website's Free plan is a different setting. If there are multiple accounts, explicitly select the correct Account ID. Do not choose Workers Paid or add payment details to proceed. Verify the target `naholosy-free-prototype` name is unused or belongs to this prototype; do not overwrite an unrelated Worker.

## Online probe procedure after authorization and plan check

1. Deploy the reviewed bundle to the selected Free account as `naholosy-free-prototype`, with no database, cron, paid bindings or custom routes. Configure `PROBE_TOKEN` as a Worker secret using the existing local value without printing it. An unset secret keeps all probes disabled (503).
2. Check `/health`: it must return `status=prototype` and `productionReady=false`. A probe without its secret must return 401. No production endpoint should exist.
3. Run one scenario at a time, initially five sequential samples. Example, substituting the actual returned workers.dev address:

   ```powershell
   npm.cmd run probe --prefix workers/prototype -- https://naholosy-free-prototype.ACCOUNT-SUBDOMAIN.workers.dev auth 5
   ```

   Supported cases: `auth`, `progress-small`, `progress-20`, `progress-max`, `purchase`, `deletion-page`. The runner accepts only localhost or this prototype's exact workers.dev name, does not follow redirects and caps each run at 20 samples. It does not print credentials or send personal data. The upper-case placeholder in the example must be replaced with the real host.
4. Inspect the Worker's Cloudflare Metrics CPU time and error counters for the same time window. Record scenario, UTC window, sample count, CPU quantiles/max where available, request outcomes and any 1102/exceededCpu failures. `clientWallMs` in the runner is round-trip latency, **not CPU**. `coldKeys=true` forces key import, not a guaranteed cold isolate start. Do not infer CPU=0 from absent telemetry.
5. Stop after failures and revise the workload/batching. Do not raise the plan to make a failing test pass. The largest allowed progress payload needs separate attention; success of the one-word case is insufficient.

This preliminary probe includes synthetic fixture generation/JSON roundtrip overhead and omits Google public-key retrieval, production claim verification, service-account OAuth, Firestore/Play network calls, transaction retries, and durable deletion recovery. A successful probe is not production acceptance. The next stage implements those real paths on development/test data with least-privilege credentials and repeats measurements.

## References

- [Workers limits](https://developers.cloudflare.com/workers/platform/limits/)
- [Metrics and analytics](https://developers.cloudflare.com/workers/observability/metrics-and-analytics/)
- [Wrangler OAuth login](https://developers.cloudflare.com/workers/wrangler/commands/general/#login)
- [Local testing](https://developers.cloudflare.com/workers/testing/)
