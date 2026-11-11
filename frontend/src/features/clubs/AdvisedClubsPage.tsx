import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, formatRelative } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useAdvisorQueue } from '@/features/events/manage'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { WorkStrip } from '@/components/ui/WorkStrip'
import {
  FOUNDER_STATUS_LABEL,
  useAdviseDecision,
  useAdvisedClubs,
  useAdvisorClubAction,
  useAdvisorOffers,
  useCreationRequests,
  useRoleChangeRequests,
  type AdviseDecision,
  type ClubCreationRequest,
  type RoleChangeRequest,
} from './advise'
import { isMemberRole, roleLabel, useClubs, type ClubDetails } from './api'
import { ClubLogo } from './ClubMark'
import { useApprovalInbox } from './manage'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'
const DANGER_LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline'
const isPresident = (role: string) => role === 'PRESIDENT' || role === 'ROLE_CLUB_OFFICIAL'
const fullName = (m: { firstName: string | null; lastName: string | null }) => [m.firstName, m.lastName].filter(Boolean).join(' ')

/**
 * Danışmanlıklarım (F-28, F-85): danışmanı olduğunuz kulüpler ve yalnız sizin karar verebileceğiniz işler —
 * kulüp kuruluş başvuruları, görev değişiklikleri, danışmanlık teklifleri. Kulüp onayları "Onay bekleyenler"de.
 */
export function AdvisedClubsPage() {
  usePageTitle('Danışmanlıklarım')
  const { accesses, details } = useAdvisedClubs()
  const creation = useCreationRequests()
  const offers = useAdvisorOffers()
  const roleChanges = useRoleChangeRequests()
  const inbox = useApprovalInbox()
  const eventQueue = useAdvisorQueue(true)

  const pendingCreation = (creation.data ?? []).filter((r) => r.status === 'PENDING')
  const pendingOffers = (offers.data ?? []).filter((o) => o.status.startsWith('PENDING'))
  const pendingRoles = (roleChanges.data ?? []).filter((r) => r.status === 'PENDING')
  const approvals = inbox.data && eventQueue.data ? inbox.data.length + eventQueue.data.length : undefined
  const clubs = details.map((d) => d.data).filter((c): c is ClubDetails => !!c)

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader
        title="Danışmanlıklarım"
        meta={
          accesses.data
            ? clubs.length || details.length
              ? `${details.length} kulübün danışmanısınız.`
              : 'Şu anda danışmanı olduğunuz kulüp yok.'
            : undefined
        }
      />
      <div className="mt-8">
        <WorkStrip
          items={[
            {
              key: 'onay',
              to: '/clubs/approvals',
              value: approvals,
              label: 'kulüp onayı bekliyor',
              note: 'etkinlik, duyuru ve kararlar',
              urgent: !!approvals,
            },
            {
              key: 'gorev',
              anchor: 'gorev-degisiklikleri',
              value: roleChanges.data ? pendingRoles.length : undefined,
              label: 'görev değişikliği',
              urgent: pendingRoles.length > 0,
            },
            {
              key: 'kurulus',
              anchor: 'kurulus-basvurulari',
              value: creation.data ? pendingCreation.length : undefined,
              label: 'kuruluş başvurusu',
              urgent: pendingCreation.length > 0,
            },
            {
              key: 'teklif',
              anchor: 'danismanlik-teklifleri',
              value: offers.data ? pendingOffers.length : undefined,
              label: 'danışmanlık teklifi',
              urgent: pendingOffers.length > 0,
            },
          ]}
        />
      </div>

      <div className="mt-14 flex flex-col gap-20">
        <Panel title="Kulüpleriniz" anchor="kulupler">
          {accesses.isPending || details.some((d) => d.isPending) ? (
            <Skeleton rows={3} />
          ) : clubs.length === 0 ? (
            <EmptyState title="Danışmanı olduğunuz kulüp yok">
              Bir kulüp sizi danışman olarak önerdiğinde ya da kuruluş başvurusunu onayladığınızda kulüp burada görünür.
            </EmptyState>
          ) : (
            <ul className="border-t border-rule">
              {clubs.map((c) => (
                <AdvisedClubRow key={c.id} club={c} />
              ))}
            </ul>
          )}
        </Panel>

        {pendingRoles.length > 0 && (
          <Panel title="Görev değişiklikleri" anchor="gorev-degisiklikleri">
            <ul className="border-t border-rule">
              {pendingRoles.map((r) => (
                <RoleChangeRow key={r.id} request={r} />
              ))}
            </ul>
          </Panel>
        )}

        {pendingCreation.length > 0 && (
          <Panel title="Kuruluş başvuruları" anchor="kurulus-basvurulari">
            <ul className="border-t border-rule">
              {pendingCreation.map((r) => (
                <CreationRow key={r.id} request={r} />
              ))}
            </ul>
          </Panel>
        )}

        {pendingOffers.length > 0 && (
          <Panel title="Danışmanlık teklifleri" anchor="danismanlik-teklifleri">
            <ul className="border-t border-rule">
              {pendingOffers.map((o) => (
                <li key={o.id} className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
                  <div className="min-w-0">
                    <p className="text-lg font-semibold">
                      <Link to={`/clubs/${o.clubId}`} className="underline-offset-4 hover:underline">
                        {o.clubName}
                      </Link>
                    </p>
                    <p className="mt-0.5 text-sm text-ink-3">
                      {o.requestedByName ? `${o.requestedByName}, ` : 'Kulüp başkanı, '}
                      {formatRelative(o.createdAt)}
                    </p>
                    {o.message && <p className="mt-2 max-w-[64ch] text-md text-ink-2">{o.message}</p>}
                  </div>
                  <DecisionButtons
                    decision={(approve, reason) => ({ kind: 'offer', id: o.id, approve, reason })}
                    approveLabel="Kabul et"
                    rejectTitle="Danışmanlık teklifini reddet"
                    rejectDescription={`${o.clubName} için danışmanlık teklifini reddedersiniz; kulüp başkanına bildirim gider.`}
                    success={(approve) => (approve ? `${o.clubName} kulübünün danışmanısınız` : 'Teklif reddedildi')}
                  />
                </li>
              ))}
            </ul>
          </Panel>
        )}

        {roleChanges.data && creation.data && offers.data && pendingRoles.length + pendingCreation.length + pendingOffers.length === 0 && (
          <p className="text-md text-ink-2">
            Görev değişikliği, kuruluş başvurusu ya da danışmanlık teklifi beklemiyor. Kulüplerin etkinlik ve duyuru onayları{' '}
            <Link to="/clubs/approvals" className="font-semibold text-ink underline-offset-4 hover:underline">
              Onay bekleyenler
            </Link>{' '}
            sayfasında.
          </p>
        )}
      </div>
    </div>
  )
}

/** Onayla / Reddet: ret isteğe bağlı gerekçeyle pencerede. */
function DecisionButtons({
  decision,
  approveLabel = 'Onayla',
  rejectTitle,
  rejectDescription,
  success,
}: {
  decision: (approve: boolean, reason?: string) => AdviseDecision
  approveLabel?: string
  rejectTitle: string
  rejectDescription: string
  success: (approve: boolean) => string
}) {
  const decide = useAdviseDecision()
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const run = (approve: boolean) =>
    decide.mutate(decision(approve, reason), {
      onSuccess: () => {
        toast.success(success(approve))
        setRejecting(false)
      },
      onError: (e) => toast.error(toApiError(e).message),
    })
  return (
    <div className="flex items-center gap-3">
      <Button size="sm" variant="primary" loading={decide.isPending && decide.variables?.approve === true} onClick={() => run(true)}>
        {approveLabel}
      </Button>
      <Button size="sm" onClick={() => setRejecting(true)}>
        Reddet
      </Button>
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        destructive
        title={rejectTitle}
        description={rejectDescription}
        confirmLabel="Reddet"
        loading={decide.isPending}
        onConfirm={() => run(false)}
      >
        <Field label="Gerekçe" hint="İsteğe bağlı; ilgili kişiye iletilir.">
          <Textarea rows={3} maxLength={255} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
        </Field>
      </ConfirmDialog>
    </div>
  )
}

function RoleChangeRow({ request: r }: { request: RoleChangeRequest }) {
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
      <div className="min-w-0">
        <p className="text-lg font-semibold">{r.studentName ?? 'Üye'}</p>
        <p className="mt-0.5 text-md">
          {r.currentRole && !isMemberRole(r.currentRole) ? `${roleLabel(r.currentRole)} → ` : ''}
          <span className="font-semibold">{roleLabel(r.requestedRole)}</span>
        </p>
        <p className="mt-0.5 text-sm text-ink-3">
          <Link to={`/clubs/${r.clubId}`} className="underline-offset-4 hover:underline">
            {r.clubName}
          </Link>
          {r.requesterName ? `, isteyen ${r.requesterName}` : ''}, {formatRelative(r.createdAt)}
        </p>
      </div>
      <DecisionButtons
        decision={(approve, reason) => ({ kind: 'roleChange', id: r.id, approve, reason })}
        rejectTitle="Görev değişikliğini reddet"
        rejectDescription={`${r.studentName ?? 'Üye'} için ${roleLabel(r.requestedRole)} görevi verilmez; isteyen kişiye bildirim gider.`}
        success={(approve) => (approve ? `${r.studentName ?? 'Üye'}: ${roleLabel(r.requestedRole)}` : 'Görev değişikliği reddedildi')}
      />
    </li>
  )
}

function CreationRow({ request: r }: { request: ClubCreationRequest }) {
  const applicant =
    r.requestingStudentName ??
    fullName(r.founders.find((f) => f.studentId === r.requestingStudentId) ?? { firstName: null, lastName: null })
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="text-lg font-semibold">{r.clubName}</p>
        <p className="mt-0.5 text-sm text-ink-3">
          {applicant ? `${applicant} başvurdu, ` : ''}
          {formatInstant(r.requestDate, 'date')}
        </p>
        {r.about && <p className="mt-2 max-w-[68ch] text-md whitespace-pre-line text-ink-2">{r.about}</p>}
        {r.founders.length > 0 && (
          <p className="mt-2 text-sm text-ink-2">
            <span className="font-semibold text-ink">Kurucular: </span>
            {r.founders.map((f, i) => (
              <span key={f.studentId}>
                {i > 0 && ', '}
                {fullName(f) || 'Öğrenci'}
                {f.status !== 'CONFIRMED' && (
                  <span className="text-ink-3"> ({FOUNDER_STATUS_LABEL[f.status].toLocaleLowerCase('tr-TR')})</span>
                )}
              </span>
            ))}
          </p>
        )}
      </div>
      <DecisionButtons
        decision={(approve, reason) => ({ kind: 'creation', id: r.id, approve, reason })}
        approveLabel="Kulübü onayla"
        rejectTitle="Kuruluş başvurusunu reddet"
        rejectDescription={`${r.clubName} kurulmaz; başvurana ve kuruculara bildirim gider.`}
        success={(approve) => (approve ? `${r.clubName} kuruldu; danışmanı sizsiniz` : 'Başvuru reddedildi')}
      />
    </li>
  )
}

type Dialog = 'appoint' | 'removePresident' | 'close' | 'resign' | null

/** Danışman olunan kulüp: durum, başkan; başkan ata/görevden al, kulübü kapat, danışmanlığı bırak. */
function AdvisedClubRow({ club: c }: { club: ClubDetails }) {
  const clubs = useClubs()
  const act = useAdvisorClubAction(c.id)
  const [dialog, setDialog] = useState<Dialog>(null)
  const [reason, setReason] = useState('')
  const [studentId, setStudentId] = useState('')
  const [error, setError] = useState<string | null>(null)
  const logo = clubs.data?.find((x) => x.id === c.id)?.logoUrl ?? c.logoUrl
  const president = c.members.find((m) => isPresident(m.role))
  const candidates = c.members.filter((m) => !isPresident(m.role))
  const closed = c.status === 'CLOSED'

  const open = (d: Dialog) => {
    setDialog(d)
    setReason('')
    setStudentId('')
    setError(null)
  }
  const run = (a: Parameters<typeof act.mutate>[0], done: string) =>
    act.mutate(a, {
      onSuccess: () => {
        toast.success(done)
        setDialog(null)
      },
      onError: (e) => setError(toApiError(e).message),
    })

  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-center">
      <div className="flex min-w-0 items-center gap-4">
        <ClubLogo name={c.name} logoUrl={logo} />
        <div className="min-w-0">
          <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <Link to={`/clubs/${c.id}?sekme=yonetim`} className="text-lg font-semibold underline-offset-4 hover:underline">
              {c.name}
            </Link>
            {closed && <Badge tone="neutral">Kapalı</Badge>}
            {!closed && !president && <Badge tone="warning">Başkan yok</Badge>}
          </p>
          <p className="mt-0.5 text-sm text-ink-3">
            {c.memberCount ?? 0} üye
            {president && `, başkan ${fullName(president) || 'atanmış'}`}
            {closed && c.closedAt && `, ${formatInstant(c.closedAt, 'date')} kapandı`}
          </p>
        </div>
      </div>
      {!closed && (
        <p className="flex flex-wrap gap-x-5 gap-y-1">
          {president ? (
            <button type="button" onClick={() => open('removePresident')} className={DANGER_LINK}>
              Başkanı görevden al<span className="sr-only">: {c.name}</span>
            </button>
          ) : (
            <button type="button" onClick={() => open('appoint')} className={LINK}>
              Başkan ata<span className="sr-only">: {c.name}</span>
            </button>
          )}
          <button type="button" onClick={() => open('close')} className={DANGER_LINK}>
            Kulübü kapat<span className="sr-only">: {c.name}</span>
          </button>
          <button type="button" onClick={() => open('resign')} className={DANGER_LINK}>
            Danışmanlığı bırak<span className="sr-only">: {c.name}</span>
          </button>
        </p>
      )}

      <ConfirmDialog
        open={dialog === 'appoint'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Başkan ata"
        description={`${c.name} kulübünün aktif üyelerinden birini başkan yaparsınız; kişiye bildirim gider. Başka bir kulüpte yönetim görevi olan üye atanamaz.`}
        confirmLabel="Başkan yap"
        loading={act.isPending}
        onConfirm={() => (studentId ? run({ kind: 'appoint', studentId }, 'Başkan atandı') : setError('Bir üye seçin.'))}
      >
        <Field label="Üye" required error={error ?? undefined}>
          <Select value={studentId} onChange={(e) => setStudentId(e.target.value)}>
            <option value="">Seçin</option>
            {candidates.map((m) => (
              <option key={m.studentId} value={m.studentId}>
                {fullName(m) || 'Adı gelmeyen üye'}
                {isMemberRole(m.role) ? '' : ` (${roleLabel(m.role)})`}
              </option>
            ))}
          </Select>
        </Field>
      </ConfirmDialog>

      <ReasonDialog
        open={dialog === 'removePresident'}
        onClose={() => setDialog(null)}
        title="Başkanı görevden al"
        description={`${fullName(president ?? { firstName: null, lastName: null }) || 'Başkan'} görevden alınır; başkan yardımcısı varsa başkan olur, yoksa yeni başkanı siz atarsınız.`}
        confirmLabel="Görevden al"
        required={false}
        reason={reason}
        setReason={setReason}
        error={error}
        loading={act.isPending}
        onConfirm={() => run({ kind: 'removePresident', reason }, 'Başkan görevden alındı')}
      />
      <ReasonDialog
        open={dialog === 'close'}
        onClose={() => setDialog(null)}
        title="Kulübü kapat"
        description={`${c.name} kapanır: üyelikler ve görevler sona erer, bekleyen istekler kapanır. Kulüp sayfası salt okunur kalır. Bu işlem geri alınamaz.`}
        confirmLabel="Kulübü kapat"
        required
        reason={reason}
        setReason={setReason}
        error={error}
        loading={act.isPending}
        onConfirm={() =>
          reason.trim() ? run({ kind: 'close', reason }, `${c.name} kapatıldı`) : setError('Gerekçe yazın; üyelere iletilir.')
        }
      />
      <ReasonDialog
        open={dialog === 'resign'}
        onClose={() => setDialog(null)}
        title="Danışmanlığı bırak"
        description={`${c.name} yeni bir danışman kabul edene kadar danışman onayı gereken işler bekler; kulüp başkanına bildirim gider.`}
        confirmLabel="Danışmanlığı bırak"
        required={false}
        reason={reason}
        setReason={setReason}
        error={error}
        loading={act.isPending}
        onConfirm={() => run({ kind: 'resign', reason }, `${c.name} danışmanlığını bıraktınız`)}
      />
    </li>
  )
}

function ReasonDialog({
  open,
  onClose,
  title,
  description,
  confirmLabel,
  required,
  reason,
  setReason,
  error,
  loading,
  onConfirm,
}: {
  open: boolean
  onClose: () => void
  title: string
  description: string
  confirmLabel: string
  required: boolean
  reason: string
  setReason: (v: string) => void
  error: string | null
  loading: boolean
  onConfirm: () => void
}) {
  return (
    <ConfirmDialog
      open={open}
      onOpenChange={(o) => !o && onClose()}
      destructive
      title={title}
      description={description}
      confirmLabel={confirmLabel}
      loading={loading}
      onConfirm={onConfirm}
    >
      <Field
        label="Gerekçe"
        required={required}
        hint={required ? undefined : 'İsteğe bağlı; kulüp başkanına iletilir.'}
        error={error ?? undefined}
      >
        <Textarea rows={3} maxLength={1000} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
    </ConfirmDialog>
  )
}
