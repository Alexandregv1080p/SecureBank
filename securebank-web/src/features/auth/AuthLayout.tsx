import type { ReactNode } from 'react'

const points = [
  'Verificação em duas etapas com aplicativo autenticador.',
  'Você vê e encerra cada sessão aberta na sua conta.',
  'Toda operação financeira fica registrada e pode ser auditada.',
]

/** Duas colunas no desktop; no celular o formulário vem primeiro e o painel some. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[5fr_4fr]">
      <section className="flex items-center px-4 py-10 sm:px-10 lg:px-20">
        <div className="mx-auto w-full max-w-md">
          <p className="mb-10 text-lg font-semibold tracking-tight">SecureBank</p>
          <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-2 text-sm text-muted">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
      <aside className="hidden bg-accent px-16 py-20 text-accent-fg lg:flex lg:flex-col lg:justify-end">
        <h2 className="max-w-md text-4xl font-semibold leading-tight tracking-tight">Banco digital feito para ser auditável.</h2>
        <ul className="mt-8 flex max-w-md flex-col gap-4 text-base opacity-90">
          {points.map((p) => (
            <li key={p}>{p}</li>
          ))}
        </ul>
      </aside>
    </div>
  )
}
