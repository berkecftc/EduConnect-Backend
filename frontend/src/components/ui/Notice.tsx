import type { ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info, XCircle } from 'lucide-react'
import { cn } from '@/lib/cn'

type Tone = 'info' | 'success' | 'warning' | 'danger'

const tones: Record<Tone, { box: string; icon: typeof Info; iconColor: string }> = {
  info: { box: 'bg-sunken border-rule', icon: Info, iconColor: 'text-ink-2' },
  success: { box: 'bg-success-soft border-success/40', icon: CheckCircle2, iconColor: 'text-success' },
  warning: { box: 'bg-warning-soft border-warning/40', icon: AlertTriangle, iconColor: 'text-warning' },
  danger: { box: 'bg-danger-soft border-danger/40', icon: XCircle, iconColor: 'text-danger' },
}

const lineColor: Record<Tone, string> = {
  info: 'border-ink-3',
  success: 'border-success',
  warning: 'border-warning',
  danger: 'border-danger',
}

/**
 * Sayfa içi bildirim. Hata tonunda `role="alert"`, diğerlerinde `status`.
 * `variant="line"`: kutusuz, yalnız soldaki tonlu çizgi (uygulama içi tarife düzeni).
 */
export function Notice({
  tone = 'info',
  variant = 'box',
  title,
  children,
  action,
  className,
}: {
  tone?: Tone
  variant?: 'box' | 'line'
  title?: string
  children?: ReactNode
  action?: ReactNode
  className?: string
}) {
  const { box, icon: Icon, iconColor } = tones[tone]
  if (variant === 'line') {
    return (
      <div role={tone === 'danger' ? 'alert' : 'status'} className={cn('border-l-[3px] py-0.5 pl-4 text-md text-ink', lineColor[tone], className)}>
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={cn(title ? 'mt-0.5 text-ink-2' : 'text-ink-2')}>{children}</div>}
        {action && <div className="mt-2.5">{action}</div>}
      </div>
    )
  }
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={cn('flex gap-3 rounded-md border px-3.5 py-3 text-md text-ink', box, className)}
    >
      <Icon className={cn('mt-0.5 size-[18px] shrink-0', iconColor)} aria-hidden />
      <div className="min-w-0 flex-1">
        {title && <p className="font-semibold">{title}</p>}
        {children && <div className={cn(title && 'mt-0.5 text-ink-2')}>{children}</div>}
        {action && <div className="mt-2.5">{action}</div>}
      </div>
    </div>
  )
}
