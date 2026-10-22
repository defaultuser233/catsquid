#!/usr/bin/env python3
"""version.properties の versionCode と versionName(パッチ) を 1 つ進める"""
import re
from pathlib import Path

path = Path(__file__).resolve().parent.parent / "version.properties"
text = path.read_text(encoding="utf-8")

code_match = re.search(r"^versionCode=(\d+)\s*$", text, re.MULTILINE)
name_match = re.search(r"^versionName=(\d+)\.(\d+)\.(\d+)\s*$", text, re.MULTILINE)
if not code_match or not name_match:
    raise SystemExit("version.properties must contain versionCode=N and versionName=X.Y.Z")

code = int(code_match.group(1))
major, minor, patch = (int(v) for v in name_match.groups())
new_code = code + 1
new_name = f"{major}.{minor}.{patch + 1}"

path.write_text(f"versionCode={new_code}\nversionName={new_name}\n", encoding="utf-8")
print(f"version {code}/{major}.{minor}.{patch} -> {new_code}/{new_name}")
