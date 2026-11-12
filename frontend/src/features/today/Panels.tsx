import { Link } from 'react-router-dom'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocal, formatRelative } from '@/lib/time'
import { cn } from '@/lib/cn'
import type { EnrolledCourse, Announcement, CourseApplication } from '@/features/courses/api'
import { APPLICATION_STATUS_LABEL } from '@/features/courses/api'
import type { MyGrades } from '@/features/assignments/api'
import type { CampusEvent } from '@/features/events/api'
import { CATEGORY_META, useOpenNotification, type AppNotification } from '@/features/notifications/api'
import { Meter } from '@/components/ui/Meter'
import { StationLine } from '@/components/ui/StationLine'

/** Notlarım: kod, ad, ilan edilen puanlar üzerinden toplam ve ince ölçek çizgisi (açık ton: ilan edilen kısım). */
export function GradeReport({ courses, grades }: { courses: EnrolledCourse[]; grades: (MyGrades | undefined)[] }) {
  return (
    <ul>
      {courses.map((c, i) => {
        const g = grades[i]
        return (
          <li key={c.id} className="border-b border-rule last:border-0">
            <Link to={`/courses/${c.id}?sekme=notlar`} className="row-fill -mx-2 block px-2 py-3">
              <span className="flex items-end justify-between gap-4">
                <span className="min-w-0">
                  <span className="tabular block text-sm font-heavy text-ders">{c.code}</span>
                  <span className="block truncate text-md">{c.title}</span>
                </span>
                <span className="shrink-0 text-right">
                  <span className="tabular text-3xl leading-none font-heavy">{g ? formatNumber(g.weightedTotal) : '–'}</span>
                </span>
              </span>
              <span className="mt-2.5 block">
                <Meter
                  value={g?.weightedTotal ?? 0}
                  evaluated={g?.gradedWeight ?? 0}
                  label={g ? `${c.code}: ilan edilen ${formatNumber(g.gradedWeight)} puan üzerinden ${formatNumber(g.weightedTotal)}` : `${c.code}: notlar yükleniyor`}
                />
              </span>
              <span className="mt-1.5 block text-xs text-ink-3">
                {g && g.gradedWeight > 0 ? `İlan edilen ${formatNumber(g.gradedWeight)} puan üzerinden` : 'Henüz ilan edilen not yok'}
              </span>
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

/** Son bildirimler: tıklayınca okundu sayılır ve bağlantıya gidilir (F-74). */
export function RecentNotifications({ list }: { list: AppNotification[] }) {
  const open = useOpenNotification()
  if (list.length === 0) return <p className="py-2 text-md text-ink-2">Yeni bildirim yok.</p>
  return (
    <ul>
      {list.map((n) => {
        const body = (
          <>
            <span aria-hidden className="mt-1.5 h-3 w-[3px] shrink-0" style={{ background: CATEGORY_META[n.category].line }} />
            <span className="min-w-0 flex-1">
              <span className={cn('block text-md', n.read ? 'text-ink-2' : 'font-semibold text-ink')}>{n.title}</span>
              <span className="block text-xs text-ink-3">
                {formatRelative(n.createdAt)}
                {!n.read && <span className="font-semibold text-etkinlik">, okunmadı</span>}
              </span>
            </span>
          </>
        )
        return (
          <li key={n.id} className="border-b border-rule last:border-0">
            {n.link || !n.read ? (
              <button type="button" onClick={() => open(n)} className="row-fill -mx-2 flex w-[calc(100%+1rem)] gap-3 px-2 py-2.5 text-left">
                {body}
              </button>
            ) : (
              <div className="flex gap-3 py-2.5">{body}</div>
            )}
          </li>
        )
      })}
    </ul>
  )
}

export function RecentAnnouncements({ list, codeOf }: { list: Announcement[]; codeOf: Map<string, string> }) {
  if (list.length === 0) return <p className="py-2 text-md text-ink-2">Derslerinizde yeni duyuru yok.</p>
  return (
    <ul>
      {list.map((a) => (
        <li key={a.id} className="border-b border-rule last:border-0">
          <Link to={`/courses/${a.courseId}?sekme=duyurular`} className="row-fill -mx-2 block px-2 py-3">
            <span className="flex items-baseline justify-between gap-3">
              <span className="tabular text-xs font-heavy text-ders">{codeOf.get(a.courseId) ?? 'Ders'}</span>
              <span className="text-xs text-ink-3">{formatRelative(a.createdAt)}</span>
            </span>
            <span className="mt-0.5 block font-semibold">{a.title}</span>
            <span className="mt-0.5 line-clamp-2 block text-sm text-ink-2">{a.content}</span>
          </Link>
        </li>
      ))}
    </ul>
  )
}

const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'long', timeZone: 'UTC' })
const WEEKDAY = new Intl.DateTimeFormat('tr-TR', { weekday: 'long', timeZone: 'UTC' })

/** Kampüste bu hafta: tarife satırı; solda dev gün rakamı, ortada başlık ve düzenleyen, sağda saat ve yer. */
export function CampusEvents({ list, registered }: { list: CampusEvent[]; registered: Set<string> }) {
  if (list.length === 0) return <p className="py-2 text-md text-ink-2">Bu hafta kampüste yayımlanmış etkinlik yok.</p>
  return (
    <ul className="@container">
      {list.map((e) => {
        const d = new Date(`${e.startsAt.slice(0, 10)}T12:00:00Z`)
        return (
          <li key={e.id} className="border-b border-rule last:border-0">
            <Link
              to={`/events/${e.id}`}
              className="row-fill grid grid-cols-[4rem_minmax(0,1fr)] items-start gap-x-5 gap-y-1 py-4 @xl:grid-cols-[4.5rem_minmax(0,1fr)_14rem]"
            >
              <span>
                <span className="tabular block text-4xl leading-none font-heavy">{d.getUTCDate()}</span>
                <span className="mt-1 block text-xs text-ink-3">
                  {MONTH.format(d)}, {WEEKDAY.format(d)}
                </span>
              </span>
              <span className="min-w-0">
                <span className="flex items-center gap-2">
                  <span aria-hidden className="h-4 w-[3px] shrink-0 bg-etkinlik" />
                  <span className="text-lg font-semibold">{e.title}</span>
                </span>
                <span className="mt-0.5 block pl-[11px] text-sm text-ink-3">{e.clubName ?? e.organizerName ?? 'Kampüs'}</span>
              </span>
              <span className="col-start-2 text-sm text-ink-2 @xl:col-start-auto @xl:text-right">
                <span className="tabular block font-semibold text-ink">
                  {formatLocal(e.startsAt, 'time')}
                  {e.endsAt ? `–${formatLocal(e.endsAt, 'time')}` : ''}
                </span>
                {e.location && <span className="block">{e.location}</span>}
                {registered.has(e.id) && <span className="block font-semibold text-success">Kayıtlısınız</span>}
              </span>
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

export function PendingApplications({ list }: { list: CourseApplication[] }) {
  return (
    <ul className="flex flex-col gap-6 pt-1">
      {list.map((a) => (
        <li key={a.id}>
          <p className="text-md">
            <span className="tabular font-heavy text-ders">{a.courseCode}</span> {a.courseTitle}
          </p>
          <StationLine
            className="mt-3"
            line="ders"
            label={`${a.courseCode} başvuru durumu`}
            stations={[
              { label: 'Başvuru', state: 'done', detail: formatInstant(a.applicationDate, 'short') },
              { label: APPLICATION_STATUS_LABEL.PENDING, state: 'current' },
              { label: 'Kayıt', state: 'pending' },
            ]}
          />
        </li>
      ))}
    </ul>
  )
}
