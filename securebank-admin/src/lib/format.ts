const date = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', year: 'numeric' })
const dateTime = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' })

export const formatDate = (iso: string) => date.format(new Date(iso))
export const formatDateTime = (iso: string) => dateTime.format(new Date(iso))

export const accountTypeLabel = (type: string) => (type === 'CHECKING' ? 'Conta corrente' : 'Poupança')
export const accountLabel = (a: { branch: string; accountNumber: string }) => `Ag. ${a.branch}, conta ${a.accountNumber}`
