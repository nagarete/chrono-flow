#!/usr/bin/env python3
"""Bump the app version, commit release notes, and create the release tag."""
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
BUILD_FILE = ROOT / "app/build.gradle"


def git(*args, check=True):
    return subprocess.run(
        ["git", *args], cwd=ROOT, check=check, text=True,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE,
    )


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in {"patch", "minor", "major"}:
        raise SystemExit("Usage: scripts/prepare-release.py patch|minor|major")

    status = git("status", "--porcelain").stdout.splitlines()
    allowed = {" M docs/RELEASE_NOTES.md", "?? docs/RELEASE_NOTES.md"}
    unexpected = [line for line in status if line not in allowed]
    if unexpected:
        raise SystemExit("Commit or discard other changes before preparing a release.")
    if git("diff", "--cached", "--quiet", check=False).returncode != 0:
        raise SystemExit("Commit or unstage staged changes before preparing a release.")

    source = BUILD_FILE.read_text()
    name_match = re.search(r"(?m)^\s*versionName '([0-9]+)\.([0-9]+)\.([0-9]+)'\s*$", source)
    code_match = re.search(r"(?m)^\s*versionCode ([0-9]+)\s*$", source)
    if not name_match or not code_match:
        raise SystemExit("Expected numeric versionName and versionCode in app/build.gradle.")

    major, minor, patch = map(int, name_match.groups())
    bump = sys.argv[1]
    if bump == "major":
        major, minor, patch = major + 1, 0, 0
    elif bump == "minor":
        major, minor, patch = major, minor + 1, 0
    else:
        patch += 1
    version = f"{major}.{minor}.{patch}"
    tag = f"v{version}"
    if git("rev-parse", "--verify", f"refs/tags/{tag}", check=False).returncode == 0:
        raise SystemExit(f"Tag {tag} already exists.")
    branch = git("branch", "--show-current").stdout.strip()
    if not branch:
        raise SystemExit("Check out a branch before preparing a release.")

    updated = source[:name_match.start(1)] + version + source[name_match.end(3):]
    updated = updated[:code_match.start(1)] + str(int(code_match.group(1)) + 1) + updated[code_match.end(1):]
    BUILD_FILE.write_text(updated)

    try:
        git("add", "app/build.gradle", "docs/RELEASE_NOTES.md")
        git("commit", "-m", f"Release {tag}")
        git("tag", "-a", tag, "-m", f"chrono-flow {tag}")
    except subprocess.CalledProcessError as error:
        sys.stderr.write(error.stderr or "Release commit or tag creation failed.\n")
        raise SystemExit(error.returncode)

    print(f"Prepared {tag}. Review it, then publish with: git push origin {branch} {tag}")


if __name__ == "__main__":
    main()
