import type { ReactNode } from 'react'

export function DataList({ children }: { children: ReactNode }) {
  return <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-[10rem_1fr]">{children}</dl>
}

export function DataRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="contents">
      <dt className="text-muted">{label}</dt>
      <dd className="break-all">{children}</dd>
    </div>
  )
}
