import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { Paperclip } from 'lucide-react'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatRelative } from '@/lib/time'
import { Badge } from '@/components/ui/Badge'
import { POST_STATUS, postKindLabel, type Post } from './api'

const ROW = 'grid gap-x-8 gap-y-2 py-6 sm:grid-cols-[9rem_minmax(0,1fr)_7rem]'

/** Satırdaki renk çizgisi: alanı anlatır (kulüp, ders); kampüs duyurusu kuruma ait, nötr. */
const lineOf = (p: Post) =>
  p.official && p.publisherType === 'CLUB'
    ? 'bg-kulup'
    : p.official && p.publisherType === 'COURSE'
      ? 'bg-ders'
      : p.official && p.publisherType === 'CAMPUS'
        ? 'bg-ink'
        : 'bg-topluluk'

function excerpt(text: string) {
  const flat = text.replace(/\s+/g, ' ').trim()
  return flat.length > 220 ? `${flat.slice(0, 220).trimEnd()}…` : flat
}

/**
 * Gönderi satırı: solda tür (resmî duyuruda yayımlayan), ortada başlık, özet ve künye, sağda beğeni ve yorum sayısı.
 * `hideCourse`: ders sayfasında ders adı yazılmaz. `actions`: satırın bağlantısı dışında kalan düğmeler (ör. Gizle).
 */
export function PostRow({ post: p, showStatus = false, hideCourse = false, actions }: { post: Post; showStatus?: boolean; hideCourse?: boolean; actions?: ReactNode }) {
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
            {p.courseLabel && !p.official && !hideCourse && <span>{p.courseLabel}</span>}
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
      {actions && <div className="pb-4 sm:pl-[calc(9rem+2rem)]">{actions}</div>}
    </li>
  )
}
