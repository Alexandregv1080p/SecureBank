// Aplica o tema salvo antes da primeira pintura (sem piscar). Arquivo externo porque a CSP não permite script inline.
try {
  var saved = localStorage.getItem('sb-theme')
  document.documentElement.dataset.theme = saved === 'light' ? 'light' : 'dark'
} catch {
  document.documentElement.dataset.theme = 'dark'
}
