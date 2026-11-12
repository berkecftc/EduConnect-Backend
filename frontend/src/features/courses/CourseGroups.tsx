import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatLocal } from '@/lib/time'
import { toInput, useGroupAction, useGroupSets, useSaveGroupSet, type GroupSet, type GroupSetDraft } from '@/features/assignments/staff'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { EmptyState, QueryBoundary } from '@/components/ui/States'
import { Switch } from '@/components/ui/Switch'
import type { Course, StaffRole } from './api'
import { abilities, assessmentsEditable, useEnrolledStudents, type EnrolledStudent } from './teach'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'
const DANGER_LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline'
const fullName = (s: { firstName: string | null; lastName: string | null }) =>
  [s.firstName, s.lastName].filter(Boolean).join(' ') || 'Öğrenci'

/**
 * Gruplar (F-50): grup ödevleri bir sete bağlanır; öğrenci bir sette en fazla bir gruptadır.
 * Koordinatör ve hoca set ve grup kurar, öğrenciyi atar ya da taşır; asistan yalnız görür.
 */
export function CourseGroupsTab({ course: c, role }: { course: Course; role: StaffRole }) {
  const sets = useGroupSets(c.id)
  const students = useEnrolledStudents(c.id)
  const can = abilities(role).assignments && assessmentsEditable(c.status)
  const [editing, setEditing] = useState<GroupSet | 'new' | null>(null)

  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
        <p className="max-w-[64ch] text-md text-ink-2">
          Grup ödevleri bir grup setine bağlanır ve her grup tek teslim yapar. Öğrenci bir sette en fazla bir gruptadır; seçimi öğrencilere
          bırakabilir ya da kendiniz atayabilirsiniz.
        </p>
        {can && <Button onClick={() => setEditing('new')}>Grup seti oluştur</Button>}
      </div>
      <QueryBoundary query={sets} what="Grup setleri">
        {(list) =>
          list.length === 0 ? (
            <EmptyState title="Grup seti yok">
              {can
                ? 'Proje ya da laboratuvar grupları için bir set oluşturun; değerlendirme eklerken bu seti seçersiniz.'
                : 'Grup setlerini koordinatör ve hocalar kurar.'}
            </EmptyState>
          ) : (
            list.map((s) => (
              <GroupSetSection key={s.id} course={c} set={s} students={students.data ?? []} can={can} onEdit={() => setEditing(s)} />
            ))
          )
        }
      </QueryBoundary>
      {editing && <GroupSetDialog course={c} set={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function GroupSetSection({
  course: c,
  set: s,
  students,
  can,
  onEdit,
}: {
  course: Course
  set: GroupSet
  students: EnrolledStudent[]
  can: boolean
  onEdit: () => void
}) {
  const act = useGroupAction(c.id)
  const [confirm, setConfirm] = useState(false)
  const [name, setName] = useState('')
  const grouped = new Set(s.groups.flatMap((g) => g.members.map((m) => m.studentId)))
  const ungrouped = students.filter((st) => !grouped.has(st.studentId)).sort((a, b) => fullName(a).localeCompare(fullName(b), 'tr'))
  const fail = (e: unknown) => toast.error(toApiError(e).message)
  const nextName = `Grup ${s.groups.length + 1}`

  const assign = (studentId: string, studentName: string, groupId: string) => {
    const g = s.groups.find((x) => x.id === groupId)
    act.mutate(
      { kind: 'assign', groupId, studentId },
      { onSuccess: () => toast.success(`${studentName}: ${g?.name ?? 'grup'}`), onError: fail },
    )
  }

  const rule = s.selfSignup
    ? s.signupOpen
      ? `Öğrenciler kendi grubunu seçiyor${s.signupClosesAt ? `; seçim ${formatLocal(s.signupClosesAt, 'datetime')} kapanır` : ''}.`
      : 'Öğrenci seçimi kapandı; atamayı siz yaparsınız.'
    : 'Grupları siz atarsınız.'

  return (
    <section aria-labelledby={`set-${s.id}`} className="border-t-2 border-ink pt-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
        <h2 id={`set-${s.id}`} className="text-2xl font-heavy">
          {s.name}
        </h2>
        {can && (
          <p className="flex gap-x-5">
            <button type="button" onClick={onEdit} className={LINK}>
              Düzenle<span className="sr-only">: {s.name}</span>
            </button>
            <button type="button" onClick={() => setConfirm(true)} className={DANGER_LINK}>
              Sil<span className="sr-only">: {s.name}</span>
            </button>
          </p>
        )}
      </div>
      <p className="mt-1 text-md text-ink-2">
        {rule} {s.maxMembers ? `Grup başına en fazla ${s.maxMembers} kişi.` : ''}
      </p>
      <p className="tabular mt-1 text-sm text-ink-3">
        {s.groups.length} grup, {grouped.size} öğrenci gruplu, {ungrouped.length} öğrenci grupsuz
      </p>

      <ul className="mt-5 border-t border-rule">
        {s.groups.map((g) => (
          <li key={g.id} className="border-b border-rule">
            <details className="group/g">
              <summary className="row-fill grid cursor-pointer list-none grid-cols-[minmax(0,1fr)_auto] items-baseline gap-x-6 py-3.5 [&::-webkit-details-marker]:hidden">
                <span className="min-w-0">
                  <span className="font-semibold">{g.name}</span>
                  <span className="ml-3 truncate text-sm text-ink-3">{g.members.map((m) => m.name).join(', ') || 'Üye yok'}</span>
                </span>
                <span className="tabular text-sm text-ink-2">
                  {g.memberCount}
                  {s.maxMembers ? ` / ${s.maxMembers}` : ''} üye
                </span>
              </summary>
              <div className="pb-4 pl-4">
                {g.members.length > 0 && (
                  <ul className="divide-y divide-rule border-l-[3px] border-rule pl-4">
                    {g.members.map((m) => (
                      <li key={m.studentId} className="flex flex-wrap items-center gap-x-5 gap-y-2 py-2">
                        <span className="min-w-[12rem] flex-1">
                          <span className="font-semibold">{m.name ?? 'Öğrenci'}</span>
                          {m.studentNumber && <span className="tabular ml-2 text-sm text-ink-3">{m.studentNumber}</span>}
                        </span>
                        {can && s.groups.length > 1 && (
                          <label className="w-[11rem]">
                            <span className="sr-only">{m.name} için yeni grup</span>
                            <Select
                              value=""
                              onChange={(e) => e.target.value && assign(m.studentId, m.name ?? 'Öğrenci', e.target.value)}
                              disabled={act.isPending}
                            >
                              <option value="">Taşı…</option>
                              {s.groups
                                .filter((x) => x.id !== g.id)
                                .map((x) => (
                                  <option key={x.id} value={x.id} disabled={!!s.maxMembers && x.memberCount >= s.maxMembers}>
                                    {x.name}
                                  </option>
                                ))}
                            </Select>
                          </label>
                        )}
                        {can && (
                          <button
                            type="button"
                            onClick={() =>
                              act.mutate(
                                { kind: 'unassign', groupId: g.id, studentId: m.studentId },
                                { onSuccess: () => toast.success(`${m.name} gruptan çıkarıldı`), onError: fail },
                              )
                            }
                            className={DANGER_LINK}
                          >
                            Gruptan çıkar<span className="sr-only">: {m.name}</span>
                          </button>
                        )}
                      </li>
                    ))}
                  </ul>
                )}
                {can && (
                  <button
                    type="button"
                    onClick={() =>
                      act.mutate(
                        { kind: 'deleteGroup', groupId: g.id },
                        { onSuccess: () => toast.success(`${g.name} silindi`), onError: fail },
                      )
                    }
                    className={`mt-3 ${DANGER_LINK}`}
                  >
                    {g.name} grubunu sil
                  </button>
                )}
              </div>
            </details>
          </li>
        ))}
      </ul>

      {can && (
        <form
          className="mt-4 flex flex-wrap items-end gap-3"
          onSubmit={(e) => {
            e.preventDefault()
            const n = name.trim() || nextName
            act.mutate(
              { kind: 'addGroup', setId: s.id, name: n },
              {
                onSuccess: () => {
                  toast.success(`${n} eklendi`)
                  setName('')
                },
                onError: fail,
              },
            )
          }}
        >
          <label className="w-[14rem]">
            <span className="mb-1 block text-sm font-semibold">Yeni grup</span>
            <Input maxLength={100} placeholder={nextName} value={name} onChange={(e) => setName(e.target.value)} />
          </label>
          <Button type="submit" loading={act.isPending && act.variables?.kind === 'addGroup'}>
            Grup ekle
          </Button>
        </form>
      )}

      {ungrouped.length > 0 && (
        <div className="mt-8">
          <h3 className="text-lg font-heavy">Grupsuz öğrenciler</h3>
          <ul className="mt-2 divide-y divide-rule border-y border-rule">
            {ungrouped.map((st) => (
              <li key={st.studentId} className="flex flex-wrap items-center gap-x-5 gap-y-2 py-2.5">
                <span className="min-w-[12rem] flex-1">
                  <span className="font-semibold">{fullName(st)}</span>
                  {st.studentNumber && <span className="tabular ml-2 text-sm text-ink-3">{st.studentNumber}</span>}
                </span>
                {can && s.groups.length > 0 && (
                  <label className="w-[11rem]">
                    <span className="sr-only">{fullName(st)} için grup</span>
                    <Select
                      value=""
                      onChange={(e) => e.target.value && assign(st.studentId, fullName(st), e.target.value)}
                      disabled={act.isPending}
                    >
                      <option value="">Gruba ata…</option>
                      {s.groups.map((x) => (
                        <option key={x.id} value={x.id} disabled={!!s.maxMembers && x.memberCount >= s.maxMembers}>
                          {x.name} ({x.memberCount}
                          {s.maxMembers ? `/${s.maxMembers}` : ''})
                        </option>
                      ))}
                    </Select>
                  </label>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}

      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive
        title="Grup setini sil"
        description={`“${s.name}” ve içindeki ${s.groups.length} grup silinir. Bir değerlendirme bu seti kullanıyorsa önce o değerlendirmenin grup ayarını kaldırın.`}
        confirmLabel="Seti sil"
        loading={act.isPending}
        onConfirm={() =>
          act.mutate(
            { kind: 'deleteSet', setId: s.id },
            {
              onSuccess: () => {
                toast.success('Grup seti silindi')
                setConfirm(false)
              },
              onError: (e) => {
                setConfirm(false)
                fail(e)
              },
            },
          )
        }
      />
    </section>
  )
}

/** Set oluştur ya da düzenle. Seçimi öğrencilere bırakınca isteğe bağlı kapanış zamanı sorulur. */
function GroupSetDialog({ course: c, set, onClose }: { course: Course; set?: GroupSet; onClose: () => void }) {
  const save = useSaveGroupSet(c.id)
  const [d, setD] = useState<GroupSetDraft>({
    name: set?.name ?? '',
    selfSignup: set?.selfSignup ?? true,
    maxMembers: set?.maxMembers ? String(set.maxMembers) : '',
    signupClosesAt: toInput(set?.signupClosesAt),
  })
  const [errors, setErrors] = useState<{ name?: string; maxMembers?: string; general?: string }>({})

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={set ? 'Grup setini düzenle' : 'Grup seti oluştur'}
      description="Ör. Proje grupları. Grupları oluşturduktan sonra ekleyebilirsiniz."
      confirmLabel={set ? 'Kaydet' : 'Oluştur'}
      loading={save.isPending}
      onConfirm={() => {
        const next: typeof errors = {}
        if (!d.name.trim()) next.name = 'Set adını yazın.'
        if (d.maxMembers.trim() && (!/^\d+$/.test(d.maxMembers.trim()) || Number(d.maxMembers) < 1 || Number(d.maxMembers) > 100))
          next.maxMembers = 'Kontenjan 1 ile 100 arasında olmalı.'
        setErrors(next)
        if (Object.keys(next).length) return
        save.mutate(
          { id: set?.id, draft: d },
          {
            onSuccess: () => {
              toast.success(set ? 'Grup seti güncellendi' : `${d.name.trim()} oluşturuldu`)
              onClose()
            },
            onError: (e) => {
              const ae = toApiError(e)
              setErrors(ae.code === 'GROUP_SET_EXISTS' ? { name: ae.message } : { general: ae.message })
            },
          },
        )
      }}
    >
      <div className="flex flex-col gap-5">
        <Field label="Ad" required error={errors.name}>
          <Input autoFocus maxLength={100} value={d.name} onChange={(e) => setD((x) => ({ ...x, name: e.target.value }))} />
        </Field>
        <div className="flex items-center justify-between gap-6 border-y border-rule py-2">
          <span>
            <span className="block font-semibold">Öğrenciler kendi grubunu seçsin</span>
            <span className="block text-sm text-ink-3">Kapalıysa öğrencileri siz atarsınız.</span>
          </span>
          <Switch
            checked={d.selfSignup}
            onCheckedChange={(v) => setD((x) => ({ ...x, selfSignup: v }))}
            label="Öğrenciler kendi grubunu seçsin"
          />
        </div>
        <Field label="Grup kontenjanı" hint="İsteğe bağlı; boşsa sınırsız." error={errors.maxMembers}>
          <Input
            inputMode="numeric"
            maxLength={3}
            className="max-w-[8rem]"
            value={d.maxMembers}
            onChange={(e) => setD((x) => ({ ...x, maxMembers: e.target.value }))}
          />
        </Field>
        {d.selfSignup && (
          <Field label="Seçim kapanışı" hint="İsteğe bağlı. Sonrasında öğrenciler grup değiştiremez.">
            <Input
              type="datetime-local"
              value={d.signupClosesAt}
              onChange={(e) => setD((x) => ({ ...x, signupClosesAt: e.target.value }))}
            />
          </Field>
        )}
        {errors.general && <p className="text-sm font-semibold text-danger">{errors.general}</p>}
      </div>
    </ConfirmDialog>
  )
}
