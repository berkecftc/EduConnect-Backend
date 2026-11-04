import { useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Download, ExternalLink } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocal, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useMyAssignments, useMyGrades, TYPE_LABEL, type MyAssignment, type MyGrades } from '@/features/assignments/api'
import { setsAwaitingChoice } from '@/features/assignments/groups'
import { viewAssignment, type AssignmentView } from '@/features/assignments/model'
import { useGroupSets } from '@/features/assignments/staff'
import { Meter } from '@/components/ui/Meter'
import { withTitle } from '@/features/people/titles'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { AssignmentRow } from '@/features/assignments/AssignmentRow'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { EmptyState, QueryBoundary, Skeleton } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Textarea } from '@/components/ui/Textarea'
import {
  COURSE_STATUS_LABEL,
  COURSE_STATUS_TONE,
  STAFF_ROLE_LABEL,
  groupMaterials,
  isRunning,
  useAnnouncements,
  useCourse,
  useCourseStaff,
  useMaterials,
  useWithdrawFromCourse,
  type Course,
  type Material,
} from './api'
import { PersonLink } from '@/features/people/PersonLink'
import { StudentGroups } from './StudentGroups'

/** Ders sayfası (öğrenci): genel bakış ve kadro, ödevler, materyaller, notlarım, duyurular; derste grup seti varsa gruplar. */
export function CoursePage() {
  const { courseId = '' } = useParams()
  const course = useCourse(courseId)
  usePageTitle(course.data ? `${course.data.code} ${course.data.title}` : 'Ders')

  return (
    <div className="mx-auto max-w-[72rem]">
      <Link to="/courses" className="text-sm font-semibold text-ders underline-offset-2 hover:underline">
        Derslerim
      </Link>
      <div className="mt-8">
        <QueryBoundary query={course} what="Ders" skeletonRows={2}>
          {(c) => <CourseBody course={c} />}
        </QueryBoundary>
      </div>
    </div>
  )
}

function CourseBody({ course: c }: { course: Course }) {
  const assignments = useMyAssignments()
  const groupSets = useGroupSets(c.id).data ?? []
  const awaitingGroup = isRunning(c.status) ? setsAwaitingChoice(groupSets).length : 0
  const mine = useMemo(
    () => (assignments.data ?? []).filter((a) => a.courseId === c.id).sort((a, b) => a.effectiveDueDate.localeCompare(b.effectiveDueDate)),
    [assignments.data, c.id],
  )
  const now = nowLocalIso()
  const next = mine
    .map((a) => ({ a, v: viewAssignment(a, now) }))
    .filter(({ v }) => !v.submitted && v.canSubmit)
    .sort((x, y) => x.v.actionAt.localeCompare(y.v.actionAt))[0]

  return (
    <>
      <CourseHero course={c} next={next} />

      <div className="mt-8">
        <Tabs
          label="Ders bölümleri"
          tabs={[
            { value: 'genel', label: 'Genel bakış', content: <Overview course={c} /> },
            {
              value: 'odevler',
              label: 'Ödevler',
              count: assignments.data ? mine.length : undefined,
              content: assignments.isPending ? (
                <Skeleton />
              ) : mine.length === 0 ? (
                <EmptyState title="Bu derste henüz ödev yok">Hoca ödev eklediğinde burada ve bildirimlerde görürsünüz.</EmptyState>
              ) : (
                <ul className="border-t border-ink">
                  {mine.map((a) => (
                    <AssignmentRow key={a.id} assignment={a} showCourse={false} />
                  ))}
                </ul>
              ),
            },
            ...(groupSets.length > 0
              ? [
                  {
                    value: 'gruplar',
                    label: 'Gruplar',
                    count: awaitingGroup || undefined,
                    content: <StudentGroups course={c} sets={groupSets} assignments={mine} />,
                  },
                ]
              : []),
            { value: 'materyaller', label: 'Materyaller', content: <Materials courseId={c.id} /> },
            { value: 'notlar', label: 'Notlarım', content: <Grades courseId={c.id} /> },
            { value: 'duyurular', label: 'Duyurular', content: <Announcements courseId={c.id} /> },
          ]}
        />
      </div>
    </>
  )
}

/**
 * Ders başlık bandı: büyük ders kodu ve adı, dönem bilgileri; sağda ağırlıklı toplam halkası ve en yakın teslim.
 */
function CourseHero({ course: c, next }: { course: Course; next?: { a: MyAssignment; v: AssignmentView } }) {
  const grades = useMyGrades(c.id)
  const g = grades.data
  return (
    <section className="grid gap-10 border-b border-ink pb-10 lg:grid-cols-12">
      <div className="min-w-0 lg:col-span-8">
        <p className="flex items-center gap-4">
          <span aria-hidden className="h-14 w-[5px] bg-ders" />
          <span className="tabular text-6xl font-heavy">{c.code}</span>
        </p>
        <RevealTitle text={c.title} className="mt-1 text-4xl sm:text-5xl" />
        <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
          {c.instructorName && <span>{withTitle(c.instructorName, c.instructorTitle, c.instructorAcademicTitle)}</span>}
          {c.termLabel && <span>{c.termLabel}</span>}
          {c.section && <span>Şube {c.section}</span>}
          <span>
            {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
          </span>
          <Badge tone={COURSE_STATUS_TONE[c.status]}>{COURSE_STATUS_LABEL[c.status]}</Badge>
        </p>
      </div>
      <dl className="grid grid-cols-2 self-end border-t border-rule lg:col-span-4 lg:grid-cols-1 lg:border-t-0 lg:border-l lg:pl-8">
        <div className="py-4 lg:pt-0">
          <dd className="tabular text-6xl leading-none font-heavy">{g ? formatNumber(g.weightedTotal) : '–'}</dd>
          <dt className="mt-2 text-sm text-ink-3">{g ? `ilan edilen ${formatNumber(g.gradedWeight)} puan üzerinden` : 'puan'}</dt>
          <div className="mt-3 max-w-[14rem]">
            <Meter
              value={g?.weightedTotal ?? 0}
              evaluated={g?.gradedWeight ?? 0}
              label={g ? `İlan edilen ${formatNumber(g.gradedWeight)} puan üzerinden ${formatNumber(g.weightedTotal)}` : 'Notlar yükleniyor'}
            />
          </div>
        </div>
        <div className="border-l border-rule py-4 pl-5 lg:border-t lg:border-l-0 lg:pl-0">
          <dt className="text-sm text-ink-3">Sıradaki teslim</dt>
          <dd className="mt-1">
            {next ? (
              <Link to={`/courses/${c.id}/assignments/${next.a.id}`} className="block hover:underline">
                <span className="block text-lg font-semibold">{next.a.title}</span>
                <span className={next.v.status.tone === 'warning' ? 'font-semibold text-warning' : 'text-ink-2'}>{next.v.timing}</span>
              </Link>
            ) : (
              <span className="text-ink-2">Bekleyen teslim yok</span>
            )}
          </dd>
        </div>
      </dl>
    </section>
  )
}

function Overview({ course: c }: { course: Course }) {
  const staff = useCourseStaff(c.id)
  return (
    <div className="grid gap-10 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <section aria-labelledby="ders-aciklama">
        <h2 id="ders-aciklama" className="text-lg">
          Ders hakkında
        </h2>
        <p className="mt-2 max-w-[68ch] whitespace-pre-line text-ink-2">
          {c.description?.trim() || 'Hoca henüz ders açıklaması eklemedi.'}
        </p>
        <dl className="mt-6 grid max-w-[30rem] grid-cols-2 gap-y-3 text-md">
          <dt className="text-ink-3">Kontenjan</dt>
          <dd className="tabular">
            {c.enrolledStudentCount} / {c.capacity}
          </dd>
          {c.termLabel && (
            <>
              <dt className="text-ink-3">Dönem</dt>
              <dd>{c.termLabel}</dd>
            </>
          )}
        </dl>
        {isRunning(c.status) && (
          <div className="mt-10 border-t border-rule pt-6">
            <h3 className="text-md font-heavy">Ders kaydı</h3>
            <p className="mt-1 max-w-[60ch] text-md text-ink-2">
              Dersi bırakmak isterseniz kaydınızı buradan silebilirsiniz. Teslimleriniz kayıtlarda kalır.
            </p>
            <div className="mt-3">
              <WithdrawAction course={c} />
            </div>
          </div>
        )}
      </section>
      <section aria-labelledby="ders-kadro">
        <h2 id="ders-kadro" className="text-lg">
          Kadro
        </h2>
        <div className="mt-3">
          <QueryBoundary query={staff} what="Kadro" skeletonRows={2}>
            {(list) =>
              list.length === 0 ? (
                <p className="text-md text-ink-2">{withTitle(c.instructorName, c.instructorTitle, c.instructorAcademicTitle) || 'Kadro bilgisi yok.'}</p>
              ) : (
                <ul className="flex flex-col gap-3">
                  {list.map((s) => (
                    <li key={s.userId} className="flex items-start justify-between gap-3">
                      <span className="min-w-0">
                        <PersonLink id={s.userId} className="block font-semibold">
                          {withTitle(s.name, s.title, s.academicTitle)}
                        </PersonLink>
                        {s.department && <span className="block text-sm text-ink-3">{s.department}</span>}
                      </span>
                      <Badge tone={s.role === 'COORDINATOR' ? 'info' : 'neutral'}>{STAFF_ROLE_LABEL[s.role]}</Badge>
                    </li>
                  ))}
                </ul>
              )
            }
          </QueryBoundary>
        </div>
      </section>
    </div>
  )
}

function Materials({ courseId }: { courseId: string }) {
  const materials = useMaterials(courseId)
  const [busy, setBusy] = useState<string | null>(null)

  const download = async (m: Material) => {
    setBusy(m.id)
    try {
      await downloadFile(`/courses/${courseId}/materials/${m.id}/file`, m.fileName ?? m.title)
    } catch (e) {
      toast.error(toApiError(e).message)
    } finally {
      setBusy(null)
    }
  }

  return (
    <QueryBoundary query={materials} what="Materyaller">
      {(list) => {
        if (list.length === 0) return <EmptyState title="Henüz materyal yok">Hoca ders notu ya da bağlantı eklediğinde burada listelenir.</EmptyState>
        return (
          <div className="flex flex-col gap-8">
            {groupMaterials(list).map(([section, items]) => (
              <section key={section} aria-label={section}>
                <h2 className="border-b border-rule pb-2 text-md font-heavy">{section}</h2>
                <ul className="divide-y divide-rule">
                  {items.map((m) => (
                    <li key={m.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3">
                      <span className="min-w-0 flex-1">
                        <span className="block font-semibold">{m.title}</span>
                        {m.description && <span className="mt-0.5 block text-sm text-ink-2">{m.description}</span>}
                        <span className="mt-0.5 block text-xs text-ink-3">{formatInstant(m.updatedAt, 'date')} güncellendi</span>
                      </span>
                      {m.kind === 'LINK' && m.linkUrl ? (
                        <Button asChild size="sm">
                          <a href={m.linkUrl} target="_blank" rel="noopener noreferrer">
                            <ExternalLink className="size-4" aria-hidden />
                            Bağlantıyı aç<span className="sr-only"> (yeni sekmede)</span>
                          </a>
                        </Button>
                      ) : (
                        <Button size="sm" loading={busy === m.id} onClick={() => void download(m)}>
                          <Download className="size-4" aria-hidden />
                          İndir<span className="sr-only">: {m.fileName ?? m.title}</span>
                        </Button>
                      )}
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </div>
        )
      }}
    </QueryBoundary>
  )
}

function Grades({ courseId }: { courseId: string }) {
  const grades = useMyGrades(courseId)
  return <QueryBoundary query={grades} what="Notlar">{(g) => <GradeTable grades={g} courseId={courseId} />}</QueryBoundary>
}

/** Notlarım (F-47): yalnız ilan edilen puanlar; ağırlıklı toplam ve kaç puanlık kısmın değerlendirildiği. */
function GradeTable({ grades: g, courseId }: { grades: MyGrades; courseId: string }) {
  if (g.assessments.length === 0) return <EmptyState title="Henüz değerlendirme yok">Hoca ödev ya da sınav eklediğinde burada görünür.</EmptyState>
  const cell = new Map(g.grades.map((c) => [c.assignmentId, c]))
  return (
    <div>
      <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
        <p className="tabular text-4xl font-heavy">{formatNumber(g.weightedTotal)}</p>
        <p className="text-md text-ink-2">
          puan, ilan edilen <span className="tabular font-semibold text-ink">{formatNumber(g.gradedWeight)}</span> puan üzerinden
        </p>
      </div>
      <div className="mt-6 overflow-x-auto">
        <table className="w-full min-w-[34rem] text-left text-md">
          <caption className="sr-only">Değerlendirmeler ve puanlarım</caption>
          <thead>
            <tr className="border-b border-rule text-sm text-ink-3">
              <th scope="col" className="py-2 pr-3 font-semibold">Değerlendirme</th>
              <th scope="col" className="py-2 pr-3 font-semibold">Son tarih</th>
              <th scope="col" className="py-2 pr-3 text-right font-semibold">Ağırlık</th>
              <th scope="col" className="py-2 text-right font-semibold">Puan</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-rule">
            {g.assessments.map((a) => {
              const c = cell.get(a.id)
              let score: React.ReactNode
              if (!a.gradesPublished) score = <span className="text-ink-3">İlan edilmedi</span>
              else if (!c || c.status === 'NOT_SUBMITTED') score = <span className="text-danger">Teslim etmedi</span>
              else if (c.status === 'SUBMITTED') score = <span className="text-ink-3">Notlanmadı</span>
              else
                score = (
                  <span className="tabular font-semibold">
                    {c.late && c.finalGrade !== c.grade ? `${formatNumber(c.grade)} → ` : ''}
                    {formatNumber(c.finalGrade ?? c.grade)} / {formatNumber(a.maxPoints)}
                  </span>
                )
              return (
                <tr key={a.id}>
                  <th scope="row" className="py-3 pr-3 font-normal">
                    <Link to={`/courses/${courseId}/assignments/${a.id}`} className="font-semibold text-ink hover:underline">
                      {a.title}
                    </Link>
                    <span className="block text-sm text-ink-3">{TYPE_LABEL[a.type]}</span>
                  </th>
                  <td className="tabular py-3 pr-3 text-ink-2">{formatLocal(a.dueDate, 'datetime')}</td>
                  <td className="tabular py-3 pr-3 text-right">%{formatNumber(a.weight)}</td>
                  <td className="py-3 text-right">
                    {score}
                    {c?.late && a.gradesPublished && c.status === 'GRADED' && <Badge tone="warning" className="ml-2">Geç</Badge>}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function Announcements({ courseId }: { courseId: string }) {
  const list = useAnnouncements(courseId)
  return (
    <QueryBoundary query={list} what="Duyurular">
      {(items) =>
        items.length === 0 ? (
          <EmptyState title="Duyuru yok">Hoca ders duyurusu yaptığında burada ve bildirimlerde görünür.</EmptyState>
        ) : (
          <ul className="flex flex-col gap-6">
            {items.map((a) => (
              <li key={a.id} className="border-b border-rule pb-6 last:border-0">
                <h2 className="text-lg">{a.title}</h2>
                <p className="mt-0.5 text-sm text-ink-3">
                  {a.createdByName ? `${a.createdByName}, ` : ''}
                  {formatInstant(a.createdAt, 'datetime')}
                </p>
                <p className="mt-3 max-w-[68ch] whitespace-pre-line">{a.content}</p>
              </li>
            ))}
          </ul>
        )
      }
    </QueryBoundary>
  )
}

/** Dersten çekilme (F-44): isteğe bağlı gerekçeyle onay penceresi. */
function WithdrawAction({ course: c }: { course: Course }) {
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const withdraw = useWithdrawFromCourse(c.id)
  const navigate = useNavigate()
  return (
    <>
      <Button variant="secondary" onClick={() => setOpen(true)} className="text-danger">
        Dersten çekil
      </Button>
      <ConfirmDialog
        open={open}
        onOpenChange={setOpen}
        destructive
        title="Dersten çekil"
        description={
          <>
            <span className="font-semibold text-ink">{c.code}</span> dersinden kaydınız silinir. Teslimleriniz kayıtlarda kalır;
            kayıt dönemi kapandıysa transkriptte çekilme olarak görünebilir.
          </>
        }
        confirmLabel="Dersten çekil"
        loading={withdraw.isPending}
        onConfirm={() =>
          withdraw.mutate(reason.trim(), {
            onSuccess: () => {
              toast.success(`${c.code} dersinden çekildiniz`)
              navigate('/courses')
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      >
        <Field label="Gerekçe" hint="İsteğe bağlı; hocanız görür.">
          <Textarea maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </>
  )
}
