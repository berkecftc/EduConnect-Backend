import { useId } from 'react'
import { Link } from 'react-router-dom'
import { hasRole, useSession } from '@/lib/auth/session'
import { usePageTitle } from '@/lib/usePageTitle'
import { EventApprovals } from '@/features/events/EventApprovals'
import { useAdvisorQueue, usePresidentQueue } from '@/features/events/manage'
import { useAwaitingClubPosts } from '@/features/posts/api'
import { ClubPostApprovals } from '@/features/posts/ClubPostApprovals'
import { EmptyState, PageHeader, QueryBoundary, Skeleton } from '@/components/ui/States'
import { ApprovalItem } from './ApprovalItem'
import { useClubs } from './api'
import { ClubLogo } from './ClubMark'
import { useApprovalInbox, useClubAccesses, type ApprovalRequest } from './manage'

/**
 * Onay bekleyenler: başkanlığını yaptığınız kulüplerde başkan onayı, danışmanı olduğunuz kulüplerde danışman onayı
 * bekleyen etkinlikler ve istekler. Etkinlikler önce, ardından kulübe göre gruplu istekler (en eski önce).
 */
export function ApprovalsPage() {
  usePageTitle('Onay bekleyenler')
  const session = useSession()
  const inbox = useApprovalInbox()
  const accesses = useClubAccesses()
  const presides = (accesses.data ?? []).some((a) => a.permissions.includes('APPROVE_AS_PRESIDENT'))
  const advises = hasRole(session, 'ROLE_ACADEMICIAN') || (accesses.data ?? []).some((a) => a.advisor)
  const presidentQueue = usePresidentQueue(presides)
  const advisorQueue = useAdvisorQueue(advises)
  const clubPosts = useAwaitingClubPosts((accesses.data ?? []).filter((a) => a.actingPresident).map((a) => a.clubId))
  // Onay kaydı logo taşımaz; kulüp listesinden (önbellekte) alınır.
  const clubs = useClubs()
  const logos = new Map((clubs.data ?? []).map((c) => [c.id, c.logoUrl]))

  const events = [...(presidentQueue.data ?? []), ...(advisorQueue.data ?? [])].length
  const total = (inbox.data?.length ?? 0) + events + clubPosts.posts.length
  const loadingEvents = (presides && presidentQueue.isPending) || (advises && advisorQueue.isPending)

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader
        title="Onay bekleyenler"
        meta={inbox.data && !loadingEvents ? (total > 0 ? `${total} kayıt kararınızı bekliyor.` : 'Kararınızı bekleyen kayıt yok.') : undefined}
      />
      <div className="mt-10 flex flex-col gap-20">
        <ClubPostApprovals posts={clubPosts.posts} />
        {loadingEvents ? <Skeleton rows={2} /> : <EventApprovals president={presidentQueue.data ?? []} advisor={advisorQueue.data ?? []} />}
        <QueryBoundary query={inbox} what="Onay kutusu" skeletonRows={4}>
          {(list) =>
            list.length === 0 ? (
              events === 0 && !loadingEvents && clubPosts.posts.length === 0 ? (
                <EmptyState title="Her şey güncel">
                  Etkinlikler, duyurular, görev değişiklikleri ve kulüp bilgisi güncellemeleri onayınıza düştüğünde burada görünür.
                </EmptyState>
              ) : null
            ) : (
              <div className="flex flex-col gap-20">
                {groupByClub(list).map(([clubId, items]) => (
                  <ClubGroup key={clubId} items={items} logoUrl={logos.get(clubId)} />
                ))}
              </div>
            )
          }
        </QueryBoundary>
      </div>
    </div>
  )
}

function groupByClub(list: ApprovalRequest[]): [string, ApprovalRequest[]][] {
  const groups = new Map<string, ApprovalRequest[]>()
  for (const r of list) groups.set(r.clubId, [...(groups.get(r.clubId) ?? []), r])
  return [...groups.entries()]
}

function ClubGroup({ items, logoUrl }: { items: ApprovalRequest[]; logoUrl?: string | null }) {
  const id = useId()
  const first = items[0]!
  const name = first.clubName ?? 'Kulüp'
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="flex items-center gap-4 border-b-2 border-ink pb-3">
        <ClubLogo name={name} logoUrl={logoUrl} />
        <Link to={`/clubs/${first.clubId}?sekme=yonetim`} className="text-3xl leading-none font-heavy tracking-[-0.02em] underline-offset-4 hover:underline">
          {name}
        </Link>
        <span className="tabular text-md text-ink-3">{items.length} istek</span>
      </h2>
      <ul>
        {items.map((r) => (
          <ApprovalItem key={r.id} request={r} mode="decide" />
        ))}
      </ul>
    </section>
  )
}
