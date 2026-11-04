import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

/** `GET /api/users/academic/catalog`: yalnız etkin birimler, her düzey ada göre sıralı (F-53). */
export type ProgramLevel = 'ASSOCIATE' | 'BACHELOR' | 'MASTER' | 'DOCTORATE'
export type CatalogProgram = { id: string; code: string; name: string; level: ProgramLevel; durationYears: number; active: boolean }
export type CatalogDepartment = { id: string; code: string; name: string; active: boolean; programs: CatalogProgram[] }
export type CatalogFaculty = { id: string; code: string; name: string; active: boolean; departments: CatalogDepartment[] }

/** `GET /api/users/academic/titles` (F-54). */
export type AcademicTitle = { code: string; label: string; category: 'FACULTY_MEMBER' | 'LECTURER' | 'RESEARCH_ASSISTANT' }

export const LEVEL_LABEL: Record<ProgramLevel, string> = {
  ASSOCIATE: 'Ön lisans',
  BACHELOR: 'Lisans',
  MASTER: 'Yüksek lisans',
  DOCTORATE: 'Doktora',
}

export function useCatalog() {
  return useQuery({
    queryKey: ['academic', 'catalog'],
    staleTime: 30 * 60_000,
    queryFn: async () => (await api.get<CatalogFaculty[]>('/users/academic/catalog')).data,
  })
}

export function useTitles(enabled: boolean) {
  return useQuery({
    queryKey: ['academic', 'titles'],
    enabled,
    staleTime: 60 * 60_000,
    queryFn: async () => (await api.get<AcademicTitle[]>('/users/academic/titles')).data,
  })
}

export type ApplicationKind = 'student' | 'academician'

/** Başvuru gövdesi (RegisterRequest). Rolün kullanmadığı alanlar gönderilmez. */
export type ApplicationRequest = {
  email: string
  password: string
  firstName: string
  lastName: string
  studentId?: string
  programId?: string
  entryYear?: number
  department?: string
  departmentId?: string
  title?: string
  officeNumber?: string
}

/**
 * Hesap başvurusu: multipart; JSON `request` parçası + belge
 * (öğrencide `studentDocument`, akademisyende `idCardImage`). Başarıda backend düz metin döner.
 */
export async function submitApplication(kind: ApplicationKind, request: ApplicationRequest, file: File) {
  const body = new FormData()
  body.append('request', new Blob([JSON.stringify(request)], { type: 'application/json' }))
  body.append(kind === 'student' ? 'studentDocument' : 'idCardImage', file)
  await api.post(kind === 'student' ? '/auth/request/student-account' : '/auth/request/academician-account', body)
}

/** Seçilebilir giriş yılları: bu yıl ve önceki 11 yıl, yeniden eskiye. */
export const ENTRY_YEARS = Array.from({ length: 12 }, (_, i) => String(new Date().getFullYear() - i))

/** Belge kuralları: sunucu dosya imzasına bakar; tek dosya en fazla 5 MB (multipart sınırı). */
export const DOCUMENT_ACCEPT = 'application/pdf,image/jpeg,image/png,image/webp,image/gif'
export const DOCUMENT_MAX_BYTES = 5 * 1024 * 1024
