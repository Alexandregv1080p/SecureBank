import { Link } from 'react-router'

export function NotFound() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-16">
      <h1 className="text-2xl font-semibold">Página não encontrada</h1>
      <Link to="/" className="mt-4 inline-block text-blue-600 hover:underline">
        Voltar ao início
      </Link>
    </main>
  )
}
