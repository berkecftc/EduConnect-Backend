import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatLocal, formatLocalRange, localDiffMs, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { isActiveMembership, useMyMemberships } from '@/features/clubs/api'
import { can, useClubAccess } from '@/features/clubs/manage'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Meter } from '@/components/ui/Meter'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { QueryBoundary, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import {
  ADMISSION_LABEL,
  AUDIENCE_LABEL,
  myEventState,
  useAvailability,
  useCancelRegistration,
  useEvent,
  useJoinEvent,
  useMyParticipationRequests,
  useMyRegistrations,
  useWithdrawParticipation,
  type CampusEvent,
  type EventAvailability,
} from './api'
import { useMyStaffEvents } from './staff'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: 'UTC' })
const MESSAGE_MAX = 500

/** Etkinlik sayfası (F-61…F-63): bildirimlerin `/events/{id}` bağlantısı buraya düşer. */
export function EventPage() {
  const { eventId = '' } = useParams()
  const event = useEvent(eventId)
  usePageTitle(event.data?.title ?? 'Etkinlik')
  return (
    <div className="mx-auto max-w-[80rem]">
      <QueryBoundary query={event} what="Etkinlik" skeletonRows={4}>
        {(e) => <EventBody event={e} />}
      </QueryBoundary>
    </div>
  )
}

function EventBody({ event: e }: { event: CampusEvent }) {
  const availability = useAvailability(e.id, e.status === 'ACTIVE')
  const access = useClubAccess(e.clubId ?? '', !!e.clubId)
  const manages = can(access.data, 'MANAGE_EVENT_OPERATIONS', 'CREATE_EVENT', 'PREPARE_EVENT', 'APPROVE_AS_PRESIDENT', 'ADVISE')
  // Onaylı görevli (yönetici değilse) kapı ekranına buradan gider.
  const duties = useMyStaffEvents(!!e.clubId && !!access.data?.member && !manages)
  const onDuty = !manages && !!duties.data?.some((x) => x.id === e.id)
  const a = availability.data
  const poster = e.imageUrl && /^https?:\/\//.test(e.imageUrl) ? e.imageUrl : null

  return (
    <>
      <section className="grid gap-10 lg:grid-cols-12">
        <div className="min-w-0 lg:col-span-8">
          <p className="flex items-center gap-3 text-md">
            <span aria-hidden className="h-5 w-[4px] bg-etkinlik" />
            {e.clubId ? (
              <Link to={`/clubs/${e.clubId}`} className="font-semibold underline-offset-4 hover:underline">
                {e.clubName ?? 'Kulüp etkinliği'}
              </Link>
            ) : (
              <span className="font-semibold">
                {e.organizerName ?? 'Kampüs'} <span className="font-normal text-ink-3">Kampüs etkinliği</span>
              </span>
            )}
          </p>
          <RevealTitle text={e.title} className="mt-1 text-5xl sm:text-6xl" />
          <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
            <span className="tabular">{formatLocalRange(e.startsAt, e.endsAt)}</span>
            {e.location && <span>{e.location}</span>}
            {e.status === 'CANCELLED' && <Badge tone="danger">İptal edildi</Badge>}
            {e.status === 'COMPLETED' && <Badge tone="neutral">Tamamlandı</Badge>}
            {(e.status === 'PENDING' || e.status === 'PENDING_PRESIDENT') && <Badge tone="warning">Onay bekliyor</Badge>}
            {manages && (
              <Link to={`/events/${e.id}/yonetim`} className="text-md font-semibold text-ink underline-offset-4 hover:underline">
                Etkinliği yönet
              </Link>
            )}
          </p>
          {e.speakers && <p className="mt-2 text-lg">Konuşmacılar: {e.speakers}</p>}
        </div>
        <Board event={e} availability={a} loading={availability.isPending && e.status === 'ACTIVE'} />
      </section>

      {onDuty && (
        <Notice variant="line" tone="info" title="Bu etkinlikte görevlisiniz" className="mt-10">
          Girişte biletleri kapı ekranından okutun.{' '}
          <Link to={`/events/${e.id}/kapi`} className="font-semibold underline underline-offset-4">
            Kapı ekranı
          </Link>
        </Notice>
      )}
      {e.status === 'CANCELLED' && (
        <Notice variant="line" tone="danger" title="Bu etkinlik iptal edildi" className="mt-10">
          {e.cancellationReason ?? 'Kayıtlı katılımcılara e-posta ile bilgi verildi.'}
        </Notice>
      )}

      <div className="mt-16 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <div className="flex min-w-0 flex-col gap-10 lg:col-span-8">
          <Panel title="Etkinlik hakkında">
            {e.description?.trim() ? (
              <p className="max-w-[64ch] text-lg whitespace-pre-line text-ink-2">{e.description}</p>
            ) : (
              <p className="text-ink-3">Düzenleyen henüz bir açıklama eklemedi.</p>
            )}
          </Panel>
          {poster && <img src={poster} alt={`${e.title} afişi`} loading="lazy" className="max-h-[36rem] w-full border border-rule object-contain" />}
        </div>
        <aside className="lg:col-span-4">
          <Panel title="Katılım koşulları">
            <dl>
              <Rule label="Kimler katılabilir">{AUDIENCE_LABEL[a?.audience ?? e.audience ?? 'ALL_STUDENTS']}</Rule>
              <Rule label="Kabul">{ADMISSION_LABEL[a?.admission ?? e.admission ?? 'AUTO_CONFIRM']}</Rule>
              <Rule label="Kontenjan">
                {(a?.capacity ?? e.capacity) ? `${formatNumber(a?.capacity ?? e.capacity ?? 0)} kişi; dolunca bekleme listesi açılır.` : 'Sınırsız.'}
              </Rule>
              <Rule label="Kayıt dönemi">{registrationWindow(a ?? e)}</Rule>
              <Rule label="Kayıt iptali">
                {(a?.cancelUntil ?? e.cancelUntil) ? `${formatLocal(a?.cancelUntil ?? e.cancelUntil, 'datetime')} tarihine kadar.` : 'Etkinlik başlayana kadar.'}
              </Rule>
            </dl>
          </Panel>
        </aside>
      </div>
    </>
  )
}

function registrationWindow(x: { registrationOpensAt?: string | null; registrationClosesAt?: string | null }): string {
  const opens = x.registrationOpensAt ? formatLocal(x.registrationOpensAt, 'datetime') : null
  const closes = x.registrationClosesAt ? formatLocal(x.registrationClosesAt, 'datetime') : null
  if (opens && closes) return `${opens} – ${closes}`
  if (closes) return `${closes} tarihine kadar.`
  if (opens) return `${opens} tarihinde açılır.`
  return 'Etkinlik başlayana kadar açık.'
}

/**
 * Tarife panosu: dev gün rakamı ve saat aralığı; altında doluluk (kayıtlı / kontenjan, bekleyen sayısı)
 * ve sizin durumunuzla tek eylem.
 */
function Board({ event: e, availability: a, loading }: { event: CampusEvent; availability?: EventAvailability; loading: boolean }) {
  const day = new Date(`${e.startsAt.slice(0, 10)}T12:00:00Z`)
  const capacity = a?.capacity ?? e.capacity
  return (
    <dl className="self-end border-t border-rule pt-6 lg:col-span-4 lg:border-t-0 lg:border-l lg:pt-0 lg:pl-8">
      <div>
        <dt className="text-sm text-ink-3">Tarih</dt>
        <dd className="mt-1 flex items-end gap-4">
          <span className="tabular text-6xl leading-none font-heavy">{DAY.format(day)}</span>
          <span className="pb-1 text-lg leading-tight">
            {formatLocal(e.startsAt, 'long').replace(/^\d+\s/, '')}
            <span className="tabular block text-ink-2">
              {formatLocal(e.startsAt, 'time')}
              {e.endsAt ? `–${formatLocal(e.endsAt, 'time')}` : ''}
            </span>
          </span>
        </dd>
      </div>
      {e.status === 'ACTIVE' && (
        <div className="mt-6 border-t border-rule pt-4">
          <dt className="text-sm text-ink-3">Doluluk</dt>
          <dd className="mt-1">
            {loading || !a ? (
              <Skeleton rows={1} />
            ) : (
              <>
                <p className="tabular text-2xl font-heavy">
                  {formatNumber(a.registered)}
                  <span className="font-semibold text-ink-3">{capacity ? ` / ${formatNumber(capacity)}` : ' kayıtlı'}</span>
                </p>
                {capacity ? (
                  <div className="mt-2 max-w-[16rem]">
                    <Meter
                      value={a.registered}
                      max={capacity}
                      color={a.remaining === 0 ? 'var(--ec-danger)' : 'var(--ec-etkinlik)'}
                      label={`Kontenjan ${formatNumber(capacity)}, kayıtlı ${formatNumber(a.registered)}`}
                    />
                  </div>
                ) : null}
                <p className={cn('mt-2 text-sm', a.remaining === 0 ? 'font-semibold text-danger' : 'text-ink-3')}>
                  {capacity ? (a.remaining === 0 ? 'Dolu' : `${formatNumber(a.remaining ?? 0)} yer kaldı`) : 'Sınırsız kontenjan'}
                  {a.waitlisted > 0 ? `, ${formatNumber(a.waitlisted)} kişi bekleme listesinde` : ''}
                </p>
              </>
            )}
          </dd>
        </div>
      )}
      <Participation event={e} availability={a} />
    </dl>
  )
}

/** Sizin durumunuz ve tek eylem: katıl, isteği geri çek, kaydı iptal et; ya da neden katılamadığınız. */
function Participation({ event: e, availability: a }: { event: CampusEvent; availability?: EventAvailability }) {
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')
  const campus = !e.clubId
  const canJoinAtAll = student || campus
  const registrations = useMyRegistrations(canJoinAtAll)
  const requests = useMyParticipationRequests(canJoinAtAll)
  const memberships = useMyMemberships(student && !!e.clubId)
  const join = useJoinEvent()
  const withdraw = useWithdrawParticipation()
  const cancel = useCancelRegistration()
  const [dialog, setDialog] = useState<'join' | 'withdraw' | 'cancel' | null>(null)
  const [message, setMessage] = useState('')
  const now = nowLocalIso()

  if (!canJoinAtAll) return null
  const fail = (err: unknown) => toast.error(toApiError(err).message)
  const state = myEventState(e.id, registrations.data, requests.data)
  const ended = localDiffMs(now, e.endsAt ?? e.startsAt) < 0
  const member = (memberships.data ?? []).some((m) => m.clubId === e.clubId && isActiveMembership(m))
  const cancelOpen = !a?.cancelUntil || localDiffMs(now, a.cancelUntil) >= 0
  const full = a?.capacity != null && a.remaining === 0
  const approval = (a?.admission ?? e.admission) === 'APPROVAL_REQUIRED'

  let body: ReactNode
  if (registrations.isPending || requests.isPending) body = <Skeleton rows={1} />
  else if (state.kind === 'registered')
    body = (
      <>
        <p className="text-lg font-heavy text-success">Kayıtlısınız</p>
        <p className="text-ink-2">{state.registration.attended ? 'Girişiniz yapıldı.' : 'Biletiniz Biletlerim sayfasında.'}</p>
        <div className="mt-4 flex flex-wrap items-center gap-x-5 gap-y-2">
          <Button asChild variant="primary">
            <Link to="/me/tickets">Biletimi göster</Link>
          </Button>
          {!ended && e.status === 'ACTIVE' && cancelOpen && (
            <button type="button" onClick={() => setDialog('cancel')} className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline">
              Kaydı iptal et
            </button>
          )}
        </div>
        {!ended && !cancelOpen && <p className="mt-2 text-sm text-ink-3">İptal süresi doldu.</p>}
      </>
    )
  else if (state.kind === 'waitlisted' || state.kind === 'pending')
    body = (
      <>
        <p className="text-lg font-heavy text-warning">{state.kind === 'waitlisted' ? 'Bekleme listesindesiniz' : 'Onay bekleniyor'}</p>
        <p className="text-ink-2">
          {state.kind === 'waitlisted' ? 'Yer açılınca kaydınız kendiliğinden yapılır.' : 'Düzenleyen onayladığında biletiniz gelir.'}
        </p>
        <button type="button" onClick={() => setDialog('withdraw')} className="mt-3 text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline">
          İsteği geri çek
        </button>
      </>
    )
  else if (e.status === 'CANCELLED') body = <p className="text-ink-2">Etkinlik iptal edildiği için katılım alınmıyor.</p>
  else if (ended || e.status === 'COMPLETED') body = <p className="text-ink-2">Etkinlik sona erdi.</p>
  else if (e.status !== 'ACTIVE') body = <p className="text-ink-2">Etkinlik henüz yayımlanmadı.</p>
  else if (a && !a.registrationOpen)
    body = (
      <p className="text-ink-2">
        {a.registrationOpensAt && localDiffMs(now, a.registrationOpensAt) > 0
          ? `Kayıt ${formatLocal(a.registrationOpensAt, 'datetime')} tarihinde açılır.`
          : 'Kayıt kapandı.'}
      </p>
    )
  else if ((a?.audience ?? e.audience) === 'MEMBERS_ONLY' && !member)
    body = (
      <>
        <p className="text-ink-2">Bu etkinliğe yalnız kulüp üyeleri katılabilir.</p>
        {e.clubId && (
          <Link to={`/clubs/${e.clubId}`} className="mt-2 inline-block text-sm font-semibold underline-offset-4 hover:underline">
            Kulübe göz at
          </Link>
        )}
      </>
    )
  else
    body = (
      <>
        {state.kind === 'rejected' && (
          <p className="mb-2 text-md text-ink-2">
            Önceki isteğiniz reddedildi{state.request.rejectionReason ? `: ${state.request.rejectionReason}` : '.'}
          </p>
        )}
        <Button
          variant="primary"
          size="lg"
          loading={join.isPending}
          onClick={() =>
            approval
              ? setDialog('join')
              : join.mutate({ eventId: e.id }, { onSuccess: (r) => toast.success(r.message), onError: fail })
          }
        >
          {full ? 'Bekleme listesine katıl' : approval ? 'Katılım isteği gönder' : 'Katıl'}
        </Button>
        {full && <p className="mt-2 text-sm text-ink-3">Yer açılınca sırayla kayıt yapılır.</p>}
      </>
    )

  return (
    <div className="mt-6 border-t border-rule pt-4">
      <dt className="text-sm text-ink-3">Katılımınız</dt>
      <dd className="mt-1">{body}</dd>

      <ConfirmDialog
        open={dialog === 'join'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Katılım isteği"
        description="Düzenleyen isteğinizi onayladığında biletiniz e-posta adresinize ve Biletlerim sayfasına gelir."
        confirmLabel="İsteği gönder"
        loading={join.isPending}
        onConfirm={() =>
          join.mutate(
            { eventId: e.id, message },
            {
              onSuccess: (r) => {
                toast.success(r.message)
                setDialog(null)
                setMessage('')
              },
              onError: fail,
            },
          )
        }
      >
        <Field label="Düzenleyene notunuz" hint="İsteğe bağlı.">
          <Textarea maxLength={MESSAGE_MAX} valueLength={message.length} value={message} onChange={(ev) => setMessage(ev.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
      <ConfirmDialog
        open={dialog === 'withdraw'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="İsteği geri çek"
        description={`${e.title} için katılım isteğiniz geri çekilecek.`}
        confirmLabel="İsteği geri çek"
        loading={withdraw.isPending}
        onConfirm={() =>
          withdraw.mutate(e.id, {
            onSuccess: () => {
              toast.success('İstek geri çekildi')
              setDialog(null)
            },
            onError: fail,
          })
        }
      />
      <ConfirmDialog
        open={dialog === 'cancel'}
        onOpenChange={(o) => !o && setDialog(null)}
        title="Kaydı iptal et"
        description={`${e.title} kaydınız ve biletiniz iptal edilecek. Kontenjan doluysa yeriniz bekleme listesindeki ilk kişiye geçer.`}
        confirmLabel="Kaydı iptal et"
        destructive
        loading={cancel.isPending}
        onConfirm={() =>
          cancel.mutate(e.id, {
            onSuccess: () => {
              toast.success('Kaydınız iptal edildi')
              setDialog(null)
            },
            onError: fail,
          })
        }
      />
    </div>
  )
}

function Rule({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="border-b border-rule py-3.5 first:pt-0">
      <dt className="text-sm font-semibold text-ink">{label}</dt>
      <dd className="mt-1 text-md text-ink-2">{children}</dd>
    </div>
  )
}
