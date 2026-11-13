/* Presentation only. Never submits, cancels or changes a business action. */
(() => {
  'use strict';
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  document.addEventListener('pointerdown', event => {
    const button = event.target.closest('.btn, .login-google');
    if (!button || button.disabled || button.getAttribute('aria-disabled') === 'true' || reduced.matches || (event.button !== undefined && event.button !== 0)) return;
    if (!Element.prototype.animate) return;
    const bounds = button.getBoundingClientRect();
    const ripple = document.createElement('span');
    ripple.className = 'sn-button-ripple';
    ripple.setAttribute('aria-hidden', 'true');
    const size = Math.max(bounds.width, bounds.height) * 2;
    Object.assign(ripple.style, {width:size+'px', height:size+'px',left:(event.clientX-bounds.left-size/2)+'px',top:(event.clientY-bounds.top-size/2)+'px'});
    button.append(ripple);
    const animation = ripple.animate([{transform:'scale(0)',opacity:.28},{transform:'scale(1)',opacity:0}], {duration:500,easing:'ease-out'});
    animation.finished.catch(() => {}).finally(() => ripple.remove());
  });
  if (!('IntersectionObserver' in window) || reduced.matches) return;
  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (!entry.isIntersecting) return;
      entry.target.classList.add('sn-enter');
      observer.unobserve(entry.target);
    });
  }, {threshold:.12});
  document.querySelectorAll('.sn-feature-banner,.login-sports-visual,.owner-stat-card,.admin-stats article,.account-shortcut').forEach(element => observer.observe(element));
})();
