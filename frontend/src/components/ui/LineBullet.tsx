import { LINES, type LineKey } from '@/design/lines'
import { cn } from '@/lib/cn'

const sizes = {
  sm: 'size-5 text-xs',
  md: 'size-[26px] text-sm',
  lg: 'size-9 text-lg',
}

/**
 * Hat işareti: alanın rengi ve harfi, metro hattı gibi.
 * `label` verilmezse dekoratiftir; alan adı yanında zaten yazılı olmalı.
 */
export function LineBullet({
  line,
  size = 'md',
  label,
  className,
}: {
  line: LineKey
  size?: keyof typeof sizes
  label?: string
  className?: string
}) {
  const def = LINES[line]
  return (
    <span
      role={label ? 'img' : undefined}
      aria-label={label}
      aria-hidden={label ? undefined : true}
      className={cn(
        'inline-grid shrink-0 place-items-center rounded-full font-heavy leading-none',
        def.fill,
        sizes[size],
        className,
      )}
    >
      <span className="translate-y-[0.08em]">{def.letter}</span>
    </span>
  )
}
