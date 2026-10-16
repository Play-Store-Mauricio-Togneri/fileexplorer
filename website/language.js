// Sends visitors to the page in their language before it is drawn, and runs the header's language
// link. A language picked with the link applies on every page. Without a pick, the browser's
// preferred languages only move visitors from an English page to its Spanish one, never back:
// search engines browse in English, and must still reach every Spanish page. Picking the browser's
// own language clears the pick, so the site follows the browser again. Without this script every
// page stays in the language of its address.
(() => {
  const STORAGE_KEY = 'language';
  const LANGUAGES = ['en', 'es'];
  const DEFAULT_LANGUAGE = 'en';
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

  let pick = null;
  let storage = true;
  try {
    pick = loadPick();
  } catch {
    storage = false;
  }

  if (Object.keys(counterparts).length === 0) {
    // The not-found page, which Firebase serves at any missing address, carries every language and
    // shows one: the address's, else the visitor's.
    root.lang = prefixLanguage(location.pathname) ?? pick ?? browserLanguage();
  } else {
    // Back and Forward show the page that was left, or they could never return to the other
    // language.
    const navigation = performance.getEntriesByType?.('navigation')[0];
    const revisit = navigation?.type === 'back_forward';

    // Arriving from this site's page in the other language is following the language link, however
    // it was opened: the link's click handler misses a new tab opened by middle-click or menu.
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
      location.replace(target + location.search + location.hash);
    }
  }

  document.addEventListener('DOMContentLoaded', () => {
    // The not-found page's tab title, in the language it shows.
    const title = document.querySelector(`[lang="${root.lang}"][data-title]`);
    if (title) document.title = title.dataset.title;

    for (const link of document.querySelectorAll('.language-link')) {
      link.addEventListener('click', () => {
        const language = link.hreflang;
        savePick(language === browserLanguage() ? null : language);
      });
    }
  });
})();
