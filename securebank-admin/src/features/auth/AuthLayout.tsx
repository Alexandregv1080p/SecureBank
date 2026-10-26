import type { ReactNode } from 'react'
import { LockKey, ShieldCheck } from '@phosphor-icons/react'

const points = [
  'Acesso só para a equipe, com verificação em duas etapas.',
  'Cada papel vê e altera apenas o que precisa.',
  'Tudo o que você faz fica na trilha de auditoria.',
]

/** Duas colunas no desktop; no celular o formulário vem primeiro e o painel some. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="min-h-dvh border-t-[3px] border-accent lg:grid lg:grid-cols-[5fr_4fr]">
      <section className="flex items-center px-4 py-10 sm:px-10 lg:px-20">
        <div className="mx-auto w-full max-w-md">
          <p className="mb-10 flex items-center gap-3">
            <span className="grid size-9 place-items-center rounded-lg bg-accent text-accent-fg">
              <ShieldCheck size={20} weight="fill" />
            </span>
            <span className="leading-tight">
              <span className="block text-lg font-semibold tracking-tight">SecureBank</span>
              <span className="block text-[11px] font-semibold uppercase tracking-[0.18em] text-accent">Painel administrativo</span>
            </span>
          </p>
          <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
          <p className="mt-2 text-sm text-muted">{subtitle}</p>
          <div className="mt-8">{children}</div>
        </div>
      </section>
      <aside className="hidden border-l border-line bg-panel px-16 py-20 lg:flex lg:flex-col lg:justify-end">
        <span className="mb-6 grid size-12 place-items-center rounded-lg bg-accent-soft text-accent">
          <LockKey size={26} weight="duotone" />
        </span>
        <h2 className="max-w-md text-3xl font-semibold leading-tight tracking-tight">Área restrita da equipe do banco.</h2>
        <ul className="mt-8 flex max-w-md flex-col gap-4 text-base text-muted">
          {points.map((p) => (
            <li key={p} className="border-l-2 border-accent pl-4">
              {p}
            </li>
          ))}
        </ul>
        <p className="mt-10 text-xs text-muted">Se você é cliente, o internet banking é outro endereço.</p>
      </aside>
    </div>
  )
}
