import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router/dom'
import './index.css'
import { ApiError, refreshSession } from './lib/api'
import { router } from './routes'
import { useAuth } from './stores/auth'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      // Erro do servidor de verdade (4xx) não adianta repetir; rede e 5xx tentam uma vez a mais.
      retry: (count, error) => !(error instanceof ApiError && !error.uncertain) && count < 2,
    },
  },
})

// Recupera a sessão pelo cookie HttpOnly (o access token vive só em memória).
refreshSession().then((ok) => {
  if (!ok) useAuth.getState().clear()
})

// Sair em outra aba ou perder a sessão limpa o cache: nada de dados do usuário anterior na tela.
useAuth.subscribe((state, previous) => {
  if (state.status === 'anonymous' && previous.status === 'authenticated') queryClient.clear()
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
)
