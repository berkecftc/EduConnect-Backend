import { useMemo, useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, formatRelative } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { APPLICATION_STATUS_LABEL, APPLICATION_TONE, isRunning, type Course, type StaffRole } from './api'
import {
  ENROLLMENT_EVENT_LABEL,
  abilities,
  useApplicationHistory,
  useDecideApplication,
  useEnrolledStudents,
  useEnrollmentHistory,
  usePendingApplications,
  useRemoveStudent,
  type EnrolledStudent,
  type StaffApplication,
} from './teach'
import { PersonLink } from '@/features/people/PersonLink'

const LINK = 'text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'

/** Hocanın gözünden başvuru durumları: geri çekmeyi öğrenci yapar. */
const DECIDED_LABEL: Partial<Record<StaffApplication['status'], string>> = { WITHDRAWN: 'Öğrenci geri çekti' }

/** Başvurular (F-42, F-44): onay bekleyenler önce; altta sonuçlananlar. */
export function CourseApplications({ course: c }: { course: Course }) {
  const pending = usePendingApplications(c.id, true)
  const history = useApplicationHistory(c.id, true)
  const full = c.enrolledStudentCount >= c.capacity
  return (
    <div className="flex flex-col gap-14">
      <Panel title="Onay bekleyenler">
        <QueryBoundary query={pending} what="Başvurular">
          {(list) =>
            list.length === 0 ? (
              <EmptyState title="Bekleyen başvuru yok">
                {c.status === 'DRAFT'
                  ? 'Ders yayımlanınca öğrenciler katalogdan başvurabilir.'
                  : isRunning(c.status)
                    ? 'Öğrenciler katalogdan başvurduğunda burada onayınızı bekler; size bildirim de gelir.'
                    : 'Dönem bittiği için yeni başvuru alınmıyor.'}
              </EmptyState>
            ) : (
              <>
                {full && (
                  <Notice variant="line" tone="warning" title="Kontenjan dolu">
                    Yeni başvuru onaylamak için Genel sekmesinden kontenjanı artırın.
                  </Notice>
                )}
                <ul className="divide-y divide-rule border-y border-rule">
                  {[...list]
                    .sort((a, b) => a.applicationDate.localeCompare(b.applicationDate))
                    .map((a) => (
                      <PendingRow key={a.id} application={a} course={c} full={full} />
                    ))}
                </ul>
              </>
            )
          }
        </QueryBoundary>
      </Panel>

      <Panel title="Sonuçlananlar">
        <QueryBoundary query={history} what="Başvuru geçmişi">
          {(all) => {
            const done = all
              .filter((a) => a.status !== 'PENDING')
              .sort((a, b) => (b.processedDate ?? '').localeCompare(a.processedDate ?? ''))
            if (done.length === 0) return <p className="text-md text-ink-2">Henüz sonuçlanan başvuru yok.</p>
            return (
              <ul className="divide-y divide-rule border-y border-rule">
                {done.map((a) => (
                  <li key={a.id} className="grid gap-x-6 gap-y-1 py-4 sm:grid-cols-[minmax(0,1fr)_auto]">
                    <div className="min-w-0">
                      <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                        <PersonLink id={a.studentId} className="font-semibold">
                          {a.studentName ?? 'Öğrenci'}
                        </PersonLink>
                        <Badge tone={APPLICATION_TONE[a.status]}>{DECIDED_LABEL[a.status] ?? APPLICATION_STATUS_LABEL[a.status]}</Badge>
                      </p>
                      <p className="tabular mt-0.5 text-sm text-ink-3">{a.studentNumber}</p>
                      {a.rejectionReason && <p className="mt-1.5 max-w-[68ch] text-md text-ink-2">{a.rejectionReason}</p>}
                    </div>
                    <p className="tabular text-sm text-ink-3 sm:text-right">
                      {formatInstant(a.processedDate ?? a.applicationDate, 'date')}
                    </p>
                  </li>
                ))}
              </ul>
            )
          }}
        </QueryBoundary>
      </Panel>
    </div>
  )
}

function PendingRow({ application: a, course: c, full }: { application: StaffApplication; course: Course; full: boolean }) {
  const decide = useDecideApplication(c.id)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const name = a.studentName ?? 'Öğrenci'
  const busy = decide.isPending && decide.variables?.id === a.id

  return (
    <li className="grid gap-x-6 gap-y-3 py-5 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-center">
      <div className="min-w-0">
        <p className="text-lg font-semibold">
          <PersonLink id={a.studentId}>{name}</PersonLink>
        </p>
        <p className="mt-0.5 flex flex-wrap gap-x-4 text-sm text-ink-3">
          {a.studentNumber && <span className="tabular">{a.studentNumber}</span>}
          {a.studentEmail && <span>{a.studentEmail}</span>}
          <span>{formatRelative(a.applicationDate)} başvurdu</span>
        </p>
      </div>
      <div className="flex items-center gap-3">
        <Button
          size="sm"
          variant="primary"
          disabled={full}
          loading={busy && decide.variables?.kind === 'approve'}
          onClick={() =>
            decide.mutate(
              { id: a.id, kind: 'approve' },
              { onSuccess: () => toast.success(`${name} derse kaydedildi`), onError: (e) => toast.error(toApiError(e).message) },
            )
          }
        >
          Onayla<span className="sr-only">: {name}</span>
        </Button>
        <Button size="sm" onClick={() => setRejecting(true)}>
          Reddet<span className="sr-only">: {name}</span>
        </Button>
      </div>
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        destructive
        title="Başvuruyu reddet"
        description={`${name} (${a.studentNumber ?? 'numara yok'}) ${c.code} dersine kaydedilmez; öğrenciye bildirim gider.`}
        confirmLabel="Reddet"
        loading={busy}
        onConfirm={() =>
          decide.mutate(
            { id: a.id, kind: 'reject', reason },
            {
              onSuccess: () => {
                toast.success('Başvuru reddedildi')
                setRejecting(false)
                setReason('')
              },
              onError: (e) => toast.error(toApiError(e).message),
            },
          )
        }
      >
        <Field label="Gerekçe" hint="İsteğe bağlı; öğrenci görür. Ör. önkoşul dersi eksik.">
          <Textarea maxLength={255} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}

const name = (s: EnrolledStudent) => [s.firstName, s.lastName].filter(Boolean).join(' ') || 'Öğrenci'
const HISTORY_PAGE = 10

/** Öğrenciler (F-44): kayıtlı liste (arama, dersten çıkarma) ve kayıt geçmişi. */
export function CourseStudents({ course: c, role }: { course: Course; role: StaffRole }) {
  const students = useEnrolledStudents(c.id)
  const history = useEnrollmentHistory(c.id, true)
  const can = abilities(role)
  const removable = can.removeStudent && isRunning(c.status)
  const [q, setQ] = useState('')
  const [allHistory, setAllHistory] = useState(false)

  const filtered = useMemo(() => {
    const needle = q.trim().toLocaleLowerCase('tr-TR')
    const list = [...(students.data ?? [])].sort((a, b) => name(a).localeCompare(name(b), 'tr'))
    if (!needle) return list
    return list.filter((s) => name(s).toLocaleLowerCase('tr-TR').includes(needle) || (s.studentNumber ?? '').includes(needle))
  }, [students.data, q])

  return (
    <div className="flex flex-col gap-14">
      <Panel
        title="Kayıtlı öğrenciler"
        action={
          (students.data?.length ?? 0) > 8 && (
            <label className="w-full sm:w-[18rem]">
              <span className="sr-only">Ad ya da numarayla ara</span>
              <Input type="search" placeholder="Ad ya da numara" value={q} onChange={(e) => setQ(e.target.value)} />
            </label>
          )
        }
      >
        <QueryBoundary query={students} what="Öğrenciler">
          {(list) =>
            list.length === 0 ? (
              <EmptyState title="Kayıtlı öğrenci yok">Onayladığınız başvurular burada öğrenci olarak listelenir.</EmptyState>
            ) : filtered.length === 0 ? (
              <p className="text-md text-ink-2">“{q}” ile eşleşen öğrenci yok.</p>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[40rem] text-left text-md">
                  <caption className="sr-only">
                    {c.code} kayıtlı öğrenciler, {list.length} kişi
                  </caption>
                  <thead>
                    <tr className="border-b border-ink text-sm text-ink-3">
                      <th scope="col" className="py-2 pr-4 font-semibold">
                        Öğrenci
                      </th>
                      <th scope="col" className="py-2 pr-4 font-semibold">
                        Numara
                      </th>
                      <th scope="col" className="py-2 pr-4 font-semibold">
                        Bölüm
                      </th>
                      <th scope="col" className="py-2 pr-4 font-semibold">
                        Kayıt
                      </th>
                      {removable && (
                        <th scope="col" className="py-2">
                          <span className="sr-only">İşlem</span>
                        </th>
                      )}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-rule">
                    {filtered.map((s) => (
                      <tr key={s.studentId}>
                        <th scope="row" className="py-3 pr-4 font-normal">
                          <PersonLink id={s.studentId} className="block font-semibold">
                            {name(s)}
                          </PersonLink>
                          {s.email && <span className="block text-sm text-ink-3">{s.email}</span>}
                        </th>
                        <td className="tabular py-3 pr-4">{s.studentNumber ?? '–'}</td>
                        <td className="py-3 pr-4 text-ink-2">{s.department ?? '–'}</td>
                        <td className="tabular py-3 pr-4 text-ink-2">{formatInstant(s.enrollmentDate, 'date')}</td>
                        {removable && (
                          <td className="py-3 text-right">
                            <RemoveStudent course={c} student={s} />
                          </td>
                        )}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )
          }
        </QueryBoundary>
      </Panel>

      <Panel title="Kayıt geçmişi">
        <QueryBoundary query={history} what="Kayıt geçmişi">
          {(events) => {
            if (events.length === 0) return <p className="text-md text-ink-2">Henüz kayıt hareketi yok.</p>
            const shown = allHistory ? events : events.slice(0, HISTORY_PAGE)
            return (
              <>
                <ul className="divide-y divide-rule border-y border-rule">
                  {shown.map((e) => (
                    <li key={e.id} className="grid gap-x-6 gap-y-1 py-3.5 sm:grid-cols-[8rem_minmax(0,1fr)]">
                      <span className="tabular text-sm text-ink-3">{formatInstant(e.occurredAt, 'date')}</span>
                      <div className="min-w-0">
                        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                          <PersonLink id={e.studentId} className="font-semibold">
                            {e.studentName ?? 'Öğrenci'}
                          </PersonLink>
                          <span className={e.type === 'ENROLLED' ? 'text-ink-2' : 'font-semibold text-ink'}>
                            {ENROLLMENT_EVENT_LABEL[e.type]}
                          </span>
                          {e.afterEnrollmentPeriod && <Badge tone="warning">Geç çekilme (W)</Badge>}
                        </p>
                        {e.reason && <p className="mt-1 max-w-[68ch] text-md text-ink-2">{e.reason}</p>}
                      </div>
                    </li>
                  ))}
                </ul>
                {events.length > HISTORY_PAGE && (
                  <button type="button" onClick={() => setAllHistory((v) => !v)} className={`mt-4 ${LINK}`}>
                    {allHistory ? 'Daha az göster' : `Tümünü göster (${events.length})`}
                  </button>
                )}
              </>
            )
          }}
        </QueryBoundary>
      </Panel>
    </div>
  )
}

function RemoveStudent({ course: c, student: s }: { course: Course; student: EnrolledStudent }) {
  const remove = useRemoveStudent(c.id)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  return (
    <>
      <button
        type="button"
        onClick={() => setOpen(true)}
        className="text-sm font-semibold whitespace-nowrap text-ink-2 underline-offset-4 hover:text-danger hover:underline"
      >
        Dersten çıkar<span className="sr-only">: {name(s)}</span>
      </button>
      <ConfirmDialog
        open={open}
        onOpenChange={(o) => {
          setOpen(o)
          if (!o) setError(null)
        }}
        destructive
        title="Dersten çıkar"
        description={`${name(s)} (${s.studentNumber ?? 'numara yok'}) ${c.code} dersinden çıkarılır. Öğrenciye bildirim gider; gerekçe kayıt geçmişinde kalır.`}
        confirmLabel="Dersten çıkar"
        loading={remove.isPending}
        onConfirm={() => {
          if (!reason.trim()) return setError('Gerekçe yazın.')
          remove.mutate(
            { studentId: s.studentId, reason },
            {
              onSuccess: () => {
                toast.success(`${name(s)} dersten çıkarıldı`)
                setOpen(false)
                setReason('')
              },
              onError: (e) => setError(toApiError(e).message),
            },
          )
        }}
      >
        <Field label="Gerekçe" required error={error ?? undefined}>
          <Textarea maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </>
  )
}
