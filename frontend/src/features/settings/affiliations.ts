import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { getSession, patchSession, type Role } from '@/lib/auth/session'

/**
 * Hesaba ek kayıt (F-54): personel öğrenci kaydı, öğrenci personel kaydı ekler. Belgeyle birlikte doğrulayıcıya gider;
 * onaylanana kadar yetki değişmez, oturumda `pendingRequests` ile "onay bekleniyor" görünür.
 */

export type AffiliationKind = 'student' | 'academician'

export type StudentRecordDraft = {
  studentNumber: string
  programId: string
  entryYear: string
  department: string
  file: File | null
}

export type StaffRecordDraft = {
  title: string
  departmentId: string
  department: string
  officeNumber: string
  file: File | null
}

export type AffiliationField = 'studentNumber' | 'programId' | 'entryYear' | 'department' | 'title' | 'departmentId' | 'officeNumber' | 'file'
export type AffiliationErrors = Partial<Record<AffiliationField, string>>

/** Sunucu hata kodu → alan; eşleşmeyenler (AFFILIATION_PENDING gibi) genel uyarı olur. */
export const AFFILIATION_CODE_FIELD: Record<string, AffiliationField> = {
  STUDENT_NUMBER_REQUIRED: 'studentNumber',
  INVALID_STUDENT_NUMBER: 'studentNumber',
  STUDENT_NUMBER_TAKEN: 'studentNumber',
  PROGRAM_NOT_FOUND: 'programId',
  PROGRAM_INACTIVE: 'programId',
  DEPARTMENT_NOT_FOUND: 'departmentId',
  DEPARTMENT_INACTIVE: 'departmentId',
  TITLE_REQUIRED: 'title',
  INVALID_TITLE: 'title',
  STUDENT_DOCUMENT_REQUIRED: 'file',
  ID_CARD_REQUIRED: 'file',
}

/** `catalog`: akademik birim listesi var; yoksa bölüm yazılarak başvurulur. `needsProgram`: seçilen bölümde program var. */
export function validateStudentRecord(d: StudentRecordDraft, opts: { catalog: boolean; needsProgram: boolean }): AffiliationErrors {
  const e: AffiliationErrors = {}
  if (!d.studentNumber.trim()) e.studentNumber = 'Öğrenci numaranızı yazın.'
  if (opts.catalog && !d.department) e.department = 'Bölümünüzü seçin.'
  if (!opts.catalog && !d.department.trim()) e.department = 'Bölümünüzü yazın.'
  if (opts.needsProgram && !d.programId) e.programId = 'Programınızı seçin.'
  if (!d.entryYear) e.entryYear = 'Giriş yılını seçin.'
  if (!d.file) e.file = 'Öğrenci belgesini ekleyin.'
  return e
}

export function validateStaffRecord(d: StaffRecordDraft, opts: { catalog: boolean }): AffiliationErrors {
  const e: AffiliationErrors = {}
  if (!d.title) e.title = 'Unvanınızı seçin.'
  if (opts.catalog && !d.departmentId) e.departmentId = 'Bölümünüzü seçin.'
  if (!opts.catalog && !d.department.trim()) e.department = 'Bölümünüzü yazın.'
  if (!d.file) e.file = 'Personel kimlik kartını ekleyin.'
  return e
}

export function studentRecordRequest(d: StudentRecordDraft) {
  return {
    studentNumber: d.studentNumber.replace(/\s+/g, ''),
    programId: d.programId || null,
    entryYear: Number(d.entryYear),
    department: d.department.trim(),
  }
}

export function staffRecordRequest(d: StaffRecordDraft) {
  return {
    title: d.title,
    departmentId: d.departmentId || null,
    department: d.department.trim(),
    officeNumber: d.officeNumber.trim() || null,
  }
}

const PENDING: Record<AffiliationKind, { role: Role; request: string }> = {
  student: { role: 'ROLE_PENDING_STUDENT', request: 'STUDENT' },
  academician: { role: 'ROLE_PENDING_ACADEMICIAN', request: 'ACADEMICIAN' },
}

export const pendingRequestOf = (kind: AffiliationKind) => PENDING[kind].request

export function useAddAffiliation(kind: AffiliationKind) {
  return useMutation({
    mutationFn: async (v: { request: object; file: File }) => {
      const body = new FormData()
      body.append('request', new Blob([JSON.stringify(v.request)], { type: 'application/json' }))
      body.append(kind === 'student' ? 'studentDocument' : 'idCardImage', v.file)
      await api.post(`/auth/affiliations/${kind}`, body)
    },
    onSuccess: () => {
      // Sunucu rolü ekledi; sıradaki jeton yenilemesini beklemeden "onay bekleniyor" görünsün.
      const s = getSession()
      if (s) patchSession({ roles: [...s.roles, PENDING[kind].role], pendingRequests: [...s.pendingRequests, PENDING[kind].request] })
    },
  })
}
