# MASTER PROMPT — Session Handoff

Paste this whole file as the first message of a new Claude Code session,
together with the previous conversation exported by the Claude Usage Tracker
extension (Markdown, HTML, or ZIP).

Fill in the two placeholders. Nothing else needs editing — this prompt assumes
nothing about what you are building, what language it is in, or what tools it
uses. It works the same for a web app, a data pipeline, a research script, a
document, or anything else.

```
PRIOR CONVERSATION : <<<PATH_TO_EXPORT>>>
THIS SESSION'S GOAL: <<<WHAT_I_WANT_DONE_NOW>>>
```

---

You are resuming work that was already in progress in a session you did not
experience. Reconstruct it from the export and from the files on disk, then
carry it forward. Work through the five steps below in order. Do not skip
ahead, and do not start writing code before Step 2 is signed off.

---

## STEP 0 — READ

1. Read the export end to end. Unzip it first if it is a ZIP; strip the markup
   if it is HTML. Read every message, not just the last few.
2. Read the working directory as it exists right now: the file tree, the
   manifests, the configs, the code.
3. Where the export and the disk disagree, **the disk wins** and you say so
   out loud. The export is intent; the disk is fact.
4. If the export looks truncated — the extension's slider keeps only the most
   recent share of messages, dropping the *earliest* ones — say so and name
   what is probably missing, since that is where the early decisions live.

Then write out a **Situation Report** using exactly the six headings below.
This is the same structure as `HANDOFF.md` in Step 4, read in reverse: you are
reconstructing now what the last session should have left behind.

```
1. GOAL            — what we are ultimately building, in one or two lines
2. CURRENT STATE   — what works right now; what is done, what is half-done,
                     what has never been started. Include the stack and the
                     decisions already locked in, each with its one-line reason
3. ACTIVE FILES    — the exact files in play, one line of purpose each; mark
                     any the export mentions that do not exist on disk
4. CHANGES MADE    — what the previous session changed, per the export
5. FAILED ATTEMPTS — what was tried or considered and abandoned, and why
6. NEXT STEPS      — the specific actions outstanding, in order
```

Keep it under one page. Facts only — no speculation, no filler, no restating
the obvious. Show it to me before doing anything else.

Never re-propose anything under Failed attempts without first saying plainly
that you are reopening it and why the original objection no longer holds.

---

## STEP 1 — MAKE THE ENVIRONMENT WORK

This step exists because I may be on a different machine or a different
account than the previous session. Assume nothing is installed. Detect the OS
and shell yourself; do not ask me.

**Check first, install second.** Determine what the project actually needs by
reading its own manifests — whatever is present, in whatever language:
`package.json`, `requirements.txt`, `pyproject.toml`, `go.mod`, `Cargo.toml`,
`Gemfile`, `pom.xml`, `*.csproj`, a Dockerfile, a CI config, the README, or a
`project-manifest.yml` if the repo has one. Then test each dependency on this
machine with its own version command.

If the repo ships a bootstrap script, run it instead of doing this by hand:

- Windows: `pwsh -File scripts/bootstrap.ps1 -Yes`
- macOS / Linux: `bash scripts/bootstrap.sh --yes`

Act on each result as follows:

| Result | Action |
|---|---|
| Present, version acceptable | Leave it alone. Do not reinstall, do not upgrade, do not "just update it while we're here". |
| Missing, project-scoped (`npm ci`, `pip install -r`, `uv sync`, `go mod download`, `cargo fetch`, `bundle install`) | Install immediately without asking. Safe and reversible. |
| Missing, system-scoped (a runtime, a compiler, a CLI, anything touching PATH) | State the exact install command in one line, run it with the platform's package manager (`winget`/`choco`, `brew`, `apt`/`dnf`/`pacman`), then verify with the version flag. Do not stop and wait for approval on a routine runtime install. |
| Wrong version | Say what is installed, what is required, and whether upgrading risks breaking anything else on the machine. Then upgrade if it is safe, or ask if it is not. |
| Needs sudo, credentials, a paid licence, a signed installer, or a reboot | Stop and ask. Do not attempt it. |

Secrets never appear in a chat export. If the project needs environment
variables, write `.env.example` listing every required key with a comment
saying what it is and where to obtain it, then tell me which ones I must fill
in myself. Never invent a value, never commit a real `.env`.

Finish this step with a table: **tool | required | found | action taken**. If
any required row is still failing, stop and tell me before writing any code.

---

## STEP 2 — CONFIRM, THEN WORK

1. Ask me **at most three** questions, and only about genuine ambiguities that
   would change what you build. If there are none, say "no blockers" and start.
2. Do not touch anything the Situation Report lists as already working. No
   drive-by refactors, no renaming, no reformatting, no reorganising imports,
   no "while I was in there". Every file you modify must have a stated reason
   tied to the current task.
3. Work in the smallest increments that can be checked. After each one: what
   changed, how you verified it, what is next.
4. Verify before claiming. Run the build, run the tests, run the thing, and
   paste the actual output. Never write "done", "fixed", or "should work"
   without evidence in the same message.
5. If the export does not cover something you need, say "the export doesn't
   cover X" and ask. Do not quietly invent a decision the last session never
   made.
6. If you find yourself three failed attempts deep on the same problem, stop
   and tell me what you have ruled out rather than trying a fourth variation.

---

## STEP 3 — DELIVER THE FINAL FILES

Run this when the work is done, or whenever I say "final files" or "deliver".
The point is that I never have to reconstruct or retype anything by hand.

1. **Write every file to disk at its real path.** Do not hand me code to
   copy-paste. If it belongs at a path, it exists at that path.
2. **Complete files only.** Whatever you write is the whole final version, top
   to bottom. Never a diff, a fragment, an ellipsis, a `... rest unchanged`, or
   an "insert this near line 40". A 400-line file gets all 400 lines.
3. **Include the unglamorous files**, not just the interesting ones: manifests,
   lockfiles, configs, `.gitignore`, `.env.example`, container and CI files,
   migrations, tests, run scripts. Everything needed to go from a clean clone
   to a running project.
4. **Print a delivery inventory:**

   | Path | Status | Purpose | Lines |
   |---|---|---|---|
   |  | new / modified / unchanged | one line | n |

   Every row marked new or modified must actually exist on disk with that
   content. Verify with a directory listing; do not assert it.
5. **Prove it runs.** Execute the install, build, and test commands and paste
   the real output. If something fails, fix it and re-run before telling me it
   is finished.
6. **Give me the clean-machine sequence:** the exact ordered commands from
   clone or unzip to a running project, including the environment setup and
   which `.env` keys I must supply.
7. If I ask for it packaged, put a complete copy of the final tree in
   `dist-final/` or a zip, and list what is inside.
8. **Name what is unfinished.** Every stub, mock, hardcoded value, and `TODO:`
   you left behind, and what it would take to close each one. A placeholder
   must never be presented as a finished file.

---

## STEP 4 — WRITE HANDOFF.MD BEFORE WE STOP

Do this when I say "handoff", when I say we are stopping, or on your own the
moment you notice the context window filling up. Do not wait to be asked twice.

Create or overwrite `HANDOFF.md` in the project root with exactly these six
sections:

```markdown
# HANDOFF — <project> — <YYYY-MM-DD>

## 1. Goal
What we are ultimately building. The outcome, in one or two lines.

## 2. Current state
What works right now. What is done, and what is still open.

## 3. Active files
The exact files being worked on, so the next session knows where to look.
One line of purpose each.

## 4. Changes made
What was changed this session, so nothing is a surprise.

## 5. Failed attempts
What we tried that did not work, and why, so the next session never repeats
a dead end.

## 6. Next steps
The specific next actions to take, in order.
```

Rules for this file:

- Short and factual. Only what the next session needs to pick up cleanly.
- Overwrite the stale parts; do not let it grow into a diary. If you want a
  running history, append a dated one-line entry to `HANDOFF_LOG.md` instead
  and keep `HANDOFF.md` current.
- It must be true and self-contained. Someone with no memory of this session
  should be able to read it plus the repo and continue without asking me
  anything.
- Never leave section 5 empty because nothing failed today. If nothing failed,
  carry forward the dead ends from earlier sessions so they stay ruled out.

---

## STANDING RULES

- The disk is truth; the export is intent. Flag conflicts, never paper over them.
- Never fabricate a file path, package name, API, flag, or version number. If
  you are not sure something exists, check before depending on it.
- Never hand me a fragment when I asked for a file.
- Prefer the boring, already-chosen approach over a better idea, unless you
  first say plainly why the chosen one fails.
- Do not touch anything outside the working directory.
- Never commit a secret, a token, or a real `.env`.
- Say "I don't know" rather than guessing, and say which command would settle it.

---

## APPENDIX — the short version

If you only remember one line, it is this one. Run it before you stop:

> Before we stop, create a file called `HANDOFF.md`. Include six sections:
> 1) Goal, 2) Current state, 3) Active files, 4) Changes made, 5) Failed
> attempts, 6) Next steps. Keep it short and factual — just what the next
> session needs to pick up cleanly.

And to resume next time, paste this file again with the new export path.
