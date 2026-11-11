import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import {
  LEVEL_LABEL,
  type CatalogDepartment,
  type CatalogFaculty,
  type CatalogProgram,
  type ProgramLevel,
} from '@/features/auth/register/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Switch } from '@/components/ui/Switch'
import { RequireAccess } from './RequireAccess'
import { UNIT_LABEL, useCatalogAll, useSaveUnit, validateUnitDraft, type UnitDraft, type UnitDraftErrors, type UnitKind } from './academic'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'

type Unit = { kind: UnitKind; id: string; code: string; name: string; active: boolean; level?: ProgramLevel; durationYears?: number }
type Editing = { kind: UnitKind; unit?: Unit; parentId?: string; parentName?: string }

const toUnit = (kind: UnitKind, u: CatalogFaculty | CatalogDepartment | CatalogProgram): Unit => ({
  kind,
  id: u.id,
  code: u.code,
  name: u.name,
  active: u.active,
  ...('level' in u ? { level: u.level, durationYears: u.durationYears } : {}),
})

const matches = (u: { code: string; name: string }, q: string) => !q || `${u.code} ${u.name}`.toLocaleLowerCase('tr-TR').includes(q)

/** Akademik yapı (yönetici, F-53): başvuru ve profil formlarındaki fakülte, bölüm ve program seçenekleri. */
export function AcademicStructurePage() {
  usePageTitle('Akademik yapı')
  return (
    <RequireAccess>
      <AcademicStructure />
    </RequireAccess>
  )
}

function AcademicStructure() {
  const catalog = useCatalogAll()
  const [q, setQ] = useState('')
  const [showClosed, setShowClosed] = useState(true)
  const [editing, setEditing] = useState<Editing | null>(null)
  const needle = q.trim().toLocaleLowerCase('tr-TR')

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Akademik yapı"
        meta="Kayıt başvurusu ve profil formlarındaki fakülte, bölüm ve program seçenekleri buradan gelir. Birimler silinmez, kapatılır."
        actions={<Button onClick={() => setEditing({ kind: 'faculty' })}>Fakülte ekle</Button>}
      />
      <div className="mt-8 flex flex-wrap items-end justify-between gap-x-8 gap-y-4">
        <label className="w-full max-w-[22rem]">
          <span className="mb-1 block text-sm font-semibold">Ara</span>
          <Input type="search" placeholder="Kod ya da ad" value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
        <div className="flex items-center gap-3">
          <span className="text-md font-semibold">Kapalı birimleri göster</span>
          <Switch checked={showClosed} onCheckedChange={setShowClosed} label="Kapalı birimleri göster" />
        </div>
      </div>

      <div className="mt-10">
        <QueryBoundary query={catalog} what="Akademik yapı">
          {(tree) => {
            const visible = (u: { active: boolean }) => showClosed || u.active
            const faculties = tree
              .filter(visible)
              .map((f) => {
                const depts = f.departments.filter(visible).map((d) => ({
                  ...d,
                  programs: d.programs.filter((p) => visible(p) && (matches(p, needle) || matches(d, needle) || matches(f, needle))),
                }))
                const keptDepts = depts.filter((d) => matches(d, needle) || matches(f, needle) || d.programs.length > 0)
                return { ...f, departments: keptDepts }
              })
              .filter((f) => matches(f, needle) || f.departments.length > 0)
            if (tree.length === 0)
              return (
                <EmptyState title="Henüz fakülte yok">
                  Fakülte ekleyerek başlayın; bölüm ve programlar fakültenin altına eklenir.
                </EmptyState>
              )
            if (faculties.length === 0) return <p className="text-md text-ink-2">“{q}” ile eşleşen birim yok.</p>
            return (
              <div className="flex flex-col gap-16">
                {faculties.map((f) => (
                  <FacultySection key={f.id} faculty={f} onEdit={setEditing} />
                ))}
              </div>
            )
          }}
        </QueryBoundary>
      </div>
      {editing && <UnitDialog editing={editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function FacultySection({ faculty: f, onEdit }: { faculty: CatalogFaculty; onEdit: (e: Editing) => void }) {
  const programs = f.departments.reduce((n, d) => n + d.programs.length, 0)
  return (
    <section aria-labelledby={`fak-${f.id}`} className="border-t-2 border-ink pt-5">
      <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
        <h2 id={`fak-${f.id}`} className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="tabular text-md font-semibold text-ink-3">{f.code}</span>
          <span className={`text-3xl font-heavy text-balance ${f.active ? '' : 'text-ink-3'}`}>{f.name}</span>
          {!f.active && <Badge tone="neutral">Kapalı</Badge>}
        </h2>
        <UnitActions unit={toUnit('faculty', f)} onEdit={onEdit} childKind="department" />
      </div>
      <p className="tabular mt-1 text-sm text-ink-3">
        {f.departments.length} bölüm, {programs} program
      </p>
      {f.departments.length > 0 && (
        <ul className="mt-5 border-t border-rule">
          {f.departments.map((d) => (
            <li key={d.id} className="border-b border-rule py-4">
              <div className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-2">
                <p className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-1">
                  <span className="tabular text-sm font-semibold text-ink-3">{d.code}</span>
                  <span className={`text-lg font-semibold ${d.active ? '' : 'text-ink-3'}`}>{d.name}</span>
                  {!d.active && <Badge tone="neutral">Kapalı</Badge>}
                </p>
                <UnitActions unit={toUnit('department', d)} onEdit={onEdit} childKind="program" />
              </div>
              {d.programs.length > 0 && (
                <ul className="mt-2 ml-1 border-l-[3px] border-rule pl-4">
                  {d.programs.map((p) => (
                    <li key={p.id} className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1 py-1.5">
                      <p className="flex min-w-0 flex-wrap items-baseline gap-x-3 gap-y-0.5 text-md">
                        <span className="tabular text-sm text-ink-3">{p.code}</span>
                        <span className={p.active ? '' : 'text-ink-3'}>{p.name}</span>
                        <span className="text-sm text-ink-3">
                          {LEVEL_LABEL[p.level]}, {p.durationYears} yıl
                        </span>
                        {!p.active && <Badge tone="neutral">Kapalı</Badge>}
                      </p>
                      <UnitActions unit={toUnit('program', p)} onEdit={onEdit} />
                    </li>
                  ))}
                </ul>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

/** Satır eylemleri: düzenle, kapat ya da aç, alt birim ekle. Kapatma onay ister; açma doğrudan. */
function UnitActions({ unit: u, onEdit, childKind }: { unit: Unit; onEdit: (e: Editing) => void; childKind?: UnitKind }) {
  const save = useSaveUnit()
  const [confirm, setConfirm] = useState(false)
  const toggle = (active: boolean) =>
    save.mutate(
      {
        kind: u.kind,
        id: u.id,
        draft: { code: u.code, name: u.name, active, level: u.level ?? 'BACHELOR', durationYears: String(u.durationYears ?? 4) },
      },
      {
        onSuccess: () => {
          toast.success(active ? `${u.name} yeniden açıldı` : `${u.name} kapatıldı`)
          setConfirm(false)
        },
        onError: (e) => {
          setConfirm(false)
          toast.error(toApiError(e).message)
        },
      },
    )
  return (
    <p className="flex flex-wrap gap-x-5 gap-y-1">
      {childKind && u.active && (
        <button type="button" onClick={() => onEdit({ kind: childKind, parentId: u.id, parentName: u.name })} className={LINK}>
          {UNIT_LABEL[childKind]} ekle<span className="sr-only">: {u.name}</span>
        </button>
      )}
      <button type="button" onClick={() => onEdit({ kind: u.kind, unit: u })} className={LINK}>
        Düzenle<span className="sr-only">: {u.name}</span>
      </button>
      {u.active ? (
        <button type="button" onClick={() => setConfirm(true)} className={`${LINK} hover:text-danger`}>
          Kapat<span className="sr-only">: {u.name}</span>
        </button>
      ) : (
        <button type="button" disabled={save.isPending} onClick={() => toggle(true)} className={LINK}>
          Aç<span className="sr-only">: {u.name}</span>
        </button>
      )}
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive
        title={`${UNIT_LABEL[u.kind]} kapatılsın mı?`}
        description={`${u.name} yeni başvurularda ve profil değişikliklerinde seçilemez. Bu birime bağlı öğrenci ve personel kayıtları korunur; istediğinizde yeniden açabilirsiniz.`}
        confirmLabel="Kapat"
        loading={save.isPending}
        onConfirm={() => toggle(false)}
      />
    </p>
  )
}

function UnitDialog({ editing: e, onClose }: { editing: Editing; onClose: () => void }) {
  const save = useSaveUnit()
  const u = e.unit
  const [d, setD] = useState<UnitDraft>({
    code: u?.code ?? '',
    name: u?.name ?? '',
    level: u?.level ?? 'BACHELOR',
    durationYears: String(u?.durationYears ?? 4),
    active: u?.active ?? true,
  })
  const [errors, setErrors] = useState<UnitDraftErrors & { general?: string }>({})
  const label = UNIT_LABEL[e.kind]
  const set = (k: 'code' | 'name' | 'durationYears') => (ev: { target: { value: string } }) => setD((x) => ({ ...x, [k]: ev.target.value }))

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={u ? `${label} düzenle` : `${label} ekle`}
      description={
        e.parentName ? `${e.parentName} altına.` : u ? u.name : 'Fakülte, bölüm ve programlarıyla birlikte formlarda seçilebilir olur.'
      }
      confirmLabel={u ? 'Kaydet' : 'Ekle'}
      loading={save.isPending}
      onConfirm={() => {
        const next = validateUnitDraft(e.kind, d)
        setErrors(next)
        if (Object.keys(next).length) return
        save.mutate(
          { kind: e.kind, id: u?.id, parentId: e.parentId, draft: d },
          {
            onSuccess: () => {
              toast.success(u ? `${d.name.trim()} güncellendi` : `${d.name.trim()} eklendi`)
              onClose()
            },
            onError: (err) => {
              const ae = toApiError(err)
              if (ae.code === 'CODE_EXISTS') setErrors({ code: ae.message })
              else setErrors({ general: ae.message })
            },
          },
        )
      }}
    >
      <div className="flex flex-col gap-5">
        <div className="grid gap-5 sm:grid-cols-[9rem_minmax(0,1fr)]">
          <Field label="Kod" required error={errors.code} hint="Ör. BIL">
            <Input autoFocus maxLength={20} autoComplete="off" value={d.code} onChange={set('code')} />
          </Field>
          <Field label="Ad" required error={errors.name}>
            <Input maxLength={200} value={d.name} onChange={set('name')} />
          </Field>
        </div>
        {e.kind === 'program' && (
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Düzey" required>
              <Select value={d.level} onChange={(ev) => setD((x) => ({ ...x, level: ev.target.value as ProgramLevel }))}>
                {(Object.keys(LEVEL_LABEL) as ProgramLevel[]).map((l) => (
                  <option key={l} value={l}>
                    {LEVEL_LABEL[l]}
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Süre (yıl)" required error={errors.durationYears}>
              <Input inputMode="numeric" maxLength={1} value={d.durationYears} onChange={set('durationYears')} className="max-w-[6rem]" />
            </Field>
          </div>
        )}
        {errors.general && <p className="text-sm font-semibold text-danger">{errors.general}</p>}
      </div>
    </ConfirmDialog>
  )
}
