# CuE: stress evidence and topology rebuild (2026-10-15)

## Environment and outcome

- Windows, **AMD Ryzen 5 5600X**, JDK 17, Forge **47.4.20**, Minecraft 1.20.1.
- Tested ordinary reobfuscated `unusualcopper-0.13.0-alpha.1.jar` alongside a **separate** production probe JAR. The probe is never installed with the player-facing mod.
- Isolated `run-production` world; no client profile or live server world modified by the benchmark.
- 100 mixed machines at full load, 650 CuE network nodes, 326 item network nodes, 36 forced chunks. Workload includes entity farming, brewing, forestry, smelting, harvesting and moving physical items.
- Seven phases: baseline before; idle and active 50; idle and active 100; 100 with energy/item pipes repeatedly broken/reconnected; baseline after.
- Evidence directory `.codex-tmp/performance-evidence/20261015T202527Z/`: `server.log`, `performance-ticks.csv`, `summary.json`.
- 35 warm-up ticks and **205 measured ticks per phase**; all seven phases completed and server exited normally. All **131** required Forge GameTests and JUnit suite passed after changing graph rebuild.

| Phase | p95 MSPT (ms) | Added vs worse baseline p95 (ms) |
|---|---:|---:|
| Baseline before | 1.665 | – |
| Idle 50 | 3.271 | 1.606 |
| Active 50 | 4.173 | 2.508 |
| Idle 100 | 2.311 | 0.646 |
| Active 100 | 4.473 | 2.808 |
| Churn 100 | 3.688 | 2.023 |
| Baseline after | 0.940 | – |

**D09 pilot passes:** the total p95 is under 45 ms in every phase and the extra p95 is no more than 5 ms, including churn, on the requested Ryzen 5 5600X. The results do not guarantee 20 TPS on a different machine, a full modpack, or a large active server with players.

## Why churn was slow and what changed

The earlier one-tick `EnergyNetworks.rebuild` traversed the entire graph after each pipe update. Under churn, it gave 9.883 ms p95 tick time and +8.618 ms versus the earlier baseline. The new `Build` spreads traversal across bounded steps of up to 96 work units per level tick. **Transfers pause while the topology is incomplete**, so they cannot use a stale path through a broken pipe. An unchanged component preserves its graph identity and observation counters. Ordinary unit tests, GameTests and the mixed real-server workload have passed.

The workload fixture was also corrected:
- `spawn-animals=true` keeps synthetic test animals alive; natural spawning still disabled by game rule.
- Reaper accepts passive adult cow fixtures rather than only monsters.
- Brewer is supplied with physical glass bottles and reagents, not water potions.
- Trees are regenerated deterministically for reproducibility; forestry has a 200-tick retry and is required to consume CuE in both steady active phases, but not necessarily each short churn window.
- Baseline and not-yet-installed slots no longer spawn synthetic animals; entities are cleared when returning to baseline.
- Probe runner stages its own temporary JARs and restores the original test runtime mod list and server.properties after exit.

## Reproduction

```powershell
cd "D:\Unusual copper"
.\gradlew.bat --offline --no-daemon test jar productionProbeJar runGameTestServer
python tools/run_performance_probe.py --jar build/libs/unusualcopper-0.13.0-alpha.1.jar --probe .codex-tmp/production-probe/unusualcopper-probe-0.13.0-alpha.1.jar --warm 35 --samples 205
```

For release certification, repeat with the default **200 warm-up and 600 measured ticks per phase**, run multiple seeds and restarts, include the complete Better MC Forge pack, measure live-server player load/client FPS, and close D11 crash/inter-chunk durability checks. This document is evidence for a verified **alpha checkpoint**, not a 1.0 sign-off.
