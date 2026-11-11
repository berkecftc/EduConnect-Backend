import { forwardRef, type TextareaHTMLAttributes } from 'react'
import { cn } from '@/lib/cn'

/**
 * Çok satırlı metin. `maxLength` verilirse altta kalan karakter sayısı görünür
 * (ekran okuyucu her tuşta anons etmesin diye aria-live yok; sınır `maxLength` ile zorlanır).
 */
export const Textarea = forwardRef<HTMLTextAreaElement, TextareaHTMLAttributes<HTMLTextAreaElement> & { valueLength?: number }>(
  function Textarea({ className, maxLength, valueLength, ...rest }, ref) {
    return (
      <div className="flex flex-col gap-1">
        <textarea
          ref={ref}
          maxLength={maxLength}
          className={cn(
            'min-h-28 w-full resize-y rounded-md border border-control bg-surface px-3 py-2.5 text-base text-ink',
            'placeholder:text-ink-3 transition-colors duration-150 hover:border-ink-2',
            'focus-visible:border-focus focus-visible:outline-2 focus-visible:outline-offset-0 aria-invalid:border-danger',
            className,
          )}
          {...rest}
        />
        {maxLength !== undefined && valueLength !== undefined && (
          <p aria-hidden className="tabular self-end text-xs text-ink-3">
            {valueLength.toLocaleString('tr-TR')} / {maxLength.toLocaleString('tr-TR')}
          </p>
        )}
      </div>
    )
  },
)
