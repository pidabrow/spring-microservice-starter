---
name: adr-format
description: Canonical ADR format and migration protocol for docs/adr/*.md. Use when creating a new ADR or migrating an existing ADR to the canonical template.
---

# ADR Format & Migration Protocol

## Canonical template

Every ADR in `docs/adr/` must follow this structure:

# ADR-{NNN} — {Title}

**Status:** {Proposed | Accepted | Rejected | Deprecated | Superseded by ADR-XXX}
**Date:** {YYYY-MM-DD}
**Decision-makers:** {who}
**Consulted:** {optional}
**Informed:** {optional}
**Related:** {ADR-XXX, files, rules}

## Context and Problem Statement
## Decision Drivers
## Considered Options
## Decision Outcome
### Consequences
**Positive** / **Negative**
### Confirmation
## Pros and Cons of the Options
## Notes for AI
## More Information

## Hard rules for migration

1. **Never fabricate content.** If the original ADR does not contain enough information to
   fill a section (Decision Drivers, Considered Options, Confirmation, per-option Pros/Cons,
   Related, Decision-makers), leave an HTML comment placeholder:
   `<!-- TODO: fill in — [what's missing] -->`
   Do not infer business rationale, stakeholders, or test names that aren't stated or
   directly implied by the original text.
2. **Preserve the original decision content exactly.** Reformatting must not change what
   was decided — only how it's structured. Implementation-detail bullets that read as
   conventions/gotchas for a coding agent (not architectural facts) should be moved into
   `## Notes for AI` rather than duplicated in `Decision`.
3. **One file per turn.** Migrate exactly one ADR file, then stop and show the full diff.
   Do not proceed to the next file and do not write/commit until the user explicitly approves.
4. **All content in English**, including TODO comments, even if the source ADR (or the
   conversation invoking this rule) is in another language.
5. **Do not touch file names or ADR numbers.** Only the internal structure changes.
