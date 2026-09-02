---
name: shipping-a-change
description: The full path from a fix to a release - branch, commits, two review cycles, green CI, merge, and the APKs. Use whenever asked to fix, implement, release, or open a PR in this repository.
---

# Shipping a change

## The loop

1. Branch off `main` (pull first).
2. Implement in **separate commits, one per concern**, each a single-line
   conventional commit message. `feat:` and `fix:` drive the version bump;
   `perf:`, `chore:`, `refactor:` do not.
3. **Two review cycles.** Not a formality - every round of this has found
   something. Re-read the diff fresh, looking for what the change breaks rather
   than confirming it works.
4. Run the local gate (see the `local-gate` skill). Do not push red.
5. Push, open a PR, wait for all checks.
6. Merge, then release - unless the request was for a PR only. Read the request
   carefully: asking to roll out a *release* means merge and release; asking to
   roll out a *pull request* means stop at the PR and let the author merge. The
   author writes in Russian and the distinction is one word, so re-read it.

## What the review cycles are for

Real findings from past cycles, as a sense of the bar:

- A page-size default meant only the first 30 starred segments were ever
  fetched.
- A listing cut short by a rate limit would have deleted every segment it did
  not manage to read.
- The watch parsed every stored segment file on the main thread, under the tap
  that starts a ride.
- A failed background sync retried forever, because WorkManager retries a
  one-off request indefinitely by default.

The pattern: the happy path was fine and the edge was silent. Look at what
happens when a list is long, a response is truncated, a file is large, or a
service is permanently broken.

## GitHub specifics

The base branch policy blocks a plain merge, and squash and merge commits are
both disabled. The only thing that works:

```
gh pr merge <N> --rebase --delete-branch --admin
```

Checks on a PR (11 of them): `pre-commit`, `actionlint`, `commitizen`,
`android`, `CodeQL`, `Analyze (java-kotlin)`, `osv-scan`, and three `connected`
emulator jobs (round watch, square watch, phone). `android` is the slow one -
it runs ktlint, detekt, lint, unit tests, coverage and both assembles.

Watch them with a polling loop rather than repeated manual checks:

```
gh pr checks <N> --json name,bucket --jq '.[] | "\(.bucket)\t\(.name)"' | sort
```

A run that fails on the `release-please--...` branch right after a merge is the
branch being deleted out from under it, not a real failure. GitHub reports it as
"This run likely failed because of a workflow file issue."

## Releasing

release-please raises a PR titled `chore(main): release X.Y.Z` a minute or two
after the merge to `main`. Merge it the same way (`--rebase --admin`), and the
build-and-distribute workflow attaches two APKs to the tag. Confirm they landed:

```
gh release view vX.Y.Z --json assets --jq '.assets[] | select(.name|endswith(".apk")) | .name'
```

Only the watch APK matters for recording; the phone one is the companion.

## Reporting back

Lead with the conclusion in a sentence or two. What shipped, what the user has
to do differently, and anything found but deliberately not fixed. Skip the
derivation unless it changes what they do next.
