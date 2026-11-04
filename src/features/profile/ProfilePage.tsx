import { Link } from 'react-router-dom'
import { hasRole, useSession } from '@/lib/auth/session'
import { formatNumber } from '@/lib/format'
import { formatInstant } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { LEVEL_LABEL } from '@/features/auth/register/api'
import { isActiveMembership, roleLabel, useMyMemberships } from '@/features/clubs/api'
import { useMySummary } from '@/features/gamification/api'
import { CourseHistory } from '@/features/courses/CourseHistory'
import { useMe, type Me } from '@/features/me/useMe'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, Skeleton } from '@/components/ui/States'
import { STAFF_CATEGORY_LABEL, STATUS_LABEL } from './api'
import { Avatar } from './Avatar'

/** Profilim: kimlik bandı, sağda puan panosu (öğrenci); altta hakkımda, iletişim (personel), kulüpler ve rozetler. */
export function ProfilePage() {
  usePageTitle('Profilim')
  const session = useSession()
  const me = useMe()
  if (me.isPending) return <Skeleton rows={5} />
  if (!me.data) {
    return (
      <div className="mx-auto max-w-[56rem]">
        <EmptyState title="Bu hesapta profil yok" action={<Button asChild><Link to="/settings/account">Hesap ayarları</Link></Button>}>
          Yönetici ve görevli hesaplarının kişisel profili bulunmaz. Şifre ve bildirim ayarlarınızı Ayarlar'dan yönetebilirsiniz.
        </EmptyState>
      </div>
    )
  }
  return <Profile me={me.data} student={hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')} />
}

function Profile({ me, student }: { me: Me; student: boolean }) {
  const name = [me.firstName, me.lastName].filter(Boolean).join(' ') || 'Profilim'
  const staff = !!(me.academicTitle || me.staffCategory)
  const summary = useMySummary(student)
  const memberships = useMyMemberships(student)
  const clubs = (memberships.data ?? []).filter(isActiveMembership)
  const status = [me.studentStatus, me.staffStatus].filter((s): s is string => !!s && s !== 'ACTIVE')

  const kicker = staff
    ? [me.title, me.staffCategory ? STAFF_CATEGORY_LABEL[me.staffCategory] : null].filter(Boolean).join(', ')
    : [me.programName, me.programLevel ? LEVEL_LABEL[me.programLevel as keyof typeof LEVEL_LABEL] : null].filter(Boolean).join(', ')

  return (
    <div className="mx-auto max-w-[80rem]">
      <section className="grid gap-10 lg:grid-cols-12">
        <div className="min-w-0 lg:col-span-8">
          <Avatar name={name} src={me.profileImageUrl} size="lg" />
          {kicker && (
            <p className="mt-6 flex items-center gap-3 text-md">
              <span aria-hidden className="h-5 w-[4px] bg-ink" />
              <span className="font-semibold">{kicker}</span>
            </p>
          )}
          <RevealTitle text={name} className="mt-1 text-5xl sm:text-6xl" />
          <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
            {me.facultyName && <span>{me.facultyName}</span>}
            {staff && me.department && <span>{me.department}</span>}
            {me.studentNumber && <span className="tabular">{me.studentNumber}</span>}
            {me.classYear && <span>{me.classYear}. sınıf</span>}
            {me.affiliations && me.affiliations.length > 1 && <Badge tone="info">Personel ve öğrenci</Badge>}
            {status.map((s) => (
              <Badge key={s} tone="warning">
                {STATUS_LABEL[s] ?? s}
              </Badge>
            ))}
          </p>
          {me.bio && <p className="mt-6 max-w-[60ch] text-lg">{me.bio}</p>}
          <div className="mt-6 flex flex-wrap gap-x-5 gap-y-2">
            <Button asChild>
              <Link to="/settings/profile">Profili düzenle</Link>
            </Button>
          </div>
        </div>

        {student && (
          <dl className="self-end border-t border-rule pt-6 lg:col-span-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-8">
            <div>
              <dt className="text-sm text-ink-3">Puan</dt>
              <dd className="tabular mt-1 text-6xl leading-none font-heavy">{summary.data ? formatNumber(summary.data.totalPoints) : '–'}</dd>
            </div>
            <div className="mt-6 grid grid-cols-2 border-t border-rule pt-4">
              <div>
                <dt className="text-sm text-ink-3">Seri</dt>
                <dd className="tabular text-2xl font-heavy">{summary.data ? `${summary.data.currentStreak} hafta` : '–'}</dd>
              </div>
              <div className="border-l border-rule pl-5">
                <dt className="text-sm text-ink-3">En uzun seri</dt>
                <dd className="tabular text-2xl font-heavy">{summary.data ? `${summary.data.highestStreak} hafta` : '–'}</dd>
              </div>
            </div>
            <dd className="mt-4">
              <Link to="/leaderboard" className="text-md font-semibold underline-offset-4 hover:underline">
                Liderlik tablosu
              </Link>
            </dd>
          </dl>
        )}
      </section>

      <div className="mt-16 grid gap-20 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-20 lg:col-span-8">
          {staff && (
            <Panel title="İletişim">
              <dl className="max-w-[40rem]">
                <Row label="E-posta">{me.email ? <a href={`mailto:${me.email}`} className="underline-offset-4 hover:underline">{me.email}</a> : '–'}</Row>
                <Row label="Ofis">{me.officeNumber ?? '–'}</Row>
                <Row label="Görüşme saatleri">{me.officeHours ? <span className="whitespace-pre-line">{me.officeHours}</span> : '–'}</Row>
              </dl>
            </Panel>
          )}
          {student && <CourseHistory />}
          {student && (
            <Panel title="Rozetler">
              {summary.isPending ? (
                <Skeleton rows={2} />
              ) : !summary.data?.badges.length ? (
                <p className="text-ink-3">Henüz rozetiniz yok. Toplulukta soru cevaplamak ve kabul edilen cevaplar rozet kazandırır.</p>
              ) : (
                <ul className="grid gap-x-8 sm:grid-cols-2">
                  {summary.data.badges.map((b) => (
                    <li key={b.badgeType} className="flex items-start gap-4 border-b border-rule py-4">
                      {b.imageUrl && /^(https?:\/\/|\/)/.test(b.imageUrl) ? (
                        <img src={b.imageUrl} alt="" className="size-12 shrink-0 object-contain" />
                      ) : (
                        <span aria-hidden className="mt-1 h-8 w-[4px] shrink-0 bg-topluluk" />
                      )}
                      <span className="min-w-0">
                        <span className="block text-lg font-semibold">{b.name}</span>
                        <span className="block text-sm text-ink-2">{b.description}</span>
                        <span className="block text-sm text-ink-3">{formatInstant(b.earnedAt, 'date')}</span>
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          )}
        </div>
        {student && (
          <aside className="lg:col-span-4">
            <Panel title="Kulüpler" to="/clubs/mine" linkLabel="Kulüplerim">
              {memberships.isPending ? (
                <Skeleton rows={2} />
              ) : clubs.length === 0 ? (
                <p className="text-ink-3">Henüz bir kulübe üye değilsiniz.</p>
              ) : (
                <ul>
                  {clubs.map((m) => (
                    <li key={m.clubId} className="border-b border-rule py-3 first:pt-0">
                      <Link to={`/clubs/${m.clubId}`} className="block font-semibold underline-offset-4 hover:underline">
                        {m.clubName}
                      </Link>
                      <span className="text-sm text-ink-2">{roleLabel(m.clubRole)}</span>
                    </li>
                  ))}
                </ul>
              )}
            </Panel>
          </aside>
        )}
      </div>
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid gap-x-6 gap-y-0.5 border-b border-rule py-3 sm:grid-cols-[11rem_minmax(0,1fr)]">
      <dt className="text-md text-ink-3">{label}</dt>
      <dd className="text-md">{children}</dd>
    </div>
  )
}
