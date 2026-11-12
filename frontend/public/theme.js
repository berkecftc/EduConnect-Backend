// Tema tercihini ilk boyamadan önce uygula (yanıp sönmeyi önler). Ayrı dosya: CSP satır içi betiğe izin vermez (F-05).
try {
  var t = localStorage.getItem('ec-theme')
  if (t === 'light' || t === 'dark') document.documentElement.dataset.theme = t
} catch (e) {}
