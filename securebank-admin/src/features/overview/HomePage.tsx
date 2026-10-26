import { Link } from 'react-router'
import { PageHeader } from '../../components/ui'
import { can, positionLabel, roleLabel, sectionsFor } from '../../lib/staff'
import { useAuth } from '../../stores/auth'
import { OverviewPage } from './OverviewPage'

/** Quem pode ler o painel (Diretoria, Financeiro, Auditoria) abre na visão gerencial; os demais abrem nos atalhos das telas deles. */
export function HomePage() {
  const roles = useAuth((s) => s.claims?.roles ?? [])
  const position = useAuth((s) => positionLabel(s.claims?.dept, s.claims?.lvl))
  if (can(roles, 'VIEW_REPORTS')) return <OverviewPage />
  const sections = sectionsFor(roles)
  return (
    <>
      <PageHeader title="Painel da equipe" description={`Você entrou como ${position ?? roles.map(roleLabel).join(', ')}. Tudo o que você faz aqui fica na trilha de auditoria.`} />
      <ul className="grid gap-4 sm:grid-cols-2">
        {sections.map((s) => (
          <li key={s.to}>
            <Link to={s.to} className="block h-full card overflow-hidden p-5 transition hover:bg-surface-2">
              <p className="font-medium">{s.label}</p>
              <p className="mt-1 text-sm text-muted">{s.description}</p>
            </Link>
          </li>
        ))}
      </ul>
    </>
  )
}
