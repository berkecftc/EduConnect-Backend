import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { formatInstant, formatLocal, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Panel } from '@/components/ui/Panel'
import { EmptyState, PageHeader, QueryBoundary, Skeleton } from '@/components/ui/States'
import {
  END_REASON_LABEL,
  POSITION_END_LABEL,
  isActiveMembership,
  renewalDue,
  roleLabel,
  roleRank,
  useCancelMembershipRequest,
  useMembershipHistory,
  useMyMembershipRequests,
  useMyMemberships,
  useMyPositions,
  useRenewMembership,
  type MembershipRequest,
  type MyMembership,
} from './api'
import { ApprovalItem } from './ApprovalItem'
import { ClubLogo } from './ClubMark'
import { FoundingInvitations, CreationRequests } from './Founding'
import { useMyCreationRequests } from './governance'
import { isPendingApproval, useMyApprovals, type ApprovalRequest } from './manage'

const ROW = 'grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 gap-y-2 sm:grid-cols-[minmax(0,1fr)_14rem]'

/**
 * Kulüplerim: üyelikler (görev ve geçerlilik), bekleyen ve sonuçlanan başvurular, görev ve üyelik geçmişi (F-32, F-38);
 * kişinin kendi talepleri ve hakkında açılanlar (F-89).
 */
export function MyClubsPage() {
  usePageTitle('Kulüplerim')
  const memberships = useMyMemberships()
  const requests = useMyMembershipRequests()
  const history = useMembershipHistory()
  const positions = useMyPositions()
  const creations = useMyCreationRequests()
  const approvals = useMyApprovals()
  const openApprovals = (approvals.data ?? []).filter((r) => isPendingApproval(r.status))
  const decidedApprovals = (approvals.data ?? []).filter((r) => !isPendingApproval(r.status))

  const active = (memberships.data ?? [])
    .filter(isActiveMembership)
    .sort((a, b) => roleRank(a.clubRole) - roleRank(b.clubRole) || a.clubName.localeCompare(b.clubName, 'tr-TR'))
  const ended = (history.data ?? []).filter((m) => !isActiveMembership(m)).sort((a, b) => (b.endedAt ?? '').localeCompare(a.endedAt ?? ''))
  const pastPositions = (positions.data ?? []).filter((p) => p.endedAt)

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Kulüplerim"
        meta={memberships.data ? (active.length > 0 ? `${active.length} kulübe üyesiniz.` : 'Henüz bir kulübe üye değilsiniz.') : undefined}
        actions={
          <>
            <Button asChild>
              <Link to="/clubs/new">Kulüp kur</Link>
            </Button>
            <Button asChild>
              <Link to="/clubs">Tüm kulüpler</Link>
            </Button>
          </>
        }
      />

      <FoundingInvitations />

      {openApprovals.length > 0 && (
        <Panel
          title="Taleplerim"
          className="mt-8 mb-16"
          action={<span className="tabular text-md text-warning">{openApprovals.length} açık</span>}
        >
          <MyApprovals list={openApprovals} />
        </Panel>
      )}

      <div className="mt-8">
        <QueryBoundary query={memberships} what="Üyelikler" skeletonRows={3}>
          {() =>
            active.length === 0 ? (
              <EmptyState
                title="Üye olduğunuz kulüp yok"
                action={
                  <Button asChild variant="primary">
                    <Link to="/clubs">Kulüplere göz at</Link>
                  </Button>
                }
              >
                Kulüp sayfasından üyelik başvurusu yapabilirsiniz. Yönetim onayladığında kulüp burada görünür.
              </EmptyState>
            ) : (
              <Memberships list={active} />
            )
          }
        </QueryBoundary>
      </div>

      <div className="mt-16 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-20 lg:col-span-7">
          {!!creations.data?.length && <CreationRequests list={creations.data} />}
          <Panel title="Üyelik başvurularım">{requests.isPending ? <Skeleton rows={2} /> : <Requests list={requests.data ?? []} />}</Panel>
          {ended.length > 0 && (
            <Panel title="Üyelik geçmişim">
              <ul>
                {ended.map((m) => (
                  <li
                    key={`${m.clubId}-${m.endedAt}`}
                    className="flex flex-wrap items-baseline gap-x-6 gap-y-1 border-b border-rule py-3.5 first:pt-0"
                  >
                    <span className="min-w-0">
                      <Link to={`/clubs/${m.clubId}`} className="font-semibold underline-offset-4 hover:underline">
                        {m.clubName}
                      </Link>
                      <span className="ml-3 text-sm text-ink-3">{roleLabel(m.clubRole)}</span>
                    </span>
                    <span className="text-sm text-ink-2">
                      {m.endReason ? END_REASON_LABEL[m.endReason] : 'Sona erdi'}
                      {m.endedAt ? `, ${formatInstant(m.endedAt, 'date')}` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            </Panel>
          )}
          {decidedApprovals.length > 0 && (
            <Panel title="Sonuçlanan taleplerim">
              <MyApprovals list={decidedApprovals} />
            </Panel>
          )}
        </div>
        {pastPositions.length > 0 && (
          <aside className="lg:col-span-5">
            <Panel title="Önceki görevlerim">
              <ul>
                {pastPositions.map((p) => (
                  <li key={`${p.clubId}-${p.position}-${p.startedAt}`} className="border-b border-rule py-3.5 first:pt-0">
                    <span className="block font-semibold">{p.positionName || roleLabel(p.position)}</span>
                    <span className="block text-sm text-ink-3">
                      {p.clubName}, {formatInstant(p.startedAt, 'date')} – {formatInstant(p.endedAt, 'date')}
                      {p.endReason ? `. ${POSITION_END_LABEL[p.endReason] ?? ''}` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            </Panel>
          </aside>
        )}
      </div>
    </div>
  )
}

/** Talep satırları: hakkında çıkarma talebi açılan savunma ekler, hazırlayan bekleyen talebini geri çeker. */
function MyApprovals({ list }: { list: ApprovalRequest[] }) {
  const me = useSession()?.userId
  return (
    <ul className="border-t border-rule">
      {list.map((r) => {
        const pending = isPendingApproval(r.status)
        const defends = pending && r.type === 'MEMBER_EXPULSION' && r.subjectUserId === me
        return (
          <ApprovalItem
            key={r.id}
            request={r}
            showClub
            self={r.subjectUserId === me}
            mode={defends ? 'defend' : pending && r.preparedBy === me ? 'withdraw' : undefined}
          />
        )
      })}
    </ul>
  )
}

/** Üyelik satırı: kulüp işareti, ad ve görev; sağda geçerlilik ve gerekiyorsa yenileme. */
function Memberships({ list }: { list: MyMembership[] }) {
  const renew = useRenewMembership()
  const today = nowLocalIso().slice(0, 10)
  return (
    <ul className="border-t-2 border-ink">
      {list.map((m) => {
        const due = renewalDue(m, today) && m.clubStatus !== 'CLOSED'
        return (
          <li key={m.clubId} className={`${ROW} border-b border-rule py-6`}>
            <span className="flex min-w-0 items-center gap-5">
              <ClubLogo name={m.clubName} logoUrl={m.logoUrl} />
              <span className="min-w-0">
                <Link to={`/clubs/${m.clubId}`} className="block text-2xl leading-tight font-heavy underline-offset-4 hover:underline">
                  {m.clubName}
                </Link>
                <span className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1 text-md text-ink-3">
                  <span className={roleRank(m.clubRole) < roleRank('MEMBER') ? 'font-semibold text-ink' : undefined}>
                    {roleLabel(m.clubRole)}
                  </span>
                  {m.termStartDate && <span>{formatInstant(m.termStartDate, 'date')} tarihinden beri</span>}
                  {m.clubStatus === 'AWAITING_ADVISOR' && <Badge tone="warning">Danışman bekleniyor</Badge>}
                  {m.clubStatus === 'CLOSED' && <Badge tone="neutral">Kulüp kapatıldı</Badge>}
                </span>
              </span>
            </span>
            <span className="flex flex-col items-end gap-2 text-right">
              {m.validUntil && (
                <span className={due ? 'text-sm font-semibold text-warning' : 'text-sm text-ink-3'}>
                  {formatLocal(m.validUntil, 'date')} tarihine kadar geçerli
                </span>
              )}
              {due && (
                <Button
                  size="sm"
                  variant="primary"
                  loading={renew.isPending && renew.variables === m.clubId}
                  onClick={() =>
                    renew.mutate(m.clubId, {
                      onSuccess: () => toast.success(`${m.clubName} üyeliğiniz yenilendi`),
                      onError: (e) => toast.error(toApiError(e).message),
                    })
                  }
                >
                  Üyeliği yenile
                </Button>
              )}
            </span>
          </li>
        )
      })}
    </ul>
  )
}

const REQUEST_STATUS: Record<MembershipRequest['status'], { label: string; tone: 'warning' | 'success' | 'danger' }> = {
  PENDING: { label: 'Yönetimde', tone: 'warning' },
  APPROVED: { label: 'Kabul edildi', tone: 'success' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
}

function Requests({ list }: { list: MembershipRequest[] }) {
  const cancel = useCancelMembershipRequest()
  const [target, setTarget] = useState<MembershipRequest | null>(null)
  if (list.length === 0) return <p className="text-ink-3">Üyelik başvurunuz yok.</p>
  const sorted = [...list].sort(
    (a, b) => Number(b.status === 'PENDING') - Number(a.status === 'PENDING') || b.requestDate.localeCompare(a.requestDate),
  )
  return (
    <>
      <ul>
        {sorted.map((r) => (
          <li key={r.id} className="grid gap-x-6 gap-y-1 border-b border-rule py-3.5 first:pt-0 sm:grid-cols-[minmax(0,1fr)_auto]">
            <span className="min-w-0">
              <span className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                <Link to={`/clubs/${r.clubId}`} className="font-semibold underline-offset-4 hover:underline">
                  {r.clubName}
                </Link>
                <Badge tone={REQUEST_STATUS[r.status].tone}>{REQUEST_STATUS[r.status].label}</Badge>
              </span>
              <span className="block text-sm text-ink-3">
                {r.processedDate
                  ? `Başvuru ${formatInstant(r.requestDate, 'datetime')}, karar ${formatInstant(r.processedDate, 'datetime')}`
                  : `${formatInstant(r.requestDate, 'datetime')} tarihinde başvurdunuz`}
              </span>
              {r.rejectionReason && <span className="mt-1 block text-md text-ink-2">Gerekçe: {r.rejectionReason}</span>}
            </span>
            <span className="flex items-center gap-4 sm:flex-col sm:items-end">
              {r.status === 'PENDING' && (
                <button
                  type="button"
                  onClick={() => setTarget(r)}
                  className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                >
                  Geri çek
                </button>
              )}
            </span>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={!!target}
        onOpenChange={(o) => !o && setTarget(null)}
        title="Başvuruyu geri çek"
        description={`${target?.clubName ?? ''} üyelik başvurunuz geri çekilecek. İsterseniz daha sonra yeniden başvurabilirsiniz.`}
        confirmLabel="Başvuruyu geri çek"
        loading={cancel.isPending}
        onConfirm={() =>
          target &&
          cancel.mutate(target.clubId, {
            onSuccess: () => {
              toast.success('Başvuru geri çekildi')
              setTarget(null)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </>
  )
}
