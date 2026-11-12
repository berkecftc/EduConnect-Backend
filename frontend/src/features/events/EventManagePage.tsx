import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatLocalRange, toLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { hasPermission, hasRole, useSession } from '@/lib/auth/session'
import { can, useClubAccess, type ClubAccess } from '@/features/clubs/manage'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { EmptyState, QueryBoundary, Skeleton } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Textarea } from '@/components/ui/Textarea'
import { useEvent, type CampusEvent } from './api'
import { EventForm } from './EventForm'
import { EventStaffPanel } from './EventStaffPanel'
import {
  CHANGE_LABEL,
  EVENT_STATUS,
  eventToDraft,
  useAttendance,
  useChangeEvent,
  useCheckIn,
  useDecideRequest,
  useEventChanges,
  useEventRequests,
  useUpdateEvent,
  type AttendanceRow,
  type ManagedRequest,
} from './manage'
import { PersonLink } from '@/features/people/PersonLink'

const REASON_MAX = 1000

/** Etkinlik yönetimi (F-62…F-65): yetki kulüpteki göreve göre backend'den (F-85). */
export function EventManagePage() {
  const { eventId = '' } = useParams()
  const event = useEvent(eventId)
  usePageTitle(event.data ? `${event.data.title} yönetimi` : 'Etkinlik yönetimi')
  return (
    <div className="mx-auto max-w-[80rem]">
      <QueryBoundary query={event} what="Etkinlik" skeletonRows={4}>
        {(e) => <ManageBody event={e} />}
      </QueryBoundary>
    </div>
  )
}

function ManageBody({ event: e }: { event: CampusEvent }) {
  const access = useClubAccess(e.clubId ?? '', !!e.clubId)
  const session = useSession()
  // Kulübü olmayan (kampüs) etkinliği kampüs yayıncısı ve yönetici yönetir (F-64).
  const publisher = !e.clubId && (hasRole(session, 'ROLE_ADMIN') || hasPermission(session, 'PERM_CAMPUS_PUBLISHER'))
  const requestsOpen = (e.status === 'ACTIVE' || e.status === 'COMPLETED') && e.admission === 'APPROVAL_REQUIRED'
  const requests = useEventRequests(e.id, requestsOpen)
  const pendingRequests = (requests.data ?? []).filter((r) => r.status === 'PENDING').length
  if (e.clubId && access.isPending) return <Skeleton rows={4} />
  const a = access.data
  const operates = publisher || can(a, 'MANAGE_EVENT_OPERATIONS', 'CREATE_EVENT', 'PREPARE_EVENT', 'APPROVE_AS_PRESIDENT', 'ADVISE')
  if (!operates) {
    return (
      <EmptyState title="Bu etkinliği yönetemezsiniz">
        {e.clubId ? 'Etkinlik yönetimi düzenleyen kulübün yönetimine açıktır.' : 'Kampüs etkinliklerini kampüs etkinliği yetkisi olan görevliler yönetir.'}{' '}
        <Link to={`/events/${e.id}`} className="font-semibold underline-offset-4 hover:underline">
          Etkinlik sayfasına dön
        </Link>
      </EmptyState>
    )
  }
  const status = EVENT_STATUS[e.status]
  const live = e.status === 'ACTIVE' || e.status === 'COMPLETED'
  const editable = (e.status === 'PENDING_PRESIDENT' || e.status === 'PENDING' || e.status === 'REJECTED') && can(a, 'CREATE_EVENT', 'PREPARE_EVENT')

  return (
    <>
      <p className="flex items-center gap-3 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-etkinlik" />
        {e.clubId ? (
          <Link to={`/clubs/${e.clubId}?sekme=yonetim`} className="font-semibold underline-offset-4 hover:underline">
            {e.clubName}
          </Link>
        ) : (
          <Link to="/manage/campus-events" className="font-semibold underline-offset-4 hover:underline">
            Kampüs etkinlikleri
          </Link>
        )}
        <span className="text-ink-3">Etkinlik yönetimi</span>
      </p>
      <RevealTitle text={e.title} className="mt-1 text-5xl" />
      <p className="mt-5 flex flex-wrap items-center gap-x-5 gap-y-1 text-lg text-ink-2">
        <Badge tone={status.tone}>{status.label}</Badge>
        <span className="tabular">{formatLocalRange(e.startsAt, e.endsAt)}</span>
        {e.location && <span>{e.location}</span>}
        <Link to={`/events/${e.id}`} className="text-md font-semibold text-ink underline-offset-4 hover:underline">
          Etkinlik sayfası
        </Link>
        {e.status === 'ACTIVE' && (publisher || can(a, 'MANAGE_EVENT_OPERATIONS')) && (
          <Link to={`/events/${e.id}/kapi`} className="text-md font-semibold text-ink underline-offset-4 hover:underline">
            Kapı ekranı
          </Link>
        )}
      </p>

      {e.status === 'REJECTED' && (
        <Notice variant="line" tone="danger" title="Etkinlik reddedildi" className="mt-8">
          {e.rejectionReason ? `Gerekçe: ${e.rejectionReason}. ` : ''}Düzenle sekmesinden düzeltip yeniden onaya gönderebilirsiniz.
        </Notice>
      )}
      {(e.status === 'PENDING_PRESIDENT' || e.status === 'PENDING') && (
        <Notice variant="line" tone="info" className="mt-8">
          {e.status === 'PENDING_PRESIDENT' ? 'Etkinlik başkan onayında; ardından danışmana gider.' : 'Etkinlik danışman onayında.'} Onaylanınca yayımlanır
          ve kayıt açılır.
        </Notice>
      )}

      <div className="mt-12">
        <Tabs
          label="Etkinlik yönetimi"
          tabs={[
            ...(live ? [{ value: 'katilimcilar', label: 'Katılımcılar', content: <Attendance event={e} /> }] : []),
            ...(live && e.admission === 'APPROVAL_REQUIRED' ? [{ value: 'istekler', label: 'Katılım istekleri', count: pendingRequests || undefined, content: <Requests eventId={e.id} /> }] : []),
            ...(e.clubId ? [{ value: 'gorevliler', label: 'Görevliler', content: <EventStaffPanel event={e} access={a} /> }] : []),
            { value: 'degisiklikler', label: 'Değişiklikler', content: <Changes event={e} access={a} publisher={publisher} /> },
            ...(editable ? [{ value: 'duzenle', label: 'Düzenle', content: <Edit event={e} /> }] : []),
          ]}
        />
      </div>
    </>
  )
}

// ——— Katılımcılar ve yoklama ———

function Attendance({ event: e }: { event: CampusEvent }) {
  const report = useAttendance(e.id, true)
  const checkIn = useCheckIn(e.id)
  const [code, setCode] = useState('')
  const [busyRow, setBusyRow] = useState<string | null>(null)

  const fail = (err: unknown) => toast.error(toApiError(err).message)
  const submitCode = (ev: React.FormEvent) => {
    ev.preventDefault()
    if (!code.trim()) return
    checkIn.mutate(
      { kind: 'code', code },
      {
        onSuccess: () => {
          toast.success('Bilet doğrulandı, giriş yapıldı')
          setCode('')
        },
        onError: fail,
      },
    )
  }

  return (
    <QueryBoundary query={report} what="Yoklama" skeletonRows={4}>
      {(r) => (
        <div className="grid gap-14 lg:grid-cols-12 lg:gap-10">
          <div className="min-w-0 lg:col-span-8">
            <dl className="grid grid-cols-2 border-y border-rule sm:grid-cols-4">
              <Stat label="Kayıtlı" value={r.registered} />
              <Stat label="Giriş yaptı" value={r.attended} accent />
              <Stat label="Gelmedi" value={r.noShow} />
              <Stat label="İptal etti" value={r.cancelled} />
            </dl>
            <div className="mt-10">
              {r.rows.length === 0 ? (
                <p className="text-ink-3">Henüz kayıtlı katılımcı yok.</p>
              ) : (
                <ul className="border-t-2 border-ink">
                  {[...r.rows]
                    .sort((x, y) => `${x.firstName} ${x.lastName}`.localeCompare(`${y.firstName} ${y.lastName}`, 'tr-TR'))
                    .map((row) => (
                      <Row
                        key={row.studentId}
                        row={row}
                        busy={checkIn.isPending && busyRow === row.studentId}
                        onToggle={() => {
                          setBusyRow(row.studentId)
                          checkIn.mutate(
                            { kind: row.attended ? 'undo' : 'in', studentId: row.studentId },
                            { onSuccess: () => toast.success(row.attended ? 'Giriş geri alındı' : 'Giriş yapıldı'), onError: fail },
                          )
                        }}
                      />
                    ))}
                </ul>
              )}
            </div>
          </div>
          <aside className="flex flex-col gap-20 lg:col-span-4">
            <Panel title="Bilet koduyla giriş">
              <form onSubmit={submitCode} className="flex flex-col gap-4">
                <Field label="Bilet kodu" hint="Barkod okuyucuyla okutun ya da biletteki kodu yapıştırın. Giriş etkinlikten 1 saat önce açılır.">
                  <Input value={code} onChange={(ev) => setCode(ev.target.value)} autoComplete="off" spellCheck={false} />
                </Field>
                <div>
                  <Button type="submit" variant="primary" loading={checkIn.isPending && !busyRow}>
                    Girişi doğrula
                  </Button>
                </div>
              </form>
            </Panel>
            <Panel title="Dışa aktar">
              <button
                type="button"
                onClick={() => void downloadFile(`/events/manage/${e.id}/attendance.csv`, `${e.title} yoklama.csv`).catch(fail)}
                className="row-fill flex w-full items-center justify-between gap-4 border-y border-rule px-1 py-3.5 text-left"
              >
                <span>
                  <span className="block font-semibold">Yoklama listesi</span>
                  <span className="block text-sm text-ink-3">CSV, tablo programında açılır</span>
                </span>
                <span className="flex items-center gap-2 text-sm font-semibold">
                  <Download className="size-4" aria-hidden />
                  İndir
                </span>
              </button>
            </Panel>
          </aside>
        </div>
      )}
    </QueryBoundary>
  )
}

function Stat({ label, value, accent }: { label: string; value: number; accent?: boolean }) {
  return (
    <div className="border-rule py-4 not-first:border-l not-first:pl-5 max-sm:[&:nth-child(3)]:border-l-0 max-sm:[&:nth-child(3)]:pl-0">
      <dt className="text-sm text-ink-3">{label}</dt>
      <dd className={cn('tabular mt-1 text-4xl leading-none font-heavy', accent && 'text-etkinlik')}>{formatNumber(value)}</dd>
    </div>
  )
}

const ROW_STATUS: Record<AttendanceRow['status'], string> = { REGISTERED: 'Kayıtlı', CANCELLED: 'Kaydını iptal etti', NO_SHOW: 'Gelmedi' }

function Row({ row, busy, onToggle }: { row: AttendanceRow; busy: boolean; onToggle: () => void }) {
  const name = [row.firstName, row.lastName].filter(Boolean).join(' ') || 'Katılımcı'
  return (
    <li className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-6 gap-y-1 border-b border-rule py-4">
      <span className="min-w-0">
        <PersonLink id={row.studentId} className="block font-semibold">
          {name}
        </PersonLink>
        <span className="text-sm text-ink-3">
          {row.studentNumber && <span className="tabular mr-4">{row.studentNumber}</span>}
          {row.attended && row.checkedInAt ? `Giriş ${formatInstant(row.checkedInAt, 'time')}` : ROW_STATUS[row.status]}
        </span>
      </span>
      {row.status === 'REGISTERED' &&
        (row.attended ? (
          <span className="flex items-center gap-4">
            <Badge tone="success">Giriş yaptı</Badge>
            <button type="button" onClick={onToggle} disabled={busy} className="text-sm font-semibold text-ink-3 underline-offset-4 hover:text-ink hover:underline">
              Geri al
            </button>
          </span>
        ) : (
          <Button size="sm" loading={busy} onClick={onToggle}>
            Giriş yap
          </Button>
        ))}
    </li>
  )
}

// ——— Katılım istekleri ———

function Requests({ eventId }: { eventId: string }) {
  const requests = useEventRequests(eventId, true)
  return (
    <QueryBoundary query={requests} what="Katılım istekleri" skeletonRows={3}>
      {(list) => {
        const pending = list.filter((r) => r.status === 'PENDING').sort((a, b) => a.requestDate.localeCompare(b.requestDate))
        const waiting = list.filter((r) => r.status === 'WAITLISTED').sort((a, b) => a.requestDate.localeCompare(b.requestDate))
        return (
          <div className="grid gap-14 lg:grid-cols-12 lg:gap-10">
            <div className="min-w-0 lg:col-span-8">
              <Panel title="Onay bekleyenler" action={pending.length ? <span className="tabular text-md text-ink-3">{pending.length}</span> : undefined}>
                {pending.length === 0 ? (
                  <p className="text-ink-3">Onay bekleyen katılım isteği yok.</p>
                ) : (
                  <ul className="border-t border-rule">
                    {pending.map((r) => (
                      <RequestRow key={r.id} eventId={eventId} request={r} />
                    ))}
                  </ul>
                )}
              </Panel>
            </div>
            <aside className="lg:col-span-4">
              <Panel title="Bekleme listesi">
                {waiting.length === 0 ? (
                  <p className="text-ink-3">Bekleme listesi boş.</p>
                ) : (
                  <ol>
                    {waiting.map((r, i) => (
                      <li key={r.id} className="flex items-baseline gap-4 border-b border-rule py-3 first:pt-0">
                        <span className="tabular w-6 text-ink-3">{i + 1}</span>
                        <span className="font-semibold">{r.studentName ?? 'Öğrenci'}</span>
                      </li>
                    ))}
                  </ol>
                )}
                <p className="mt-3 text-sm text-ink-3">Yer açıldıkça sıradaki kişinin kaydı kendiliğinden yapılır.</p>
              </Panel>
            </aside>
          </div>
        )
      }}
    </QueryBoundary>
  )
}

function RequestRow({ eventId, request: r }: { eventId: string; request: ManagedRequest }) {
  const decide = useDecideRequest(eventId)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const name = r.studentName ?? 'Öğrenci'
  const fail = (err: unknown) => toast.error(toApiError(err).message)
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 sm:grid-cols-[minmax(0,1fr)_auto]">
      <div className="min-w-0">
        <p className="text-lg font-semibold">
          <PersonLink id={r.studentId}>{name}</PersonLink>
        </p>
        <p className="text-sm text-ink-3">
          {r.studentEmail && <span className="mr-4">{r.studentEmail}</span>}
          {formatInstant(r.requestDate, 'datetime')}
        </p>
        {r.message && <p className="mt-2 max-w-[60ch] text-md text-ink-2">“{r.message}”</p>}
      </div>
      <div className="flex items-center gap-4 sm:flex-col sm:items-end">
        <Button
          size="sm"
          variant="primary"
          loading={decide.isPending && !rejecting}
          onClick={() => decide.mutate({ requestId: r.id, kind: 'approve' }, { onSuccess: () => toast.success(`${name} onaylandı, bileti gönderildi`), onError: fail })}
        >
          Onayla
        </Button>
        <button type="button" onClick={() => setRejecting(true)} className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-danger hover:underline">
          Reddet
        </button>
      </div>
      <ConfirmDialog
        open={rejecting}
        onOpenChange={setRejecting}
        title={`${name} isteği reddedilsin mi?`}
        description="Gerekçe yazarsanız katılımcıya iletilir."
        confirmLabel="Reddet"
        destructive
        loading={decide.isPending}
        onConfirm={() =>
          decide.mutate(
            { requestId: r.id, kind: 'reject', reason },
            {
              onSuccess: () => {
                toast.success('İstek reddedildi')
                setRejecting(false)
              },
              onError: fail,
            },
          )
        }
      >
        <Field label="Gerekçe" hint="İsteğe bağlı.">
          <Textarea maxLength={REASON_MAX} valueLength={reason.length} value={reason} onChange={(ev) => setReason(ev.target.value)} rows={3} />
        </Field>
      </ConfirmDialog>
    </li>
  )
}

// ——— Değişiklikler ———

type ChangeKind = 'postpone' | 'relocate' | 'cancel'

/** Yayımlanmış etkinlikte başkan erteler ya da yerini değiştirir; başkan ve danışman iptal eder. Geçmiş altta. */
function Changes({ event: e, access, publisher }: { event: CampusEvent; access?: ClubAccess; publisher: boolean }) {
  const changes = useEventChanges(e.id, true)
  const change = useChangeEvent(e.id)
  const [dialog, setDialog] = useState<ChangeKind | null>(null)
  const [form, setForm] = useState({ startsAt: '', endsAt: '', location: '', reason: '' })
  const [failure, setFailure] = useState<ApiError | null>(null)
  // Kulüp etkinliğinde başkan (iptalde danışman da), kampüs etkinliğinde yayıncı. Ertelenen kampüs etkinliği onaya düşmez.
  const president = publisher || can(access, 'APPROVE_AS_PRESIDENT')
  const changeable = e.status === 'ACTIVE'
  const actions: { kind: ChangeKind; label: string; allowed: boolean; hint: string }[] = [
    {
      kind: 'postpone',
      label: 'Ertele',
      allowed: president,
      hint: publisher
        ? 'Yeni tarih verin; kayıtlılara e-posta gider.'
        : 'Yeni tarih verin. Etkinlik yeniden danışman onayına düşer, kayıtlılara e-posta gider.',
    },
    { kind: 'relocate', label: 'Yeri değiştir', allowed: president, hint: 'Kayıtlılara yeni yer e-postayla bildirilir.' },
    { kind: 'cancel', label: 'İptal et', allowed: president || can(access, 'ADVISE'), hint: 'Kayıtlılara gerekçeyle birlikte e-posta gider. Geri alınamaz.' },
  ]
  const open = (k: ChangeKind) => {
    setForm({ startsAt: '', endsAt: '', location: e.location ?? '', reason: '' })
    setFailure(null)
    setDialog(k)
  }
  const current = actions.find((x) => x.kind === dialog)
  const run = () => {
    if (!form.reason.trim()) {
      toast.error('Gerekçe yazın.')
      return
    }
    if (dialog === 'postpone' && !form.startsAt) {
      toast.error('Yeni başlangıç zamanını seçin.')
      return
    }
    change.mutate(
      {
        kind: dialog!,
        reason: form.reason,
        startsAt: form.startsAt ? toLocalIso(form.startsAt) : undefined,
        endsAt: form.endsAt ? toLocalIso(form.endsAt) : undefined,
        location: form.location,
      },
      {
        onSuccess: () => {
          toast.success(dialog === 'postpone' ? (publisher ? 'Etkinlik ertelendi; kayıtlılara e-posta gitti' : 'Etkinlik ertelendi, danışman onayına gönderildi') : dialog === 'relocate' ? 'Etkinliğin yeri değişti' : 'Etkinlik iptal edildi')
          setDialog(null)
        },
        onError: (err) => setFailure(toApiError(err)),
      },
    )
  }

  return (
    <div className="grid gap-14 lg:grid-cols-12 lg:gap-10">
      <div className="min-w-0 lg:col-span-8">
        <Panel title="Değişiklik geçmişi">
          <QueryBoundary query={changes} what="Değişiklikler" skeletonRows={2}>
            {(list) =>
              list.length === 0 ? (
                <p className="text-ink-3">Etkinlikte henüz değişiklik yapılmadı.</p>
              ) : (
                <ol className="border-t border-rule">
                  {[...list]
                    .sort((x, y) => y.createdAt.localeCompare(x.createdAt))
                    .map((c) => (
                      <li key={c.id} className="grid gap-x-8 gap-y-1 border-b border-rule py-4 sm:grid-cols-[10rem_minmax(0,1fr)]">
                        <span>
                          <span className="block font-semibold">{CHANGE_LABEL[c.kind]}</span>
                          <span className="tabular text-sm text-ink-3">{formatInstant(c.createdAt, 'datetime')}</span>
                        </span>
                        <span className="min-w-0 text-md text-ink-2">
                          {c.details && <span className="block">{c.details}</span>}
                          {c.reason && <span className="block">Gerekçe: {c.reason}</span>}
                        </span>
                      </li>
                    ))}
                </ol>
              )
            }
          </QueryBoundary>
        </Panel>
      </div>
      <aside className="lg:col-span-4">
        <Panel title="Değişiklik yap">
          {!changeable ? (
            <p className="text-ink-3">Erteleme, yer değişikliği ve iptal yalnız yayındaki etkinlikte yapılır.</p>
          ) : (
            <ul>
              {actions
                .filter((x) => x.allowed)
                .map((x) => (
                  <li key={x.kind} className="border-b border-rule py-3.5 first:pt-0">
                    <button
                      type="button"
                      onClick={() => open(x.kind)}
                      className={cn('font-semibold underline-offset-4 hover:underline', x.kind === 'cancel' && 'text-danger')}
                    >
                      {x.label}
                    </button>
                    <p className="mt-0.5 text-sm text-ink-3">{x.hint}</p>
                  </li>
                ))}
              {actions.every((x) => !x.allowed) && <p className="text-ink-3">Bu değişiklikleri başkan ve danışman yapabilir.</p>}
            </ul>
          )}
        </Panel>
      </aside>

      <ConfirmDialog
        open={!!dialog}
        onOpenChange={(o) => !o && setDialog(null)}
        title={current ? `${current.label}: ${e.title}` : ''}
        description={current?.hint ?? ''}
        confirmLabel={current?.label ?? 'Kaydet'}
        destructive={dialog === 'cancel'}
        loading={change.isPending}
        onConfirm={run}
      >
        <div className="flex flex-col gap-4">
          {dialog === 'postpone' && (
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Yeni başlangıç" required>
                <Input type="datetime-local" value={form.startsAt} onChange={(ev) => setForm((f) => ({ ...f, startsAt: ev.target.value }))} />
              </Field>
              <Field label="Yeni bitiş">
                <Input type="datetime-local" value={form.endsAt} onChange={(ev) => setForm((f) => ({ ...f, endsAt: ev.target.value }))} />
              </Field>
            </div>
          )}
          {dialog === 'relocate' && (
            <Field label="Yeni yer" required>
              <Input maxLength={255} value={form.location} onChange={(ev) => setForm((f) => ({ ...f, location: ev.target.value }))} />
            </Field>
          )}
          <Field label="Gerekçe" required hint="Kayıtlılara iletilir.">
            <Textarea maxLength={REASON_MAX} valueLength={form.reason.length} value={form.reason} onChange={(ev) => setForm((f) => ({ ...f, reason: ev.target.value }))} rows={3} />
          </Field>
          {failure && (
            <Notice variant="line" tone="danger">
              {failure.message}
            </Notice>
          )}
        </div>
      </ConfirmDialog>
    </div>
  )
}

// ——— Düzenle ———

function Edit({ event: e }: { event: CampusEvent }) {
  const update = useUpdateEvent(e.id)
  const [note, setNote] = useState('')
  const [failure, setFailure] = useState<ApiError | null>(null)
  const rejected = e.status === 'REJECTED'
  const noteField: ReactNode = (
    <Field
      label={rejected ? 'Düzeltme notu' : 'Not'}
      required={rejected}
      hint={rejected ? 'Neyi düzelttiğinizi yazın; onaylayacak kişiye gösterilir.' : 'İsteğe bağlı.'}
      error={failure?.code === 'RESUBMISSION_NOTE_REQUIRED' ? failure.message : undefined}
    >
      <Textarea maxLength={REASON_MAX} valueLength={note.length} value={note} onChange={(ev) => setNote(ev.target.value)} rows={3} />
    </Field>
  )
  return (
    <EventForm
      initial={eventToDraft(e)}
      submitLabel={rejected ? 'Yeniden onaya gönder' : 'Değişiklikleri kaydet'}
      busy={update.isPending}
      failure={failure?.code === 'RESUBMISSION_NOTE_REQUIRED' ? null : failure}
      withPoster={false}
      extra={noteField}
      onSubmit={(draft) => {
        if (rejected && !note.trim()) {
          setFailure(null)
          toast.error('Düzeltme notu yazın.')
          return
        }
        setFailure(null)
        update.mutate(
          { draft, note },
          {
            onSuccess: () => toast.success(rejected ? 'Etkinlik yeniden onaya gönderildi' : 'Değişiklikler kaydedildi'),
            onError: (err) => setFailure(toApiError(err)),
          },
        )
      }}
    />
  )
}
