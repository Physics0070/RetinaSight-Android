# Claude Chat → Claude Code Handoff Kit

One prompt, two helper scripts, two slash commands. Drop them into any project
to continue a Claude.ai conversation inside Claude Code without re-explaining
anything, on any machine or account.

Nothing here assumes what you are building or what stack you use.

Built around **Claude Usage Tracker — Chat Export**
(`madhogacekcffodccklcahghccobigof`), which exports a conversation to
Markdown / HTML / ZIP and shows how full the current context window is. When
the token counter climbs, you export and continue here.

## Files

| File | What it's for |
|---|---|
| `MASTER_PROMPT.md` | **The one prompt.** Read the export → rebuild context → fix the environment → work → deliver final files → write `HANDOFF.md`. |
| `.claude/commands/resume.md` | `/resume path/to/export.md` — runs the master prompt. |
| `.claude/commands/handoff.md` | `/handoff` — writes the six-section `HANDOFF.md` before you stop. |
| `project-manifest.yml` | Optional. Declares tools, project deps, and env var names for the bootstrap scripts. |
| `scripts/bootstrap.sh` | macOS/Linux: checks every dependency, installs what's missing. |
| `scripts/bootstrap.ps1` | Windows: same, via winget (falls back to choco). |
| `HANDOFF_LOG.md` | Optional running history, one dated line per session. |
| `exports/` | Where you drop exported chats. Gitignored. |

`HANDOFF.md` itself is written by the agent, not shipped here.

## The six sections

Everything in this kit is built around one structure, used both to read the
past and to write the future:

1. **Goal** — what we are ultimately building, in one or two lines
2. **Current state** — what works now; what's done, what's open
3. **Active files** — the exact files in play
4. **Changes made** — what changed this session
5. **Failed attempts** — dead ends, so they're never repeated
6. **Next steps** — specific actions, in order

## Setup

1. Copy every file into your project root, keeping `.claude/` and `scripts/`.
2. Optional: edit `project-manifest.yml` so the tool list matches your project,
   and add any required env var **names** under `env:` (names only, never
   values). Skip this and the agent will read your own manifests instead.
3. Optional sanity check:

   ```bash
   bash scripts/bootstrap.sh --check              # macOS / Linux
   pwsh -File scripts\bootstrap.ps1 -CheckOnly    # Windows
   ```

## The loop

1. Work normally in Claude.ai. Watch the extension's token counter.
2. Before quality drops, hit **Export** → Markdown, slider at 100%.
3. Save it into `exports/`.
4. In Claude Code: `/resume exports/your-export.md`, then state your goal.
   (No slash commands? Paste `MASTER_PROMPT.md` with its two placeholders
   filled in — same result.)
5. The agent shows a Situation Report, fixes the environment, then works.
6. Say `final files` when done — every file written to disk in full, plus an
   inventory table, real build/test output, and the clean-machine sequence.
7. Say `/handoff` before you stop.

## New machine or new account

Nothing extra to do. Step 1 of the prompt makes the agent detect the OS, work
out the dependencies from your own manifests, and install what's missing.
Anything needing sudo, credentials, a licence, or a reboot stops and asks you
instead of guessing.

Secrets never travel in a chat export. `.env` stays local and gitignored; the
agent writes `.env.example` listing which keys you need and where to get them.

## Slash command scope

`.claude/commands/` is project-scoped — it ships with the repo and works for
anyone who clones it. Copy the files to `~/.claude/commands/` instead to get
`/resume` and `/handoff` in every project.

## Gotchas

- The extension doesn't run on `claude.ai/code` pages or the Claude Code CLI —
  it's for normal Claude.ai chats, which is the direction of this workflow.
- A partial export drops the *earliest* messages, which is usually where the
  architectural decisions live. Use 100% for real handoffs.
- HTML exports carry styling you don't need. Markdown is the better input.
- `winget` updates PATH for new processes only. The PowerShell script refreshes
  PATH in-session, but if a freshly installed tool still isn't found, reopen the
  terminal and re-run.
- On Windows the manifest's `python3` / `pip3` map to `python` / `pip`
  automatically, and the Microsoft Store python stub is ignored.
