"""Reject local captures, personal filesystem paths, and common credential formats in tracked files."""

from pathlib import Path
import re
import subprocess
import sys

root = Path(__file__).resolve().parents[2]
names = subprocess.check_output(["git", "ls-files", "-z"], cwd=root).decode().split("\0")
blocked_directories = {".setup", ".gradle", "build", "run", "private", "saves", "logs"}
checks = {
    "personal filesystem path": re.compile(r"(?i)\b[a-z]:[\\/]+Users[\\/]+|/[U]sers/|/[h]ome/"),
    "credential": re.compile(
        r"gh[pousr]_[A-Za-z0-9]{20,255}|github_pat_[A-Za-z0-9_]{30,255}"
        r"|AKIA[A-Z0-9]{16}|-----BEGIN (?:RSA |OPENSSH |EC )?PRIVATE KEY-----"
    ),
}
failures = []
for name in filter(None, names):
    path = Path(name)
    if any(part in blocked_directories for part in path.parts) or name.startswith("docs/qualification/"):
        failures.append(f"{name}: local-only directory")
    if path.name.startswith(".env") or path.name in {"accounts.json", "servers.dat", "usercache.json"}:
        failures.append(f"{name}: private configuration")
    if name.startswith("docs/") and ("plan" in path.stem.lower() or path.name.startswith("code-size-")):
        failures.append(f"{name}: internal planning document")
    try:
        content = (root / name).read_text(encoding="utf-8-sig")
    except UnicodeError:
        continue
    for number, line in enumerate(content.splitlines(), 1):
        for label, pattern in checks.items():
            if pattern.search(line):
                # Report locations, never credential contents.
                failures.append(f"{name}:{number}: {label}")

if failures:
    print("\n".join(failures), file=sys.stderr)
    sys.exit(1)
print("PASS public tree checks (tracked files only; no universal secret-detection guarantee)")
