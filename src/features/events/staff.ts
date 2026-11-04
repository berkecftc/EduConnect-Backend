import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { ApiError } from '@/lib/api/problem'
import type { CampusEvent } from './api'

/**
 * Etkinlik görevlileri (F-63): kapıda bilet okutan, kulübün aktif üyelerinden seçilen kişiler. Etkinliği yöneten
 * (MANAGE_EVENT_OPERATIONS) önerir; başkan önerirse hemen atanır, diğer önerileri başkan onaylar. Yalnız kulüp
 * etkinliklerinde ve etkinlik bitmeden. Onaylı görevli kapı ekranından giriş yapar (`verify-qr`).
 */

export type EventStaffStatus = 'PENDING' | 'APPROVED'

export type EventStaff = {
  eventId: string
  userId: string
  firstName: string | null
  lastName: string | null
  status: EventStaffStatus
  proposedBy: string | null
  approvedBy: string | null
  createdAt: string
  decidedAt: string | null
}

export const staffName = (s: Pick<EventStaff, 'firstName' | 'lastName'>) => [s.firstName, s.lastName].filter(Boolean).join(' ') || 'Üye'

/** Görevli atanabilen durumlar (sunucu kuralı): onayda ya da yayında olan, bitmemiş etkinlik. */
export const staffable = (e: Pick<CampusEvent, 'status' | 'clubId'>) =>
  !!e.clubId && (e.status === 'PENDING_PRESIDENT' || e.status === 'PENDING' || e.status === 'ACTIVE')

export const staffKeys = {
  list: (eventId: string) => ['events', eventId, 'staff'] as const,
  mine: ['events', 'staff', 'me'] as const,
}

export function useEventStaff(eventId: string, enabled: boolean) {
  return useQuery({
    queryKey: staffKeys.list(eventId),
    enabled,
    queryFn: async () => (await api.get<EventStaff[]>(`/events/manage/${eventId}/staff`)).data,
  })
}

/** Görevli olduğum, yayında ve bitmemiş etkinlikler. */
export function useMyStaffEvents(enabled = true) {
  return useQuery({
    queryKey: staffKeys.mine,
    enabled,
    staleTime: 60_000,
    queryFn: async () => (await api.get<CampusEvent[]>('/events/manage/staff/me')).data,
  })
}

export type StaffAction = { kind: 'propose' | 'approve' | 'reject' | 'remove'; userId: string }

export function useStaffAction(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: StaffAction) => {
      const base = `/events/manage/${eventId}/staff`
      if (a.kind === 'propose') return (await api.post<EventStaff>(base, { userId: a.userId })).data
      if (a.kind === 'remove') return void (await api.delete(`${base}/${a.userId}`))
      return void (await api.post(`${base}/${a.userId}/${a.kind}`))
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: staffKeys.list(eventId) })
      void qc.invalidateQueries({ queryKey: staffKeys.mine })
    },
  })
}

// ——— Kapı ———

/** `POST /api/events/manage/verify-qr` yanıtı (F-88). Ad gelmeyebilir; giriş yine sayılmıştır. */
export type TicketCheckIn = {
  eventId: string
  studentId: string
  firstName: string | null
  lastName: string | null
  studentNumber: string | null
  checkedInAt: string
}

export const checkInName = (t: Pick<TicketCheckIn, 'firstName' | 'lastName'>) => [t.firstName, t.lastName].filter(Boolean).join(' ')

/** Bilet doğrulama hatası: sunucunun mesajı; kapıda ne yapılacağını söyleyen iki istisna dışında. */
export function scanFailure(e: ApiError): string {
  if (e.code === 'INVALID_TICKET' || e.code === 'VERIFICATION_FAILED') return 'Bilet bulunamadı. Kodu kontrol edin.'
  return e.status === 403 ? 'Bu biletin etkinliğinde giriş yapma yetkiniz yok.' : e.message
}

/** Bilet kodunu bu etkinlik için doğrular ve girişi yapar; başka etkinliğin bileti reddedilir, yoklama listesi tazelenir. */
export function useVerifyTicket(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (code: string) =>
      (await api.post<TicketCheckIn>('/events/manage/verify-qr', { qrCode: code.trim(), eventId })).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events', eventId, 'attendance'] }),
  })
}
