import { useId, useState } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCatalog } from '@/features/auth/register/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Dialog } from '@/components/ui/Dialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import {
  PERMISSIONS,
  PERMISSION_TITLE,
  grantsToDraft,
  useCreateStaffAccount,
  useStaffAccounts,
  useStaffSuspension,
  useUpdateStaffGrants,
  type GrantDraft,
  type StaffAccount,
  type StaffPermission,
} from './accounts'
import { RequireAccess } from './RequireAccess'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'

/** Görevli hesapları (yönetici, F-57): yetkiler; öğrenci doğrulama fakülteyle sınırlanabilir. */
export function StaffAccountsPage() {
  usePageTitle('Görevli hesapları')
  return (
    <RequireAccess>
      <StaffAccounts />
    </RequireAccess>
  )
}

function StaffAccounts() {
  const list = useStaffAccounts()
  const [editing, setEditing] = useState<StaffAccount | 'new' | null>(null)
  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Görevli hesapları"
        meta="Öğrenci işleri, moderasyon ve hesap yönetimi gibi görevler için hesaplar. Görevliler yalnız verdiğiniz yetkilerin bölümlerini görür."
        actions={<Button onClick={() => setEditing('new')}>Görevli hesabı oluştur</Button>}
      />
      <div className="mt-8">
        <QueryBoundary query={list} what="Görevli hesapları">
          {(items) =>
            items.length === 0 ? (
              <EmptyState title="Görevli hesabı yok">Oluşturduğunuz hesaba parola belirleme bağlantısı e-postayla gider.</EmptyState>
            ) : (
              <ul className="border-t border-ink">
                {[...items]
                  .sort((a, b) => a.displayName.localeCompare(b.displayName, 'tr'))
                  .map((s) => (
                    <StaffRow key={s.id} account={s} onEdit={() => setEditing(s)} />
                  ))}
              </ul>
            )
          }
        </QueryBoundary>
      </div>
      {editing && <StaffDialog account={editing === 'new' ? undefined : editing} onClose={() => setEditing(null)} />}
    </div>
  )
}

function StaffRow({ account: s, onEdit }: { account: StaffAccount; onEdit: () => void }) {
  const catalog = useCatalog()
  const suspension = useStaffSuspension()
  const [confirm, setConfirm] = useState(false)
  const [reason, setReason] = useState('')
  const faculty = (id: string) => catalog.data?.find((f) => f.id === id)?.name ?? 'Fakülte'
  const suspended = s.status === 'SUSPENDED'

  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="text-lg font-semibold">{s.displayName}</span>
          {suspended && <Badge tone="warning">Askıda</Badge>}
        </p>
        <p className="mt-0.5 text-sm break-all text-ink-3">{s.email}</p>
        {s.grants.length === 0 ? (
          <p className="mt-2 text-md text-ink-3">Yetki tanımlanmamış.</p>
        ) : (
          <ul className="mt-2 flex flex-col gap-0.5 text-md">
            {grantsToDraft(s.grants).map((g) => (
              <li key={g.permission}>
                <span className="font-semibold">{PERMISSION_TITLE[g.permission]}</span>
                {g.permission === 'STUDENT_VERIFIER' && (
                  <span className="text-ink-2">: {g.facultyIds.length ? g.facultyIds.map(faculty).join(', ') : 'bütün kurum'}</span>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      <p className="flex gap-x-5">
        <button type="button" onClick={onEdit} className={LINK}>
          Yetkileri düzenle<span className="sr-only">: {s.displayName}</span>
        </button>
        <button type="button" onClick={() => setConfirm(true)} className={`${LINK} ${suspended ? '' : 'hover:text-danger'}`}>
          {suspended ? 'Etkinleştir' : 'Askıya al'}
          <span className="sr-only">: {s.displayName}</span>
        </button>
      </p>
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive={!suspended}
        title={suspended ? 'Hesabı etkinleştir' : 'Görevli hesabını askıya al'}
        description={
          suspended
            ? `${s.displayName} yeniden giriş yapabilir.`
            : `${s.displayName} giriş yapamaz; açık oturumları kapanır. Yetkileri korunur.`
        }
        confirmLabel={suspended ? 'Etkinleştir' : 'Askıya al'}
        loading={suspension.isPending}
        onConfirm={() =>
          suspension.mutate(
            { id: s.id, suspend: !suspended, reason },
            {
              onSuccess: () => {
                toast.success(suspended ? `${s.displayName} yeniden etkin` : `${s.displayName} askıya alındı`)
                setConfirm(false)
              },
              onError: (e) => {
                setConfirm(false)
                toast.error(toApiError(e).message)
              },
            },
          )
        }
      >
        {!suspended && (
          <Field label="Gerekçe" hint="İsteğe bağlı; denetim kaydına yazılır.">
            <Textarea rows={2} maxLength={500} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
        )}
      </ConfirmDialog>
    </li>
  )
}

/** Oluşturma ya da yetki düzenleme penceresi. Yetki değişince görevlinin oturumları kapanır. */
function StaffDialog({ account, onClose }: { account?: StaffAccount; onClose: () => void }) {
  const create = useCreateStaffAccount()
  const update = useUpdateStaffGrants()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [grants, setGrants] = useState<GrantDraft[]>(account ? grantsToDraft(account.grants) : [])
  const [errors, setErrors] = useState<{ name?: string; email?: string; grants?: string; general?: string }>({})
  const busy = create.isPending || update.isPending

  const submit = () => {
    const next: typeof errors = {}
    if (!account) {
      if (!name.trim()) next.name = 'Görünen adı yazın.'
      if (!/^\S+@\S+\.\S+$/.test(email.trim())) next.email = 'Geçerli bir e-posta adresi yazın.'
    }
    if (grants.length === 0) next.grants = 'En az bir yetki seçin. Görevin bittiyse hesabı askıya alın.'
    setErrors(next)
    if (Object.keys(next).length) return
    const onError = (e: unknown) => {
      const ae = toApiError(e)
      if (ae.code === 'EMAIL_TAKEN') setErrors({ email: 'Bu e-posta adresi başka bir hesapta kullanılıyor.' })
      else if (ae.code === 'EMAIL_DOMAIN_NOT_ALLOWED') setErrors({ email: ae.message })
      else setErrors({ general: ae.message })
    }
    if (account)
      update.mutate(
        { id: account.id, grants },
        {
          onSuccess: () => {
            toast.success(`${account.displayName}: yetkiler güncellendi; oturumları kapandı`)
            onClose()
          },
          onError,
        },
      )
    else
      create.mutate(
        { email, displayName: name, grants },
        {
          onSuccess: (s) => {
            toast.success(`${s.displayName} oluşturuldu; parola belirleme bağlantısı e-postayla gitti`)
            onClose()
          },
          onError,
        },
      )
  }

  return (
    <Dialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={account ? `${account.displayName}: yetkiler` : 'Görevli hesabı oluştur'}
      description={
        account
          ? 'Kaydettiğinizde seçtiğiniz yetkiler eskilerin yerine geçer ve görevlinin açık oturumları kapanır.'
          : 'Hesaba rastgele bir parola atanır; görevli e-postasındaki bağlantıyla kendi parolasını belirler.'
      }
      footer={
        <>
          {errors.general && <p className="mr-auto text-sm font-semibold text-danger">{errors.general}</p>}
          <Button variant="ghost" onClick={onClose}>
            Vazgeç
          </Button>
          <Button variant="primary" loading={busy} onClick={submit}>
            {account ? 'Yetkileri kaydet' : 'Hesabı oluştur'}
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-6">
        {!account && (
          <div className="grid gap-5 sm:grid-cols-2">
            <Field label="Görünen ad" required error={errors.name} hint="Ör. Öğrenci İşleri, Mühendislik">
              <Input autoFocus maxLength={200} value={name} onChange={(e) => setName(e.target.value)} />
            </Field>
            <Field label="E-posta" required error={errors.email}>
              <Input type="email" maxLength={255} value={email} onChange={(e) => setEmail(e.target.value)} />
            </Field>
          </div>
        )}
        <GrantsEditor value={grants} onChange={setGrants} error={errors.grants} />
      </div>
    </Dialog>
  )
}

/** Yetki seçimi: her yetki bir satır; öğrenci doğrulamada kapsam (bütün kurum ya da seçili fakülteler). */
function GrantsEditor({ value, onChange, error }: { value: GrantDraft[]; onChange: (v: GrantDraft[]) => void; error?: string }) {
  const catalog = useCatalog()
  const id = useId()
  const find = (p: StaffPermission) => value.find((g) => g.permission === p)
  const toggle = (p: StaffPermission, on: boolean) =>
    onChange(on ? [...value, { permission: p, facultyIds: [] }] : value.filter((g) => g.permission !== p))
  const setFaculties = (p: StaffPermission, ids: string[]) =>
    onChange(value.map((g) => (g.permission === p ? { ...g, facultyIds: ids } : g)))

  return (
    <fieldset aria-describedby={error ? `${id}-error` : undefined}>
      <legend className="text-md font-heavy">Yetkiler</legend>
      <ul className="mt-2 divide-y divide-rule border-y border-rule">
        {PERMISSIONS.map((p) => {
          const g = find(p.value)
          const scoped = !!g && g.facultyIds.length > 0
          return (
            <li key={p.value} className="py-3">
              <label className="flex cursor-pointer items-start gap-3">
                <input
                  type="checkbox"
                  checked={!!g}
                  onChange={(e) => toggle(p.value, e.target.checked)}
                  className="mt-1 size-[18px] shrink-0 cursor-pointer accent-[var(--ec-ink)]"
                />
                <span>
                  <span className="block font-semibold">{p.title}</span>
                  <span className="block text-sm text-ink-2">{p.description}</span>
                </span>
              </label>
              {g && p.scoped && (
                <div className="mt-3 ml-[30px] flex flex-col gap-2 border-l-[3px] border-rule pl-4">
                  <label className="flex cursor-pointer items-center gap-2 text-md">
                    <input
                      type="radio"
                      name={`${id}-${p.value}`}
                      checked={!scoped}
                      onChange={() => setFaculties(p.value, [])}
                      className="size-4 accent-[var(--ec-ink)]"
                    />
                    Bütün kurum
                  </label>
                  <label className="flex cursor-pointer items-center gap-2 text-md">
                    <input
                      type="radio"
                      name={`${id}-${p.value}`}
                      checked={scoped}
                      onChange={() => setFaculties(p.value, catalog.data?.[0] ? [catalog.data[0].id] : [])}
                      className="size-4 accent-[var(--ec-ink)]"
                    />
                    Seçili fakülteler
                  </label>
                  {scoped && (
                    <div className="mt-1 flex flex-col gap-1.5 pl-6">
                      {(catalog.data ?? []).map((f) => (
                        <label key={f.id} className="flex cursor-pointer items-center gap-2 text-md">
                          <input
                            type="checkbox"
                            checked={g.facultyIds.includes(f.id)}
                            disabled={g.facultyIds.length === 1 && g.facultyIds.includes(f.id)}
                            onChange={(e) =>
                              setFaculties(p.value, e.target.checked ? [...g.facultyIds, f.id] : g.facultyIds.filter((x) => x !== f.id))
                            }
                            className="size-4 accent-[var(--ec-ink)]"
                          />
                          {f.name}
                        </label>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ul>
      {error && (
        <p id={`${id}-error`} className="mt-2 text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </fieldset>
  )
}
