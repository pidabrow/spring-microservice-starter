# chg

Deterministic CLI behind the change delivery flow. Everything an agent would
otherwise have to be told in every prompt — identifiers, folder scaffolding,
branch names, evidence collection — lives here instead.

The convention it enforces: [`docs/delivery/CONVENTION.md`](../../docs/delivery/CONVENTION.md).

## Install

```bash
pip install -e ./tools/chg      # requires Python 3.11+
```

## Use

```bash
chg new --type feat --title "Outbox retry with backoff"
chg approve GH-42 spec
chg lint GH-42
chg evidence GH-42
chg lint --strict GH-42
```

Offline or without the `gh` CLI, pass an existing identifier:
`chg new --type fix --title "..." --ref PDEV-123 --no-branch`.

## Design

- A command exists only when it is deterministic and would otherwise have to be
  described in words in every prompt. Five commands is the budget; the bar for a
  sixth is high.
- The CLI never asks an agent for anything. Reasoning stays in the personas.
- `model.py` holds the vocabulary: change types, artifacts, statuses and the
  artifact contract per type. It is the only place those are defined.

## Tests

```bash
python -m pytest tools/chg/tests -q
```
