import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocal, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useMe } from '@/features/me/useMe'
import { useMyRegistrations } from '@/features/events/api'
import { withTitle } from '@/features/people/titles'
import { CampusEvents } from '@/features/today/Panels'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, QueryBoundary, Skeleton } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Textarea } from '@/components/ui/Textarea'
import {
  CATEGORY_LABEL,
  isActiveMembership,
  renewalDue,
  roleLabel,
  roleRank,
  useCancelMembershipRequest,
  useClub,
  useClubAnnouncements,
  useClubBoard,
  useClubEvents,
  useLeaveClub,
  useMyMembershipRequests,
  useMyMemberships,
  useRenewMembership,
  useRequestMembership,
  type ClubDetails,
} from './api'
import { ClubLogo } from './ClubMark'
import { ClubManage } from './ClubManage'
import { ClubElections } from './ClubElections'
import { ClubSettings } from './ClubSettings'
import { ballotsToVote, isRunning, useClubElections } from './elections'
import { useResign } from './governance'
import { usePastPresidents } from './records'
import { can, isPendingApproval, useClubAccess, useMyApprovals, useRemoveAnnouncement, type ClubAccess } from './manage'
import { PersonLink } from '@/features/people/PersonLink'

const MESSAGE_MAX = 500

/** Kulüp sayfası: başlık bandı ve üyelik paneli; sekmelerde genel bakış, etkinlikler ve (üyelere) duyurular. */
export function ClubPage() {
  const { clubId = '' } = useParams()
  const club = useClub(clubId)
  usePageTitle(club.data?.name ?? 'Kulüp')

  return (
    <div className="mx-auto max-w-[80rem]">
      <QueryBoundary query={club} what="Kulüp" skeletonRows={4}>
        {(c) => <ClubBody club={c} />}
      </QueryBoundary>
    </div>
  )
}

function ClubBody({ club: c }: { club: ClubDetails }) {
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT')
  const memberships = useMyMemberships(student)
  const membership = (memberships.data ?? []).find((m) => m.clubId === c.id && isActiveMembership(m))
  // Görev ve yetkiler backend'den (F-85); kurallar burada kopyalanmaz.
  const { data: access } = useClubAccess(c.id, !!session)
  const insider = !!membership || !!access?.member || !!access?.advisor
  // Duyurular üyelere ve danışmana açık; 403 gelirse sekme hiç görünmez (F-34).
  const announcements = useClubAnnouncements(c.id, insider)
  const showAnnouncements = insider && !announcements.isError
  const manages = can(
    access,
    'MANAGE_MEMBERSHIP_REQUESTS',
    'REVIEW_MEMBERSHIP_REQUESTS',
    'PREPARE_ANNOUNCEMENT',
    'VIEW_DECISIONS',
    'VIEW_FINANCE',
    'APPROVE_AS_PRESIDENT',
    'ADVISE',
    'CREATE_EVENT',
    'PREPARE_EVENT',
    'MANAGE_EVENT_OPERATIONS',
    'PREPARE_SPONSORSHIP',
    'PREPARE_AUDIT_REPORT',
  )
  // Seçim sekmesi: seçimi açabilen görevliye her zaman, diğer üyelere ve danışmana seçim olduysa.
  const viewsMembers = can(access, 'VIEW_MEMBERS')
  const elections = useClubElections(c.id, viewsMembers)
  const running = elections.data?.find((e) => isRunning(e.status))
  const showElections = viewsMembers && ((can(access, 'PREPARE_ELECTION') && c.status !== 'CLOSED') || !!elections.data?.length)
  const toVote = running && access?.member ? ballotsToVote(running).length : 0
  // Kurul, kulüp bilgileri, logo, danışman ve kapatma talepleri; kapatılmış kulüpte hiçbiri açılmaz.
  const configures =
    c.status !== 'CLOSED' &&
    can(access, 'PROPOSE_POSITION_CHANGE', 'PREPARE_PROFILE_CHANGE', 'PREPARE_LOGO_CHANGE', 'PROPOSE_ADVISOR_CHANGE', 'REQUEST_CLUB_CLOSURE')

  return (
    <>
      <section className="grid gap-10 lg:grid-cols-12">
        <div className="min-w-0 lg:col-span-8">
          <ClubLogo name={c.name} logoUrl={c.logoUrl} size="lg" />
          <p className="mt-6 flex items-center gap-3 text-md">
            <span aria-hidden className="h-5 w-[4px] bg-kulup" />
            <Link
              to={c.profile?.category ? `/clubs?kategori=${c.profile.category}` : '/clubs'}
              className="font-semibold underline-offset-4 hover:underline"
            >
              {c.profile?.category ? CATEGORY_LABEL[c.profile.category] : 'Kulüpler'}
            </Link>
          </p>
          <RevealTitle text={c.name} className="mt-1 text-5xl sm:text-6xl" />
          <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
            {c.advisorName && (
              <span>
                Danışman <PersonLink id={c.academicAdvisorId}>{withTitle(c.advisorName, c.advisorTitle)}</PersonLink>
              </span>
            )}
            {c.status === 'CLOSED' && <Badge tone="neutral">Kapatıldı</Badge>}
            {c.status === 'AWAITING_ADVISOR' && <Badge tone="warning">Danışman bekleniyor</Badge>}
          </p>
          <Links club={c} />
        </div>
        <MembershipBoard
          club={c}
          student={student}
          membershipsLoading={memberships.isPending && student}
          acting={!!access?.actingPresident && access.position !== 'PRESIDENT' && access.position !== 'ROLE_CLUB_OFFICIAL'}
          presides={!!access?.actingPresident}
        />
      </section>

      {c.status === 'CLOSED' && (
        <Notice variant="line" tone="info" title="Bu kulüp kapatıldı" className="mt-10">
          {c.closedAt ? `${formatInstant(c.closedAt, 'date')} tarihinde kapatıldı. ` : ''}
          {c.closureReason ?? 'Kulübün bilgileri arşiv olarak görüntülenir; yeni üyelik ya da etkinlik yoktur.'}
        </Notice>
      )}
      {c.status === 'AWAITING_ADVISOR' && (
        <Notice variant="line" tone="warning" title="Kulüp yeni danışmanını bekliyor" className="mt-10">
          Danışman atanana kadar etkinlik ve bazı yönetim işlemleri bekletilir.
          {can(access, 'PROPOSE_ADVISOR_CHANGE') && (
            <>
              {' '}
              <Link to={{ search: '?sekme=ayarlar' }} className="font-semibold underline underline-offset-4">
                Danışman önerin
              </Link>
            </>
          )}
        </Notice>
      )}

      {running && access?.member && (running.status === 'CANDIDACY' || running.status === 'VOTING') && (
        <Notice
          variant="line"
          tone={toVote ? 'warning' : 'info'}
          title={running.status === 'CANDIDACY' ? 'Genel kurul seçimi: aday başvuruları açık' : 'Genel kurul seçimi: oylama sürüyor'}
          className="mt-10"
        >
          {running.status === 'CANDIDACY'
            ? 'Başkanlık, yönetim kurulu ya da denetim kurulu için aday olabilirsiniz. '
            : toVote
              ? `${toVote} kurulda oyunuzu henüz kullanmadınız. `
              : 'Oyunuzu kullandınız; sonuç oylama bitince açıklanır. '}
          <Link to={{ search: '?sekme=secim' }} className="font-semibold underline underline-offset-4">
            Seçime git
          </Link>
        </Notice>
      )}

      <div className="mt-14">
        <Tabs
          label="Kulüp bölümleri"
          tabs={[
            {
              value: 'genel',
              label: 'Genel bakış',
              content: <Overview club={c} member={viewsMembers} />,
            },
            {
              value: 'etkinlikler',
              label: 'Etkinlikler',
              content: <Events clubId={c.id} />,
            },
            ...(showAnnouncements
              ? [
                  {
                    value: 'duyurular',
                    label: 'Duyurular',
                    count: announcements.data?.length || undefined,
                    content: <Announcements clubId={c.id} query={announcements} access={access} />,
                  },
                ]
              : []),
            ...(showElections && access
              ? [{ value: 'secim', label: 'Seçim', count: toVote || undefined, content: <ClubElections club={c} access={access} /> }]
              : []),
            ...(manages && access ? [{ value: 'yonetim', label: 'Yönetim', content: <ClubManage clubId={c.id} access={access} /> }] : []),
            ...(configures && access ? [{ value: 'ayarlar', label: 'Ayarlar', content: <ClubSettings club={c} access={access} /> }] : []),
          ]}
        />
      </div>
    </>
  )
}

/** Web sitesi ve sosyal hesaplar: düz metin bağlantılar. */
function Links({ club: c }: { club: ClubDetails }) {
  const p = c.profile
  if (!p) return null
  const links = [
    p.websiteUrl && { href: p.websiteUrl, label: 'Web sitesi' },
    p.instagramUrl && { href: p.instagramUrl, label: 'Instagram' },
    p.xUrl && { href: p.xUrl, label: 'X' },
    p.linkedinUrl && { href: p.linkedinUrl, label: 'LinkedIn' },
    p.contactEmail && {
      href: `mailto:${p.contactEmail}`,
      label: p.contactEmail,
    },
  ].filter(Boolean) as { href: string; label: string }[]
  if (links.length === 0) return null
  return (
    <ul className="mt-4 flex flex-wrap gap-x-5 gap-y-1 text-md">
      {links.map((l) => (
        <li key={l.href}>
          <a
            href={l.href}
            target={l.href.startsWith('mailto:') ? undefined : '_blank'}
            rel="noreferrer"
            className="font-semibold text-kulup underline-offset-4 hover:underline"
          >
            {l.label}
          </a>
        </li>
      ))}
    </ul>
  )
}

/**
 * Üyelik paneli (tarife panosu): büyük üye sayısı; altında sizin durumunuz ve tek eylem.
 * Üyeyseniz görev, geçerlilik, yenileme ve ayrılma; başvurunuz varsa geri çekme; değilse üye olma.
 */
function MembershipBoard({
  club: c,
  student,
  membershipsLoading,
  acting,
  presides,
}: {
  club: ClubDetails
  student: boolean
  membershipsLoading: boolean
  /** Başkanlık boş ve kişi başkan yetkilerini vekâleten kullanıyor (F-85 `actingPresident`). */
  acting: boolean
  /** Başkan ya da vekili: istifası yalnız danışman onayına gider. */
  presides: boolean
}) {
  const { data: me } = useMe()
  const memberships = useMyMemberships(student)
  const requests = useMyMembershipRequests(student)
  const membership = (memberships.data ?? []).find((m) => m.clubId === c.id && isActiveMembership(m))
  const pending = (requests.data ?? []).find((r) => r.clubId === c.id && r.status === 'PENDING')
  const today = nowLocalIso().slice(0, 10)

  const request = useRequestMembership()
  const cancel = useCancelMembershipRequest()
  const leave = useLeaveClub()
  const renew = useRenewMembership()
  const resign = useResign(c.id)
  const [dialog, setDialog] = useState<'join' | 'leave' | 'cancel' | 'resign' | null>(null)
  const [message, setMessage] = useState('')
  const official = !!membership && roleRank(membership.clubRole) < roleRank('MEMBER')
  const myApprovals = useMyApprovals(official)
  const resigning = myApprovals.data?.some((r) => r.clubId === c.id && r.type === 'RESIGNATION' && isPendingApproval(r.status))

  const fail = (e: unknown) => toast.error(toApiError(e).message)

  return (
    <dl className="self-end border-t border-rule pt-6 lg:col-span-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-8">
      <div>
        <dt className="text-sm text-ink-3">Üye</dt>
        <dd className="tabular mt-1 text-6xl leading-none font-heavy">{c.memberCount != null ? formatNumber(c.memberCount) : '–'}</dd>
      </div>

      {student && (
        <div className="mt-6 border-t border-rule pt-4">
          <dt className="text-sm text-ink-3">Üyeliğiniz</dt>
          <dd className="mt-1">
            {membershipsLoading ? (
              <Skeleton rows={1} />
            ) : membership ? (
              <>
                <p className="text-lg font-heavy">{roleLabel(membership.clubRole)}</p>
                {acting && <p className="text-md text-ink-2">Başkanlık boş; başkan yetkilerini vekâleten kullanıyorsunuz.</p>}
                {membership.validUntil && (
                  <p className={renewalDue(membership, today) ? 'font-semibold text-warning' : 'text-ink-2'}>
                    {formatLocal(membership.validUntil, 'date')} tarihine kadar geçerli
                  </p>
                )}
                <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2">
                  {renewalDue(membership, today) && c.status !== 'CLOSED' && (
                    <Button
                      variant="primary"
                      loading={renew.isPending}
                      onClick={() =>
                        renew.mutate(c.id, {
                          onSuccess: () => toast.success('Üyeliğiniz yenilendi'),
                          onError: fail,
                        })
                      }
                    >
                      Üyeliği yenile
                    </Button>
                  )}
                  {official &&
                    c.status !== 'CLOSED' &&
                    (resigning ? (
                      <Link to="/clubs/mine" className="text-sm font-semibold text-warning underline-offset-4 hover:underline">
                        Ayrılma talebiniz onayda
                      </Link>
                    ) : (
                      <button
                        type="button"
                        onClick={() => setDialog('resign')}
                        className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                      >
                        Görevden ayrıl
                      </button>
                    ))}
                  <button
                    type="button"
                    onClick={() => setDialog('leave')}
                    className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
                  >
                    Kulüpten ayrıl
                  </button>
                </div>
              </>
            ) : pending ? (
              <>
                <p className="text-lg font-heavy text-warning">Başvurunuz yönetimde</p>
                <p className="text-ink-2">{formatInstant(pending.requestDate, 'datetime')} tarihinde başvurdunuz.</p>
                <button
                  type="button"
                  onClick={() => setDialog('cancel')}
                  className="mt-3 text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
                >
                  Başvuruyu geri çek
                </button>
              </>
            ) : c.status === 'CLOSED' ? (
              <p className="text-ink-2">Kapatılmış kulübe üye olunamaz.</p>
            ) : me?.studentStatus === 'ON_LEAVE' ? (
              <p className="text-ink-2">Kaydınız dondurulduğu için kulüplere üye olamazsınız.</p>
            ) : (
              <>
                <p className="text-ink-2">Üye değilsiniz. Başvurunuzu kulüp yönetimi değerlendirir.</p>
                <Button variant="primary" className="mt-4" onClick={() => setDialog('join')}>
                  Üye ol
                </Button>
              </>
            )}
          </dd>
        </div>
      )}

      <ConfirmDialog
        open={dialog === 'join'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={`${c.name} üyeliği`}
        description="Başvurunuz kulüp yönetimine iletilir; karar verildiğinde bildirim alırsınız."
        confirmLabel="Başvuruyu gönder"
        loading={request.isPending}
        onConfirm={() =>
          request.mutate(
            { clubId: c.id, message },
            {
              onSuccess: () => {
                toast.success('Üyelik başvurunuz gönderildi')
                setDialog(null)
                setMessage('')
              },
              onError: fail,
            },
          )
        }
      >
        <Field label="Yönetime notunuz" hint="İsteğe bağlı. Ör. hangi çalışmalarda yer almak istediğiniz.">
          <Textarea
            maxLength={MESSAGE_MAX}
            valueLength={message.length}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            rows={3}
          />
        </Field>
      </ConfirmDialog>

      <ConfirmDialog
        open={dialog === 'cancel'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Başvuruyu geri çek"
        description={`${c.name} üyelik başvurunuz geri çekilecek. İsterseniz daha sonra yeniden başvurabilirsiniz.`}
        confirmLabel="Başvuruyu geri çek"
        loading={cancel.isPending}
        onConfirm={() =>
          cancel.mutate(c.id, {
            onSuccess: () => {
              toast.success('Başvuru geri çekildi')
              setDialog(null)
            },
            onError: fail,
          })
        }
      />

      <ConfirmDialog
        open={dialog === 'resign'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={`${membership ? roleLabel(membership.clubRole) : 'Görev'} görevinden ayrıl`}
        description={`İstifanız ${presides ? 'danışman' : 'başkan ve danışman'} onayından sonra geçerli olur. Kulübe üye olarak devam edersiniz.`}
        confirmLabel="İstifayı gönder"
        loading={resign.isPending}
        onConfirm={() =>
          resign.mutate(message, {
            onSuccess: () => {
              toast.success(`İstifanız ${presides ? 'danışman' : 'başkan'} onayına gönderildi`)
              setDialog(null)
              setMessage('')
            },
            onError: fail,
          })
        }
      >
        <Field label="Notunuz" hint="İsteğe bağlı. Onaylayanlar görür.">
          <Textarea maxLength={MESSAGE_MAX} valueLength={message.length} value={message} onChange={(e) => setMessage(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>

      <ConfirmDialog
        open={dialog === 'leave'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Kulüpten ayrıl"
        description={
          membership && roleRank(membership.clubRole) < roleRank('MEMBER')
            ? `${c.name} kulübünden ayrılırsanız ${roleLabel(membership.clubRole).toLocaleLowerCase('tr-TR')} göreviniz de sona erer. Yeniden üye olmak için başvurmanız gerekir.`
            : `${c.name} kulübünden ayrılacaksınız. Yeniden üye olmak için başvurmanız gerekir.`
        }
        confirmLabel="Kulüpten ayrıl"
        destructive
        loading={leave.isPending}
        onConfirm={() =>
          leave.mutate(c.id, {
            onSuccess: () => {
              toast.success('Kulüpten ayrıldınız')
              setDialog(null)
            },
            onError: fail,
          })
        }
      />
    </dl>
  )
}

/** Genel bakış: solda tanıtım ve iletişim, sağda yönetim. */
function Overview({ club: c, member }: { club: ClubDetails; member: boolean }) {
  const board = useClubBoard(c.id)
  const presidents = usePastPresidents(c.id)
  const list = [...(board.data ?? [])].sort((a, b) => roleRank(a.role) - roleRank(b.role))
  return (
    <div className="grid gap-14 lg:grid-cols-12 lg:gap-10">
      <div className="min-w-0 lg:col-span-8">
        <Panel title="Kulüp hakkında">
          {c.about?.trim() ? (
            <p className="max-w-[64ch] text-lg whitespace-pre-line text-ink-2">{c.about}</p>
          ) : (
            <p className="text-ink-3">Kulüp henüz bir tanıtım yazısı eklemedi.</p>
          )}
        </Panel>
      </div>
      <aside className="lg:col-span-4">
        <Panel title="Yönetim">
          {board.isPending ? (
            <Skeleton rows={3} />
          ) : board.isError || list.length === 0 ? (
            <p className="text-ink-3">Yönetim bilgisi yok.</p>
          ) : (
            <ul>
              {list.map((m) => (
                <li key={m.studentId} className="border-b border-rule py-3 first:pt-0">
                  <PersonLink id={m.studentId} className="block font-semibold">
                    {[m.firstName, m.lastName].filter(Boolean).join(' ')}
                  </PersonLink>
                  <span className="text-sm text-ink-3">{roleLabel(m.role)}</span>
                </li>
              ))}
              {c.advisorName && (
                <li className="py-3">
                  <PersonLink id={c.academicAdvisorId} className="block font-semibold">
                    {withTitle(c.advisorName, c.advisorTitle)}
                  </PersonLink>
                  <span className="text-sm text-ink-3">Danışman</span>
                </li>
              )}
            </ul>
          )}
        </Panel>
        {!!presidents.data?.length && (
          <Panel title="Önceki başkanlar" className="mt-16">
            <ul>
              {presidents.data.map((p) => (
                <li key={`${p.studentId}-${p.termStartDate}`} className="border-b border-rule py-3 first:pt-0">
                  <span className="block font-semibold">{[p.firstName, p.lastName].filter(Boolean).join(' ') || 'Üye'}</span>
                  <span className="tabular text-sm text-ink-3">
                    {formatInstant(p.termStartDate, 'date')} – {formatInstant(p.termEndDate, 'date')}
                  </span>
                </li>
              ))}
            </ul>
          </Panel>
        )}
        {member && (
          <Link to={`/clubs/${c.id}/kayitlar?sekme=gorevler`} className="mt-6 inline-block text-md font-semibold underline-offset-4 hover:underline">
            Görev geçmişi
          </Link>
        )}
      </aside>
    </div>
  )
}

function Events({ clubId }: { clubId: string }) {
  const events = useClubEvents(clubId)
  const registrations = useMyRegistrations()
  const registered = new Set((registrations.data ?? []).map((r) => r.eventId))
  return (
    <QueryBoundary query={events} what="Etkinlikler">
      {(list) => {
        const upcoming = [...list].sort((a, b) => a.startsAt.localeCompare(b.startsAt))
        return upcoming.length === 0 ? (
          <EmptyState title="Yaklaşan etkinlik yok">Kulüp yeni bir etkinlik duyurduğunda burada görünür.</EmptyState>
        ) : (
          <div className="max-w-[56rem]">
            <CampusEvents list={upcoming} registered={registered} />
          </div>
        )
      }}
    </QueryBoundary>
  )
}

function Announcements({ clubId, query, access }: { clubId: string; query: ReturnType<typeof useClubAnnouncements>; access?: ClubAccess }) {
  const remove = useRemoveAnnouncement(clubId)
  const [target, setTarget] = useState<string | null>(null)
  const canRemove = can(access, 'APPROVE_AS_PRESIDENT', 'ADVISE')
  return (
    <QueryBoundary query={query} what="Duyurular">
      {(list) =>
        list.length === 0 ? (
          <EmptyState title="Duyuru yok">Kulüp yönetimi bir duyuru yayımladığında burada görünür.</EmptyState>
        ) : (
          <>
            <ul className="max-w-[56rem] border-t-2 border-ink">
              {list.map((a) => (
                <li key={a.id} className="grid gap-x-8 gap-y-1 border-b border-rule py-6 sm:grid-cols-[9rem_minmax(0,1fr)]">
                  <span className="tabular text-sm text-ink-3">{formatInstant(a.publishedAt ?? a.createdAt, 'date')}</span>
                  <div className="min-w-0">
                    <h3 className="text-xl font-heavy">{a.title}</h3>
                    <p className="mt-2 max-w-[64ch] whitespace-pre-line text-ink-2">{a.body}</p>
                    {canRemove && (
                      <button
                        type="button"
                        onClick={() => setTarget(a.id)}
                        className="mt-3 text-sm font-semibold text-ink-3 underline-offset-4 hover:text-danger hover:underline"
                      >
                        Yayından kaldır
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
            <ConfirmDialog
              open={!!target}
              onOpenChange={(o) => !o && setTarget(null)}
              title="Duyuru yayından kaldırılsın mı?"
              description="Duyuru üyelerin listesinden kalkar; karar kaydında iz kalır."
              confirmLabel="Yayından kaldır"
              destructive
              loading={remove.isPending}
              onConfirm={() =>
                target &&
                remove.mutate(target, {
                  onSuccess: () => {
                    toast.success('Duyuru yayından kaldırıldı')
                    setTarget(null)
                  },
                  onError: (e) => toast.error(toApiError(e).message),
                })
              }
            />
          </>
        )
      }
    </QueryBoundary>
  )
}
