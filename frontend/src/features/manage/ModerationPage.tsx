import { useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import type { Paged } from '@/lib/api/page'
import { formatInstant, formatRelative } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { POST_STATUS, type PostStatus } from '@/features/posts/api'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Field } from '@/components/ui/Field'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { Tabs } from '@/components/ui/Tabs'
import { Textarea } from '@/components/ui/Textarea'
import {
  ACTION_LABEL,
  ACTOR_LABEL,
  APPEALED_LABEL,
  PAGE,
  TARGET_LABEL,
  moderationErrorMessage,
  useAppealQueue,
  useModerate,
  useModerationHistory,
  useQueue,
  useReports,
  type AppealItem,
  type ModerationAction,
  type QueueItem,
  type ReportItem,
  type TargetType,
} from './moderation'
import { RequireAccess } from './RequireAccess'

const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'

/** Moderasyon (F-68…F-70): inceleme kuyruğu (gönderi, yorum), şikâyetler (öncelikliler üstte), itirazlar. */
export function ModerationPage() {
  usePageTitle('Moderasyon')
  return (
    <RequireAccess perms={['PERM_MODERATOR']}>
      <Moderation />
    </RequireAccess>
  )
}

function Moderation() {
  // Sekme sayıları: tek öğelik sayfadan toplam.
  const posts = useQueue('POST', 0, 1)
  const comments = useQueue('COMMENT', 0, 1)
  const reports = useReports(0, 1)
  const appeals = useAppealQueue(0, 1)
  const counts = [posts, comments, reports, appeals].map((q) => q.data?.totalElements)
  const total = counts.every((n) => n !== undefined) ? counts.reduce((a, b) => a! + b!, 0) : undefined

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader
        title="Moderasyon"
        meta={total === undefined ? undefined : total ? `${total} kayıt kararınızı bekliyor.` : 'Kararınızı bekleyen kayıt yok.'}
      />
      <div className="mt-8">
        <Tabs
          label="Moderasyon kuyrukları"
          tabs={[
            { value: 'gonderiler', label: 'Gönderiler', count: counts[0], content: <QueueList type="POST" /> },
            { value: 'yorumlar', label: 'Yorumlar', count: counts[1], content: <QueueList type="COMMENT" /> },
            { value: 'sikayetler', label: 'Şikâyetler', count: counts[2], content: <ReportList /> },
            { value: 'itirazlar', label: 'İtirazlar', count: counts[3], content: <AppealList /> },
          ]}
        />
      </div>
    </div>
  )
}

// ——— Ortak parçalar ———

/** Sayfa geçişi: "21–40 / 57", önceki ve sonraki. Tek sayfada görünmez. */
function Pager<T>({ data, page, onChange }: { data: Paged<T>; page: number; onChange: (p: number) => void }) {
  if (data.totalPages <= 1) return null
  const from = page * PAGE + 1
  const to = Math.min(data.totalElements, from + data.content.length - 1)
  return (
    <nav aria-label="Sayfalar" className="mt-5 flex items-center gap-4">
      <Button size="sm" disabled={page === 0} onClick={() => onChange(page - 1)}>
        Önceki
      </Button>
      <span className="tabular text-sm text-ink-3">
        {from}–{to} / {data.totalElements}
      </span>
      <Button size="sm" disabled={data.last} onClick={() => onChange(page + 1)}>
        Sonraki
      </Button>
    </nav>
  )
}

/** İçerik alıntısı: uzunsa dört satıra kısalır, "Tamamını göster" ile açılır. */
function Quote({ label, children }: { label?: string; children: string | null }) {
  const [open, setOpen] = useState(false)
  if (!children) return <p className="mt-3 text-sm text-ink-3">İçerik metni yok.</p>
  const long = children.length > 320
  return (
    <div className="mt-3 max-w-[68ch]">
      {label && <p className="text-sm font-semibold text-ink-3">{label}</p>}
      <p className={`border-l-[3px] border-rule pl-4 text-md whitespace-pre-line ${long && !open ? 'line-clamp-4' : ''}`}>{children}</p>
      {long && (
        <button type="button" onClick={() => setOpen((v) => !v)} className={`mt-1 ml-[19px] ${LINK}`}>
          {open ? 'Kısalt' : 'Tamamını göster'}
        </button>
      )}
    </div>
  )
}

/** Moderasyon geçmişi: kim, ne zaman, hangi kararı verdi. Açılınca yüklenir. */
function History({ type, id }: { type: TargetType; id: string }) {
  const [open, setOpen] = useState(false)
  const history = useModerationHistory(type, id, open)
  return (
    <div className="mt-3">
      <button type="button" aria-expanded={open} onClick={() => setOpen((v) => !v)} className={LINK}>
        {open ? 'Geçmişi gizle' : 'Moderasyon geçmişi'}
      </button>
      {open &&
        (history.isPending ? (
          <p className="mt-2 text-sm text-ink-3">Yükleniyor…</p>
        ) : (history.data?.length ?? 0) === 0 ? (
          <p className="mt-2 text-sm text-ink-3">Kayıt yok.</p>
        ) : (
          <ul className="mt-2 divide-y divide-rule border-y border-rule text-sm">
            {history.data!.map((h) => (
              <li key={h.id} className="flex flex-wrap gap-x-4 gap-y-0.5 py-2">
                <span className="tabular text-ink-3">{formatInstant(h.createdAt, 'datetime')}</span>
                <span className="font-semibold">{ACTION_LABEL[h.action] ?? h.action}</span>
                <span className="text-ink-2">{ACTOR_LABEL[h.actorType]}</span>
                {h.reason && <span className="basis-full text-ink-2">{h.reason}</span>}
              </li>
            ))}
          </ul>
        ))}
    </div>
  )
}

const postLink = (postId: string) => (
  <Link to={`/posts/${postId}`} className={LINK}>
    Gönderiyi aç
  </Link>
)

type Pending = { action: ModerationAction; title: string; description: string; confirmLabel: string; destructive?: boolean; done: string }

/** Kararlar gerekçeli; pencere gerekçeyi alır, kararı gönderir. Onaylarda gerekçe isteğe bağlı. */
function useDecision() {
  const moderate = useModerate()
  const [pending, setPending] = useState<Pending | null>(null)
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string | null>(null)
  const optional = pending?.action.kind === 'decide' && pending.action.approve

  const ask = (p: Pending) => {
    setPending(p)
    setReason('')
    setError(null)
  }
  const run = (action: ModerationAction, done: string) =>
    moderate.mutate(action, {
      onSuccess: () => {
        toast.success(done)
        setPending(null)
      },
      onError: (e) => {
        const ae = toApiError(e)
        setError(moderationErrorMessage(ae.code, ae.message))
      },
    })

  const dialog: ReactNode = (
    <ConfirmDialog
      open={!!pending}
      onOpenChange={(o) => !o && setPending(null)}
      destructive={pending?.destructive}
      title={pending?.title ?? ''}
      description={pending?.description ?? ''}
      confirmLabel={pending?.confirmLabel ?? 'Tamam'}
      loading={moderate.isPending}
      onConfirm={() => {
        if (!pending) return
        if (!optional && !reason.trim()) return setError('Gerekçe yazın; kayıtlarda kalır ve içerik sahibine iletilir.')
        run({ ...pending.action, reason } as ModerationAction, pending.done)
      }}
    >
      <Field
        label="Gerekçe"
        required={!optional}
        error={error ?? undefined}
        hint={optional ? 'İsteğe bağlı.' : 'İçerik sahibi görür; itiraz ederse değerlendirmede okunur.'}
      >
        <Textarea rows={3} maxLength={1000} valueLength={reason.length} value={reason} onChange={(e) => setReason(e.target.value)} />
      </Field>
    </ConfirmDialog>
  )
  return { ask, run, busy: moderate.isPending, dialog }
}

// ——— İnceleme kuyruğu ———

function QueueList({ type }: { type: TargetType }) {
  const [page, setPage] = useState(0)
  const list = useQueue(type, page)
  return (
    <QueryBoundary query={list} what="İnceleme kuyruğu">
      {(data) =>
        data.content.length === 0 ? (
          <EmptyState title={type === 'POST' ? 'İncelenecek gönderi yok' : 'İncelenecek yorum yok'}>
            Otomatik kontrolün karar veremediği ya da şüphelendiği içerikler burada sizi bekler; en eski önce.
          </EmptyState>
        ) : (
          <>
            <ul className="border-t border-ink">
              {data.content.map((item) => (
                <QueueRow key={item.id} item={item} />
              ))}
            </ul>
            <Pager data={data} page={page} onChange={setPage} />
          </>
        )
      }
    </QueryBoundary>
  )
}

function QueueRow({ item: q }: { item: QueueItem }) {
  const d = useDecision()
  const label = q.targetType === 'POST' ? (q.title ?? 'Başlıksız gönderi') : 'Yorum'
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="text-lg font-semibold">{label}</p>
        <p className="mt-0.5 flex flex-wrap items-baseline gap-x-4 text-sm text-ink-3">
          <span>{q.authorName ?? 'Yazar'}</span>
          <span>{formatRelative(q.submittedAt)} gönderildi</span>
          {postLink(q.postId)}
        </p>
        {q.flag && <p className="mt-2 border-l-[3px] border-warning pl-4 text-md font-semibold">{q.flag}</p>}
        <Quote>{q.content}</Quote>
        <History type={q.targetType} id={q.id} />
      </div>
      <div className="flex items-center gap-3">
        <Button
          size="sm"
          variant="primary"
          disabled={d.busy}
          onClick={() =>
            d.ask({
              action: { kind: 'decide', type: q.targetType, id: q.id, approve: true, reason: '' },
              title: `${TARGET_LABEL[q.targetType]} yayımlansın mı?`,
              description: 'İçerik akışta görünür; yazarına bildirim gider.',
              confirmLabel: 'Yayımla',
              done: `${TARGET_LABEL[q.targetType]} yayımlandı`,
            })
          }
        >
          Yayımla<span className="sr-only">: {label}</span>
        </Button>
        <Button
          size="sm"
          disabled={d.busy}
          onClick={() =>
            d.ask({
              action: { kind: 'decide', type: q.targetType, id: q.id, approve: false, reason: '' },
              title: `${TARGET_LABEL[q.targetType]} reddedilsin mi?`,
              description: 'İçerik yayımlanmaz; yazarı gerekçeyi görür ve bir kez itiraz edebilir.',
              confirmLabel: 'Reddet',
              destructive: true,
              done: `${TARGET_LABEL[q.targetType]} reddedildi`,
            })
          }
        >
          Reddet<span className="sr-only">: {label}</span>
        </Button>
      </div>
      {d.dialog}
    </li>
  )
}

// ——— Şikâyetler ———

function ReportList() {
  const [page, setPage] = useState(0)
  const list = useReports(page)
  return (
    <QueryBoundary query={list} what="Şikâyetler">
      {(data) =>
        data.content.length === 0 ? (
          <EmptyState title="Açık şikâyet yok">
            Kullanıcılar bir gönderi ya da yorumu şikâyet ettiğinde burada listelenir. Taciz ve tehdit şikâyetleri en üstte.
          </EmptyState>
        ) : (
          <>
            <ul className="border-t border-ink">
              {data.content.map((r) => (
                <ReportRow key={r.id} report={r} />
              ))}
            </ul>
            <Pager data={data} page={page} onChange={setPage} />
          </>
        )
      }
    </QueryBoundary>
  )
}

function ReportRow({ report: r }: { report: ReportItem }) {
  const d = useDecision()
  const status = POST_STATUS[r.targetStatus as PostStatus]
  const removed = r.targetStatus === 'REMOVED'
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
          <span className="text-lg font-semibold">{r.reasonLabel ?? r.reason}</span>
          {r.sensitive && <Badge tone="danger">Öncelikli</Badge>}
          {r.openReportsOnTarget > 1 && (
            <span className="tabular text-sm font-semibold text-warning">{r.openReportsOnTarget} açık şikâyet</span>
          )}
        </p>
        <p className="mt-0.5 flex flex-wrap items-baseline gap-x-4 text-sm text-ink-3">
          <span>
            {TARGET_LABEL[r.targetType]}
            {status ? `, ${status.label.toLocaleLowerCase('tr-TR')}` : ''}
          </span>
          <span>{formatRelative(r.createdAt)} şikâyet edildi</span>
          {postLink(r.postId)}
        </p>
        {r.details && (
          <p className="mt-2 max-w-[68ch] text-md text-ink-2">
            <span className="font-semibold text-ink">Şikâyet notu: </span>
            {r.details}
          </p>
        )}
        <Quote label="İçerik">{r.targetContent}</Quote>
        <History type={r.targetType} id={r.targetId} />
      </div>
      <div className="flex flex-col items-start gap-2 md:items-end">
        {!removed && (
          <Button
            size="sm"
            variant="danger"
            disabled={d.busy}
            onClick={() =>
              d.ask({
                action: { kind: 'remove', type: r.targetType, id: r.targetId, reason: '' },
                title: `${TARGET_LABEL[r.targetType]} kaldırılsın mı?`,
                description:
                  'İçerik herkesten kaldırılır ve bu içeriğe açılmış şikâyetler haklı sayılır. Yazarı gerekçeyi görür ve itiraz edebilir.',
                confirmLabel: 'İçeriği kaldır',
                destructive: true,
                done: 'İçerik kaldırıldı; şikâyetler kapandı',
              })
            }
          >
            İçeriği kaldır
          </Button>
        )}
        <Button
          size="sm"
          disabled={d.busy}
          onClick={() =>
            d.ask({
              action: { kind: 'dismiss', reportId: r.id, reason: '' },
              title: 'Şikâyeti reddet',
              description: 'İçerik yayında kalır; şikâyet kapanır.',
              confirmLabel: 'Şikâyeti reddet',
              done: 'Şikâyet reddedildi',
            })
          }
        >
          Şikâyeti reddet
        </Button>
      </div>
      {d.dialog}
    </li>
  )
}

// ——— İtirazlar ———

function AppealList() {
  const [page, setPage] = useState(0)
  const list = useAppealQueue(page)
  return (
    <QueryBoundary query={list} what="İtirazlar">
      {(data) =>
        data.content.length === 0 ? (
          <EmptyState title="Açık itiraz yok">
            Reddedilen, gizlenen ya da kaldırılan içeriğin sahibi itiraz ettiğinde burada listelenir. İlk kararı veren kişi itirazı
            değerlendiremez.
          </EmptyState>
        ) : (
          <>
            <ul className="border-t border-ink">
              {data.content.map((a) => (
                <AppealRow key={a.id} appeal={a} />
              ))}
            </ul>
            <Pager data={data} page={page} onChange={setPage} />
          </>
        )
      }
    </QueryBoundary>
  )
}

function AppealRow({ appeal: a }: { appeal: AppealItem }) {
  const d = useDecision()
  const what = `${TARGET_LABEL[a.targetType]} ${APPEALED_LABEL[a.appealedAction] ?? 'karar'} kararına itiraz`
  return (
    <li className="grid gap-x-8 gap-y-3 border-b border-rule py-5 md:grid-cols-[minmax(0,1fr)_auto] md:items-start">
      <div className="min-w-0">
        <p className="text-lg font-semibold">{what}</p>
        <p className="mt-0.5 flex flex-wrap items-baseline gap-x-4 text-sm text-ink-3">
          <span>
            {a.appellantName ? `${a.appellantName}, ${formatRelative(a.createdAt)} itiraz etti` : `${formatRelative(a.createdAt)} itiraz edildi`}
          </span>
          {postLink(a.postId)}
        </p>
        <Quote label="İtiraz">{a.statement}</Quote>
        <Quote label="İçerik">{a.targetContent}</Quote>
        <History type={a.targetType} id={a.targetId} />
      </div>
      <div className="flex items-center gap-3">
        <Button
          size="sm"
          variant="primary"
          disabled={d.busy}
          onClick={() =>
            d.ask({
              action: { kind: 'appeal', appealId: a.id, accept: true, reason: '' },
              title: 'İtirazı kabul et',
              description: 'İlk karar geri alınır ve içerik yeniden yayımlanır. İçerik sahibine bildirim gider.',
              confirmLabel: 'Kabul et',
              done: 'İtiraz kabul edildi; içerik yeniden yayında',
            })
          }
        >
          Kabul et
        </Button>
        <Button
          size="sm"
          disabled={d.busy}
          onClick={() =>
            d.ask({
              action: { kind: 'appeal', appealId: a.id, accept: false, reason: '' },
              title: 'İtirazı reddet',
              description: 'İlk karar geçerli kalır; bu karara yeniden itiraz edilemez.',
              confirmLabel: 'Reddet',
              destructive: true,
              done: 'İtiraz reddedildi',
            })
          }
        >
          Reddet
        </Button>
      </div>
      {d.dialog}
    </li>
  )
}
