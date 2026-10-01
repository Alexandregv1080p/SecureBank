import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Dev: o browser fala só com o Vite (mesma origem) e o proxy encaminha /api para a API — sem CORS.
// Produção: o nginx da imagem faz o mesmo papel (ver nginx.conf).
export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': process.env.VITE_API_PROXY ?? 'http://localhost:8080',
    },
  },
})
