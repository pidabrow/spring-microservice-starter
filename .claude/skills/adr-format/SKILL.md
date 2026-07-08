---
name: adr-format
description: Canonical ADR format and migration protocol for docs/adr/*.md. Use when creating a new ADR or migrating an existing ADR to the canonical template.
---

# ADR Format & Migration Protocol

The authoritative ADR policy for this repo is `CLAUDE.md`, section [45 — ADR Policy](../../../CLAUDE.md#45--adr-policy). **Read it in full before creating or migrating an ADR.** This skill delegates to that section and adds workflow tactics not present there.

## Workflow

1. **Read the "45 — ADR Policy" section of `CLAUDE.md` end-to-end.** Do not summarise from memory — read the file, and follow the canonical template and hard rules for migration defined there.
2. **Never fabricate content.** If the original ADR does not contain enough information to fill a section, leave an HTML comment placeholder: `<!-- TODO: fill in — [what's missing] -->`. Do not infer business rationale, stakeholders, or test names that aren't stated or directly implied by the original text.
3. **One file per turn.** Migrate exactly one ADR file, then stop and show the full diff. Do not proceed to the next file and do not write/commit until the user explicitly approves.
4. **All content in English**, including TODO comments, even if the source ADR (or the conversation invoking this skill) is in another language.
5. **Do not rename files or renumber ADRs.** Only the internal structure changes.
