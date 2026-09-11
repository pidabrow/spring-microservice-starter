"""Deterministic operations. No command here asks an agent for anything."""

from __future__ import annotations

import datetime as dt
import shutil
from pathlib import Path

from . import fm, gitctx
from .lint import find_change_dir
from .model import (
    FIRST_ARTIFACT,
    GATED,
    OWNER,
    REQUIRED,
    Artifact,
    ChangeType,
    Frontmatter,
    Links,
    Status,
    artifact_path,
    branch_name,
    change_dir,
    slugify,
)

TEMPLATES = Path("docs/delivery/templates")
AGENTS_SRC = Path("docs/delivery/agents")


def _now() -> str:
    return dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat()


def new_change(root: Path, change_type: ChangeType, title: str, tracker: str = "GH",
               ref: str | None = None, create_branch: bool = True) -> Path:
    """Create issue -> derive ref -> scaffold folder -> create branch.

    The issue is the *output* of refinement, not its input: run this once the
    title and scope are already sharpened.
    """
    issue_url = None
    if ref is None:
        if not gitctx.gh_available():
            raise RuntimeError("gh CLI not available; pass --ref to work offline")
        issue_url = gitctx.create_issue(title=title, body=_issue_body(change_type, title), labels=[change_type.value])
        ref = f"{tracker}-{gitctx.issue_number_from_url(issue_url)}"

    slug = slugify(title)
    directory = root / change_dir(ref, slug)
    if directory.exists():
        raise RuntimeError(f"{directory} already exists")
    (directory / "evidence").mkdir(parents=True)

    branch = branch_name(change_type, ref, slug)
    planned = [a for a in REQUIRED[change_type] if a is not Artifact.EVIDENCE]
    first = FIRST_ARTIFACT[change_type]
    for artifact in planned:
        _scaffold(root, directory, artifact, change_type, ref, branch, issue_url)
    if first is not None and first not in planned:
        _scaffold(root, directory, first, change_type, ref, branch, issue_url)

    if create_branch:
        gitctx.run("git", "checkout", "-b", branch)
    return directory


def _issue_body(change_type: ChangeType, title: str) -> str:
    gates = ", ".join(a.value for a in GATED[change_type]) or "none"
    artifacts = ", ".join(a.value for a in REQUIRED[change_type])
    return (
        f"Delivered through the change flow (`docs/delivery/CONVENTION.md`).\n\n"
        f"- type: `{change_type.value}`\n"
        f"- required artifacts: {artifacts}\n"
        f"- human gates: {gates}\n\n"
        f"Acceptance criteria live in the change folder, not in this issue body.\n"
    )


def _template_body(template: Path) -> str:
    """Templates carry a body only; the header is generated, never copied."""
    if not template.exists():
        return f"# {template.stem}\n"
    text = template.read_text(encoding="utf-8")
    if text.lstrip().startswith("---"):
        _, body = fm.split(text)
        return body
    return text


def _scaffold(root: Path, directory: Path, artifact: Artifact, change_type: ChangeType,
              ref: str, branch: str, issue_url: str | None) -> None:
    template = root / TEMPLATES / f"{artifact.value}.md"
    target = artifact_path(directory, artifact)
    body = _template_body(template)
    meta = Frontmatter(
        workItemRef=ref,
        artifact=artifact,
        type=change_type,
        version=1,
        status=Status.DRAFT,
        persona=OWNER[artifact],
        links=Links(issue=issue_url, branch=branch),
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    fm.write(target, meta, body)


def approve(root: Path, ref: str, artifact: Artifact, who: str | None = None) -> Path:
    """Stamp an approval. Human approval is a command, never a hand edit."""
    directory = find_change_dir(ref, root)
    if directory is None:
        raise RuntimeError(f"no change folder for {ref}")
    path = artifact_path(directory, artifact)
    if not path.exists():
        raise RuntimeError(f"{path} does not exist")
    meta, body = fm.read(path)
    if meta.status is Status.APPROVED:
        raise RuntimeError(f"{artifact.value} is already approved (version {meta.version})")
    meta.status = Status.APPROVED
    meta.approved_at = _now()
    meta.approved_by = who or gitctx.gh_login() or gitctx.run("git", "config", "user.name", check=False) or "unknown"
    fm.write(path, meta, body)
    return path


def collect_evidence(root: Path, ref: str) -> Path:
    """Generate evidence from machine facts. Never written by an agent."""
    directory = find_change_dir(ref, root)
    if directory is None:
        raise RuntimeError(f"no change folder for {ref}")
    branch = gitctx.current_branch()
    sha = gitctx.head_sha()
    run = gitctx.latest_run_for_branch(branch) if gitctx.gh_available() else None

    collected: list[str] = []
    for pattern, label in (("**/build/test-results/**/*.xml", "junit"), ("**/build/reports/jacoco/**/*.xml", "coverage")):
        for src in sorted(root.glob(pattern)):
            dest = directory / "evidence" / label / src.relative_to(root).as_posix().replace("/", "_")
            dest.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(src, dest)
            collected.append(str(dest.relative_to(directory)))

    first = next((artifact_path(directory, a) for a in Artifact if artifact_path(directory, a).exists()), None)
    change_type = fm.read(first)[0].type if first else ChangeType.CHORE

    lines = [
        "# Evidence",
        "",
        "Generated by `chg evidence`. Do not edit by hand -- rerun the command.",
        "",
        f"- commit: `{sha}`",
        f"- branch: `{branch}`",
        f"- workflow: {run['workflowName'] if run else 'n/a'}",
        f"- conclusion: **{(run or {}).get('conclusion') or 'unknown'}**",
        f"- run: {(run or {}).get('url') or 'n/a'}",
        "",
        "## Collected artifacts",
        "",
    ]
    lines += [f"- `{c}`" for c in collected] or ["- none found (run the build first)"]
    lines += ["", "## Interpretation", "", "<!-- the only section an agent may write -->", ""]

    meta = Frontmatter(
        workItemRef=ref,
        artifact=Artifact.EVIDENCE,
        type=change_type,
        persona=None,
        links=Links(branch=branch, ci_run=(run or {}).get("url")),
    )
    target = artifact_path(directory, Artifact.EVIDENCE)
    fm.write(target, meta, "\n".join(lines))
    return target


def sync_agents(root: Path) -> list[Path]:
    """Generate tool adapters from the canonical persona definitions.

    One source of truth, two consumers: Copilot reads .github/agents,
    Claude Code reads .claude/agents.
    """
    written: list[Path] = []
    sources = sorted((root / AGENTS_SRC).glob("*.md"))
    for src in sources:
        text = src.read_text(encoding="utf-8")
        data, body = fm.split(text)
        name = data.get("name", src.stem)
        description = data.get("description", "")

        copilot = root / ".github" / "agents" / f"{name}.agent.md"
        copilot.parent.mkdir(parents=True, exist_ok=True)
        copilot.write_text(
            f"---\nname: {name}\ndescription: {description}\n---\n{body}", encoding="utf-8"
        )
        written.append(copilot)

        claude = root / ".claude" / "agents" / f"{name}.md"
        claude.parent.mkdir(parents=True, exist_ok=True)
        claude.write_text(
            f"---\nname: {name}\ndescription: {description}\n---\n{body}", encoding="utf-8"
        )
        written.append(claude)
    return written
