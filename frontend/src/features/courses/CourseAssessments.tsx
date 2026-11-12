import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { formatNumber } from '@/lib/format'
import { formatLocal, nowLocalIso } from '@/lib/time'
import { TYPE_LABEL, type GradeCell } from '@/features/assignments/api'
import {
  columnProgress,
  remainingWeight,
  useCourseAssignments,
  useGradebook,
  type Gradebook,
  type StaffAssignment,
} from '@/features/assignments/staff'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Meter } from '@/components/ui/Meter'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import type { Course, StaffRole } from './api'
import { abilities, assessmentsEditable } from './teach'
import { PersonLink } from '@/features/people/PersonLink'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: 'UTC' })
const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'short', timeZone: 'UTC' })
const ROW = 'grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-5 gap-y-2 sm:grid-cols-[4rem_minmax(0,1fr)_12rem_10rem]'

/** Kadronun değerlendirme durumu: ilan edildi, notlama bekliyor, ilana hazır ya da teslim açık. */
function stateOf(a: StaffAssignment, p: { submitted: number; graded: number; waiting: number }, now: string): { label: string; tone: BadgeTone } {
  if (a.gradesPublishedAt) return { label: 'Puanlar ilan edildi', tone: 'success' }
  const closed = (a.lateUntil ?? a.dueDate) <= now
  const waiting = p.waiting
  if (waiting > 0 && closed) return { label: `${waiting} teslim notlanmadı`, tone: 'warning' }
  if (closed) return { label: p.submitted ? 'İlana hazır' : 'Teslim kapandı', tone: p.submitted ? 'info' : 'neutral' }
  return { label: 'Teslim açık', tone: 'neutral' }
}

/** Ödevler sekmesi (kadro): ağırlık toplamı, değerlendirmeler son teslim sırasıyla; satırda teslim ve notlama ilerlemesi. */
export function CourseAssessmentsTab({ course: c, role }: { course: Course; role: StaffRole }) {
  const list = useCourseAssignments(c.id)
  const book = useGradebook(c.id)
  const can = abilities(role).assignments && assessmentsEditable(c.status)
  const now = nowLocalIso()

  return (
    <QueryBoundary query={list} what="Değerlendirmeler">
      {(items) => {
        const used = 100 - remainingWeight(items)
        const sorted = [...items].sort((a, b) => a.dueDate.localeCompare(b.dueDate))
        return (
          <div className="flex flex-col gap-8">
            <div className="flex flex-wrap items-end justify-between gap-x-10 gap-y-5">
              <div className="min-w-[14rem]">
                <p className="flex items-baseline gap-2">
                  <span className="tabular text-4xl leading-none font-heavy">%{formatNumber(used)}</span>
                  <span className="text-md text-ink-3">ağırlık tanımlı</span>
                </p>
                <div className="mt-3 max-w-[16rem]">
                  <Meter value={used} label={`Dersin ağırlık toplamı yüzde ${formatNumber(used)}`} />
                </div>
                <p className="mt-2 text-sm text-ink-3">
                  {used >= 100 ? 'Toplam %100; yeni değerlendirme ağırlıksız (%0) eklenebilir.' : `Kalan %${formatNumber(100 - used)}.`}
                </p>
              </div>
              {can && (
                <Button asChild variant="primary">
                  <Link to={`/courses/${c.id}/assignments/new`}>Değerlendirme ekle</Link>
                </Button>
              )}
            </div>
            {sorted.length === 0 ? (
              <EmptyState title="Henüz değerlendirme yok">
                {can
                  ? 'Ödev, proje, kısa sınav ya da sınav ekleyin; öğrenciler bildirim alır.'
                  : 'Değerlendirmeleri koordinatör ve hocalar ekler.'}
              </EmptyState>
            ) : (
              <ul className="border-t border-ink">
                {sorted.map((a) => {
                  const p = columnProgress(book.data, a.id)
                  const s = stateOf(a, p, now)
                  const at = new Date(`${a.dueDate.slice(0, 10)}T12:00:00Z`)
                  return (
                    <li key={a.id} className="border-b border-rule">
                      <Link to={`/courses/${c.id}/assignments/${a.id}`} className={`${ROW} row-fill py-5`}>
                        <span className="row-span-2 sm:row-span-1">
                          <span className="tabular block text-3xl leading-none font-heavy">{DAY.format(at)}</span>
                          <span className="mt-1 block text-xs text-ink-3">
                            {MONTH.format(at)}, {formatLocal(a.dueDate, 'time')}
                          </span>
                        </span>
                        <span className="min-w-0">
                          <span className="block text-lg font-semibold">{a.title}</span>
                          <span className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
                            <span>{TYPE_LABEL[a.type]}</span>
                            <span>ağırlık %{formatNumber(a.weight)}</span>
                            <span>{formatNumber(a.maxPoints)} puan</span>
                            {a.groupSetId && <span>grup ödevi</span>}
                          </span>
                        </span>
                        <span className="col-start-2 min-w-0 sm:col-start-auto">
                          {book.data ? (
                            <>
                              <span className="tabular block text-md">
                                <span className="font-heavy">{p.submitted}</span>
                                <span className="text-ink-3"> / {p.students} teslim</span>
                                {p.submitted > 0 && <span className="text-ink-3">, {p.graded} notlandı</span>}
                              </span>
                              <span className="mt-2 block max-w-[10rem]">
                                <Meter
                                  value={p.graded}
                                  evaluated={p.submitted}
                                  max={Math.max(1, p.students)}
                                  label={`${p.students} öğrenciden ${p.submitted} teslim, ${p.graded} notlandı`}
                                />
                              </span>
                            </>
                          ) : (
                            <span className="text-sm text-ink-3">Teslimler yükleniyor</span>
                          )}
                        </span>
                        <span className="col-start-2 sm:col-start-auto sm:text-right">
                          <Badge tone={s.tone}>{s.label}</Badge>
                        </span>
                      </Link>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        )
      }}
    </QueryBoundary>
  )
}

/** Not defteri hücresi: puan / azami, teslim etmedi, notlanmadı; geç teslim işaretli. */
function Cell({ cell, max, open }: { cell: GradeCell | undefined; max: number; open: boolean }) {
  if (!cell || cell.status === 'NOT_SUBMITTED')
    return open ? (
      <span className="text-ink-3" aria-label="Son tarih gelmedi">
        –
      </span>
    ) : (
      <span className="text-sm text-danger">Teslim etmedi</span>
    )
  if (cell.status === 'SUBMITTED') return <span className="text-sm text-ink-3">Notlanmadı</span>
  const final = cell.finalGrade ?? cell.grade
  return (
    <span className="tabular whitespace-nowrap">
      <span className="font-semibold">{formatNumber(final)}</span>
      <span className="text-ink-3"> / {formatNumber(max)}</span>
      {cell.late && (
        <abbr title="Geç teslim" className="ml-1.5 text-xs font-semibold text-warning no-underline">
          G
        </abbr>
      )}
    </span>
  )
}

/** Not defteri (F-47): satır öğrenci, sütun değerlendirme; sonda ağırlıklı toplam. CSV indirilebilir. */
export function GradebookTab({ course: c }: { course: Course }) {
  const book = useGradebook(c.id)
  const [busy, setBusy] = useState(false)
  const csv = async () => {
    setBusy(true)
    try {
      await downloadFile(`/assignments/course/${c.id}/gradebook.csv`, `${c.code.replace(/\s+/g, '')}-not-defteri.csv`)
    } catch (e) {
      toast.error(toApiError(e).message)
    } finally {
      setBusy(false)
    }
  }
  return (
    <QueryBoundary query={book} what="Not defteri">
      {(b) =>
        b.assessments.length === 0 ? (
          <EmptyState title="Not defteri boş">Değerlendirme ekledikçe sütunlar burada oluşur.</EmptyState>
        ) : b.students.length === 0 ? (
          <EmptyState title="Kayıtlı öğrenci yok">Öğrenciler derse kaydoldukça satırlar burada oluşur.</EmptyState>
        ) : (
          <GradebookTable book={b} course={c} onCsv={() => void csv()} busy={busy} />
        )
      }
    </QueryBoundary>
  )
}

function GradebookTable({ book: b, course: c, onCsv, busy }: { book: Gradebook; course: Course; onCsv: () => void; busy: boolean }) {
  const columns = [...b.assessments].sort((x, y) => x.dueDate.localeCompare(y.dueDate))
  // Son tarihi gelmemiş sütunda teslim yokluğu eksik sayılmaz (kişisel uzatmalar not defterinde görünmez).
  const now = nowLocalIso()
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-3">
        <p className="max-w-[64ch] text-md text-ink-2">
          Toplam, ilan edilmiş ve edilmemiş bütün puanlarla hesaplanır; öğrenciler yalnız ilan edilenleri görür. “G” geç teslimdir, puan
          kesintili gösterilir; “–” son tarihi gelmemiş değerlendirmedir.
        </p>
        <Button size="sm" loading={busy} onClick={onCsv}>
          <Download className="size-4" aria-hidden />
          CSV indir
        </Button>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[40rem] border-collapse text-left text-md">
          <caption className="sr-only">
            {c.code} not defteri: {b.students.length} öğrenci, {columns.length} değerlendirme
          </caption>
          <thead>
            <tr className="border-b border-ink align-bottom text-sm">
              <th scope="col" className="sticky left-0 z-10 bg-canvas py-2 pr-4 font-semibold text-ink-3">
                Öğrenci
              </th>
              {columns.map((a) => (
                <th key={a.id} scope="col" className="min-w-[8.5rem] px-3 py-2 font-semibold">
                  <Link to={`/courses/${c.id}/assignments/${a.id}`} className="block leading-snug hover:underline">
                    {a.title}
                  </Link>
                  <span className="block font-normal text-ink-3">
                    %{formatNumber(a.weight)}
                    {!a.gradesPublished && ', ilan edilmedi'}
                  </span>
                </th>
              ))}
              <th scope="col" className="py-2 pl-3 text-right font-semibold text-ink-3">
                Toplam
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-rule">
            {b.students.map((r) => (
              <tr key={r.studentId}>
                <th scope="row" className="sticky left-0 z-10 bg-canvas py-3 pr-4 font-normal">
                  <PersonLink id={r.studentId} className="block font-semibold whitespace-nowrap">
                    {r.studentName ?? 'Öğrenci'}
                  </PersonLink>
                  <span className="tabular block text-sm text-ink-3">{r.studentNumber}</span>
                </th>
                {columns.map((a) => (
                  <td key={a.id} className="px-3 py-3">
                    <Cell cell={r.grades.find((g) => g.assignmentId === a.id)} max={a.maxPoints} open={a.dueDate > now} />
                  </td>
                ))}
                <td className="py-3 pl-3 text-right whitespace-nowrap">
                  <span className="tabular block text-lg font-heavy">{formatNumber(r.weightedTotal)}</span>
                  <span className="tabular block text-xs text-ink-3">%{formatNumber(r.gradedWeight)} üzerinden</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
