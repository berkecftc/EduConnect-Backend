import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocal } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCourse, type Course } from '@/features/courses/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { Meter } from '@/components/ui/Meter'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import {
  AI_NOTE_MAX,
  AI_POLICY,
  SUBMISSION_TEXT_MAX,
  TYPE_LABEL,
  useMyAssignments,
  useSubmissionVersions,
  useSubmitAssignment,
  type MyAssignment,
} from './api'
import { useGroupSets } from './staff'
import { DeadlineTrack } from './DeadlineTrack'
import { viewAssignment, type AssignmentView } from './model'

/** Teslim dosyası için ön sınır; asıl doğrulama sunucuda. */
const SUBMISSION_MAX_BYTES = 20 * 1024 * 1024

/** Ödev sayfası (öğrenci): bildirim bağlantısı /courses/{courseId}/assignments/{id} buraya düşer (F-76). */
export function AssignmentPage() {
  const { courseId = '', assignmentId = '' } = useParams()
  const assignments = useMyAssignments()
  const course = useCourse(courseId)
  const a = assignments.data?.find((x) => x.id === assignmentId)
  usePageTitle(a ? a.title : 'Ödev')

  return (
    <div className="mx-auto max-w-[80rem]">
      <QueryBoundary query={assignments} what="Ödev" skeletonRows={3}>
        {() =>
          a ? (
            <AssignmentBody assignment={a} course={course.data} />
          ) : (
            <EmptyState title="Bu ödev bulunamadı">
              Ödev kaldırılmış ya da bu derse kayıtlı olmayabilirsiniz.{' '}
              <Link to="/assignments" className="font-semibold text-ders underline-offset-2 hover:underline">
                Ödevlerime dön
              </Link>
            </EmptyState>
          )
        }
      </QueryBoundary>
    </div>
  )
}

function AssignmentBody({ assignment: a, course }: { assignment: MyAssignment; course?: Course }) {
  const v = viewAssignment(a)
  const s = a.submission
  const [downloading, setDownloading] = useState<string | null>(null)

  const download = async (url: string, name: string) => {
    setDownloading(url)
    try {
      await downloadFile(url, name)
    } catch (e) {
      toast.error(toApiError(e).message)
    } finally {
      setDownloading(null)
    }
  }

  return (
    <>
      {/* 1. Başlık bandı: solda ders, tür ve başlık; sağda tarife panosu gibi saat ya da puan. */}
      <section className="grid gap-10 lg:grid-cols-12">
        <div className="min-w-0 lg:col-span-8">
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-md">
            <span aria-hidden className="h-5 w-[4px] bg-ders" />
            <Link to={`/courses/${a.courseId}?sekme=odevler`} className="font-semibold underline-offset-4 hover:underline">
              {course ? (
                <>
                  <span className="tabular font-heavy">{course.code}</span> {course.title}
                </>
              ) : (
                'Ders ödevleri'
              )}
            </Link>
            <span className="text-ink-3">{TYPE_LABEL[a.type]}</span>
          </p>
          <RevealTitle text={a.title} className="mt-1 text-5xl sm:text-6xl" />
          <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
            <Badge tone={v.status.tone}>{v.status.label}</Badge>
            {a.groupSetId && <span>{a.groupName ? `Grup ödevi, ${a.groupName}` : 'Grup ödevi'}</span>}
          </p>
        </div>
        <Board assignment={a} view={v} />
      </section>

      {/* 2. Teslim takvimi: açık, geç teslim ve kapalı dönemler; şu anki kalın çizgiyle. */}
      <DeadlineTrack assignment={a} phase={v.phase} className="mt-14" />

      {/* 3. İçerik: solda açıklama, teslimim ve teslim formu; sağda kurallar. */}
      <div className="mt-16 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-20 lg:col-span-8">
          {(a.description || a.fileUrl) && (
            <Panel title="Açıklama">
              {a.description && <p className="max-w-[68ch] text-lg whitespace-pre-line text-ink-2">{a.description}</p>}
              {a.fileUrl && (
                <DownloadRow
                  className={a.description ? 'mt-6' : undefined}
                  title="Ödev dosyası"
                  hint="Hocanızın eklediği dosya"
                  busy={downloading === `/assignments/${a.id}/file`}
                  onClick={() => void download(`/assignments/${a.id}/file`, a.title)}
                />
              )}
            </Panel>
          )}

          {s && (
            <Panel title="Teslimim">
              <dl className="border-t border-rule">
                <Row label="Teslim">
                  <span className="tabular">{formatInstant(s.submittedAt, 'datetime')}</span>
                  {s.late && <Badge tone="warning" className="ml-3">Geç teslim</Badge>}
                </Row>
                {v.gradeText && (
                  <Row label="Puan">
                    <span className="tabular text-2xl font-heavy">{v.gradeText}</span>
                    {s.late && v.penaltyText && <span className="ml-3 text-sm text-ink-3">{v.penaltyText} kesintisiyle</span>}
                  </Row>
                )}
                {!v.gradeText && v.timing && <Row label="Puan">{v.timing}</Row>}
                {s.feedback && (
                  <Row label="Hocanızın notu">
                    <span className="block max-w-[64ch] whitespace-pre-line">{s.feedback}</span>
                  </Row>
                )}
                {s.textContent && (
                  <Row label="Metin">
                    <span className="block max-h-60 max-w-[64ch] overflow-y-auto whitespace-pre-line text-md">{s.textContent}</span>
                  </Row>
                )}
                {s.aiUsed !== null && (
                  <Row label="Yapay zekâ beyanı">
                    <span className="font-semibold">{s.aiUsed ? 'Kullandım' : 'Kullanmadım'}</span>
                    {s.aiUsed && s.aiNote && <span className="mt-0.5 block text-md text-ink-2">{s.aiNote}</span>}
                  </Row>
                )}
                <Versions submissionId={s.submissionId} />
              </dl>
              <DownloadRow
                className="border-t-0"
                title="Teslim dosyam"
                hint="Son teslim ettiğiniz sürüm"
                busy={downloading === `/assignments/submissions/${s.submissionId}/file`}
                onClick={() => void download(`/assignments/submissions/${s.submissionId}/file`, `${a.title} teslim`)}
              />
              {!v.canSubmit && <p className="mt-4 text-md text-ink-3">{v.blockedReason}</p>}
            </Panel>
          )}

          {(v.canSubmit || !s) && (
            <Panel title={s ? 'Yeniden teslim et' : 'Teslim et'}>
              {v.canSubmit ? (
                <SubmitForm assignment={a} resubmit={!!s} late={v.phase === 'late'} />
              ) : v.needsGroup ? (
                <GroupGate assignment={a} />
              ) : (
                <p className="text-lg text-ink-2">{v.blockedReason}</p>
              )}
            </Panel>
          )}
        </div>

        <aside className="lg:col-span-4">
          <Panel title="Kurallar">
            <dl>
              <Rule label="Yapay zekâ kullanımı">
                <span className="font-semibold text-ink">{AI_POLICY[a.aiPolicy].label}.</span> {AI_POLICY[a.aiPolicy].description}
              </Rule>
              <Rule label="Geç teslim">
                {a.effectiveLateUntil
                  ? `${formatLocal(a.effectiveLateUntil, 'datetime')} kadar açık; ${a.latePenaltyPercent > 0 ? `puandan %${formatNumber(a.latePenaltyPercent)} kesilir` : 'kesinti uygulanmaz'}.`
                  : 'Kabul edilmez; teslim son tarihte kapanır.'}
              </Rule>
              <Rule label="Yeniden teslim">
                Puanlanana kadar yeniden teslim edebilirsiniz; yeni teslim öncekinin yerine geçer. Geç teslimden sonra yeniden teslim yapılamaz.
              </Rule>
              {a.groupSetId && (
                <Rule label="Grup">
                  {a.groupName ?? 'Henüz bir grubunuz yok.'}{' '}
                  <Link to={`/courses/${a.courseId}?sekme=gruplar`} className="font-semibold underline-offset-4 hover:underline">
                    Gruplar
                  </Link>
                </Rule>
              )}
            </dl>
          </Panel>
        </aside>
      </div>
    </>
  )
}

/**
 * Tarife panosu: puanlandıysa büyük puan ve ölçek çizgisi; değilse eylem saatinin büyük rakamı, günü ve kalan süre.
 * Altında ağırlık ve tam puan.
 */
function Board({ assignment: a, view: v }: { assignment: MyAssignment; view: AssignmentView }) {
  const s = a.submission
  const final = s ? (s.finalGrade ?? s.grade) : null
  const graded = v.graded && final !== null
  const at = v.phase === 'late' ? v.closesAt : a.effectiveDueDate
  const urgent = v.status.tone === 'warning' || v.status.tone === 'danger'
  const note = v.timing ?? (v.phase === 'closed' && !s ? 'Teslim süresi doldu' : null)

  return (
    <dl className="self-end border-t border-rule pt-6 lg:col-span-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-8">
      {graded ? (
        <div>
          <dt className="text-sm text-ink-3">Puanınız</dt>
          <dd className="tabular mt-1 text-6xl leading-none font-heavy">
            {formatNumber(final)}
            <span className="text-2xl font-semibold text-ink-3"> / {formatNumber(a.maxPoints)}</span>
          </dd>
          <dd className="mt-4 max-w-[16rem]">
            <Meter value={final} max={a.maxPoints} label={`${formatNumber(a.maxPoints)} üzerinden ${formatNumber(final)}`} />
          </dd>
          {s?.late && final !== s.grade && (
            <dd className="mt-2 text-sm text-ink-3">Geç teslim kesintisi öncesi {formatNumber(s.grade)}</dd>
          )}
        </div>
      ) : (
        <div>
          <dt className="text-sm text-ink-3">{v.phase === 'late' ? 'Geç teslim bitişi' : 'Son tarih'}</dt>
          <dd className="tabular mt-1 text-6xl leading-none font-heavy">{formatLocal(at, 'time')}</dd>
          <dd className="mt-2 text-lg">{formatLocal(at, 'long')}</dd>
          {note && <dd className={cn('mt-1 text-md', urgent ? 'font-semibold text-warning' : 'text-ink-2')}>{note}</dd>}
        </div>
      )}
      <div className="mt-6 grid grid-cols-2 border-t border-rule pt-4">
        <div>
          <dt className="text-sm text-ink-3">Ağırlık</dt>
          <dd className="tabular text-2xl font-heavy">{a.weight > 0 ? `%${formatNumber(a.weight)}` : '–'}</dd>
        </div>
        <div className="border-l border-rule pl-5">
          <dt className="text-sm text-ink-3">Tam puan</dt>
          <dd className="tabular text-2xl font-heavy">{formatNumber(a.maxPoints)}</dd>
        </div>
      </div>
    </dl>
  )
}

/** Teslim ayrıntısı satırı: solda etiket, sağda değer; ince çizgiyle ayrılır. */
/** Grubu olmayan öğrenci grup ödevini teslim edemez: seçim açıksa gruplara yönlendirir, değilse hocaya. */
function GroupGate({ assignment: a }: { assignment: MyAssignment }) {
  const sets = useGroupSets(a.courseId)
  const set = sets.data?.find((s) => s.id === a.groupSetId)
  if (set && !set.signupOpen) return <p className="text-lg text-ink-2">Bu bir grup ödevi ve henüz bir grubunuz yok. Grubunuz için hocanıza başvurun.</p>
  return (
    <>
      <p className="text-lg text-ink-2">Bu bir grup ödevi. Teslim etmek için önce {set ? `${set.name} içinden ` : ''}bir gruba katılın.</p>
      <Button asChild variant="primary" className="mt-5">
        <Link to={`/courses/${a.courseId}?sekme=gruplar`}>Gruplara git</Link>
      </Button>
    </>
  )
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid gap-x-6 gap-y-1 border-b border-rule py-3.5 sm:grid-cols-[11rem_minmax(0,1fr)]">
      <dt className="text-md text-ink-3">{label}</dt>
      <dd>{children}</dd>
    </div>
  )
}

/** Kural: küçük etiket, altında açıklama. */
function Rule({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="border-b border-rule py-3.5 first:pt-0">
      <dt className="text-sm font-semibold text-ink">{label}</dt>
      <dd className="mt-1 text-md text-ink-2">{children}</dd>
    </div>
  )
}

/** İndirme satırı: kutusuz, tam genişlik; üzerine gelince zemin soldan dolar. */
function DownloadRow({ title, hint, busy, onClick, className }: { title: string; hint: string; busy: boolean; onClick: () => void; className?: string }) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      aria-busy={busy || undefined}
      className={cn('row-fill flex w-full items-center justify-between gap-4 border-y border-rule px-1 py-3.5 text-left disabled:opacity-60', className)}
    >
      <span className="min-w-0">
        <span className="block font-semibold">{title}</span>
        <span className="block text-sm text-ink-3">{hint}</span>
      </span>
      <span className="flex shrink-0 items-center gap-2 text-sm font-semibold">
        <Download className="size-4" aria-hidden />
        {busy ? 'İndiriliyor…' : 'İndir'}
      </span>
    </button>
  )
}

function Versions({ submissionId }: { submissionId: string }) {
  const versions = useSubmissionVersions(submissionId)
  if (!versions.data || versions.data.length < 2) return null
  return (
    <Row label="Sürümler">
      <ol className="flex flex-col gap-1 text-md">
        {[...versions.data].reverse().map((ver) => (
          <li key={ver.versionNo} className="tabular flex gap-4">
            <span className="w-16 font-semibold">Sürüm {ver.versionNo}</span>
            <span className="text-ink-2">
              {formatInstant(ver.submittedAt, 'datetime')}
              {ver.late ? ', geç' : ''}
            </span>
          </li>
        ))}
      </ol>
    </Row>
  )
}

/** Teslim formu (F-48, F-79): dosya ve/veya metin; politika gerektiriyorsa yapay zekâ beyanı. */
function SubmitForm({ assignment: a, resubmit, late }: { assignment: MyAssignment; resubmit: boolean; late: boolean }) {
  const submit = useSubmitAssignment(a.id)
  const [file, setFile] = useState<File | null>(null)
  const [text, setText] = useState('')
  const [aiUsed, setAiUsed] = useState<'yes' | 'no' | undefined>()
  const [aiNote, setAiNote] = useState('')
  const [errors, setErrors] = useState<{ content?: string; ai?: string; aiNote?: string }>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const needsDeclaration = a.aiPolicy === 'ALLOWED_WITH_DISCLOSURE'

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setFailure(null)
    const next: typeof errors = {}
    if (!file && !text.trim()) next.content = 'Dosya ekleyin ya da metin yazın.'
    if (needsDeclaration && !aiUsed) next.ai = 'Yapay zekâ kullanıp kullanmadığınızı seçin.'
    setErrors(next)
    if (Object.keys(next).length) return
    submit.mutate(
      { file, text, aiUsed: needsDeclaration ? aiUsed === 'yes' : null, aiNote },
      {
        onSuccess: () => {
          toast.success(resubmit ? 'Yeniden teslim edildi' : 'Teslim edildi')
          setFile(null)
          setText('')
        },
        onError: (err) => {
          const ae = toApiError(err)
          if (ae.code === 'SUBMISSION_EMPTY') setErrors({ content: ae.message })
          else if (ae.code === 'AI_DECLARATION_REQUIRED') setErrors({ ai: ae.message })
          else if (ae.code === 'AI_NOTE_TOO_LONG') setErrors({ aiNote: ae.message })
          else setFailure(ae)
        },
      },
    )
  }

  const penalty = a.latePenaltyPercent > 0 ? ` ve puandan %${formatNumber(a.latePenaltyPercent)} kesilir` : ''

  return (
    <form onSubmit={onSubmit} noValidate className="flex max-w-[44rem] flex-col gap-6">
      {(resubmit || late) && (
        <Notice variant="line" tone={late ? 'warning' : 'info'}>
          {late && `Son tarih geçti; bu teslim geç sayılır${penalty}. Geç teslimden sonra yeniden teslim yapılamaz. `}
          {resubmit && 'Yeni teslim öncekinin yerine geçer; önceki sürüm geçmişte kalır.'}
        </Notice>
      )}
      <FileField label="Dosya" hint="İsteğe bağlı, en fazla 20 MB." maxBytes={SUBMISSION_MAX_BYTES} value={file} onChange={setFile} />
      <Field label="Metin" hint="İsteğe bağlı. Dosya ya da metinden en az biri gerekli." error={errors.content}>
        <Textarea maxLength={SUBMISSION_TEXT_MAX} valueLength={text.length} value={text} onChange={(e) => setText(e.target.value)} rows={6} />
      </Field>
      {needsDeclaration && (
        <>
          <ChoiceGroup
            layout="rows"
            label="Yapay zekâ beyanı"
            value={aiUsed}
            onChange={setAiUsed}
            error={errors.ai}
            choices={[
              { value: 'no', title: 'Kullanmadım', description: 'Bu çalışmada yapay zekâ aracı kullanmadım.' },
              { value: 'yes', title: 'Kullandım', description: 'Hangi araçla ve ne için kullandığınızı aşağıya yazın.' },
            ]}
          />
          {aiUsed === 'yes' && (
            <Field label="Nasıl kullandınız?" hint="Ör. kod hatasını bulmak için ChatGPT'ye danıştım." error={errors.aiNote}>
              <Textarea maxLength={AI_NOTE_MAX} valueLength={aiNote.length} value={aiNote} onChange={(e) => setAiNote(e.target.value)} rows={3} />
            </Field>
          )}
        </>
      )}
      {failure && (
        <Notice variant="line" tone={failure.status === 409 ? 'warning' : 'danger'} title="Teslim edilemedi">
          {failure.message}
        </Notice>
      )}
      <div>
        <Button type="submit" variant="primary" size="lg" loading={submit.isPending}>
          {resubmit ? 'Yeniden teslim et' : 'Teslim et'}
        </Button>
      </div>
    </form>
  )
}
