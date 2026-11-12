import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export type EventStatus = 'PENDING_PRESIDENT' | 'PENDING' | 'ACTIVE' | 'REJECTED' | 'CANCELLED' | 'COMPLETED'
export type RegistrationStatus = 'REGISTERED' | 'CANCELLED' | 'NO_SHOW'
export type EventAudience = 'MEMBERS_ONLY' | 'ALL_STUDENTS' | 'CAMPUS'
export type AdmissionMode = 'AUTO_CONFIRM' | 'APPROVAL_REQUIRED'
export type ParticipationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'CLOSED' | 'WAITLISTED' | 'WITHDRAWN'

/** `GET /api/events/my-registrations` öğesi. `eventDate` = başlangıç (bölgesiz yerel saat); `qrCode` bilet belirteci. */
export type MyRegistration = {
  eventId: string
  eventTitle: string
  eventDescription: string | null
  eventDate: string
  eventLocation: string | null
  qrCode: string | null
  registrationTime: string
  attended: boolean
  registrationStatus: RegistrationStatus
  eventStatus: EventStatus
  endsAt?: string | null
  cancellationReason?: string | null
  checkInOpensAt?: string | null
}

/** `GET /api/events/{id}` ve liste öğesi (EventResponse). Saatler bölgesiz yerel (F-61). */
export type CampusEvent = {
  id: string
  title: string
  description: string | null
  startsAt: string
  endsAt: string | null
  speakers?: string | null
  audience?: EventAudience | null
  admission?: AdmissionMode | null
  location: string | null
  imageUrl: string | null
  clubId: string | null
  clubName: string | null
  organizerName: string | null
  capacity: number | null
  registrationOpensAt?: string | null
  registrationClosesAt?: string | null
  cancelUntil?: string | null
  status: EventStatus
  rejectionReason?: string | null
  cancellationReason?: string | null
  /** Girişin açıldığı an (F-91): tekil etkinlikte ve görevli listesinde gelir, diğer listelerde boş. */
  checkInOpensAt?: string | null
}

/** `GET /api/events/{id}/availability` (F-62). `capacity` boşsa sınırsız. */
export type EventAvailability = {
  eventId: string
  audience: EventAudience
  admission: AdmissionMode
  capacity: number | null
  registered: number
  waitlisted: number
  remaining: number | null
  registrationOpen: boolean
  registrationOpensAt: string | null
  registrationClosesAt: string | null
  cancelUntil: string | null
}

/** `GET /api/events/my-participation-requests` öğesi. */
export type ParticipationRequest = {
  id: string
  eventId: string
  eventTitle: string
  status: ParticipationStatus
  requestDate: string
  processedDate: string | null
  message: string | null
  rejectionReason: string | null
}

export const AUDIENCE_LABEL: Record<EventAudience, string> = {
  MEMBERS_ONLY: 'Yalnız kulüp üyeleri',
  ALL_STUDENTS: 'Tüm öğrenciler',
  CAMPUS: 'Herkes',
}

export const ADMISSION_LABEL: Record<AdmissionMode, string> = {
  AUTO_CONFIRM: 'Kayıt hemen tamamlanır',
  APPROVAL_REQUIRED: 'Kayıt, düzenleyici onayından sonra tamamlanır',
}

export const eventKeys = {
  all: ['events', 'all'] as const,
  detail: (id: string) => ['events', id] as const,
  availability: (id: string) => ['events', id, 'availability'] as const,
  registrations: ['events', 'registrations', 'mine'] as const,
  requests: ['events', 'requests', 'mine'] as const,
}

/** Etkin etkinliklerin ilk sayfası (oturumsuz da açık uç). */
export function useUpcomingEvents(size = 6) {
  return useQuery({
    queryKey: ['events', 'upcoming', size],
    queryFn: async () => (await api.get<{ content: CampusEvent[] }>('/events', { params: { page: 0, size } })).data.content,
  })
}

/** Bütün etkin etkinlikler (`GET /api/events`). */
export function useEvents() {
  return useQuery({ queryKey: eventKeys.all, queryFn: async () => (await api.get<CampusEvent[]>('/events')).data })
}

export function useEvent(id: string) {
  return useQuery({ queryKey: eventKeys.detail(id), queryFn: async () => (await api.get<CampusEvent>(`/events/${id}`)).data })
}

export function useAvailability(id: string, enabled = true) {
  return useQuery({
    queryKey: eventKeys.availability(id),
    enabled,
    queryFn: async () => (await api.get<EventAvailability>(`/events/${id}/availability`)).data,
  })
}

export function useMyRegistrations(enabled = true) {
  return useQuery({
    queryKey: eventKeys.registrations,
    enabled,
    queryFn: async () => (await api.get<MyRegistration[]>('/events/my-registrations')).data,
  })
}

export function useMyParticipationRequests(enabled = true) {
  return useQuery({
    queryKey: eventKeys.requests,
    enabled,
    queryFn: async () => (await api.get<ParticipationRequest[]>('/events/my-participation-requests')).data,
  })
}

/** Katılım işlemlerinden sonra kişinin kayıtları, istekleri ve etkinliğin doluluğu tazelenir. */
function useRefreshMine() {
  const qc = useQueryClient()
  return (eventId: string) => {
    void qc.invalidateQueries({ queryKey: eventKeys.registrations })
    void qc.invalidateQueries({ queryKey: eventKeys.requests })
    void qc.invalidateQueries({ queryKey: eventKeys.availability(eventId) })
  }
}

/** Katıl (F-62): yanıttaki `status` APPROVED kayıt tamam, WAITLISTED bekleme listesi, PENDING onay bekleniyor. */
export function useJoinEvent() {
  const refresh = useRefreshMine()
  return useMutation({
    mutationFn: async ({ eventId, message }: { eventId: string; message?: string }) =>
      (
        await api.post<{ message: string; requestId: string; status: ParticipationStatus }>(
          `/events/${eventId}/participation-request`,
          message?.trim() ? { message: message.trim() } : {},
        )
      ).data,
    onSuccess: (_d, v) => refresh(v.eventId),
  })
}

/** Bekleyen ya da bekleme listesindeki isteği geri çek. */
export function useWithdrawParticipation() {
  const refresh = useRefreshMine()
  return useMutation({
    mutationFn: async (eventId: string) => {
      await api.delete(`/events/${eventId}/participation-request`)
    },
    onSuccess: (_d, eventId) => refresh(eventId),
  })
}

/** Kaydı iptal et (`CANCELLATION_CLOSED` ise backend mesajı gösterilir). */
export function useCancelRegistration() {
  const refresh = useRefreshMine()
  return useMutation({
    mutationFn: async (eventId: string) => {
      await api.delete(`/events/${eventId}/registration`)
    },
    onSuccess: (_d, eventId) => refresh(eventId),
  })
}

/** Kişinin bir etkinlikteki durumu: kayıt (bilet) ve istekten türetilir. */
export type MyEventState =
  | { kind: 'registered'; registration: MyRegistration }
  | { kind: 'waitlisted' | 'pending'; request: ParticipationRequest }
  | { kind: 'rejected'; request: ParticipationRequest }
  | { kind: 'none' }

export function myEventState(eventId: string, registrations: MyRegistration[] = [], requests: ParticipationRequest[] = []): MyEventState {
  const registration = registrations.find((r) => r.eventId === eventId && r.registrationStatus === 'REGISTERED')
  if (registration) return { kind: 'registered', registration }
  const latest = requests.filter((r) => r.eventId === eventId).sort((a, b) => b.requestDate.localeCompare(a.requestDate))[0]
  if (latest?.status === 'WAITLISTED') return { kind: 'waitlisted', request: latest }
  if (latest?.status === 'PENDING') return { kind: 'pending', request: latest }
  if (latest?.status === 'REJECTED') return { kind: 'rejected', request: latest }
  return { kind: 'none' }
}
