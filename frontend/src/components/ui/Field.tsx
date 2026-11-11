import { cloneElement, isValidElement, useId, type ReactElement, type ReactNode } from 'react'
import { cn } from '@/lib/cn'

type ControlProps = {
  id?: string
  'aria-describedby'?: string
  'aria-invalid'?: boolean
  'aria-required'?: boolean
}

/**
 * Etiket + kontrol + ipucu + hata. İpucu ve hata kontrolün `aria-describedby`'ına bağlanır.
 */
export function Field({
  label,
  hint,
  error,
  action,
  required,
  className,
  children,
}: {
  label: string
  hint?: ReactNode
  error?: string
  /** Etiketin sağındaki bağlantı (ör. "Şifremi unuttum"). */
  action?: ReactNode
  /** Zorunlu alan: etikette yıldız, kontrolde aria-required. */
  required?: boolean
  className?: string
  children: ReactElement<ControlProps>
}) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined

  // DOM sırası etiket → kontrol → yan bağlantı: Tab tuşu önce alana gider.
  // Görselde bağlantı etiket satırında durur (grid alanları).
  return (
    <div
      className={cn(
        'grid grid-cols-[minmax(0,1fr)_auto] gap-x-3 gap-y-1.5 [grid-template-areas:"label_action""control_control""msg_msg"]',
        className,
      )}
    >
      <label htmlFor={id} className="self-baseline text-md font-semibold text-ink [grid-area:label]">
        {label}
        {required && <span aria-hidden className="text-danger"> *</span>}
      </label>
      <div className="[grid-area:control]">
        {isValidElement(children)
          ? cloneElement(children, {
              id,
              'aria-describedby': describedBy,
              'aria-invalid': error ? true : undefined,
              'aria-required': required || undefined,
            })
          : children}
      </div>
      {action && <div className="self-baseline [grid-area:action]">{action}</div>}
      {hint && !error && (
        <p id={hintId} className="text-sm text-ink-3 [grid-area:msg]">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm font-semibold text-danger [grid-area:msg]">
          {error}
        </p>
      )}
    </div>
  )
}
