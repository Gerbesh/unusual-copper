"""Isolated, disposable smoke boot for the installed Better MC Forge server plus CuE.

Never points Forge at the actual game world. It copies mod/config/library inputs
into a timestamped test directory and starts a loopback-only server there.
"""
from datetime import datetime, timezone
from pathlib import Path
import hashlib
import json
import os
import shutil
import socket
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SERVER = Path("D:/Pash_Ver_minecraft")
CLIENT_MODS = Path("C:/Users/gerbe/curseforge/minecraft/Instances/Better MC [FORGE] BMC4/mods")
SOURCE_MOD = SERVER / "mods" / "unusualcopper-0.13.0-alpha.1.jar"
CLIENT_MOD = CLIENT_MODS / SOURCE_MOD.name
ARGFILE = Path("libraries/net/minecraftforge/forge/1.20.1-47.4.20/win_args.txt")

def digest(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
    if not SOURCE_MOD.exists() or not CLIENT_MOD.exists() or digest(SOURCE_MOD) != digest(CLIENT_MOD):
        raise RuntimeError("CuE must be identically installed on server and client")
    if (SERVER / "eula.txt").read_text(encoding="utf-8").strip() != "eula=true":
        raise RuntimeError("Existing server EULA is not accepted")
    with socket.socket() as sock:
        if sock.connect_ex(("127.0.0.1", 25588)) == 0:
            raise RuntimeError("Loopback test port 25588 occupied")
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    run = ROOT / ".codex-tmp" / ("modpack-smoke-" + stamp)
    run.mkdir(parents=True, exist_ok=False)
    for name in ("mods", "config", "defaultconfigs", "configureddefaults",
                 "libraries", "datapacks", "moonlight-global-datapacks", "patchouli_books"):
        src = SERVER / name
        if src.is_dir():
            shutil.copytree(src, run / name)
    shutil.copy2(SERVER / "eula.txt", run / "eula.txt")
    (run / "server.properties").write_text(
        "server-ip=127.0.0.1\nserver-port=25588\nonline-mode=false\n"
        "enable-rcon=false\nenable-query=false\nmax-players=1\n"
        "level-name=cue-modpack-smoke-world\nlevel-type=minecraft:normal\n"
        "view-distance=2\nsimulation-distance=2\n"
        "spawn-animals=false\nspawn-monsters=false\nspawn-npcs=false\n"
        "generate-structures=false\nallow-nether=false\n"
        "enable-command-block=false\n", encoding="utf-8")
    if not (run / ARGFILE).exists():
        raise RuntimeError("Forge server installation not copied into smoke runtime")
    (run / "smoke.json").write_text(json.dumps({
        "source_server": str(SERVER), "world": str(run / "cue-modpack-smoke-world"),
        "port": 25588, "jar_sha256": digest(SOURCE_MOD),
        "mods": len(list((run / "mods").glob("*.jar"))),
        "start_utc": stamp,
        "note": "disposable runtime, no access to main save",
    }, indent=2), encoding="utf-8")
    log = (run / "server.log").open("w", encoding="utf-8")
    java = shutil.which("java")
    if java is None:
        raise RuntimeError("Java not on PATH")
    args = [java, "-Xms1G", "-Xmx8G", "@" + ARGFILE.as_posix(), "--nogui"]
    proc = subprocess.Popen(args, cwd=run, stdin=subprocess.DEVNULL,
                            stdout=log, stderr=subprocess.STDOUT,
                            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0)
    log.close()
    (run / "process.json").write_text(json.dumps({
        "pid": proc.pid, "command": args, "root": str(run),
    }, indent=2), encoding="utf-8")
    print("SMOKE_STARTED", run, "PID", proc.pid, "MOD_JARS",len(list((run/"mods").glob("*.jar"))),flush=True)

if __name__ == "__main__":
    main()
