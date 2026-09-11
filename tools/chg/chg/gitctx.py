"""Thin wrappers over `git` and `gh`.

Everything deterministic lives here so that no agent ever has to be told
how to name a branch or where to find a CI run.
"""

from __future__ import annotations

import json
import subprocess
from pathlib import Path


class CommandError(RuntimeError):
    pass


def run(*args: str, check: bool = True, cwd: Path | None = None) -> str:
    try:
        proc = subprocess.run(args, capture_output=True, text=True, cwd=cwd)
    except FileNotFoundError as exc:
        raise CommandError(f"{args[0]} is not installed") from exc
    if check and proc.returncode != 0:
        raise CommandError(f"{' '.join(args)} failed: {proc.stderr.strip()}")
    return proc.stdout.strip()


def repo_root() -> Path:
    return Path(run("git", "rev-parse", "--show-toplevel"))


def current_branch() -> str:
    return run("git", "rev-parse", "--abbrev-ref", "HEAD")


def head_sha() -> str:
    return run("git", "rev-parse", "HEAD")


def first_commit_timestamp(pathspec: str) -> int | None:
    """Unix timestamp of the earliest commit touching a pathspec, or None."""
    out = run("git", "log", "--reverse", "--format=%ct", "--", pathspec, check=False)
    lines = [line for line in out.splitlines() if line.strip()]
    return int(lines[0]) if lines else None


def file_at_revision(revision: str, path: str) -> str | None:
    proc = subprocess.run(["git", "show", f"{revision}:{path}"], capture_output=True, text=True)
    return proc.stdout if proc.returncode == 0 else None


def changed_in_head() -> list[str]:
    out = run("git", "diff", "--name-only", "HEAD~1", "HEAD", check=False)
    return [line for line in out.splitlines() if line.strip()]


def gh_available() -> bool:
    try:
        return subprocess.run(["gh", "--version"], capture_output=True).returncode == 0
    except FileNotFoundError:
        return False


def gh_json(*args: str) -> dict | list:
    return json.loads(run("gh", *args))


def create_issue(title: str, body: str, labels: list[str]) -> str:
    """Create an issue and return its URL."""
    cmd = ["gh", "issue", "create", "--title", title, "--body", body]
    for label in labels:
        cmd += ["--label", label]
    return run(*cmd)


def issue_number_from_url(url: str) -> int:
    return int(url.rstrip("/").rsplit("/", 1)[-1])


def latest_run_for_branch(branch: str) -> dict | None:
    try:
        runs = gh_json(
            "run", "list", "--branch", branch, "--limit", "1",
            "--json", "databaseId,conclusion,status,url,headSha,workflowName",
        )
    except CommandError:
        return None
    return runs[0] if runs else None


def gh_login() -> str | None:
    if not gh_available():
        return None
    try:
        data = gh_json("api", "user")
    except CommandError:
        return None
    return data.get("login") if isinstance(data, dict) else None
