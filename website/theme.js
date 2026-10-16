// Applies the visitor's light or dark pick before the page is drawn, and runs the header's switcher.
// Switching to the system's own theme clears the pick, so the page follows the system again. Without
// this script the page follows the system theme and the switcher stays hidden.
(() => {
  const STORAGE_KEY = 'theme';
  const root = document.documentElement;
  // The switcher's label in the page's language, named after the theme it switches to.
  const LABELS = {
    en: { light: 'Switch to light theme', dark: 'Switch to dark theme' },
    es: { light: 'Cambiar al tema claro', dark: 'Cambiar al tema oscuro' },
    fr: { light: 'Passer au thème clair', dark: 'Passer au thème sombre' },
  };
  const system = window.matchMedia('(prefers-color-scheme: dark)');
  const systemTheme = () => (system.matches ? 'dark' : 'light');

  const loadPick = () => {
    try {
      const theme = localStorage.getItem(STORAGE_KEY);
      return theme === 'light' || theme === 'dark' ? theme : null;
    } catch {
      return null;
    }
  };

  const savePick = (theme) => {
    try {
      if (theme) localStorage.setItem(STORAGE_KEY, theme);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage is blocked: the pick lasts until the visitor leaves the page.
    }
  };

  // The browser bar's color. Both tags get the picked theme's color, so whichever one the system
  // theme selects shows it.
  const metas = [...document.querySelectorAll('meta[name="theme-color"]')];
  const barColors = {};
  for (const meta of metas) barColors[meta.media.includes('dark') ? 'dark' : 'light'] = meta.content;

  let pick = loadPick();
  let darkSources = [];
  let button = null;

  const apply = (theme) => {
    root.dataset.theme = theme;
    for (const meta of metas) meta.content = barColors[theme];
    // A screenshot's dark version is chosen by the system theme, so point it at the page's instead.
    for (const source of darkSources) source.media = theme === 'dark' ? 'all' : 'not all';
    if (button) {
      const labels = LABELS[root.lang] ?? LABELS.en;
      const label = theme === 'dark' ? labels.light : labels.dark;
      button.setAttribute('aria-label', label);
      button.title = label;
    }
  };

  apply(pick ?? systemTheme());

  system.addEventListener('change', () => {
    if (!pick) apply(systemTheme());
  });

  // Back and Forward can restore a page as it was left, before a pick made on another page.
  window.addEventListener('pageshow', (event) => {
    if (!event.persisted) return;
    pick = loadPick();
    apply(pick ?? systemTheme());
  });

  document.addEventListener('DOMContentLoaded', () => {
    darkSources = [...document.querySelectorAll('picture source[media="(prefers-color-scheme: dark)"]')];
    button = document.querySelector('.theme-toggle');
    apply(root.dataset.theme);

    button?.addEventListener('click', () => {
      const theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
      pick = theme === systemTheme() ? null : theme;
      savePick(pick);
      apply(theme);
    });
  });
})();
