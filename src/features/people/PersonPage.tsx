import { Link, Navigate, useParams } from 'react-router-dom'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { formatInstant } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { LEVEL_LABEL } from '@/features/auth/register/api'
import { useCurrentTerm } from '@/features/courses/api'
import { STAFF_CATEGORY_LABEL, STATUS_LABEL } from '@/features/profile/api'
import { Avatar } from '@/features/profile/Avatar'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, QueryBoundary, Skeleton } from '@/components/ui/States'
import { isAcademician, isStudent, usePersonCourses, usePersonExtras, usePersonProfile, type PersonExtras, type PersonProfile } from './api'
import { titleLabel } from './titles'

/**
 * Kişi profili: başkasının kimlik bandı, hakkında, iletişim (görünürse), son gönderiler, puan ve rozetler.
 * Neyin görüneceği sunucuda belirlenir (F-92); boş gelen alanın bölümü hiç çizilmez, liderlik tablosundan çıkan
 * kişi için "gizliyor" gibi bir şey de yazılmaz. Kendi kimliğiniz Profilim'e yönlenir.
 */
export function PersonPage() {
  const { userId = '' } = useParams()
  const session = useSession()
  const profile = usePersonProfile(userId)
  const name = profile.data ? [profile.data.firstName, profile.data.lastName].filter(Boolean).join(' ') : ''
  usePageTitle(name || 'Profil')

  if (session?.userId === userId) return <Navigate to="/profile" replace />
  if (profile.isError && toApiError(profile.error).status === 404) {
    return (
      <div className="mx-auto max-w-[56rem]">
        <EmptyState
          title="Bu profil bulunamadı"
          action={
            <Button asChild>
              <Link to="/">Bugün ekranına dön</Link>
            </Button>
          }
        >
          Hesap kapatılmış olabilir ya da bu bir görevli hesabıdır; görevli hesaplarının kişisel profili yoktur.
        </EmptyState>
      </div>
    )
  }
  return (
    <div className="mx-auto max-w-[80rem]">
      <QueryBoundary query={profile} what="Profil" skeletonRows={5}>
        {(p) => <Person person={p} />}
      </QueryBoundary>
    </div>
  )
}

function Person({ person: p }: { person: PersonProfile }) {
  const extras = usePersonExtras(p.id, true)
  const name = [p.firstName, p.lastName].filter(Boolean).join(' ') || 'Kullanıcı'
  const staff = isAcademician(p)
  const student = isStudent(p)
  const title = titleLabel(p.title, p.academicTitle)
  const kicker = staff
    ? [title, p.staffCategory ? STAFF_CATEGORY_LABEL[p.staffCategory] : null].filter(Boolean).join(', ')
    : [p.programName, p.programLevel ? LEVEL_LABEL[p.programLevel as keyof typeof LEVEL_LABEL] : null].filter(Boolean).join(', ')
  const contact = staff ? !!(p.email || p.officeNumber || p.officeHours) : !!p.email

  return (
    <>
      <section className="min-w-0">
        <Avatar name={name} src={p.profileImageUrl} size="lg" />
        {kicker && (
          <p className="mt-6 flex items-center gap-3 text-md">
            <span aria-hidden className="h-5 w-[4px] bg-ink" />
            <span className="font-semibold">{kicker}</span>
          </p>
        )}
        <RevealTitle text={name} className="mt-1 text-5xl sm:text-6xl" />
        <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
          {p.facultyName && <span>{p.facultyName}</span>}
          {staff && p.department && <span>{p.department}</span>}
          {student && p.classYear && <span>{p.classYear}. sınıf</span>}
          {p.studentNumber && <span className="tabular">{p.studentNumber}</span>}
          {staff && student && <Badge tone="info">Personel ve öğrenci</Badge>}
          {p.studentStatus && p.studentStatus !== 'ACTIVE' && <Badge tone="warning">{STATUS_LABEL[p.studentStatus] ?? p.studentStatus}</Badge>}
        </p>
        {p.bio && <p className="mt-6 max-w-[60ch] text-lg whitespace-pre-line">{p.bio}</p>}
      </section>

      <div className="mt-16 grid gap-20 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-20 lg:col-span-8">
          {contact && (
            <Panel title="İletişim">
              <dl className="max-w-[40rem]">
                {p.email && (
                  <Row label="E-posta">
                    <a href={`mailto:${p.email}`} className="underline-offset-4 hover:underline">
                      {p.email}
                    </a>
                  </Row>
                )}
                {staff && p.officeNumber && <Row label="Ofis">{p.officeNumber}</Row>}
                {staff && p.officeHours && (
                  <Row label="Görüşme saatleri">
                    <span className="whitespace-pre-line">{p.officeHours}</span>
                  </Row>
                )}
              </dl>
            </Panel>
          )}
          <Posts extras={extras.data} loading={extras.isPending} />
        </div>
        <aside className="flex flex-col gap-20 lg:col-span-4">
          {staff && <Teaching id={p.id} />}
          <Contribution extras={extras.data} loading={extras.isPending} showPoints={student} />
        </aside>
      </div>
    </>
  )
}

function Posts({ extras, loading }: { extras?: PersonExtras; loading: boolean }) {
  if (loading) return <Skeleton rows={3} />
  const posts = extras?.recentPosts ?? []
  return (
    <Panel title="Son gönderiler">
      {posts.length === 0 ? (
        <p className="text-ink-3">Akışta henüz paylaşımı yok.</p>
      ) : (
        <ul className="border-t border-rule">
          {posts.map((post) => (
            <li key={post.id} className="border-b border-rule">
              <Link to={`/posts/${post.id}`} className="row-fill block px-1 py-4">
                <span className="block text-lg font-semibold">{post.title}</span>
                <span className="mt-1 line-clamp-2 block max-w-[64ch] text-md text-ink-2">{post.content}</span>
                <span className="tabular mt-1 block text-sm text-ink-3">{formatInstant(post.createdAt, 'date')}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  )
}

/** Bu dönem koordinatörü ya da hocası olduğu dersler. */
function Teaching({ id }: { id: string }) {
  const term = useCurrentTerm()
  const courses = usePersonCourses(id)
  const list = (courses.data ?? []).filter((c) => c.termId === term.data?.id).sort((a, b) => a.code.localeCompare(b.code, 'tr'))
  if (list.length === 0) return null
  return (
    <Panel title="Bu dönem verdiği dersler">
      <ul className="border-t border-rule">
        {list.map((c) => (
          <li key={c.id} className="border-b border-rule">
            <Link to={`/courses/${c.id}`} className="row-fill grid grid-cols-[6.5rem_minmax(0,1fr)] gap-x-4 px-1 py-3">
              <span className="tabular font-heavy">{c.code}</span>
              <span className="min-w-0">
                <span className="block font-semibold">{c.title}</span>
                {c.section && <span className="block text-sm text-ink-3">Şube {c.section}</span>}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  )
}

/** Katkı: toplam puan ve rozetler. Puan `null` ise (liderlik tablosundan çıkmış) sıfır gibi gösterilmez, bölüm hiç çizilmez. */
function Contribution({ extras, loading, showPoints }: { extras?: PersonExtras; loading: boolean; showPoints: boolean }) {
  if (loading) return null
  const points = showPoints ? (extras?.gamification?.totalPoints ?? null) : null
  const badges = extras?.gamification?.badges ?? []
  if (points === null && badges.length === 0) return null
  return (
    <Panel title="Katkı">
      {points !== null && (
        <p className="mb-5">
          <span className="tabular text-6xl leading-none font-heavy">{points.toLocaleString('tr-TR')}</span>
          <span className="ml-2 text-xl text-ink-3">puan</span>
        </p>
      )}
      <ul>
        {badges.map((b) => (
          <li key={b.badgeType} className="flex items-start gap-4 border-b border-rule py-4 first:pt-0">
            {b.imageUrl && /^(https?:\/\/|\/)/.test(b.imageUrl) ? (
              <img src={b.imageUrl} alt="" className="size-12 shrink-0 object-contain" />
            ) : (
              <span aria-hidden className="mt-1 h-8 w-[4px] shrink-0 bg-topluluk" />
            )}
            <span className="min-w-0">
              <span className="block text-lg font-semibold">{b.name}</span>
              {b.description && <span className="block text-sm text-ink-2">{b.description}</span>}
              {b.earnedAt && <span className="block text-sm text-ink-3">{formatInstant(b.earnedAt, 'date')}</span>}
            </span>
          </li>
        ))}
      </ul>
    </Panel>
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
