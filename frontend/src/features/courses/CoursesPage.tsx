import { Link } from 'react-router-dom'
import { usePageTitle } from '@/lib/usePageTitle'
import { nowLocalIso } from '@/lib/time'
import { formatNumber } from '@/lib/format'
import { useGradesFor, useMyAssignments } from '@/features/assignments/api'
import { viewAssignment } from '@/features/assignments/model'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { withTitle } from '@/features/people/titles'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import {
  COURSE_STATUS_LABEL,
  COURSE_STATUS_TONE,
  useCurrentTerm,
  useMyApplications,
  useMyCourses,
  type EnrolledCourse,
} from './api'

/** Derslerim (öğrenci): bu dönemin dersleri, geçmiş dönemler ayrı; başvurular altta (F-42, F-44). */
export function CoursesPage() {
  usePageTitle('Derslerim')
  const courses = useMyCourses()
  const term = useCurrentTerm()
  const applications = useMyApplications()
  const pending = (applications.data ?? []).filter((a) => a.status === 'PENDING').length

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Derslerim"
        meta={term.data ? `${term.data.label} dönemi` : undefined}
        actions={
          <Button asChild>
            <Link to="/courses/catalog">Ders kataloğu</Link>
          </Button>
        }
      />

      <div className="mt-8">
        <QueryBoundary query={courses} what="Dersler">
          {(list) => {
            // Süren dersler ya da güncel dönemin dersleri; dönem yüklenmeden tamamlanan/arşivlenenler geçmişe düşer.
            const current = list.filter((c) => c.status === 'OPEN' || c.status === 'ACTIVE' || (term.data ? c.termId === term.data.id : false))
            const past = list.filter((c) => !current.includes(c))
            if (list.length === 0) {
              return (
                <EmptyState
                  title="Kayıtlı dersiniz yok"
                  action={
                    <Button asChild variant="primary">
                      <Link to="/courses/catalog">Ders kataloğuna göz at</Link>
                    </Button>
                  }
                >
                  Katalogdan bu dönem açılan derslere başvurabilirsiniz. Hoca onayladığında ders burada görünür.
                </EmptyState>
              )
            }
            return (
              <>
                <CourseTiles courses={current} />
                {past.length > 0 && <CourseList courses={past} label="Geçmiş dönemler" className="mt-12" />}
              </>
            )
          }}
        </QueryBoundary>
      </div>

      {pending > 0 && (
        <p className="mt-12 flex flex-wrap items-baseline gap-x-4 gap-y-1 border-t-2 border-ink pt-4 text-lg">
          <span className="font-heavy">{pending} ders başvurunuz hoca onayında.</span>
          <Link to="/courses/catalog?sekme=basvurular" className="text-md font-semibold underline-offset-4 hover:underline">
            Başvurularım
          </Link>
        </p>
      )}
    </div>
  )
}

const BOARD = 'grid grid-cols-[minmax(0,1fr)_auto] gap-x-6 gap-y-3 lg:grid-cols-[13rem_minmax(0,1fr)_16rem_7rem]'

/**
 * Bu dönemin dersleri, kalkış tablosu gibi: hat çizgisi ve dev ders kodu, ders adı ve hoca,
 * sıradaki teslim ve kalan süre, ağırlıklı puan. Kart yok; satırlar ince çizgilerle ayrılır.
 */
function CourseTiles({ courses }: { courses: EnrolledCourse[] }) {
  const grades = useGradesFor(courses.map((c) => c.id))
  const assignments = useMyAssignments()
  const now = nowLocalIso()
  if (courses.length === 0) return null
  return (
    <section aria-label="Bu dönem">
      <div aria-hidden className={`${BOARD} hidden border-b border-ink pb-2 text-sm text-ink-3 lg:grid`}>
        <span>Ders</span>
        <span />
        <span>Sıradaki teslim</span>
        <span className="text-right">Puan</span>
      </div>
      <ul>
        {courses.map((c, i) => {
          const g = grades[i]?.data
          const next = (assignments.data ?? [])
            .filter((a) => a.courseId === c.id)
            .map((a) => ({ a, v: viewAssignment(a, now) }))
            .filter(({ v }) => !v.submitted && v.canSubmit)
            .sort((x, y) => x.v.actionAt.localeCompare(y.v.actionAt))[0]
          return (
            <li key={c.id} className="border-b border-rule">
              <Link to={`/courses/${c.id}`} className={`${BOARD} row-fill group py-7`}>
                <span className="flex items-start gap-3">
                  <span aria-hidden className="mt-1 h-8 w-[4px] shrink-0 bg-ders" />
                  <span className="tabular text-3xl leading-none font-heavy tracking-[-0.03em] whitespace-nowrap sm:text-4xl">{c.code}</span>
                </span>
                <span className="col-span-2 min-w-0 lg:col-span-1">
                  <span className="block text-2xl leading-tight font-heavy text-balance transition-transform duration-300 ease-rail group-hover:translate-x-1">
                    {c.title}
                  </span>
                  <span className="mt-1.5 flex flex-wrap gap-x-4 text-md text-ink-3">
                    <span>{withTitle(c.instructorName, c.instructorTitle, c.instructorAcademicTitle)}</span>
                    {c.section && <span>Şube {c.section}</span>}
                    <span>
                      {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
                    </span>
                  </span>
                </span>
                <span className="min-w-0 text-md">
                  {next ? (
                    <>
                      <span className="block truncate font-semibold">{next.a.title}</span>
                      <span className={next.v.status.tone === 'warning' ? 'font-semibold text-warning' : 'text-ink-2'}>{next.v.timing}</span>
                    </>
                  ) : (
                    <span className="text-ink-3">Bekleyen teslim yok</span>
                  )}
                </span>
                <span className="row-start-1 col-start-2 text-right lg:row-start-auto lg:col-start-auto">
                  <span className="tabular block text-4xl leading-none font-heavy">{g ? formatNumber(g.weightedTotal) : '–'}</span>
                  <span className="mt-1 block text-xs text-ink-3">{g ? `ilan edilen ${formatNumber(g.gradedWeight)} puan üzerinden` : ''}</span>
                </span>
              </Link>
            </li>
          )
        })}
      </ul>
    </section>
  )
}

function CourseList({ courses, label, className }: { courses: EnrolledCourse[]; label: string; className?: string }) {
  if (courses.length === 0) return null
  return (
    <section aria-label={label} className={className}>
      <h2 className="text-2xl">{label}</h2>
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
                  {c.instructorName && <span>{withTitle(c.instructorName, c.instructorTitle, c.instructorAcademicTitle)}</span>}
                  {c.section && <span>Şube {c.section}</span>}
                  <span>
                    {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
                  </span>
                  {c.termLabel && <span>{c.termLabel}</span>}
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
