#!/usr/bin/env python3
"""Report non-ASCII bytes in files edited through Codex apply_patch."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
PATCH_PATH = re.compile(r"^\*\*\* (?:Add File|Update File|Move to): (.+)$", re.MULTILINE)


def edited_paths(payload: dict[str, Any], root: Path) -> list[Path]:
    """Resolve patch and legacy edit paths within this repository."""
    tool_input = payload.get("tool_input") or {}
    if not isinstance(tool_input, dict):
        return []
    raw_paths = []
    raw_path = tool_input.get("file_path")
    if isinstance(raw_path, str):
        raw_paths.append(raw_path)
    command = tool_input.get("command")
    if isinstance(command, str):
        raw_paths.extend(PATCH_PATH.findall(command))
    cwd = Path(payload.get("cwd") or root).resolve()
    paths = []
    for name in raw_paths:
        path = (cwd / name).resolve()
        if path.is_relative_to(root) and path.is_file() and path not in paths:
            paths.append(path)
    return paths


def violations(paths: list[Path], root: Path, checks: dict[str, str]) -> list[str]:
    """Apply the same file and exemption patterns as the pre-commit ASCII gate."""
    problems = []
    for path in paths:
        relative = path.relative_to(root).as_posix()
        if not re.search(checks["files"], relative):
            continue
        if re.search(checks["exclude"], relative):
            continue
        if any(byte > 127 for byte in path.read_bytes()):
            problems.append(relative)
    return problems


def main() -> int:
    try:
        payload = json.load(sys.stdin)
    except ValueError:
        return 0
    if not isinstance(payload, dict):
        return 0
    checks = json.loads((ROOT / ".codex/hooks/checks.json").read_text())
    problems = violations(edited_paths(payload, ROOT), ROOT, checks)
    if not problems:
        return 0
    print(
        "Non-ASCII bytes in " + ", ".join(problems) + ". Fix before committing.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
