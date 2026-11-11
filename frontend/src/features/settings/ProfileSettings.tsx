import { useRef, useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant } from '@/lib/time'
import { LEVEL_LABEL, useCatalog, useTitles } from '@/features/auth/register/api'
import { useMe, type Me } from '@/features/me/useMe'
import { Avatar } from '@/features/profile/Avatar'
import {
  CHANGE_STATUS,
  STAFF_CATEGORY_LABEL,
  useChangeRequests,
  useSubmitChangeRequest,
  useUpdateProfile,
  useUploadAvatar,
  type ChangeRequestInput,
} from '@/features/profile/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { QueryBoundary, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'

const BIO_MAX = 255
const HOURS_MAX = 500
const REASON_MAX = 1000
const AVATAR_ACCEPT = 'image/jpeg,image/png,image/webp'
const AVATAR_MAX_BYTES = 5 * 1024 * 1024

/** Personel mi öğrenci mi (çift bağlılıkta ikisi de): alanlar buna göre gösterilir. */
const isStaff = (me: Me) => !!(me.academicTitle || me.staffCategory || me.affiliations?.includes('ACADEMICIAN'))
const isStudent = (me: Me) => !!(me.studentNumber || me.programId || me.affiliations?.includes('STUDENT'))

/** Profil (F-54, F-55): fotoğraf ve hakkımda doğrudan; ad, unvan, bölüm/program onaylı talep ister. */
export function ProfileSettings() {
  const me = useMe()
  if (me.isPending) return <Skeleton rows={4} />
  if (!me.data) return <p className="text-ink-2">Bu hesap türünde düzenlenecek bir profil yok.</p>
  const m = me.data
  return (
    <div className="flex flex-col gap-20">
      <Photo me={m} />
      <About me={m} />
      <Official me={m} />
    </div>
  )
}

function Photo({ me }: { me: Me }) {
  const upload = useUploadAvatar()
  const input = useRef<HTMLInputElement>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const name = [me.firstName, me.lastName].filter(Boolean).join(' ')

  const pick = (file: File | undefined) => {
    if (!file) return
    if (!AVATAR_ACCEPT.split(',').includes(file.type)) return toast.error('Fotoğraf JPG, PNG ya da WebP olmalı.')
    if (file.size > AVATAR_MAX_BYTES) return toast.error('Fotoğraf en fazla 5 MB olabilir.')
    const url = URL.createObjectURL(file)
    setPreview(url)
    upload.mutate(file, {
      onSuccess: () => toast.success('Profil fotoğrafınız güncellendi'),
      onError: (e) => {
        setPreview(null)
        toast.error(toApiError(e).message)
      },
      onSettled: () => URL.revokeObjectURL(url),
    })
  }

  return (
    <Panel title="Profil fotoğrafı">
      <div className="flex flex-wrap items-center gap-8">
        <Avatar name={name} src={preview ?? me.profileImageUrl} size="lg" />
        <div>
          <p className="max-w-[40ch] text-md text-ink-2">Kare bir fotoğraf en iyi sonucu verir. JPG, PNG ya da WebP; en fazla 5 MB.</p>
          <input ref={input} type="file" accept={AVATAR_ACCEPT} className="sr-only" tabIndex={-1} onChange={(e) => (pick(e.target.files?.[0]), (e.target.value = ''))} />
          <Button className="mt-4" loading={upload.isPending} onClick={() => input.current?.click()}>
            {me.profileImageUrl ? 'Fotoğrafı değiştir' : 'Fotoğraf yükle'}
          </Button>
        </div>
      </div>
    </Panel>
  )
}

function About({ me }: { me: Me }) {
  const update = useUpdateProfile(me.id)
  const staff = isStaff(me)
  const [bio, setBio] = useState(me.bio ?? '')
  const [office, setOffice] = useState(me.officeNumber ?? '')
  const [hours, setHours] = useState(me.officeHours ?? '')

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    update.mutate(staff ? { bio, officeNumber: office, officeHours: hours } : { bio }, {
      onSuccess: () => toast.success('Profiliniz güncellendi'),
      onError: (err) => toast.error(toApiError(err).message),
    })
  }

  return (
    <Panel title={staff ? 'Hakkımda ve iletişim' : 'Hakkımda'}>
      <form onSubmit={onSubmit} noValidate className="flex max-w-[40rem] flex-col gap-5">
        <Field label="Hakkımda" hint="Profilinizde adınızın altında görünür. İsteğe bağlı.">
          <Textarea maxLength={BIO_MAX} valueLength={bio.length} value={bio} onChange={(e) => setBio(e.target.value)} rows={3} />
        </Field>
        {staff && (
          <>
            <Field label="Ofis" hint="Ör. B Blok 312">
              <Input maxLength={255} value={office} onChange={(e) => setOffice(e.target.value)} />
            </Field>
            <Field label="Görüşme saatleri" hint="Öğrencilerin göreceği biçimde yazın. Ör. Salı 14.00–16.00, Perşembe 10.00–12.00">
              <Textarea maxLength={HOURS_MAX} valueLength={hours.length} value={hours} onChange={(e) => setHours(e.target.value)} rows={3} />
            </Field>
          </>
        )}
        <div>
          <Button type="submit" variant="primary" loading={update.isPending}>
            Kaydet
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/** Resmî bilgiler salt okunur; değişiklik için gerekçeli talep (yalnız değişen alanlar gönderilir). */
function Official({ me }: { me: Me }) {
  const staff = isStaff(me)
  const student = isStudent(me)
  const requests = useChangeRequests()
  const pending = (requests.data ?? []).some((r) => r.status === 'PENDING')
  const [open, setOpen] = useState(false)

  const rows: [string, ReactNode][] = [
    ['Ad soyad', [me.firstName, me.lastName].filter(Boolean).join(' ') || '–'],
    ...(staff
      ? ([
          ['Unvan', me.title ?? '–'],
          ['Bölüm', me.department ?? '–'],
          ['Kadro', me.staffCategory ? (STAFF_CATEGORY_LABEL[me.staffCategory] ?? me.staffCategory) : '–'],
        ] as [string, ReactNode][])
      : []),
    ...(student
      ? ([
          ['Öğrenci numarası', <span className="tabular">{me.studentNumber ?? '–'}</span>],
          ['Fakülte', me.facultyName ?? '–'],
          ['Program', me.programName ? `${me.programName}${me.programLevel ? `, ${LEVEL_LABEL[me.programLevel as keyof typeof LEVEL_LABEL] ?? ''}` : ''}` : (me.department ?? '–')],
          ['Sınıf', me.classYear ? `${me.classYear}. sınıf${me.entryYear ? `, ${me.entryYear} girişli` : ''}` : '–'],
        ] as [string, ReactNode][])
      : []),
  ]

  return (
    <Panel
      title="Resmî bilgiler"
      action={
        pending ? (
          <span className="text-sm text-ink-2">Bekleyen talebiniz var</span>
        ) : (
          <button type="button" onClick={() => setOpen(true)} className="text-md font-semibold underline-offset-4 hover:underline">
            Değişiklik talep et
          </button>
        )
      }
    >
      <p className="max-w-[60ch] text-md text-ink-2">Bu bilgiler kurum kayıtlarından gelir ve doğrudan düzenlenemez. Yanlışsa gerekçesiyle değişiklik talep edin; doğrulayıcı onaylayınca güncellenir.</p>
      <dl className="mt-6 max-w-[40rem]">
        {rows.map(([k, v]) => (
          <div key={k} className="grid gap-x-6 gap-y-0.5 border-b border-rule py-3 first:border-t sm:grid-cols-[11rem_minmax(0,1fr)]">
            <dt className="text-md text-ink-3">{k}</dt>
            <dd className="text-md font-semibold">{v}</dd>
          </div>
        ))}
      </dl>

      <div className="mt-10">
        <h3 className="text-xl font-heavy">Taleplerim</h3>
        <QueryBoundary query={requests} what="Talepler" skeletonRows={2}>
          {(list) =>
            list.length === 0 ? (
              <p className="mt-2 text-ink-3">Henüz bir değişiklik talebiniz yok.</p>
            ) : (
              <ul className="mt-3">
                {[...list]
                  .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
                  .map((r) => (
                    <li key={r.id} className="border-b border-rule py-3.5 first:pt-0">
                      <p className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                        <span className="font-semibold">{summarize(r)}</span>
                        <Badge tone={CHANGE_STATUS[r.status].tone}>{CHANGE_STATUS[r.status].label}</Badge>
                      </p>
                      <p className="text-sm text-ink-2">
                        {formatInstant(r.createdAt, 'date')}. Gerekçe: {r.reason}
                      </p>
                      {r.reviewNote && <p className="mt-1 text-md text-ink-2">Doğrulayıcı notu: {r.reviewNote}</p>}
                    </li>
                  ))}
              </ul>
            )
          }
        </QueryBoundary>
      </div>

      {open && <ChangeDialog me={me} staff={staff} student={student} onClose={() => setOpen(false)} />}
    </Panel>
  )
}

function summarize(r: { firstName: string | null; lastName: string | null; titleLabel: string | null; programId: string | null; departmentId: string | null }) {
  const parts = [
    r.firstName && r.lastName ? `Ad soyad: ${r.firstName} ${r.lastName}` : r.firstName ? `Ad: ${r.firstName}` : r.lastName ? `Soyad: ${r.lastName}` : null,
    r.titleLabel && `Unvan: ${r.titleLabel}`,
    r.programId && 'Program değişikliği',
    r.departmentId && 'Bölüm değişikliği',
  ].filter(Boolean)
  return parts.join(', ') || 'Değişiklik talebi'
}

/** Talep penceresi: alanlar mevcut değerlerle dolu gelir; yalnız değişenler ve gerekçe gönderilir. */
function ChangeDialog({ me, staff, student, onClose }: { me: Me; staff: boolean; student: boolean; onClose: () => void }) {
  const submit = useSubmitChangeRequest()
  const catalog = useCatalog()
  const titles = useTitles(staff)
  const [first, setFirst] = useState(me.firstName ?? '')
  const [last, setLast] = useState(me.lastName ?? '')
  const [title, setTitle] = useState(me.academicTitle ?? '')
  const [programId, setProgramId] = useState(me.programId ?? '')
  const [departmentId, setDepartmentId] = useState(me.departmentId ?? '')
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)

  const send = () => {
    const input: ChangeRequestInput = { reason: reason.trim() }
    if (first.trim() !== (me.firstName ?? '')) input.firstName = first.trim()
    if (last.trim() !== (me.lastName ?? '')) input.lastName = last.trim()
    if (staff && title && title !== (me.academicTitle ?? '')) input.title = title
    if (student && programId && programId !== (me.programId ?? '')) input.programId = programId
    if (staff && departmentId && departmentId !== (me.departmentId ?? '')) input.departmentId = departmentId
    if (Object.keys(input).length === 1) return setError('Değiştirmek istediğiniz bir bilgi seçin.')
    if (!input.reason) return setError('Gerekçe yazın.')
    setError(null)
    submit.mutate(input, {
      onSuccess: () => {
        toast.success('Değişiklik talebiniz doğrulayıcıya iletildi')
        onClose()
      },
      onError: (e) => setError(toApiError(e).message),
    })
  }

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Resmî bilgi değişikliği"
      description="Yalnız değiştirdiğiniz alanlar gönderilir. Talep onaylanana kadar mevcut bilgileriniz geçerlidir."
      confirmLabel="Talebi gönder"
      loading={submit.isPending}
      onConfirm={send}
    >
      <div className="flex max-h-[55vh] flex-col gap-4 overflow-y-auto pr-1">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Ad">
            <Input value={first} onChange={(e) => setFirst(e.target.value)} />
          </Field>
          <Field label="Soyad">
            <Input value={last} onChange={(e) => setLast(e.target.value)} />
          </Field>
        </div>
        {staff && (
          <Field label="Unvan">
            <Select value={title} onChange={(e) => setTitle(e.target.value)} disabled={!titles.data}>
              <option value="">Değişmesin</option>
              {titles.data?.map((t) => (
                <option key={t.code} value={t.code}>
                  {t.label}
                </option>
              ))}
            </Select>
          </Field>
        )}
        {student && (
          <Field label="Program">
            <Select value={programId} onChange={(e) => setProgramId(e.target.value)} disabled={!catalog.data}>
              <option value="">Değişmesin</option>
              {catalog.data?.flatMap((f) =>
                f.departments.map((d) => (
                  <optgroup key={d.id} label={`${f.name} · ${d.name}`}>
                    {d.programs.filter((p) => p.active).map((p) => (
                      <option key={p.id} value={p.id}>
                        {p.name} ({LEVEL_LABEL[p.level]})
                      </option>
                    ))}
                  </optgroup>
                )),
              )}
            </Select>
          </Field>
        )}
        {staff && (
          <Field label="Bölüm">
            <Select value={departmentId} onChange={(e) => setDepartmentId(e.target.value)} disabled={!catalog.data}>
              <option value="">Değişmesin</option>
              {catalog.data?.map((f) => (
                <optgroup key={f.id} label={f.name}>
                  {f.departments.filter((d) => d.active).map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.name}
                    </option>
                  ))}
                </optgroup>
              ))}
            </Select>
          </Field>
        )}
        <Field label="Gerekçe" required hint="Ör. Nüfus kaydımda soyadım değişti. Doğrulayıcı gerekirse belge ister.">
          <Textarea maxLength={REASON_MAX} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} rows={3} />
        </Field>
        {error && <p className="text-sm font-semibold text-danger">{error}</p>}
      </div>
    </ConfirmDialog>
  )
}
