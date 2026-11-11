import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { usePageTitle } from '@/lib/usePageTitle'
import { usePanel, viewRoles } from '@/lib/auth/panel'
import { hasRole, useSession } from '@/lib/auth/session'
import { formatLocal, greeting, localDiffMs, nowLocalIso } from '@/lib/time'
import { useMe } from '@/features/me/useMe'
import { useGradesFor, useMyAssignments } from '@/features/assignments/api'
import { useMyApplications, useMyCourses } from '@/features/courses/api'
import { useMyRegistrations, useUpcomingEvents } from '@/features/events/api'
import { useRecentNotifications } from '@/features/notifications/api'
import { buildItems, summarize } from '@/features/today/items'
import { CampusEvents, GradeReport, PendingApplications, RecentNotifications } from '@/features/today/Panels'
import { Panel } from '@/components/ui/Panel'
import { Timeline } from '@/features/today/Timeline'
import { WeekStrip } from '@/features/today/WeekStrip'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { Notice } from '@/components/ui/Notice'
import { Skeleton } from '@/components/ui/States'
import { AcademicianToday } from '@/features/today/AcademicianToday'
import { StaffToday } from '@/features/today/StaffToday'

/** Bugün: kişinin günü tek bakışta. Öğrencide çizelge, hafta şeridi, ders karnesi; akademisyende bekleyen işler ve dersler. */
export function HomePage() {
  usePageTitle('Bugün')
  const session = useSession()
  // Menü gibi: çift bağlılıkta seçili görünüm belirler.
  const { student, academician } = viewRoles(session, usePanel())
  const staffOrAdmin = hasRole(session, 'ROLE_STAFF', 'ROLE_ADMIN')

  return (
    <div className="mx-auto max-w-[80rem]">
      {session && session.pendingRequests.length > 0 && (
        <Notice tone="warning" title="Ek kayıt başvurunuz onay bekliyor" className="mb-6">
          {session.pendingRequests.includes('ACADEMICIAN') ? 'Personel' : 'Öğrenci'} kaydınız doğrulayıcı onayından sonra etkinleşecek.
        </Notice>
      )}
      {academician ? <AcademicianToday /> : student ? <StudentToday /> : staffOrAdmin ? <StaffToday /> : <OtherRoleToday />}
    </div>
  )
}

function OtherRoleToday() {
  const now = nowLocalIso()
  return (
    <>
      <p className="text-md font-semibold text-ink-3">{formatLocal(now, 'long')}</p>
      <RevealTitle className="mt-1 text-5xl" text={greeting(now) + '.'} />
      <Notice title="Bu ekran rolünüz için hazırlanıyor" className="mt-8">
        Menüdeki bölümlerden devam edebilirsiniz.
      </Notice>
    </>
  )
}

function StudentToday() {
  const now = nowLocalIso()
  const { data: me } = useMe()
  const assignments = useMyAssignments()
  const registrations = useMyRegistrations()
  const courses = useMyCourses()
  const applications = useMyApplications()
  const events = useUpcomingEvents(6)
  const notifications = useRecentNotifications(5)
  const [day, setDay] = useState<string | null>(null)

  const active = useMemo(() => (courses.data ?? []).filter((c) => c.status === 'OPEN' || c.status === 'ACTIVE'), [courses.data])
  const ids = useMemo(() => active.map((c) => c.id), [active])
  const grades = useGradesFor(ids)
  const codeOf = useMemo(() => new Map((courses.data ?? []).map((c) => [c.id, c.code])), [courses.data])

  const items = useMemo(
    () => buildItems(assignments.data ?? [], registrations.data ?? [], now),
    [assignments.data, registrations.data, now],
  )
  const shown = day ? items.filter((it) => it.at.startsWith(day)) : items
  const weekEvents = useMemo(
    () =>
      (events.data ?? [])
        .filter((e) => localDiffMs(now, e.startsAt) >= -3_600_000 && localDiffMs(now, e.startsAt) <= 7 * 86_400_000)
        .slice(0, 4),
    [events.data, now],
  )
  const registered = useMemo(() => new Set((registrations.data ?? []).map((r) => r.eventId)), [registrations.data])
  const pending = (applications.data ?? []).filter((a) => a.status === 'PENDING')
  const loading = assignments.isPending || registrations.isPending

  return (
    <>
      {/* 1. Karşılama: tarih, selam, günün özeti */}
      <header className="max-w-[48rem]">
        <p className="text-md text-ink-3">{formatLocal(now, 'long')}</p>
        <RevealTitle className="mt-1 text-5xl" text={`${greeting(now)}${me?.firstName ? `, ${me.firstName}` : ''}.`} />
        <p className="mt-4 text-xl text-ink-2">{loading ? 'Gününüz hazırlanıyor…' : summarize(items, now)}</p>
      </header>

      {/* 2. İçerik: solda yaklaşanlar ve kampüs, sağda notlar ve bildirimler. Bütün bölümler aynı başlık düzeyinde. */}
      <div className="mt-14 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-20 lg:col-span-8">
          <Panel
            title={day ? formatLocal(`${day}T12:00:00`, 'long') : 'Yaklaşanlar'}
            action={
              day ? (
                <button type="button" onClick={() => setDay(null)} className="text-sm font-semibold underline-offset-4 hover:underline">
                  Tüm günler
                </button>
              ) : (
                <Link to="/assignments" className="text-sm font-semibold underline-offset-4 hover:underline">
                  Tüm ödevler
                </Link>
              )
            }
          >
            <WeekStrip items={items} now={now} selected={day} onSelect={setDay} />
            <div className="mt-2">
              {loading ? (
                <Skeleton rows={4} className="mt-4" />
              ) : assignments.isError ? (
                <Notice tone="warning" title="Ödevler yüklenemedi" className="mt-4">
                  Sayfayı yenileyip tekrar deneyin.
                </Notice>
              ) : shown.length === 0 ? (
                <p className="py-6 text-md text-ink-2">
                  {day
                    ? 'Bu güne düşen teslim ya da kayıtlı etkinlik yok.'
                    : 'Önümüzdeki iki hafta için teslim ya da kayıtlı etkinlik yok.'}
                </p>
              ) : (
                <Timeline items={shown} now={now} codeOf={codeOf} />
              )}
            </div>
          </Panel>

          <Panel title="Kampüste bu hafta" to="/events" linkLabel="Tüm etkinlikler">
            {events.isPending ? <Skeleton rows={2} /> : <CampusEvents list={weekEvents} registered={registered} />}
          </Panel>
        </div>

        <aside className="flex flex-col gap-20 lg:col-span-4">
          <Panel title="Notlarım" to="/courses" linkLabel="Derslerim">
            {courses.isPending ? (
              <Skeleton rows={3} />
            ) : active.length === 0 ? (
              <p className="py-2 text-md text-ink-2">Bu dönem kayıtlı dersiniz yok.</p>
            ) : (
              <GradeReport courses={active} grades={grades.map((g) => g.data)} />
            )}
          </Panel>
          <Panel title="Bildirimler" to="/notifications">
            {notifications.isPending ? <Skeleton rows={3} /> : <RecentNotifications list={notifications.data ?? []} />}
          </Panel>
          {pending.length > 0 && (
            <Panel title="Ders başvurularım" to="/courses/catalog?sekme=basvurular" linkLabel="Başvurularım">
              <PendingApplications list={pending} />
            </Panel>
          )}
        </aside>
      </div>
    </>
  )
}
