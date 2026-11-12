import { useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { toApiError } from '@/lib/api/problem'
import { hasPermission, hasRole, useSession } from '@/lib/auth/session'
import { formatLocal, formatLocalRange, localStamp, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { can, useClubAccess } from '@/features/clubs/manage'
import { Button } from '@/components/ui/Button'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { EmptyState, QueryBoundary, Skeleton } from '@/components/ui/States'
import { useEvent, type CampusEvent } from './api'
import { useAttendance } from './manage'
import { QrScanner } from './QrScanner'
import { checkInName, scanFailure, useMyStaffEvents, useVerifyTicket } from './staff'

/** Sonuç ekranda kalırken okuyucu bekler; aynı kod bu süre içinde yeniden gönderilmez. */
const HOLD_MS = 1800
const SAME_CODE_MS = 5000

/** Başarılı okutmada `message` katılımcının adı (gelmediyse boş), başarısızda neden. */
type Scan = { id: number; ok: boolean; message: string; number: string | null; at: string; code: string }

/**
 * Kapı ekranı (F-63): görevli ya da etkinliği yöneten, bileti kamerayla okutur ya da kodu yazar; sonuç büyük ve
 * renk çizgisiyle görünür. Barkod okuyucu klavye gibi yazıp Enter'a bastığı için kod alanıyla da çalışır.
 */
export function EventGatePage() {
  const { eventId = '' } = useParams()
  const event = useEvent(eventId)
  usePageTitle(event.data ? `${event.data.title} kapısı` : 'Kapı')
  return (
    <div className="mx-auto max-w-[72rem]">
      <QueryBoundary query={event} what="Etkinlik" skeletonRows={3}>
        {(e) => <Gate event={e} />}
      </QueryBoundary>
    </div>
  )
}

function Gate({ event: e }: { event: CampusEvent }) {
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT')
  const access = useClubAccess(e.clubId ?? '', !!e.clubId)
  const mine = useMyStaffEvents(student && !!e.clubId)
  const publisher = !e.clubId && (hasRole(session, 'ROLE_ADMIN') || hasPermission(session, 'PERM_CAMPUS_PUBLISHER'))
  const manages = publisher || can(access.data, 'MANAGE_EVENT_OPERATIONS')
  const assigned = !!mine.data?.some((x) => x.id === e.id)

  if ((e.clubId && access.isPending) || (student && e.clubId && mine.isPending)) return <Skeleton rows={4} />
  if (!manages && !assigned) {
    return (
      <EmptyState title="Bu etkinliğin kapısında görevli değilsiniz">
        Girişleri etkinliği yöneten kulüp görevlileri ve onaylı etkinlik görevlileri yapar.{' '}
        <Link to={`/events/${e.id}`} className="font-semibold underline-offset-4 hover:underline">
          Etkinlik sayfasına dön
        </Link>
      </EmptyState>
    )
  }
  return <GateBody event={e} manages={manages} />
}

function GateBody({ event: e, manages }: { event: CampusEvent; manages: boolean }) {
  const verify = useVerifyTicket(e.id)
  // Sayaç yalnız katılımcı listesini görebilenlere (yöneten); görevli bu oturumdaki girişleri görür.
  const report = useAttendance(e.id, manages)
  const [scans, setScans] = useState<Scan[]>([])
  const [hold, setHold] = useState(false)
  const [code, setCode] = useState('')
  const recent = useRef<{ code: string; at: number } | null>(null)
  const seq = useRef(0)

  const now = localStamp(nowLocalIso())
  // Açılış saati gelmezse erken uyarısı çıkmaz; sunucu yine `CHECK_IN_CLOSED` ile söyler.
  const early = !!e.checkInOpensAt && now < localStamp(e.checkInOpensAt)
  const phase = early ? 'early' : now > localStamp(e.endsAt ?? e.startsAt) ? 'over' : 'open'
  const last = scans[0]

  const submit = (raw: string) => {
    const value = raw.trim()
    if (!value || verify.isPending) return
    const t = Date.now()
    if (recent.current && recent.current.code === value && t - recent.current.at < SAME_CODE_MS) return
    recent.current = { code: value, at: t }
    const record = (ok: boolean, message: string, number: string | null = null) => {
      setScans((s) => [{ id: ++seq.current, ok, message, number, at: nowLocalIso(), code: value }, ...s].slice(0, 30))
      setHold(true)
      setTimeout(() => setHold(false), HOLD_MS)
      navigator.vibrate?.(ok ? 80 : [60, 60, 60])
    }
    verify.mutate(value, {
      onSuccess: (t) => record(true, checkInName(t), t.studentNumber),
      onError: (err) => record(false, scanFailure(toApiError(err))),
    })
  }

  const done = scans.filter((s) => s.ok).length

  return (
    <>
      <p className="flex items-center gap-3 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-etkinlik" />
        <Link to={manages ? `/events/${e.id}/yonetim` : '/me/tickets'} className="font-semibold underline-offset-4 hover:underline">
          {manages ? 'Etkinlik yönetimi' : 'Biletlerim'}
        </Link>
        <span className="text-ink-3">Kapı</span>
      </p>
      <h1 className="mt-1 text-4xl leading-tight font-heavy text-balance sm:text-5xl">{e.title}</h1>
      <p className="tabular mt-3 flex flex-wrap gap-x-5 text-lg text-ink-2">
        <span>{formatLocalRange(e.startsAt, e.endsAt)}</span>
        {e.location && <span>{e.location}</span>}
      </p>

      {phase !== 'open' && (
        <Notice variant="line" tone={phase === 'early' ? 'info' : 'warning'} className="mt-8">
          {phase === 'early' ? `Giriş ${formatLocal(e.checkInOpensAt, 'datetime')} itibarıyla açılır.` : 'Etkinlik bitti; giriş kapandı.'}
        </Notice>
      )}

      {/* Dört parça: kamera, son sonuç, kod alanı, sayaç ve kayıt. Telefonda bu sırayla alt alta (sonuç kameranın hemen
          altında, kaydırmadan görünür); geniş ekranda solda kamera ve altında kod, sağda sonuç ve hemen altında sayaç. */}
      <div className="mt-10 grid gap-x-10 gap-y-8 lg:grid-cols-12">
        <div className="min-w-0 lg:col-span-7 lg:row-span-2 lg:row-start-1">
          <QrScanner paused={hold || verify.isPending} onCode={submit} />
        </div>

        <section aria-live="polite" aria-label="Son okutma" className="border-t-2 border-ink pt-5 lg:col-span-5 lg:col-start-8 lg:row-start-1">
          {last ? (
            <div className={`border-l-[6px] pl-5 ${last.ok ? 'border-success' : 'border-danger'}`}>
              <p className={`text-3xl leading-tight font-heavy ${last.ok ? 'text-success' : 'text-danger'}`}>
                {last.ok ? 'Giriş yapıldı' : 'Giriş yok'}
              </p>
              {last.message && <p className={last.ok ? 'mt-1 text-2xl leading-tight font-semibold' : 'mt-1 text-lg'}>{last.message}</p>}
              <p className="tabular mt-2 text-sm text-ink-3">
                {formatLocal(last.at, 'time')}, {last.number ?? `kod …${last.code.slice(-6)}`}
              </p>
            </div>
          ) : (
            <p className="text-lg text-ink-3">Okutulan ilk bilet burada görünür.</p>
          )}
        </section>

        <div className="min-w-0 lg:col-span-7 lg:col-start-1 lg:row-start-3">
          <form
            onSubmit={(ev) => {
              ev.preventDefault()
              submit(code)
              setCode('')
            }}
            className="flex flex-wrap items-end gap-4"
          >
            <label className="min-w-0 flex-1">
              <span className="mb-1.5 block text-md font-semibold">Bilet kodu</span>
              <Input
                value={code}
                onChange={(ev) => setCode(ev.target.value)}
                autoComplete="off"
                spellCheck={false}
                aria-describedby="kod-ipucu"
              />
            </label>
            <Button type="submit" loading={verify.isPending}>
              Doğrula
            </Button>
          </form>
          <p id="kod-ipucu" className="mt-2 text-sm text-ink-3">
            Kamera okuyamazsa kodu yazın; barkod okuyucu da buraya yazar.
          </p>
        </div>

        <div className="flex flex-col gap-10 lg:col-span-5 lg:col-start-8 lg:row-start-2 lg:self-start">
          <section aria-label="Girişler" className="border-t border-rule pt-5">
            {manages && report.data ? (
              <>
                <p className="text-sm text-ink-3">Giriş yapan</p>
                <p className="mt-1">
                  <span className="tabular text-6xl leading-none font-heavy">{report.data.attended}</span>
                  <span className="tabular ml-2 text-xl text-ink-3">/ {report.data.registered} kayıtlı</span>
                </p>
              </>
            ) : (
              <>
                <p className="text-sm text-ink-3">Bu oturumda</p>
                <p className="mt-1">
                  <span className="tabular text-6xl leading-none font-heavy">{done}</span>
                  <span className="ml-2 text-xl text-ink-3">giriş</span>
                </p>
              </>
            )}
          </section>

          {scans.length > 0 && (
            <section aria-labelledby="okutmalar" className="border-t border-rule pt-5">
              <h2 id="okutmalar" className="text-lg font-heavy">
                Bu oturumdaki okutmalar
              </h2>
              <ol className="mt-3 max-h-[18rem] overflow-y-auto">
                {scans.map((s) => (
                  <li key={s.id} className="grid grid-cols-[4rem_minmax(0,1fr)] gap-x-4 border-b border-rule py-2 text-md">
                    <span className="tabular text-ink-3">{formatLocal(s.at, 'time')}</span>
                    <span className={s.ok ? undefined : 'text-danger'}>
                      {s.message || 'Giriş yapıldı'} <span className="tabular text-sm text-ink-3">{s.number ?? `…${s.code.slice(-6)}`}</span>
                    </span>
                  </li>
                ))}
              </ol>
            </section>
          )}
        </div>
      </div>
    </>
  )
}
