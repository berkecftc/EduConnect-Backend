import type { ReactNode } from 'react'
import { cn } from '@/lib/cn'

export type BadgeTone = 'neutral' | 'info' | 'warning' | 'danger' | 'success'

const text: Record<BadgeTone, string> = {
  neutral: 'text-ink-2',
  info: 'text-ders',
  warning: 'text-warning',
  danger: 'text-danger',
  success: 'text-success',
}

const mark: Record<BadgeTone, string> = {
  neutral: 'bg-ink-3',
  info: 'bg-ders',
  warning: 'bg-warning',
  danger: 'bg-danger',
  success: 'bg-success',
}

/**
 * Durum: kutusuz, tarife tablosundaki gibi kısa metin; önünde tonunu taşıyan küçük kare.
 * Renk tek başına anlam taşımaz: metin her zaman durumu söyler.
 */
export function Badge({ tone = 'neutral', children, className }: { tone?: BadgeTone; children: ReactNode; className?: string }) {
  return (
    <span className={cn('inline-flex shrink-0 items-center gap-1.5 text-sm font-semibold whitespace-nowrap', text[tone], className)}>
      <span aria-hidden className={cn('size-1.5', mark[tone])} />
      {children}
    </span>
  )
}
