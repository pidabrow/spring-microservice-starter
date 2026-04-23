# Personal session overrides

## Current session: ADR-011 spike (experimental)

This is the first experimental run evaluating Claude Code on this repo. Behavior expectations for this session:

- **Slow down.** Narrate decisions before coding. Stop for my confirmation before starting implementation.
- **Declare change classification upfront** per .cursor/rules/00-base.mdc.
- **Check "Related:" ADRs before starting.** If ADR-011 depends on an ADR that is not yet implemented, surface the gap before coding — do not silently extend scope.
- **Scope is strictly ADR-011 points 1, 2, 3, 5.** Point 4 (traceId injection) is explicitly OUT OF SCOPE for this spike — leave a `// TODO(ADR-010)` seam.
- **No new domain exceptions invented for demo purposes.** Use what exists in the codebase; if nothing suitable exists, ask.