document.addEventListener('DOMContentLoaded', function () {
  var mainNav = document.getElementById('mainNavbar');
  if (!mainNav) return;
  mainNav.querySelectorAll('.nav-link').forEach(function (link) {
    link.addEventListener('mousedown', function (e) {
      mainNav.querySelectorAll('.nav-link').forEach(function (el) {
        el.classList.remove('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
      });
      this.classList.add('active', 'bg-gradient', 'text-primary-emphasis', 'shadow-sm');
    });
  });
});
