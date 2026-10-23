# Website

These rules apply to everything in this folder, the site published at https://fileexplorer.org. The
app's rules live in the repository's root `AGENTS.md`.

Keep this file up to date if rules need to be added or updated.

## Commands

The site is static files served as written, with no build step.

Reserved for the owner — never part of the per-change loop, and run from the repository root:

- `./scripts/deploy.sh` — publishes this folder to Firebase Hosting (https://fileexplorer.org). The
  site is public the moment it runs.
- `./scripts/serve.sh` — serves this folder locally through the Firebase Hosting emulator
  (http://127.0.0.1:5000), with the same clean URLs and 404 page as production. Runs until Ctrl+C.

## Development Standards

### Theming (Dark/Light)

- All pages must render correctly in light, dark and system theme modes
- Test new pages and UI in both themes before completing work

### Localization

- Localized documents (`privacy.md`, `terms.md`) live in the app's `raw/` and the matching `raw-*/`
  directories for the same 20 languages — when the English version changes, update all 19
  translations, copy the English text into `privacy.html` or `terms.html`, and copy each language's
  text into its folder's page (`es/privacy.html`, …)
- Consider text expansion (~30-40%) when designing layouts
- RTL languages (Arabic, Urdu) are supported; see the language rules below

### Content

- `index.html` repeats the README's intro word for word, and states every fact of its feature
  sections in its own form: fewer sections, each a list of points with a short title and
  description. Each translated home page (`es/index.html`, …) says the same in its language. When a
  feature is added, removed or changed in `../README.md` or on a home page, make the same change to
  the facts in the README and on every home page — the wording and grouping may differ, the facts
  may not
- The hero's list of figures (downloads, years on the market) lives only on the home pages, not in
  `../README.md`, and is typed by hand: change a figure on all 20 home pages at once. List only
  figures that stay true as time passes and that a visitor checking Google Play won't find
  contradicted — which is why it leaves out the star rating (Play shows each country its own: 3.6
  to 4.1 in October 2026) and active users
- A point's icon is a classic Material Icons Outlined SVG — the set the app's `Icons.Outlined` and
  the header's icons come from — pasted inline as `<svg viewBox="0 0 24 24" aria-hidden="true"
  focusable="false"><path fill="currentColor" d="…"/></svg>`, keeping only the shapes from the
  source file. Take new ones from the `outlined/` folder of the `@material-design-icons/svg` npm
  package
- Each feature's `data-screen` picks the screenshot the pinned phone shows beside it. Screenshots
  live in `images/<code>/` as `<screen>-dark.webp`/`<screen>-light.webp` pairs, 600px wide: each
  home page shows its own language's, in the visitor's theme. They were captured once, from the
  app's real screens fed made-up files and public-domain photos, by `scripts/website-screenshots.sh`
  and the `WebsiteScreenshots` instrumentation class, with the photos and PDF in
  `../app/src/androidTest/assets/website/`. All three were then deleted; to capture a screen again,
  restore them from the commit before the one that deleted them, as `../SCREENSHOTS.md` shows, and
  run the script with the screen's name

### Languages

- The site is in English at `/` and in the app's 19 other languages under `/<code>` (`/es`, `/fr`,
  …; Indonesian is `/id` here though the app's folder is `in`, Portuguese is Brazilian and Chinese
  is Simplified): every page here has a copy at the same path in each language's folder, except
  `404.html`, which Firebase serves at any missing address and so carries every language (one
  `.notice-copy` block each, hidden by a `styles.css` rule per language) and shows one. Every other
  page names the addresses of all its languages in `<link rel="alternate" hreflang>` tags (one per
  language, plus `x-default` → English), which `language.js` reads to redirect; `404.html` must
  carry none, since that is how `language.js` and `analytics.js` recognize it. Every page's header
  has a language menu linking to each language, marking the page's own with `aria-current="true"`
  (on `404.html`, `language.js` marks it). Arabic and Urdu pages carry `dir="rtl"` on `<html>` (on
  `404.html`, `language.js` sets it). Every page loads `language.js` in its `<head>`, without
  `defer`, after any `hreflang` tags and before `theme.js`
- Adding a language takes: its folder of pages; its code in `language.js` (`LANGUAGES`,
  `RTL_LANGUAGES` when it is written right to left, `MENU_LABELS`) and `theme.js` (`LABELS`); a
  menu entry on every page, `404.html` included; an `hreflang` tag on every page except
  `404.html`; a `.notice-copy` block in `404.html` with its `styles.css` rule; its `og:locale` on
  every page, plus an `og:locale:alternate` for it; its `<title>` and `<meta name="description">`;
  its entries in `sitemap.xml`; its localized Play badge from Google's Partner Marketing Hub
  (Get it on Google Play Badges → Digital → svg; Japanese has only the print artwork there); and
  its screenshots in `images/<code>/` (English's are in `images/en/`), captured by restoring the
  capture tool the screenshot rule above names and adding the code to its language map

### Scripts and styles

- Visitors can pick light or dark with the header's switcher, and `theme.js` applies the pick. Every
  page loads `theme.js` in its `<head>`, without `defer` and after both `theme-color` metas, and
  carries the switcher's `<header>`. A dark screenshot's `<source>` keeps exactly
  `media="(prefers-color-scheme: dark)"`, which is how the script finds it. `styles.css` lists the
  dark colors twice, once for the system theme and once for the switcher, and the two must match
- The site makes no third-party requests but Google's tag, which `analytics.js` loads: host every
  other image, font and script inside this folder
- `analytics.js` reports visits and the events Google's tag misses (Play badge clicks, features
  scrolled into view, language and theme picks, missing pages) to the website's own Google Analytics
  property, not the app's, and only on `fileexplorer.org`, so `./scripts/serve.sh` and other
  previews send nothing. Every page loads it in its `<head>` with `defer`, after `theme.js`. Events
  describe the visit, never the visitor, as in the app
- Every Google Play badge link carries a `referrer` campaign tag naming its spot and language
  (`utm_campaign%3Dhero-es`), which Play Console's acquisition reports group installs by. A new
  badge gets its own

### Search, sharing and security

- Search and sharing: every page except `404.html` has a `<link rel="canonical">` equal to its own
  `hreflang` address and its `og:url`, a unique `<title>` and description (`og:title` and
  `og:description` repeat them), and the Open Graph and Twitter Card tags pointing at
  `images/og-image.png`, one language-neutral 1200×630 picture of the app's icon and name. Only the
  home pages carry JSON-LD. `404.html` is `noindex` and carries none of these. Every page,
  `404.html` included, carries the author, referrer and CSP metas and the `apple-touch-icon` link.
  Every `target="_blank"` link has `rel="noopener noreferrer"`
- `sitemap.xml` lists every page's canonical address, and its `<lastmod>` is typed by hand: raise
  it to the day a page changes, and add an entry for every new page and language
- The CSP meta allows only the site's own files plus Google's tag hosts. Nothing may need
  `unsafe-inline`: no inline scripts, `<style>` blocks or `style=` attributes. A new third-party
  host must be added to the policy on every page
- `.well-known/security.txt` expires on 2027-10-01; renew its `Expires` before then.
  `../firebase.json` ignores dotfiles except `.well-known`, and ignores every `AGENTS.md`, so this
  file is never published
