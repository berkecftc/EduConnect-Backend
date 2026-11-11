import { forwardRef, useState, type InputHTMLAttributes } from 'react'
import { Eye, EyeOff } from 'lucide-react'
import { cn } from '@/lib/cn'

const inputClass = cn(
  'h-11 w-full rounded-md border border-control bg-surface px-3 text-base text-ink',
  'placeholder:text-ink-3 transition-colors duration-150',
  'hover:border-ink-2 focus-visible:border-focus focus-visible:outline-2 focus-visible:outline-offset-0',
  'aria-invalid:border-danger disabled:cursor-not-allowed disabled:bg-sunken disabled:text-ink-3',
)

export const Input = forwardRef<HTMLInputElement, InputHTMLAttributes<HTMLInputElement>>(function Input(
  { className, ...rest },
  ref,
) {
  return <input ref={ref} className={cn(inputClass, className)} {...rest} />
})

export const PasswordInput = forwardRef<HTMLInputElement, Omit<InputHTMLAttributes<HTMLInputElement>, 'type'>>(
  function PasswordInput({ className, ...rest }, ref) {
    const [visible, setVisible] = useState(false)
    return (
      <div className="relative">
        <input ref={ref} type={visible ? 'text' : 'password'} className={cn(inputClass, 'pr-12', className)} {...rest} />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? 'Şifreyi gizle' : 'Şifreyi göster'}
          aria-pressed={visible}
          className="absolute inset-y-0 right-0 grid w-11 place-items-center rounded-r-md text-ink-3 hover:text-ink"
        >
          {visible ? <EyeOff className="size-[18px]" aria-hidden /> : <Eye className="size-[18px]" aria-hidden />}
        </button>
      </div>
    )
  },
)
