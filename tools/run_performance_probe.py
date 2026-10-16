"""Run CuE's ordinary-JAR stress benchmark in the owned Forge fixture.

Keeps the previous mods and server.properties, uses a unique disposable world,
and writes raw tick data and machine-readable summaries under .codex-tmp.
"""
import argparse
import csv
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import shutil
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
PHASES = ("baseline-before", "idle-50", "active-50", "idle-100",
          "active-100", "churn-100", "baseline-after")
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--runtime", type=Path, default=ROOT / "run-production")
parser.add_argument("--jar", type=Path, required=True)
parser.add_argument("--probe", type=Path, required=True)
parser.add_argument("--java", default=shutil.which("java"))
parser.add_argument("--forge", default="1.20.1-47.4.20")
parser.add_argument("--warm", type=int, default=200)
parser.add_argument("--samples", type=int, default=600)
parser.add_argument("--max-seconds", type=int, default=900)
args = parser.parse_args()
if not 0 <= args.warm <= 100000 or not 5 <= args.samples <= 100000:
    parser.error("--warm must be >=0 and --samples must be >=5")
runtime = args.runtime.resolve()
runtime.relative_to(ROOT)
if not args.java:
    parser.error("Java 17 runtime is required")
eula = runtime / "eula.txt"
if not eula.exists() or "eula=true" not in eula.read_text(encoding="utf-8").lower().splitlines():
    parser.error("Owner must accept EULA in the already-installed isolated runtime")
argfile = Path("libraries/net/minecraftforge/forge") / args.forge / (
    "win_args.txt" if os.name == "nt" else "unix_args.txt")
if not (runtime / argfile).is_file():
    parser.error("Isolated Forge " + args.forge + " is not installed")
mods = runtime / "mods"
mods.mkdir(exist_ok=True)
old_mods = sorted(mods.glob("*.jar"))
if any(not m.name.startswith(("unusualcopper-", "unusualcopper-probe-"))
       for m in old_mods):
    parser.error("Unexpected mods in the isolated runtime; refusing to replace them")
jar, probe = args.jar.resolve(), args.probe.resolve()
if not jar.is_file() or not probe.is_file() or jar.name == probe.name:
    parser.error("Both distinct built ordinary JARs must exist")
with socket.socket() as sock:
    if sock.connect_ex(("127.0.0.1", 25587)) == 0:
        parser.error("Dedicated test port 25587 is in use; not touching this runtime")
stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
evidence = ROOT / ".codex-tmp" / "performance-evidence" / stamp
evidence.mkdir(parents=True, exist_ok=False)
backup = evidence / "original-mods"
backup.mkdir()
lock = runtime / ".cue-performance.lock"
try:
    lock.open("x").close()
except FileExistsError:
    parser.error("Another performance run owns " + str(lock))
properties_file = runtime / "server.properties"
old_properties = properties_file.read_bytes() if properties_file.exists() else None
exit_code = None
try:
    for m in old_mods:
        shutil.copy2(m, backup / m.name)
        m.unlink()
    shutil.copy2(jar, mods / jar.name)
    shutil.copy2(probe, mods / probe.name)
    properties_file.write_text(
        "server-ip=127.0.0.1\nserver-port=25587\nonline-mode=false\n"
        "max-players=1\nlevel-name=cue-performance-" + stamp.lower() + "\n"
        "level-type=minecraft:flat\n"
        "generator-settings={\"biome\":\"minecraft:plains\",\"layers\":["
        "{\"block\":\"minecraft:bedrock\",\"height\":1},"
        "{\"block\":\"minecraft:dirt\",\"height\":2},"
        "{\"block\":\"minecraft:grass_block\",\"height\":1}]}\n"
        "generate-structures=false\nview-distance=2\nsimulation-distance=2\n"
        "sync-chunk-writes=true\nenable-rcon=false\nenable-query=false\n"
        "spawn-monsters=false\nspawn-animals=true\nspawn-npcs=false\n"
        "spawn-protection=0\n", encoding="utf-8", newline="\n")
    log = evidence / "server.log"
    command = [str(args.java), "-Xms512m", "-Xmx3g",
               "-Dunusualcopper.performanceProbe=true",
               f"-Dunusualcopper.perfWarm={args.warm}",
               f"-Dunusualcopper.perfSamples={args.samples}",
               "@" + str(argfile), "--nogui"]
    with log.open("w", encoding="utf-8") as stream:
        process = subprocess.Popen(command, cwd=runtime, stdin=subprocess.DEVNULL,
                                   stdout=stream, stderr=subprocess.STDOUT)
        try:
            exit_code = process.wait(timeout=args.max_seconds)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
            raise RuntimeError("Benchmark timed out after " + str(args.max_seconds) + "s")
    log_text = log.read_text(encoding="utf-8", errors="replace")
    csv_path = runtime / "performance-ticks.csv"
    if not csv_path.is_file():
        raise RuntimeError("Server produced no performance-ticks.csv")
    shutil.copy2(csv_path, evidence / "performance-ticks.csv")
    if exit_code != 0 or "CuE PERFORMANCE PASS" not in log_text or "Encountered an unexpected exception" in log_text:
        raise RuntimeError("Server did not complete successfully; last lines:\n" +
                           "\n".join(log_text.splitlines()[-35:]))
    if any("CuE PERFORMANCE PHASE PASS: " + phase not in log_text for phase in PHASES):
        raise RuntimeError("Missing mandatory successful phase in server log")
    with (evidence / "performance-ticks.csv").open(newline="", encoding="utf-8") as f:
        records = list(csv.DictReader(f))
    if len(records) != len(PHASES) * args.samples:
        raise RuntimeError(f"Expected {len(PHASES)*args.samples} rows, got {len(records)}")
    by_phase = {}
    def percentile(seq, fraction):
        return sorted(seq)[max(0, math.ceil(len(seq) * fraction) - 1)]
    for phase in PHASES:
        rows = [row for row in records if row["phase"] == phase]
        if len(rows) != args.samples:
            raise RuntimeError(f"Missing samples for {phase}: {len(rows)}")
        mspt = [int(row["body_ns"]) / 1e6 for row in rows]
        by_phase[phase] = {
            "samples": len(rows),
            "median_mspt": round(percentile(mspt, .5), 3),
            "p95_mspt": round(percentile(mspt, .95), 3),
            "max_mspt": round(max(mspt), 3),
            "peak_heap_mib": round(max(int(row["heap_bytes"]) for row in rows) / 1048576, 2),
            "avg_harness_ms": round(sum(int(row["harness_ns"]) for row in rows) / len(rows) / 1e6, 3),
            "subsystems_ms_per_tick": {
                key: round(sum(int(row[key + "_ns"]) for row in rows) / len(rows) / 1e6, 4)
                for key in ("machines", "energy", "items", "area", "sentinel", "brewer", "population")
            }
        }
    baseline = max(by_phase[p]["p95_mspt"] for p in ("baseline-before", "baseline-after"))
    comparison = {
        phase: {"added_p95_ms": round(by_phase[phase]["p95_mspt"] - baseline, 3),
                "under_45ms": by_phase[phase]["p95_mspt"] < 45,
                "added_under_5ms": by_phase[phase]["p95_mspt"] - baseline <= 5}
        for phase in ("idle-50", "active-50", "idle-100", "active-100", "churn-100")
    }
    summary = {
        "timestamp_utc": stamp, "os": platform.platform(),
        "cpu": platform.processor(), "java": str(args.java),
        "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
        "probe_sha256": hashlib.sha256(probe.read_bytes()).hexdigest(),
        "forge": args.forge, "warmup_ticks": args.warm,
        "sample_ticks": args.samples, "phases": by_phase,
        "baseline_p95_mspt": baseline, "comparison": comparison,
        "note": "body MSPT is instrumented server-tick time excluding probe maintenance; "
                "wall_ns includes deliberate 50 ms tick pacing. "
                "Comparisons are not hardware certification without confirming Ryzen 5 5600X."
    }
    (evidence / "summary.json").write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    print("CuE PERFORMANCE PASS: evidence =", evidence)
    for name in PHASES:
        s = by_phase[name]
        print(f"  {name}: median={s['median_mspt']}ms p95={s['p95_mspt']}ms max={s['max_mspt']}ms")
    print("D09 delta from worse baseline p95:", comparison)
except Exception as error:
    print("CuE PERFORMANCE FAIL:", error, "\nEvidence:", evidence)
    raise SystemExit(1)
finally:
    for m in (jar, probe):
        (mods / m.name).unlink(missing_ok=True)
    for m in old_mods:
        shutil.copy2(backup / m.name, mods / m.name)
    if old_properties is None:
        properties_file.unlink(missing_ok=True)
    else:
        properties_file.write_bytes(old_properties)
    lock.unlink(missing_ok=True)
