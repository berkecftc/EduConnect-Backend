import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

/**
 * Hesap yönetimi (F-55, F-56, F-59) ve görevli hesapları (F-57).
 * Hesap yöneticisi yalnız öğrenci ve akademisyen hesaplarını yönetir; yönetici ve görevli hesapları burada yönetilmez.
 */

export type StudentStatus = 'ACTIVE' | 'ON_LEAVE' | 'GRADUATED' | 'WITHDRAWN' | 'EXPELLED' | 'TRANSFERRED_OUT'
export type StaffStatus = 'ACTIVE' | 'ON_LEAVE' | 'RETIRED' | 'RESIGNED' | 'TERMINATED'

/** `GET /api/auth/account-management/users?query=` öğesi. Ad taşımaz; e-posta ve numarayla tanınır. */
export type ManagedAccount = {
  id: string
  email: string
  roles: string[]
  status: 'ACTIVE' | 'SUSPENDED'
  studentNumber: string | null
  studentStatus: StudentStatus | null
  staffStatus: StaffStatus | null
  /** Bütün bağlılıklar sona erdiyse hesabın kapanacağı an. */
  closureDueAt: string | null
  firstName?: string | null
  lastName?: string | null
}

export type AffiliationChange = {
  affiliation: 'STUDENT' | 'STAFF'
  previousStatus: string | null
  status: string
  effectiveDate: string
  reason: string | null
  changedBy: string | null
  changedAt: string
}

export type AffiliationStatus = {
  userId: string
  studentStatus: StudentStatus | null
  staffStatus: StaffStatus | null
  closureDueAt: string | null
  history: AffiliationChange[]
}

export const STUDENT_STATUS_LABEL: Record<StudentStatus, string> = {
  ACTIVE: 'Aktif',
  ON_LEAVE: 'Kaydı dondurulmuş',
  GRADUATED: 'Mezun',
  WITHDRAWN: 'Kaydını sildirdi',
  EXPELLED: 'İlişiği kesildi',
  TRANSFERRED_OUT: 'Yatay geçişle ayrıldı',
}

export const STAFF_STATUS_LABEL: Record<StaffStatus, string> = {
  ACTIVE: 'Aktif',
  ON_LEAVE: 'İzinli',
  RETIRED: 'Emekli',
  RESIGNED: 'İstifa etti',
  TERMINATED: 'Görevi sona erdi',
}

/** Bu durumlar bağlılığı bitirir; başka bağlılık yoksa hesap kapanır (F-56). */
export const ENDED_STUDENT: StudentStatus[] = ['GRADUATED', 'WITHDRAWN', 'EXPELLED', 'TRANSFERRED_OUT']
export const ENDED_STAFF: StaffStatus[] = ['RETIRED', 'RESIGNED', 'TERMINATED']

export const ROLE_LABEL: Record<string, string> = {
  ROLE_STUDENT: 'Öğrenci',
  ROLE_ACADEMICIAN: 'Akademisyen',
  ROLE_CLUB_OFFICIAL: 'Kulüp yöneticisi',
  ROLE_STAFF: 'Görevli',
  ROLE_ADMIN: 'Yönetici',
}

const BASE = '/auth/account-management/users'

export const accountKeys = {
  search: (q: string) => ['manage', 'accounts', 'search', q] as const,
  affiliations: (id: string) => ['manage', 'accounts', id, 'affiliations'] as const,
  staff: ['manage', 'staff-accounts'] as const,
}

/** Arama: en az 3 karakter; öğrenci numarası tam, e-posta içerir (en fazla 50). */
export function useAccountSearch(q: string) {
  const query = q.trim()
  return useQuery({
    queryKey: accountKeys.search(query.toLocaleLowerCase('tr-TR')),
    enabled: query.length >= 3,
    queryFn: async () => (await api.get<ManagedAccount[]>(BASE, { params: { query } })).data,
  })
}

export function useAffiliations(userId: string | null) {
  return useQuery({
    queryKey: accountKeys.affiliations(userId ?? ''),
    enabled: !!userId,
    queryFn: async () => (await api.get<AffiliationStatus>(`${BASE}/${userId}/affiliations`)).data,
  })
}

export type AccountAction =
  | { kind: 'studentStatus' | 'staffStatus'; status: string; effectiveDate: string; reason: string }
  | { kind: 'suspend'; reason: string }
  | { kind: 'reactivate' }
  | { kind: 'email'; newEmail: string; reason: string }

/** Hesap işlemleri; başarıda arama sonuçları ve bağlılık geçmişi tazelenir. */
export function useAccountAction(userId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (a: AccountAction) => {
      const url = `${BASE}/${userId}`
      switch (a.kind) {
        case 'studentStatus':
        case 'staffStatus':
          return void (await api.put(`${url}/${a.kind === 'studentStatus' ? 'student-status' : 'staff-status'}`, {
            status: a.status,
            effectiveDate: a.effectiveDate || null,
            reason: a.reason.trim() || null,
          }))
        case 'suspend':
          return void (await api.put(`${url}/suspend`, { reason: a.reason.trim() || null }))
        case 'reactivate':
          return void (await api.put(`${url}/reactivate`))
        case 'email':
          return void (await api.put(`${url}/email`, { newEmail: a.newEmail.trim(), reason: a.reason.trim() || null }))
      }
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['manage', 'accounts'] }),
  })
}

/** Sunucu hata kodu → hesap yöneticisine anlaşılır mesaj (F-59). */
export function accountErrorMessage(code: string | undefined, fallback: string) {
  switch (code) {
    case 'TARGET_NOT_MANAGED':
      return 'Yönetici ve görevli hesapları burada yönetilmez.'
    case 'STATUS_UNCHANGED':
      return 'Hesap zaten bu durumda.'
    case 'EMAIL_UNCHANGED':
      return 'Yeni adres mevcut adresle aynı.'
    case 'EMAIL_TAKEN':
      return 'Bu e-posta adresi başka bir hesapta kullanılıyor.'
    default:
      return fallback
  }
}

// ——— Görevli hesapları (yönetici) ———

export type StaffPermission = 'STUDENT_VERIFIER' | 'STAFF_VERIFIER' | 'MODERATOR' | 'CAMPUS_PUBLISHER' | 'ACCOUNT_MANAGER'

export type StaffAccount = {
  id: string
  email: string
  displayName: string
  status: 'ACTIVE' | 'SUSPENDED'
  /** Fakülteyle sınırlı yetki fakülte başına bir kayıt; `facultyId` null ise bütün kurum. */
  grants: { permission: StaffPermission; facultyId: string | null }[]
}

export type GrantDraft = { permission: StaffPermission; facultyIds: string[] }

export const PERMISSIONS: { value: StaffPermission; title: string; description: string; scoped?: boolean }[] = [
  {
    value: 'STUDENT_VERIFIER',
    title: 'Öğrenci doğrulama',
    description: 'Öğrenci başvurularını ve öğrencilerin profil taleplerini değerlendirir. Fakülteyle sınırlanabilir.',
    scoped: true,
  },
  {
    value: 'STAFF_VERIFIER',
    title: 'Personel doğrulama',
    description: 'Akademisyen başvurularını ve akademisyenlerin profil taleplerini değerlendirir.',
  },
  { value: 'MODERATOR', title: 'Moderasyon', description: 'İncelemedeki gönderi ve yorumlara, şikâyetlere ve itirazlara karar verir.' },
  {
    value: 'CAMPUS_PUBLISHER',
    title: 'Kampüs etkinlikleri',
    description: 'Kulüp dışı kampüs etkinliklerini yayımlar, erteler ve iptal eder.',
  },
  {
    value: 'ACCOUNT_MANAGER',
    title: 'Hesap yönetimi',
    description: 'Öğrencilik ve personel durumunu, askıya almayı ve e-posta değişikliğini yönetir.',
  },
]

export const PERMISSION_TITLE = Object.fromEntries(PERMISSIONS.map((p) => [p.value, p.title])) as Record<StaffPermission, string>

/** Kayıtlardaki fakülte başına yetkileri yetki başına tek taslağa toplar. */
export function grantsToDraft(grants: StaffAccount['grants']): GrantDraft[] {
  const map = new Map<StaffPermission, string[]>()
  for (const g of grants)
    map.set(g.permission, g.facultyId ? [...(map.get(g.permission) ?? []), g.facultyId] : (map.get(g.permission) ?? []))
  return [...map.entries()].map(([permission, facultyIds]) => ({ permission, facultyIds }))
}

export function useStaffAccounts() {
  return useQuery({
    queryKey: accountKeys.staff,
    queryFn: async () => (await api.get<StaffAccount[]>('/auth/admin/staff-accounts')).data,
  })
}

export function useCreateStaffAccount() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { email: string; displayName: string; grants: GrantDraft[] }) =>
      (
        await api.post<StaffAccount>('/auth/admin/staff-accounts', {
          email: v.email.trim(),
          displayName: v.displayName.trim(),
          grants: v.grants,
        })
      ).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: accountKeys.staff }),
  })
}

/** Yetkileri değiştirir (hepsinin yerine geçer); görevlinin oturumları kapanır. */
export function useUpdateStaffGrants() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { id: string; grants: GrantDraft[] }) => {
      await api.put(`/auth/admin/staff-accounts/${v.id}/permissions`, { grants: v.grants })
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: accountKeys.staff }),
  })
}

/** Görevli hesabını askıya al ya da yeniden etkinleştir (yönetici ucu). */
export function useStaffSuspension() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { id: string; suspend: boolean; reason?: string }) => {
      if (v.suspend) await api.put(`/auth/admin/users/${v.id}/suspend`, { reason: v.reason?.trim() || null })
      else await api.put(`/auth/admin/users/${v.id}/reactivate`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: accountKeys.staff }),
  })
}
