/* Navigation enhancement only; authentication and actions remain server-owned. */
(() => {
  'use strict';
  const current = window.location.pathname;
  document.querySelectorAll('.nav-links > a, .admin-nav a, .owner-section-links a').forEach(link => {
    const target = new URL(link.href, window.location.href);
    if (target.origin !== window.location.origin || target.hash) return;
    const exact = target.pathname === current;
    const section = target.pathname !== '/' && target.pathname !== '/register' && current.startsWith(target.pathname + '/');
    if (exact || section) { link.classList.add('sn-active'); if(exact) link.setAttribute('aria-current','page'); }
  });
})();
