import { cn } from '@/lib/cn'

/**
 * Aktarma işareti: dört alanın kolu (solda Dersler, üstte Kulüpler, sağda Etkinlikler, altta Topluluk)
 * ortadaki halkada buluşur. Giriş ekranındaki 3D işaretle aynı biçim.
 */
export function Mark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 28 28" className={cn('size-7', className)} aria-hidden="true">
      <rect x="1" y="11.5" width="12" height="5" rx="2.5" fill="var(--ec-ders)" />
      <rect x="11.5" y="1" width="5" height="12" rx="2.5" fill="var(--ec-kulup)" />
      <rect x="15" y="11.5" width="12" height="5" rx="2.5" fill="var(--ec-etkinlik)" />
      <rect x="11.5" y="15" width="5" height="12" rx="2.5" fill="var(--ec-topluluk)" />
      <circle cx="14" cy="14" r="6" fill="var(--ec-surface)" stroke="var(--ec-ink)" strokeWidth="3" />
    </svg>
  )
}

export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-2 text-ink', className)}>
      <Mark />
      <span className="text-lg font-heavy tracking-[-0.02em]">EduConnect</span>
    </span>
  )
}
