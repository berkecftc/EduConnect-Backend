import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { formatNumber } from '@/lib/format'
import { formatLocal, greeting, localDiffMs, nowLocalIso } from '@/lib/time'
import { useMe } from '@/features/me/useMe'
import { TYPE_LABEL } from '@/features/assignments/api'
import {
  columnProgress,
  ungradedCount,
  useCourseAssignmentsFor,
  useGradebooksFor,
  type StaffAssignment,
} from '@/features/assignments/staff'
import { STAFF_ROLE_LABEL, isRunning } from '@/features/courses/api'
import { abilities, useTeachingCourses, type TeachingCourse } from '@/features/courses/teach'
import { useAdvisorOffers, useCreationRequests, useRoleChangeRequests } from '@/features/clubs/advise'
import { useApprovalInbox, useClubAccesses } from '@/features/clubs/manage'
import { useAdvisorQueue } from '@/features/events/manage'
import { useUpcomingEvents } from '@/features/events/api'
import { useRecentNotifications } from '@/features/notifications/api'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, Skeleton } from '@/components/ui/States'
import { WorkStrip } from '@/components/ui/WorkStrip'
import { CampusEvents, RecentNotifications } from './Panels'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: 'UTC' })
const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'short', timeZone: 'UTC' })
const HORIZON_DAYS = 14

/** "3 ders başvurusu, 16 teslim ve 2 kulüp onayı" */
function joinTr(parts: string[]) {
  return parts.length <= 1 ? parts.join('') : `${parts.slice(0, -1).join(', ')} ve ${parts.at(-1)}`
}

/**
 * Bugün (akademisyen): karar bekleyen işler (ders başvurusu, notlanacak teslim, kulüp onayı, danışmanlık),
 * bu dönem verdiğim dersler ve iki hafta içindeki teslim tarihleri; yanda bildirimler ve kampüs.
 */
export function AcademicianToday() {
  const now = nowLocalIso()
  const { data: me } = useMe()
  const teaching = useTeachingCourses()
  const running = useMemo(() => (teaching.data ?? []).filter((c) => isRunning(c.status)), [teaching.data])
  const ids = useMemo(() => running.map((c) => c.id), [running])
  const assessments = useCourseAssignmentsFor(ids)
  const books = useGradebooksFor(ids)
  const accesses = useClubAccesses()
  const advises = (accesses.data ?? []).some((a) => a.advisor)
  const inbox = useApprovalInbox()
  const eventQueue = useAdvisorQueue(true)
  const creation = useCreationRequests()
  const offers = useAdvisorOffers()
  const roleChanges = useRoleChangeRequests()
  const notifications = useRecentNotifications(5)
  const events = useUpcomingEvents(6)

  const applications = running.filter((c) => abilities(c.staffRole).applications).reduce((n, c) => n + c.pendingApplicationCount, 0)
  const booksReady = books.every((b) => !b.isPending)
  const ungraded = books.reduce((n, b) => n + ungradedCount(b.data), 0)
  const approvals = inbox.data && eventQueue.data ? inbox.data.length + eventQueue.data.length : undefined
  const advising =
    creation.data && offers.data && roleChanges.data
      ? creation.data.filter((r) => r.status === 'PENDING').length +
        offers.data.filter((o) => o.status.startsWith('PENDING')).length +
        roleChanges.data.filter((r) => r.status === 'PENDING').length
      : undefined

  // Şeridin bağlantıları en çok iş bekleyen derse gider.
  const mostApps = [...running].sort((a, b) => b.pendingApplicationCount - a.pendingApplicationCount)[0]
  const ungradedBy = running.map((c, i) => ({ c, n: ungradedCount(books[i]?.data) })).sort((a, b) => b.n - a.n)[0]

  const upcoming = useMemo(() => {
    const list: { a: StaffAssignment; course: TeachingCourse; index: number }[] = []
    running.forEach((course, index) => {
      for (const a of assessments[index]?.data ?? []) {
        const diff = localDiffMs(now, a.dueDate)
        if (diff >= 0 && diff <= HORIZON_DAYS * 86_400_000) list.push({ a, course, index })
      }
    })
    return list.sort((x, y) => x.a.dueDate.localeCompare(y.a.dueDate))
  }, [running, assessments, now])

  const loading = teaching.isPending || !booksReady
  const parts = [
    applications ? `${applications} ders başvurusu` : '',
    ungraded ? `${ungraded} notlanmamış teslim` : '',
    approvals ? `${approvals} kulüp onayı` : '',
    advising ? `${advising} danışmanlık işi` : '',
  ].filter(Boolean)
  const summary = loading
    ? 'Gününüz hazırlanıyor…'
    : parts.length
      ? `${joinTr(parts)} kararınızı bekliyor.`
      : 'Bugün kararınızı bekleyen bir iş yok.'

  return (
    <>
      <header className="max-w-[52rem]">
        <p className="text-md text-ink-3">{formatLocal(now, 'long')}</p>
        <RevealTitle className="mt-1 text-5xl" text={`${greeting(now)}${me?.firstName ? `, ${me.firstName}` : ''}.`} />
        <p className="mt-4 text-xl text-ink-2">{summary}</p>
      </header>

      <div className="mt-10">
        <WorkStrip
          items={[
            {
              key: 'basvuru',
              to: mostApps && applications ? `/courses/${mostApps.id}?sekme=basvurular` : '/courses',
              value: teaching.data ? applications : undefined,
              label: 'ders başvurusu',
              urgent: applications > 0,
            },
            {
              key: 'teslim',
              to: ungradedBy && ungraded ? `/courses/${ungradedBy.c.id}?sekme=odevler` : '/courses',
              value: booksReady && teaching.data ? ungraded : undefined,
              label: 'teslim notlanmayı bekliyor',
              urgent: ungraded > 0,
            },
            { key: 'onay', to: '/clubs/approvals', value: approvals, label: 'kulüp onayı', urgent: !!approvals },
            ...(advises || advising
              ? [
                  {
                    key: 'danisman',
                    to: '/clubs/advised',
                    value: advising,
                    label: 'danışmanlık işi',
                    note: 'kuruluş, görev, teklif',
                    urgent: !!advising,
                  },
                ]
              : []),
          ]}
        />
      </div>

      <div className="mt-14 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <div className="@container flex min-w-0 flex-col gap-20 lg:col-span-8">
          <Panel title="Bu dönem verdiğiniz dersler" to="/courses" linkLabel="Verdiğim dersler">
            {teaching.isPending ? (
              <Skeleton rows={3} />
            ) : running.length === 0 ? (
              <EmptyState title="Süren dersiniz yok">Kadrosunda olduğunuz dersler yayımlanıp dönem başladığında burada görünür.</EmptyState>
            ) : (
              <ul className="border-t border-rule">
                {running.map((c, i) => {
                  const next = [...(assessments[i]?.data ?? [])]
                    .filter((a) => a.dueDate > now)
                    .sort((x, y) => x.dueDate.localeCompare(y.dueDate))[0]
                  const waiting = ungradedCount(books[i]?.data)
                  const can = abilities(c.staffRole)
                  return (
                    <li key={c.id} className="border-b border-rule">
                      <Link
                        to={`/courses/${c.id}`}
                        className="row-fill group grid gap-x-6 gap-y-2 py-5 @xl:grid-cols-[8rem_minmax(0,1fr)_14rem]"
                      >
                        <span className="flex items-start gap-3">
                          <span aria-hidden className="mt-1 h-6 w-[3px] shrink-0 bg-ders" />
                          <span className="tabular text-2xl leading-none font-heavy whitespace-nowrap">{c.code}</span>
                        </span>
                        <span className="min-w-0">
                          <span className="block text-lg font-semibold">{c.title}</span>
                          <span className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
                            <span>{STAFF_ROLE_LABEL[c.staffRole]}</span>
                            <span className="tabular">
                              {c.enrolledStudentCount} / {c.capacity} öğrenci
                            </span>
                            {next && (
                              <span>
                                sıradaki: {next.title}, {formatLocal(next.dueDate, 'short')}
                              </span>
                            )}
                          </span>
                        </span>
                        <span className="flex flex-col gap-0.5 text-md @xl:items-end @xl:text-right">
                          {can.applications && c.pendingApplicationCount > 0 && (
                            <span className="font-semibold text-warning">{c.pendingApplicationCount} başvuru bekliyor</span>
                          )}
                          {waiting > 0 && <span className="font-semibold text-warning">{waiting} teslim notlanmadı</span>}
                          {!(can.applications && c.pendingApplicationCount) && !waiting && (
                            <span className="text-ink-3">Bekleyen iş yok</span>
                          )}
                        </span>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </Panel>

          <Panel title="Yaklaşan teslim tarihleri">
            {loading ? (
              <Skeleton rows={3} />
            ) : upcoming.length === 0 ? (
              <p className="py-2 text-md text-ink-2">Önümüzdeki iki hafta içinde son tarihi gelen değerlendirme yok.</p>
            ) : (
              <ul className="border-t border-rule">
                {upcoming.map(({ a, course, index }) => {
                  const p = columnProgress(books[index]?.data, a.id)
                  const at = new Date(`${a.dueDate.slice(0, 10)}T12:00:00Z`)
                  return (
                    <li key={a.id} className="border-b border-rule">
                      <Link
                        to={`/courses/${course.id}/assignments/${a.id}`}
                        className="row-fill grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-5 gap-y-1 py-4 @lg:grid-cols-[4rem_minmax(0,1fr)_11rem]"
                      >
                        <span className="row-span-2 @lg:row-span-1">
                          <span className="tabular block text-3xl leading-none font-heavy">{DAY.format(at)}</span>
                          <span className="mt-1 block text-xs text-ink-3">
                            {MONTH.format(at)}, {formatLocal(a.dueDate, 'time')}
                          </span>
                        </span>
                        <span className="min-w-0">
                          <span className="block text-lg font-semibold">{a.title}</span>
                          <span className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
                            <span className="tabular font-semibold text-ders">{course.code}</span>
                            <span>{TYPE_LABEL[a.type]}</span>
                            <span>ağırlık %{formatNumber(a.weight)}</span>
                          </span>
                        </span>
                        <span className="tabular col-start-2 text-sm text-ink-2 @lg:col-start-auto @lg:text-right">
                          {p.submitted} / {p.students} teslim
                        </span>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </Panel>
        </div>

        <aside className="flex flex-col gap-20 lg:col-span-4">
          <Panel title="Bildirimler" to="/notifications">
            {notifications.isPending ? <Skeleton rows={3} /> : <RecentNotifications list={notifications.data ?? []} />}
          </Panel>
          <Panel title="Kampüste bu hafta" to="/events" linkLabel="Tüm etkinlikler">
            {events.isPending ? (
              <Skeleton rows={2} />
            ) : (
              <CampusEvents
                list={(events.data ?? [])
                  .filter((e) => localDiffMs(now, e.startsAt) >= -3_600_000 && localDiffMs(now, e.startsAt) <= 7 * 86_400_000)
                  .slice(0, 4)}
                registered={new Set()}
              />
            )}
          </Panel>
        </aside>
      </div>
    </>
  )
}
