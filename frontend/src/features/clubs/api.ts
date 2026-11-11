import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { CampusEvent } from '@/features/events/api'

export type ClubCategory = 'ACADEMIC' | 'SCIENCE_TECHNOLOGY' | 'ARTS_CULTURE' | 'SPORTS' | 'SOCIAL_RESPONSIBILITY' | 'HOBBY' | 'CAREER' | 'OTHER'
export type ClubStatus = 'ACTIVE' | 'AWAITING_ADVISOR' | 'CLOSED'
export type MembershipEndReason = 'LEFT' | 'EXPELLED' | 'EXPIRED' | 'FROZEN' | 'AFFILIATION_ENDED'
export type MembershipRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** `GET /api/clubs` öğesi. */
export type ClubSummary = {
  id: string
  name: string
  logoUrl: string | null
  memberCount: number | null
  advisorName: string | null
  advisorId: string | null
  category: ClubCategory | null
}

export type ClubProfile = {
  category: ClubCategory | null
  contactEmail: string | null
  websiteUrl: string | null
  instagramUrl: string | null
  xUrl: string | null
  linkedinUrl: string | null
}

/** Görev kodu: yeni adlar (PRESIDENT …) ya da eski uyumluluk adları (ROLE_CLUB_OFFICIAL …) gelebilir. */
export type ClubRole = string

export type ClubMember = {
  studentId: string
  firstName: string | null
  lastName: string | null
  role: ClubRole
  isActive?: boolean
  termStartDate?: string | null
  termEndDate?: string | null
}

/** `GET /api/clubs/{id}`. `members` yalnız görevlileri içerebilir (üye listesi yetkiye bağlı) ve ad taşımaz. */
export type ClubDetails = {
  id: string
  name: string
  about: string | null
  /** Geçerli akademik yılın bitiş günü (F-91). */
  academicYearEnd?: string | null
  logoUrl: string | null
  academicAdvisorId: string | null
  advisorName: string | null
  advisorTitle: string | null
  memberCount: number | null
  members: ClubMember[]
  status: ClubStatus
  closedAt: string | null
  closureReason: string | null
  profile: ClubProfile | null
}

/** `GET /api/clubs/my-memberships` ve `…/history` öğesi. Etkinlik alanı sürüme göre `active` ya da `isActive`. */
export type MyMembership = {
  clubId: string
  clubName: string
  logoUrl: string | null
  clubRole: ClubRole
  active?: boolean
  isActive?: boolean
  termStartDate: string | null
  clubStatus: ClubStatus
  validUntil: string | null
  endedAt: string | null
  endReason: MembershipEndReason | null
}

export type MembershipRequest = {
  id: string
  clubId: string
  clubName: string
  clubLogoUrl: string | null
  status: MembershipRequestStatus
  requestDate: string
  processedDate: string | null
  message: string | null
  rejectionReason: string | null
}

export type ClubAnnouncement = { id: string; clubId: string; title: string; body: string; createdAt: string; publishedAt: string | null }

export type PositionTerm = {
  clubId: string
  clubName: string
  position: ClubRole
  positionName: string
  startedAt: string
  endedAt: string | null
  endReason: string | null
}

export const CATEGORY_LABEL: Record<ClubCategory, string> = {
  ACADEMIC: 'Akademik',
  SCIENCE_TECHNOLOGY: 'Bilim ve Teknoloji',
  ARTS_CULTURE: 'Kültür ve Sanat',
  SPORTS: 'Spor',
  SOCIAL_RESPONSIBILITY: 'Sosyal Sorumluluk',
  HOBBY: 'Hobi',
  CAREER: 'Kariyer',
  OTHER: 'Diğer',
}

/** Görev adı ve sırası; eski uyumluluk adları da aynı göreve eşlenir. */
const ROLES: { codes: string[]; label: string }[] = [
  { codes: ['PRESIDENT', 'ROLE_CLUB_OFFICIAL'], label: 'Başkan' },
  { codes: ['VICE_PRESIDENT', 'ROLE_VICE_PRESIDENT'], label: 'Başkan Yardımcısı' },
  { codes: ['GENERAL_SECRETARY', 'ROLE_SECRETARY'], label: 'Genel Sekreter' },
  { codes: ['TREASURER', 'ROLE_TREASURER'], label: 'Sayman' },
  { codes: ['BOARD_MEMBER', 'ROLE_BOARD_MEMBER'], label: 'Yönetim Kurulu Üyesi' },
  { codes: ['AUDITOR', 'ROLE_AUDITOR'], label: 'Denetim Kurulu Üyesi' },
  { codes: ['EVENT_COORDINATOR', 'ROLE_EVENT_COORDINATOR'], label: 'Etkinlik Koordinatörü' },
  { codes: ['COMMUNICATIONS_OFFICER', 'ROLE_COMMUNICATIONS_OFFICER'], label: 'İletişim ve Sosyal Medya Sorumlusu' },
  { codes: ['MEMBERSHIP_OFFICER', 'ROLE_MEMBERSHIP_OFFICER'], label: 'Üyelik Sorumlusu' },
  { codes: ['SPONSORSHIP_OFFICER', 'ROLE_SPONSORSHIP_OFFICER'], label: 'Sponsorluk ve Dış İlişkiler Sorumlusu' },
  { codes: ['MEMBER', 'ROLE_MEMBER'], label: 'Üye' },
]

export function roleLabel(code: ClubRole | null | undefined): string {
  return ROLES.find((r) => r.codes.includes(code ?? ''))?.label ?? 'Üye'
}

/** Görev sırası: başkan önce, üye en son. */
export function roleRank(code: ClubRole | null | undefined): number {
  const i = ROLES.findIndex((r) => r.codes.includes(code ?? ''))
  return i === -1 ? ROLES.length : i
}

export const isMemberRole = (code: ClubRole | null | undefined) => roleRank(code) === ROLES.length - 1

export const END_REASON_LABEL: Record<MembershipEndReason, string> = {
  LEFT: 'Ayrıldınız',
  EXPELLED: 'Çıkarıldınız',
  EXPIRED: 'Süresi doldu',
  FROZEN: 'Dondurulmuş',
  AFFILIATION_ENDED: 'Öğrencilik sona erdi',
}

export const POSITION_END_LABEL: Record<string, string> = {
  CHANGED: 'Görev değişti',
  RESIGNED: 'İstifa',
  REMOVED: 'Görevden alındı',
  LEFT_CLUB: 'Kulüpten ayrıldı',
  EXPELLED: 'Çıkarıldı',
  HANDOVER: 'Seçimle devretti',
  CLUB_CLOSED: 'Kulüp kapandı',
}

export const isActiveMembership = (m: MyMembership) => (m.active ?? m.isActive ?? true) && !m.endedAt

export const clubKeys = {
  all: (category: string) => ['clubs', 'list', category] as const,
  detail: (id: string) => ['clubs', id] as const,
  board: (id: string) => ['clubs', id, 'board'] as const,
  events: (id: string) => ['clubs', id, 'events'] as const,
  announcements: (id: string) => ['clubs', id, 'announcements'] as const,
  memberships: ['clubs', 'memberships'] as const,
  history: ['clubs', 'memberships', 'history'] as const,
  requests: ['clubs', 'requests'] as const,
  positions: ['clubs', 'positions'] as const,
}

export function useClubs(category?: ClubCategory) {
  return useQuery({
    queryKey: clubKeys.all(category ?? 'all'),
    queryFn: async () => (await api.get<ClubSummary[]>('/clubs', { params: category ? { category } : {} })).data,
  })
}

export function useClub(id: string) {
  return useQuery({ queryKey: clubKeys.detail(id), queryFn: async () => (await api.get<ClubDetails>(`/clubs/${id}`)).data })
}

/** Yönetim: adlarla birlikte görevliler (`GET /api/clubs/{id}/board-members`). */
export function useClubBoard(id: string) {
  return useQuery({ queryKey: clubKeys.board(id), queryFn: async () => (await api.get<ClubMember[]>(`/clubs/${id}/board-members`)).data })
}

/** Kulübün etkin etkinlikleri (`GET /api/events/club/{id}`). */
export function useClubEvents(id: string) {
  return useQuery({ queryKey: clubKeys.events(id), queryFn: async () => (await api.get<CampusEvent[]>(`/events/club/${id}`)).data })
}

/** Duyurular yalnız üyelere ve danışmana açık (F-34); 403 gelirse sekme gizlenir. */
export function useClubAnnouncements(id: string, enabled: boolean) {
  return useQuery({
    queryKey: clubKeys.announcements(id),
    enabled,
    retry: false,
    queryFn: async () => (await api.get<{ content: ClubAnnouncement[] }>(`/clubs/${id}/announcements`, { params: { page: 0, size: 20 } })).data.content,
  })
}

export function useMyMemberships(enabled = true) {
  return useQuery({
    queryKey: clubKeys.memberships,
    enabled,
    queryFn: async () => (await api.get<MyMembership[]>('/clubs/my-memberships')).data,
  })
}

export function useMembershipHistory(enabled = true) {
  return useQuery({
    queryKey: clubKeys.history,
    enabled,
    queryFn: async () => (await api.get<MyMembership[]>('/clubs/my-memberships/history')).data,
  })
}

export function useMyMembershipRequests(enabled = true) {
  return useQuery({
    queryKey: clubKeys.requests,
    enabled,
    queryFn: async () => (await api.get<MembershipRequest[]>('/clubs/my-membership-requests')).data,
  })
}

export function useMyPositions(enabled = true) {
  return useQuery({
    queryKey: clubKeys.positions,
    enabled,
    queryFn: async () => (await api.get<PositionTerm[]>('/clubs/my-positions')).data,
  })
}

/** Üyelik işlemlerinden sonra kişinin kulüp verileri ve kulübün sayıları tazelenir. */
function useInvalidateMine() {
  const qc = useQueryClient()
  return (clubId: string) => {
    void qc.invalidateQueries({ queryKey: ['clubs', 'memberships'] })
    void qc.invalidateQueries({ queryKey: clubKeys.requests })
    void qc.invalidateQueries({ queryKey: clubKeys.detail(clubId), exact: true })
  }
}

export function useRequestMembership() {
  const refresh = useInvalidateMine()
  return useMutation({
    mutationFn: async ({ clubId, message }: { clubId: string; message: string }) =>
      (await api.post<MembershipRequest>(`/clubs/${clubId}/membership-requests`, message.trim() ? { message: message.trim() } : {})).data,
    onSuccess: (_d, v) => refresh(v.clubId),
  })
}

export function useCancelMembershipRequest() {
  const refresh = useInvalidateMine()
  return useMutation({
    mutationFn: async (clubId: string) => {
      await api.delete(`/clubs/${clubId}/membership-requests`)
    },
    onSuccess: (_d, clubId) => refresh(clubId),
  })
}

export function useLeaveClub() {
  const refresh = useInvalidateMine()
  return useMutation({
    mutationFn: async (clubId: string) => {
      await api.delete(`/clubs/${clubId}/leave`)
    },
    onSuccess: (_d, clubId) => refresh(clubId),
  })
}

export function useRenewMembership() {
  const refresh = useInvalidateMine()
  return useMutation({
    mutationFn: async (clubId: string) => (await api.post<MyMembership>(`/clubs/${clubId}/membership/renew`)).data,
    onSuccess: (_d, clubId) => refresh(clubId),
  })
}

/** Üyeliğin bitişine 30 günden az kaldıysa ya da geçtiyse yenileme önerilir. */
export function renewalDue(m: MyMembership, today: string): boolean {
  if (!m.validUntil) return false
  const days = (Date.parse(`${m.validUntil}T00:00:00Z`) - Date.parse(`${today}T00:00:00Z`)) / 86_400_000
  return days <= 30
}
