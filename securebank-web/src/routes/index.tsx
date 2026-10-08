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
import { FxPage } from '../features/fx/FxPage'
import { FxTradePage } from '../features/fx/FxTradePage'
import { ApplyInvestmentPage } from '../features/investments/ApplyInvestmentPage'
import { InvestmentDetailPage } from '../features/investments/InvestmentDetailPage'
import { InvestmentsPage } from '../features/investments/InvestmentsPage'
import { NewPiggyPage } from '../features/piggies/NewPiggyPage'
import { PiggiesPage } from '../features/piggies/PiggiesPage'
import { PiggyDetailPage } from '../features/piggies/PiggyDetailPage'
import { PixChargesPage } from '../features/pix/PixChargesPage'
import { PixHistoryPage } from '../features/pix/PixHistoryPage'
import { PixHomePage } from '../features/pix/PixHomePage'
import { PixKeysPage } from '../features/pix/PixKeysPage'
import { PixLayout } from '../features/pix/PixLayout'
import { PixReceivePage } from '../features/pix/PixReceivePage'
import { PixSchedulesPage } from '../features/pix/PixSchedulesPage'
import { PixSendPage } from '../features/pix/PixSendPage'
import { NotFound, RequireAuth, RequireCustomer, RequireStaff } from './guards'

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
        element: <RequireCustomer />,
        children: [
          { path: '/cambio', element: <FxPage /> },
          { path: '/cambio/:side/:currency', element: <FxTradePage /> },
          { path: '/investimentos', element: <InvestmentsPage /> },
          { path: '/investimentos/aplicar/:code', element: <ApplyInvestmentPage /> },
          { path: '/investimentos/:id', element: <InvestmentDetailPage /> },
          { path: '/porquinhos', element: <PiggiesPage /> },
          { path: '/porquinhos/novo', element: <NewPiggyPage /> },
          { path: '/porquinhos/:id', element: <PiggyDetailPage /> },
          {
            path: '/pix',
            element: <PixLayout />,
            children: [
              { index: true, element: <PixHomePage /> },
              { path: 'enviar', element: <PixSendPage /> },
              { path: 'receber', element: <PixReceivePage /> },
              { path: 'cobrancas', element: <PixChargesPage /> },
              { path: 'agendados', element: <PixSchedulesPage /> },
              { path: 'chaves', element: <PixKeysPage /> },
              { path: 'historico', element: <PixHistoryPage /> },
            ],
          },
        ],
      },
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
