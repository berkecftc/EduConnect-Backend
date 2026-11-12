import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { toLocalIso } from '@/lib/time'
import { eventKeys, type AdmissionMode, type CampusEvent, type EventAudience, type ParticipationStatus, type RegistrationStatus } from './api'

/** Etkinlik yönetimi (F-61…F-65): oluşturma, onay kuyrukları, katılım istekleri, yoklama ve değişiklikler. */

/** Form değerleri: saatler `datetime-local` biçiminde (`2026-10-12T18:30`); gönderirken bölgesiz ISO'ya çevrilir. */
export type EventDraft = {
  title: string
  description: string
  startsAt: string
  endsAt: string
  location: string
  speakers: string
  audience: EventAudience
  admission: AdmissionMode
  capacity: string
  registrationOpensAt: string
  registrationClosesAt: string
  cancelUntil: string
  /** Yalnız kampüs etkinliğinde: düzenleyen birim (ör. Kariyer Merkezi). */
  organizerName: string
}

/** Boş metni null'a, saatleri bölgesiz ISO'ya, kontenjanı sayıya çevirir. */
export function draftToPayload(d: EventDraft) {
  const text = (s: string) => (s.trim() ? s.trim() : null)
  const time = (s: string) => (s ? toLocalIso(s) : null)
  return {
    title: d.title.trim(),
    description: text(d.description),
    startsAt: toLocalIso(d.startsAt),
    endsAt: time(d.endsAt),
    location: text(d.location),
    speakers: text(d.speakers),
    audience: d.audience,
    admission: d.admission,
    capacity: d.capacity.trim() ? Number(d.capacity) : null,
    registrationOpensAt: time(d.registrationOpensAt),
    registrationClosesAt: time(d.registrationClosesAt),
    cancelUntil: time(d.cancelUntil),
  }
}

/** Kulüp adına etkinlik (multipart: `data` JSON + isteğe bağlı `poster`). Backend kulübü adından bulur. */
export function useCreateEvent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ draft, clubName, poster }: { draft: EventDraft; clubName: string; poster: File | null }) => {
      const form = new FormData()
      form.append('data', new Blob([JSON.stringify({ ...draftToPayload(draft), clubName })], { type: 'application/json' }))
      if (poster) form.append('poster', poster)
      return (await api.post<CampusEvent>('/events/manage', form)).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events'] }),
  })
}

/** Kampüs etkinlikleri (PERM_CAMPUS_PUBLISHER, F-64): kulüp dışı, onaysız yayımlanır; bütün durumlar, yeniden eskiye. */
export function useCampusEvents(enabled = true) {
  return useQuery({
    queryKey: ['events', 'campus'],
    enabled,
    queryFn: async () => (await api.get<CampusEvent[]>('/events/campus')).data,
  })
}

/** Kampüs etkinliği oluştur: multipart `data` (kitle CAMPUS, düzenleyen birim zorunlu) + isteğe bağlı afiş. */
export function useCreateCampusEvent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ draft, poster }: { draft: EventDraft; poster: File | null }) => {
      // Kitle sunucuda CAMPUS olur; gönderilmez.
      const rest: Partial<ReturnType<typeof draftToPayload>> = draftToPayload(draft)
      delete rest.audience
      const form = new FormData()
      form.append('data', new Blob([JSON.stringify({ ...rest, organizerName: draft.organizerName.trim() })], { type: 'application/json' }))
      if (poster) form.append('poster', poster)
      return (await api.post<CampusEvent>('/events/campus', form)).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events'] }),
  })
}

/** Onay bekleyen ya da reddedilmiş etkinliği düzenle; reddedilmişte `note` (düzeltme notu) zorunlu. */
export function useUpdateEvent(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ draft, note }: { draft: EventDraft; note: string }) =>
      (await api.put<CampusEvent>(`/events/manage/${eventId}`, { ...draftToPayload(draft), note: note.trim() || null })).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events'] }),
  })
}

/** Kulübün bütün etkinlikleri (yönetim görünümü: onay bekleyen ve reddedilenler dahil). */
export function useClubManagedEvents(clubId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['events', 'manage', 'club', clubId],
    enabled,
    queryFn: async () => (await api.get<CampusEvent[]>(`/events/manage/club/${clubId}/events`)).data,
  })
}

// ——— Onay kuyrukları ———

export function usePresidentQueue(enabled: boolean) {
  return useQuery({
    queryKey: ['events', 'queue', 'president'],
    enabled,
    queryFn: async () => (await api.get<CampusEvent[]>('/events/president/pending')).data,
  })
}

export function useAdvisorQueue(enabled: boolean) {
  return useQuery({
    queryKey: ['events', 'queue', 'advisor'],
    enabled,
    queryFn: async () => (await api.get<CampusEvent[]>('/events/advisor/pending')).data,
  })
}

/** Başkan ya da danışman kararı; başkanın reddinde gerekçe zorunlu, danışmanda isteğe bağlı. */
export function useDecideEvent() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { role: 'president' | 'advisor'; eventId: string; kind: 'approve' | 'reject'; reason?: string }) => {
      const base = `/events/${a.role}/${a.eventId}`
      if (a.kind === 'approve') return (await api.post<CampusEvent>(`${base}/approve`)).data
      return (await api.post<CampusEvent>(`${base}/reject`, { reason: a.reason?.trim() || null })).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events'] }),
  })
}

// ——— Katılım istekleri ———

export type ManagedRequest = {
  id: string
  eventId: string
  eventTitle: string
  studentId: string
  studentName: string | null
  studentEmail: string | null
  status: ParticipationStatus
  requestDate: string
  processedDate: string | null
  message: string | null
  rejectionReason: string | null
}

export function useEventRequests(eventId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['events', eventId, 'requests'],
    enabled,
    queryFn: async () => (await api.get<ManagedRequest[]>(`/events/${eventId}/participation-requests`)).data,
  })
}

export function useDecideRequest(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { requestId: string; kind: 'approve' | 'reject'; reason?: string }) => {
      const base = `/events/participation-requests/${a.requestId}`
      if (a.kind === 'approve') await api.post(`${base}/approve`)
      else await api.post(`${base}/reject`, { reason: a.reason?.trim() || null })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['events', eventId] })
      void qc.invalidateQueries({ queryKey: eventKeys.availability(eventId) })
    },
  })
}

// ——— Yoklama ———

export type AttendanceRow = {
  studentId: string
  firstName: string | null
  lastName: string | null
  studentNumber: string | null
  status: RegistrationStatus
  attended: boolean
  checkedInAt: string | null
  method: string | null
}

export type AttendanceReport = {
  eventId: string
  title: string
  registered: number
  attended: number
  noShow: number
  cancelled: number
  rows: AttendanceRow[]
}

export function useAttendance(eventId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['events', eventId, 'attendance'],
    enabled,
    queryFn: async () => (await api.get<AttendanceReport>(`/events/manage/${eventId}/attendance`)).data,
  })
}

/** Elle giriş ya da geri alma; bilet koduyla giriş (`verify-qr`) aynı listeyi tazeler. */
export function useCheckIn(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { kind: 'in' | 'undo'; studentId: string } | { kind: 'code'; code: string }) => {
      if (a.kind === 'code') await api.post('/events/manage/verify-qr', { qrCode: a.code.trim(), eventId })
      else if (a.kind === 'in') await api.post(`/events/manage/${eventId}/registrations/${a.studentId}/check-in`)
      else await api.delete(`/events/manage/${eventId}/registrations/${a.studentId}/check-in`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events', eventId, 'attendance'] }),
  })
}

// ——— Değişiklikler ———

export type EventChangeKind = 'EDITED' | 'RESUBMITTED' | 'POSTPONED' | 'RELOCATED' | 'CANCELLED'
export type EventChange = { id: string; kind: EventChangeKind; details: string | null; reason: string | null; createdAt: string }

export const CHANGE_LABEL: Record<EventChangeKind, string> = {
  EDITED: 'Düzenlendi',
  RESUBMITTED: 'Yeniden onaya gönderildi',
  POSTPONED: 'Ertelendi',
  RELOCATED: 'Yeri değişti',
  CANCELLED: 'İptal edildi',
}

export function useEventChanges(eventId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['events', eventId, 'changes'],
    enabled,
    queryFn: async () => (await api.get<EventChange[]>(`/events/manage/${eventId}/changes`)).data,
  })
}

/** Ertele (yeni saatler), yeri değiştir (yeni yer) ya da iptal et; gerekçe her zaman zorunlu. */
export function useChangeEvent(eventId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { kind: 'postpone' | 'relocate' | 'cancel'; reason: string; startsAt?: string; endsAt?: string; location?: string }) =>
      (
        await api.post<CampusEvent>(`/events/manage/${eventId}/${a.kind}`, {
          startsAt: a.startsAt || null,
          endsAt: a.endsAt || null,
          location: a.location?.trim() || null,
          reason: a.reason.trim(),
        })
      ).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['events'] }),
  })
}

export const EVENT_STATUS: Record<CampusEvent['status'], { label: string; tone: 'warning' | 'success' | 'danger' | 'neutral' | 'info' }> = {
  PENDING_PRESIDENT: { label: 'Başkan onayında', tone: 'warning' },
  PENDING: { label: 'Danışman onayında', tone: 'warning' },
  ACTIVE: { label: 'Yayında', tone: 'success' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
  CANCELLED: { label: 'İptal edildi', tone: 'neutral' },
  COMPLETED: { label: 'Tamamlandı', tone: 'neutral' },
}

/** Var olan etkinlikten form değerleri (düzenleme için). */
export function eventToDraft(e: CampusEvent): EventDraft {
  const local = (s: string | null | undefined) => (s ? s.slice(0, 16) : '')
  return {
    title: e.title,
    description: e.description ?? '',
    startsAt: local(e.startsAt),
    endsAt: local(e.endsAt),
    location: e.location ?? '',
    speakers: e.speakers ?? '',
    audience: e.audience ?? 'ALL_STUDENTS',
    admission: e.admission ?? 'AUTO_CONFIRM',
    capacity: e.capacity != null ? String(e.capacity) : '',
    registrationOpensAt: local(e.registrationOpensAt),
    registrationClosesAt: local(e.registrationClosesAt),
    cancelUntil: local(e.cancelUntil),
    organizerName: e.organizerName ?? '',
  }
}

export type DraftErrors = Partial<Record<keyof EventDraft, string>>

/** İstemci ön kontrolü (asıl kurallar backend'de: EVENT_IN_PAST, EVENT_TOO_SOON, INVALID_EVENT_TIMES …). */
export function validateDraft(d: EventDraft, now: string): DraftErrors {
  const e: DraftErrors = {}
  if (!d.title.trim()) e.title = 'Başlık yazın.'
  if (!d.startsAt) e.startsAt = 'Başlangıç zamanını seçin.'
  else if (toLocalIso(d.startsAt) <= now) e.startsAt = 'Başlangıç ileri bir zaman olmalı.'
  if (d.endsAt && d.startsAt && d.endsAt <= d.startsAt) e.endsAt = 'Bitiş, başlangıçtan sonra olmalı.'
  if (d.capacity.trim() && !(Number.isInteger(Number(d.capacity)) && Number(d.capacity) > 0)) e.capacity = 'Kontenjan pozitif bir tam sayı olmalı.'
  if (d.registrationOpensAt && d.registrationClosesAt && d.registrationClosesAt <= d.registrationOpensAt)
    e.registrationClosesAt = 'Kayıt kapanışı, açılıştan sonra olmalı.'
  if (d.registrationClosesAt && d.startsAt && d.registrationClosesAt > d.startsAt) e.registrationClosesAt = 'Kayıt, etkinlik başlamadan kapanmalı.'
  if (d.cancelUntil && d.startsAt && d.cancelUntil > d.startsAt) e.cancelUntil = 'İptal son tarihi, etkinlikten önce olmalı.'
  return e
}

export const EMPTY_DRAFT: EventDraft = {
  title: '',
  description: '',
  startsAt: '',
  endsAt: '',
  location: '',
  speakers: '',
  audience: 'ALL_STUDENTS',
  admission: 'AUTO_CONFIRM',
  capacity: '',
  registrationOpensAt: '',
  registrationClosesAt: '',
  cancelUntil: '',
  organizerName: '',
}
