import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { formatInstant } from '@/lib/time'
import { Badge, type BadgeTone } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Panel } from '@/components/ui/Panel'
import { FOUNDER_STATUS_LABEL } from './advise'
import { useFoundingInvitations, useFounderResponse, type ClubCreationRequest } from './governance'

const CREATION_STATUS: Record<ClubCreationRequest['status'], { label: string; tone: BadgeTone }> = {
  PENDING_FOUNDERS: { label: 'Kurucuların yanıtı bekleniyor', tone: 'warning' },
  PENDING: { label: 'Danışman onayında', tone: 'warning' },
  APPROVED: { label: 'Kuruldu', tone: 'success' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
}

type Founder = ClubCreationRequest['founders'][number]

const founderName = (f: Founder) => [f.firstName, f.lastName].filter(Boolean).join(' ') || 'Öğrenci'

/** Kurucuların yanıt özeti: "2 onayladı, 1 yanıt bekleniyor". Başvuran ve `me` (davetteki kişi) sayılmaz. */
function founderSummary(r: ClubCreationRequest, me?: string): string | null {
  const others = r.founders.filter((f) => f.studentId !== r.requestingStudentId && f.studentId !== me)
  if (others.length === 0) return null
  const n = (s: Founder['status']) => others.filter((f) => f.status === s).length
  return [
    n('CONFIRMED') && `${n('CONFIRMED')} onayladı`,
    n('INVITED') && `${n('INVITED')} yanıt bekleniyor`,
    n('DECLINED') && `${n('DECLINED')} katılmadı`,
  ]
    .filter(Boolean)
    .join(', ')
}

/**
 * Kurucu davetleri: başka bir öğrenci sizi kuracağı kulübün kurucuları arasına ekledi. Bütün kurucular
 * yanıt verince başvuru danışmana gider; kulüp kurulursa onaylayan kurucular üye olur. Davet yoksa hiç görünmez.
 */
export function FoundingInvitations() {
  const invitations = useFoundingInvitations()
  const list = invitations.data ?? []
  if (list.length === 0) return null
  return (
    <Panel
      title="Kurucu davetleri"
      className="mt-8 mb-16"
      action={<span className="tabular text-md text-warning">{list.length} yanıt bekliyor</span>}
    >
      <ul className="border-t border-rule">
        {list.map((r) => (
          <Invitation key={r.id} request={r} />
        ))}
      </ul>
    </Panel>
  )
}

function Invitation({ request: r }: { request: ClubCreationRequest }) {
  const session = useSession()
  const respond = useFounderResponse()
  const [declining, setDeclining] = useState(false)
  const requester = r.founders.find((f) => f.studentId === r.requestingStudentId)
  const summary = founderSummary(r, session?.userId)
  const fail = (e: unknown) => toast.error(toApiError(e).message)

  return (
    <li className="grid gap-x-10 gap-y-4 border-b border-rule py-6 md:grid-cols-[minmax(0,1fr)_auto]">
      <div className="min-w-0 border-l-[3px] border-kulup pl-5">
        <p className="text-2xl leading-tight font-heavy">{r.clubName}</p>
        <p className="mt-1 text-md text-ink-2">
          {r.requestingStudentName ?? (requester ? founderName(requester) : 'Bir öğrenci')} sizi kurucular arasına ekledi,{' '}
          {formatInstant(r.requestDate, 'date')}.
        </p>
        {r.about && <p className="mt-3 line-clamp-3 max-w-[64ch] text-md whitespace-pre-line text-ink-2">{r.about}</p>}
        {summary && <p className="mt-2 text-sm text-ink-3">Diğer kurucular: {summary}.</p>}
        <p className="mt-2 text-sm text-ink-3">Bütün kurucular yanıt verince başvuru danışmana gider. Kulüp kurulursa üyesi olursunuz.</p>
      </div>
      <div className="flex flex-wrap items-center gap-5 md:flex-col md:items-end">
        <Button
          variant="primary"
          loading={respond.isPending && respond.variables?.confirm === true}
          onClick={() =>
            respond.mutate(
              { requestId: r.id, confirm: true },
              { onSuccess: () => toast.success(`“${r.clubName}” kurucuları arasına katıldınız`), onError: fail },
            )
          }
        >
          Kurucu olarak katıl
        </Button>
        <button
          type="button"
          onClick={() => setDeclining(true)}
          className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
        >
          Katılmıyorum
        </button>
      </div>
      <ConfirmDialog
        open={declining}
        onOpenChange={setDeclining}
        title={`“${r.clubName}” kurucuları arasına katılmıyor musunuz?`}
        description="Yanıtınız başvurana iletilir ve sonradan değiştirilemez."
        confirmLabel="Katılmıyorum"
        loading={respond.isPending}
        onConfirm={() =>
          respond.mutate(
            { requestId: r.id, confirm: false },
            {
              onSuccess: () => {
                toast.success('Yanıtınız iletildi')
                setDeclining(false)
              },
              onError: fail,
            },
          )
        }
      />
    </li>
  )
}

/** Kuruluş başvurularım: durum, kurucuların yanıtları, ret gerekçesi; kurulan kulübe bağlantı. */
export function CreationRequests({ list }: { list: ClubCreationRequest[] }) {
  const session = useSession()
  const open = (r: ClubCreationRequest) => r.status === 'PENDING' || r.status === 'PENDING_FOUNDERS'
  const sorted = [...list].sort((a, b) => Number(open(b)) - Number(open(a)) || b.requestDate.localeCompare(a.requestDate))
  return (
    <Panel title="Kuruluş başvurularım">
      <ul>
        {sorted.map((r) => {
          const waiting = r.founders.filter((f) => f.status === 'INVITED' && f.studentId !== session?.userId)
          const summary = founderSummary(r)
          return (
            <li key={r.id} className="grid gap-x-6 gap-y-1 border-b border-rule py-3.5 first:pt-0 sm:grid-cols-[minmax(0,1fr)_auto]">
              <span className="min-w-0">
                <span className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                  <span className="font-semibold">{r.clubName}</span>
                  <Badge tone={CREATION_STATUS[r.status].tone}>{CREATION_STATUS[r.status].label}</Badge>
                </span>
                <span className="block text-sm text-ink-3">
                  {r.processedAt
                    ? `Başvuru ${formatInstant(r.requestDate, 'date')}, karar ${formatInstant(r.processedAt, 'date')}`
                    : `${formatInstant(r.requestDate, 'datetime')} tarihinde başvurdunuz`}
                  {summary ? `. Kurucular: ${summary}` : ''}
                  {r.suggestedAdvisorName ? `. Danışman: ${r.suggestedAdvisorName}` : ''}
                </span>
                {r.status === 'PENDING_FOUNDERS' && waiting.length > 0 && (
                  <span className="mt-1 block text-md text-ink-2">
                    {FOUNDER_STATUS_LABEL.INVITED}: {waiting.map(founderName).join(', ')}
                  </span>
                )}
                {r.rejectionReason && <span className="mt-1 block text-md text-ink-2">Gerekçe: {r.rejectionReason}</span>}
              </span>
              {r.status === 'APPROVED' && r.clubId && (
                <Link
                  to={`/clubs/${r.clubId}`}
                  className="self-start text-sm font-semibold underline-offset-4 hover:underline sm:justify-self-end"
                >
                  Kulübe git
                </Link>
              )}
            </li>
          )
        })}
      </ul>
    </Panel>
  )
}
