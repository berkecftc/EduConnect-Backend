import { useSearchParams, Link } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { cn } from '@/lib/cn'
import { formatLocal, localStamp, nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import {
  CATEGORY_META,
  isReminder,
  notificationShortcut,
  useMarkAllRead,
  useNotifications,
  useOpenNotification,
  useUnreadCount,
  type AppNotification,
} from './api'

const ROW = 'relative grid grid-cols-[3.5rem_minmax(0,1fr)] gap-x-5 gap-y-1 py-5 sm:grid-cols-[4.5rem_minmax(0,1fr)_auto]'

/**
 * Bildirimler (F-74, F-75, F-78): günlere göre; satırda saat, kategori çizgisi, başlık ve metin.
 * Satıra tıklamak okundu sayar ve bağlantıya gider; hatırlatmalarda ayrıca kısayol var.
 */
export function NotificationsPage() {
  usePageTitle('Bildirimler')
  const [params, setParams] = useSearchParams()
  const unreadOnly = params.get('goster') === 'okunmamis'
  const unread = useUnreadCount()
  const list = useNotifications(unreadOnly)
  const markAll = useMarkAllRead()
  const count = unread.data ?? 0

  const setFilter = (only: boolean) => {
    const next = new URLSearchParams(params)
    if (only) next.set('goster', 'okunmamis')
    else next.delete('goster')
    setParams(next, { replace: true })
  }

  return (
    <div className="mx-auto max-w-[64rem]">
      <PageHeader
        title="Bildirimler"
        meta={unread.data === undefined ? undefined : count > 0 ? `${count} okunmamış bildirim.` : 'Okunmamış bildiriminiz yok.'}
        actions={
          <>
            {count > 0 && (
              <Button
                loading={markAll.isPending}
                onClick={() =>
                  markAll.mutate(undefined, {
                    onSuccess: (n) => toast.success(`${n} bildirim okundu sayıldı`),
                    onError: (e) => toast.error(toApiError(e).message),
                  })
                }
              >
                Tümünü okundu say
              </Button>
            )}
            <Button asChild variant="ghost">
              <Link to="/settings/notifications">Bildirim ayarları</Link>
            </Button>
          </>
        }
      />

      <nav aria-label="Bildirim süzgeci" className="mt-8 border-y border-rule">
        <ul className="-mx-3 flex">
          {[
            { only: false, label: 'Tümü', n: undefined },
            { only: true, label: 'Okunmamış', n: count || undefined },
          ].map((f) => (
            <li key={f.label}>
              <button
                type="button"
                aria-pressed={unreadOnly === f.only}
                onClick={() => setFilter(f.only)}
                className={cn('relative flex h-12 items-center gap-2 px-3 text-md font-semibold transition-colors', unreadOnly === f.only ? 'text-ink' : 'text-ink-3 hover:text-ink')}
              >
                {f.label}
                {f.n !== undefined && <span className="tabular text-sm font-normal text-ink-3">{f.n}</span>}
                {unreadOnly === f.only && <span aria-hidden className="absolute inset-x-3 -bottom-px h-[3px] bg-ink" />}
              </button>
            </li>
          ))}
        </ul>
      </nav>

      <div className="mt-8">
        <QueryBoundary query={list} what="Bildirimler" skeletonRows={5}>
          {(data) => {
            const items = data.pages.flatMap((p) => p.content)
            if (items.length === 0) {
              return unreadOnly ? (
                <EmptyState title="Okunmamış bildirim yok">Yeni bir bildirim geldiğinde burada görünür.</EmptyState>
              ) : (
                <EmptyState title="Henüz bildirim yok">
                  Ödev puanları, kulüp kararları, etkinlik hatırlatmaları gibi gelişmeler burada ve zildeki sayıda görünür.
                </EmptyState>
              )
            }
            return (
              <>
                <Days items={items} />
                {list.hasNextPage && (
                  <div className="mt-8">
                    <Button loading={list.isFetchingNextPage} onClick={() => void list.fetchNextPage()}>
                      Daha eski bildirimler
                    </Button>
                  </div>
                )}
              </>
            )
          }}
        </QueryBoundary>
      </div>
    </div>
  )
}

/** Türkiye gününe göre gruplar: "Bugün", "Dün", sonra tam tarih. */
function Days({ items }: { items: AppNotification[] }) {
  const now = nowLocalIso()
  const today = now.slice(0, 10)
  const yesterday = new Date(localStamp(`${today}T00:00:00`) - 86_400_000).toISOString().slice(0, 10)
  const groups: [string, { n: AppNotification; local: string }[]][] = []
  for (const n of items) {
    const local = nowLocalIso(new Date(n.createdAt))
    const day = local.slice(0, 10)
    const last = groups[groups.length - 1]
    if (last && last[0] === day) last[1].push({ n, local })
    else groups.push([day, [{ n, local }]])
  }
  return (
    <div className="flex flex-col gap-12">
      {groups.map(([day, rows]) => {
        const label = day === today ? 'Bugün' : day === yesterday ? 'Dün' : formatLocal(`${day}T12:00:00`, 'long')
        return (
          <section key={day} aria-label={label}>
            <h2 className="border-b-2 border-ink pb-2 text-2xl font-heavy">{label}</h2>
            <ul>
              {rows.map(({ n, local }) => (
                <Row key={n.id} n={n} time={formatLocal(local, 'time')} />
              ))}
            </ul>
          </section>
        )
      })}
    </div>
  )
}

/**
 * Bildirim satırı: başlık bütün satırı kaplayan düğmedir (tıklayınca okundu + bağlantı);
 * türüne göre kısayol satırın üstünde ayrı bir düğme olarak durur.
 */
function Row({ n, time }: { n: AppNotification; time: string }) {
  const open = useOpenNotification()
  const meta = CATEGORY_META[n.category]
  const shortcut = notificationShortcut(n)
  const actionable = !!n.link || !n.read
  const reminder = isReminder(n)

  return (
    <li className={cn('border-b border-rule', actionable && 'row-fill')}>
      <div className={ROW}>
        <span className="tabular row-span-2 pt-0.5 text-md text-ink-2 sm:row-span-1">{time}</span>
        <div className="min-w-0">
          <p className="flex items-center gap-2 text-sm text-ink-2">
            <span aria-hidden className="h-3 w-[3px]" style={{ background: meta.line }} />
            {meta.label}
            {reminder && <span className="text-ink-3">· Hatırlatma</span>}
          </p>
          {actionable ? (
            <button
              type="button"
              onClick={() => open(n)}
              className={cn(
                'mt-1 block text-left text-lg after:absolute after:inset-0 after:content-[""]',
                n.read ? 'text-ink-2' : 'font-semibold text-ink',
              )}
            >
              {n.title}
              {!n.link && <span className="sr-only">, okundu say</span>}
            </button>
          ) : (
            <p className={cn('mt-1 text-lg', n.read ? 'text-ink-2' : 'font-semibold')}>{n.title}</p>
          )}
          {n.body && <p className="mt-0.5 max-w-[64ch] text-md text-ink-2">{n.body}</p>}
          {shortcut && (
            <button
              type="button"
              onClick={() => open(n, shortcut.to)}
              className="relative z-10 mt-2 text-sm font-semibold underline underline-offset-4 hover:text-ink"
            >
              {shortcut.label}
            </button>
          )}
        </div>
        <span className="col-start-2 sm:col-start-auto">{!n.read && <Badge tone="info">Yeni</Badge>}</span>
      </div>
    </li>
  )
}
