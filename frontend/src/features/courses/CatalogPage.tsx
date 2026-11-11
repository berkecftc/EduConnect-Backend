import { useId, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { motion } from 'motion/react'
import { ChevronDown } from 'lucide-react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatLocal, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { transition } from '@/design/motion'
import { useMe } from '@/features/me/useMe'
import { withTitle } from '@/features/people/titles'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Meter } from '@/components/ui/Meter'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Applications } from './Applications'
import {
  COURSE_STATUS_LABEL,
  enrollmentOpen,
  isRunning,
  useApplyToCourse,
  useCatalog,
  useCurrentTerm,
  useMyApplications,
  useMyCourses,
  useTerms,
  type Course,
  type Term,
} from './api'

/** Satırın sağındaki durum: başvurulabilir mi, değilse neden. */
type Availability =
  | { kind: 'apply' }
  | { kind: 'enrolled' }
  | { kind: 'pending' }
  | { kind: 'closed'; label: string }
  | { kind: 'full' }
  | { kind: 'none' }

const ROW =
  'grid grid-cols-[minmax(0,1fr)_auto] gap-x-6 gap-y-3 lg:grid-cols-[8.5rem_minmax(0,1fr)_10rem_11rem] lg:items-start'

/** Ders kataloğu (öğrenci): dönemin açılan dersleri ders koduna göre gruplu; başvuru ve başvurularım (F-41, F-44). */
export function CatalogPage() {
  usePageTitle('Ders kataloğu')
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT')
  const [params, setParams] = useSearchParams()
  const terms = useTerms()
  const current = useCurrentTerm()
  const applications = useMyApplications()

  // Seçili dönem: adresteki `donem`, yoksa güncel dönem; güncel dönem yoksa (NO_TERM) tüm dönemler.
  const termId = params.get('donem') ?? (current.isPending ? undefined : (current.data?.id ?? ''))
  const term = terms.data?.find((t) => t.id === termId) ?? (current.data?.id === termId ? current.data : undefined)
  const pendingCount = (applications.data ?? []).filter((a) => a.status === 'PENDING').length

  const setTerm = (id: string) => {
    const next = new URLSearchParams(params)
    if (id === current.data?.id) next.delete('donem')
    else next.set('donem', id)
    setParams(next, { replace: true })
  }

  const catalog = (
    <Catalog termId={termId} term={term} terms={terms.data ?? []} onTermChange={setTerm} student={student} />
  )

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader title="Ders kataloğu" meta={term ? <TermLine term={term} /> : undefined} />
      <div className="mt-8">
        {student ? (
          <Tabs
            label="Katalog bölümleri"
            tabs={[
              { value: 'dersler', label: 'Dersler', content: catalog },
              {
                value: 'basvurular',
                label: 'Başvurularım',
                count: pendingCount || undefined,
                content: (
                  <QueryBoundary query={applications} what="Başvurular">
                    {(list) =>
                      list.length === 0 ? (
                        <EmptyState title="Henüz başvurunuz yok">
                          Katalogdan bir derse başvurduğunuzda burada görünür. Hoca onayladığında ders Derslerim'e eklenir.
                        </EmptyState>
                      ) : (
                        <Applications list={list} />
                      )
                    }
                  </QueryBoundary>
                ),
              },
            ]}
          />
        ) : (
          catalog
        )}
      </div>
    </div>
  )
}

/** Dönem ve kayıt penceresi: "2026-2027 Güz. Kayıt dönemi 14 Eylül'e kadar açık." */
function TermLine({ term }: { term: Term }) {
  const today = nowLocalIso().slice(0, 10)
  const open = enrollmentOpen(term, today)
  const opens = term.enrollmentOpensOn ? formatLocal(term.enrollmentOpensOn, 'date') : null
  const closes = term.enrollmentClosesOn ? formatLocal(term.enrollmentClosesOn, 'date') : null
  let text: string
  if (open) text = closes ? `Kayıt dönemi ${closes} tarihine kadar açık.` : 'Kayıt dönemi açık.'
  else if (opens && today < term.enrollmentOpensOn!) text = `Kayıt dönemi ${opens} tarihinde açılıyor.`
  else text = closes ? `Kayıt dönemi ${closes} tarihinde kapandı.` : 'Kayıt dönemi kapalı.'
  return (
    <span className="flex flex-wrap items-center gap-x-5 gap-y-1">
      <span>{term.label}</span>
      <Badge tone={open ? 'success' : 'neutral'}>{text}</Badge>
    </span>
  )
}

function Catalog({
  termId,
  term,
  terms,
  onTermChange,
  student,
}: {
  termId: string | undefined
  term: Term | undefined
  terms: Term[]
  onTermChange: (id: string) => void
  student: boolean
}) {
  const catalog = useCatalog(termId)
  const myCourses = useMyCourses()
  const applications = useMyApplications()
  const { data: me } = useMe()
  const [q, setQ] = useState('')
  const [onlyOpen, setOnlyOpen] = useState(false)
  const onlyOpenId = useId()

  const today = nowLocalIso().slice(0, 10)
  const windowOpen = enrollmentOpen(term, today)
  const onLeave = me?.studentStatus === 'ON_LEAVE'
  const enrolled = useMemo(() => new Set((myCourses.data ?? []).map((c) => c.id)), [myCourses.data])
  const pending = useMemo(
    () => new Set((applications.data ?? []).filter((a) => a.status === 'PENDING').map((a) => a.courseId)),
    [applications.data],
  )

  const availability = (c: Course): Availability => {
    if (!student) return { kind: 'none' }
    if (enrolled.has(c.id)) return { kind: 'enrolled' }
    if (pending.has(c.id)) return { kind: 'pending' }
    if (!isRunning(c.status)) return { kind: 'closed', label: COURSE_STATUS_LABEL[c.status] }
    if (c.enrolledStudentCount >= c.capacity) return { kind: 'full' }
    if (onLeave) return { kind: 'closed', label: 'Kaydınız dondurulmuş' }
    if (!windowOpen) return { kind: 'closed', label: 'Kayıt dönemi dışında' }
    return { kind: 'apply' }
  }

  return (
    <>
      <div className="grid gap-4 border-b border-rule pb-6 sm:grid-cols-[minmax(0,1fr)_16rem] sm:items-end">
        <Field label="Ara">
          <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} placeholder="Ders kodu, ders adı ya da hoca" />
        </Field>
        <Field label="Dönem">
          <Select value={termId ?? ''} onChange={(e) => onTermChange(e.target.value)} disabled={terms.length === 0}>
            {terms.length === 0 && <option value={termId ?? ''}>{term?.label ?? 'Dönemler yükleniyor'}</option>}
            {terms.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
              </option>
            ))}
          </Select>
        </Field>
        {student && (
          <label htmlFor={onlyOpenId} className="flex w-fit cursor-pointer items-center gap-2.5 text-md sm:col-span-2">
            <input
              id={onlyOpenId}
              type="checkbox"
              checked={onlyOpen}
              onChange={(e) => setOnlyOpen(e.target.checked)}
              className="size-[18px] cursor-pointer accent-[var(--ec-ink)]"
            />
            Yalnız başvurabileceğim dersler
          </label>
        )}
      </div>

      <div className="mt-2">
        {termId === undefined ? (
          <EmptyState title="Dönem bilgisi yükleniyor" />
        ) : (
          <QueryBoundary query={catalog} what="Katalog" skeletonRows={5}>
            {(list) => {
              const needle = q.trim().toLocaleLowerCase('tr-TR')
              const shown = list
                .filter((c) => !needle || [c.code, c.title, c.instructorName ?? ''].some((s) => s.toLocaleLowerCase('tr-TR').includes(needle)))
                .filter((c) => !onlyOpen || availability(c).kind === 'apply')
                .sort((a, b) => a.code.localeCompare(b.code, 'tr-TR') || (a.section ?? '').localeCompare(b.section ?? '', 'tr-TR'))
              if (list.length === 0) {
                return (
                  <EmptyState title="Bu dönem için açılmış ders yok">
                    Dersler hocalar tarafından yayımlandıkça burada görünür. Başka bir dönemi seçebilirsiniz.
                  </EmptyState>
                )
              }
              if (shown.length === 0) {
                return (
                  <EmptyState
                    title="Eşleşen ders yok"
                    action={
                      <Button
                        onClick={() => {
                          setQ('')
                          setOnlyOpen(false)
                        }}
                      >
                        Süzgeçleri temizle
                      </Button>
                    }
                  >
                    {onlyOpen ? 'Aramanıza uyan ve şu an başvurabileceğiniz bir ders bulunamadı.' : 'Aramanıza uyan bir ders bulunamadı.'}
                  </EmptyState>
                )
              }
              return (
                <>
                  <p className="tabular py-3 text-sm text-ink-3" aria-live="polite">
                    {shown.length === list.length ? `${list.length} ders` : `${shown.length} ders gösteriliyor, toplam ${list.length}`}
                  </p>
                  <div className="flex flex-col gap-12">
                    {groupByPrefix(shown).map(([prefix, courses]) => (
                      <CourseGroup key={prefix} prefix={prefix} courses={courses} availability={availability} />
                    ))}
                  </div>
                </>
              )
            }}
          </QueryBoundary>
        )}
      </div>
    </>
  )
}

/** Ders kodunun harf kısmına göre gruplar: "BİL 301" → "BİL". */
function groupByPrefix(list: Course[]): [string, Course[]][] {
  const groups = new Map<string, Course[]>()
  for (const c of list) {
    const prefix = c.code.match(/^\p{L}+/u)?.[0] ?? c.code
    groups.set(prefix, [...(groups.get(prefix) ?? []), c])
  }
  return [...groups.entries()]
}

function CourseGroup({ prefix, courses, availability }: { prefix: string; courses: Course[]; availability: (c: Course) => Availability }) {
  const id = useId()
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="flex items-baseline gap-4 border-b-2 border-ink pb-2">
        <span className="tabular text-4xl leading-none font-heavy tracking-[-0.03em]">{prefix}</span>
        <span className="tabular text-md text-ink-3">{courses.length} ders</span>
      </h2>
      <ul>
        {courses.map((c) => (
          <CatalogRow key={c.id} course={c} availability={availability(c)} />
        ))}
      </ul>
    </section>
  )
}

/**
 * Katalog satırı: ders kodu, ad ve hoca, kontenjan çizgisi, sağda başvuru ya da durum.
 * Açıklama varsa ders adı açılır kapanır düğmedir; başvuru düğmesi satırın içinde ayrı durur.
 */
function CatalogRow({ course: c, availability: av }: { course: Course; availability: Availability }) {
  const [open, setOpen] = useState(false)
  const detailId = useId()
  const left = Math.max(0, c.capacity - c.enrolledStudentCount)
  const hasDetail = !!c.description?.trim()

  const title = (
    <>
      <span className="block text-xl leading-tight font-heavy text-balance">{c.title}</span>
      <span className="mt-1 flex flex-wrap gap-x-4 text-md text-ink-3">
        {c.instructorName && <span>{withTitle(c.instructorName, c.instructorTitle, c.instructorAcademicTitle)}</span>}
        {c.section && <span>Şube {c.section}</span>}
        <span>
          {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
        </span>
      </span>
    </>
  )

  return (
    <li className="border-b border-rule">
      <div className={`${ROW} py-6`}>
        <span className="flex items-start gap-3">
          <span aria-hidden className="mt-0.5 h-6 w-[4px] shrink-0 bg-ders" />
          <span className="tabular text-2xl leading-none font-heavy whitespace-nowrap">{c.code}</span>
        </span>

        <div className="col-span-2 row-start-2 min-w-0 lg:col-span-1 lg:row-start-auto">
          {hasDetail ? (
            <button
              type="button"
              aria-expanded={open}
              aria-controls={detailId}
              onClick={() => setOpen((o) => !o)}
              className="group flex w-full items-start justify-between gap-4 text-left"
            >
              <span className="min-w-0">{title}</span>
              <ChevronDown
                aria-hidden
                className={cn('mt-1 size-5 shrink-0 text-ink-3 transition-transform duration-300 ease-rail group-hover:text-ink', open && 'rotate-180')}
              />
            </button>
          ) : (
            title
          )}
          {hasDetail && open && (
            <motion.p
              id={detailId}
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              transition={transition.base}
              className="mt-4 max-w-[64ch] text-md whitespace-pre-line text-ink-2"
            >
              {c.description}
            </motion.p>
          )}
        </div>

        <div className="col-span-2 row-start-3 lg:col-span-1 lg:row-start-auto">
          <p className="flex items-baseline justify-between gap-3">
            <span className="tabular text-lg font-heavy">
              {formatNumber(c.enrolledStudentCount)}
              <span className="font-semibold text-ink-3"> / {formatNumber(c.capacity)}</span>
            </span>
            <span className={cn('text-sm', left === 0 ? 'font-semibold text-danger' : 'text-ink-3')}>
              {left === 0 ? 'Dolu' : `${formatNumber(left)} yer`}
            </span>
          </p>
          <div className="mt-2">
            <Meter
              value={c.enrolledStudentCount}
              max={Math.max(1, c.capacity)}
              color={left === 0 ? 'var(--ec-danger)' : 'var(--ec-ink-3)'}
              label={`Kontenjan ${formatNumber(c.capacity)}, kayıtlı ${formatNumber(c.enrolledStudentCount)}`}
            />
          </div>
        </div>

        <div className="row-start-1 col-start-2 flex justify-end lg:row-start-auto lg:col-start-auto">
          <Action course={c} availability={av} />
        </div>
      </div>
    </li>
  )
}

function Action({ course: c, availability: av }: { course: Course; availability: Availability }) {
  const apply = useApplyToCourse()
  switch (av.kind) {
    case 'none':
      return null
    case 'enrolled':
      return (
        <Link to={`/courses/${c.id}`} className="text-right text-sm font-semibold text-success underline-offset-4 hover:underline">
          Kayıtlısınız, derse git
        </Link>
      )
    case 'pending':
      return <Badge tone="warning">Hoca onayında</Badge>
    case 'full':
      return <Badge tone="danger">Kontenjan dolu</Badge>
    case 'closed':
      return <span className="text-right text-sm text-ink-3">{av.label}</span>
    case 'apply':
      return (
        <Button
          variant="primary"
          loading={apply.isPending}
          onClick={() =>
            apply.mutate(c.id, {
              onSuccess: () => toast.success(`${c.code} başvurunuz alındı`, { description: 'Hoca onayladığında bildirim alacaksınız.' }),
              onError: (e) => toast.error(toApiError(e).message),
            })
          }
        >
          Başvur
        </Button>
      )
  }
}
