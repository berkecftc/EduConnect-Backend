import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import { downloadFile } from '@/lib/api/client'
import { toApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { formatNumber } from '@/lib/format'
import { formatInstant, formatRelative } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Badge } from '@/components/ui/Badge'
import { Button } from '@/components/ui/Button'
import { ConfirmDialog } from '@/components/ui/ConfirmDialog'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { RevealTitle } from '@/components/ui/RevealTitle'
import { QueryBoundary } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import {
  APPEALABLE,
  COMMENT_STATUS,
  POST_STATUS,
  postKindLabel,
  publisherPath,
  useAcceptAnswer,
  useAddComment,
  useComments,
  useDeleteComment,
  useDeletePost,
  usePost,
  useToggleBookmark,
  useToggleLike,
  type Post,
  type PostComment,
} from './api'
import { AppealButton, HideButton, ReportButton } from './ContentActions'
import { PersonLink } from '@/features/people/PersonLink'

const COMMENT_MAX = 2000
const LINK = 'text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline'

/** Resmî duyuruda yayımlayan: kulübe ya da derse bağlantı; kampüs biriminin sayfası yok. */
function Publisher({ post: p }: { post: Post }) {
  const name = p.publisherName ?? p.authorName
  const path = publisherPath(p)
  return path ? (
    <Link to={path} className="font-semibold text-ink underline-offset-4 hover:underline">
      {name}
    </Link>
  ) : (
    <span className="font-semibold text-ink">{name}</span>
  )
}

/** Gönderi sayfası (F-67…F-72). */
export function PostPage() {
  const { postId = '' } = useParams()
  const post = usePost(postId)
  usePageTitle(post.data?.title ?? 'Gönderi')
  return (
    <div className="mx-auto max-w-[72rem]">
      <QueryBoundary query={post} what="Gönderi" skeletonRows={5}>
        {(p) => <PostBody post={p} />}
      </QueryBoundary>
    </div>
  )
}

function PostBody({ post: p }: { post: Post }) {
  const session = useSession()
  const navigate = useNavigate()
  const own = !!session && session.userId === p.authorId
  const like = useToggleLike(p)
  const bookmark = useToggleBookmark(p)
  const remove = useDeletePost()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const status = POST_STATUS[p.status]
  const locked = p.status === 'HIDDEN' || p.status === 'REMOVED'
  const target = { kind: 'post' as const, id: p.id, postId: p.id }

  return (
    <>
      <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-md">
        <span aria-hidden className="h-5 w-[4px] bg-topluluk" />
        <Link to={`/posts?goster=${p.category === 'SORU' ? 'sorular' : p.category === 'DERS_NOTU' ? 'notlar' : p.official ? 'duyurular' : 'genel'}`} className="font-semibold underline-offset-4 hover:underline">
          {postKindLabel(p)}
        </Link>
        {p.courseLabel && !p.official && <span className="text-ink-2">{p.courseLabel}</span>}
      </p>
      <RevealTitle text={p.title} className="mt-1 text-4xl sm:text-5xl" />
      <p className="mt-4 flex flex-wrap gap-x-4 gap-y-1 text-md text-ink-2">
        {p.official ? (
          <Publisher post={p} />
        ) : (
          <PersonLink id={p.authorId} className="font-semibold text-ink">
            {p.authorName}
          </PersonLink>
        )}
        {!p.official && p.authorDepartment && <span>{p.authorDepartment}</span>}
        <span>{formatInstant(p.createdAt, 'datetime')}</span>
      </p>

      {own && status && (
        <Notice variant="line" tone={status.tone === 'danger' ? 'danger' : 'warning'} title={status.label} className="mt-8">
          {p.reviewNote
            ? `Gerekçe: ${p.reviewNote}`
            : p.status === 'AWAITING_APPROVAL'
              ? 'Kulüp başkanı onaylayınca akışta yayımlanır; o zamana kadar düzenleyebilirsiniz.'
              : p.status === 'PENDING'
                ? 'Gönderiniz otomatik kontrolden geçtikten sonra yayımlanır.'
                : 'Gönderiniz yalnız size görünüyor.'}
          {APPEALABLE.has(p.status) && (
            <span className="mt-2 block">
              <AppealButton target={target} />
            </span>
          )}
        </Notice>
      )}

      <div className="mt-10 grid gap-14 lg:grid-cols-12 lg:gap-10">
        <article className="min-w-0 lg:col-span-8">
          <div className="max-w-[68ch] text-lg leading-relaxed whitespace-pre-line">{p.content}</div>
          {p.attachmentName && (
            <button
              type="button"
              onClick={() => void downloadFile(`/posts/${p.id}/attachment`, p.attachmentName!).catch((e) => toast.error(toApiError(e).message))}
              className="row-fill mt-8 flex w-full max-w-[40rem] items-center justify-between gap-4 border-y border-rule px-1 py-3.5 text-left"
            >
              <span className="min-w-0">
                <span className="block truncate font-semibold">{p.attachmentName}</span>
                <span className="block text-sm text-ink-3">Ek dosya</span>
              </span>
              <span className="flex shrink-0 items-center gap-2 text-sm font-semibold">
                <Download className="size-4" aria-hidden />
                İndir
              </span>
            </button>
          )}
        </article>

        <aside className="lg:col-span-4">
          <div className="flex flex-col gap-4 border-t-2 border-ink pt-5">
            <div className="grid grid-cols-2">
              <button
                type="button"
                aria-pressed={p.liked}
                disabled={p.status !== 'PUBLISHED'}
                onClick={() => like.mutate(undefined, { onError: (e) => toast.error(toApiError(e).message) })}
                className="row-fill flex flex-col items-start px-1 py-2 text-left disabled:opacity-60"
              >
                <span className={cn('tabular text-4xl leading-none font-heavy', p.liked && 'text-topluluk')}>{formatNumber(p.likeCount)}</span>
                <span className="mt-1 text-md font-semibold">{p.liked ? 'Beğendiniz' : 'Beğen'}</span>
              </button>
              <a href="#yorumlar" className="row-fill flex flex-col items-start border-l border-rule px-4 py-2">
                <span className="tabular text-4xl leading-none font-heavy">{formatNumber(p.commentCount)}</span>
                <span className="mt-1 text-md font-semibold">{p.category === 'SORU' ? 'cevap' : 'yorum'}</span>
              </a>
            </div>
            <div className="flex flex-wrap gap-x-5 gap-y-2 border-t border-rule pt-4">
              <button
                type="button"
                aria-pressed={p.bookmarked}
                onClick={() =>
                  bookmark.mutate(undefined, {
                    onSuccess: (r) => toast.success(r.bookmarked ? 'Kaydedilenlere eklendi' : 'Kaydedilenlerden çıkarıldı'),
                    onError: (e) => toast.error(toApiError(e).message),
                  })
                }
                className={cn(LINK, p.bookmarked && 'text-ink')}
              >
                {p.bookmarked ? 'Kaydedildi' : 'Kaydet'}
              </button>
              {own ? (
                <>
                  {!locked && (
                    <Link to={`/posts/${p.id}/edit`} className={LINK}>
                      Düzenle
                    </Link>
                  )}
                  <button type="button" onClick={() => setConfirmDelete(true)} className={cn(LINK, 'hover:text-danger')}>
                    Sil
                  </button>
                </>
              ) : (
                <ReportButton target={target} />
              )}
            </div>
          </div>
        </aside>
      </div>

      <div className="mt-16 max-w-[56rem]">
        <Comments post={p} own={own} />
      </div>

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title="Gönderi silinsin mi?"
        description="Gönderi, yorumları ve beğenileriyle birlikte kaldırılır. Bu işlem geri alınamaz."
        confirmLabel="Sil"
        destructive
        loading={remove.isPending}
        onConfirm={() =>
          remove.mutate(p.id, {
            onSuccess: () => {
              toast.success('Gönderi silindi')
              navigate('/posts?goster=benim')
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </>
  )
}

/** Yorumlar: kabul edilen cevap başta; yazar kendi bekleyen/reddedilen yorumunu durumuyla görür (F-68, F-72). */
function Comments({ post: p, own }: { post: Post; own: boolean }) {
  const comments = useComments(p.id)
  const add = useAddComment(p.id)
  const [text, setText] = useState('')
  const canComment = p.status === 'PUBLISHED' && !p.commentsDisabled

  const send = (e: React.FormEvent) => {
    e.preventDefault()
    if (!text.trim()) return
    add.mutate(
      { content: text },
      {
        onSuccess: (c) => {
          setText('')
          toast.success(c.status === 'PUBLISHED' ? 'Yorumunuz eklendi' : 'Yorumunuz kontrol edildikten sonra yayımlanacak')
        },
        onError: (err) => toast.error(toApiError(err).message),
      },
    )
  }

  return (
    <Panel anchor="yorumlar" title={p.category === 'SORU' ? 'Cevaplar' : 'Yorumlar'}>
      {canComment ? (
        <form onSubmit={send} className="flex flex-col gap-3">
          <label htmlFor="yorum" className="sr-only">
            {p.category === 'SORU' ? 'Cevabınız' : 'Yorumunuz'}
          </label>
          <Textarea
            id="yorum"
            placeholder={p.category === 'SORU' ? 'Cevabınızı yazın' : 'Yorumunuzu yazın'}
            maxLength={COMMENT_MAX}
            valueLength={text.length}
            value={text}
            onChange={(e) => setText(e.target.value)}
            rows={3}
          />
          <div>
            <Button type="submit" variant="primary" loading={add.isPending} disabled={!text.trim()}>
              {p.category === 'SORU' ? 'Cevapla' : 'Yorum yap'}
            </Button>
          </div>
        </form>
      ) : (
        <p className="text-ink-3">{p.commentsDisabled ? 'Bu gönderi yorumlara kapalı.' : 'Gönderi yayımlanınca yorum yazılabilir.'}</p>
      )}

      <div className="mt-8">
        <QueryBoundary query={comments} what="Yorumlar" skeletonRows={3}>
          {(data) => {
            const list = data.pages.flatMap((pg) => pg.content)
            const sorted = [...list].sort((a, b) => Number(b.id === p.acceptedCommentId) - Number(a.id === p.acceptedCommentId))
            if (sorted.length === 0) return <p className="text-ink-3">{p.category === 'SORU' ? 'Henüz cevap yok. İlk cevabı siz yazın.' : 'Henüz yorum yok.'}</p>
            return (
              <>
                <ul className="border-t border-rule">
                  {sorted.map((c) => (
                    <CommentItem key={c.id} comment={c} post={p} postOwner={own} />
                  ))}
                </ul>
                {comments.hasNextPage && (
                  <div className="mt-6">
                    <Button loading={comments.isFetchingNextPage} onClick={() => void comments.fetchNextPage()}>
                      Daha fazla yorum
                    </Button>
                  </div>
                )}
              </>
            )
          }}
        </QueryBoundary>
      </div>
    </Panel>
  )
}

function CommentItem({ comment: c, post: p, postOwner, reply = false }: { comment: PostComment; post: Post; postOwner: boolean; reply?: boolean }) {
  const session = useSession()
  const mine = !!session && session.userId === c.authorId
  const accept = useAcceptAnswer(p.id)
  const add = useAddComment(p.id)
  const del = useDeleteComment(p.id)
  const [replying, setReplying] = useState(false)
  const [text, setText] = useState('')
  const [confirmDelete, setConfirmDelete] = useState(false)
  const accepted = p.acceptedCommentId === c.id
  const published = c.status === 'PUBLISHED'
  const status = COMMENT_STATUS[c.status]
  const target = { kind: 'comment' as const, id: c.id, postId: p.id }
  const canAccept = !reply && postOwner && p.category === 'SORU' && published && !mine

  return (
    <li className={cn(reply ? 'border-t border-rule py-4 pl-5' : 'border-b border-rule py-5', accepted && !reply && 'relative pl-5')}>
      {accepted && !reply && <span aria-hidden className="absolute inset-y-4 left-0 w-[4px] bg-success" />}
      <p className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <PersonLink id={c.authorId} className="font-semibold">
          {c.authorName ?? 'Kullanıcı'}
        </PersonLink>
        <span className="text-sm text-ink-3">{formatRelative(c.createdAt)}</span>
        {accepted && <Badge tone="success">Kabul edilen cevap</Badge>}
        {mine && status && <Badge tone={status.tone}>{status.label}</Badge>}
      </p>
      <p className="mt-1.5 max-w-[68ch] whitespace-pre-line">{c.content}</p>
      {mine && c.moderationNote && <p className="mt-1 text-sm text-ink-2">Gerekçe: {c.moderationNote}</p>}

      <div className="mt-2 flex flex-wrap gap-x-5 gap-y-1">
        {!reply && published && p.status === 'PUBLISHED' && !p.commentsDisabled && (
          <button type="button" onClick={() => setReplying((r) => !r)} className={LINK}>
            Yanıtla
          </button>
        )}
        {canAccept && (
          <button
            type="button"
            onClick={() =>
              accept.mutate(accepted ? null : c.id, {
                onSuccess: () => toast.success(accepted ? 'Cevap seçimi kaldırıldı' : 'Cevap olarak seçildi'),
                onError: (e) => toast.error(toApiError(e).message),
              })
            }
            className={cn(LINK, !accepted && 'text-success')}
          >
            {accepted ? 'Seçimi kaldır' : 'Cevap olarak seç'}
          </button>
        )}
        {mine ? (
          <>
            {APPEALABLE.has(c.status) && <AppealButton target={target} />}
            <button type="button" onClick={() => setConfirmDelete(true)} className={cn(LINK, 'hover:text-danger')}>
              Sil
            </button>
          </>
        ) : (
          published && (
            <>
              <ReportButton target={target} />
              {postOwner && <HideButton target={target} />}
            </>
          )
        )}
      </div>

      {replying && (
        <form
          className="mt-3 flex flex-col gap-2 border-l-2 border-rule pl-4"
          onSubmit={(e) => {
            e.preventDefault()
            if (!text.trim()) return
            add.mutate(
              { content: text, parentId: c.id },
              {
                onSuccess: (r) => {
                  setText('')
                  setReplying(false)
                  toast.success(r.status === 'PUBLISHED' ? 'Yanıtınız eklendi' : 'Yanıtınız kontrol edildikten sonra yayımlanacak')
                },
                onError: (err) => toast.error(toApiError(err).message),
              },
            )
          }}
        >
          <label htmlFor={`yanit-${c.id}`} className="sr-only">
            {c.authorName} yorumuna yanıt
          </label>
          <Textarea autoFocus id={`yanit-${c.id}`} maxLength={COMMENT_MAX} valueLength={text.length} value={text} onChange={(e) => setText(e.target.value)} rows={2} />
          <div className="flex items-center gap-4">
            <Button size="sm" type="submit" variant="primary" loading={add.isPending} disabled={!text.trim()}>
              Yanıtla
            </Button>
            <button type="button" onClick={() => setReplying(false)} className={LINK}>
              Vazgeç
            </button>
          </div>
        </form>
      )}

      {c.replies.length > 0 && (
        <ul className="mt-4 ml-1 border-l-2 border-rule">
          {c.replies.map((r) => (
            <CommentItem key={r.id} comment={r} post={p} postOwner={postOwner} reply />
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={confirmDelete}
        onOpenChange={setConfirmDelete}
        title={reply ? 'Yanıt silinsin mi?' : 'Yorum silinsin mi?'}
        description="Bu işlem geri alınamaz."
        confirmLabel="Sil"
        destructive
        loading={del.isPending}
        onConfirm={() =>
          del.mutate(c.id, {
            onSuccess: () => {
              toast.success('Silindi')
              setConfirmDelete(false)
            },
            onError: (e) => toast.error(toApiError(e).message),
          })
        }
      />
    </li>
  )
}
