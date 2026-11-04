import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export type CourseStatus = 'DRAFT' | 'OPEN' | 'ACTIVE' | 'COMPLETED' | 'ARCHIVED'

/** `GET /api/courses/my-courses` öğesi (aktif kayıtlar). */
export type EnrolledCourse = {
  id: string
  title: string
  code: string
  description: string | null
  credit: number
  semester: string | null
  termId: string | null
  termLabel: string | null
  section: string | null
  ects: number | null
  status: CourseStatus
  imageUrl: string | null
  instructorId: string | null
  instructorName: string | null
  /** Unvan etiketi ("Doç. Dr.") ve kodu ("ASSOCIATE_PROFESSOR"); personel değilse ya da bilinmiyorsa null (F-84). */
  instructorTitle?: string | null
  instructorAcademicTitle?: string | null
  enrollmentDate: string
}

/** `GET /api/courses/{id}`. */
export type Course = Omit<EnrolledCourse, 'enrollmentDate'> & { capacity: number; enrolledStudentCount: number }

export type Term = {
  id: string
  academicYear: number
  season: 'FALL' | 'SPRING' | 'SUMMER'
  label: string
  startsOn: string
  endsOn: string
  enrollmentOpensOn: string | null
  enrollmentClosesOn: string | null
}

export type StaffRole = 'COORDINATOR' | 'INSTRUCTOR' | 'ASSISTANT'
export type CourseStaff = {
  userId: string
  role: StaffRole
  name: string
  title?: string | null
  academicTitle?: string | null
  department: string | null
  since: string
}

export type Material = {
  id: string
  courseId: string
  title: string
  description: string | null
  section: string | null
  sortOrder: number
  kind: 'FILE' | 'LINK'
  fileName: string | null
  linkUrl: string | null
  visible: boolean
  createdAt: string
  updatedAt: string
}

export type Announcement = {
  id: string
  courseId: string
  title: string
  content: string
  createdAt: string
  createdByName: string | null
}

export type ApplicationStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'CLOSED'
export type CourseApplication = {
  id: string
  courseId: string
  courseTitle: string
  courseCode: string
  status: ApplicationStatus
  applicationDate: string
  processedDate: string | null
  rejectionReason: string | null
}

export const COURSE_STATUS_LABEL: Record<CourseStatus, string> = {
  DRAFT: 'Taslak',
  OPEN: 'Kayıt açık',
  ACTIVE: 'Devam ediyor',
  COMPLETED: 'Tamamlandı',
  ARCHIVED: 'Arşiv',
}

/** Ders durumu rozetinin tonu. */
export const COURSE_STATUS_TONE: Record<CourseStatus, 'neutral' | 'info' | 'success'> = {
  DRAFT: 'neutral',
  OPEN: 'info',
  ACTIVE: 'success',
  COMPLETED: 'neutral',
  ARCHIVED: 'neutral',
}

export const STAFF_ROLE_LABEL: Record<StaffRole, string> = {
  COORDINATOR: 'Koordinatör',
  INSTRUCTOR: 'Hoca',
  ASSISTANT: 'Asistan',
}

export const APPLICATION_STATUS_LABEL: Record<ApplicationStatus, string> = {
  PENDING: 'Hoca onayında',
  APPROVED: 'Onaylandı',
  REJECTED: 'Reddedildi',
  WITHDRAWN: 'Geri çekildi',
  CLOSED: 'Dönem sona erdi',
}

/** Ders sürüyor mu (başvuru, teslim, çekilme açık). */
export const isRunning = (s: CourseStatus) => s === 'OPEN' || s === 'ACTIVE'

/** Materyalleri bölümlere ayırır: bölümsüzler "Genel" başta, diğerleri doğal sırayla (Hafta 2, Hafta 3, Hafta 10). */
export function groupMaterials(list: Material[]): [string, Material[]][] {
  const groups = new Map<string, Material[]>()
  for (const m of [...list].sort((a, b) => a.sortOrder - b.sortOrder)) {
    const key = m.section?.trim() || 'Genel'
    groups.set(key, [...(groups.get(key) ?? []), m])
  }
  return [...groups.entries()].sort(([a], [b]) => (a === 'Genel' ? -1 : b === 'Genel' ? 1 : a.localeCompare(b, 'tr', { numeric: true })))
}

export const courseKeys = {
  mine: ['courses', 'mine'] as const,
  detail: (id: string) => ['courses', id] as const,
  staff: (id: string) => ['courses', id, 'staff'] as const,
  materials: (id: string) => ['courses', id, 'materials'] as const,
  announcements: (id: string) => ['courses', id, 'announcements'] as const,
  applications: ['courses', 'applications', 'mine'] as const,
  currentTerm: ['courses', 'terms', 'current'] as const,
}

export function useMyCourses(enabled = true) {
  return useQuery({
    queryKey: courseKeys.mine,
    enabled,
    queryFn: async () => (await api.get<EnrolledCourse[]>('/courses/my-courses')).data,
  })
}

export function useCourse(id: string) {
  return useQuery({ queryKey: courseKeys.detail(id), queryFn: async () => (await api.get<Course>(`/courses/${id}`)).data })
}

export function useCourseStaff(id: string) {
  return useQuery({
    queryKey: courseKeys.staff(id),
    queryFn: async () => (await api.get<CourseStaff[]>(`/courses/${id}/staff`)).data,
  })
}

export function useMaterials(id: string, enabled = true) {
  return useQuery({
    queryKey: courseKeys.materials(id),
    enabled,
    queryFn: async () => (await api.get<Material[]>(`/courses/${id}/materials`)).data,
  })
}

export function useAnnouncements(id: string, enabled = true) {
  return useQuery({
    queryKey: courseKeys.announcements(id),
    enabled,
    queryFn: async () => (await api.get<Announcement[]>(`/courses/${id}/announcements`)).data,
  })
}

/** Birden çok dersin duyuruları (Bugün ekranı); önbellek ders sayfasıyla paylaşılır. */
export function useAnnouncementsFor(courseIds: string[]) {
  return useQueries({
    queries: courseIds.map((id) => ({
      queryKey: courseKeys.announcements(id),
      queryFn: async () => (await api.get<Announcement[]>(`/courses/${id}/announcements`)).data,
    })),
  })
}

export function useMyApplications() {
  return useQuery({
    queryKey: courseKeys.applications,
    queryFn: async () => (await api.get<CourseApplication[]>('/courses/my-applications')).data,
  })
}

export function useCurrentTerm() {
  return useQuery({
    queryKey: courseKeys.currentTerm,
    staleTime: 60 * 60_000,
    queryFn: async () => (await api.get<Term>('/courses/terms/current')).data,
  })
}

/** Dersten çekilme (F-44): `POST /courses/{id}/withdraw {reason?}`. */
export function useWithdrawFromCourse(id: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (reason: string) => {
      await api.post(`/courses/${id}/withdraw`, reason ? { reason } : {})
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: courseKeys.mine })
      void qc.invalidateQueries({ queryKey: ['assignments'] })
    },
  })
}

/** Bekleyen başvuruyu geri çekme (F-44). */
export function useWithdrawApplication() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (applicationId: string) => {
      await api.post(`/courses/applications/${applicationId}/withdraw`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: courseKeys.applications }),
  })
}

/** Dönemler (F-41): `GET /api/courses/terms`, yeniden eskiye. */
export function useTerms() {
  return useQuery({
    queryKey: ['courses', 'terms'],
    staleTime: 60 * 60_000,
    queryFn: async () =>
      (await api.get<Term[]>('/courses/terms')).data.slice().sort((a, b) => b.startsOn.localeCompare(a.startsOn)),
  })
}

/** Katalog: bir dönemin taslak dışındaki dersleri (`GET /api/courses?termId=`). */
export function useCatalog(termId: string | undefined) {
  return useQuery({
    queryKey: ['courses', 'catalog', termId ?? 'all'],
    enabled: termId !== undefined,
    queryFn: async () => (await api.get<Course[]>('/courses', { params: termId ? { termId } : {} })).data,
  })
}

/** Derse başvuru (`POST /api/courses/{id}/apply`); başarıda başvurular ve katalogdaki sayılar tazelenir. */
export function useApplyToCourse() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (courseId: string) => (await api.post<CourseApplication>(`/courses/${courseId}/apply`)).data,
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: courseKeys.applications })
      void qc.invalidateQueries({ queryKey: ['courses', 'catalog'] })
    },
  })
}

/** Kayıt penceresi bugün açık mı (backend `Term.acceptsApplications` ile aynı; tarihler gün bazında). */
export function enrollmentOpen(term: Term | undefined, today: string): boolean {
  if (!term) return true
  return (!term.enrollmentOpensOn || today >= term.enrollmentOpensOn) && (!term.enrollmentClosesOn || today <= term.enrollmentClosesOn)
}

/** Başvuru durumunun tonu. */
export const APPLICATION_TONE: Record<ApplicationStatus, 'neutral' | 'warning' | 'success' | 'danger'> = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  WITHDRAWN: 'neutral',
  CLOSED: 'neutral',
}
