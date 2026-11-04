import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Dialog } from 'radix-ui'
import { X } from 'lucide-react'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { formatInstant, formatLocal, localDiffMs, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Panel } from '@/components/ui/Panel'
import { QrCode } from '@/components/ui/QrCode'
import { EmptyState, PageHeader, QueryBoundary, Skeleton } from '@/components/ui/States'
import { useMyParticipationRequests, useMyRegistrations, useWithdrawParticipation, type MyRegistration, type ParticipationRequest } from './api'
import { StaffDuties } from './StaffDuties'

const DAY = new Intl.DateTimeFormat('tr-TR', { day: 'numeric', timeZone: 'UTC' })
const MONTH = new Intl.DateTimeFormat('tr-TR', { month: 'short', weekday: 'short', timeZone: 'UTC' })

/** Biletlerim: yaklaşan etkinliklerin biletleri (QR), bekleyen istekler ve geçmiş katılımlar (F-62, F-78). */
export function TicketsPage() {
  usePageTitle('Biletlerim')
  const registrations = useMyRegistrations()
  const requests = useMyParticipationRequests()
  const now = nowLocalIso()

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Biletlerim"
        actions={
          <Button asChild>
            <Link to="/events">Etkinlikler</Link>
          </Button>
        }
      />
      <StaffDuties />
      <QueryBoundary query={registrations} what="Biletler" skeletonRows={3}>
        {(list) => {
          const live = list.filter((r) => r.registrationStatus === 'REGISTERED' && r.eventStatus !== 'CANCELLED' && r.eventStatus !== 'COMPLETED')
          // Etkinlik bitene kadar bilet yaklaşanlarda kalır (bitiş boşsa başlangıçtan 2 saat sonra, sunucunun varsayılanı).
          const upcoming = live
            .filter((r) => (r.endsAt ? localDiffMs(now, r.endsAt) > 0 : localDiffMs(now, r.eventDate) > -2 * 3_600_000))
            .sort((a, b) => a.eventDate.localeCompare(b.eventDate))
          const past = list.filter((r) => !upcoming.includes(r)).sort((a, b) => b.eventDate.localeCompare(a.eventDate))
          return (
            <>
              <p className="mt-4 text-lg text-ink-2">
                {upcoming.length > 0 ? `${upcoming.length} yaklaşan etkinlik için biletiniz var.` : 'Yaklaşan bir etkinlik için biletiniz yok.'}
              </p>
              <div className="mt-8">
                {upcoming.length === 0 ? (
                  <EmptyState
                    title="Bilet yok"
                    action={
                      <Button asChild variant="primary">
                        <Link to="/events">Etkinliklere göz at</Link>
                      </Button>
                    }
                  >
                    Bir etkinliğe kaydolduğunuzda biletiniz burada ve e-posta adresinizde olur. Girişte QR kodu okutmanız yeterli.
                  </EmptyState>
                ) : (
                  <ul className="border-t-2 border-ink">
                    {upcoming.map((r) => (
                      <Ticket key={r.eventId} registration={r} />
                    ))}
                  </ul>
                )}
              </div>
              <div className="mt-16 grid gap-14 lg:grid-cols-12 lg:gap-10">
                <div className="lg:col-span-7">
                  <Panel title="Geçmiş">
                    {past.length === 0 ? <p className="text-ink-3">Henüz geçmiş bir etkinliğiniz yok.</p> : <History list={past} />}
                  </Panel>
                </div>
                <aside className="lg:col-span-5">
                  <Panel title="Bekleyen istekler">
                    {requests.isPending ? <Skeleton rows={2} /> : <Requests list={requests.data ?? []} />}
                  </Panel>
                </aside>
              </div>
            </>
          )
        }}
      </QueryBoundary>
    </div>
  )
}

/**
 * Bilet: solda dev gün rakamı, ortada etkinlik ve saat, sağda kesik çizgiyle ayrılmış koçanda QR.
 * QR'a dokununca tam ekran, beyaz zeminde büyük hâli açılır (girişte okutmak için).
 */
function Ticket({ registration: r }: { registration: MyRegistration }) {
  const day = new Date(`${r.eventDate.slice(0, 10)}T12:00:00Z`)
  return (
    <li className="grid grid-cols-[4rem_minmax(0,1fr)] items-center gap-x-6 gap-y-4 border-b border-rule py-7 sm:grid-cols-[5rem_minmax(0,1fr)_9rem]">
      <span>
        <span className="tabular block text-5xl leading-none font-heavy">{DAY.format(day)}</span>
        <span className="mt-1 block text-sm text-ink-3">{MONTH.format(day)}</span>
      </span>
      <span className="min-w-0">
        <Link to={`/events/${r.eventId}`} className="block text-2xl leading-tight font-heavy underline-offset-4 hover:underline">
          {r.eventTitle}
        </Link>
        <span className="mt-1 flex flex-wrap gap-x-4 text-md text-ink-3">
          <span className="tabular font-semibold text-ink">{formatLocal(r.eventDate, 'time')}</span>
          {r.eventLocation && <span>{r.eventLocation}</span>}
          {r.attended && <Badge tone="success">Giriş yapıldı</Badge>}
          {!r.attended && r.checkInOpensAt && localDiffMs(nowLocalIso(), r.checkInOpensAt) > 0 && (
            <span>Giriş {formatLocal(r.checkInOpensAt, 'time')} itibarıyla açılır</span>
          )}
        </span>
      </span>
      <span className="col-span-2 flex items-center gap-4 border-t border-dashed border-rule-strong pt-4 sm:col-span-1 sm:block sm:border-t-0 sm:border-l sm:pt-0 sm:pl-6">
        {r.qrCode ? <TicketQr registration={r} /> : <span className="text-sm text-ink-3">QR hazırlanıyor</span>}
      </span>
    </li>
  )
}

function TicketQr({ registration: r }: { registration: MyRegistration }) {
  const code = r.qrCode!
  return (
    <Dialog.Root>
      <Dialog.Trigger className="group block text-left" aria-label={`${r.eventTitle} biletini büyüt`}>
        <QrCode value={code} label={`${r.eventTitle} bilet kodu`} className="size-24 border border-rule transition-transform duration-200 group-hover:scale-[1.03]" />
        <span className="mt-1.5 block text-xs font-semibold text-ink-3 group-hover:text-ink">Büyüt</span>
      </Dialog.Trigger>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-50 bg-scrim data-[state=open]:animate-fade-in" />
        <Dialog.Content className="fixed inset-x-4 top-1/2 z-50 mx-auto max-w-sm -translate-y-1/2 bg-[#fff] p-6 text-center text-[#0f1b24] shadow-xl outline-none">
          <Dialog.Title className="text-xl font-heavy">{r.eventTitle}</Dialog.Title>
          <Dialog.Description className="mt-1 text-md text-[#4a5560]">
            {formatLocal(r.eventDate, 'long')}, {formatLocal(r.eventDate, 'time')}
            {r.eventLocation ? `. ${r.eventLocation}` : ''}
          </Dialog.Description>
          <QrCode value={code} label={`${r.eventTitle} bilet kodu`} className="mx-auto mt-5 w-full max-w-[18rem]" />
          <p className="tabular mt-3 text-sm text-[#4a5560]">Bilet no {code.slice(-8).toLocaleUpperCase('tr-TR')}</p>
          <p className="mt-1 text-sm text-[#4a5560]">Girişte bu kodu görevliye okutun. Ekran parlaklığını artırmak okutmayı kolaylaştırır.</p>
          <Dialog.Close className="absolute top-3 right-3 grid size-10 place-items-center text-[#4a5560] hover:text-[#000]" aria-label="Kapat">
            <X className="size-5" aria-hidden />
          </Dialog.Close>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function History({ list }: { list: MyRegistration[] }) {
  return (
    <ul>
      {list.map((r) => {
        const status =
          r.eventStatus === 'CANCELLED'
            ? { label: 'Etkinlik iptal edildi', tone: 'neutral' as const }
            : r.registrationStatus === 'CANCELLED'
              ? { label: 'Kaydı iptal ettiniz', tone: 'neutral' as const }
              : r.attended
                ? { label: 'Katıldınız', tone: 'success' as const }
                : r.registrationStatus === 'NO_SHOW'
                  ? { label: 'Katılmadınız', tone: 'warning' as const }
                  : { label: 'Sona erdi', tone: 'neutral' as const }
        return (
          <li key={`${r.eventId}-${r.registrationTime}`} className="flex flex-wrap items-baseline gap-x-6 gap-y-1 border-b border-rule py-3.5 first:pt-0">
            <span className="min-w-0">
              <Link to={`/events/${r.eventId}`} className="font-semibold underline-offset-4 hover:underline">
                {r.eventTitle}
              </Link>
              <span className="tabular ml-3 text-sm text-ink-3">{formatLocal(r.eventDate, 'short')}</span>
              {r.eventStatus === 'CANCELLED' && r.cancellationReason && (
                <span className="mt-1 block text-md text-ink-2">Gerekçe: {r.cancellationReason}</span>
              )}
            </span>
            <Badge tone={status.tone}>{status.label}</Badge>
          </li>
        )
      })}
    </ul>
  )
}

function Requests({ list }: { list: ParticipationRequest[] }) {
  const withdraw = useWithdrawParticipation()
  const [target, setTarget] = useState<ParticipationRequest | null>(null)
  const open = list.filter((r) => r.status === 'PENDING' || r.status === 'WAITLISTED').sort((a, b) => b.requestDate.localeCompare(a.requestDate))
  if (open.length === 0) return <p className="text-ink-3">Onay ya da yer bekleyen isteğiniz yok.</p>
  return (
    <>
      <ul>
        {open.map((r) => (
          <li key={r.id} className="border-b border-rule py-3.5 first:pt-0">
            <Link to={`/events/${r.eventId}`} className="font-semibold underline-offset-4 hover:underline">
              {r.eventTitle}
            </Link>
            <span className="mt-1 flex flex-wrap items-center gap-x-4 gap-y-1">
              <Badge tone={r.status === 'WAITLISTED' ? 'info' : 'warning'}>{r.status === 'WAITLISTED' ? 'Bekleme listesinde' : 'Onay bekleniyor'}</Badge>
              <span className="text-sm text-ink-3">{formatInstant(r.requestDate, 'datetime')}</span>
              <button type="button" onClick={() => setTarget(r)} className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline">
                Geri çek
              </button>
            </span>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={!!target}
        onOpenChange={(o) => !o && setTarget(null)}
        title="İsteği geri çek"
        description={`${target?.eventTitle ?? ''} için katılım isteğiniz geri çekilecek.`}
        confirmLabel="İsteği geri çek"
        loading={withdraw.isPending}
        onConfirm={() =>
          target &&
          withdraw.mutate(target.eventId, {
            onSuccess: () => {
              toast.success('İstek geri çekildi')
              setTarget(null)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </>
  )
}
