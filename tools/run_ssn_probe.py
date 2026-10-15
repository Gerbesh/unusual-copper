"""Run the native SSN probe in a repo-owned Forge runtime, checking its actual pass markers."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("runtime", type=Path)
parser.add_argument("--forge-runtime", type=Path, default=ROOT / "run-production")
parser.add_argument("--java", required=True)
parser.add_argument("--jar", required=True, type=Path)
parser.add_argument("--probe", required=True, type=Path)
parser.add_argument("--ssn", type=Path, default=ROOT / ".codex-tmp/compat/SimpleStorageNetwork-1.20.1-1.13.2.jar")
parser.add_argument("--flib", type=Path, default=ROOT / ".codex-tmp/compat/flib-1.20.1-0.0.16.jar")
parser.add_argument("--progressive", type=Path)
parser.add_argument("--opac", type=Path)
args = parser.parse_args()
runtime, template = args.runtime.resolve(), args.forge_runtime.resolve()
runtime.relative_to(ROOT)
template.relative_to(ROOT)
if runtime == template:
    raise SystemExit("Use a separate SSN fixture runtime")
runtime.mkdir(exist_ok=True)
eula = runtime / "eula.txt"
if not eula.exists():
    accepted = template / "eula.txt"
    if not accepted.exists() or "eula=true" not in accepted.read_text(encoding="utf-8").lower().splitlines():
        raise SystemExit("The fixture needs the owner's existing EULA acceptance")
    shutil.copy2(accepted, eula)
if "eula=true" not in eula.read_text(encoding="utf-8").lower().splitlines():
    raise SystemExit("The fixture's existing EULA acceptance is required")

argfile = Path("libraries/net/minecraftforge/forge/1.20.1-47.4.20") / ("win_args.txt" if os.name == "nt" else "unix_args.txt")
if not (runtime / argfile).is_file():
    if (runtime / "libraries").exists() or not (template / argfile).is_file():
        raise SystemExit("The fixture needs a complete Forge 47.4.20 dependency tree")
    def link_or_copy(source, target):
        try:
            os.link(source, target)
        except OSError:
            shutil.copy2(source, target)
    # Dependencies are read-only inputs. No worlds/configs/mods are copied from the template.
    shutil.copytree(template / "libraries", runtime / "libraries", copy_function=link_or_copy)

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

inputs = [p.resolve() for p in (args.jar, args.probe, args.ssn, args.flib, args.progressive, args.opac) if p]
if any(not p.is_file() for p in inputs):
    raise SystemExit("A requested fixture JAR is missing")
expected = {p.name: p for p in inputs}
if len(expected)!=len(inputs):
    raise SystemExit("Fixture inputs must have distinct JAR filenames")
mods = runtime / "mods"
mods.mkdir(exist_ok=True)
if mods.resolve()!=runtime / "mods":
    raise SystemExit("The owned fixture mods directory must not redirect elsewhere")
manifest = runtime / "fixture-managed-mods.json"
previous = json.loads(manifest.read_text(encoding="utf-8")) if manifest.exists() else {}
if any(p.name not in expected and p.name not in previous for p in mods.glob("*.jar")):
    raise SystemExit("Unexpected JAR in the owned SSN fixture; inspect it first")
for name, checksum in previous.items():
    if name in expected:
        continue
    target = mods / name
    if target.exists():
        if target.resolve().parent != mods.resolve() or target.is_symlink() or digest(target) != checksum:
            raise SystemExit("Managed fixture JAR changed; refusing to remove " + name)
        target.unlink()  # Exact previously recorded, verified file inside this fixture; never recursive.
for name, source in expected.items():
    target = mods / name
    if target.is_symlink() or target.resolve().parent != mods.resolve():
        raise SystemExit("Fixture JAR target escapes the mods directory")
    if source != target.resolve():
        shutil.copy2(source, target)
manifest.write_text(json.dumps({n: digest(mods / n) for n in expected}, indent=2) + "\n", encoding="utf-8")
(runtime / "server.properties").write_text("""server-ip=127.0.0.1
server-port=25590
online-mode=false
max-players=1
level-name=cue-ssn-production
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1}],"biome":"minecraft:plains"}
view-distance=2
simulation-distance=2
spawn-protection=0
spawn-monsters=false
spawn-animals=false
enable-rcon=false
""", encoding="utf-8", newline="\n")
log = ROOT / ".codex-tmp/ssn-production.log"
with log.open("w", encoding="utf-8") as output:
    process = subprocess.Popen([args.java, "-Xms512m", "-Xmx2g", "-Dunusualcopper.ssnSmoke=true", "@" + str(argfile), "--nogui"], cwd=runtime, stdin=subprocess.DEVNULL, stdout=output, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 180
    while process.poll() is None and time.monotonic() < deadline:
        time.sleep(.5)
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
        raise SystemExit("Owned SSN fixture timed out: " + str(log))
text = log.read_text(encoding="utf-8", errors="replace")
required = ["CONNECTED", "EXACT PASS", "SIDED INDEX PASS", "FAR INSERT PASS", "FILTER PASS", "DOUBLE CHEST PASS", "STOCK PASS"]
missing = [marker for marker in required if "CuE SSN BRIDGE " + marker not in text]
profile = "CuE SSN PROGRESSIVE PROFILE PASS" if args.progressive else "CuE SSN VANILLA PROFILE PASS"
if profile not in text:
    missing.append(profile)
if "CuE SSN DISK CHECKPOINT" not in text:
    missing.append("CuE SSN DISK CHECKPOINT")
if args.opac and "CuE SSN CLAIMS PASS" not in text:
    missing.append("CuE SSN CLAIMS PASS")
if process.returncode or missing or "Encountered an unexpected exception" in text:
    print("\n".join(text.splitlines()[-40:]))
    raise SystemExit("SSN fixture failed; Java exit " + str(process.returncode) + "; missing " + repr(missing))
for line in text.splitlines():
    if "CuE SSN " in line:
        print(line)
print("Native SSN fixture stopped cleanly; raw log:", log)
