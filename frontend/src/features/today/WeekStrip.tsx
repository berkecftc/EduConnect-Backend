import { motion } from 'motion/react'
import { transition } from '@/design/motion'
import { cn } from '@/lib/cn'
import { formatLocal } from '@/lib/time'
import { nextDays, type TodayItem } from './items'

const SHORT_DAY = new Intl.DateTimeFormat('tr-TR', { weekday: 'short', timeZone: 'UTC' })

/**
 * Hafta şeridi, tarife başlığı gibi: ince çizgilerle bölünmüş 7 kolon, dev gün rakamı,
 * altında teslim (ders mavisi) ve etkinlik (turuncu) çentikleri. Seçili günün üstünde kayan kalın çizgi.
 */
export function WeekStrip({
  items,
  now,
  selected,
  onSelect,
}: {
  items: TodayItem[]
  now: string
  selected: string | null
  onSelect: (day: string | null) => void
}) {
  const days = nextDays(now, 7)
  return (
    <div role="group" aria-label="Önümüzdeki 7 gün" className="grid grid-cols-7 border-y border-rule">
      {days.map((day, i) => {
        const list = items.filter((it) => it.at.startsWith(day))
        const due = list.filter((it) => it.kind === 'assignment').length
        const events = list.length - due
        const on = selected === day
        const date = new Date(`${day}T12:00:00Z`)
        return (
          <button
            key={day}
            type="button"
            aria-pressed={on}
            onClick={() => onSelect(on ? null : day)}
            className={cn(
              'row-fill relative flex flex-col items-start gap-2 px-2 pt-4 pb-3 text-left sm:px-4',
              i > 0 && 'border-l border-rule',
            )}
          >
            {on && <motion.span layoutId="hafta-secili" transition={transition.base} aria-hidden className="absolute inset-x-0 -top-px h-[4px] bg-ink" />}
            <span className={cn('text-xs sm:text-sm', on ? 'font-semibold text-ink' : 'text-ink-3')}>
              {i === 0 ? 'Bugün' : SHORT_DAY.format(date)}
            </span>
            <span className={cn('tabular text-2xl leading-none font-heavy sm:text-4xl', on || i === 0 ? 'text-ink' : 'text-ink-2')}>
              {date.getUTCDate()}
            </span>
            <span aria-hidden className="flex h-[3px] gap-1">
              {Array.from({ length: Math.min(due, 3) }, (_, k) => (
                <span key={`d${k}`} className="h-[3px] w-3 bg-ders" />
              ))}
              {Array.from({ length: Math.min(events, 3) }, (_, k) => (
                <span key={`e${k}`} className="h-[3px] w-3 bg-etkinlik" />
              ))}
            </span>
            <span className="sr-only">
              {formatLocal(`${day}T12:00:00`, 'long')}: {due ? `${due} teslim` : 'teslim yok'}, {events ? `${events} etkinlik` : 'etkinlik yok'}
            </span>
          </button>
        )
      })}
    </div>
  )
}
