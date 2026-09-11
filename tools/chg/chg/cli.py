"""`chg` -- the change CLI.

Five commands, one vocabulary. A command exists only when it is deterministic
and you would otherwise have to describe it in words in every prompt.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import commands, gitctx
from .lint import Severity, find_change_dir, lint
from .model import Artifact, ChangeType, artifact_path

COLOURS = {Severity.ERROR: "\033[31m", Severity.WARN: "\033[33m", Severity.OK: "\033[32m", Severity.SKIP: "\033[90m"}
RESET = "\033[0m"


def _root() -> Path:
    try:
        return gitctx.repo_root()
    except Exception:  # noqa: BLE001
        return Path.cwd()


def _print(report, strict: bool) -> int:
    for finding in report.findings:
        colour = COLOURS.get(finding.severity, "") if sys.stdout.isatty() else ""
        reset = RESET if colour else ""
        print(f"{colour}{finding.severity.value:5}{reset} {finding.rule:4} {finding.message}")
    failed = report.failed(strict)
    print("\nFAILED" if failed else "\nOK")
    return 1 if failed else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="chg", description="Change delivery CLI")
    sub = parser.add_subparsers(dest="command", required=True)

    p_new = sub.add_parser("new", help="create issue, scaffold change folder, create branch")
    p_new.add_argument("--type", required=True, choices=[t.value for t in ChangeType])
    p_new.add_argument("--title", required=True)
    p_new.add_argument("--tracker", default="GH")
    p_new.add_argument("--ref", help="use an existing workItemRef instead of creating an issue")
    p_new.add_argument("--no-branch", action="store_true")

    p_approve = sub.add_parser("approve", help="stamp a human gate")
    p_approve.add_argument("ref")
    p_approve.add_argument("artifact", choices=[a.value for a in Artifact])
    p_approve.add_argument("--by")

    p_lint = sub.add_parser("lint", help="validate a change against the convention")
    p_lint.add_argument("ref", nargs="?")
    p_lint.add_argument("--strict", action="store_true", help="warnings become errors (use in CI)")

    p_status = sub.add_parser("status", help="show artifacts and their state")
    p_status.add_argument("ref")

    p_evidence = sub.add_parser("evidence", help="generate evidence from CI and build output")
    p_evidence.add_argument("ref")

    sub.add_parser("sync-agents", help="generate .github/agents and .claude/agents from the canonical personas")

    args = parser.parse_args(argv)
    root = _root()

    if args.command == "new":
        directory = commands.new_change(
            root, ChangeType(args.type), args.title, tracker=args.tracker,
            ref=args.ref, create_branch=not args.no_branch,
        )
        print(f"created {directory.relative_to(root)}")
        return 0

    if args.command == "approve":
        path = commands.approve(root, args.ref, Artifact(args.artifact), who=args.by)
        print(f"approved {path.relative_to(root)}")
        return 0

    if args.command == "lint":
        ref = args.ref or _ref_from_branch()
        if ref is None:
            print("cannot infer workItemRef from the current branch; pass it explicitly", file=sys.stderr)
            return 2
        return _print(lint(ref, root, strict=args.strict), args.strict)

    if args.command == "status":
        directory = find_change_dir(args.ref, root)
        if directory is None:
            print(f"no change folder for {args.ref}", file=sys.stderr)
            return 2
        from . import fm

        print(f"{args.ref}  {directory.relative_to(root)}")
        for artifact in Artifact:
            path = artifact_path(directory, artifact)
            if not path.exists():
                continue
            meta, _ = fm.read(path)
            print(f"  {artifact.value:15} {meta.status.value:9} v{meta.version}  {meta.approved_by or ''}")
        return 0

    if args.command == "evidence":
        path = commands.collect_evidence(root, args.ref)
        print(f"wrote {path.relative_to(root)}")
        return 0

    if args.command == "sync-agents":
        for path in commands.sync_agents(root):
            print(f"wrote {path.relative_to(root)}")
        return 0

    return 2


def _ref_from_branch() -> str | None:
    from .model import parse_branch

    try:
        parsed = parse_branch(gitctx.current_branch())
    except Exception:  # noqa: BLE001
        return None
    return parsed[1] if parsed else None


if __name__ == "__main__":
    raise SystemExit(main())
