"""Run the reobfuscated probe in an isolated, preinstalled Forge server under this repo."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("runtime", type=Path)
parser.add_argument("--java", default=shutil.which("java"))
parser.add_argument("--jar", required=True, type=Path)
parser.add_argument("--probe", required=True, type=Path)
parser.add_argument("--forge", default="1.20.1-47.4.20")
args = parser.parse_args()
runtime = args.runtime.resolve()
runtime.relative_to(ROOT)  # This helper never launches against an external/live server.
if not args.java:
    raise SystemExit("Java 17 executable is required")
eula = runtime / "eula.txt"
if not eula.exists() or "eula=true" not in eula.read_text(encoding="utf-8").lower().splitlines():
    raise SystemExit("The isolated runtime needs its owner's existing EULA acceptance")
mods = runtime / "mods"
mods.mkdir(exist_ok=True)
allowed = {args.jar.name, args.probe.name}
if any(path.name not in allowed for path in mods.glob("*.jar")):
    raise SystemExit("Unexpected mods in the isolated runtime; use a clean fixture")
for source in (args.jar, args.probe):
    shutil.copy2(source.resolve(), mods / source.name)
properties = """server-ip=127.0.0.1
server-port=25587
online-mode=false
max-players=1
level-name=population-probe
level-type=minecraft:flat
generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}
generate-structures=false
view-distance=2
simulation-distance=2
sync-chunk-writes=true
enable-rcon=false
enable-query=false
spawn-monsters=false
spawn-animals=true
spawn-npcs=false
spawn-protection=16
"""
(runtime / "server.properties").write_text(properties, encoding="utf-8", newline="\n")
argfile = Path("libraries/net/minecraftforge/forge") / args.forge / ("win_args.txt" if os.name == "nt" else "unix_args.txt")
if not (runtime / argfile).is_file():
    raise SystemExit("Install Forge into the isolated runtime first")
log = ROOT / ".codex-tmp/production-population.log"
with log.open("w", encoding="utf-8") as output:
    process = subprocess.Popen([args.java, "-Xms512m", "-Xmx2g", "-Dunusualcopper.productionProbe=true", "@" + str(argfile), "--nogui"], cwd=runtime, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 180
    while process.poll() is None and time.monotonic() < deadline:
        time.sleep(.5)
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
        raise SystemExit("Owned production probe timed out; inspect " + str(log))
text = log.read_text(encoding="utf-8", errors="replace")
passed = [line for line in text.splitlines() if "CuE PRODUCTION POPULATION PASS" in line]
reaper = [line for line in text.splitlines() if "CuE PRODUCTION REAPER PASS" in line]
windup = [line for line in text.splitlines() if "CuE PRODUCTION WINDUP PASS" in line]
arborist = [line for line in text.splitlines() if "CuE PRODUCTION ARBORIST PASS" in line]
sentinel = [line for line in text.splitlines() if "CuE PRODUCTION SENTINEL PASS" in line]
brewer = [line for line in text.splitlines() if "CuE PRODUCTION BREWER PASS" in line]
enchanter = [line for line in text.splitlines() if "CuE PRODUCTION ENCHANTER PASS" in line]
inventory_index = [line for line in text.splitlines() if "CuE PRODUCTION INVENTORY INDEX PASS" in line]
energy_tiers = [line for line in text.splitlines() if "CuE PRODUCTION ENERGY TIERS PASS" in line]
energy_checkpoint = [line for line in text.splitlines() if "CuE PRODUCTION ENERGY TIERS DISK CHECKPOINT" in line]
energy_control = [line for line in text.splitlines() if "CuE PRODUCTION ENERGY CONTROL PASS" in line]
control_checkpoint = [line for line in text.splitlines() if "CuE PRODUCTION ENERGY CONTROL DISK CHECKPOINT" in line]
if process.returncode or "Encountered an unexpected exception" in text or not passed or not reaper or not windup or not arborist or not sentinel or not brewer or not enchanter or not inventory_index or not energy_tiers or not energy_checkpoint or not energy_control or not control_checkpoint:
    print("\n".join(text.splitlines()[-45:]))
    raise SystemExit("Production probe failed; Java exit " + str(process.returncode))
print(passed[-1])
print(reaper[-1])
print(windup[-1])
print(arborist[-1])
print(sentinel[-1])
print(brewer[-1])
print(enchanter[-1])
print(inventory_index[-1])
print(energy_tiers[-1])
print(energy_checkpoint[-1])
print(energy_control[-1])
print(control_checkpoint[-1])
for line in text.splitlines():
    if "CuE PRODUCTION BREWER DISK" in line or "CuE PRODUCTION ENCHANTER DISK" in line or "CuE PRODUCTION ENERGY TIERS DISK RESTORE" in line or "CuE PRODUCTION ENERGY CONTROL DISK RESTORE" in line:
        print(line)
print("Production server stopped cleanly; log:", log)
