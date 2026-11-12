import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { clubKeys, type ClubAnnouncement, type ClubCategory, type ClubRole, type MembershipRequest } from './api'
import type { Election } from './elections'
import type { Budget, FinanceEntry, Meeting, Report, Sponsorship } from './records'

/** Kulüp yönetimi (F-85): yetkiler, üyelik başvuruları, onay kayıtları ve duyurular. Yetki kuralları backend'de. */

export type ClubPermission =
  | 'VIEW_MEMBERS'
  | 'VIEW_MANAGEMENT_DATA'
  | 'VIEW_DECISIONS'
  | 'MANAGE_MEMBERSHIP_REQUESTS'
  | 'REVIEW_MEMBERSHIP_REQUESTS'
  | 'PREPARE_ANNOUNCEMENT'
  | 'APPROVE_AS_PRESIDENT'
  | 'ADVISE'
  | (string & {})

/** `GET /api/clubs/{id}/my-access` ve `/my-access` öğesi: kişinin kulüpteki görevi ve yetkileri. */
export type ClubAccess = {
  clubId: string
  userId: string | null
  position: ClubRole | null
  member: boolean
  actingPresident: boolean
  advisor: boolean
  permissions: ClubPermission[]
  clubName: string | null
}

export const can = (access: ClubAccess | undefined, ...permissions: ClubPermission[]) =>
  !!access && permissions.some((p) => access.permissions.includes(p))

export function useClubAccess(clubId: string, enabled = true) {
  return useQuery({
    queryKey: ['clubs', clubId, 'access'],
    enabled,
    staleTime: 60_000,
    queryFn: async () => (await api.get<ClubAccess>(`/clubs/${clubId}/my-access`)).data,
  })
}

/** Görevli ya da danışman olunan bütün kulüpler; menü ve kısayollar için. */
export function useClubAccesses(enabled = true) {
  return useQuery({
    queryKey: ['clubs', 'access'],
    enabled,
    staleTime: 60_000,
    queryFn: async () => (await api.get<ClubAccess[]>('/clubs/my-access')).data,
  })
}

// ——— Üyelik başvuruları ———

export type MembershipRecommendation = 'APPROVE' | 'REJECT'

/** Yöneticinin gördüğü bekleyen başvuru: öğrenci adı ve e-postası, varsa üyelik sorumlusunun önerisi. */
export type PendingMembershipRequest = MembershipRequest & {
  studentId: string
  studentName: string | null
  studentEmail: string | null
  recommendation: MembershipRecommendation | null
  recommendationNote: string | null
}

export function usePendingMembershipRequests(clubId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['clubs', clubId, 'membership-requests'],
    enabled,
    queryFn: async () => (await api.get<PendingMembershipRequest[]>(`/clubs/${clubId}/membership-requests/pending`)).data,
  })
}

export type MembershipDecision =
  | { kind: 'approve'; requestId: string }
  | { kind: 'reject'; requestId: string; reason: string }
  | { kind: 'recommend'; requestId: string; recommendation: MembershipRecommendation; note: string }

/** Başvuru kararı: kabul, ret (isteğe bağlı gerekçe) ya da üyelik sorumlusunun önerisi. */
export function useDecideMembership(clubId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: MembershipDecision) => {
      const base = `/clubs/${clubId}/membership-requests/${a.requestId}`
      if (a.kind === 'approve') await api.put(`${base}/approve`)
      else if (a.kind === 'reject') await api.put(`${base}/reject`, a.reason.trim() ? { rejectionReason: a.reason.trim() } : {})
      else await api.put(`${base}/recommendation`, { recommendation: a.recommendation, note: a.note.trim() || null })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['clubs', clubId, 'membership-requests'] })
      void qc.invalidateQueries({ queryKey: clubKeys.detail(clubId), exact: true })
    },
  })
}

// ——— Onay kayıtları ———

export type ApprovalType =
  | 'ROLE_CHANGE'
  | 'RESIGNATION'
  | 'ADVISOR_CHANGE'
  | 'CLUB_CLOSURE'
  | 'MEMBER_EXPULSION'
  | 'CLUB_PROFILE_UPDATE'
  | 'CLUB_LOGO_CHANGE'
  | 'CLUB_ANNOUNCEMENT'
  | (string & {})
export type ApprovalStatus = 'PENDING_PRESIDENT' | 'PENDING_ADVISOR' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN'

export type ProfileChange = {
  about: string | null
  category: ClubCategory | null
  contactEmail: string | null
  websiteUrl: string | null
  instagramUrl: string | null
  xUrl: string | null
  linkedinUrl: string | null
  logoUrl: string | null
}

/** Onay kaydı (görev değişikliği, duyuru, profil, logo …). Kişi adları null olabilir. */
export type ApprovalRequest = {
  id: string
  clubId: string
  clubName: string | null
  type: ApprovalType
  status: ApprovalStatus
  preparedBy: string | null
  preparedByName: string | null
  subjectUserId: string | null
  subjectUserName: string | null
  currentPosition: ClubRole | null
  requestedPosition: ClubRole | null
  note: string | null
  rejectionReason: string | null
  responseNote: string | null
  createdAt: string
  presidentDecidedByName: string | null
  presidentDecidedAt: string | null
  decidedByName: string | null
  decidedAt: string | null
  profileChange: ProfileChange | null
  announcement: ClubAnnouncement | null
  /** Türe göre onaylanan kaydın kendisi (bütçe, gelir-gider, sponsorluk, tutanak, rapor, seçim). */
  budget?: Budget | null
  financeEntry?: FinanceEntry | null
  sponsorship?: Sponsorship | null
  meeting?: Meeting | null
  report?: Report | null
  election?: Election | null
}

export const APPROVAL_TYPE_LABEL: Record<string, string> = {
  ROLE_CHANGE: 'Görev değişikliği',
  RESIGNATION: 'Görevden ayrılma',
  ADVISOR_CHANGE: 'Danışman değişikliği',
  CLUB_CLOSURE: 'Kulübü kapatma',
  MEMBER_EXPULSION: 'Üyelikten çıkarma',
  CLUB_PROFILE_UPDATE: 'Kulüp bilgileri',
  CLUB_LOGO_CHANGE: 'Logo değişikliği',
  CLUB_ANNOUNCEMENT: 'Duyuru',
  CLUB_BUDGET: 'Bütçe',
  CLUB_FINANCE_ENTRY: 'Gelir ya da gider kaydı',
  CLUB_SPONSORSHIP: 'Sponsorluk',
  CLUB_MEETING_MINUTES: 'Toplantı tutanağı',
  CLUB_ACTIVITY_REPORT: 'Faaliyet raporu',
  CLUB_AUDIT_REPORT: 'Denetim raporu',
  CLUB_ELECTION: 'Seçim',
}

export const APPROVAL_STATUS: Record<ApprovalStatus, { label: string; tone: 'warning' | 'success' | 'danger' | 'neutral' }> = {
  PENDING_PRESIDENT: { label: 'Başkan onayında', tone: 'warning' },
  PENDING_ADVISOR: { label: 'Danışman onayında', tone: 'warning' },
  APPROVED: { label: 'Onaylandı', tone: 'success' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
  WITHDRAWN: { label: 'Geri çekildi', tone: 'neutral' },
}

export const isPendingApproval = (s: ApprovalStatus) => s === 'PENDING_PRESIDENT' || s === 'PENDING_ADVISOR'

/** Onay kutusu: başkanlık yapılan kulüplerde başkan onayı, danışmanı olunan kulüplerde danışman onayı bekleyenler. */
export function useApprovalInbox(enabled = true) {
  return useQuery({
    queryKey: ['clubs', 'approvals', 'inbox'],
    enabled,
    queryFn: async () => (await api.get<ApprovalRequest[]>('/clubs/approvals/inbox')).data,
  })
}

/** Taleplerim (F-89): kişinin kendi hazırladığı ve hakkında açılan talepler, yeniden eskiye. */
export function useMyApprovals(enabled = true) {
  return useQuery({
    queryKey: ['clubs', 'approvals', 'mine'],
    enabled,
    queryFn: async () => (await api.get<ApprovalRequest[]>('/clubs/approvals/mine')).data,
  })
}

/** Hakkındaki üyelikten çıkarma talebine savunma: danışman kararını vermeden önce yenilenebilir. */
export function useSubmitDefence() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { clubId: string; requestId: string; note: string }) =>
      (await api.post<ApprovalRequest>(`/clubs/${a.clubId}/approvals/${a.requestId}/defence`, { note: a.note.trim() })).data,
    onSuccess: (_d, a) => {
      void qc.invalidateQueries({ queryKey: ['clubs', 'approvals'] })
      void qc.invalidateQueries({ queryKey: ['clubs', a.clubId, 'approvals'] })
    },
  })
}

/** Kulübün karar kayıtları (VIEW_DECISIONS), yeniden eskiye. */
export function useClubApprovals(clubId: string, enabled: boolean) {
  return useQuery({
    queryKey: ['clubs', clubId, 'approvals'],
    enabled,
    queryFn: async () => (await api.get<ApprovalRequest[]>(`/clubs/${clubId}/approvals`)).data,
  })
}

export type ApprovalDecision = { clubId: string; requestId: string; kind: 'approve' | 'reject' | 'withdraw'; reason?: string }

export function useDecideApproval() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: ApprovalDecision) => {
      const base = `/clubs/${a.clubId}/approvals/${a.requestId}`
      if (a.kind === 'reject') return (await api.post<ApprovalRequest>(`${base}/reject`, { reason: a.reason?.trim() })).data
      return (await api.post<ApprovalRequest>(`${base}/${a.kind}`)).data
    },
    onSuccess: (_d, a) => {
      void qc.invalidateQueries({ queryKey: ['clubs', 'approvals'] })
      void qc.invalidateQueries({ queryKey: ['clubs', a.clubId] })
    },
  })
}

// ——— Duyurular ———

/** Duyuru hazırla (PREPARE_ANNOUNCEMENT): yanıt onay kaydıdır; başkan hazırlarsa doğrudan yayımlanır. */
export function useSubmitAnnouncement(clubId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: { title: string; body: string }) =>
      (await api.post<ApprovalRequest>(`/clubs/${clubId}/announcements`, { title: a.title.trim(), body: a.body.trim() })).data,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: clubKeys.announcements(clubId) })
      void qc.invalidateQueries({ queryKey: ['clubs', clubId, 'approvals'] })
      void qc.invalidateQueries({ queryKey: ['clubs', 'approvals'] })
    },
  })
}

/** Yayından kaldır (başkan ya da danışman). */
export function useRemoveAnnouncement(clubId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (announcementId: string) => {
      await api.delete(`/clubs/${clubId}/announcements/${announcementId}`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: clubKeys.announcements(clubId) }),
  })
}

/** Onay sırası sizde mi: başkan onayındakinde başkan yetkisi, danışman onayındakinde danışmanlık. */
export const isMyTurn = (r: ApprovalRequest, access: ClubAccess | undefined) =>
  (r.status === 'PENDING_PRESIDENT' && can(access, 'APPROVE_AS_PRESIDENT')) || (r.status === 'PENDING_ADVISOR' && !!access?.advisor)
