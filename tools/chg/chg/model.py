"""Canonical vocabulary of the delivery flow.

One word per concept, used identically in the CLI, in artifact frontmatter,
in persona definitions and in lint messages. If you need a new word here,
you are probably adding a concept -- think twice.
"""

from __future__ import annotations

import re
from enum import Enum
from pathlib import Path

from pydantic import BaseModel, ConfigDict, field_validator

CHANGES_ROOT = Path("docs/changes")
WORK_ITEM_RE = re.compile(r"^[A-Z][A-Z0-9]*-\d+$")
BRANCH_RE = re.compile(r"^(?P<type>feat|fix|chore|spike)/(?P<ref>[A-Z][A-Z0-9]*-\d+)-(?P<slug>[a-z0-9][a-z0-9-]*)$")

# Paths considered "production code" for the test-before-code rule (R5).
PRODUCTION_PATHSPEC = ":(glob)**/src/main/**"


class ChangeType(str, Enum):
    """Type of change. A value exists only when it implies a different artifact contract."""

    FEAT = "feat"
    FIX = "fix"
    CHORE = "chore"
    SPIKE = "spike"


class Artifact(str, Enum):
    ANALYSIS = "analysis"
    DIAGNOSIS = "diagnosis"
    BRIEF = "brief"
    SPEC = "spec"
    TEST_PLAN = "test-plan"
    IMPLEMENTATION = "implementation"
    FINDINGS = "findings"
    EVIDENCE = "evidence"


class Status(str, Enum):
    DRAFT = "draft"
    APPROVED = "approved"


class Persona(str, Enum):
    ANALYST = "analyst"
    TESTER = "tester"
    DEVELOPER = "developer"


#: Artifacts that must exist before a PR may be opened.
REQUIRED: dict[ChangeType, tuple[Artifact, ...]] = {
    ChangeType.FEAT: (Artifact.ANALYSIS, Artifact.SPEC, Artifact.TEST_PLAN, Artifact.IMPLEMENTATION, Artifact.EVIDENCE),
    ChangeType.FIX: (Artifact.DIAGNOSIS, Artifact.TEST_PLAN, Artifact.IMPLEMENTATION, Artifact.EVIDENCE),
    ChangeType.CHORE: (Artifact.EVIDENCE,),
    ChangeType.SPIKE: (Artifact.BRIEF, Artifact.FINDINGS),
}

#: Artifacts that require an explicit human gate (`chg approve`).
GATED: dict[ChangeType, tuple[Artifact, ...]] = {
    ChangeType.FEAT: (Artifact.ANALYSIS, Artifact.SPEC, Artifact.TEST_PLAN),
    ChangeType.FIX: (Artifact.DIAGNOSIS, Artifact.TEST_PLAN),
    ChangeType.CHORE: (),
    ChangeType.SPIKE: (Artifact.BRIEF,),
}

#: Which artifact the flow starts with, per type.
FIRST_ARTIFACT: dict[ChangeType, Artifact | None] = {
    ChangeType.FEAT: Artifact.ANALYSIS,
    ChangeType.FIX: Artifact.DIAGNOSIS,
    ChangeType.CHORE: None,
    ChangeType.SPIKE: Artifact.BRIEF,
}

#: Owner of each artifact. Personas write only their own output.
OWNER: dict[Artifact, Persona | None] = {
    Artifact.ANALYSIS: Persona.ANALYST,
    Artifact.DIAGNOSIS: Persona.ANALYST,
    Artifact.BRIEF: Persona.ANALYST,
    Artifact.SPEC: Persona.ANALYST,
    Artifact.TEST_PLAN: Persona.TESTER,
    Artifact.IMPLEMENTATION: Persona.DEVELOPER,
    Artifact.FINDINGS: Persona.DEVELOPER,
    Artifact.EVIDENCE: None,  # machine-generated, never written by an agent
}


class Links(BaseModel):
    model_config = ConfigDict(extra="forbid")

    issue: str | None = None
    branch: str | None = None
    pr: str | None = None
    ci_run: str | None = None


class Frontmatter(BaseModel):
    """Schema every artifact header must satisfy (lint rule R3)."""

    model_config = ConfigDict(extra="forbid", use_enum_values=False)

    workItemRef: str
    artifact: Artifact
    type: ChangeType
    version: int = 1
    status: Status = Status.DRAFT
    persona: Persona | None = None
    links: Links = Links()
    approved_at: str | None = None
    approved_by: str | None = None

    @field_validator("workItemRef")
    @classmethod
    def _ref_shape(cls, v: str) -> str:
        if not WORK_ITEM_RE.match(v):
            raise ValueError(f"workItemRef must look like GH-42 or PDEV-123, got {v!r}")
        return v

    @field_validator("version")
    @classmethod
    def _version_positive(cls, v: int) -> int:
        if v < 1:
            raise ValueError("version starts at 1")
        return v


def slugify(title: str, max_len: int = 48) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
    if len(slug) > max_len:
        slug = slug[:max_len].rstrip("-")
    return slug or "change"


def branch_name(change_type: ChangeType, ref: str, slug: str) -> str:
    return f"{change_type.value}/{ref}-{slug}"


def parse_branch(branch: str) -> tuple[ChangeType, str, str] | None:
    """Return (type, workItemRef, slug) or None when the branch is not a change branch."""
    m = BRANCH_RE.match(branch)
    if not m:
        return None
    return ChangeType(m.group("type")), m.group("ref"), m.group("slug")


def change_dir(ref: str, slug: str) -> Path:
    return CHANGES_ROOT / f"{ref}-{slug}"


def artifact_path(directory: Path, artifact: Artifact) -> Path:
    if artifact is Artifact.EVIDENCE:
        return directory / "evidence" / "evidence.md"
    return directory / f"{artifact.value}.md"


def needs_version_bump(old_status: Status, old_version: int, new_status: Status, new_version: int) -> bool:
    """R7: an approved artifact may not change silently.

    Editing an approved artifact is allowed only if the version is bumped
    or the status is pulled back to draft.
    """
    if old_status is not Status.APPROVED:
        return False
    if new_status is Status.DRAFT:
        return False
    return new_version <= old_version
