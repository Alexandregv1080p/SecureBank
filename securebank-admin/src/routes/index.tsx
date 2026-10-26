import { createBrowserRouter } from 'react-router'
import { LoginPage } from '../features/auth/LoginPage'
import { HomePage } from '../features/overview/HomePage'
import { SecurityPage } from '../features/security/SecurityPage'
import { AccountsAdminPage } from '../features/staff/AccountsAdminPage'
import { ApprovalsPage } from '../features/staff/ApprovalsPage'
import { AuditPage } from '../features/staff/AuditPage'
import { CustomersPage } from '../features/staff/CustomersPage'
import { FxRatesPage } from '../features/staff/FxRatesPage'
import { TeamPage } from '../features/staff/TeamPage'
import { NotFound, RequireAuth, RequireStaff } from './guards'

export const router = createBrowserRouter([
  { path: '/entrar', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <RequireStaff />,
        children: [
          { path: '/', element: <HomePage /> },
          { path: '/aprovacoes', element: <ApprovalsPage /> },
          { path: '/auditoria', element: <AuditPage /> },
          { path: '/clientes', element: <CustomersPage /> },
          { path: '/contas', element: <AccountsAdminPage /> },
          { path: '/cambio', element: <FxRatesPage /> },
          { path: '/usuarios', element: <TeamPage /> },
          { path: '/seguranca', element: <SecurityPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <NotFound /> },
])
