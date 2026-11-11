import { formatRemaining, localDiffMs, localStamp } from '@/lib/time'
import type { MyAssignment } from '@/features/assignments/api'
import { viewAssignment } from '@/features/assignments/model'
import type { MyRegistration } from '@/features/events/api'

export type TodayItem =
  | { kind: 'assignment'; at: string; assignment: MyAssignment }
  | { kind: 'event'; at: string; event: MyRegistration }

export const HORIZON_DAYS = 14

/**
 * Bugün çizelgesinin öğeleri: teslim edilmemiş ve hâlâ teslim edilebilir ödevler
 * (geç teslim penceresindekiler kapanış anına göre) ve kayıtlı, yaklaşan etkinlikler.
 */
export function buildItems(assignments: MyAssignment[], registrations: MyRegistration[], now: string): TodayItem[] {
  const horizon = HORIZON_DAYS * 86_400_000
  const list: TodayItem[] = []
  for (const a of assignments) {
    const v = viewAssignment(a, now)
    if (v.submitted || !v.canSubmit) continue
    const at = v.phase === 'late' ? v.closesAt : a.effectiveDueDate
    if (localDiffMs(now, at) <= horizon) list.push({ kind: 'assignment', at, assignment: a })
  }
  for (const e of registrations) {
    if (e.registrationStatus !== 'REGISTERED' || e.eventStatus !== 'ACTIVE') continue
    const diff = localDiffMs(now, e.eventDate)
    if (diff >= -3 * 3_600_000 && diff <= horizon) list.push({ kind: 'event', at: e.eventDate, event: e })
  }
  return list.sort((x, y) => x.at.localeCompare(y.at))
}

/** Bugünden başlayan `n` günün yerel tarihleri (yyyy-MM-dd). */
export function nextDays(now: string, n = 7): string[] {
  const start = localStamp(`${now.slice(0, 10)}T00:00:00`)
  return Array.from({ length: n }, (_, i) => new Date(start + i * 86_400_000).toISOString().slice(0, 10))
}

/**
 * Günün özeti, iki kısa cümle: bugünkü teslimler (yoksa en yakın teslime kalan süre) ve bugün/yarın kayıtlı etkinlik.
 * Etkinlik adlarına Türkçe ek eklenmez (ünlü uyumu bilinemez); ad iki noktadan sonra yazılır.
 */
export function summarize(items: TodayItem[], now: string): string {
  const [today, tomorrow] = nextDays(now, 2)
  const due = items.filter((i) => i.kind === 'assignment')
  const dueToday = due.filter((i) => i.at.startsWith(today!)).length
  const sentences: string[] = []

  if (dueToday > 0) sentences.push(`Bugün ${dueToday} teslim var.`)
  else if (due[0]) sentences.push(`Bugün teslim yok. En yakın teslim ${formatRemaining(localDiffMs(now, due[0].at))} sonra.`)

  const event = items.find((i) => i.kind === 'event' && (i.at.startsWith(today!) || i.at.startsWith(tomorrow!)))
  if (event && event.kind === 'event') {
    sentences.push(`${event.at.startsWith(today!) ? 'Bugün' : 'Yarın'} kayıtlı olduğunuz bir etkinlik var: ${event.event.eventTitle}.`)
  }

  return sentences.length ? sentences.join(' ') : 'Önümüzdeki iki hafta için teslim ya da kayıtlı etkinlik yok.'
}
