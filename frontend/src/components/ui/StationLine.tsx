import { X } from 'lucide-react'
import { motion } from 'motion/react'
import { LINES, type LineKey } from '@/design/lines'
import { transition } from '@/design/motion'
import { cn } from '@/lib/cn'

export type StationState = 'done' | 'current' | 'pending' | 'rejected'

export type Station = {
  label: string
  state: StationState
  /** Kısa ayrıntı: kim, ne zaman. */
  detail?: string
}

const SR_STATE: Record<StationState, string> = {
  done: 'tamamlandı',
  current: 'şu anda bu adımda',
  pending: 'sırada',
  rejected: 'reddedildi',
}

/**
 * Durak çizgisi: onay zincirleri ve çok adımlı süreçler (başvuru → başkan → danışman → yayın).
 * Dar ekranda dikey, `sm` ve üstünde yatay. Ekran okuyucu için sıralı liste.
 */
export function StationLine({
  line,
  stations,
  label,
  className,
}: {
  line: LineKey
  stations: Station[]
  /** Listenin erişilebilir adı, ör. "Bahar şenliği standı onay durumu". */
  label: string
  className?: string
}) {
  const color = LINES[line].stroke
  return (
    <ol aria-label={label} className={cn('grid sm:grid-flow-col sm:auto-cols-fr', className)}>
      {stations.map((s, i) => {
        const next = stations[i + 1]
        const travelled = next && next.state !== 'pending'
        return (
          <li
            key={`${s.label}-${i}`}
            aria-current={s.state === 'current' ? 'step' : undefined}
            className="relative flex gap-3 pb-5 last:pb-0 sm:flex-col sm:items-center sm:gap-2 sm:pb-0 sm:text-center"
          >
            {next && (
              <span
                aria-hidden
                className="absolute top-5 bottom-0 left-[8px] w-1 rounded-full sm:top-2 sm:bottom-auto sm:left-1/2 sm:h-1 sm:w-full"
                style={{ background: travelled ? color : 'var(--ec-line-off)' }}
              />
            )}
            <Dot state={s.state} color={color} />
            <div className="min-w-0 pt-px sm:px-1">
              <p
                className={cn(
                  'text-sm',
                  s.state === 'current' ? 'font-semibold text-ink' : 'text-ink-2',
                  s.state === 'rejected' && 'font-semibold text-danger',
                )}
              >
                {s.label}
                <span className="sr-only">, {SR_STATE[s.state]}</span>
              </p>
              {s.detail && <p className="tabular text-xs text-ink-3">{s.detail}</p>}
            </div>
          </li>
        )
      })}
    </ol>
  )
}

function Dot({ state, color }: { state: StationState; color: string }) {
  if (state === 'rejected') {
    return (
      <span aria-hidden className="relative z-10 grid size-5 shrink-0 place-items-center rounded-full bg-danger text-surface dark:text-canvas">
        <X className="size-3" strokeWidth={3} />
      </span>
    )
  }
  if (state === 'current') {
    return (
      <span aria-hidden className="relative z-10 grid size-5 shrink-0 place-items-center">
        <motion.span
          className="absolute inset-[-5px] rounded-full border-2"
          style={{ borderColor: color }}
          initial={{ scale: 0.6, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={transition.slow}
        />
        <span className="size-5 rounded-full border-4 border-surface" style={{ background: color }} />
      </span>
    )
  }
  return (
    <span
      aria-hidden
      className="relative z-10 size-5 shrink-0 rounded-full border-4 bg-surface"
      style={{ borderColor: state === 'done' ? color : 'var(--ec-line-off)' }}
    />
  )
}
