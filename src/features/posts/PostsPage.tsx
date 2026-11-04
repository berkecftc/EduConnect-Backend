import { Link, useSearchParams } from 'react-router-dom'
import { Paperclip } from 'lucide-react'
import { hasRole, useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatRelative } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { Panel } from '@/components/ui/Panel'
import { EmptyState, PageHeader, QueryBoundary } from '@/components/ui/States'
import { APPEAL_STATUS, POST_STATUS, postKindLabel, useFeed, useMyAppeals, type FeedFilter, type Post, type PublisherType } from './api'
import { usePublishers } from './publishers'

type View = { key: string; label: string; filter: FeedFilter }

/** Duyurular görünümünde yayımlayana göre daraltma. */
const SOURCES: { key: string; label: string; type?: PublisherType }[] = [
  { key: 'hepsi', label: 'Hepsi' },
  { key: 'kulup', label: 'Kulüpler', type: 'CLUB' },
  { key: 'ders', label: 'Dersler', type: 'COURSE' },
  { key: 'kampus', label: 'Kampüs', type: 'CAMPUS' },
]

/** Satırdaki renk çizgisi: alanı anlatır (kulüp, ders); kampüs duyurusu kuruma ait, nötr. */
const lineOf = (p: Post) =>
  p.official && p.publisherType === 'CLUB'
    ? 'bg-kulup'
    : p.official && p.publisherType === 'COURSE'
      ? 'bg-ders'
      : p.official && p.publisherType === 'CAMPUS'
        ? 'bg-ink'
        : 'bg-topluluk'

const VIEWS: View[] = [
  { key: 'tumu', label: 'Tümü', filter: { list: 'all' } },
  { key: 'sorular', label: 'Sorular', filter: { list: 'all', category: 'SORU' } },
  { key: 'notlar', label: 'Ders notları', filter: { list: 'all', category: 'DERS_NOTU' } },
  { key: 'genel', label: 'Genel', filter: { list: 'all', category: 'GENEL' } },
  { key: 'duyurular', label: 'Duyurular', filter: { list: 'all', official: true } },
  { key: 'kaydedilenler', label: 'Kaydedilenler', filter: { list: 'saved' } },
  { key: 'benim', label: 'Gönderilerim', filter: { list: 'mine' } },
]

const ROW = 'grid gap-x-8 gap-y-2 py-6 sm:grid-cols-[9rem_minmax(0,1fr)_7rem]'

/**
 * Akış (F-67): kategori ve liste süzgeçleri; satırda tür, başlık, özet, yazar ve sayılar. Gönderi yazmak öğrencilere,
 * resmî duyuru yazmak kulüp, ders ya da kampüs adına yetkisi olanlara açık.
 */
export function PostsPage() {
  usePageTitle('Akış')
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')
  const { publishers } = usePublishers()
  const writes = student || publishers.length > 0
  const [params, setParams] = useSearchParams()
  const view = VIEWS.find((v) => v.key === params.get('goster')) ?? VIEWS[0]!
  const official = !!view.filter.official
  const source = SOURCES.find((s) => s.key === params.get('yayinci')) ?? SOURCES[0]!
  const feed = useFeed(official ? { ...view.filter, publisherType: source.type } : view.filter)
  const mine = view.filter.list === 'mine'

  const setView = (key: string) => {
    const next = new URLSearchParams(params)
    next.delete('yayinci')
    if (key === 'tumu') next.delete('goster')
    else next.set('goster', key)
    setParams(next, { replace: true })
  }
  const setSource = (key: string) => {
    const next = new URLSearchParams(params)
    if (key === 'hepsi') next.delete('yayinci')
    else next.set('yayinci', key)
    setParams(next, { replace: true })
  }

  return (
    <div className="mx-auto max-w-[80rem]">
      <PageHeader
        title="Akış"
        meta="Sorular, ders notları ve duyurular. Katkılarınız puan kazandırır."
        actions={
          writes ? (
            <Button asChild variant="primary">
              <Link to="/posts/new">{student ? 'Gönderi yaz' : 'Duyuru yaz'}</Link>
            </Button>
          ) : undefined
        }
      />

      <nav aria-label="Akış süzgeçleri" className="mt-8 border-y border-rule">
        <ul className="-mx-3 flex overflow-x-auto">
          {VIEWS.filter((v) => writes || v.key !== 'benim').map((v, i) => (
            <li key={v.key} className={cn(i === 5 && 'ml-auto')}>
              <button
                type="button"
                aria-pressed={view.key === v.key}
                onClick={() => setView(v.key)}
                className={cn('relative flex h-12 items-center px-3 text-md font-semibold whitespace-nowrap transition-colors', view.key === v.key ? 'text-ink' : 'text-ink-3 hover:text-ink')}
              >
                {v.label}
                {view.key === v.key && <span aria-hidden className="absolute inset-x-3 -bottom-px h-[3px] bg-topluluk" />}
              </button>
            </li>
          ))}
        </ul>
      </nav>

      {official && (
        <ul aria-label="Duyuruyu yayımlayan" className="mt-4 flex flex-wrap gap-x-5 gap-y-1">
          {SOURCES.map((s) => (
            <li key={s.key}>
              <button
                type="button"
                aria-pressed={source.key === s.key}
                onClick={() => setSource(s.key)}
                className={cn('text-sm font-semibold underline-offset-4 hover:underline', source.key === s.key ? 'text-ink underline' : 'text-ink-3')}
              >
                {s.label}
              </button>
            </li>
          ))}
        </ul>
      )}

      <div className={cn('mt-8', mine && 'grid gap-20 lg:grid-cols-12 lg:gap-10')}>
        <div className={cn(mine && 'min-w-0 lg:col-span-8')}>
          <QueryBoundary query={feed} what="Akış" skeletonRows={5}>
            {(data) => {
              const posts = data.pages.flatMap((p) => p.content)
              if (posts.length === 0) return <Empty view={view} student={student} />
              return (
                <>
                  <ul className="border-t-2 border-ink">
                    {posts.map((p) => (
                      <PostRow key={p.id} post={p} showStatus={mine} />
                    ))}
                  </ul>
                  {feed.hasNextPage && (
                    <div className="mt-8">
                      <Button loading={feed.isFetchingNextPage} onClick={() => void feed.fetchNextPage()}>
                        Daha eski gönderiler
                      </Button>
                    </div>
                  )}
                </>
              )
            }}
          </QueryBoundary>
        </div>
        {mine && (
          <aside className="lg:col-span-4">
            <Appeals />
          </aside>
        )}
      </div>
    </div>
  )
}

function Empty({ view, student }: { view: View; student: boolean }) {
  const text: Record<string, [string, string]> = {
    kaydedilenler: ['Kaydettiğiniz gönderi yok', 'Gönderi sayfasındaki "Kaydet" ile ders notlarını ve soruları buraya ekleyebilirsiniz.'],
    benim: ['Henüz gönderiniz yok', 'Bir soru sorun ya da ders notu paylaşın; cevaplar ve beğeniler puan kazandırır.'],
    duyurular: ['Resmî duyuru yok', 'Kulüplerin, derslerin ve kampüs birimlerinin duyuruları burada görünür.'],
  }
  const [title, body] = text[view.key] ?? ['Bu süzgeçte gönderi yok', 'İlk gönderiyi siz yazabilirsiniz.']
  return (
    <EmptyState
      title={title}
      action={
        student && view.key !== 'duyurular' && view.key !== 'kaydedilenler' ? (
          <Button asChild variant="primary">
            <Link to="/posts/new">Gönderi yaz</Link>
          </Button>
        ) : undefined
      }
    >
      {body}
    </EmptyState>
  )
}

function excerpt(text: string) {
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > 220 ? `${flat.slice(0, 220).trimEnd()}…` : flat
}

/** Satır: solda tür (resmî duyuruda yayımlayan), ortada başlık, özet ve künye, sağda beğeni ve yorum sayısı. */
function PostRow({ post: p, showStatus }: { post: Post; showStatus: boolean }) {
  const status = POST_STATUS[p.status]
  const answered = p.category === 'SORU' && !!p.acceptedCommentId
  return (
    <li className="border-b border-rule">
      <Link to={`/posts/${p.id}`} className={cn(ROW, 'row-fill group')}>
        <span className="flex flex-wrap items-baseline gap-x-3 sm:block">
          <span className="flex items-center gap-2 text-md font-semibold">
            <span aria-hidden className={cn('h-4 w-[3px] shrink-0', lineOf(p))} />
            {postKindLabel(p)}
          </span>
          <span className="text-sm text-ink-3 sm:mt-1 sm:block">{formatRelative(p.createdAt)}</span>
        </span>
        <span className="min-w-0">
          <span className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
            <span className="text-xl leading-snug font-semibold text-balance transition-transform duration-300 ease-rail group-hover:translate-x-1">{p.title}</span>
            {answered && <Badge tone="success">Cevaplandı</Badge>}
            {showStatus && status && <Badge tone={status.tone}>{status.label}</Badge>}
          </span>
          <span className="mt-1 line-clamp-2 block max-w-[72ch] text-md text-ink-2">{excerpt(p.content)}</span>
          <span className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-sm text-ink-3">
            <span className={p.official ? 'font-semibold text-ink' : undefined}>{p.official ? (p.publisherName ?? p.authorName) : p.authorName}</span>
            {p.courseLabel && !p.official && <span>{p.courseLabel}</span>}
            {p.attachmentName && (
              <span className="flex items-center gap-1">
                <Paperclip className="size-3.5" aria-hidden />
                Ek dosya
              </span>
            )}
          </span>
        </span>
        <span className="flex gap-6 sm:flex-col sm:items-end sm:gap-1 sm:text-right">
          <span className="text-sm text-ink-2">
            <span className="tabular text-lg font-heavy text-ink">{formatNumber(p.commentCount)}</span> yorum
          </span>
          <span className="text-sm text-ink-2">
            <span className="tabular text-lg font-heavy text-ink">{formatNumber(p.likeCount)}</span> beğeni
          </span>
        </span>
      </Link>
    </li>
  )
}

/** İtirazlarım (F-70): Gönderilerim görünümünde yan sütunda. */
function Appeals() {
  const appeals = useMyAppeals()
  return (
    <Panel title="İtirazlarım">
      <QueryBoundary query={appeals} what="İtirazlar" skeletonRows={2}>
        {(list) =>
          list.length === 0 ? (
            <p className="text-ink-3">İtirazınız yok. Reddedilen ya da gizlenen içeriğinizin sayfasında "İtiraz et" ile karara itiraz edebilirsiniz.</p>
          ) : (
            <ul>
              {list.map((a) => (
                <li key={a.id} className="border-b border-rule py-3.5 first:pt-0">
                  <p className="flex flex-wrap items-baseline gap-x-3">
                    <span className="font-semibold">{a.targetType === 'POST' ? 'Gönderi' : 'Yorum'}</span>
                    <Badge tone={APPEAL_STATUS[a.status].tone}>{APPEAL_STATUS[a.status].label}</Badge>
                  </p>
                  <p className="mt-1 line-clamp-2 text-md text-ink-2">{a.statement}</p>
                  {a.decisionNote && <p className="mt-1 text-sm text-ink-2">Karar notu: {a.decisionNote}</p>}
                  {a.postId && (
                    <Link to={`/posts/${a.postId}`} className="mt-1 inline-block text-sm font-semibold underline-offset-4 hover:underline">
                      İçeriğe git
                    </Link>
                  )}
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
    </Panel>
  )
}
