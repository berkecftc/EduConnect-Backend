import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { toPaged, type Paged } from '@/lib/api/page'
import { useNavigate } from 'react-router-dom'
import { api } from '@/lib/api/client'
import { LINES } from '@/design/lines'

export type NotificationCategory =
  | 'ACCOUNT'
  | 'COURSE'
  | 'EVENT'
  | 'MODERATION'
  | 'CLUB_MANAGEMENT'
  | 'CLUB_NEWS'
  | 'COMMUNITY'
  | 'ACHIEVEMENT'

/** `GET /api/notifications` öğesi (F-74). `link` uygulama içi göreli yol; silinmiş kulüpte null olabilir (F-75). */
export type AppNotification = {
  id: string
  category: NotificationCategory
  type: string
  title: string
  body: string
  link: string | null
  read: boolean
  createdAt: string
}

/** Spring `Page` (DIRECT) biçiminin kullandığımız kısmı. */
/** Sayfalı yanıt; ortak biçim `@/lib/api/page`'te. */
export type SpringPage<T> = Paged<T>

/** `GET /api/notifications/preferences` öğesi: zorunlu kategoride e-posta kapatılamaz. */
export type NotificationPreference = { category: NotificationCategory; label: string; mandatory: boolean; emailEnabled: boolean }

/** Kategori → hat rengi ve kısa ad (satırdaki ince çizgi ve etiket). */
export const CATEGORY_META: Record<NotificationCategory, { line: string; label: string }> = {
  ACCOUNT: { line: LINES.yonetim.stroke, label: 'Hesap' },
  COURSE: { line: LINES.ders.stroke, label: 'Ders' },
  EVENT: { line: LINES.etkinlik.stroke, label: 'Etkinlik' },
  MODERATION: { line: LINES.yonetim.stroke, label: 'Moderasyon' },
  CLUB_MANAGEMENT: { line: LINES.kulup.stroke, label: 'Kulüp yönetimi' },
  CLUB_NEWS: { line: LINES.kulup.stroke, label: 'Kulüp' },
  COMMUNITY: { line: LINES.topluluk.stroke, label: 'Topluluk' },
  ACHIEVEMENT: { line: LINES.topluluk.stroke, label: 'Puan ve rozet' },
}

/** Hatırlatmalar diğer bildirimlerden ayrı işaretlenir (F-78). */
export const isReminder = (n: Pick<AppNotification, 'type'>) => n.type === 'EVENT_REMINDER' || n.type === 'ASSIGNMENT_REMINDER'

/**
 * Türüne göre kısayol (F-75…F-78): satırın kendisi `link`e gider, kısayol işin yapıldığı yere. Kapatılmış kulüpte `link` boş
 * olabilir; o zaman yalnız sabit adresli kısayollar kalır.
 */
export function notificationShortcut(n: Pick<AppNotification, 'type' | 'title' | 'link'>): { label: string; to: string } | null {
  const { link } = n
  switch (n.type) {
    case 'EVENT_REMINDER':
    case 'EVENT_TICKET':
      return { label: 'Biletimi göster', to: '/me/tickets' }
    case 'EVENT_APPROVAL_REQUEST':
      return { label: 'Onay kutusuna git', to: '/clubs/approvals' }
    case 'ASSIGNMENT_REMINDER':
      return link ? { label: 'Teslim et', to: link } : null
    case 'CLUB_MEMBERSHIP':
      // Başvuranın kararı da aynı türle gelir; yöneticiye giden yalnız başlığından ayrılır.
      return link && n.title.toLocaleLowerCase('tr-TR').includes('yeni üyelik başvurusu') ? { label: 'Başvuruları gör', to: `${link}?sekme=yonetim` } : null
    case 'EVENT_PARTICIPATION_REQUEST':
      return link ? { label: 'Katılım isteklerini gör', to: `${link}/yonetim?sekme=istekler` } : null
    case 'EVENT_STAFF_ASSIGNED':
      return link ? { label: 'Kapı ekranı', to: `${link}/kapi` } : null
    case 'COURSE_APPLICATION':
      return link ? { label: 'Başvuruları gör', to: `${link}?sekme=basvurular` } : null
    case 'COURSE_MATERIAL':
      return link ? { label: 'Materyallere git', to: `${link}?sekme=materyaller` } : null
    case 'MODERATION_REJECTED':
    case 'MODERATION_HIDDEN':
    case 'MODERATION_REMOVED':
      return link ? { label: 'İtiraz et', to: link } : null
    default:
      return null
  }
}

export const notificationKeys = {
  unread: ['notifications', 'unread'] as const,
  list: (unreadOnly: boolean) => ['notifications', 'list', unreadOnly] as const,
  preferences: ['notifications', 'preferences'] as const,
}

export function useUnreadCount() {
  return useQuery({
    queryKey: notificationKeys.unread,
    refetchInterval: 60_000,
    queryFn: async () => (await api.get<{ unread: number }>('/notifications/unread-count')).data.unread,
  })
}

export function useRecentNotifications(size = 5) {
  return useQuery({
    queryKey: ['notifications', 'recent', size],
    queryFn: async () => toPaged((await api.get<SpringPage<AppNotification>>('/notifications', { params: { page: 0, size } })).data).content,
  })
}

const PAGE_SIZE = 20

/** Tüm bildirimler, sayfa sayfa ("Daha eski bildirimler" ile devam). */
export function useNotifications(unreadOnly: boolean) {
  return useInfiniteQuery({
    queryKey: notificationKeys.list(unreadOnly),
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      toPaged((await api.get<SpringPage<AppNotification>>('/notifications', { params: { unreadOnly, page: pageParam, size: PAGE_SIZE } })).data),
    getNextPageParam: (last) => (last.last ? undefined : last.number + 1),
  })
}

/** Okundu say: listedeki satırı yerinde günceller, sayacı tazeler. */
export function useMarkRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.post(`/notifications/${id}/read`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

export function useMarkAllRead() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => (await api.post<{ updated: number }>('/notifications/read-all')).data.updated,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['notifications'] }),
  })
}

/**
 * Bildirimi aç: okunmamışsa okundu sayar, bağlantısı varsa uygulama içinde oraya gider (F-74).
 * Okundu isteği beklenmez; yönlendirme hemen olur.
 */
export function useOpenNotification() {
  const markRead = useMarkRead()
  const navigate = useNavigate()
  return (n: AppNotification, to: string | null = n.link) => {
    if (!n.read) markRead.mutate(n.id)
    if (to) navigate(to)
  }
}

export function usePreferences() {
  return useQuery({
    queryKey: notificationKeys.preferences,
    queryFn: async () => (await api.get<NotificationPreference[]>('/notifications/preferences')).data,
  })
}

/** E-posta tercihini değiştir; ekranda hemen değişir, hata olursa geri alınır. */
export function useUpdatePreference() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (p: { category: NotificationCategory; emailEnabled: boolean }) =>
      (await api.put<NotificationPreference>('/notifications/preferences', p)).data,
    onMutate: async (p) => {
      await qc.cancelQueries({ queryKey: notificationKeys.preferences })
      const before = qc.getQueryData<NotificationPreference[]>(notificationKeys.preferences)
      qc.setQueryData<NotificationPreference[]>(notificationKeys.preferences, (list) =>
        list?.map((x) => (x.category === p.category ? { ...x, emailEnabled: p.emailEnabled } : x)),
      )
      return { before }
    },
    onError: (_e, _p, ctx) => qc.setQueryData(notificationKeys.preferences, ctx?.before),
    onSettled: () => void qc.invalidateQueries({ queryKey: notificationKeys.preferences }),
  })
}

/** E-postadaki "abonelikten çık" bağlantısı (oturumsuz). */
export function useUnsubscribe() {
  return useMutation({
    mutationFn: async (token: string) => (await api.post<NotificationPreference>('/notifications/unsubscribe', { token })).data,
  })
}
