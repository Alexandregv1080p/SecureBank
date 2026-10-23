import { Link } from 'react-router'
import { PageHeader } from '../../components/ui'
import { useAuth } from '../../stores/auth'
import { roleLabel, sectionsFor } from '../../lib/staff'

export function StaffHomePage() {
  const roles = useAuth((s) => s.claims?.roles ?? [])
  const sections = sectionsFor(roles)
  return (
    <>
      <PageHeader title="Painel da equipe" description={`Você entrou como ${roles.map(roleLabel).join(', ')}. Tudo o que você faz aqui fica na trilha de auditoria.`} />
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
