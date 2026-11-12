import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { useMe } from '@/features/me/useMe'
import { courseKeys, type CourseApplication, type CourseStatus, type StaffRole } from './api'

/** Akademisyen tarafı (F-41…F-44): verdiğim dersler, ders açma, yaşam döngüsü, başvurular, öğrenciler, duyuru ve materyal. */

/** `GET /api/courses/instructor/me/courses` öğesi: kişinin kadrodaki görevi ve bekleyen başvuru sayısıyla. */
export type TeachingCourse = {
  id: string
  title: string
  code: string
  description: string | null
  credit: number
  termId: string | null
  termLabel: string | null
  section: string | null
  ects: number | null
  status: CourseStatus
  enrolledStudentCount: number
  capacity: number
  pendingApplicationCount: number
  staffRole: StaffRole
}

/**
 * Kadro görevine göre yapılabilenler (F-43). Kurallar backend'de de var; burada yalnız düğmeleri gizlemek için.
 * Koordinatör her şeyi; hoca düzenleme, yayın/arşiv ve kadro dışında; asistan yalnız teslimleri görür ve puanlar.
 */
export function abilities(role: StaffRole | undefined) {
  const coordinator = role === 'COORDINATOR'
  const assistant = role === 'ASSISTANT'
  return {
    edit: coordinator,
    lifecycle: coordinator,
    staff: coordinator,
    applications: !!role && !assistant,
    announce: !!role && !assistant,
    materials: !!role && !assistant,
    removeStudent: !!role && !assistant,
    /** Değerlendirme ekleme/düzenleme, süre uzatma ve puan ilanı (assignment-service: koordinatör ya da hoca). */
    assignments: !!role && !assistant,
    /** Teslimleri görme ve puan verme: kadrodaki herkes. */
    grade: !!role,
  }
}

/** Değerlendirme eklemek, düzenlemek ve silmek için ders kaydı açık olmalı (taslak, kayıt açık, devam ediyor). */
export const assessmentsEditable = (s: CourseStatus) => s === 'DRAFT' || s === 'OPEN' || s === 'ACTIVE'

/** Araştırma görevlisi ders koordinatörü olamaz (409 RESEARCH_ASSISTANT_NOT_COORDINATOR); "Ders aç" gösterilmez. */
export function useCanOpenCourse() {
  const me = useMe()
  return me.data?.staffCategory !== 'RESEARCH_ASSISTANT'
}

export const teachKeys = {
  mine: ['courses', 'teaching'] as const,
  pending: (id: string) => ['courses', id, 'applications', 'pending'] as const,
  applications: (id: string) => ['courses', id, 'applications', 'all'] as const,
  students: (id: string) => ['courses', id, 'students'] as const,
  history: (id: string) => ['courses', id, 'enrollment-history'] as const,
}

export function useTeachingCourses(enabled = true) {
  return useQuery({
    queryKey: teachKeys.mine,
    enabled,
    queryFn: async () => (await api.get<TeachingCourse[]>('/courses/instructor/me/courses')).data,
  })
}

/** Ders kataloğu önerisi (F-41): kod yazılırken; katalogda varsa ad, kredi ve AKTS katalogdan gelir. */
export type CatalogCourse = { id: string; code: string; title: string; credit: number; ects: number | null }

export function useCatalogSuggestions(q: string) {
  const term = q.trim()
  return useQuery({
    queryKey: ['courses', 'catalog-suggest', term.toLocaleUpperCase('tr-TR')],
    enabled: term.length >= 2,
    staleTime: 5 * 60_000,
    queryFn: async () => (await api.get<CatalogCourse[]>('/courses/catalog', { params: { q: term } })).data,
  })
}

export type CourseDraft = {
  code: string
  title: string
  description: string
  credit: string
  ects: string
  capacity: string
  termId: string
  section: string
}

export type CourseDraftErrors = Partial<Record<keyof CourseDraft, string>>

/** Kod karşılaştırması sunucudakiyle aynı (F-86): boşluk tekleşir, Türkçe büyütülür, İ→I (bil 301 = BİL 301 = BIL 301). */
export const normCode = (s: string) => s.trim().replace(/\s+/g, ' ').toLocaleUpperCase('tr-TR').replace(/İ/g, 'I')

/**
 * Ders açma formunun ön kontrolü; sınırlar CourseRequest ile aynı. Katalogdaki ders seçildiyse
 * ad, kredi ve AKTS katalogdan geldiği için denetlenmez.
 */
export function validateCourseDraft(d: CourseDraft, fromCatalog: boolean): CourseDraftErrors {
  const e: CourseDraftErrors = {}
  const int = (v: string) => /^\d+$/.test(v.trim())
  if (!d.code.trim()) e.code = 'Ders kodunu yazın.'
  if (!fromCatalog) {
    if (!d.title.trim()) e.title = 'Ders adını yazın.'
    if (!int(d.credit) || Number(d.credit) < 1) e.credit = 'Kredi en az 1 olmalı.'
    if (d.ects.trim() && (!int(d.ects) || Number(d.ects) > 60)) e.ects = 'AKTS 0 ile 60 arasında olmalı.'
  }
  if (d.section.trim() && !/^[A-Za-z0-9]{1,10}$/.test(d.section.trim()))
    e.section = 'Şube en fazla 10 harf ya da rakam olabilir (ör. 1, 2A).'
  if (!int(d.capacity) || Number(d.capacity) < 1 || Number(d.capacity) > 10000) e.capacity = 'Kontenjan 1 ile 10.000 arasında olmalı.'
  if (!d.termId) e.termId = 'Dönem seçin.'
  return e
}

/** Ders aç: multipart `course` (JSON) + isteğe bağlı görsel; `draft` taslak olarak kaydeder. */
export function useCreateCourse() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      draft,
      instructorId,
      asDraft,
      image,
    }: {
      draft: CourseDraft
      instructorId: string
      asDraft: boolean
      image: File | null
    }) => {
      const body = {
        code: normCode(draft.code),
        title: draft.title.trim(),
        description: draft.description.trim() || null,
        credit: Number(draft.credit),
        ects: draft.ects.trim() ? Number(draft.ects) : null,
        capacity: Number(draft.capacity),
        termId: draft.termId || null,
        section: draft.section.trim() || null,
        instructorId,
        draft: asDraft,
      }
      const form = new FormData()
      form.append('course', new Blob([JSON.stringify(body)], { type: 'application/json' }))
      if (image) form.append('file', image)
      return (await api.post<{ id: string; status: CourseStatus }>('/courses', form)).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['courses'] }),
  })
}

/** Ders ile ilgili her yönetim işleminden sonra ders, liste ve sayılar tazelenir. */
function useRefresh(courseId: string) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: courseKeys.detail(courseId) })
    void qc.invalidateQueries({ queryKey: ['courses', courseId] })
    void qc.invalidateQueries({ queryKey: teachKeys.mine })
  }
}

export function useUpdateCourse(courseId: string) {
  const refresh = useRefresh(courseId)
  return useMutation({
    mutationFn: async (v: { description: string; capacity: number }) => {
      await api.put(`/courses/${courseId}`, { description: v.description.trim() || null, capacity: v.capacity })
    },
    onSuccess: refresh,
  })
}

/** Yayımla (taslaktan), arşivle (tamamlanmıştan) ya da sil (yalnız taslak). */
export function useCourseLifecycle(courseId: string) {
  const qc = useQueryClient()
  const refresh = useRefresh(courseId)
  return useMutation({
    mutationFn: async (action: 'publish' | 'archive' | 'delete') => {
      if (action === 'delete') await api.delete(`/courses/${courseId}`)
      else await api.post(`/courses/${courseId}/${action}`)
    },
    onSuccess: (_d, action) => (action === 'delete' ? void qc.invalidateQueries({ queryKey: teachKeys.mine }) : refresh()),
  })
}

/** Başvuruyu yapan öğrenci bilgisiyle (hoca görünümü). */
export type StaffApplication = CourseApplication & {
  studentId: string
  studentName: string | null
  studentNumber: string | null
  studentEmail: string | null
}

export function usePendingApplications(courseId: string, enabled: boolean) {
  return useQuery({
    queryKey: teachKeys.pending(courseId),
    enabled,
    queryFn: async () => (await api.get<StaffApplication[]>(`/courses/${courseId}/applications/pending`)).data,
  })
}

export function useApplicationHistory(courseId: string, enabled: boolean) {
  return useQuery({
    queryKey: teachKeys.applications(courseId),
    enabled,
    queryFn: async () => (await api.get<StaffApplication[]>(`/courses/${courseId}/applications`)).data,
  })
}

export function useDecideApplication(courseId: string) {
  const refresh = useRefresh(courseId)
  return useMutation({
    mutationFn: async (a: { id: string; kind: 'approve' | 'reject'; reason?: string }) => {
      if (a.kind === 'approve') await api.put(`/courses/applications/${a.id}/approve`)
      else await api.put(`/courses/applications/${a.id}/reject`, { rejectionReason: a.reason?.trim() || null })
    },
    onSuccess: refresh,
  })
}

export type EnrolledStudent = {
  studentId: string
  firstName: string | null
  lastName: string | null
  studentNumber: string | null
  email: string | null
  department: string | null
  enrollmentDate: string
}

export function useEnrolledStudents(courseId: string, enabled = true) {
  return useQuery({
    queryKey: teachKeys.students(courseId),
    enabled,
    queryFn: async () => (await api.get<EnrolledStudent[]>(`/courses/${courseId}/enrolled-students`)).data,
  })
}

export type EnrollmentEvent = {
  id: string
  studentId: string
  studentName: string | null
  studentNumber: string | null
  type: 'ENROLLED' | 'WITHDRAWN' | 'REMOVED'
  reason: string | null
  occurredAt: string
  afterEnrollmentPeriod: boolean
}

export const ENROLLMENT_EVENT_LABEL: Record<EnrollmentEvent['type'], string> = {
  ENROLLED: 'Kaydoldu',
  WITHDRAWN: 'Çekildi',
  REMOVED: 'Çıkarıldı',
}

export function useEnrollmentHistory(courseId: string, enabled: boolean) {
  return useQuery({
    queryKey: teachKeys.history(courseId),
    enabled,
    queryFn: async () => (await api.get<EnrollmentEvent[]>(`/courses/${courseId}/enrollment-history`)).data,
  })
}

export function useRemoveStudent(courseId: string) {
  const refresh = useRefresh(courseId)
  return useMutation({
    mutationFn: async ({ studentId, reason }: { studentId: string; reason: string }) => {
      await api.post(`/courses/${courseId}/students/${studentId}/remove`, { reason: reason.trim() })
    },
    onSuccess: refresh,
  })
}

export function usePostAnnouncement(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { title: string; content: string }) => {
      await api.post(`/courses/${courseId}/announcements`, { title: v.title.trim(), content: v.content.trim() })
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: courseKeys.announcements(courseId) }),
  })
}

export function useDeleteAnnouncement(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/courses/announcements/${id}`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: courseKeys.announcements(courseId) }),
  })
}

export type MaterialDraft = {
  title: string
  description: string
  section: string
  kind: 'FILE' | 'LINK'
  linkUrl: string
  visible: boolean
}

export function useAddMaterial(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ draft, file }: { draft: MaterialDraft; file: File | null }) => {
      const body = {
        title: draft.title.trim(),
        description: draft.description.trim() || null,
        section: draft.section.trim() || null,
        kind: draft.kind,
        linkUrl: draft.kind === 'LINK' ? draft.linkUrl.trim() : null,
        visible: draft.visible,
      }
      const form = new FormData()
      form.append('material', new Blob([JSON.stringify(body)], { type: 'application/json' }))
      if (draft.kind === 'FILE' && file) form.append('file', file)
      await api.post(`/courses/${courseId}/materials`, form)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: courseKeys.materials(courseId) }),
  })
}

export function useUpdateMaterial(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, ...patch }: { id: string; visible?: boolean }) => {
      await api.put(`/courses/${courseId}/materials/${id}`, patch)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: courseKeys.materials(courseId) }),
  })
}

export function useDeleteMaterial(courseId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/courses/${courseId}/materials/${id}`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: courseKeys.materials(courseId) }),
  })
}

// ——— Kadro (F-43) ———

/** Akademisyen arama (`GET /api/users/search/academicians?query=`, F-87): tam ad da bulunur, yalnız aktif personel, en fazla 10 sonuç. */
export type AcademicianHit = {
  id: string
  firstName: string | null
  lastName: string | null
  title: string | null
  academicTitle: string | null
  department: string | null
  departmentId: string | null
}

export function useAcademicianSearch(q: string) {
  const query = q.trim()
  return useQuery({
    queryKey: ['users', 'search', 'academicians', query.toLocaleLowerCase('tr-TR')],
    enabled: query.length >= 2,
    staleTime: 60_000,
    queryFn: async () => (await api.get<AcademicianHit[]>('/users/search/academicians', { params: { query } })).data,
  })
}

function useStaffRefresh(courseId: string) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: courseKeys.staff(courseId) })
    void qc.invalidateQueries({ queryKey: teachKeys.mine })
  }
}

/** Kadroya ekle (koordinatör): yalnız hoca ya da asistan; koordinatör devri yöneticidedir. */
export function useAddStaff(courseId: string) {
  const refresh = useStaffRefresh(courseId)
  return useMutation({
    mutationFn: async (v: { userId: string; role: Exclude<StaffRole, 'COORDINATOR'> }) => {
      await api.post(`/courses/${courseId}/staff`, v)
    },
    onSuccess: refresh,
  })
}

export function useChangeStaffRole(courseId: string) {
  const refresh = useStaffRefresh(courseId)
  return useMutation({
    mutationFn: async (v: { userId: string; role: Exclude<StaffRole, 'COORDINATOR'> }) => {
      await api.put(`/courses/${courseId}/staff/${v.userId}`, { role: v.role })
    },
    onSuccess: refresh,
  })
}

/** Kadrodan çıkar (koordinatör) ya da kadrodan ayrıl (kişinin kendisi). */
export function useRemoveStaff(courseId: string) {
  const refresh = useStaffRefresh(courseId)
  return useMutation({
    mutationFn: async (userId: string) => {
      await api.delete(`/courses/${courseId}/staff/${userId}`)
    },
    onSuccess: refresh,
  })
}
