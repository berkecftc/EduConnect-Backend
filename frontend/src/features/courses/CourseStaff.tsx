import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { withTitle } from '@/features/people/titles'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { QueryBoundary } from '@/components/ui/States'
import { STAFF_ROLE_LABEL, useCourseStaff, type Course, type CourseStaff, type StaffRole } from './api'
import { abilities, useAcademicianSearch, useAddStaff, useChangeStaffRole, useRemoveStaff } from './teach'
import { PersonLink } from '@/features/people/PersonLink'

type Assignable = Exclude<StaffRole, 'COORDINATOR'>

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'
const DANGER_LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline'

const ROLE_CHOICES: { value: Assignable; title: string; description: string }[] = [
  {
    value: 'INSTRUCTOR',
    title: 'Hoca',
    description: 'Başvuruları, duyuruları ve materyalleri yönetir; değerlendirme ekler, puan ilan eder.',
  },
  { value: 'ASSISTANT', title: 'Asistan', description: 'Teslimleri görür ve puan verir.' },
]

/**
 * Kadro (F-43): koordinatör ekler, rol değiştirir, çıkarır; kadrodaki herkes kendisi ayrılabilir.
 * Koordinatörün kendisi değiştirilemez (devir yöneticide); arşivdeki derste kadro donar.
 */
export function StaffManage({ course: c, role }: { course: Course; role: StaffRole }) {
  const staff = useCourseStaff(c.id)
  const session = useSession()
  const manage = abilities(role).staff && c.status !== 'ARCHIVED'
  const [adding, setAdding] = useState(false)

  return (
    <section aria-labelledby="ders-kadro">
      <h2 id="ders-kadro" className="text-lg">
        Kadro
      </h2>
      <div className="mt-3">
        <QueryBoundary query={staff} what="Kadro" skeletonRows={2}>
          {(list) => (
            <ul className="divide-y divide-rule border-y border-rule">
              {list.map((s) => (
                <StaffRow key={s.userId} course={c} member={s} manage={manage} me={s.userId === session?.userId} />
              ))}
            </ul>
          )}
        </QueryBoundary>
      </div>
      {manage && (
        <div className="mt-4">
          <Button size="sm" onClick={() => setAdding(true)}>
            Kadroya ekle
          </Button>
          {adding && <AddStaffDialog course={c} existing={(staff.data ?? []).map((s) => s.userId)} onClose={() => setAdding(false)} />}
        </div>
      )}
    </section>
  )
}

function StaffRow({ course: c, member: s, manage, me }: { course: Course; member: CourseStaff; manage: boolean; me: boolean }) {
  const change = useChangeStaffRole(c.id)
  const remove = useRemoveStaff(c.id)
  const navigate = useNavigate()
  const [confirm, setConfirm] = useState<'remove' | 'leave' | null>(null)
  const name = withTitle(s.name, s.title, s.academicTitle)
  const coordinator = s.role === 'COORDINATOR'
  const other: Assignable = s.role === 'INSTRUCTOR' ? 'ASSISTANT' : 'INSTRUCTOR'
  const fail = (e: unknown) => toast.error(toApiError(e).message)

  return (
    <li className="py-3">
      <div className="flex items-start justify-between gap-3">
        <span className="min-w-0">
          <span className="block font-semibold">
            <PersonLink id={s.userId}>{name}</PersonLink>
            {me && <span className="font-normal text-ink-3"> (siz)</span>}
          </span>
          {s.department && <span className="block text-sm text-ink-3">{s.department}</span>}
        </span>
        <Badge tone={coordinator ? 'info' : 'neutral'}>{STAFF_ROLE_LABEL[s.role]}</Badge>
      </div>
      {!coordinator && (manage || me) && c.status !== 'ARCHIVED' && (
        <p className="mt-1.5 flex flex-wrap gap-x-4 gap-y-1">
          {manage && (
            <button
              type="button"
              disabled={change.isPending}
              onClick={() =>
                change.mutate(
                  { userId: s.userId, role: other },
                  {
                    onSuccess: () => toast.success(`${s.name} artık ${STAFF_ROLE_LABEL[other].toLocaleLowerCase('tr-TR')}`),
                    onError: fail,
                  },
                )
              }
              className={LINK}
            >
              {other === 'INSTRUCTOR' ? 'Hoca yap' : 'Asistan yap'}
              <span className="sr-only">: {s.name}</span>
            </button>
          )}
          {manage && !me && (
            <button type="button" onClick={() => setConfirm('remove')} className={DANGER_LINK}>
              Çıkar<span className="sr-only">: {s.name}</span>
            </button>
          )}
          {me && (
            <button type="button" onClick={() => setConfirm('leave')} className={DANGER_LINK}>
              Kadrodan ayrıl
            </button>
          )}
        </p>
      )}
      <ConfirmDialog
        open={confirm === 'remove'}
        onOpenChange={(o) => !o && setConfirm(null)}
        destructive
        title="Kadrodan çıkar"
        description={`${name}, ${c.code} dersinin kadrosundan çıkarılır ve bildirim alır. Verdiği puanlar kayıtlarda kalır.`}
        confirmLabel="Kadrodan çıkar"
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(s.userId, {
            onSuccess: () => {
              toast.success(`${s.name} kadrodan çıkarıldı`)
              setConfirm(null)
            },
            onError: fail,
          })
        }
      />
      <ConfirmDialog
        open={confirm === 'leave'}
        onOpenChange={(o) => !o && setConfirm(null)}
        destructive
        title="Kadrodan ayrıl"
        description={`${c.code} dersinin kadrosundan ayrılırsınız; ders "Verdiğim dersler"den kalkar. Yeniden eklenmek için koordinatöre başvurmanız gerekir.`}
        confirmLabel="Kadrodan ayrıl"
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(s.userId, {
            onSuccess: () => {
              toast.success(`${c.code} kadrosundan ayrıldınız`)
              navigate('/courses')
            },
            onError: fail,
          })
        }
      />
    </li>
  )
}

/** Kadroya ekle: adla arama (en az iki harf), sonuçtan kişi ve görev seçimi. */
function AddStaffDialog({ course: c, existing, onClose }: { course: Course; existing: string[]; onClose: () => void }) {
  const add = useAddStaff(c.id)
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  const [userId, setUserId] = useState<string | undefined>()
  const [role, setRole] = useState<Assignable>('ASSISTANT')
  const [error, setError] = useState<string | null>(null)
  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 250)
    return () => clearTimeout(t)
  }, [q])
  const hits = useAcademicianSearch(query)
  const choices = (hits.data ?? [])
    .filter((h) => !existing.includes(h.id))
    .map((h) => ({
      value: h.id,
      title: withTitle([h.firstName, h.lastName].filter(Boolean).join(' ') || 'Akademisyen', h.title, h.academicTitle),
      description: h.department ?? undefined,
    }))
  const already = (hits.data ?? []).filter((h) => existing.includes(h.id)).length
  const picked = choices.find((x) => x.value === userId)

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Kadroya ekle"
      description={`${c.code} dersine hoca ya da asistan ekleyin. Eklenen kişiye bildirim gider.`}
      confirmLabel="Kadroya ekle"
      loading={add.isPending}
      onConfirm={() => {
        if (!picked) return setError('Listeden bir kişi seçin.')
        add.mutate(
          { userId: picked.value, role },
          {
            onSuccess: () => {
              toast.success(`${picked.title} kadroya ${STAFF_ROLE_LABEL[role].toLocaleLowerCase('tr-TR')} olarak eklendi`)
              onClose()
            },
            onError: (e) => {
              const ae = toApiError(e)
              setError(
                ae.code === 'STAFF_EXISTS'
                  ? 'Bu kişi zaten dersin kadrosunda.'
                  : ae.code === 'STAFF_NOT_ACADEMICIAN'
                    ? 'Yalnız aktif akademisyenler kadroya eklenebilir.'
                    : ae.message,
              )
            },
          },
        )
      }}
    >
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto pr-1">
        <Field label="Akademisyen" hint="Ad ya da soyadından en az iki harf yazın.">
          <Input
            type="search"
            autoFocus
            autoComplete="off"
            value={q}
            onChange={(e) => {
              setQ(e.target.value)
              setUserId(undefined)
              setError(null)
            }}
          />
        </Field>
        {query.trim().length >= 2 && hits.isSuccess && (
          <>
            {choices.length > 0 ? (
              <ChoiceGroup layout="rows" label="Sonuçlar" value={userId} onChange={setUserId} choices={choices} />
            ) : (
              <p className="text-md text-ink-2">
                {already ? 'Bulunanlar zaten kadroda.' : `“${query.trim()}” ile eşleşen akademisyen yok.`}
              </p>
            )}
            {already > 0 && choices.length > 0 && <p className="-mt-3 text-sm text-ink-3">Kadroda olanlar listelenmedi.</p>}
          </>
        )}
        {picked && <ChoiceGroup layout="rows" label="Görevi" value={role} onChange={setRole} choices={ROLE_CHOICES} />}
        {error && <p className="text-sm font-semibold text-danger">{error}</p>}
      </div>
    </ConfirmDialog>
  )
}
