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
import { NotFound, RequireAuth } from './guards'

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
    ],
  },
  { path: '*', element: <Outlet /> , children: [{ path: '*', element: <NotFound /> }] },
])
