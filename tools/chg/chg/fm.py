"""Reading and writing artifact frontmatter without touching the body."""

from __future__ import annotations

from pathlib import Path

import yaml

from .model import Frontmatter

DELIM = "---"


class FrontmatterError(ValueError):
    pass


def split(text: str) -> tuple[dict, str]:
    """Split a markdown document into (frontmatter dict, body)."""
    lines = text.splitlines()
    if not lines or lines[0].strip() != DELIM:
        raise FrontmatterError("file does not start with a '---' frontmatter block")
    try:
        end = next(i for i, line in enumerate(lines[1:], start=1) if line.strip() == DELIM)
    except StopIteration as exc:
        raise FrontmatterError("frontmatter block is not closed") from exc
    raw = "\n".join(lines[1:end])
    body = "\n".join(lines[end + 1 :])
    data = yaml.safe_load(raw) or {}
    if not isinstance(data, dict):
        raise FrontmatterError("frontmatter must be a YAML mapping")
    return data, body


def join(data: dict, body: str) -> str:
    dumped = yaml.safe_dump(data, sort_keys=False, allow_unicode=True).rstrip("\n")
    return f"{DELIM}\n{dumped}\n{DELIM}\n{body.lstrip(chr(10))}"


def parse(text: str) -> tuple[Frontmatter, str]:
    data, body = split(text)
    return Frontmatter.model_validate(data), body


def read(path: Path) -> tuple[Frontmatter, str]:
    return parse(path.read_text(encoding="utf-8"))


def write(path: Path, meta: Frontmatter, body: str) -> None:
    data = meta.model_dump(mode="json", exclude_none=False)
    path.write_text(join(data, body), encoding="utf-8")
