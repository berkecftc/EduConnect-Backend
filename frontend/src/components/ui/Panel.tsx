import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'

/**
 * Bölüm: kalın üst çizgi ve büyük başlık, sağda bağlantı ya da eylem. Kutu yok.
 * Hiyerarşi boyutla kurulur: bölüm başlığı (36px) satır başlıklarının (18px) iki katı; içerik
 * başlığın altında ikinci bir çizgiyle başlamaz (ilk öğenin üst çizgisi kaldırılır), bölüm sınırı tek çizgidir.
 */
export function Panel({
  title,
  to,
  linkLabel = 'Tümü',
  action,
  children,
  className,
  anchor,
}: {
  title: string
  to?: string
  linkLabel?: string
  action?: ReactNode
  children: ReactNode
  className?: string
  /** Sayfa içi bağlantı için bölümün kimliği (ör. bekleyen işler şeridinden kaydırma). */
  anchor?: string
}) {
  const id = `bolum-${title.replace(/\s+/g, '-').toLocaleLowerCase('tr-TR')}`
  return (
    <section id={anchor} aria-labelledby={id} className={cn('scroll-mt-24 border-t-2 border-ink pt-5', className)}>
      <div className="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
        <h2 id={id} className="text-3xl font-heavy text-balance">
          {title}
        </h2>
        {action}
        {!action && to && (
          <Link to={to} className="text-md font-semibold underline-offset-4 hover:underline">
            {linkLabel}
          </Link>
        )}
      </div>
      <div className="mt-6 [&>*:first-child]:border-t-0">{children}</div>
    </section>
  )
}
