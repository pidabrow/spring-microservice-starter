from __future__ import annotations

import pytest

from chg import fm
from chg.lint import Severity, lint
from chg.model import (
    GATED,
    REQUIRED,
    Artifact,
    ChangeType,
    Frontmatter,
    Status,
    branch_name,
    change_dir,
    needs_version_bump,
    parse_branch,
    slugify,
)


# --- vocabulary --------------------------------------------------------------

def test_slug_is_stable_and_bounded():
    assert slugify("Outbox retry with exponential backoff!") == "outbox-retry-with-exponential-backoff"
    assert len(slugify("x" * 200)) <= 48


@pytest.mark.parametrize("change_type", list(ChangeType))
def test_branch_roundtrip(change_type):
    branch = branch_name(change_type, "GH-42", "outbox-retry")
    assert parse_branch(branch) == (change_type, "GH-42", "outbox-retry")


def test_parse_branch_rejects_freestyle_names():
    assert parse_branch("feature/GH-42") is None
    assert parse_branch("feat/gh-42-x") is None
    assert parse_branch("main") is None


def test_jira_style_refs_are_accepted():
    assert parse_branch("fix/PDEV-123-npe-on-login")[1] == "PDEV-123"


def test_change_dir_sorts_by_tracker_id():
    assert change_dir("GH-42", "outbox-retry").as_posix() == "docs/changes/GH-42-outbox-retry"


# --- artifact contracts ------------------------------------------------------

def test_every_type_has_a_contract():
    assert set(REQUIRED) == set(ChangeType)
    assert set(GATED) == set(ChangeType)


def test_fix_requires_a_test_plan_but_no_spec():
    assert Artifact.TEST_PLAN in REQUIRED[ChangeType.FIX]
    assert Artifact.SPEC not in REQUIRED[ChangeType.FIX]
    assert Artifact.DIAGNOSIS in REQUIRED[ChangeType.FIX]


def test_chore_has_no_gate():
    assert GATED[ChangeType.CHORE] == ()


def test_gated_artifacts_are_always_required():
    for change_type in ChangeType:
        assert set(GATED[change_type]) <= set(REQUIRED[change_type])


# --- frontmatter -------------------------------------------------------------

VALID = """---
workItemRef: GH-42
artifact: spec
type: feat
version: 1
status: draft
persona: analityk
links:
  issue: https://example.invalid/1
  branch: feat/GH-42-outbox-retry
---

# Spec
"""


def test_frontmatter_parses_and_keeps_body():
    meta, body = fm.parse(VALID)
    assert meta.workItemRef == "GH-42"
    assert meta.artifact is Artifact.SPEC
    assert meta.status is Status.DRAFT
    assert "# Spec" in body


def test_malformed_ref_is_rejected():
    with pytest.raises(ValueError):
        Frontmatter(workItemRef="gh42", artifact=Artifact.SPEC, type=ChangeType.FEAT)


def test_unknown_field_is_rejected():
    bad = VALID.replace("version: 1", "version: 1\nowner: piotr")
    with pytest.raises(ValueError):
        fm.parse(bad)


def test_missing_frontmatter_is_an_error():
    with pytest.raises(fm.FrontmatterError):
        fm.parse("# just a document\n")


# --- R7: no silent drift -----------------------------------------------------

def test_draft_may_change_freely():
    assert not needs_version_bump(Status.DRAFT, 1, Status.DRAFT, 1)


def test_approved_change_without_bump_is_flagged():
    assert needs_version_bump(Status.APPROVED, 1, Status.APPROVED, 1)


def test_bumping_the_version_is_the_way_out():
    assert not needs_version_bump(Status.APPROVED, 1, Status.APPROVED, 2)


def test_pulling_back_to_draft_is_the_other_way_out():
    assert not needs_version_bump(Status.APPROVED, 1, Status.DRAFT, 1)


# --- lint on a filesystem fixture --------------------------------------------

def _write(tmp_path, artifact: Artifact, change_type=ChangeType.FEAT, ref="GH-42", status=Status.DRAFT):
    directory = tmp_path / change_dir(ref, "outbox-retry")
    (directory / "evidence").mkdir(parents=True, exist_ok=True)
    from chg.model import OWNER, Links, artifact_path

    meta = Frontmatter(
        workItemRef=ref, artifact=artifact, type=change_type, status=status,
        persona=OWNER[artifact], links=Links(branch=branch_name(change_type, ref, "outbox-retry")),
    )
    fm.write(artifact_path(directory, artifact), meta, "# body\n")
    return directory


def test_missing_change_folder_is_an_error(tmp_path):
    report = lint("GH-99", tmp_path, branch="feat/GH-99-nothing")
    assert any(f.rule == "R1" and f.severity is Severity.ERROR for f in report.findings)


def test_incomplete_feature_fails_strict_but_not_loose(tmp_path):
    _write(tmp_path, Artifact.ANALYSIS)
    branch = "feat/GH-42-outbox-retry"
    assert not lint("GH-42", tmp_path, strict=False, branch=branch).failed(strict=False)
    assert lint("GH-42", tmp_path, strict=True, branch=branch).failed(strict=True)


def test_wrong_ref_in_artifact_is_caught(tmp_path):
    _write(tmp_path, Artifact.ANALYSIS)
    _write(tmp_path, Artifact.SPEC, ref="GH-42")
    directory = tmp_path / change_dir("GH-42", "outbox-retry")
    spec = directory / "spec.md"
    spec.write_text(spec.read_text().replace("GH-42", "GH-77"), encoding="utf-8")
    report = lint("GH-42", tmp_path, branch="feat/GH-42-outbox-retry")
    assert any(f.rule in {"R3", "R4"} and f.severity is Severity.ERROR for f in report.findings)


def test_folder_slug_must_match_branch(tmp_path):
    _write(tmp_path, Artifact.ANALYSIS)
    report = lint("GH-42", tmp_path, branch="feat/GH-42-something-else")
    assert any(f.rule == "R1" and f.severity is Severity.ERROR for f in report.findings)


def test_ungated_chore_passes_with_evidence_only(tmp_path):
    directory = _write(tmp_path, Artifact.EVIDENCE, change_type=ChangeType.CHORE, ref="GH-7")
    (directory / "evidence" / "evidence.md").write_text(
        (directory / "evidence" / "evidence.md").read_text().replace("# body", "commit: `abc123`"),
        encoding="utf-8",
    )
    from chg import fm as fmod
    from chg.model import artifact_path

    path = artifact_path(directory, Artifact.EVIDENCE)
    meta, body = fmod.read(path)
    meta.links.ci_run = "https://example.invalid/run/1"
    fmod.write(path, meta, body)

    report = lint("GH-7", tmp_path, strict=True, branch="chore/GH-7-outbox-retry")
    errors = [f for f in report.findings if f.severity is Severity.ERROR]
    assert not errors, errors
