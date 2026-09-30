#!/usr/bin/env python3
"""Generate the updater contract from the APK's actual packaged version."""
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys


def generate(tag, directory):
    if not re.fullmatch(r"v[0-9A-Za-z.\-]+", tag):
        raise ValueError("Invalid release tag")
    apk = directory / f"chrono-flow-{tag}.apk"
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    aapt = sdk / "build-tools" / "35.0.0" / "aapt"
    badging = subprocess.check_output([str(aapt), "dump", "badging", str(apk)], text=True)
    package = re.search(r"package: name='([^']+)' versionCode='([0-9]+)' versionName='([^']+)'", badging)
    if not package or package[1] != "dev.chronoflow" or "v" + package[3] != tag:
        raise ValueError("APK identity does not match the release")
    metadata = {
        "versionCode": int(package[2]),
        "versionName": package[3],
        "url": f"https://github.com/nagarete/chrono-flow/releases/download/{tag}/{apk.name}",
        "sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "size": apk.stat().st_size,
    }
    (directory / "update.json").write_text(json.dumps(metadata, indent=2) + "\n")


if __name__ == "__main__":
    generate(sys.argv[1], Path(sys.argv[2]))
