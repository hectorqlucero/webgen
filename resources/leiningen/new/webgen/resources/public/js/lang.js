document.addEventListener('DOMContentLoaded', function(){
  var toggle = document.getElementById('languageDropdown');
  var items = Array.prototype.slice.call(document.querySelectorAll('[aria-labelledby=\"languageDropdown\"] .dropdown-item[data-locale]'));
  if(!items.length) return;
  function applyLocale(loc){
    var match = items.find(function(a){ return a.dataset && a.dataset.locale === loc; });
    items.forEach(function(a){ a.classList.toggle('active', a === match); });
    if(match && toggle){
      var flag = (match.querySelector('span') || {}).textContent || '';
      var name = match.textContent.replace(flag, '').trim();
      toggle.innerHTML = '<span class=\"me-2\">' + flag + '</span>' + name;
    }
  }
  try{ var stored = localStorage.getItem('locale'); if(stored) applyLocale(stored); }catch(e){}
  items.forEach(function(a){
    a.addEventListener('click', function(ev){
      try{ ev.preventDefault(); }catch(e){}
      var loc = a.dataset && a.dataset.locale;
      if(!loc) return;
      try{ localStorage.setItem('locale', loc); }catch(e){}
      applyLocale(loc);
    });
  });
});
