import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { formatInstant } from '@/lib/time'
import { withTitle } from '@/features/people/titles'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { AcademicianPicker, type PickedAcademician } from './AcademicianPicker'
import { CATEGORY_LABEL, roleLabel, roleRank, useClubBoard, type ClubCategory, type ClubDetails, type ClubMember } from './api'
import { ClubLogo } from './ClubMark'
import {
  ABOUT_MAX,
  ASSIGNABLE_ROLES,
  LINK_FIELDS,
  LOGO_TYPES,
  REASON_MAX,
  canonicalRole,
  holderCount,
  logoProblem,
  profileChanges,
  profileDraftOf,
  useAdvisorChanges,
  useCancelAdvisorChange,
  useLogoChange,
  useProfileChange,
  useProposeAdvisor,
  useProposeRole,
  useRequestClosure,
  useRequestExpulsion,
  useRevokeRole,
  validateProfileDraft,
  type AdvisorChange,
  type ProfileDraft,
  type ProfileDraftErrors,
} from './governance'
import { APPROVAL_STATUS, can, isPendingApproval, useClubApprovals, type ApprovalRequest, type ClubAccess } from './manage'
import { PersonLink } from '@/features/people/PersonLink'

const NOTE_MAX = 1000
const memberName = (m: { firstName: string | null; lastName: string | null }) => [m.firstName, m.lastName].filter(Boolean).join(' ')

/**
 * Ayarlar sekmesi: kurul, üyeler, kulüp bilgileri, logo, danışman ve kapatma. Her bölüm yetkiye bağlı (F-85);
 * her değişiklik onay kaydı açar, kimin onaylayacağı metinde yazılır. Bekleyen talep varken aynı türden yenisi açılmaz.
 */
export function ClubSettings({ club, access }: { club: ClubDetails; access: ClubAccess }) {
  const proposes = can(access, 'PROPOSE_POSITION_CHANGE')
  const edits = can(access, 'PREPARE_PROFILE_CHANGE')
  const logo = can(access, 'PREPARE_LOGO_CHANGE')
  const advisor = can(access, 'PROPOSE_ADVISOR_CHANGE')
  const closes = can(access, 'REQUEST_CLUB_CLOSURE')
  const approvals = useClubApprovals(club.id, can(access, 'VIEW_DECISIONS'))
  const pending = (approvals.data ?? []).filter((r) => isPendingApproval(r.status))
  const first = (type: string) => pending.find((r) => r.type === type)
  // Başkan (ya da vekili) hazırlarsa yalnız danışman onaylar; diğer görevlilerde önce başkan.
  const route = access.actingPresident ? 'danışman onayına' : 'başkan ve danışman onayına'

  return (
    <div className="grid gap-20 lg:grid-cols-12 lg:gap-10">
      <div className="flex min-w-0 flex-col gap-20 lg:col-span-8">
        {proposes && <Board clubId={club.id} pending={pending} />}
        {proposes && <Members club={club} pending={pending} />}
        {edits && <ProfileForm club={club} route={route} pending={first('CLUB_PROFILE_UPDATE')} />}
        {closes && <Closure club={club} pending={first('CLUB_CLOSURE')} />}
      </div>
      {(advisor || logo) && (
        <aside className="flex flex-col gap-20 lg:col-span-4">
          {advisor && <Advisor club={club} />}
          {logo && <Logo club={club} route={route} pending={first('CLUB_LOGO_CHANGE')} />}
        </aside>
      )}
    </div>
  )
}

/** Bekleyen talep satırı: ne istendi, hangi onayda, ne zaman açıldı. */
function PendingLine({ request: r, children }: { request: ApprovalRequest; children: React.ReactNode }) {
  return (
    <p className="flex flex-wrap items-baseline gap-x-4 gap-y-1 border-l-[3px] border-warning pl-4 text-md">
      <span>{children}</span>
      <Badge tone={APPROVAL_STATUS[r.status].tone}>{APPROVAL_STATUS[r.status].label}</Badge>
      <span className="text-sm text-ink-3">{formatInstant(r.createdAt, 'date')}</span>
    </p>
  )
}

// ——— Kurul ———

type BoardDialog = { kind: 'assign' } | { kind: 'change'; member: ClubMember } | { kind: 'revoke'; member: ClubMember }

/**
 * Kurul: görevliler göreve göre sıralı; başkan ve siz dışındakilerde görev değiştirme ve görevden alma.
 * Danışman onayındaki görev değişiklikleri altta. Görev vermek için öğrenci numarası yeter (kişi kulübe üye olmalı).
 */
function Board({ clubId, pending }: { clubId: string; pending: ApprovalRequest[] }) {
  const session = useSession()
  const board = useClubBoard(clubId)
  const [dialog, setDialog] = useState<BoardDialog | null>(null)
  const roleChanges = pending.filter((r) => r.type === 'ROLE_CHANGE')
  const waiting = new Set(roleChanges.map((r) => r.subjectUserId))

  return (
    <Panel
      anchor="ayarlar-kurul"
      title="Kurul"
      action={
        <Button size="sm" onClick={() => setDialog({ kind: 'assign' })}>
          Görev ver
        </Button>
      }
    >
      <p className="max-w-[64ch] text-md text-ink-2">
        Görev değişiklikleri danışman onayından sonra geçerli olur. Başkanlık seçimle ya da danışman kararıyla değişir.
      </p>
      <div className="mt-6">
        <QueryBoundary query={board} what="Kurul" skeletonRows={3}>
          {(list) => (
            <ul className="border-t border-rule">
              {[...list]
                .sort((a, b) => roleRank(a.role) - roleRank(b.role))
                .map((m) => {
                  const president = canonicalRole(m.role) === 'PRESIDENT'
                  const me = m.studentId === session?.userId
                  const name = memberName(m) || 'Görevli'
                  return (
                    <li
                      key={m.studentId}
                      className="grid gap-x-6 gap-y-1 border-b border-rule py-4 sm:grid-cols-[minmax(0,1fr)_minmax(0,14rem)_13rem] sm:items-baseline"
                    >
                      <span className="min-w-0 font-semibold">
                        <PersonLink id={m.studentId}>{name}</PersonLink>
                        {me && <span className="ml-2 text-sm font-normal text-ink-3">siz</span>}
                      </span>
                      <span className="text-md text-ink-2">{roleLabel(m.role)}</span>
                      <span className="flex flex-wrap gap-x-5 gap-y-1 text-sm sm:justify-end">
                        {president || me ? null : waiting.has(m.studentId) ? (
                          <span className="text-ink-3">Talep danışmanda</span>
                        ) : (
                          <>
                            <button
                              type="button"
                              onClick={() => setDialog({ kind: 'change', member: m })}
                              className="font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                            >
                              Görevi değiştir<span className="sr-only">: {name}</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => setDialog({ kind: 'revoke', member: m })}
                              className="font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                            >
                              Görevden al<span className="sr-only">: {name}</span>
                            </button>
                          </>
                        )}
                      </span>
                    </li>
                  )
                })}
            </ul>
          )}
        </QueryBoundary>
      </div>

      {roleChanges.length > 0 && (
        <div className="mt-8">
          <h3 className="text-lg font-heavy">Onay bekleyen görev değişiklikleri</h3>
          <div className="mt-3 flex flex-col gap-3">
            {roleChanges.map((r) => (
              <PendingLine key={r.id} request={r}>
                <span className="font-semibold">{r.subjectUserName ?? 'Üye'}</span>:{' '}
                {canonicalRole(r.requestedPosition) === 'MEMBER'
                  ? `${roleLabel(r.currentPosition).toLocaleLowerCase('tr-TR')} görevinden alınıyor`
                  : `${roleLabel(r.currentPosition)} → ${roleLabel(r.requestedPosition)}`}
              </PendingLine>
            ))}
          </div>
        </div>
      )}

      {dialog?.kind === 'revoke' ? (
        <RevokeDialog clubId={clubId} member={dialog.member} onClose={() => setDialog(null)} />
      ) : dialog ? (
        <RoleDialog
          clubId={clubId}
          board={board.data ?? []}
          member={dialog.kind === 'change' ? dialog.member : undefined}
          onClose={() => setDialog(null)}
        />
      ) : null}
    </Panel>
  )
}

/** Görev ver (öğrenci numarasıyla) ya da kuruldaki birinin görevini değiştir. Dolu görevler seçilemez. */
function RoleDialog({ clubId, board, member, onClose }: { clubId: string; board: ClubMember[]; member?: ClubMember; onClose: () => void }) {
  const propose = useProposeRole(clubId)
  const [number, setNumber] = useState('')
  const [role, setRole] = useState('')
  const [errors, setErrors] = useState<{ number?: string; role?: string }>({})
  const [failure, setFailure] = useState<string | null>(null)
  const current = member ? canonicalRole(member.role) : null
  const name = member ? memberName(member) || 'Görevli' : null

  const submit = () => {
    const next: typeof errors = {}
    if (!member && !number.trim()) next.number = 'Öğrenci numarasını yazın.'
    if (!role) next.role = 'Görevi seçin.'
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) return
    propose.mutate(member ? { studentId: member.studentId, role } : { studentNumber: number, role }, {
      onSuccess: () => {
        toast.success('Görev talebi danışman onayına gönderildi')
        onClose()
      },
      onError: (e) => setFailure(toApiError(e).message),
    })
  }

  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={member ? `${name}: görevi değiştir` : 'Görev ver'}
      description={
        member
          ? `Şu anki görevi ${roleLabel(member.role).toLocaleLowerCase('tr-TR')}. Danışman onaylarsa yeni görev başlar.`
          : 'Öğrenci kulübün üyesi olmalı. Danışman onaylarsa görev başlar. Bir öğrenci aynı anda yalnız bir kulüpte yönetim görevi alabilir.'
      }
      confirmLabel="Danışmana gönder"
      loading={propose.isPending}
      onConfirm={submit}
    >
      <div className="flex flex-col gap-5">
        {!member && (
          <Field label="Öğrenci numarası" required error={errors.number}>
            <Input
              autoFocus
              inputMode="numeric"
              autoComplete="off"
              maxLength={20}
              value={number}
              onChange={(e) => {
                setNumber(e.target.value)
                setErrors((x) => ({ ...x, number: undefined }))
                setFailure(null)
              }}
              className="max-w-[14rem]"
            />
          </Field>
        )}
        <Field label="Görev" required error={errors.role} hint="Kişi sayısı sınırlı görevlerde parantez içinde dolu yer sayısı yazar.">
          <Select
            value={role}
            placeholder="Görev seçin"
            onChange={(e) => {
              setRole(e.target.value)
              setErrors((x) => ({ ...x, role: undefined }))
              setFailure(null)
            }}
          >
            {ASSIGNABLE_ROLES.filter((r) => r.code !== current).map((r) => {
              const n = holderCount(board, r.code)
              const full = r.limit != null && n >= r.limit
              return (
                <option key={r.code} value={r.code} disabled={full}>
                  {r.label}
                  {r.limit != null ? ` (${n}/${r.limit}${full ? ', dolu' : ''})` : ''}
                </option>
              )
            })}
          </Select>
        </Field>
        {failure && <p className="text-sm font-semibold text-danger">{failure}</p>}
      </div>
    </ConfirmDialog>
  )
}

function RevokeDialog({ clubId, member, onClose }: { clubId: string; member: ClubMember; onClose: () => void }) {
  const revoke = useRevokeRole(clubId)
  const name = memberName(member) || 'Görevli'
  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`${name} görevden alınsın mı?`}
      description={`${roleLabel(member.role)} görevi sona erer; ${name} kulübe üye olarak devam eder. Talep danışman onayına gider.`}
      confirmLabel="Görevden al"
      destructive
      loading={revoke.isPending}
      onConfirm={() =>
        revoke.mutate(member.studentId, {
          onSuccess: () => {
            toast.success('Görevden alma talebi danışman onayına gönderildi')
            onClose()
          },
          onError: (e) => toast.error(toApiError(e).message),
        })
      }
    />
  )
}

// ——— Üyeler ———

const MEMBER_LIMIT = 30

/**
 * Üyeler: üyelikten çıkarma talebi (gerekçe zorunlu, danışman karar verir). Başkan ve siz listede yoksunuz.
 * Üye adları kulüp ayrıntısında geldiğinde görünür; ad yoksa bölüm hiç gösterilmez.
 */
function Members({ club, pending }: { club: ClubDetails; pending: ApprovalRequest[] }) {
  const session = useSession()
  const expel = useRequestExpulsion(club.id)
  const [q, setQ] = useState('')
  const [target, setTarget] = useState<ClubMember | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const expelling = new Set(pending.filter((r) => r.type === 'MEMBER_EXPULSION').map((r) => r.subjectUserId))
  const named = club.members
    .filter((m) => memberName(m) && canonicalRole(m.role) !== 'PRESIDENT' && m.studentId !== session?.userId)
    .sort((a, b) => roleRank(a.role) - roleRank(b.role) || memberName(a).localeCompare(memberName(b), 'tr-TR'))
  if (named.length === 0) return null
  const needle = q.trim().toLocaleLowerCase('tr-TR')
  const shown = named.filter((m) => !needle || memberName(m).toLocaleLowerCase('tr-TR').includes(needle))
  const close = () => {
    setTarget(null)
    setReason('')
    setError(null)
  }

  return (
    <Panel anchor="ayarlar-uyeler" title="Üyeler" action={<span className="tabular text-md text-ink-3">{named.length} kişi</span>}>
      {named.length > 8 && (
        <label className="block max-w-[22rem]">
          <span className="mb-1 block text-sm font-semibold">Üye ara</span>
          <Input type="search" value={q} onChange={(e) => setQ(e.target.value)} />
        </label>
      )}
      <ul className="mt-5 border-t border-rule">
        {shown.slice(0, MEMBER_LIMIT).map((m) => {
          const name = memberName(m)
          return (
            <li key={m.studentId} className="flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1 border-b border-rule py-3">
              <span className="min-w-0">
                <PersonLink id={m.studentId} className="font-semibold">
                  {name}
                </PersonLink>
                {canonicalRole(m.role) !== 'MEMBER' && <span className="ml-3 text-sm text-ink-3">{roleLabel(m.role)}</span>}
              </span>
              {expelling.has(m.studentId) ? (
                <span className="text-sm text-ink-3">Çıkarma talebi danışmanda</span>
              ) : (
                <button
                  type="button"
                  onClick={() => setTarget(m)}
                  className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                >
                  Üyelikten çıkar<span className="sr-only">: {name}</span>
                </button>
              )}
            </li>
          )
        })}
      </ul>
      {shown.length === 0 && <p className="mt-4 text-md text-ink-2">“{q.trim()}” ile eşleşen üye yok.</p>}
      {shown.length > MEMBER_LIMIT && (
        <p className="mt-4 text-sm text-ink-3">{shown.length - MEMBER_LIMIT} üye daha var; aramayla bulabilirsiniz.</p>
      )}

      <ConfirmDialog
        open={!!target}
        onOpenChange={(o) => !o && close()}
        title={`${target ? memberName(target) : ''} üyelikten çıkarılsın mı?`}
        description="Talep danışman onayına gider. Danışman onaylarsa üyelik sona erer; kişi kulübe yeniden başvurabilir."
        confirmLabel="Danışmana gönder"
        destructive
        loading={expel.isPending}
        onConfirm={() => {
          if (!target) return
          if (!reason.trim()) return setError('Gerekçeyi yazın.')
          expel.mutate(
            { studentId: target.studentId, reason },
            {
              onSuccess: () => {
                toast.success('Çıkarma talebi danışman onayına gönderildi')
                close()
              },
              onError: (e) => setError(toApiError(e).message),
            },
          )
        }}
      >
        <Field label="Gerekçe" required error={error ?? undefined} hint="Danışman kararını bu gerekçeye bakarak verir.">
          <Textarea
            rows={4}
            maxLength={REASON_MAX}
            valueLength={reason.length}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value)
              setError(null)
            }}
          />
        </Field>
      </ConfirmDialog>
    </Panel>
  )
}

// ——— Kulüp bilgileri ———

const LINK_LABEL: Record<(typeof LINK_FIELDS)[number], string> = {
  websiteUrl: 'Web sitesi',
  instagramUrl: 'Instagram',
  xUrl: 'X',
  linkedinUrl: 'LinkedIn',
}

/** Kulüp bilgileri: yalnız değişen alanlar gönderilir; boşaltılan alan silinir. Onaylanınca kulüp sayfasında görünür. */
function ProfileForm({ club, route, pending }: { club: ClubDetails; route: string; pending?: ApprovalRequest }) {
  const change = useProfileChange(club.id)
  const [initial] = useState(() => profileDraftOf(club))
  const [d, setD] = useState<ProfileDraft>(initial)
  const [note, setNote] = useState('')
  const [errors, setErrors] = useState<ProfileDraftErrors>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const [unchanged, setUnchanged] = useState(false)

  const set = (k: keyof ProfileDraft) => (e: { target: { value: string } }) => {
    setD((x) => ({ ...x, [k]: e.target.value }))
    setErrors((x) => (x[k] ? { ...x, [k]: undefined } : x))
    setFailure(null)
    setUnchanged(false)
  }
  const err = (k: keyof ProfileDraft) => errors[k] ?? failure?.fieldErrors.find((f) => f.field === k)?.message

  if (pending) {
    return (
      <Panel anchor="ayarlar-bilgiler" title="Kulüp bilgileri">
        <PendingLine request={pending}>Kulüp bilgileri için bir değişiklik talebi var.</PendingLine>
        <p className="mt-3 max-w-[64ch] text-md text-ink-2">Talep sonuçlanınca yeni bir değişiklik gönderebilirsiniz.</p>
      </Panel>
    )
  }

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const next = validateProfileDraft(d)
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) return
    const changes = profileChanges(initial, d)
    if (!changes) return setUnchanged(true)
    change.mutate(
      { changes, note },
      {
        onSuccess: (r) => {
          toast.success(r.status === 'APPROVED' ? 'Kulüp bilgileri güncellendi' : `Değişiklik ${route} gönderildi`)
          setNote('')
        },
        onError: (x) => setFailure(toApiError(x)),
      },
    )
  }

  return (
    <Panel anchor="ayarlar-bilgiler" title="Kulüp bilgileri">
      <p className="max-w-[64ch] text-md text-ink-2">
        Değişiklikler {route} gider; onaylanınca kulüp sayfasında görünür. Boşalttığınız alan silinir.
      </p>
      <form noValidate onSubmit={onSubmit} className="mt-6 flex max-w-[40rem] flex-col gap-5">
        <Field label="Tanıtım" error={err('about')}>
          <Textarea rows={6} maxLength={ABOUT_MAX} valueLength={d.about.length} value={d.about} onChange={set('about')} />
        </Field>
        <Field label="Kategori" error={err('category')} className="max-w-[20rem]">
          <Select value={d.category} onChange={set('category')} placeholder={initial.category ? undefined : 'Kategori seçin'}>
            {(Object.keys(CATEGORY_LABEL) as ClubCategory[]).map((c) => (
              <option key={c} value={c}>
                {CATEGORY_LABEL[c]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="İletişim e-postası" error={err('contactEmail')}>
          <Input type="email" autoComplete="off" maxLength={255} value={d.contactEmail} onChange={set('contactEmail')} />
        </Field>
        <div className="grid gap-5 sm:grid-cols-2">
          {LINK_FIELDS.map((k) => (
            <Field key={k} label={LINK_LABEL[k]} error={err(k)}>
              <Input
                type="url"
                autoComplete="off"
                spellCheck={false}
                maxLength={255}
                placeholder="https://"
                value={d[k]}
                onChange={set(k)}
              />
            </Field>
          ))}
        </div>
        <Field label="Onaylayanlara not" hint="İsteğe bağlı. Değişikliğin nedeni.">
          <Textarea rows={2} maxLength={NOTE_MAX} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
        {unchanged && <p className="text-sm font-semibold text-danger">Gönderilecek bir değişiklik yok.</p>}
        {failure && failure.fieldErrors.length === 0 && (
          <Notice variant="line" tone="danger" title="Gönderilemedi">
            {failure.message}
          </Notice>
        )}
        <div>
          <Button type="submit" variant="primary" loading={change.isPending}>
            Onaya gönder
          </Button>
        </div>
      </form>
    </Panel>
  )
}

// ——— Logo ———

function Logo({ club, route, pending }: { club: ClubDetails; route: string; pending?: ApprovalRequest }) {
  const upload = useLogoChange(club.id)
  const input = useRef<HTMLInputElement>(null)
  const [picked, setPicked] = useState<{ file: File; url: string } | null>(null)
  const [note, setNote] = useState('')
  // Önizleme adresi seçimde açılır; yenisi seçilince, vazgeçilince ya da bölüm kapanınca bırakılır.
  const shown = useRef<string | null>(null)
  useEffect(() => () => void (shown.current && URL.revokeObjectURL(shown.current)), [])
  const show = (next: { file: File; url: string } | null) => {
    if (shown.current) URL.revokeObjectURL(shown.current)
    shown.current = next?.url ?? null
    setPicked(next)
  }
  const file = picked?.file ?? null
  const preview = picked?.url ?? null

  const pick = (f: File | undefined) => {
    if (!f) return
    const problem = logoProblem(f)
    if (problem) return toast.error(problem)
    show({ file: f, url: URL.createObjectURL(f) })
  }

  return (
    <Panel anchor="ayarlar-logo" title="Logo">
      <div className="flex items-end gap-6">
        <figure>
          <ClubLogo name={club.name} logoUrl={club.logoUrl} size="lg" />
          {preview && <figcaption className="mt-1 text-sm text-ink-3">Şimdiki</figcaption>}
        </figure>
        {preview && (
          <figure>
            {/* Yerel önizleme (blob:) ClubLogo'nun kabul ettiği adreslerden değil; aynı kare doğrudan çizilir. */}
            <span className="grid size-24 place-items-center overflow-hidden border border-rule bg-surface">
              <img src={preview} alt="" className="size-full object-contain" />
            </span>
            <figcaption className="mt-1 text-sm font-semibold">Yeni</figcaption>
          </figure>
        )}
      </div>
      {pending ? (
        <div className="mt-5">
          <PendingLine request={pending}>Yeni logo onayda.</PendingLine>
        </div>
      ) : (
        <>
          <p className="mt-5 text-md text-ink-2">
            Kare bir görsel en iyi sonucu verir. JPEG, PNG, GIF ya da WEBP; en fazla 5 MB. Yeni logo {route} gider.
          </p>
          <input
            ref={input}
            type="file"
            accept={LOGO_TYPES.join(',')}
            className="sr-only"
            tabIndex={-1}
            onChange={(e) => (pick(e.target.files?.[0]), (e.target.value = ''))}
          />
          {!file ? (
            <Button className="mt-5" onClick={() => input.current?.click()}>
              Görsel seç
            </Button>
          ) : (
            <div className="mt-5 flex flex-col gap-4">
              <Field label="Onaylayanlara not" hint="İsteğe bağlı.">
                <Textarea rows={2} maxLength={NOTE_MAX} valueLength={note.length} value={note} onChange={(e) => setNote(e.target.value)} />
              </Field>
              <div className="flex flex-wrap items-center gap-5">
                <Button
                  variant="primary"
                  loading={upload.isPending}
                  onClick={() =>
                    upload.mutate(
                      { file, note },
                      {
                        onSuccess: (r) => {
                          toast.success(r.status === 'APPROVED' ? 'Logo güncellendi' : `Yeni logo ${route} gönderildi`)
                          show(null)
                          setNote('')
                        },
                        onError: (e) => toast.error(toApiError(e).message),
                      },
                    )
                  }
                >
                  Onaya gönder
                </Button>
                <button
                  type="button"
                  onClick={() => input.current?.click()}
                  className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                >
                  Başka görsel seç
                </button>
                <button
                  type="button"
                  onClick={() => show(null)}
                  className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                >
                  Vazgeç
                </button>
              </div>
            </div>
          )}
        </>
      )}
    </Panel>
  )
}

// ——— Danışman ———

const OFFER_STATUS: Record<AdvisorChange['status'], { label: string; tone: BadgeTone }> = {
  PENDING_PRESIDENT: { label: 'Yanıt bekleniyor', tone: 'warning' },
  PENDING_ADVISOR: { label: 'Yanıt bekleniyor', tone: 'warning' },
  APPROVED: { label: 'Kabul etti', tone: 'success' },
  REJECTED: { label: 'Kabul etmedi', tone: 'danger' },
  WITHDRAWN: { label: 'Geri çekildi', tone: 'neutral' },
}

/**
 * Danışman: şu anki danışman ve öneriler. Önerilen akademisyen kabul ederse kulübün danışmanı olur;
 * bekleyen öneri varken yenisi açılmaz, geri çekilebilir.
 */
function Advisor({ club }: { club: ClubDetails }) {
  const changes = useAdvisorChanges(club.id, true)
  const cancel = useCancelAdvisorChange(club.id)
  const [proposing, setProposing] = useState(false)
  const [withdraw, setWithdraw] = useState<AdvisorChange | null>(null)
  const list = [...(changes.data ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
  const open = list.find((r) => isPendingApproval(r.status))
  const recent = list.filter((r) => r !== open).slice(0, 3)
  const nameOf = (r: AdvisorChange) => r.proposedAdvisorName ?? 'Önerdiğiniz akademisyen'

  return (
    <Panel anchor="ayarlar-danisman" title="Danışman">
      {club.advisorName ? (
        <p className="text-lg font-heavy">{withTitle(club.advisorName, club.advisorTitle)}</p>
      ) : (
        <p className="text-lg font-heavy text-warning">Kulübün danışmanı yok</p>
      )}
      <p className="mt-1 text-md text-ink-2">Önerdiğiniz akademisyen kabul ederse kulübün yeni danışmanı olur.</p>

      {open ? (
        <div className="mt-5 border-l-[3px] border-warning pl-4">
          <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span className="font-semibold">{nameOf(open)}</span>
            <Badge tone={OFFER_STATUS[open.status].tone}>{OFFER_STATUS[open.status].label}</Badge>
          </p>
          <p className="text-sm text-ink-3">{formatInstant(open.createdAt, 'date')} tarihinde önerildi</p>
          {open.message && <p className="mt-1 text-md text-ink-2">“{open.message}”</p>}
          <button
            type="button"
            onClick={() => setWithdraw(open)}
            className="mt-2 text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
          >
            Öneriyi geri çek
          </button>
        </div>
      ) : (
        <Button className="mt-5" onClick={() => setProposing(true)}>
          Yeni danışman öner
        </Button>
      )}

      {recent.length > 0 && (
        <ul className="mt-6 border-t border-rule">
          {recent.map((r) => (
            <li key={r.id} className="border-b border-rule py-3">
              <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
                <span className="font-semibold">{nameOf(r)}</span>
                <Badge tone={OFFER_STATUS[r.status].tone}>{OFFER_STATUS[r.status].label}</Badge>
              </span>
              <span className="block text-sm text-ink-3">{formatInstant(r.decidedAt ?? r.createdAt, 'date')}</span>
              {r.rejectionReason && <span className="block text-md text-ink-2">Gerekçe: {r.rejectionReason}</span>}
            </li>
          ))}
        </ul>
      )}

      {proposing && <ProposeAdvisorDialog club={club} onClose={() => setProposing(false)} />}
      <ConfirmDialog
        open={!!withdraw}
        onOpenChange={(o) => !o && setWithdraw(null)}
        title="Öneriyi geri çek"
        description={`${withdraw ? nameOf(withdraw) : ''} için yaptığınız danışmanlık önerisi geri çekilecek.`}
        confirmLabel="Geri çek"
        loading={cancel.isPending}
        onConfirm={() =>
          withdraw &&
          cancel.mutate(withdraw.id, {
            onSuccess: () => {
              toast.success('Öneri geri çekildi')
              setWithdraw(null)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </Panel>
  )
}

function ProposeAdvisorDialog({ club, onClose }: { club: ClubDetails; onClose: () => void }) {
  const propose = useProposeAdvisor(club.id)
  const [picked, setPicked] = useState<PickedAcademician | null>(null)
  const [message, setMessage] = useState('')
  const [error, setError] = useState<string | null>(null)
  return (
    <ConfirmDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Yeni danışman öner"
      description="Öneri akademisyene bildirim olarak gider. Kabul ederse kulübün danışmanı olur ve önceki danışmanlık sona erer."
      confirmLabel="Öneriyi gönder"
      loading={propose.isPending}
      onConfirm={() => {
        if (!picked) return setError('Akademisyeni listeden seçin.')
        propose.mutate(
          { advisorId: picked.id, message },
          {
            onSuccess: () => {
              toast.success(`Öneri ${picked.name} adlı akademisyene gönderildi`)
              onClose()
            },
            onError: (e) => setError(toApiError(e).message),
          },
        )
      }}
    >
      <div className="flex max-h-[60vh] flex-col gap-5 overflow-y-auto pr-1">
        <AcademicianPicker
          label="Akademisyen"
          autoFocus
          value={picked}
          exclude={[club.academicAdvisorId]}
          onChange={(v) => {
            setPicked(v)
            setError(null)
          }}
        />
        <Field label="Mesajınız" hint="İsteğe bağlı. Akademisyen öneriyle birlikte görür.">
          <Textarea
            rows={3}
            maxLength={NOTE_MAX}
            valueLength={message.length}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
          />
        </Field>
        {error && <p className="text-sm font-semibold text-danger">{error}</p>}
      </div>
    </ConfirmDialog>
  )
}

// ——— Kapatma ———

function Closure({ club, pending }: { club: ClubDetails; pending?: ApprovalRequest }) {
  const request = useRequestClosure(club.id)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  return (
    <Panel anchor="ayarlar-kapat" title="Kulübü kapat">
      <p className="max-w-[64ch] text-md text-ink-2">
        Kapatma talebi danışmana gider. Danışman onaylarsa kulüp kapanır: üyelikler ve görevler sona erer, yeni üyelik ve etkinlik olmaz.
        Kulübün sayfası arşiv olarak kalır.
      </p>
      {pending ? (
        <div className="mt-5">
          <PendingLine request={pending}>Kapatma talebi açık.</PendingLine>
        </div>
      ) : (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="mt-5 text-md font-semibold text-danger underline-offset-4 hover:underline"
        >
          Kapatma talebi aç
        </button>
      )}
      <ConfirmDialog
        open={open}
        onOpenChange={(o) => {
          setOpen(o)
          if (!o) setError(null)
        }}
        title={`${club.name} kapatılsın mı?`}
        description="Talep danışmana gider. Danışman onaylarsa kulüp kapanır ve bu geri alınamaz."
        confirmLabel="Danışmana gönder"
        destructive
        loading={request.isPending}
        onConfirm={() => {
          if (!reason.trim()) return setError('Gerekçeyi yazın.')
          request.mutate(reason, {
            onSuccess: () => {
              toast.success('Kapatma talebi danışman onayına gönderildi')
              setOpen(false)
              setReason('')
            },
            onError: (e) => setError(toApiError(e).message),
          })
        }}
      >
        <Field label="Gerekçe" required error={error ?? undefined} hint="Kulüp kapanırsa bu gerekçe kulüp sayfasında görünür.">
          <Textarea
            rows={4}
            maxLength={REASON_MAX}
            valueLength={reason.length}
            value={reason}
            onChange={(e) => {
              setReason(e.target.value)
              setError(null)
            }}
          />
        </Field>
      </ConfirmDialog>
    </Panel>
  )
}
