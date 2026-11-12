import { formatLocal, greeting, nowLocalIso } from '@/lib/time'
import { hasPermission, hasRole, useSession } from '@/lib/auth/session'
import { buildNav } from '@/components/layout/nav'
import { useAcademicianRequests, useChangeRequests, useStudentRequests } from '@/features/manage/verification'
import { useAppealQueue, useQueue, useReports } from '@/features/manage/moderation'
import { useCampusEvents } from '@/features/events/manage'
import { useRecentNotifications } from '@/features/notifications/api'
import { Link } from 'react-router-dom'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { Skeleton } from '@/components/ui/States'
import { WorkStrip, type WorkItem } from '@/components/ui/WorkStrip'
import { RecentNotifications } from './Panels'

/**
 * Bugün (görevli ve yönetici): yetkilere göre bekleyen işler şeridi, yönetim bölümlerine kısa yol ve bildirimler.
 * Görevli ve yöneticinin profili yoktur (F-83); selamlama adsızdır.
 */
export function StaffToday() {
  const now = nowLocalIso()
  const session = useSession()
  const admin = hasRole(session, 'ROLE_ADMIN')
  const verifiesStudents = admin || hasPermission(session, 'PERM_STUDENT_VERIFIER')
  const verifiesStaff = admin || hasPermission(session, 'PERM_STAFF_VERIFIER')
  const students = useStudentRequests(verifiesStudents)
  const academicians = useAcademicianRequests(verifiesStaff)
  const changes = useChangeRequests('PENDING', verifiesStudents || verifiesStaff)
  const moderates = admin || hasPermission(session, 'PERM_MODERATOR')
  const queuePosts = useQueue('POST', 0, 1, moderates)
  const queueComments = useQueue('COMMENT', 0, 1, moderates)
  const reports = useReports(0, 1, moderates)
  const appeals = useAppealQueue(0, 1, moderates)
  const publishes = admin || hasPermission(session, 'PERM_CAMPUS_PUBLISHER')
  const campus = useCampusEvents(publishes)
  const notifications = useRecentNotifications(5)
  const sections = session ? (buildNav(session).find((g) => g.line === 'yonetim')?.items ?? []) : []

  // Şerit en fazla dört hücre: iki doğrulama yetkisi birlikteyse başvurular tek hücrede toplanır.
  const items: WorkItem[] = []
  if (verifiesStudents && verifiesStaff) {
    const s = students.data?.length
    const a = academicians.data?.length
    const n = s === undefined || a === undefined ? undefined : s + a
    items.push({
      key: 'basvuru',
      to: a && !s ? '/manage/academicians' : '/manage/students',
      value: n,
      label: 'kayıt başvurusu',
      note: n ? `${s} öğrenci, ${a} akademisyen` : undefined,
      urgent: !!n,
    })
  } else if (verifiesStudents) {
    const n = students.data?.length
    items.push({ key: 'ogrenci', to: '/manage/students', value: n, label: 'öğrenci başvurusu', urgent: !!n })
  } else if (verifiesStaff) {
    const n = academicians.data?.length
    items.push({ key: 'akademisyen', to: '/manage/academicians', value: n, label: 'akademisyen başvurusu', urgent: !!n })
  }
  if (verifiesStudents || verifiesStaff) {
    const n = changes.data?.length
    items.push({ key: 'profil', to: '/manage/change-requests', value: n, label: 'profil talebi', urgent: !!n })
  }
  if (moderates) {
    const parts = [queuePosts, queueComments, reports, appeals].map((q) => q.data?.totalElements)
    const ready = parts.every((p) => p !== undefined)
    const [p, c, r, ap] = parts as number[]
    const n = ready ? p! + c! + r! + ap! : undefined
    const note =
      ready && n
        ? [p! + c! ? `${p! + c!} inceleme` : '', r ? `${r} şikâyet` : '', ap ? `${ap} itiraz` : ''].filter(Boolean).join(', ')
        : undefined
    items.push({
      key: 'moderasyon',
      to: r ? '/manage/moderation?sekme=sikayetler' : '/manage/moderation',
      value: n,
      label: 'moderasyon kaydı',
      note,
      urgent: !!n,
    })
  }
  if (publishes) {
    const n = campus.data?.filter((e) => e.status === 'ACTIVE' && (e.endsAt ?? e.startsAt) >= now).length
    items.push({ key: 'kampus', to: '/manage/campus-events', value: n, label: 'yaklaşan kampüs etkinliği', urgent: false })
  }

  // Şeritte bağlantısı olmayan yönetim bölümleri (ör. yöneticinin dönemler, akademik yapı bölümleri).
  const others = sections.filter((s) => !items.some((it) => it.to?.split('?')[0] === s.to))
  // Kampüs etkinliği karar beklemez; özete sayılmaz.
  const total = items.reduce((n, it) => n + (it.key === 'kampus' ? 0 : (it.value ?? 0)), 0)
  const loading = items.some((it) => it.value === undefined)
  const summary =
    items.length === 0
      ? 'Yönetim bölümleri menüde.'
      : loading
        ? 'Bekleyen işler yükleniyor…'
        : total
          ? `${total} kayıt kararınızı bekliyor.`
          : 'Kararınızı bekleyen kayıt yok.'

  return (
    <>
      <header className="max-w-[52rem]">
        <p className="text-md text-ink-3">{formatLocal(now, 'long')}</p>
        <RevealTitle className="mt-1 text-5xl" text={`${greeting(now)}.`} />
        <p className="mt-4 text-xl text-ink-2">{summary}</p>
      </header>

      {items.length > 0 && (
        <div className="mt-10">
          <WorkStrip items={items} />
        </div>
      )}

      <div className={others.length || sections.length === 0 ? 'mt-14 grid gap-14 lg:grid-cols-12 lg:gap-10' : 'mt-14 max-w-[44rem]'}>
        {(others.length > 0 || sections.length === 0) && (
          <div className="min-w-0 lg:col-span-8">
            <Panel title={items.length ? 'Diğer bölümler' : 'Yönetim'}>
              {sections.length === 0 ? (
                <p className="text-md text-ink-2">
                  Hesabınıza henüz yönetim yetkisi tanımlanmamış. Yetki için sistem yöneticisine başvurun.
                </p>
              ) : (
                <ul className="grid border-t border-rule sm:grid-cols-2">
                  {others.map((s) => (
                    <li key={s.to} className="border-b border-rule sm:odd:border-r sm:odd:pr-6 sm:even:pl-6">
                      <Link to={s.to} className="row-fill group flex items-center gap-3 py-4 text-lg font-semibold">
                        <span aria-hidden className="h-5 w-[3px] shrink-0 bg-yonetim" />
                        <span className="group-hover:underline group-hover:underline-offset-4">{s.label}</span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </div>
        )}
        <aside className={others.length || sections.length === 0 ? 'lg:col-span-4' : undefined}>
          <Panel title="Bildirimler" to="/notifications">
            {notifications.isPending ? <Skeleton rows={3} /> : <RecentNotifications list={notifications.data ?? []} />}
          </Panel>
        </aside>
      </div>
    </>
  )
}
