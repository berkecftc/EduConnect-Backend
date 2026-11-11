import { Link } from 'react-router-dom'
import { AnimatePresence, motion } from 'motion/react'
import { LINES } from '@/design/lines'
import { transition } from '@/design/motion'
import { dayLabel, formatLocal } from '@/lib/time'
import { viewAssignment } from '@/features/assignments/model'
import { TYPE_LABEL } from '@/features/assignments/api'
import { Badge } from '@/components/ui/Badge'
import type { TodayItem } from './items'

const ROW = 'grid grid-cols-[3.75rem_minmax(0,1fr)] gap-x-4 gap-y-1.5 @xl:grid-cols-[4.5rem_6.5rem_minmax(0,1fr)_11rem]'

/**
 * Yaklaşanlar tablosu: gün başlıkları altında saat, ders kodu ya da etkinlik, başlık ve durum kolonları. Kolonlar sayfaya
 * değil tablonun kendi genişliğine göre açılır; yan sütunlu dar sayfada başlık sıkışmaz.
 * Satıra gelince zemin soldan dolar; liste süzülünce günler kısa bir geçişle girip çıkar.
 */
export function Timeline({ items, now, codeOf }: { items: TodayItem[]; now: string; codeOf: Map<string, string> }) {
  const groups: [string, TodayItem[]][] = []
  for (const it of items) {
    const key = it.at.slice(0, 10)
    const last = groups[groups.length - 1]
    if (last && last[0] === key) last[1].push(it)
    else groups.push([key, [it]])
  }

  return (
    <div className="@container">
      <AnimatePresence initial={false}>
        {groups.map(([day, list]) => {
          const label = dayLabel(list[0]!.at, now)
          const full = formatLocal(list[0]!.at, 'long')
          return (
            <motion.section
              key={day}
              aria-label={label === full ? label : `${label}, ${full}`}
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              transition={transition.base}
            >
              <h3 className="flex items-baseline gap-3 border-b border-rule pt-6 pb-2">
                <span className="text-lg font-heavy">{label}</span>
                {label !== full && <span className="text-md text-ink-3">{full}</span>}
              </h3>
              <ul>
                {list.map((it) =>
                  it.kind === 'assignment' ? (
                    <AssignmentRow key={`a-${it.assignment.id}`} item={it} code={codeOf.get(it.assignment.courseId)} now={now} />
                  ) : (
                    <EventRow key={`e-${it.event.eventId}`} item={it} />
                  ),
                )}
              </ul>
            </motion.section>
          )
        })}
      </AnimatePresence>
    </div>
  )
}

function Line({ color, code }: { color: string; code: string }) {
  return (
    <span className="hidden items-center gap-2 pt-1 @xl:flex">
      <span aria-hidden className="h-4 w-[3px]" style={{ background: color }} />
      <span className="tabular truncate text-md font-heavy">{code}</span>
    </span>
  )
}

function AssignmentRow({ item, code, now }: { item: Extract<TodayItem, { kind: 'assignment' }>; code?: string; now: string }) {
  const a = item.assignment
  const v = viewAssignment(a, now)
  return (
    <li className="border-b border-rule">
      <Link to={`/courses/${a.courseId}/assignments/${a.id}`} className={`${ROW} row-fill py-4`}>
        <span className="tabular text-lg font-semibold">{formatLocal(item.at, 'time')}</span>
        <Line color={LINES.ders.stroke} code={code ?? 'Ders'} />
        <span className="min-w-0">
          <span className="block text-lg font-semibold">{a.title}</span>
          <span className="mt-0.5 block text-sm text-ink-3">
            <span className="tabular font-semibold text-ders @xl:hidden">{code} </span>
            {v.phase === 'late' ? `Geç teslim bitişi${v.penaltyText ? `, ${v.penaltyText}` : ''}` : TYPE_LABEL[a.type]}
          </span>
        </span>
        <span className="col-start-2 flex flex-wrap items-center gap-x-3 gap-y-1 @xl:col-start-auto @xl:flex-col @xl:items-end @xl:text-right">
          <Badge tone={v.status.tone}>{v.status.label}</Badge>
          {v.timing && <span className="tabular text-sm text-ink-3">{v.timing}</span>}
        </span>
      </Link>
    </li>
  )
}

function EventRow({ item }: { item: Extract<TodayItem, { kind: 'event' }> }) {
  const e = item.event
  return (
    <li className="border-b border-rule">
      <Link to={`/events/${e.eventId}`} className={`${ROW} row-fill py-4`}>
        <span className="tabular text-lg font-semibold">{formatLocal(item.at, 'time')}</span>
        <Line color={LINES.etkinlik.stroke} code="Etkinlik" />
        <span className="min-w-0">
          <span className="block text-lg font-semibold">{e.eventTitle}</span>
          {e.eventLocation && <span className="mt-0.5 block text-sm text-ink-3">{e.eventLocation}</span>}
        </span>
        <span className="col-start-2 @xl:col-start-auto @xl:text-right">
          <Badge tone="success">Kayıtlısınız</Badge>
        </span>
      </Link>
    </li>
  )
}
