"""Seven rules that turn the delivery convention into a failing build.

A prompt is a request. A schema is a contract. Everything an agent could
quietly get wrong about the shape of a change is checked here instead of
being re-explained in every prompt.

Severity:
  ERROR -- always fails
  WARN  -- reported, fails only with --strict
  SKIP  -- not checkable in this context (e.g. no git history)
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path

from . import fm, gitctx
from .model import (
    CHANGES_ROOT,
    GATED,
    OWNER,
    PRODUCTION_PATHSPEC,
    REQUIRED,
    Artifact,
    ChangeType,
    Frontmatter,
    Status,
    artifact_path,
    needs_version_bump,
    parse_branch,
)


class Severity(str, Enum):
    ERROR = "ERROR"
    WARN = "WARN"
    SKIP = "SKIP"
    OK = "OK"


@dataclass
class Finding:
    rule: str
    severity: Severity
    message: str


@dataclass
class Report:
    findings: list[Finding] = field(default_factory=list)

    def add(self, rule: str, severity: Severity, message: str) -> None:
        self.findings.append(Finding(rule, severity, message))

    def failed(self, strict: bool) -> bool:
        bad = {Severity.ERROR} | ({Severity.WARN} if strict else set())
        return any(f.severity in bad for f in self.findings)


def find_change_dir(ref: str, root: Path) -> Path | None:
    base = root / CHANGES_ROOT
    if not base.is_dir():
        return None
    matches = sorted(p for p in base.iterdir() if p.is_dir() and p.name.startswith(f"{ref}-"))
    return matches[0] if matches else None


def _load(path: Path) -> tuple[Frontmatter | None, str | None]:
    try:
        meta, _ = fm.read(path)
        return meta, None
    except Exception as exc:  # noqa: BLE001 - surfaced as a lint finding
        return None, str(exc)


def lint(ref: str, root: Path, strict: bool = False, branch: str | None = None) -> Report:
    report = Report()
    branch = branch or _safe_branch()
    directory = find_change_dir(ref, root)

    # --- R1: branch and change folder agree -------------------------------
    parsed = parse_branch(branch) if branch else None
    if directory is None:
        report.add("R1", Severity.ERROR, f"no change folder under {CHANGES_ROOT}/ for {ref}")
        return report
    if parsed is None:
        report.add("R1", Severity.WARN, f"branch {branch!r} does not follow <type>/<workItemRef>-<slug>")
        change_type = _type_from_folder(directory)
    else:
        btype, bref, bslug = parsed
        change_type = btype
        expected = f"{bref}-{bslug}"
        if bref != ref:
            report.add("R1", Severity.ERROR, f"branch is for {bref}, linting {ref}")
        elif directory.name != expected:
            report.add("R1", Severity.ERROR, f"folder {directory.name} does not match branch slug {expected}")
        else:
            report.add("R1", Severity.OK, f"branch and folder agree ({expected})")

    if change_type is None:
        report.add("R1", Severity.ERROR, "cannot determine change type from branch or artifacts")
        return report

    # --- R3: frontmatter matches the schema -------------------------------
    metas: dict[Artifact, Frontmatter] = {}
    for artifact in Artifact:
        path = artifact_path(directory, artifact)
        if not path.exists():
            continue
        meta, error = _load(path)
        if error or meta is None:
            report.add("R3", Severity.ERROR, f"{path.name}: {error}")
            continue
        metas[artifact] = meta
        if meta.artifact is not artifact:
            report.add("R3", Severity.ERROR, f"{path.name}: declares artifact={meta.artifact.value}")
        owner = OWNER[artifact]
        if owner is not None and meta.persona is not None and meta.persona is not owner:
            report.add("R3", Severity.WARN, f"{path.name}: persona={meta.persona.value}, expected {owner.value}")
    if not any(f.rule == "R3" and f.severity is not Severity.OK for f in report.findings):
        report.add("R3", Severity.OK, f"{len(metas)} artifact header(s) valid")

    # --- R2: the artifact set required by this type ------------------------
    required = REQUIRED[change_type]
    missing = [a.value for a in required if a not in metas]
    if missing:
        report.add("R2", Severity.WARN if not strict else Severity.ERROR,
                   f"{change_type.value} requires: {', '.join(missing)}")
    else:
        report.add("R2", Severity.OK, f"artifact set complete for {change_type.value}")

    # --- R4: one workItemRef everywhere ------------------------------------
    wrong = [a.value for a, m in metas.items() if m.workItemRef != ref]
    wrong_type = [a.value for a, m in metas.items() if m.type is not change_type]
    if wrong:
        report.add("R4", Severity.ERROR, f"workItemRef mismatch in: {', '.join(wrong)}")
    if wrong_type:
        report.add("R4", Severity.ERROR, f"type mismatch (expected {change_type.value}) in: {', '.join(wrong_type)}")
    if not wrong and not wrong_type:
        report.add("R4", Severity.OK, f"{ref} consistent across artifacts and branch")

    # --- R5: the test contract predates the code ---------------------------
    _rule_test_before_code(report, root, directory, change_type)

    # --- R6: evidence is machine-generated ---------------------------------
    _rule_evidence(report, directory, metas, change_type, strict)

    # --- R7: approved artifacts do not drift silently -----------------------
    _rule_no_silent_drift(report, root, directory, metas)

    # --- gates --------------------------------------------------------------
    not_approved = [a.value for a in GATED[change_type] if a in metas and metas[a].status is not Status.APPROVED]
    if not_approved:
        report.add("GATE", Severity.WARN if not strict else Severity.ERROR,
                   f"awaiting approval: {', '.join(not_approved)}")
    else:
        report.add("GATE", Severity.OK, "all gated artifacts approved")

    return report


def _rel(root: Path, path: Path) -> str:
    """Git pathspecs are repo-relative; lint may be called with an absolute root."""
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def _safe_branch() -> str | None:
    try:
        return gitctx.current_branch()
    except Exception:  # noqa: BLE001
        return None


def _type_from_folder(directory: Path) -> ChangeType | None:
    for artifact in Artifact:
        path = artifact_path(directory, artifact)
        if path.exists():
            meta, _ = _load(path)
            if meta is not None:
                return meta.type
    return None


def _rule_test_before_code(report: Report, root: Path, directory: Path, change_type: ChangeType) -> None:
    if change_type in (ChangeType.CHORE, ChangeType.SPIKE):
        report.add("R5", Severity.SKIP, f"not applicable to {change_type.value}")
        return
    test_plan = artifact_path(directory, Artifact.TEST_PLAN)
    if not test_plan.exists():
        report.add("R5", Severity.WARN, "no test-plan yet")
        return
    try:
        plan_ts = gitctx.first_commit_timestamp(_rel(root, test_plan))
        code_ts = gitctx.first_commit_timestamp(PRODUCTION_PATHSPEC)
    except Exception:  # noqa: BLE001
        report.add("R5", Severity.SKIP, "git history unavailable")
        return
    if plan_ts is None:
        report.add("R5", Severity.WARN, "test-plan is not committed yet")
    elif code_ts is None:
        report.add("R5", Severity.OK, "no production code touched yet")
    elif plan_ts > code_ts:
        report.add("R5", Severity.ERROR,
                   "test-plan was committed after production code -- tests derived from the code, not the requirement")
    else:
        report.add("R5", Severity.OK, "test contract predates the code")


def _rule_evidence(report: Report, directory: Path, metas: dict[Artifact, Frontmatter],
                   change_type: ChangeType, strict: bool) -> None:
    if Artifact.EVIDENCE not in REQUIRED[change_type]:
        report.add("R6", Severity.SKIP, f"not required for {change_type.value}")
        return
    meta = metas.get(Artifact.EVIDENCE)
    if meta is None:
        report.add("R6", Severity.WARN if not strict else Severity.ERROR,
                   "evidence/evidence.md missing -- run `chg evidence`")
        return
    missing = [name for name in ("ci_run",) if getattr(meta.links, name) is None]
    body = artifact_path(directory, Artifact.EVIDENCE).read_text(encoding="utf-8")
    if "commit:" not in body:
        missing.append("commit sha")
    if missing:
        report.add("R6", Severity.ERROR, f"evidence incomplete: {', '.join(missing)}")
    else:
        report.add("R6", Severity.OK, "evidence links to a CI run and a commit")


def _rule_no_silent_drift(report: Report, root: Path, directory: Path, metas: dict[Artifact, Frontmatter]) -> None:
    try:
        changed = set(gitctx.changed_in_head())
    except Exception:  # noqa: BLE001
        report.add("R7", Severity.SKIP, "no parent commit to compare against")
        return
    offenders: list[str] = []
    for artifact, meta in metas.items():
        rel = _rel(root, artifact_path(directory, artifact))
        if rel not in changed:
            continue
        previous = gitctx.file_at_revision("HEAD~1", rel)
        if previous is None:
            continue
        try:
            old, _ = fm.parse(previous)
        except Exception:  # noqa: BLE001
            continue
        if needs_version_bump(old.status, old.version, meta.status, meta.version):
            offenders.append(artifact.value)
    if offenders:
        report.add("R7", Severity.ERROR,
                   f"approved artifact changed without a version bump: {', '.join(offenders)}")
    else:
        report.add("R7", Severity.OK, "no silent drift in approved artifacts")
