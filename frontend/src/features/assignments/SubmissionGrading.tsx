import { useMemo, useState } from 'react'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocal, nowLocalIso } from '@/lib/time'
import { useEnrolledStudents } from '@/features/courses/teach'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Dialog } from '@/components/ui/Dialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { useSubmissionVersions } from './api'
import {
  gradeText,
  parseDecimal,
  useExtensions,
  useGradeHistory,
  useGradeSubmission,
  useGroupSets,
  useMemberGrade,
  useSubmissions,
  type StaffAssignment,
  type SubmissionSummary,
} from './staff'

type Row = { key: string; name: string; number: string | null; submission?: SubmissionSummary; studentId?: string; note?: string }
type Filter = 'hepsi' | 'bekleyen' | 'eksik'

const ROW = 'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 gap-y-2 py-4 md:grid-cols-[minmax(0,1fr)_11rem_9rem_7rem]'

/**
 * Teslimler (F-45…F-48, F-51): kayıtlı öğrenciler (grup ödevinde gruplar) ve teslimleri; süzgeçle notlanmayı bekleyenler
 * ya da teslim etmeyenler. Puanlama penceresi sırayla ilerler ("Kaydet ve sonraki").
 */
export function SubmissionsTab({
  assignment: a,
  canExtend,
  onExtend,
}: {
  assignment: StaffAssignment
  canExtend: boolean
  onExtend: (studentId: string) => void
}) {
  const submissions = useSubmissions(a.id)
  const students = useEnrolledStudents(a.courseId, !a.groupSetId)
  const sets = useGroupSets(a.courseId, !!a.groupSetId)
  const extensions = useExtensions(a.id)
  const [filter, setFilter] = useState<Filter>('hepsi')
  const [open, setOpen] = useState<string | null>(null)

  const rows = useMemo<Row[]>(() => {
    const subs = submissions.data ?? []
    if (a.groupSetId) {
      const set = sets.data?.find((s) => s.id === a.groupSetId)
      const byGroup = new Map(subs.map((s) => [s.groupId, s]))
      const groups = (set?.groups ?? []).map((g) => ({
        key: g.id,
        name: g.name,
        number: `${g.memberCount} üye`,
        submission: byGroup.get(g.id),
      }))
      const orphan = subs.filter((s) => !set?.groups.some((g) => g.id === s.groupId))
      return [
        ...groups,
        ...orphan.map((s) => ({ key: s.submissionId, name: s.groupName ?? s.studentName ?? 'Grup', number: null, submission: s })),
      ]
    }
    const byStudent = new Map(subs.map((s) => [s.studentId, s]))
    const enrolled = (students.data ?? []).map((s) => ({
      key: s.studentId,
      studentId: s.studentId,
      name: [s.firstName, s.lastName].filter(Boolean).join(' ') || 'Öğrenci',
      number: s.studentNumber,
      submission: byStudent.get(s.studentId),
    }))
    const left = subs
      .filter((s) => !(students.data ?? []).some((e) => e.studentId === s.studentId))
      .map((s) => ({
        key: s.submissionId,
        name: s.studentName ?? 'Öğrenci',
        number: s.studentNumber,
        submission: s,
        note: 'Dersten ayrıldı',
      }))
    return [...enrolled, ...left].sort((x, y) => x.name.localeCompare(y.name, 'tr'))
  }, [submissions.data, students.data, sets.data, a.groupSetId])

  const waiting = rows.filter((r) => r.submission && r.submission.grade == null)
  const missing = rows.filter((r) => !r.submission)
  const shown = filter === 'bekleyen' ? waiting : filter === 'eksik' ? missing : rows
  const queue = shown.filter((r) => r.submission).map((r) => r.submission!)
  const current = queue.find((s) => s.submissionId === open) ?? (submissions.data ?? []).find((s) => s.submissionId === open)
  const extended = new Map((extensions.data ?? []).map((e) => [e.studentId, e]))
  // Teslim süresi sürüyorsa teslim yokluğu henüz eksik sayılmaz.
  const windowOpen = (a.lateUntil ?? a.dueDate) > nowLocalIso()

  return (
    <QueryBoundary query={submissions} what="Teslimler">
      {() => (
        <div className="flex flex-col gap-6">
          <div role="group" aria-label="Süzgeç" className="flex flex-wrap gap-x-1 gap-y-2">
            {(
              [
                ['hepsi', 'Tümü', rows.length],
                ['bekleyen', 'Notlanmayı bekleyen', waiting.length],
                ['eksik', a.groupSetId ? 'Teslim etmeyen gruplar' : 'Teslim etmeyen', missing.length],
              ] as const
            ).map(([value, label, n]) => (
              <button
                key={value}
                type="button"
                aria-pressed={filter === value}
                onClick={() => setFilter(value)}
                className="h-10 rounded-sm px-3 text-md font-semibold text-ink-3 transition-colors hover:text-ink aria-pressed:bg-ink aria-pressed:text-canvas"
              >
                {label} <span className="tabular font-normal">{n}</span>
              </button>
            ))}
          </div>

          {shown.length === 0 ? (
            <EmptyState
              title={
                filter === 'bekleyen' ? 'Notlanmayı bekleyen teslim yok' : filter === 'eksik' ? 'Herkes teslim etti' : 'Henüz teslim yok'
              }
            >
              {filter === 'hepsi' ? 'Öğrenciler teslim ettikçe burada listelenir.' : 'Süzgeci değiştirerek diğer satırları görebilirsiniz.'}
            </EmptyState>
          ) : (
            <ul className="border-t border-ink">
              {shown.map((r) => {
                const s = r.submission
                const ext = r.studentId ? extended.get(r.studentId) : undefined
                return (
                  <li key={r.key} className={`${ROW} border-b border-rule`}>
                    <div className="min-w-0">
                      <p className="font-semibold">{r.name}</p>
                      <p className="mt-0.5 flex flex-wrap gap-x-3 text-sm text-ink-3">
                        {r.number && <span className="tabular">{r.number}</span>}
                        {r.note && <span>{r.note}</span>}
                        {s?.groupName && !a.groupSetId && <span>grup {s.groupName}</span>}
                      </p>
                    </div>
                    <div className="col-start-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm md:col-start-auto">
                      {s ? (
                        <>
                          <span className="tabular text-ink-2">{formatInstant(s.submittedAt, 'datetime')}</span>
                          {s.late && <Badge tone="warning">Geç</Badge>}
                          {s.aiUsed && <Badge tone="info">YZ beyanı</Badge>}
                        </>
                      ) : ext ? (
                        <span className="text-ink-2">Süre uzatıldı: {formatLocal(ext.dueDate, 'datetime')}</span>
                      ) : (
                        <span className={windowOpen ? 'text-ink-3' : 'text-danger'}>
                          {windowOpen ? 'Henüz teslim yok' : 'Teslim etmedi'}
                        </span>
                      )}
                    </div>
                    <div className="col-start-2 row-start-1 text-right md:col-start-auto md:row-start-auto md:text-left">
                      {s ? (
                        gradeText(s, a.maxPoints) ? (
                          <span className="tabular text-lg font-heavy whitespace-nowrap">{gradeText(s, a.maxPoints)}</span>
                        ) : (
                          <span className="text-sm text-ink-3">Notlanmadı</span>
                        )
                      ) : (
                        <span className="text-ink-3">–</span>
                      )}
                    </div>
                    <div className="col-start-2 text-right md:col-start-auto">
                      {s ? (
                        <Button size="sm" variant={s.grade == null ? 'primary' : 'secondary'} onClick={() => setOpen(s.submissionId)}>
                          {s.grade == null ? 'Notla' : 'İncele'}
                          <span className="sr-only">: {r.name}</span>
                        </Button>
                      ) : (
                        canExtend &&
                        r.studentId && (
                          <button
                            type="button"
                            onClick={() => onExtend(r.studentId!)}
                            className="text-sm font-semibold whitespace-nowrap text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                          >
                            Süre uzat<span className="sr-only">: {r.name}</span>
                          </button>
                        )
                      )}
                    </div>
                  </li>
                )
              })}
            </ul>
          )}
          {current && (
            <GradeDialog
              key={current.submissionId}
              assignment={a}
              submission={current}
              name={rows.find((r) => r.submission?.submissionId === current.submissionId)?.name ?? current.studentName ?? 'Öğrenci'}
              next={queue[queue.findIndex((s) => s.submissionId === current.submissionId) + 1]}
              onNext={(id) => setOpen(id)}
              onClose={() => setOpen(null)}
            />
          )}
        </div>
      )}
    </QueryBoundary>
  )
}

/** Teslimi okuma ve puanlama penceresi: dosya, metin, YZ beyanı, sürümler; puan, geri bildirim, gerekçe ve puan geçmişi. */
function GradeDialog({
  assignment: a,
  submission: s,
  name,
  next,
  onNext,
  onClose,
}: {
  assignment: StaffAssignment
  submission: SubmissionSummary
  name: string
  next?: SubmissionSummary
  onNext: (id: string) => void
  onClose: () => void
}) {
  const grade = useGradeSubmission(a)
  const versions = useSubmissionVersions(s.submissionId)
  const history = useGradeHistory(s.submissionId)
  const published = !!a.gradesPublishedAt
  const [value, setValue] = useState(s.grade == null ? '' : String(s.grade).replace('.', ','))
  const [feedback, setFeedback] = useState(s.feedback ?? '')
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<{ grade?: string; reason?: string; general?: string }>({})
  const [busy, setBusy] = useState<string | null>(null)
  const [goingNext, setGoingNext] = useState(false)

  const parsed = parseDecimal(value)
  const changed = parsed !== null && parsed !== s.grade
  const needsReason = published && s.grade != null && changed
  const penalty = a.latePenaltyPercent ?? 0
  const preview = s.late && penalty > 0 && parsed !== null ? Math.round(parsed * (100 - penalty)) / 100 : null

  const download = async (key: string, url: string, fallback: string) => {
    setBusy(key)
    try {
      await downloadFile(url, fallback)
    } catch (e) {
      toast.error(toApiError(e).message)
    } finally {
      setBusy(null)
    }
  }

  const save = (goNext: boolean) => {
    const next: typeof errors = {}
    if (parsed === null || parsed > a.maxPoints || Math.round(parsed * 100) !== parsed * 100)
      next.grade = `Puan 0 ile ${formatNumber(a.maxPoints)} arasında, en fazla iki ondalıklı olmalı.`
    if (needsReason && !reason.trim()) next.reason = 'İlan edilmiş puanı değiştirmek için gerekçe yazın; öğrenciye bildirimle gider.'
    setErrors(next)
    if (Object.keys(next).length) return
    setGoingNext(goNext)
    grade.mutate(
      { submissionId: s.submissionId, grade: parsed!, feedback, reason },
      {
        onSuccess: () => {
          toast.success(`${name}: ${formatNumber(parsed)} / ${formatNumber(a.maxPoints)}`)
          if (goNext && nextId) onNext(nextId)
          else onClose()
        },
        onError: (e) => {
          const ae = toApiError(e)
          if (ae.code === 'INVALID_GRADE') setErrors({ grade: ae.message })
          else if (ae.code === 'GRADE_CHANGE_REASON_REQUIRED') setErrors({ reason: ae.message })
          else setErrors({ general: ae.message })
        },
      },
    )
  }
  const nextId = next?.submissionId

  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={name}
      description={
        <span className="flex flex-wrap items-center gap-x-3 gap-y-1">
          {s.studentNumber && !s.groupId && <span className="tabular">{s.studentNumber}</span>}
          <span>{formatInstant(s.submittedAt, 'datetime')} teslim</span>
          {s.late && <Badge tone="warning">Geç teslim</Badge>}
        </span>
      }
      footer={
        <>
          {errors.general && <p className="mr-auto text-sm font-semibold text-danger">{errors.general}</p>}
          <Button variant="ghost" onClick={onClose}>
            Vazgeç
          </Button>
          {nextId && (
            <Button loading={grade.isPending && goingNext} disabled={grade.isPending} onClick={() => save(true)}>
              Kaydet ve sonraki
            </Button>
          )}
          <Button variant="primary" loading={grade.isPending && !goingNext} disabled={grade.isPending} onClick={() => save(false)}>
            Kaydet
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-7">
        <section aria-labelledby="teslim-baslik">
          <h3 id="teslim-baslik" className="text-md font-heavy">
            Teslim
          </h3>
          {s.submissionFileUrl && (
            <Button
              size="sm"
              className="mt-3"
              loading={busy === 'file'}
              onClick={() => void download('file', `/assignments/submissions/${s.submissionId}/file`, `${name}-teslim`)}
            >
              <Download className="size-4" aria-hidden />
              Dosyayı indir
            </Button>
          )}
          {s.textContent && (
            <div className="mt-3 max-h-[16rem] overflow-y-auto border-l-[3px] border-rule pl-4 text-md whitespace-pre-wrap">
              {s.textContent}
            </div>
          )}
          {!s.submissionFileUrl && !s.textContent && <p className="mt-2 text-md text-ink-2">Teslimde dosya ya da metin yok.</p>}
          {s.aiUsed !== null && (
            <p className="mt-3 text-md">
              <span className="font-semibold">
                {s.aiUsed ? 'Yapay zekâ kullandığını beyan etti.' : 'Yapay zekâ kullanmadığını beyan etti.'}
              </span>
              {s.aiNote && <span className="block text-ink-2">{s.aiNote}</span>}
            </p>
          )}
          {(versions.data?.length ?? 0) > 1 && (
            <details className="mt-4">
              <summary className="cursor-pointer text-sm font-semibold text-ink-2">Önceki sürümler ({versions.data!.length - 1})</summary>
              <ul className="mt-2 divide-y divide-rule border-y border-rule text-sm">
                {[...versions.data!]
                  .sort((x, y) => y.versionNo - x.versionNo)
                  .slice(1)
                  .map((v) => (
                    <li key={v.versionNo} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2">
                      <span className="font-semibold">Sürüm {v.versionNo}</span>
                      <span className="tabular text-ink-3">{formatInstant(v.submittedAt, 'datetime')}</span>
                      {v.late && <Badge tone="warning">Geç</Badge>}
                      {v.fileUrl && (
                        <button
                          type="button"
                          disabled={busy === v.fileUrl}
                          onClick={() =>
                            void download(
                              v.fileUrl!,
                              `/assignments/files/download?url=${encodeURIComponent(v.fileUrl!)}`,
                              `surum-${v.versionNo}`,
                            )
                          }
                          className="ml-auto font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                        >
                          İndir
                        </button>
                      )}
                    </li>
                  ))}
              </ul>
            </details>
          )}
        </section>

        <section aria-labelledby="puan-baslik" className="flex flex-col gap-5 border-t border-rule pt-6">
          <h3 id="puan-baslik" className="text-md font-heavy">
            Puan
          </h3>
          <Field
            label={`Puan (en fazla ${formatNumber(a.maxPoints)})`}
            required
            error={errors.grade}
            hint={preview !== null ? `Geç teslim: %${formatNumber(penalty)} kesintiyle öğrenci ${formatNumber(preview)} görür.` : undefined}
          >
            <Input
              autoFocus
              inputMode="decimal"
              maxLength={7}
              value={value}
              onChange={(e) => setValue(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  save(!!nextId)
                }
              }}
              className="max-w-[8rem]"
            />
          </Field>
          <Field label="Geri bildirim" hint={published ? 'Öğrenci hemen görür.' : 'Puanlar ilan edilince öğrenci görür.'}>
            <Textarea
              rows={4}
              maxLength={5000}
              valueLength={feedback.length}
              value={feedback}
              onChange={(e) => setFeedback(e.target.value)}
            />
          </Field>
          {published && s.grade != null && (
            <Field
              label="Değişiklik gerekçesi"
              required={needsReason}
              error={errors.reason}
              hint="Puanlar ilan edildi; puanı değiştirirseniz gerekçe öğrenciye bildirimle gider."
            >
              <Textarea rows={2} maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
            </Field>
          )}
          {(history.data?.length ?? 0) > 0 && (
            <div>
              <p className="text-sm font-semibold text-ink-3">Puan geçmişi</p>
              <ul className="mt-1 divide-y divide-rule text-sm">
                {history.data!.map((h, i) => (
                  <li key={i} className="flex flex-wrap gap-x-4 gap-y-0.5 py-2">
                    <span className="tabular text-ink-3">{formatInstant(h.changedAt, 'datetime')}</span>
                    {h.studentId && (
                      <span className="text-ink-2">
                        {s.members?.find((m) => m.studentId === h.studentId)?.studentName ?? 'Üye'} (kişisel)
                      </span>
                    )}
                    <span className="tabular font-semibold">
                      {h.oldGrade == null ? formatNumber(h.newGrade) : `${formatNumber(h.oldGrade)} → ${formatNumber(h.newGrade)}`}
                    </span>
                    {h.feedbackChanged && <span className="text-ink-2">geri bildirim değişti</span>}
                    {h.afterPublication && <span className="text-ink-2">ilandan sonra</span>}
                    {h.reason && <span className="basis-full text-ink-2">{h.reason}</span>}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
        {(s.members?.length ?? 0) > 0 && <MemberGrades assignment={a} submission={s} />}
      </div>
    </Dialog>
  )
}

/**
 * Grup üyeleri (F-51, F-87): her üye grubun puanını alır; katkısı farklı olan üyeye gerekçeyle kişisel puan verilir.
 * Kişisel puan kaldırılınca üye yeniden grubun puanını alır.
 */
function MemberGrades({ assignment: a, submission: s }: { assignment: StaffAssignment; submission: SubmissionSummary }) {
  const member = useMemberGrade(a)
  const [editing, setEditing] = useState<string | null>(null)
  const [value, setValue] = useState('')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const fail = (e: unknown) => setError(toApiError(e).message)

  const open = (studentId: string, current: number | null) => {
    setEditing(studentId)
    setValue(current == null ? '' : String(current).replace('.', ','))
    setReason('')
    setError(null)
  }

  return (
    <section aria-labelledby="uyeler-baslik" className="flex flex-col gap-3 border-t border-rule pt-6">
      <h3 id="uyeler-baslik" className="text-md font-heavy">
        Grup üyeleri
      </h3>
      <p className="text-sm text-ink-3">Herkes grubun puanını alır. Katkısı farklı olan üyeye gerekçeyle kişisel puan verebilirsiniz.</p>
      <ul className="divide-y divide-rule border-y border-rule">
        {s.members!.map((m) => (
          <li key={m.studentId} className="py-3">
            <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
              <span className="min-w-[10rem] flex-1">
                <span className="font-semibold">{m.studentName ?? 'Öğrenci'}</span>
                {m.studentNumber && <span className="tabular ml-2 text-sm text-ink-3">{m.studentNumber}</span>}
              </span>
              <span className="tabular text-md">
                {m.grade == null ? (
                  <span className="text-ink-3">Notlanmadı</span>
                ) : (
                  <>
                    <span className="font-semibold">{formatNumber(m.finalGrade ?? m.grade)}</span>
                    <span className="text-ink-3"> / {formatNumber(a.maxPoints)}</span>
                  </>
                )}
                {m.personalGrade != null && <span className="ml-2 text-sm text-ink-2">kişisel</span>}
              </span>
              {editing !== m.studentId && (
                <span className="flex gap-x-4">
                  <button
                    type="button"
                    onClick={() => open(m.studentId, m.personalGrade)}
                    className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                  >
                    {m.personalGrade == null ? 'Kişisel puan' : 'Değiştir'}
                    <span className="sr-only">: {m.studentName}</span>
                  </button>
                  {m.personalGrade != null && (
                    <button
                      type="button"
                      disabled={member.isPending}
                      onClick={() =>
                        member.mutate(
                          { submissionId: s.submissionId, studentId: m.studentId, grade: null, reason: '' },
                          {
                            onSuccess: () => toast.success(`${m.studentName}: grubun puanına döndü`),
                            onError: (e) => toast.error(toApiError(e).message),
                          },
                        )
                      }
                      className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                    >
                      Kaldır<span className="sr-only">: {m.studentName} kişisel puanı</span>
                    </button>
                  )}
                </span>
              )}
            </div>
            {editing === m.studentId && (
              <div className="mt-3 grid gap-3 sm:grid-cols-[8rem_minmax(0,1fr)]">
                <Field label="Kişisel puan" required>
                  <Input autoFocus inputMode="decimal" maxLength={7} value={value} onChange={(e) => setValue(e.target.value)} />
                </Field>
                <Field label="Gerekçe" required hint="Kayıtta kalır; puanlar ilan edildiyse öğrenciye bildirim gider.">
                  <Input maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
                </Field>
                {error && <p className="text-sm font-semibold text-danger sm:col-span-2">{error}</p>}
                <div className="flex items-center gap-4 sm:col-span-2">
                  <Button
                    size="sm"
                    variant="primary"
                    loading={member.isPending}
                    onClick={() => {
                      const g = parseDecimal(value)
                      if (g === null || g > a.maxPoints) return setError(`Puan 0 ile ${formatNumber(a.maxPoints)} arasında olmalı.`)
                      if (!reason.trim()) return setError('Kişisel puan için gerekçe yazın.')
                      member.mutate(
                        { submissionId: s.submissionId, studentId: m.studentId, grade: g, reason },
                        {
                          onSuccess: () => {
                            toast.success(`${m.studentName}: kişisel puan ${formatNumber(g)}`)
                            setEditing(null)
                          },
                          onError: fail,
                        },
                      )
                    }}
                  >
                    Kaydet
                  </Button>
                  <button
                    type="button"
                    onClick={() => setEditing(null)}
                    className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                  >
                    Vazgeç
                  </button>
                </div>
              </div>
            )}
          </li>
        ))}
      </ul>
    </section>
  )
}
