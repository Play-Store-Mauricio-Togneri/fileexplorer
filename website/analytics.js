// Reports visits to Google Analytics, in the website's own property apart from the app's, along
// with what Google's tag does not see on its own: which Play badge was clicked, which features
// scrolled into view, the language and theme picks, and addresses not found. Only the production
// address reports, so local previews and the emulator send nothing, and never a page the language
// redirect is leaving, which would count that visit twice. Without this script the site works the
// same, unmeasured.
(() => {
  // The website property's measurement ID, from its web stream's details in Google Analytics.
  // Empty, nothing is sent.
  const MEASUREMENT_ID = 'G-PNEPF5GEVR';
  const HOST = 'fileexplorer.org';
  const root = document.documentElement;
  if (!MEASUREMENT_ID || location.hostname !== HOST || 'leaving' in root.dataset) return;

  window.dataLayer = window.dataLayer || [];
  // Google's tag reads each call's arguments object, so this cannot be an arrow function.
  function gtag() {
    window.dataLayer.push(arguments);
  }
  // A visit the language redirect forwarded arrives with the page it left as its referrer, so
  // report the one language.js saved from that page instead.
  const config = {};
  try {
    const referrer = sessionStorage.getItem('referrer');
    sessionStorage.removeItem('referrer');
    if (referrer !== null && document.referrer && new URL(document.referrer).origin === location.origin) {
      config.page_referrer = referrer;
    }
  } catch {
    // Storage is blocked: language.js saved nothing either.
  }

  gtag('js', new Date());
  gtag('config', MEASUREMENT_ID, config);

  const tag = document.createElement('script');
  tag.async = true;
  tag.src = `https://www.googletagmanager.com/gtag/js?id=${MEASUREMENT_ID}`;
  document.head.append(tag);

  // Only the not-found page names no other languages, which is how language.js tells it apart.
  if (!document.querySelector('link[rel="alternate"][hreflang]')) {
    gtag('event', 'page_not_found', { path: location.pathname });
  }

  // Listening on the document runs after each control's own handler, so the theme has already
  // switched when its click arrives here.
  document.addEventListener('click', (event) => {
    const badge = event.target.closest('.play-badge');
    if (badge) {
      gtag('event', 'play_badge_click', { placement: badge.closest('.hero') ? 'hero' : 'closing' });
      return;
    }

    const language = event.target.closest('.language-link');
    if (language && language.hreflang !== root.lang) {
      gtag('event', 'language_select', { from: root.lang, to: language.hreflang });
      return;
    }

    if (event.target.closest('.theme-toggle')) {
      gtag('event', 'theme_select', { theme: root.dataset.theme });
    }
  });

  if (!('IntersectionObserver' in window)) return;

  // A feature counts as seen the first time it crosses the middle of the viewport, the band in
  // which script.js puts its screenshot on the pinned phone.
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (!entry.isIntersecting) continue;
      observer.unobserve(entry.target);
      gtag('event', 'feature_view', { feature: entry.target.getAttribute('aria-labelledby') });
    }
  }, { rootMargin: '-50% 0px -50% 0px' });

  document.querySelectorAll('.tour .feature').forEach((feature) => observer.observe(feature));
})();
