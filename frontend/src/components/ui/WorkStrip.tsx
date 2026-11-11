import { Link } from 'react-router-dom'
import { useReducedMotion } from 'motion/react'
import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/cn'

/** `anchor`: sayfadaki bölüme kaydırır; `to`: başka sayfaya gider. `value` undefined ise yükleniyor ("–"). */
export type WorkItem = {
  key: string
  value: number | undefined
  label: string
  note?: string
  urgent: boolean
  anchor?: string
  to?: string
}

/**
 * Bekleyen işler şeridi: sayfanın ilk satırı "ne yapmam gerekiyor?" sorusunu cevaplar. Dev rakam, kısa etiket;
 * acil olanlar uyarı renginde. Kutusuz; hücreler ince çizgiyle ayrılır.
 */
export function WorkStrip({ items, label = 'Bekleyen işler' }: { items: WorkItem[]; label?: string }) {
  const reduced = useReducedMotion()
  if (items.length === 0) return null
  const wide = items.length >= 4
  return (
    <nav
      aria-label={label}
      className={cn(
        'grid border-b border-rule',
        wide ? 'sm:grid-cols-2 lg:grid-cols-4' : items.length === 3 ? 'sm:grid-cols-3' : 'sm:grid-cols-2',
      )}
    >
      {items.map((it, i) => {
        const cls = cn(
          'row-fill group flex flex-col items-start px-1 py-5 text-left sm:px-5',
          i > 0 && 'border-t border-rule',
          wide ? i > 0 && 'lg:border-t-0 lg:border-l' : i > 0 && 'sm:border-t-0 sm:border-l',
          wide && i === 2 && 'sm:border-l-0 lg:border-l',
          wide && i === 1 && 'sm:border-t-0 sm:border-l',
          wide && i === 3 && 'sm:border-l',
          i === 0 && 'sm:pl-1',
        )
        const body = (
          <>
            <span
              className={cn('tabular text-5xl leading-none font-heavy', it.urgent ? 'text-warning' : it.value ? 'text-ink' : 'text-ink-3')}
            >
              {it.value === undefined ? '–' : formatNumber(it.value)}
            </span>
            <span className="mt-2 text-md font-semibold group-hover:underline group-hover:underline-offset-4">{it.label}</span>
            {it.note && <span className="text-sm text-ink-2">{it.note}</span>}
          </>
        )
        return it.to ? (
          <Link key={it.key} to={it.to} className={cls}>
            {body}
          </Link>
        ) : (
          <button
            key={it.key}
            type="button"
            onClick={() =>
              it.anchor && document.getElementById(it.anchor)?.scrollIntoView({ behavior: reduced ? 'auto' : 'smooth', block: 'start' })
            }
            className={cls}
          >
            {body}
          </button>
        )
      })}
    </nav>
  )
}
