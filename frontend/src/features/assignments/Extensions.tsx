import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, formatLocal, localStamp, nowLocalIso } from '@/lib/time'
import { useEnrolledStudents } from '@/features/courses/teach'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { toInput, useExtensions, useGrantExtension, useRevokeExtension, type StaffAssignment } from './staff'

/** Uzatmalar (F-48): öğrenciye özel son tarih; koordinatör ve hoca verir ve kaldırır, kadro görür. */
export function ExtensionsTab({ assignment: a, canEdit, onGrant }: { assignment: StaffAssignment; canEdit: boolean; onGrant: () => void }) {
  const list = useExtensions(a.id)
  const revoke = useRevokeExtension(a)
  const [target, setTarget] = useState<{ id: string; name: string } | null>(null)

  return (
    <div className="flex flex-col gap-6">
      {canEdit && (
        <div>
          <Button onClick={onGrant}>Süre uzat</Button>
        </div>
      )}
      <QueryBoundary query={list} what="Uzatmalar">
        {(items) =>
          items.length === 0 ? (
            <EmptyState title="Uzatma yok">
              {canEdit
                ? 'Raporlu ya da mazeretli öğrenciye özel son tarih verebilirsiniz; diğer öğrencilerin süresi değişmez.'
                : 'Süre uzatmayı koordinatör ve hocalar verir.'}
            </EmptyState>
          ) : (
            <ul className="divide-y divide-rule border-y border-ink">
              {items.map((e) => (
                <li key={e.studentId} className="grid gap-x-6 gap-y-1 py-4 sm:grid-cols-[minmax(0,1fr)_14rem_auto] sm:items-baseline">
                  <div className="min-w-0">
                    <p className="font-semibold">{e.studentName ?? 'Öğrenci'}</p>
                    <p className="mt-0.5 flex flex-wrap gap-x-3 text-sm text-ink-3">
                      {e.studentNumber && <span className="tabular">{e.studentNumber}</span>}
                      <span>{formatInstant(e.grantedAt, 'date')} verildi</span>
                    </p>
                    {e.reason && <p className="mt-1 max-w-[60ch] text-md text-ink-2">{e.reason}</p>}
                  </div>
                  <p className="text-md">
                    <span className="tabular font-semibold">{formatLocal(e.dueDate, 'datetime')}</span>
                    {e.lateUntil && (
                      <span className="block text-sm text-ink-3">geç teslim {formatLocal(e.lateUntil, 'datetime')} kadar</span>
                    )}
                  </p>
                  {canEdit && (
                    <button
                      type="button"
                      onClick={() => setTarget({ id: e.studentId, name: e.studentName ?? 'Öğrenci' })}
                      className="justify-self-start text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline sm:justify-self-end"
                    >
                      Kaldır<span className="sr-only">: {e.studentName}</span>
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
      <ConfirmDialog
        open={!!target}
        onOpenChange={(o) => !o && setTarget(null)}
        destructive
        title="Uzatmayı kaldır"
        description={`${target?.name ?? ''} için son tarih herkesinkiyle aynı olur (${formatLocal(a.dueDate, 'datetime')}).`}
        confirmLabel="Uzatmayı kaldır"
        loading={revoke.isPending}
        onConfirm={() =>
          revoke.mutate(target!.id, {
            onSuccess: () => {
              toast.success('Uzatma kaldırıldı')
              setTarget(null)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </div>
  )
}

/** Süre uzat penceresi: öğrenci (satırdan geldiyse seçili), yeni son tarih ve isteğe bağlı gerekçe. */
export function ExtensionDialog({
  assignment: a,
  studentId,
  onClose,
}: {
  assignment: StaffAssignment
  studentId?: string
  onClose: () => void
}) {
  const students = useEnrolledStudents(a.courseId)
  const grant = useGrantExtension(a)
  // Varsayılan: son tarih henüz gelmediyse ondan bir gün sonra, geçtiyse yarın 23.59.
  const now = nowLocalIso()
  const base = a.dueDate > now ? a.dueDate : `${now.slice(0, 10)}T23:59:00`
  const [who, setWho] = useState(studentId ?? '')
  const [due, setDue] = useState(() => toInput(new Date(localStamp(base) + 86_400_000).toISOString()))
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<{ who?: string; due?: string; general?: string }>({})
  const list = [...(students.data ?? [])].sort((x, y) => `${x.firstName} ${x.lastName}`.localeCompare(`${y.firstName} ${y.lastName}`, 'tr'))

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Süre uzat"
      description={`Yalnız seçtiğiniz öğrencinin son tarihi değişir. Şu anki son teslim: ${formatLocal(a.dueDate, 'datetime')}.`}
      confirmLabel="Süreyi uzat"
      loading={grant.isPending}
      onConfirm={() => {
        const next: typeof errors = {}
        if (!who) next.who = 'Öğrenci seçin.'
        if (!due) next.due = 'Yeni son tarihi seçin.'
        else if (due <= toInput(a.dueDate)) next.due = 'Yeni tarih şu anki son teslimden sonra olmalı.'
        else if (due <= toInput(now)) next.due = 'Yeni tarih ileri bir zaman olmalı.'
        setErrors(next)
        if (Object.keys(next).length) return
        grant.mutate(
          { studentId: who, dueDate: due, reason },
          {
            onSuccess: (e) => {
              toast.success(`${e.studentName ?? 'Öğrenci'} için son teslim ${formatLocal(e.dueDate, 'datetime')}`)
              onClose()
            },
            onError: (err) => {
              const ae = toApiError(err)
              if (ae.code === 'EXTENSION_NOT_LATER') setErrors({ due: ae.message })
              else if (ae.code === 'STUDENT_NOT_ENROLLED') setErrors({ who: ae.message })
              else setErrors({ general: ae.message })
            },
          },
        )
      }}
    >
      <div className="flex flex-col gap-5">
        <Field label="Öğrenci" required error={errors.who}>
          <Select value={who} onChange={(e) => setWho(e.target.value)} disabled={!!studentId || students.isPending}>
            <option value="">Seçin</option>
            {list.map((s) => (
              <option key={s.studentId} value={s.studentId}>
                {s.firstName} {s.lastName} {s.studentNumber ? `(${s.studentNumber})` : ''}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Yeni son teslim" required error={errors.due} hint="Türkiye saati.">
          <Input type="datetime-local" value={due} onChange={(e) => setDue(e.target.value)} />
        </Field>
        <Field label="Gerekçe" hint="İsteğe bağlı; kayıtta kalır. Ör. sağlık raporu.">
          <Textarea rows={2} maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
        {errors.general && <p className="text-sm font-semibold text-danger">{errors.general}</p>}
      </div>
    </ConfirmDialog>
  )
}
