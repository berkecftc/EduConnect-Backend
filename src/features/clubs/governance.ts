import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { type ClubCategory, type ClubDetails, type ClubMember, type ClubRole } from './api'
import type { ClubCreationRequest } from './advise'
import type { ApprovalRequest, ApprovalStatus } from './manage'

/**
 * Kulüp yönetimi (F-28, F-85): kuruluş başvurusu ve kurucu davetleri; görev verme, görevden alma, istifa,
 * üyelikten çıkarma; kulüp bilgileri ve logo değişikliği; danışman önerme ve kapatma talebi.
 * Hepsi onay kaydı olarak açılır; kim onaylar backend'de belirlenir (başkan, danışman ya da önerilen akademisyen).
 */

export type { ClubCreationRequest }

/** Verilebilecek görevler (başkanlık seçimle ya da danışman kararıyla değişir) ve en fazla kaç kişide olabileceği. */
export const ASSIGNABLE_ROLES: { code: ClubRole; label: string; limit: number | null }[] = [
  { code: 'VICE_PRESIDENT', label: 'Başkan Yardımcısı', limit: 1 },
  { code: 'GENERAL_SECRETARY', label: 'Genel Sekreter', limit: 1 },
  { code: 'TREASURER', label: 'Sayman', limit: 1 },
  { code: 'BOARD_MEMBER', label: 'Yönetim Kurulu Üyesi', limit: 7 },
  { code: 'AUDITOR', label: 'Denetim Kurulu Üyesi', limit: 3 },
  { code: 'EVENT_COORDINATOR', label: 'Etkinlik Koordinatörü', limit: null },
  { code: 'COMMUNICATIONS_OFFICER', label: 'İletişim ve Sosyal Medya Sorumlusu', limit: null },
  { code: 'MEMBERSHIP_OFFICER', label: 'Üyelik Sorumlusu', limit: null },
  { code: 'SPONSORSHIP_OFFICER', label: 'Sponsorluk ve Dış İlişkiler Sorumlusu', limit: null },
]

/** Eski uyumluluk adı (ROLE_TREASURER …) yeni koda çevrilir; görev sayımı ve karşılaştırma için. */
export const canonicalRole = (code: ClubRole | null | undefined): ClubRole =>
  !code
    ? 'MEMBER'
    : code === 'ROLE_CLUB_OFFICIAL'
      ? 'PRESIDENT'
      : code === 'ROLE_SECRETARY'
        ? 'GENERAL_SECRETARY'
        : code.replace(/^ROLE_/, '')

/** Görevdeki kişi sayısı (kurul listesinden). */
export function holderCount(board: ClubMember[], code: ClubRole) {
  return board.filter((m) => canonicalRole(m.role) === code).length
}

export const governanceKeys = {
  invitations: ['clubs', 'founding', 'invitations'] as const,
  creations: ['clubs', 'founding', 'mine'] as const,
  advisorChanges: (clubId: string) => ['clubs', clubId, 'advisor-changes'] as const,
}

// ——— Kuruluş ———

/** Kurucu olarak davet edildiğim kuruluş başvuruları. */
export function useFoundingInvitations(enabled = true) {
  return useQuery({
    queryKey: governanceKeys.invitations,
    enabled,
    queryFn: async () => (await api.get<ClubCreationRequest[]>('/clubs/founding-invitations')).data,
  })
}

/** Yaptığım kuruluş başvuruları. */
export function useMyCreationRequests(enabled = true) {
  return useQuery({
    queryKey: governanceKeys.creations,
    enabled,
    queryFn: async () => (await api.get<ClubCreationRequest[]>('/clubs/my-creation-requests')).data,
  })
}

export type ClubCreationDraft = { name: string; about: string; advisorId: string | undefined; founders: string }
export type ClubCreationErrors = Partial<Record<'name' | 'about' | 'advisorId' | 'founders', string>>

export const CLUB_NAME_MAX = 255
export const ABOUT_MAX = 5000
export const FOUNDERS_MAX = 50

/** Yazılan ya da yapıştırılan kurucu numaraları: satır, virgül ya da boşlukla ayrılır; tekrarlar atılır. */
export const parseFounderNumbers = (text: string) => [...new Set(text.split(/[\s,;]+/).filter(Boolean))]

export function validateCreationDraft(d: ClubCreationDraft): ClubCreationErrors {
  const e: ClubCreationErrors = {}
  if (!d.name.trim()) e.name = 'Kulübün adını yazın.'
  if (!d.advisorId) e.advisorId = 'Danışman olacak akademisyeni listeden seçin.'
  if (parseFounderNumbers(d.founders).length > FOUNDERS_MAX) e.founders = `En fazla ${FOUNDERS_MAX} kurucu eklenebilir.`
  return e
}

/**
 * Kuruluş başvurusu: yanıt düz metin, başvuru "Kuruluş başvurularım"dan izlenir. Kurucular öğrenci numarasıyla eklenir
 * (F-89); asgari sayı sunucu ayarı, eksikse `FOUNDERS_REQUIRED` mesajı sayıyı söyler.
 */
export function useRequestClubCreation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (d: ClubCreationDraft) => {
      await api.post('/clubs/request-creation', {
        name: d.name.trim(),
        about: d.about.trim() || null,
        academicAdvisorId: d.advisorId,
        founderStudentNumbers: parseFounderNumbers(d.founders),
      })
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['clubs', 'founding'] }),
  })
}

/** Kurucu davetine yanıt: katıl ya da katılma. */
export function useFounderResponse() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { requestId: string; confirm: boolean }) =>
      (await api.post<ClubCreationRequest>(`/clubs/creation-requests/${v.requestId}/founders/${v.confirm ? 'confirm' : 'decline'}`)).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['clubs', 'founding'] }),
  })
}

// ——— Kulüp üzerinde talepler ———

/** Kulübün bütün verisi (ayrıntı, kurul, yetki, kararlar) ve onay kutusu tazelenir. */
function useClubRefresh(clubId: string) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: ['clubs', clubId] })
    void qc.invalidateQueries({ queryKey: ['clubs', 'approvals'] })
    void qc.invalidateQueries({ queryKey: ['clubs', 'memberships'] })
  }
}

/** Görev ver ya da değiştir (PROPOSE_POSITION_CHANGE): öğrenci numarasıyla ya da kurul listesinden. Danışman onaylar. */
export function useProposeRole(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { studentId?: string; studentNumber?: string; role: ClubRole }) =>
      (
        await api.post(`/clubs/${clubId}/role-change-requests`, {
          ...(v.studentId ? { studentId: v.studentId } : { studentNumber: v.studentNumber?.trim() }),
          requestedRole: v.role,
        })
      ).data,
    onSuccess: refresh,
  })
}

/** Görevden al: kişi üye olarak kalır. Danışman onaylar. */
export function useRevokeRole(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (studentId: string) => {
      await api.delete(`/clubs/${clubId}/members/${studentId}/role`)
    },
    onSuccess: refresh,
  })
}

/** Görevden ayrıl (istifa): üyelik sürer. Başkan dışındakiler önce başkan onayına gider. */
export function useResign(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (note: string) =>
      (await api.post<ApprovalRequest>(`/clubs/${clubId}/resignations`, { note: note.trim() || null })).data,
    onSuccess: refresh,
  })
}

/** Üyelikten çıkarma talebi: gerekçe zorunlu, danışman karar verir. */
export function useRequestExpulsion(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { studentId: string; reason: string }) =>
      (await api.post<ApprovalRequest>(`/clubs/${clubId}/expulsion-requests`, { studentId: v.studentId, reason: v.reason.trim() })).data,
    onSuccess: refresh,
  })
}

export const REASON_MAX = 1000

// ——— Kulüp bilgileri ve logo ———

export type ProfileDraft = {
  about: string
  category: ClubCategory | ''
  contactEmail: string
  websiteUrl: string
  instagramUrl: string
  xUrl: string
  linkedinUrl: string
}

export type ProfileDraftErrors = Partial<Record<keyof ProfileDraft, string>>

export const LINK_FIELDS = ['websiteUrl', 'instagramUrl', 'xUrl', 'linkedinUrl'] as const

export function profileDraftOf(c: ClubDetails): ProfileDraft {
  const p = c.profile
  return {
    about: c.about ?? '',
    category: p?.category ?? '',
    contactEmail: p?.contactEmail ?? '',
    websiteUrl: p?.websiteUrl ?? '',
    instagramUrl: p?.instagramUrl ?? '',
    xUrl: p?.xUrl ?? '',
    linkedinUrl: p?.linkedinUrl ?? '',
  }
}

/** Bağlantılar https ile başlamalı, e-posta geçerli olmalı (sunucu kuralı). */
export function validateProfileDraft(d: ProfileDraft): ProfileDraftErrors {
  const e: ProfileDraftErrors = {}
  const email = d.contactEmail.trim()
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) e.contactEmail = 'Geçerli bir e-posta adresi yazın.'
  for (const k of LINK_FIELDS) {
    const v = d[k].trim()
    if (v && !/^https:\/\/\S+$/.test(v)) e[k] = 'Bağlantı https:// ile başlamalı ve boşluk içermemeli.'
  }
  return e
}

/**
 * Değişen alanlar: değişmeyen alan gönderilmez (null = olduğu gibi kalsın); boşaltılan alan "" gider ve silinir.
 * Hiçbir alan değişmediyse null.
 */
export function profileChanges(before: ProfileDraft, after: ProfileDraft): Partial<ProfileDraft> | null {
  const out: Partial<ProfileDraft> = {}
  for (const k of Object.keys(after) as (keyof ProfileDraft)[]) {
    const v = after[k].trim()
    if (v !== before[k].trim()) (out as Record<string, string>)[k] = v
  }
  return Object.keys(out).length ? out : null
}

export function useProfileChange(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { changes: Partial<ProfileDraft>; note: string }) =>
      (await api.post<ApprovalRequest>(`/clubs/${clubId}/profile-change-requests`, { ...v.changes, note: v.note.trim() || null })).data,
    onSuccess: refresh,
  })
}

export const LOGO_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
export const LOGO_MAX_BYTES = 5 * 1024 * 1024

export function logoProblem(file: File): string | null {
  if (!LOGO_TYPES.includes(file.type)) return 'Logo JPEG, PNG, GIF ya da WEBP olmalı.'
  if (file.size > LOGO_MAX_BYTES) return 'Logo en fazla 5 MB olabilir.'
  return null
}

export function useLogoChange(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { file: File; note: string }) => {
      const form = new FormData()
      form.append('file', v.file)
      if (v.note.trim()) form.append('note', v.note.trim())
      return (await api.post<ApprovalRequest>(`/clubs/${clubId}/logo-change-requests`, form)).data
    },
    onSuccess: refresh,
  })
}

// ——— Danışman ve kapatma ———

/** Danışman önerisi: önerilen akademisyen kabul ederse kulübün danışmanı olur. */
export type AdvisorChange = {
  id: string
  clubId: string
  clubName: string | null
  proposedAdvisorId: string
  proposedAdvisorName?: string | null
  requestedBy: string
  requestedByName?: string | null
  message: string | null
  status: ApprovalStatus
  rejectionReason: string | null
  createdAt: string
  decidedAt: string | null
}

export function useAdvisorChanges(clubId: string, enabled: boolean) {
  return useQuery({
    queryKey: governanceKeys.advisorChanges(clubId),
    enabled,
    queryFn: async () => (await api.get<AdvisorChange[]>(`/clubs/${clubId}/advisor-change-requests`)).data,
  })
}

export function useProposeAdvisor(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { advisorId: string; message: string }) =>
      (
        await api.post<AdvisorChange>(`/clubs/${clubId}/advisor-change-requests`, {
          proposedAdvisorId: v.advisorId,
          message: v.message.trim() || null,
        })
      ).data,
    onSuccess: refresh,
  })
}

export function useCancelAdvisorChange(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (requestId: string) => {
      await api.delete(`/clubs/${clubId}/advisor-change-requests/${requestId}`)
    },
    onSuccess: refresh,
  })
}

/** Kapatma talebi: danışman onaylarsa kulüp kapanır. */
export function useRequestClosure(clubId: string) {
  const refresh = useClubRefresh(clubId)
  return useMutation({
    mutationFn: async (reason: string) =>
      (await api.post<ApprovalRequest>(`/clubs/${clubId}/closure-requests`, { reason: reason.trim() })).data,
    onSuccess: refresh,
  })
}
