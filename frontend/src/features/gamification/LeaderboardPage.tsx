import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { toApiError } from '@/lib/api/problem'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatLocal } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { useCatalog } from '@/features/auth/register/api'
import { Button } from '@/components/ui/Button'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader, Skeleton } from '@/components/ui/States'
import { BADGE_LABEL, badgeImage, useLeaderboard, type Leaderboard, type LeaderboardPeriod } from './api'

const PAGE = 20
const ROW = 'grid grid-cols-[3.5rem_minmax(0,1fr)_auto] items-center gap-x-5 py-4 sm:grid-cols-[6rem_minmax(0,1fr)_12rem_8rem]'

/**
 * Liderlik tablosu (F-73): dönem ya da tüm zamanlar, isteğe bağlı fakülte. İlk üç dev rakamla;
 * kendi satırınız topluluk çizgisiyle işaretli. Sağda sizin durumunuz.
 */
export function LeaderboardPage() {
  usePageTitle('Liderlik tablosu')
  const [params, setParams] = useSearchParams()
  const period: LeaderboardPeriod = params.get('donem') === 'tum' ? 'ALL_TIME' : 'TERM'
  const facultyId = params.get('fakulte')
  const [limit, setLimit] = useState(PAGE)
  const board = useLeaderboard(period, facultyId, limit)
  const catalog = useCatalog()
  const error = board.error ? toApiError(board.error) : null

  const set = (key: string, value: string | null) => {
    const next = new URLSearchParams(params)
    if (value) next.set(key, value)
    else next.delete(key)
    setParams(next, { replace: true })
    setLimit(PAGE)
  }

  const d = board.data
  const meta =
    d?.period === 'TERM' && d.termLabel
      ? `${d.termLabel}${d.from && d.to ? `, ${formatLocal(d.from, 'short')} – ${formatLocal(d.to, 'short')}` : ''}`
      : d?.period === 'ALL_TIME'
        ? 'Tüm zamanlarda toplanan puanlar.'
        : undefined

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader title="Liderlik tablosu" meta={meta} />

      <div className="mt-8 flex flex-wrap items-center justify-between gap-x-8 gap-y-2 border-y border-rule">
        <nav aria-label="Dönem">
          <ul className="-mx-3 flex">
            {(
              [
                ['TERM', 'Bu dönem'],
                ['ALL_TIME', 'Tüm zamanlar'],
              ] as [LeaderboardPeriod, string][]
            ).map(([p, label]) => (
              <li key={p}>
                <button
                  type="button"
                  aria-pressed={period === p}
                  onClick={() => set('donem', p === 'ALL_TIME' ? 'tum' : null)}
                  className={cn('relative flex h-12 items-center px-3 text-md font-semibold transition-colors', period === p ? 'text-ink' : 'text-ink-3 hover:text-ink')}
                >
                  {label}
                  {period === p && <span aria-hidden className="absolute inset-x-3 -bottom-px h-[3px] bg-topluluk" />}
                </button>
              </li>
            ))}
          </ul>
        </nav>
        <label className="flex w-full items-center gap-3 pb-2 text-md font-semibold text-ink-3 sm:w-auto sm:pb-0">
          Fakülte
          <span className="min-w-0 flex-1 sm:w-64 sm:flex-none">
            <Select value={facultyId ?? ''} onChange={(e) => set('fakulte', e.target.value || null)} disabled={!catalog.data} className="h-10">
              <option value="">Tüm fakülteler</option>
              {catalog.data?.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </Select>
          </span>
        </label>
      </div>

      <div className="mt-10 grid gap-20 lg:grid-cols-12 lg:gap-10">
        <div className="min-w-0 lg:col-span-8">
          {board.isPending ? (
            <Skeleton rows={6} />
          ) : error?.code === 'NO_TERM' ? (
            <EmptyState title="Şu an tanımlı bir dönem yok" action={<Button onClick={() => set('donem', 'tum')}>Tüm zamanlara geç</Button>}>
              Dönem başlayınca bu sekmede dönemin sıralaması görünür.
            </EmptyState>
          ) : error ? (
            <Notice
              variant="line"
              tone="warning"
              title="Liderlik tablosu yüklenemedi"
              action={
                <Button size="sm" onClick={() => void board.refetch()}>
                  Tekrar dene
                </Button>
              }
            >
              {error.code === 'LEADERBOARD_UNAVAILABLE' ? 'Tablo şu an hazırlanıyor. Biraz sonra tekrar deneyin.' : error.message}
            </Notice>
          ) : d && d.entries.length === 0 ? (
            <EmptyState title="Henüz sıralama yok">Toplulukta puan kazanan öğrenciler burada sıralanır.</EmptyState>
          ) : d ? (
            <Table board={d} canMore={d.entries.length >= limit} loadingMore={board.isFetching} onMore={() => setLimit((n) => n + PAGE)} />
          ) : null}
        </div>
        <aside className="lg:col-span-4">{d && <Standing board={d} />}</aside>
      </div>
    </div>
  )
}

function Table({ board, canMore, loadingMore, onMore }: { board: Leaderboard; canMore: boolean; loadingMore: boolean; onMore: () => void }) {
  return (
    <>
      <div aria-hidden className={`${ROW} hidden border-b-2 border-ink py-2 text-sm text-ink-3 sm:grid`}>
        <span>Sıra</span>
        <span>Öğrenci</span>
        <span>Rozetler</span>
        <span className="text-right">Puan</span>
      </div>
      <ol>
        {board.entries.map((e) => {
          const top = e.rank <= 3
          return (
            <li key={`${e.rank}-${e.displayName}`} className={cn('relative border-b border-rule', e.me && 'bg-sunken')}>
              {e.me && <span aria-hidden className="absolute inset-y-0 left-0 w-[4px] bg-topluluk" />}
              <div className={cn(ROW, e.me && 'pl-3')}>
                <span className={cn('tabular leading-none font-heavy', top ? 'text-5xl sm:text-6xl' : 'text-2xl text-ink-2')}>{e.rank}</span>
                <span className="min-w-0">
                  <span className={cn('block truncate', top ? 'text-2xl font-heavy' : 'text-lg font-semibold')}>{e.displayName}</span>
                  {e.me && <span className="text-sm font-semibold text-topluluk">Siz</span>}
                </span>
                <span className="hidden flex-wrap gap-1.5 sm:flex">
                  {e.badges.slice(0, 5).map((b) => (
                    <BadgeIcon key={b} type={b} />
                  ))}
                  {e.badges.length > 5 && <span className="self-center text-sm text-ink-3">+{e.badges.length - 5}</span>}
                </span>
                <span className={cn('tabular text-right leading-none font-heavy', top ? 'text-3xl' : 'text-xl')}>
                  {formatNumber(e.points)}
                  <span className="sr-only"> puan</span>
                </span>
              </div>
            </li>
          )
        })}
      </ol>
      {canMore && (
        <div className="mt-8">
          <Button loading={loadingMore} onClick={onMore}>
            Daha fazla göster
          </Button>
        </div>
      )}
    </>
  )
}

/** Rozet: görsel herkese açık uçtan; yüklenemezse adının baş harfi. Adı ipucu ve ekran okuyucu metni. */
function BadgeIcon({ type }: { type: string }) {
  const [failed, setFailed] = useState(false)
  const name = BADGE_LABEL[type] ?? type
  return failed ? (
    <span title={name} className="grid size-7 place-items-center border border-rule text-xs font-heavy text-ink-2">
      {name[0]}
      <span className="sr-only">{name}</span>
    </span>
  ) : (
    <img src={badgeImage(type)} alt={name} title={name} onError={() => setFailed(true)} className="size-7 object-contain" />
  )
}

function Standing({ board }: { board: Leaderboard }) {
  const m = board.me
  return (
    <Panel title="Sizin durumunuz">
      <dl>
        <div>
          <dt className="text-sm text-ink-3">Sıra</dt>
          <dd className="tabular mt-1 text-6xl leading-none font-heavy">{m.rank ?? '–'}</dd>
        </div>
        <div className="mt-6 border-t border-rule pt-4">
          <dt className="text-sm text-ink-3">Puan</dt>
          <dd className="tabular text-3xl font-heavy">{formatNumber(m.points)}</dd>
        </div>
      </dl>
      <p className="mt-6 text-md text-ink-2">
        {!m.visible
          ? 'Tabloda görünmüyorsunuz; sıranızı yalnız siz görürsünüz.'
          : m.rank
            ? 'Adınız tabloda seçtiğiniz biçimde görünüyor.'
            : 'Bu dönem henüz puan kazanmadınız. Toplulukta soru sormak ve cevaplamak puan kazandırır.'}
      </p>
      <Link to="/settings/leaderboard" className="mt-3 inline-block text-md font-semibold underline-offset-4 hover:underline">
        Görünürlük ayarları
      </Link>
    </Panel>
  )
}
