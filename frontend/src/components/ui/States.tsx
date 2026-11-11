import type { ReactNode } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { toApiError } from '@/lib/api/problem'
import { cn } from '@/lib/cn'
import { Button } from './Button'
import { Notice } from './Notice'
import { RevealTitle } from './RevealTitle'

/** Yükleniyor iskeleti: içeriğin yerini tutan sakin satırlar (yerleşim kaymasın). */
export function Skeleton({ rows = 3, className }: { rows?: number; className?: string }) {
  return (
    <div role="status" aria-label="Yükleniyor" className={cn('flex flex-col gap-3', className)}>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="h-14 animate-pulse rounded-md bg-sunken motion-reduce:animate-none" />
      ))}
    </div>
  )
}

/** Boş durum: ne olduğunu söyler, varsa yapılacak işi önerir. */
export function EmptyState({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="rounded-md border border-dashed border-rule-strong px-5 py-8">
      <p className="font-semibold text-ink">{title}</p>
      {children && <p className="mt-1 max-w-[60ch] text-md text-ink-2">{children}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}

/**
 * Sorgu durumu: yüklenirken iskelet, hatada mesaj + tekrar dene, veri gelince içerik.
 * 403/404 gibi kalıcı hatalarda tekrar dene düğmesi gösterilmez.
 */
export function QueryBoundary<T>({
  query,
  children,
  skeletonRows,
  what,
}: {
  query: UseQueryResult<T>
  children: (data: T) => ReactNode
  skeletonRows?: number
  /** Hata başlığında kullanılır: "Dersler yüklenemedi". */
  what: string
}) {
  if (query.isPending) return <Skeleton rows={skeletonRows} />
  if (query.isError) {
    const e = toApiError(query.error)
    const retryable = e.status === 0 || e.status >= 500 || e.status === 429
    return (
      <Notice
        tone={retryable ? 'warning' : 'danger'}
        title={`${what} yüklenemedi`}
        action={
          retryable ? (
            <Button size="sm" onClick={() => void query.refetch()}>
              Tekrar dene
            </Button>
          ) : undefined
        }
      >
        {e.message}
      </Notice>
    )
  }
  return <>{children(query.data)}</>
}

/** Sayfa başlığı: dev, maskeden kayarak gelen başlık; kısa bağlam satırı ve sağda eylemler. */
export function PageHeader({ title, meta, actions, children }: { title: ReactNode; meta?: ReactNode; actions?: ReactNode; children?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-6 pb-2">
      <div className="min-w-0">
        {typeof title === 'string' ? <RevealTitle text={title} className="text-5xl sm:text-6xl" /> : <h1 className="text-5xl">{title}</h1>}
        {meta && <div className="mt-4 text-lg text-ink-2">{meta}</div>}
        {children}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </header>
  )
}
