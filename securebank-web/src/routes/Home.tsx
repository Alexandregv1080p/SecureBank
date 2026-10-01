import { ApiStatusCard } from '../features/system/ApiStatusCard'

export function Home() {
  return (
    <main className="mx-auto max-w-3xl px-4 py-16">
      <header className="mb-10 flex items-center gap-3">
        <svg viewBox="0 0 24 24" className="size-8 text-blue-600" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden>
          <path d="M12 3 4 6v6c0 4.5 3.2 7.7 8 9 4.8-1.3 8-4.5 8-9V6l-8-3Z" strokeLinejoin="round" />
          <path d="m9 12 2 2 4-4" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
        <h1 className="text-2xl font-semibold tracking-tight">SecureBank</h1>
      </header>
      <ApiStatusCard />
    </main>
  )
}
