import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { toPaged, type Paged } from '@/lib/api/page'

/**
 * Moderasyon (PERM_MODERATOR, F-68…F-70): incelemedeki gönderi ve yorumlar, açık şikâyetler, açık itirazlar.
 * Liste uçları post-service'in VIA_DTO sayfasıyla gelir; `toPaged` ile ortak biçime çevrilir.
 */

export type TargetType = 'POST' | 'COMMENT'

/** İnceleme kuyruğu öğesi: otomatik kontrolün karar veremediği ya da şüphelendiği içerik. */
export type QueueItem = {
  targetType: TargetType
  id: string
  postId: string
  title: string | null
  content: string
  authorId: string
  authorName: string | null
  status: string
  /** Otomatik kontrolün notu, ör. "zorbalık veya hakaret şüphesi". */
  flag: string | null
  submittedAt: string
}

export type ReportItem = {
  id: string
  targetType: TargetType
  targetId: string
  postId: string
  reason: string
  reasonLabel: string | null
  /** Taciz ve tehdit: öncelikli, listenin başında. */
  sensitive: boolean
  details: string | null
  reporterId: string
  status: string
  openReportsOnTarget: number
  targetStatus: string
  targetContent: string | null
  createdAt: string
}

export type AppealItem = {
  id: string
  targetType: TargetType
  targetId: string
  postId: string
  appellantId: string
  appellantName?: string | null
  statement: string
  appealedAction: string
  status: 'OPEN' | 'ACCEPTED' | 'REJECTED'
  decisionNote: string | null
  decidedAt: string | null
  createdAt: string
  targetContent: string | null
}

export type ModerationRecord = {
  id: string
  targetType: TargetType
  targetId: string
  postId: string
  action: string
  actorType: 'AUTOMATIC' | 'MODERATOR' | 'SCOPE_OWNER'
  actorId: string | null
  reason: string | null
  createdAt: string
}

export const ACTION_LABEL: Record<string, string> = {
  PUBLISHED: 'Yayımlandı',
  REJECTED: 'Reddedildi',
  SENT_TO_REVIEW: 'İncelemeye alındı',
  HIDDEN: 'Gizlendi',
  REMOVED: 'Kaldırıldı',
  RESTORED: 'Geri yüklendi',
  REPORT_DISMISSED: 'Şikâyet reddedildi',
  APPEAL_ACCEPTED: 'İtiraz kabul edildi',
  APPEAL_REJECTED: 'İtiraz reddedildi',
}

export const ACTOR_LABEL: Record<ModerationRecord['actorType'], string> = {
  AUTOMATIC: 'Otomatik kontrol',
  MODERATOR: 'Moderatör',
  SCOPE_OWNER: 'Kapsam sahibi',
}

/** İtiraz edilen karar: "gizleme kararına itiraz". */
export const APPEALED_LABEL: Record<string, string> = {
  REJECTED: 'reddetme',
  HIDDEN: 'gizleme',
  REMOVED: 'kaldırma',
}

export const TARGET_LABEL: Record<TargetType, string> = { POST: 'Gönderi', COMMENT: 'Yorum' }

const BASE = '/posts/moderation'
export const PAGE = 20

export const moderationKeys = {
  queue: (type: TargetType, page: number) => ['manage', 'moderation', 'queue', type, page] as const,
  reports: (page: number) => ['manage', 'moderation', 'reports', page] as const,
  appeals: (page: number) => ['manage', 'moderation', 'appeals', page] as const,
  history: (type: TargetType, id: string) => ['manage', 'moderation', 'history', type, id] as const,
}

export function useQueue(type: TargetType, page = 0, size = PAGE, enabled = true) {
  return useQuery({
    queryKey: [...moderationKeys.queue(type, page), size],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => toPaged((await api.get(`${BASE}/queue`, { params: { type, page, size } })).data) as Paged<QueueItem>,
  })
}

export function useReports(page = 0, size = PAGE, enabled = true) {
  return useQuery({
    queryKey: [...moderationKeys.reports(page), size],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => toPaged((await api.get(`${BASE}/reports`, { params: { page, size } })).data) as Paged<ReportItem>,
  })
}

export function useAppealQueue(page = 0, size = PAGE, enabled = true) {
  return useQuery({
    queryKey: [...moderationKeys.appeals(page), size],
    enabled,
    placeholderData: keepPreviousData,
    queryFn: async () => toPaged((await api.get(`${BASE}/appeals`, { params: { page, size } })).data) as Paged<AppealItem>,
  })
}

export function useModerationHistory(type: TargetType, id: string, enabled: boolean) {
  return useQuery({
    queryKey: moderationKeys.history(type, id),
    enabled,
    queryFn: async () => (await api.get<ModerationRecord[]>(`${BASE}/history`, { params: { targetType: type, targetId: id } })).data,
  })
}

const path = (type: TargetType, id: string) => `${BASE}/${type === 'POST' ? 'posts' : 'comments'}/${id}`

export type ModerationAction =
  | { kind: 'decide'; type: TargetType; id: string; approve: boolean; reason: string }
  | { kind: 'remove' | 'restore'; type: TargetType; id: string; reason: string }
  | { kind: 'dismiss'; reportId: string; reason: string }
  | { kind: 'appeal'; appealId: string; accept: boolean; reason: string }

/** Bütün moderasyon kararları; başarıda bütün kuyruklar ve sayılar tazelenir. */
export function useModerate() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: ModerationAction) => {
      const reason = a.reason.trim() || null
      switch (a.kind) {
        case 'decide':
          return void (await api.post(`${path(a.type, a.id)}/decision`, { action: a.approve ? 'APPROVE' : 'REJECT', reason }))
        case 'remove':
        case 'restore':
          return void (await api.post(`${path(a.type, a.id)}/${a.kind}`, { reason }))
        case 'dismiss':
          return void (await api.post(`${BASE}/reports/${a.reportId}/dismiss`, { reason }))
        case 'appeal':
          return void (await api.post(`${BASE}/appeals/${a.appealId}/decision`, { outcome: a.accept ? 'ACCEPT' : 'REJECT', reason }))
      }
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['manage', 'moderation'] })
      void qc.invalidateQueries({ queryKey: ['posts'] })
    },
  })
}

/** Sunucu hata kodu → moderatöre anlaşılır mesaj (F-70). */
export function moderationErrorMessage(code: string | undefined, fallback: string) {
  switch (code) {
    case 'SAME_DECIDER':
      return 'İlk kararı siz verdiniz; itirazı başka bir moderatör ya da yönetici değerlendirmeli.'
    case 'NOT_IN_REVIEW':
    case 'REPORT_CLOSED':
    case 'APPEAL_CLOSED':
      return 'Bu kayıt başka biri tarafından sonuçlandırılmış; liste yenilendi.'
    case 'REASON_REQUIRED':
      return 'Gerekçe yazın.'
    default:
      return fallback
  }
}
