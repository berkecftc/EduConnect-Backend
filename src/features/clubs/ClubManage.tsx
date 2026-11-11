import { useState } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { formatInstant, formatLocalRange, localDiffMs, nowLocalIso } from '@/lib/time'
import { EVENT_STATUS, useClubManagedEvents } from '@/features/events/manage'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Panel } from '@/components/ui/Panel'
import { QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { WorkStrip, type WorkItem } from '@/components/ui/WorkStrip'
import { ApprovalItem } from './ApprovalItem'
import {
  can,
  isMyTurn,
  isPendingApproval,
  useClubApprovals,
  useDecideMembership,
  usePendingMembershipRequests,
  useSubmitAnnouncement,
  type ClubAccess,
  type MembershipRecommendation,
  type PendingMembershipRequest,
} from './manage'
import { PersonLink } from '@/features/people/PersonLink'

const TITLE_MAX = 200
const BODY_MAX = 5000
const NOTE_MAX = 1000

/**
 * Yönetim sekmesi: yalnız yetkinin izin verdiği bölümler görünür (F-85).
 * Üstte bekleyen işler şeridi; bölümler önce iş (başvurular, kararlar), sonra bilgi (etkinlikler) sırasıyla.
 */
export function ClubManage({ clubId, access }: { clubId: string; access: ClubAccess }) {
  const decides = can(access, 'MANAGE_MEMBERSHIP_REQUESTS')
  const reviews = can(access, 'REVIEW_MEMBERSHIP_REQUESTS')
  const showRequests = decides || reviews
  const showDecisions = can(access, 'VIEW_DECISIONS')
  const announces = can(access, 'PREPARE_ANNOUNCEMENT')
  const creates = can(access, 'CREATE_EVENT', 'PREPARE_EVENT')
  const operatesEvents = creates || can(access, 'MANAGE_EVENT_OPERATIONS', 'APPROVE_AS_PRESIDENT', 'ADVISE')
  const records = can(access, 'VIEW_DECISIONS', 'VIEW_FINANCE', 'PREPARE_SPONSORSHIP', 'PREPARE_AUDIT_REPORT')

  return (
    <div className="flex flex-col gap-16">
      <ClubWorkStrip clubId={clubId} access={access} requests={showRequests} decisions={showDecisions} events={operatesEvents} />
      <div className="grid gap-20 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-20 lg:col-span-8">
          {showRequests && <MembershipRequests clubId={clubId} decides={decides} />}
          {showDecisions && <Decisions clubId={clubId} access={access} />}
          {operatesEvents && <Events clubId={clubId} creates={creates} />}
        </div>
        {(announces || records) && (
          <aside className="flex flex-col gap-20 lg:col-span-4">
            {announces && <AnnouncementForm clubId={clubId} publishesDirectly={access.actingPresident} />}
            {records && <RecordLinks clubId={clubId} access={access} />}
          </aside>
        )}
      </div>
    </div>
  )
}

/**
 * Bekleyen işler: sekmenin ilk satırı "ne yapmam gerekiyor?" sorusunu cevaplar. Dev rakam, kısa etiket;
 * tıklayınca ilgili bölüme kayar. Sizden karar bekleyen rakamlar uyarı renginde.
 */
function ClubWorkStrip({
  clubId,
  access,
  requests,
  decisions,
  events,
}: {
  clubId: string
  access: ClubAccess
  requests: boolean
  decisions: boolean
  events: boolean
}) {
  const pending = usePendingMembershipRequests(clubId, requests)
  const approvals = useClubApprovals(clubId, decisions)
  const managed = useClubManagedEvents(clubId, events)
  const now = nowLocalIso()

  const items: WorkItem[] = []
  if (requests) {
    const n = pending.data?.length
    items.push({ key: 'basvurular', anchor: 'yonetim-basvurular', value: n, label: 'başvuru bekliyor', urgent: !!n })
  }
  if (decisions) {
    const open = (approvals.data ?? []).filter((r) => isPendingApproval(r.status))
    const mine = open.filter((r) => isMyTurn(r, access)).length
    items.push(
      mine > 0
        ? {
            key: 'kararlar',
            anchor: 'yonetim-kararlar',
            value: mine,
            label: 'karar sizi bekliyor',
            note: open.length > mine ? `${open.length - mine} karar başkasında` : undefined,
            urgent: true,
          }
        : {
            key: 'kararlar',
            anchor: 'yonetim-kararlar',
            value: approvals.data ? open.length : undefined,
            label: 'karar sürüyor',
            urgent: false,
          },
    )
  }
  if (events) {
    const list = (managed.data ?? []).filter((e) => localDiffMs(now, e.endsAt ?? e.startsAt) >= 0)
    const upcoming = list.filter((e) => e.status === 'ACTIVE').length
    const waiting = list.filter((e) => e.status === 'PENDING_PRESIDENT' || e.status === 'PENDING' || e.status === 'REJECTED').length
    items.push({
      key: 'etkinlikler',
      anchor: 'yonetim-etkinlikler',
      value: managed.data ? upcoming : undefined,
      label: 'yaklaşan etkinlik',
      note: waiting ? `${waiting} etkinlik onayda ya da düzeltmede` : undefined,
      urgent: false,
    })
  }
  return <WorkStrip items={items} />
}

function MembershipRequests({ clubId, decides }: { clubId: string; decides: boolean }) {
  const requests = usePendingMembershipRequests(clubId, true)
  return (
    <Panel
      anchor="yonetim-basvurular"
      title="Üyelik başvuruları"
      action={requests.data?.length ? <span className="tabular text-md text-ink-3">{requests.data.length} bekliyor</span> : undefined}
    >
      <QueryBoundary query={requests} what="Başvurular" skeletonRows={2}>
        {(list) =>
          list.length === 0 ? (
            <p className="text-ink-3">Bekleyen üyelik başvurusu yok.</p>
          ) : (
            <ul className="border-t border-rule">
              {[...list]
                .sort((a, b) => a.requestDate.localeCompare(b.requestDate))
                .map((r) => (
                  <RequestRow key={r.id} clubId={clubId} request={r} decides={decides} />
                ))}
            </ul>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}

/**
 * Bekleyen başvuru: öğrenci, başvuru zamanı ve notu, varsa üyelik sorumlusunun önerisi.
 * Karar yetkisi olan kabul eder ya da reddeder; üyelik sorumlusu yalnız öneri verir.
 */
function RequestRow({ clubId, request: r, decides }: { clubId: string; request: PendingMembershipRequest; decides: boolean }) {
  const decide = useDecideMembership(clubId)
  const [dialog, setDialog] = useState<'reject' | MembershipRecommendation | null>(null)
  const [text, setText] = useState('')
  const name = r.studentName ?? 'Öğrenci'

  const done = (message: string) => ({
    onSuccess: () => {
      toast.success(message)
      setDialog(null)
      setText('')
    },
    onError: (e: unknown) => toast.error(toApiError(e).message),
  })

  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 sm:grid-cols-[minmax(0,1fr)_auto]">
      <div className="min-w-0">
        <p className="text-lg font-semibold">
          <PersonLink id={r.studentId}>{name}</PersonLink>
        </p>
        <p className="text-sm text-ink-3">
          {r.studentEmail && <span className="mr-4">{r.studentEmail}</span>}
          {formatInstant(r.requestDate, 'datetime')} tarihinde başvurdu
        </p>
        {r.message && <p className="mt-2 max-w-[60ch] text-md whitespace-pre-line text-ink-2">“{r.message}”</p>}
        {r.recommendation && (
          <p className="mt-2 text-md">
            <Badge tone={r.recommendation === 'APPROVE' ? 'success' : 'danger'}>
              {r.recommendation === 'APPROVE' ? 'Üyelik sorumlusu kabul öneriyor' : 'Üyelik sorumlusu ret öneriyor'}
            </Badge>
            {r.recommendationNote && <span className="ml-3 text-ink-2">{r.recommendationNote}</span>}
          </p>
        )}
      </div>
      <div className="flex items-center gap-4 sm:flex-col sm:items-end">
        {decides ? (
          <>
            <Button
              size="sm"
              variant="primary"
              loading={decide.isPending && dialog === null}
              onClick={() => decide.mutate({ kind: 'approve', requestId: r.id }, done(`${name} üyeliğe kabul edildi`))}
            >
              Kabul et
            </Button>
            <button
              type="button"
              onClick={() => setDialog('reject')}
              className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline"
            >
              Reddet
            </button>
          </>
        ) : (
          <>
            <Button size="sm" onClick={() => setDialog('APPROVE')}>
              Kabul öner
            </Button>
            <button
              type="button"
              onClick={() => setDialog('REJECT')}
              className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
            >
              Ret öner
            </button>
          </>
        )}
      </div>

      <ConfirmDialog
        open={dialog === 'reject'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={`${name} başvurusu reddedilsin mi?`}
        description="Gerekçe yazarsanız öğrenciye iletilir."
        confirmLabel="Reddet"
        destructive
        loading={decide.isPending}
        onConfirm={() => decide.mutate({ kind: 'reject', requestId: r.id, reason: text }, done('Başvuru reddedildi'))}
      >
        <Field label="Gerekçe" hint="İsteğe bağlı.">
          <Textarea maxLength={NOTE_MAX} valueLength={text.length} value={text} onChange={(e) => setText(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
      <ConfirmDialog
        open={dialog === 'APPROVE' || dialog === 'REJECT'}
        onOpenChange={(o) => !o && setDialog(null)}
        title={dialog === 'APPROVE' ? 'Kabul öner' : 'Ret öner'}
        description="Öneriniz kararı verecek yöneticiye başvuruyla birlikte gösterilir."
        confirmLabel="Öneriyi gönder"
        loading={decide.isPending}
        onConfirm={() =>
          (dialog === 'APPROVE' || dialog === 'REJECT') &&
          decide.mutate({ kind: 'recommend', requestId: r.id, recommendation: dialog, note: text }, done('Öneriniz kaydedildi'))
        }
      >
        <Field label="Notunuz" hint="İsteğe bağlı.">
          <Textarea maxLength={NOTE_MAX} valueLength={text.length} value={text} onChange={(e) => setText(e.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}

/**
 * Kulüp kararları: bekleyenler önce, sonra son kararlar. Sıra size geldiyse karar verebilir,
 * kendi hazırladığınız bekleyen isteği geri çekebilirsiniz.
 */
function Decisions({ clubId, access }: { clubId: string; access: ClubAccess }) {
  const session = useSession()
  const approvals = useClubApprovals(clubId, true)
  return (
    <Panel anchor="yonetim-kararlar" title="Kulüp kararları">
      <QueryBoundary query={approvals} what="Kararlar" skeletonRows={2}>
        {(list) => {
          if (list.length === 0) return <p className="text-ink-3">Henüz bir karar kaydı yok.</p>
          const pending = list.filter((r) => isPendingApproval(r.status))
          const past = list.filter((r) => !isPendingApproval(r.status)).slice(0, 10)
          return (
            <ul className="border-t border-rule">
              {[...pending, ...past].map((r) => {
                const myTurn = isMyTurn(r, access)
                const mine = isPendingApproval(r.status) && !!session && r.preparedBy === session.userId
                return <ApprovalItem key={r.id} request={r} mode={myTurn ? 'decide' : mine ? 'withdraw' : undefined} />
              })}
            </ul>
          )
        }}
      </QueryBoundary>
    </Panel>
  )
}

/** Duyuru hazırla: başkan (ya da vekili) hazırlarsa hemen yayımlanır; diğerlerinde başkan onayına gider. */
function AnnouncementForm({ clubId, publishesDirectly }: { clubId: string; publishesDirectly: boolean }) {
  const submit = useSubmitAnnouncement(clubId)
  const [open, setOpen] = useState(false)
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [errors, setErrors] = useState<{ title?: string; body?: string }>({})

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const next: typeof errors = {}
    if (!title.trim()) next.title = 'Başlık yazın.'
    if (!body.trim()) next.body = 'Duyuru metnini yazın.'
    setErrors(next)
    if (Object.keys(next).length) return
    submit.mutate(
      { title, body },
      {
        onSuccess: (r) => {
          toast.success(r.status === 'APPROVED' ? 'Duyuru yayımlandı' : 'Duyuru başkan onayına gönderildi')
          setTitle('')
          setBody('')
          setOpen(false)
        },
        onError: (err) => toast.error(toApiError(err).message),
      },
    )
  }

  return (
    <Panel title="Duyuru">
      <p className="text-md text-ink-2">
        {publishesDirectly
          ? 'Başkan olarak yazdığınız duyuru hemen üyelere yayımlanır.'
          : 'Yazdığınız duyuru başkan onayladıktan sonra üyelere yayımlanır.'}
      </p>
      {!open ? (
        <Button className="mt-5" onClick={() => setOpen(true)}>
          Duyuru yaz
        </Button>
      ) : (
        <form onSubmit={onSubmit} noValidate className="mt-6 flex flex-col gap-5">
          <Field label="Başlık" error={errors.title} required>
            <Input autoFocus maxLength={TITLE_MAX} value={title} onChange={(e) => setTitle(e.target.value)} />
          </Field>
          <Field label="Metin" error={errors.body} required>
            <Textarea maxLength={BODY_MAX} valueLength={body.length} value={body} onChange={(e) => setBody(e.target.value)} rows={6} />
          </Field>
          <div className="flex items-center gap-5">
            <Button type="submit" variant="primary" loading={submit.isPending}>
              {publishesDirectly ? 'Yayımla' : 'Onaya gönder'}
            </Button>
            <button
              type="button"
              onClick={() => {
                setOpen(false)
                setErrors({})
              }}
              className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
            >
              Vazgeç
            </button>
          </div>
        </form>
      )}
    </Panel>
  )
}

/** Kulübün etkinlikleri (onay bekleyen, reddedilen ve yayındakiler); satır yönetim sayfasına gider. */
function Events({ clubId, creates }: { clubId: string; creates: boolean }) {
  const events = useClubManagedEvents(clubId, true)
  return (
    <Panel
      anchor="yonetim-etkinlikler"
      title="Etkinlikler"
      action={
        creates ? (
          <Link to={`/clubs/${clubId}/events/new`} className="text-sm font-semibold underline-offset-4 hover:underline">
            Etkinlik oluştur
          </Link>
        ) : undefined
      }
    >
      <QueryBoundary query={events} what="Etkinlikler" skeletonRows={2}>
        {(list) => {
          // Önce sonuçlanmamışlar (onay bekleyen, reddedilen, yayındaki), sonra geçmiş; her grupta tarihe göre.
          const open = list
            .filter((e) => e.status !== 'COMPLETED' && e.status !== 'CANCELLED')
            .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
          const done = list
            .filter((e) => e.status === 'COMPLETED' || e.status === 'CANCELLED')
            .sort((a, b) => b.startsAt.localeCompare(a.startsAt))
            .slice(0, 5)
          if (list.length === 0) return <p className="text-ink-3">Henüz etkinlik yok.</p>
          return (
            <ul className="border-t border-rule">
              {[...open, ...done].map((e) => (
                <li key={e.id} className="border-b border-rule">
                  <Link to={`/events/${e.id}/yonetim`} className="row-fill block px-1 py-4">
                    <span className="flex flex-wrap items-baseline gap-x-4 gap-y-1">
                      <span className="text-lg font-semibold">{e.title}</span>
                      <Badge tone={EVENT_STATUS[e.status].tone}>{EVENT_STATUS[e.status].label}</Badge>
                    </span>
                    <span className="tabular text-sm text-ink-2">{formatLocalRange(e.startsAt, e.endsAt)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )
        }}
      </QueryBoundary>
    </Panel>
  )
}

/** Kulüp kayıtlarına giriş: yetkinin açtığı bölümler, her biri kısa açıklamayla. */
function RecordLinks({ clubId, access }: { clubId: string; access: ClubAccess }) {
  const items = [
    can(access, 'VIEW_DECISIONS') && { tab: 'toplantilar', title: 'Toplantılar', note: 'Tutanaklar ve karar defteri' },
    can(access, 'VIEW_FINANCE', 'PREPARE_SPONSORSHIP') && { tab: 'mali', title: 'Mali işler', note: 'Bütçe, gelir ve gider, sponsorluklar' },
    can(access, 'VIEW_DECISIONS', 'PREPARE_AUDIT_REPORT') && { tab: 'raporlar', title: 'Raporlar', note: 'Faaliyet ve denetim raporları' },
    can(access, 'VIEW_DECISIONS') && { tab: 'gunluk', title: 'Karar günlüğü', note: 'Verilen her kararın izi' },
    { tab: 'gorevler', title: 'Görev geçmişi', note: 'Kim, hangi görevde, ne zamandan beri' },
  ].filter(Boolean) as { tab: string; title: string; note: string }[]
  return (
    <Panel title="Kulüp kayıtları">
      <ul className="border-t border-rule">
        {items.map((i) => (
          <li key={i.tab} className="border-b border-rule">
            <Link to={`/clubs/${clubId}/kayitlar?sekme=${i.tab}`} className="row-fill block px-1 py-3">
              <span className="block font-semibold">{i.title}</span>
              <span className="block text-sm text-ink-3">{i.note}</span>
            </Link>
          </li>
        ))}
      </ul>
    </Panel>
  )
}
