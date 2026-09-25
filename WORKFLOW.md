# Team Workflow — GitHub Flow

This project uses **GitHub Flow**: one short-lived branch per task, merged into `main`
via pull request. `main` is always in a working state — nothing goes in except through
a reviewed PR.

## Why branches instead of passing files

Pushing a branch is safer and more professional than sending files through one person:

- everyone always works against the latest code (`git pull origin main`)
- each commit shows **who** wrote it (set your own git identity, one time)
- nobody is a bottleneck; conflicts are fixed by the person who wrote the code
- `main` stays clean: one commit per PR (squash merge)

## Team & branches

| Who  | Role            | Branch prefix            |
| ---- | --------------- | ------------------------ |
| A    | Team lead / reviewer | merges PRs into `main` |
| B    | Frontend        | `feature/frontend/*`     |
| C    | Backend         | `feature/backend/*`      |
| D    | Backend         | `feature/backend/*`      |
| any  | Docs / tooling  | `docs/*`                 |

Everyone needs **write access** to this repo so they can push their own branches:
A → repo **Settings → Collaborators** → add each member's GitHub account.

## One-time setup (each member, own machine)

```bash
# your real name/email — this is what appears on every commit you make
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"

git clone <repo-url>
cd <repo>
```

## Daily loop

```bash
# always start from an up-to-date main
git checkout main
git pull origin main

# one task → one small branch, with your role's prefix + short task name
git checkout -b feature/backend/products

# ... work, commit small and often ...
git add -A
git commit -m "feat: add product CRUD service"

# share the branch (first time: -u sets the upstream)
git push -u origin feature/backend/products
```

## Pull request → merge

1. On GitHub open a PR: `feature/<...>` → `main`.
2. Title = what it does. Description = what / why / how tested.
3. Request review from A (and one teammate as a second pair of eyes).
4. A approves, then uses **Squash and merge** — `main` stays linear, one commit per PR.
5. Delete the feature branch after merge (GitHub button).

If you only want feedback and the code isn't ready to merge, still push the branch and
open a PR marked **Draft** — teammates can pull it and comment.

## Rules

- **Never push to `main` directly** — changes only land via PR.
- **Never force-push** a branch other people have already seen (`git push --force`).
- **Never rewrite shared history** — no rebasing `main`, no `reset --hard` against the shared repo.
- Always `git pull origin main` before starting a new branch.
- Commit as yourself — the history should honestly show who did what.