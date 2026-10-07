import { createBrowserRouter, Outlet } from 'react-router'
import { AccountDetailPage } from '../features/accounts/AccountDetailPage'
import { AccountsPage } from '../features/accounts/AccountsPage'
import { LoginPage } from '../features/auth/LoginPage'
import { RegisterPage } from '../features/auth/RegisterPage'
import { DashboardPage } from '../features/dashboard/DashboardPage'
import { NotificationsPage } from '../features/notifications/NotificationsPage'
import { PaymentsPage } from '../features/payments/PaymentsPage'
import { SecurityPage } from '../features/security/SecurityPage'
import { TransferPage } from '../features/transfers/TransferPage'
import { AccountsAdminPage } from '../features/staff/AccountsAdminPage'
import { AuditPage } from '../features/staff/AuditPage'
import { CustomersPage } from '../features/staff/CustomersPage'
import { FxRatesPage } from '../features/staff/FxRatesPage'
import { StaffHomePage } from '../features/staff/StaffHomePage'
import { TeamPage } from '../features/staff/TeamPage'
import { NotFound, RequireAuth, RequireStaff } from './guards'

export const router = createBrowserRouter([
  { path: '/entrar', element: <LoginPage /> },
  { path: '/cadastro', element: <RegisterPage /> },
  {
    element: <RequireAuth />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/contas', element: <AccountsPage /> },
      { path: '/contas/:id', element: <AccountDetailPage /> },
      { path: '/transferir', element: <TransferPage /> },
      { path: '/pagar', element: <PaymentsPage /> },
      { path: '/notificacoes', element: <NotificationsPage /> },
      { path: '/seguranca', element: <SecurityPage /> },
      {
        element: <RequireStaff />,
        children: [
          { path: '/equipe', element: <StaffHomePage /> },
          { path: '/equipe/auditoria', element: <AuditPage /> },
          { path: '/equipe/clientes', element: <CustomersPage /> },
          { path: '/equipe/contas', element: <AccountsAdminPage /> },
          { path: '/equipe/cambio', element: <FxRatesPage /> },
          { path: '/equipe/usuarios', element: <TeamPage /> },
        ],
      },
    ],
  },
  { path: '*', element: <Outlet /> , children: [{ path: '*', element: <NotFound /> }] },
])
