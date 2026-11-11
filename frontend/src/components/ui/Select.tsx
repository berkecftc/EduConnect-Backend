import { forwardRef, type SelectHTMLAttributes } from 'react'
import { ChevronDown } from 'lucide-react'
import { cn } from '@/lib/cn'

/**
 * Yerel seçim kutusu: mobilde sistem seçicisini açar, ekran okuyucularda en tutarlı davranan seçenek.
 * Uzun ve aranması gereken listeler için ileride ayrı bir Combobox gelecek.
 */
export const Select = forwardRef<HTMLSelectElement, SelectHTMLAttributes<HTMLSelectElement> & { placeholder?: string }>(
  function Select({ className, children, placeholder, ...rest }, ref) {
    return (
      <div className="relative">
        <select
          ref={ref}
          className={cn(
            'h-11 w-full appearance-none rounded-md border border-control bg-surface pr-10 pl-3 text-base text-ink',
            'transition-colors duration-150 hover:border-ink-2',
            'focus-visible:border-focus focus-visible:outline-2 focus-visible:outline-offset-0',
            'aria-invalid:border-danger disabled:cursor-not-allowed disabled:bg-sunken disabled:text-ink-3',
            className,
          )}
          {...rest}
        >
          {placeholder !== undefined && (
            <option value="" disabled>
              {placeholder}
            </option>
          )}
          {children}
        </select>
        <ChevronDown aria-hidden className="pointer-events-none absolute top-1/2 right-3 size-4 -translate-y-1/2 text-ink-3" />
      </div>
    )
  },
)
