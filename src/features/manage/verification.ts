import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { hasRole, useSession } from '@/lib/auth/session'
import { LEVEL_LABEL, type CatalogFaculty } from '@/features/auth/register/api'

/**
 * Doğrulayıcı paneli (F-58, F-54, F-55): öğrenci ve akademisyen başvuruları, profil değişiklik talepleri.
 * Öğrenci doğrulayıcısı yalnız yetkili olduğu fakültelerin başvurularını görür; akademisyen başvurularını
 * personel doğrulayıcısı görür. Yönetici hepsini görür.
 */

/** `GET /api/auth/verification/students` öğesi. Belge bağlantısı 15 dakika geçerli (MinIO). */
export type StudentRequest = {
  id: number
  firstName: string | null
  lastName: string | null
  email: string | null
  studentNumber: string | null
  department: string | null
  studentDocumentUrl: string | null
  emailVerified: boolean
  /** Hesap zaten var; bu, mevcut hesaba öğrenci kaydı ekleme başvurusu (F-54). */
  additionalAffiliation: boolean
  programId: string | null
  createdAt?: string | null
}

/** `GET /api/auth/verification/academicians` öğesi. Onay ve ret `userId` ile yapılır. */
export type AcademicianRequest = {
  id: number
  userId: string
  firstName: string | null
  lastName: string | null
  title: string | null
  department: string | null
  officeNumber: string | null
  idCardImageUrl: string | null
  emailVerified: boolean
  /** Kişi zaten öğrenci; bu, mevcut hesaba personel kaydı ekleme başvurusu. */
  additionalAffiliation: boolean
  createdAt?: string | null
}

export type ChangeStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

/** Profil değişiklik talebi (ProfileChangeResponse): yalnız değişmesi istenen alanlar dolu. */
export type ChangeRequest = {
  id: string
  userId: string
  currentName: string | null
  firstName: string | null
  lastName: string | null
  academicTitle: string | null
  titleLabel: string | null
  programId: string | null
  departmentId: string | null
  reason: string
  status: ChangeStatus
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
}

export const verifyKeys = {
  students: ['manage', 'verification', 'students'] as const,
  academicians: ['manage', 'verification', 'academicians'] as const,
  changes: (status: ChangeStatus) => ['manage', 'change-requests', status] as const,
}

/** Belge bağlantıları 15 dakika geçerli; liste açıkken 10 dakikada bir tazelenir. */
const FRESH_LINKS = 10 * 60_000

export function useStudentRequests(enabled = true) {
  return useQuery({
    queryKey: verifyKeys.students,
    enabled,
    refetchInterval: FRESH_LINKS,
    queryFn: async () => (await api.get<StudentRequest[]>('/auth/verification/students')).data,
  })
}

export function useAcademicianRequests(enabled = true) {
  return useQuery({
    queryKey: verifyKeys.academicians,
    enabled,
    refetchInterval: FRESH_LINKS,
    queryFn: async () => (await api.get<AcademicianRequest[]>('/auth/verification/academicians')).data,
  })
}

export type VerifyDecision = { kind: 'student' | 'academician'; id: string | number; approve: boolean; reason?: string }

/** Onay ya da ret. Ret gerekçesi sorgu parametresiyle gider; öğrenci retinde yüklenen belge silinir. */
export function useDecideRegistration() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (d: VerifyDecision) => {
      const base = `/auth/verification/${d.kind === 'student' ? 'students' : 'academicians'}/${d.id}`
      if (d.approve) await api.post(`${base}/approve`)
      else await api.post(`${base}/reject`, null, { params: d.reason?.trim() ? { reason: d.reason.trim() } : {} })
    },
    onSuccess: (_d, v) => void qc.invalidateQueries({ queryKey: v.kind === 'student' ? verifyKeys.students : verifyKeys.academicians }),
  })
}

/** Yönetici talepleri kendi ucundan görür (kapsamsız); doğrulayıcılar fakülte kapsamıyla. */
function useChangeBase() {
  const session = useSession()
  return hasRole(session, 'ROLE_ADMIN') ? '/users/admin/change-requests' : '/users/verification/change-requests'
}

export function useChangeRequests(status: ChangeStatus, enabled = true) {
  const base = useChangeBase()
  return useQuery({
    queryKey: verifyKeys.changes(status),
    enabled,
    queryFn: async () => (await api.get<ChangeRequest[]>(base, { params: { status } })).data,
  })
}

export function useDecideChange() {
  const base = useChangeBase()
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (d: { id: string; approve: boolean; note?: string }) => {
      if (d.approve) await api.post(`${base}/${d.id}/approve`)
      else await api.post(`${base}/${d.id}/reject`, { note: d.note?.trim() || null })
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['manage', 'change-requests'] }),
  })
}

/** Katalogdan program ve bölüm adları: "Bilgisayar Mühendisliği (Lisans), Mühendislik Fakültesi". */
export function catalogIndex(catalog: CatalogFaculty[] | undefined) {
  const programs = new Map<string, string>()
  const departments = new Map<string, string>()
  for (const f of catalog ?? []) {
    for (const d of f.departments) {
      departments.set(d.id, `${d.name}, ${f.name}`)
      for (const p of d.programs) programs.set(p.id, `${p.name} (${LEVEL_LABEL[p.level]}), ${f.name}`)
    }
  }
  return {
    program: (id: string | null) => (id ? programs.get(id) : undefined),
    department: (id: string | null) => (id ? departments.get(id) : undefined),
  }
}

/** Sunucu hatası → doğrulayıcıya anlaşılır mesaj. */
export function verifyErrorMessage(code: string | undefined, fallback: string) {
  switch (code) {
    case 'OUT_OF_SCOPE':
      return 'Bu kayıt yetki kapsamınız dışında.'
    case 'STUDENT_NUMBER_TAKEN':
      return 'Bu öğrenci numarası başka bir hesapta kayıtlı; başvuruyu reddedip öğrenciden numarasını kontrol etmesini isteyin.'
    case 'CHANGE_REQUEST_CLOSED':
      return 'Bu talep zaten sonuçlandırılmış.'
    default:
      return fallback
  }
}
