import { useInfiniteQuery, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { toPaged, type Paged as SpringPage } from '@/lib/api/page'
import type { Publisher } from './publishers'

/** Topluluk akışı (F-67…F-72). */

export type PostCategory = 'SORU' | 'GENEL' | 'DERS_NOTU' | 'DUYURU'
export type PostStatus = 'AWAITING_APPROVAL' | 'PENDING' | 'IN_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'HIDDEN' | 'REMOVED'
export type CommentStatus = 'PENDING' | 'IN_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'HIDDEN' | 'REMOVED'
export type PublisherType = 'STUDENT' | 'CLUB' | 'COURSE' | 'CAMPUS'

export type Post = {
  id: string
  title: string
  content: string
  category: PostCategory
  status: PostStatus
  publisherType: PublisherType | null
  clubId: string | null
  courseId: string | null
  publisherName: string | null
  official: boolean
  commentsDisabled: boolean
  reviewNote: string | null
  courseLabel: string | null
  attachmentName: string | null
  acceptedCommentId: string | null
  authorId: string
  authorName: string | null
  authorDepartment: string | null
  likeCount: number
  commentCount: number
  liked: boolean
  bookmarked: boolean
  createdAt: string
  updatedAt: string | null
}

export type PostComment = {
  id: string
  postId: string
  authorId: string
  authorName: string | null
  parentCommentId: string | null
  content: string
  status: CommentStatus
  moderationNote: string | null
  replies: PostComment[]
  createdAt: string
  updatedAt: string | null
}

export const CATEGORY_LABEL: Record<PostCategory, string> = { SORU: 'Soru', GENEL: 'Genel', DERS_NOTU: 'Ders notu', DUYURU: 'Duyuru' }

/** Gönderinin türü: resmî duyuruda kimin adına olduğu da söylenir. */
export function postKindLabel(p: Pick<Post, 'official' | 'publisherType' | 'category'>): string {
  if (p.official && p.publisherType === 'CLUB') return 'Kulüp duyurusu'
  if (p.official && p.publisherType === 'COURSE') return 'Ders duyurusu'
  if (p.official && p.publisherType === 'CAMPUS') return 'Kampüs duyurusu'
  return CATEGORY_LABEL[p.category]
}

/** Yayımlayanın sayfası (kampüs biriminin sayfası yok). */
export function publisherPath(p: Pick<Post, 'official' | 'publisherType' | 'clubId' | 'courseId'>): string | null {
  if (p.official && p.publisherType === 'CLUB' && p.clubId) return `/clubs/${p.clubId}`
  if (p.official && p.publisherType === 'COURSE' && p.courseId) return `/courses/${p.courseId}`
  return null
}

/** Yazarın gördüğü durum etiketleri; yayındaki içerikte etiket yok. */
export const POST_STATUS: Partial<Record<PostStatus, { label: string; tone: 'warning' | 'danger' | 'neutral' }>> = {
  AWAITING_APPROVAL: { label: 'Başkan onayı bekliyor', tone: 'warning' },
  PENDING: { label: 'Kontrol ediliyor', tone: 'warning' },
  IN_REVIEW: { label: 'Moderatör incelemesinde', tone: 'warning' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
  HIDDEN: { label: 'Gizlendi', tone: 'neutral' },
  REMOVED: { label: 'Kaldırıldı', tone: 'danger' },
}
export const COMMENT_STATUS = POST_STATUS as Partial<Record<CommentStatus, { label: string; tone: 'warning' | 'danger' | 'neutral' }>>

/** İtiraz edilebilen durumlar (F-70); başkanın reddi itiraz değil düzenlemeyle çözülür (backend NOT_APPEALABLE der). */
export const APPEALABLE = new Set<string>(['REJECTED', 'HIDDEN', 'REMOVED'])

export const REPORT_REASONS = [
  ['BULLYING', 'Zorbalık veya hakaret'],
  ['HARASSMENT', 'Taciz'],
  ['THREAT', 'Şiddet veya tehdit'],
  ['PERSONAL_DATA', 'Kişisel veri paylaşımı'],
  ['ACADEMIC_INTEGRITY', 'Akademik dürüstlük ihlali'],
  ['SPAM', 'Reklam veya spam'],
  ['WRONG_CATEGORY', 'Yanlış kategori'],
  ['OTHER', 'Diğer'],
] as const
export type ReportReason = (typeof REPORT_REASONS)[number][0]

export type FeedFilter = { list: 'all' | 'saved' | 'mine'; category?: PostCategory; official?: boolean; publisherType?: PublisherType }

const PAGE_SIZE = 10

export const postKeys = {
  feed: (f: FeedFilter) => ['posts', 'feed', f.list, f.category ?? 'all', f.official ?? false, f.publisherType ?? 'all'] as const,
  detail: (id: string) => ['posts', id] as const,
  comments: (id: string) => ['posts', id, 'comments'] as const,
  appeals: ['posts', 'appeals', 'me'] as const,
  awaiting: (clubId: string) => ['posts', 'awaiting', clubId] as const,
}

export function useFeed(f: FeedFilter) {
  return useInfiniteQuery({
    queryKey: postKeys.feed(f),
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const url = f.list === 'saved' ? '/posts/saved' : f.list === 'mine' ? '/posts/me' : '/posts'
      const params: Record<string, unknown> = { page: pageParam, size: PAGE_SIZE }
      if (f.list === 'all') {
        if (f.category) params.category = f.category
        if (f.official) params.official = true
        if (f.publisherType) params.publisherType = f.publisherType
      }
      return toPaged((await api.get<SpringPage<Post>>(url, { params })).data)
    },
    getNextPageParam: (last) => (last.last ? undefined : last.number + 1),
  })
}

export function usePost(id: string, enabled = true) {
  return useQuery({ queryKey: postKeys.detail(id), enabled: enabled && !!id, queryFn: async () => (await api.get<Post>(`/posts/${id}`)).data })
}

export function useComments(postId: string) {
  return useInfiniteQuery({
    queryKey: postKeys.comments(postId),
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      toPaged((await api.get<SpringPage<PostComment>>(`/posts/${postId}/comments`, { params: { page: pageParam, size: 20 } })).data),
    getNextPageParam: (last) => (last.last ? undefined : last.number + 1),
  })
}

/** Gönderiyle ilgili her değişiklikten sonra ayrıntı, yorumlar ve akış tazelenir. */
function useRefresh() {
  const qc = useQueryClient()
  return (postId?: string) => {
    if (postId) {
      void qc.invalidateQueries({ queryKey: postKeys.detail(postId) })
      void qc.invalidateQueries({ queryKey: postKeys.comments(postId) })
    }
    void qc.invalidateQueries({ queryKey: ['posts', 'feed'] })
  }
}

export type PostInput = {
  title: string
  content: string
  category: PostCategory
  courseId?: string | null
  sharingDeclaration?: boolean
  /** Resmî duyuru: kimin adına; kampüs duyurusunda birimin adı. */
  publisher?: Publisher
  publisherName?: string
  commentsDisabled?: boolean
}

/** Yeni gönderinin gövdesi: duyuru yayımlayana, diğerleri öğrenci adına ve isteğe bağlı derse bağlanır. */
export function createPostBody(input: PostInput) {
  const base = { title: input.title.trim(), content: input.content.trim(), category: input.category }
  const p = input.publisher
  if (input.category === 'DUYURU' && p) {
    return {
      ...base,
      publisherType: p.type,
      clubId: p.type === 'CLUB' ? p.clubId : null,
      courseId: p.type === 'COURSE' ? p.courseId : null,
      publisherName: p.type === 'CAMPUS' ? input.publisherName?.trim() : null,
      commentsDisabled: !!input.commentsDisabled,
    }
  }
  return {
    ...base,
    publisherType: 'STUDENT' as const,
    courseId: input.courseId || null,
    sharingDeclaration: input.category === 'DERS_NOTU' ? !!input.sharingDeclaration : null,
  }
}

export function useSavePost(postId?: string) {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async (input: PostInput) => {
      if (postId) {
        const body = { title: input.title.trim(), content: input.content.trim(), category: input.category }
        return (await api.put<Post>(`/posts/${postId}`, input.category === 'DUYURU' ? { ...body, commentsDisabled: !!input.commentsDisabled } : body)).data
      }
      return (await api.post<Post>('/posts', createPostBody(input))).data
    },
    onSuccess: (p) => refresh(p.id),
  })
}

// ——— Başkan onayı ———

/** Yönettiği kulüplerde başkan onayı bekleyen duyurular (F-67); eskiden yeniye. Yetki yoksa o kulüp sessizce boş kalır. */
export function useAwaitingClubPosts(clubIds: string[]) {
  return useQueries({
    queries: clubIds.map((clubId) => ({
      queryKey: postKeys.awaiting(clubId),
      queryFn: async () => toPaged((await api.get<SpringPage<Post>>(`/posts/club/${clubId}/awaiting-approval`, { params: { size: 50 } })).data).content,
    })),
    combine: (results) => ({ posts: results.flatMap((r) => r.data ?? []), isPending: results.some((r) => r.isPending) }),
  })
}

export function useDecideClubPost() {
  const refresh = useRefresh()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { postId: string; approve: true } | { postId: string; approve: false; note: string }) =>
      (a.approve ? await api.post<Post>(`/posts/${a.postId}/approve`) : await api.post<Post>(`/posts/${a.postId}/reject`, { note: a.note.trim() })).data,
    onSuccess: (p) => {
      refresh(p.id)
      void qc.invalidateQueries({ queryKey: ['posts', 'awaiting'] })
    },
  })
}

export function useDeletePost() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async (postId: string) => {
      await api.delete(`/posts/${postId}`)
    },
    onSuccess: () => refresh(),
  })
}

/** Tek ek (en fazla 20 MB); yenisi eskisinin yerine geçer (F-71). */
export function useAttachment(postId: string) {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async (file: File | null) => {
      if (!file) return void (await api.delete(`/posts/${postId}/attachment`))
      const form = new FormData()
      form.append('file', file)
      await api.put(`/posts/${postId}/attachment`, form)
    },
    onSuccess: () => refresh(postId),
  })
}

/** Beğeni ve kaydetme: ekranda hemen değişir, yanıtla düzeltilir. */
export function useToggleLike(post: Post) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () =>
      (post.liked ? await api.delete<{ liked: boolean; likeCount: number }>(`/posts/${post.id}/like`) : await api.put<{ liked: boolean; likeCount: number }>(`/posts/${post.id}/like`)).data,
    onMutate: () => {
      qc.setQueryData<Post>(postKeys.detail(post.id), (p) => p && { ...p, liked: !p.liked, likeCount: p.likeCount + (p.liked ? -1 : 1) })
    },
    onSuccess: (r) => qc.setQueryData<Post>(postKeys.detail(post.id), (p) => p && { ...p, liked: r.liked, likeCount: r.likeCount }),
    onError: () => void qc.invalidateQueries({ queryKey: postKeys.detail(post.id) }),
    onSettled: () => void qc.invalidateQueries({ queryKey: ['posts', 'feed'] }),
  })
}

export function useToggleBookmark(post: Post) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => (await api.post<{ bookmarked: boolean }>(`/posts/${post.id}/bookmark`)).data,
    onSuccess: (r) => {
      qc.setQueryData<Post>(postKeys.detail(post.id), (p) => p && { ...p, bookmarked: r.bookmarked })
      void qc.invalidateQueries({ queryKey: ['posts', 'feed'] })
    },
  })
}

export function useAddComment(postId: string) {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async ({ content, parentId }: { content: string; parentId?: string }) =>
      (
        await api.post<PostComment>(parentId ? `/posts/comments/${parentId}/replies` : `/posts/${postId}/comments`, { content: content.trim() })
      ).data,
    onSuccess: () => refresh(postId),
  })
}

export function useDeleteComment(postId: string) {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async (commentId: string) => {
      await api.delete(`/posts/${postId}/comments/${commentId}`)
    },
    onSuccess: () => refresh(postId),
  })
}

export function useAcceptAnswer(postId: string) {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async (commentId: string | null) => {
      if (commentId) await api.put(`/posts/${postId}/accepted-answer`, { commentId })
      else await api.delete(`/posts/${postId}/accepted-answer`)
    },
    onSuccess: () => refresh(postId),
  })
}

export type Target = { kind: 'post' | 'comment'; id: string; postId: string }
const targetUrl = (t: Target, action: string) => (t.kind === 'post' ? `/posts/${t.id}/${action}` : `/posts/comments/${t.id}/${action}`)

/** Şikâyet: hassas nedenlerde yanıt `supportMessage` taşır, ekranda gösterilir (F-69). */
export function useReport() {
  return useMutation({
    mutationFn: async ({ target, reason, details }: { target: Target; reason: ReportReason; details: string }) =>
      (await api.post<{ sensitive: boolean; supportMessage: string | null; reasonLabel: string }>(targetUrl(target, 'report'), { reason, details: details.trim() || null }))
        .data,
  })
}

export function useHide() {
  const refresh = useRefresh()
  return useMutation({
    mutationFn: async ({ target, reason }: { target: Target; reason: string }) => {
      await api.post(targetUrl(target, 'hide'), { reason: reason.trim() })
    },
    onSuccess: (_d, v) => refresh(v.target.postId),
  })
}

export type Appeal = {
  id: string
  targetType: 'POST' | 'COMMENT'
  targetId: string
  postId: string | null
  statement: string
  status: 'OPEN' | 'ACCEPTED' | 'REJECTED'
  decisionNote: string | null
  createdAt: string
}

export const APPEAL_STATUS: Record<Appeal['status'], { label: string; tone: 'warning' | 'success' | 'danger' }> = {
  OPEN: { label: 'Değerlendiriliyor', tone: 'warning' },
  ACCEPTED: { label: 'Kabul edildi', tone: 'success' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
}

export function useMyAppeals(enabled = true) {
  return useQuery({ queryKey: postKeys.appeals, enabled, queryFn: async () => (await api.get<Appeal[]>('/posts/appeals/me')).data })
}

export function useAppeal() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ target, statement }: { target: Target; statement: string }) => {
      await api.post(targetUrl(target, 'appeal'), { statement: statement.trim() })
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: postKeys.appeals }),
  })
}

/** Yeni gönderiye ek yükleme (oluşturduktan hemen sonra; kanca kimliği önceden bilmediği için düz işlev). */
export async function uploadAttachment(postId: string, file: File) {
  const form = new FormData()
  form.append('file', file)
  await api.put(`/posts/${postId}/attachment`, form)
}
