// Points the pinned phone at the feature crossing the middle of the screen, and marks that feature
// so the text beside the phone matches the screenshot it shows. Without this script the phone stays
// on its first screenshot and every feature's text stays at full ink.
(() => {
  const tour = document.querySelector('.tour');
  if (!tour || !('IntersectionObserver' in window)) return;

  const features = [...tour.querySelectorAll('.feature')];
  const screens = [...tour.querySelectorAll('.tour-phone .screen')];

  const show = (feature) => {
    for (const each of features) each.classList.toggle('is-current', each === feature);
    for (const screen of screens) screen.classList.toggle('is-shown', screen.dataset.screen === feature.dataset.screen);
  };

  // A zero-height band across the middle of the viewport: a feature intersects it while it is the
  // one beside the pinned phone.
  const observer = new IntersectionObserver((entries) => {
    for (const entry of entries) {
      if (entry.isIntersecting) show(entry.target);
    }
  }, { rootMargin: '-50% 0px -50% 0px' });

  features.forEach((feature) => observer.observe(feature));
  show(features[0]);
  tour.classList.add('is-live');
})();
