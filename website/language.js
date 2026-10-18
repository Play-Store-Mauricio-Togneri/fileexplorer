// Sends visitors to the page in their language before it is drawn, and runs the header's language
// menu. A language picked in the menu applies on every page. Without a pick, the browser's
// preferred languages only move visitors from an English page to the same page in their language,
// never back: search engines browse in English, and must still reach every translated page. Picking
// the browser's own language clears the pick, so the site follows the browser again. Without this
// script every page stays in the language of its address.
(() => {
  const STORAGE_KEY = 'language';
  // Where analytics.js finds the referrer of a page this script redirected from.
  const REFERRER_KEY = 'referrer';
  const LANGUAGES = ['en', 'es', 'fr'];
  const DEFAULT_LANGUAGE = 'en';
  // The menu button's label on the not-found page, which other pages carry in their markup.
  const MENU_LABELS = { en: 'Language', es: 'Idioma', fr: 'Langue' };
  const root = document.documentElement;

  // Throws when storage is blocked.
  const loadPick = () => {
    const language = localStorage.getItem(STORAGE_KEY);
    return LANGUAGES.includes(language) ? language : null;
  };

  const savePick = (language) => {
    try {
      if (language) localStorage.setItem(STORAGE_KEY, language);
      else localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Storage is blocked: the page's address is the only pick there is.
    }
  };

  // The first of the browser's preferred languages the site is written in, in any region.
  const browserLanguage = () => {
    for (const tag of navigator.languages ?? [navigator.language]) {
      const language = tag?.split('-')[0].toLowerCase();
      if (LANGUAGES.includes(language)) return language;
    }
    return DEFAULT_LANGUAGE;
  };

  // The language an address on this site names with its prefix (/es/…), or null for none.
  const prefixLanguage = (pathname) => LANGUAGES.find((language) => language !== DEFAULT_LANGUAGE
    && (pathname === `/${language}` || pathname.startsWith(`/${language}/`))) ?? null;

  // The same page in each language, from the links that tell search engines about it.
  const counterparts = {};
  for (const link of document.querySelectorAll('link[rel="alternate"][hreflang]')) {
    counterparts[link.hreflang] = new URL(link.href).pathname;
  }
  // Only the not-found page has none: it is the same page at every address.
  const notFound = Object.keys(counterparts).length === 0;

  let pick = null;
  let storage = true;
  try {
    pick = loadPick();
  } catch {
    storage = false;
  }

  if (notFound) {
    // The not-found page, which Firebase serves at any missing address, carries every language and
    // shows one: the address's, else the visitor's.
    root.lang = prefixLanguage(location.pathname) ?? pick ?? browserLanguage();
  } else {
    // Back and Forward show the page that was left, or they could never return to another
    // language.
    const navigation = performance.getEntriesByType?.('navigation')[0];
    const revisit = navigation?.type === 'back_forward';

    // Arriving from this site's page in another language is following the language menu, however
    // the link was opened: its click handler misses a new tab opened by middle-click or menu.
    const referrer = document.referrer ? new URL(document.referrer) : null;
    const from = referrer?.origin === location.origin
      ? prefixLanguage(referrer.pathname) ?? DEFAULT_LANGUAGE
      : null;
    if (!revisit && from && from !== root.lang) {
      pick = root.lang === browserLanguage() ? null : root.lang;
      savePick(pick);
    }

    // Without storage a pick could not be kept, so the browser's language must not override the
    // address either: a visitor who picked English would be sent straight back to Spanish.
    const wanted = pick ?? (storage && root.lang === DEFAULT_LANGUAGE ? browserLanguage() : null);
    const target = wanted && wanted !== root.lang ? counterparts[wanted] : null;
    if (target && !revisit) {
      // Tells analytics.js not to count a page the visitor never sees, and which page sent them
      // here: the next page's referrer will be this one.
      root.dataset.leaving = '';
      try {
        sessionStorage.setItem(REFERRER_KEY, document.referrer);
      } catch {
        // Storage is blocked: the visit is reported as coming from this page.
      }
      location.replace(target + location.search + location.hash);
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    const menu = document.querySelector('.language-menu');

    if (notFound) {
      // The tab title and the menu, in the language the page shows.
      const title = document.querySelector(`[lang="${root.lang}"][data-title]`);
      if (title) document.title = title.dataset.title;
      const toggle = menu?.querySelector('.language-toggle');
      toggle?.setAttribute('aria-label', MENU_LABELS[root.lang]);
      if (toggle) toggle.title = MENU_LABELS[root.lang];
      menu?.querySelector(`.language-link[hreflang="${root.lang}"]`)?.setAttribute('aria-current', 'true');
    }

    for (const link of document.querySelectorAll('.language-link')) {
      link.addEventListener('click', () => {
        const language = link.hreflang;
        savePick(language === browserLanguage() ? null : language);
      });
    }

    if (!menu) return;

    // The menu closes when the visitor clicks elsewhere, presses Escape or tabs out of it.
    document.addEventListener('click', (event) => {
      if (menu.open && !menu.contains(event.target)) menu.open = false;
    });
    menu.addEventListener('keydown', (event) => {
      if (event.key !== 'Escape' || !menu.open) return;
      menu.open = false;
      menu.querySelector('.language-toggle').focus();
    });
    menu.addEventListener('focusout', (event) => {
      if (event.relatedTarget && !menu.contains(event.relatedTarget)) menu.open = false;
    });

    // Back and Forward can restore a page with the menu still open from picking a language.
    window.addEventListener('pageshow', (event) => {
      if (event.persisted) menu.open = false;
    });
  });
})();
