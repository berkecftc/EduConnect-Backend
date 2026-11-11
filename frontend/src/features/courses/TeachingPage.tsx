import { Link } from 'react-router-dom'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Meter } from '@/components/ui/Meter'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { COURSE_STATUS_LABEL, COURSE_STATUS_TONE, STAFF_ROLE_LABEL, isRunning, useCurrentTerm } from './api'
import { abilities, useCanOpenCourse, useTeachingCourses, type TeachingCourse } from './teach'

/** Verdiğim dersler (akademisyen, F-41…F-43): süren dersler tabloda; taslaklar ve geçmiş dönemler ayrı listede. */
export function TeachingPage() {
  usePageTitle('Verdiğim dersler')
  const courses = useTeachingCourses()
  const term = useCurrentTerm()
  const canOpen = useCanOpenCourse()

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Verdiğim dersler"
        meta={term.data ? `${term.data.label} dönemi` : undefined}
        actions={
          canOpen && (
            <Button asChild>
              <Link to="/courses/new">Ders aç</Link>
            </Button>
          )
        }
      />
      <div className="mt-8">
        <QueryBoundary query={courses} what="Dersler">
          {(list) => {
            if (list.length === 0) {
              return (
                <EmptyState
                  title="Henüz ders vermiyorsunuz"
                  action={
                    canOpen && (
                      <Button asChild variant="primary">
                        <Link to="/courses/new">Ders aç</Link>
                      </Button>
                    )
                  }
                >
                  {canOpen
                    ? 'Açtığınız dersler ve kadrosuna eklendiğiniz dersler burada görünür.'
                    : 'Bir dersin koordinatörü sizi kadroya eklediğinde ders burada görünür.'}
                </EmptyState>
              )
            }
            const running = list.filter((c) => isRunning(c.status))
            const drafts = list.filter((c) => c.status === 'DRAFT')
            const past = list.filter((c) => c.status === 'COMPLETED' || c.status === 'ARCHIVED')
            return (
              <>
                {running.length > 0 ? (
                  <Board courses={running} />
                ) : (
                  <p className="border-y border-rule py-6 text-md text-ink-2">Şu anda süren bir dersiniz yok.</p>
                )}
                <CompactList label="Taslaklar" courses={drafts} note="Yalnız ders kadrosu görür; yayımlayınca katalogda açılır." />
                <CompactList label="Geçmiş dönemler" courses={past} />
              </>
            )
          }}
        </QueryBoundary>
      </div>
    </div>
  )
}

const BOARD = 'grid grid-cols-[minmax(0,1fr)_auto] gap-x-6 gap-y-3 xl:grid-cols-[13rem_minmax(0,1fr)_13rem_8rem]'

/**
 * Süren dersler: dev ders kodu, ad ve göreviniz; kayıtlı öğrenci ve kontenjan doluluğu;
 * onay bekleyen başvuru sayısı (asistanlar başvuruları görmez).
 */
function Board({ courses }: { courses: TeachingCourse[] }) {
  return (
    <section aria-label="Süren dersler">
      <div aria-hidden className={`${BOARD} hidden border-b border-ink pb-2 text-sm text-ink-3 xl:grid`}>
        <span>Ders</span>
        <span />
        <span>Öğrenci</span>
        <span className="text-right">Bekleyen başvuru</span>
      </div>
      <ul>
        {courses.map((c) => {
          const can = abilities(c.staffRole)
          const full = c.enrolledStudentCount >= c.capacity
          return (
            <li key={c.id} className="border-b border-rule">
              <Link to={`/courses/${c.id}`} className={`${BOARD} row-fill group py-7`}>
                <span className="flex items-start gap-3">
                  <span aria-hidden className="mt-1 h-8 w-[4px] shrink-0 bg-ders" />
                  <span className="tabular text-3xl leading-none font-heavy tracking-[-0.03em] whitespace-nowrap sm:text-4xl">
                    {c.code}
                  </span>
                </span>
                <span className="col-span-2 min-w-0 xl:col-span-1">
                  <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                    <span className="text-2xl leading-tight font-heavy text-balance transition-transform duration-300 ease-rail group-hover:translate-x-1">
                      {c.title}
                    </span>
                    {c.status === 'OPEN' && <Badge tone={COURSE_STATUS_TONE[c.status]}>{COURSE_STATUS_LABEL[c.status]}</Badge>}
                  </span>
                  <span className="mt-1.5 flex flex-wrap gap-x-4 text-md text-ink-3">
                    <span className="font-semibold text-ink-2">{STAFF_ROLE_LABEL[c.staffRole]}</span>
                    {c.section && <span>Şube {c.section}</span>}
                    <span>
                      {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
                    </span>
                  </span>
                </span>
                <span className="col-span-2 min-w-0 xl:col-span-1">
                  <span className="flex items-baseline gap-2">
                    <span className="tabular text-2xl leading-none font-heavy">{c.enrolledStudentCount}</span>
                    <span className="tabular text-md text-ink-3">/ {c.capacity}</span>
                    {full && <span className="text-sm font-semibold text-warning">Dolu</span>}
                  </span>
                  <span className="mt-2 block max-w-[11rem]">
                    <Meter
                      value={c.enrolledStudentCount}
                      max={c.capacity}
                      label={`${c.capacity} kişilik kontenjanın ${c.enrolledStudentCount} kişisi dolu`}
                    />
                  </span>
                </span>
                <span className="row-start-1 col-start-2 text-right xl:row-start-auto xl:col-start-auto">
                  {can.applications ? (
                    <>
                      <span className={`tabular block text-4xl leading-none font-heavy ${c.pendingApplicationCount ? '' : 'text-ink-3'}`}>
                        {c.pendingApplicationCount || '–'}
                      </span>
                      <span className="mt-1 block text-xs text-ink-3">
                        {c.pendingApplicationCount ? 'onayınızı bekliyor' : 'bekleyen yok'}
                      </span>
                    </>
                  ) : (
                    <span className="sr-only">Başvuruları koordinatör ve hocalar değerlendirir</span>
                  )}
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function CompactList({ label, courses, note }: { label: string; courses: TeachingCourse[]; note?: string }) {
  if (courses.length === 0) return null
  return (
    <section aria-label={label} className="mt-14">
      <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <h2 className="text-2xl">{label}</h2>
        {note && <p className="text-md text-ink-3">{note}</p>}
      </div>
      <ul className="mt-3 divide-y divide-rule border-y border-t-ink">
        {courses.map((c) => (
          <li key={c.id}>
            <Link
              to={`/courses/${c.id}`}
              className="row-fill grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 gap-y-1 py-4 sm:grid-cols-[13rem_minmax(0,1fr)_auto]"
            >
              <span className="tabular text-md font-heavy text-ders">{c.code}</span>
              <span className="col-start-1 row-start-2 min-w-0 sm:col-start-2 sm:row-start-1">
                <span className="block text-base font-semibold text-ink">{c.title}</span>
                <span className="mt-0.5 flex flex-wrap gap-x-3 text-sm text-ink-3">
                  {c.termLabel && <span>{c.termLabel}</span>}
                  {c.section && <span>Şube {c.section}</span>}
                  <span>{STAFF_ROLE_LABEL[c.staffRole]}</span>
                  {c.status !== 'DRAFT' && <span className="tabular">{c.enrolledStudentCount} öğrenci</span>}
                </span>
              </span>
              <span className="row-span-2 sm:row-span-1">
                <Badge tone={COURSE_STATUS_TONE[c.status]}>{COURSE_STATUS_LABEL[c.status]}</Badge>
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
