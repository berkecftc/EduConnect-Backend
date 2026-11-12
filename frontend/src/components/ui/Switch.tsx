import { Switch as RSwitch } from 'radix-ui'
import { cn } from '@/lib/cn'

/**
 * Açık/kapalı anahtarı (Radix Switch): Boşluk tuşuyla değişir, ekran okuyucuya durumunu söyler.
 * Dokunma alanı 44px; kilitliyken (`disabled`) neden kilitli olduğu yanında yazmalıdır.
 */
export function Switch({
  checked,
  onCheckedChange,
  disabled,
  label,
  className,
}: {
  checked: boolean
  onCheckedChange: (v: boolean) => void
  disabled?: boolean
  /** Görünür etiket yoksa ekran okuyucu adı. */
  label?: string
  className?: string
}) {
  return (
    <RSwitch.Root
      checked={checked}
      onCheckedChange={onCheckedChange}
      disabled={disabled}
      aria-label={label}
      className={cn(
        'group relative inline-flex h-11 w-14 shrink-0 cursor-pointer items-center disabled:cursor-not-allowed',
        'focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-focus',
        className,
      )}
    >
      <span
        aria-hidden
        className="absolute inset-x-0 h-6 rounded-sm border border-control bg-sunken transition-colors duration-150 group-data-[state=checked]:border-ink group-data-[state=checked]:bg-ink group-disabled:opacity-50"
      />
      <RSwitch.Thumb className="relative ml-1 block size-4 rounded-xs bg-ink-2 transition-transform duration-200 ease-rail data-[state=checked]:translate-x-8 data-[state=checked]:bg-canvas" />
    </RSwitch.Root>
  )
}
