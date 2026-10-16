"""Install a verified CuE ordinary JAR into an offline client/server pair.

Existing versions require --replace-existing and are backed up with SHA checks.
Files are staged before either installation changes. World saves are not touched.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import zipfile


def sha(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jar", type=Path, required=True)
    parser.add_argument("--client-mods", type=Path, required=True)
    parser.add_argument("--server-mods", type=Path, required=True)
    parser.add_argument("--apply", action="store_true", help="Explicitly write to both mods folders")
    parser.add_argument("--replace-existing",action="store_true",help="Back up and replace existing CuE JARs")
    args = parser.parse_args()
    source = args.jar.resolve(strict=True)
    targets = [args.client_mods.resolve(strict=True), args.server_mods.resolve(strict=True)]
    if targets[0] == targets[1] or any(not p.is_dir() for p in targets):
        parser.error("Distinct existing mods directories required")
    if not source.name.startswith("unusualcopper-") or "probe" in source.name:
        parser.error("Not an ordinary Unusual Copper release JAR")
    with zipfile.ZipFile(source) as archive:
        if "META-INF/mods.toml" not in archive.namelist() or b'modId="unusualcopper"' not in archive.read("META-INF/mods.toml"):
            parser.error("Missing expected Forge mod identity")
        if any("/probe/" in name for name in archive.namelist()):
            parser.error("Benchmark probe was packaged in the main mod")
    previous=[]
    for p in targets:
        old=list(p.glob("unusualcopper*.jar"))
        if old and not args.replace_existing:
            parser.error(f"Existing CuE version found in {p}; refusing silent overwrite")
        for jar in old:
            if jar.resolve().parent!=p:parser.error("Existing JAR escaped its target directory")
            with zipfile.ZipFile(jar) as archive:
                if b'modId="unusualcopper"' not in archive.read("META-INF/mods.toml"):
                    parser.error(f"Unexpected mod identity: {jar}")
            previous.append(jar)
    client_names = {p.name.lower() for p in targets[0].glob("*.jar")}
    server_names = {p.name.lower() for p in targets[1].glob("*.jar")}
    overlap = len(client_names & server_names)
    if overlap < 250:
        parser.error(f"Client/server modpack overlap too small: {overlap}")
    if os.name != 'nt':
        parser.error("This local client/server installer requires Windows process checks")
    check=subprocess.run(['powershell.exe','-NoProfile','-NonInteractive','-Command',
        'Get-CimInstance Win32_Process -Filter "Name = \'java.exe\' OR Name = \'javaw.exe\'" | Select-Object ProcessId,Name | ConvertTo-Json -Compress'],
        check=True,capture_output=True,text=True)
    processes=json.loads(check.stdout) if check.stdout.strip() else []
    if processes:
        parser.error(f"Java game/server process still running: {processes}")
    expected = sha(source)
    if not args.apply:
        print("DRY RUN", source, expected, targets, "shared mod jars:", overlap)
        return
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    staged, installed, backed_up = [], [], []
    backup_root=Path(__file__).resolve().parents[1]/'.codex-tmp'/'installs'/stamp
    try:
        for mods in targets:
            tmp = mods / (".cue-install-" + stamp + ".pending")
            if tmp.exists():
                raise RuntimeError(f"Refusing occupied staging path {tmp}")
            shutil.copy2(source, tmp)
            staged.append(tmp)
            if sha(tmp) != expected:
                raise RuntimeError(f"Staging checksum mismatch: {tmp}")
        for tmp, mods in zip(staged, targets):
            for old in [p for p in previous if p.parent==mods]:
                backup=backup_root/('client' if mods==targets[0] else 'server')/old.name
                backup.parent.mkdir(parents=True,exist_ok=True)
                shutil.copy2(old,backup)
                if sha(backup)!=sha(old):raise RuntimeError(f"Backup mismatch: {old}")
                backed_up.append((old,backup,sha(backup)))
                old.unlink()
            final = mods / source.name
            os.replace(tmp, final)
            installed.append(final)
            if sha(final) != expected:
                raise RuntimeError(f"Installed checksum mismatch: {final}")
        receipt = {
            "timestamp_utc": stamp, "source": str(source),
            "sha256": expected, "jar_bytes": source.stat().st_size,
            "shared_mod_jars_before_install": overlap,
            "installed": [str(p) for p in installed],
            "previous_jars": [{"original":str(p),"backup":str(b),"sha256":h} for p,b,h in backed_up],
            "world_saves": "Not modified; no world backup or game launch performed by this installer",
        }
        receipt_path = Path(__file__).resolve().parents[1] / ".codex-tmp" / ("cue-install-" + stamp + ".json")
        receipt_path.parent.mkdir(exist_ok=True)
        receipt_path.write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
        print("INSTALLED AND VERIFIED", json.dumps(receipt, ensure_ascii=False))
        print("RECEIPT", receipt_path)
    except Exception:
        for p in installed:
            p.unlink(missing_ok=True)
        for old,backup,expected_old in backed_up:
            shutil.copy2(backup,old)
            if sha(old)!=expected_old:raise RuntimeError(f"Rollback checksum mismatch: {old}")
        raise
    finally:
        for p in staged:
            p.unlink(missing_ok=True)


if __name__ == "__main__":
    main()
