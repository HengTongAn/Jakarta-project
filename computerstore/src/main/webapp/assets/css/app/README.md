# app/ stylesheets — layer structure

These files replace the old 2,922-line `assets/css/style.css` (split on
2026-09-24 by `tools/css/split-css.py`). Every line of the original was
preserved exactly once (verified by a multiset check) and each file keeps its
blocks in the *original* relative order.

## Why layers

The original file accumulated top-to-bottom over time, so equal-specificity
overrides depended on position. Splitting into layers with a fixed load order
makes the intent explicit and removes the hidden ordering traps.

## Load order (also wired in `WEB-INF/views/layouts/header.jspf`)

| order | file            | contains                                              | rule |
|-------|-----------------|-------------------------------------------------------|------|
| 1     | `variables.css` | `:root` design tokens (colors, spacing…)              | no rules, only tokens |
| 2     | `base.css`      | body/type, links, focus, scrollbar, empty states      | global element styles only |
| 3     | `layout.css`    | hero, navbar, search bar, storefront sections         | structural page furniture |
| 4     | `components.css`| buttons, cards, forms, mail/chats, toasts, tables…    | reusable pieces |
| 5     | `pages.css`     | checkout, cart, profile, browse, admin page styles    | one-off page layouts |
| 6     | `theme.css`     | dark mode, responsive pass, motion, reduced-motion    | LAST — may refine anything |

Rules are simple:

- **A layer may only refine earlier layers** (never the other way around).
- Keep a rule with its own `@media` block together (responsive tweaks live in
  the file of the component they modify, or in `theme.css` for cross-cutting
  ones).
- When adding a new stylesheet to the app, prefer a block in the right layer
  over editing `theme.css`, and never re-order the `<link>` tags.

## Regenerating (rare)

`python3 tools/css/split-css.py` rebuilds the six files from a `style.css`
placed back at `src/main/webapp/assets/css/style.css`, then verifies line
preservation. It exits non-zero and writes nothing final unless the check
passes.

## Note for reviewers

This split intentionally fixes one latent mobile bug: `.table-filter` was kept
at `max-width: 260px` on phones because the admin-table rule loaded after the
responsive pass. `theme.css` now loads last, so the responsive rule wins on
small screens.