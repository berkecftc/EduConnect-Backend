import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export type AssignmentType = 'HOMEWORK' | 'PROJECT' | 'QUIZ' | 'LAB' | 'MIDTERM' | 'FINAL' | 'OTHER'
export type AiPolicy = 'NONE' | 'GUIDANCE' | 'ALLOWED_WITH_DISCLOSURE'

/** Ondalıklar backend'den sayı olarak gelir (BigDecimal). */
export type MySubmission = {
  submissionId: string
  submittedAt: string
  grade: number | null
  finalGrade: number | null
  textContent: string | null
  feedback: string | null
  late: boolean
  aiUsed: boolean | null
  aiNote: string | null
}

/** `GET /api/assignments/my-assignments` öğesi. Tarihler bölgesiz yerel saat; `effective*` kişisel uzatmayı içerir. */
export type MyAssignment = {
  id: string
  title: string
  description: string | null
  dueDate: string
  courseId: string
  fileUrl: string | null
  type: AssignmentType
  aiPolicy: AiPolicy
  weight: number
  maxPoints: number
  gradesPublished: boolean
  latePenaltyPercent: number
  effectiveDueDate: string
  effectiveLateUntil: string | null
  groupSetId: string | null
  groupId: string | null
  groupName: string | null
  submission: MySubmission | null
}

export type GradeCell = {
  assignmentId: string
  submissionId: string | null
  status: 'NOT_SUBMITTED' | 'SUBMITTED' | 'GRADED'
  grade: number | null
  finalGrade: number | null
  late: boolean
  submittedAt: string | null
}

export type GradeColumn = {
  id: string
  title: string
  type: AssignmentType
  weight: number
  maxPoints: number
  dueDate: string
  gradesPublished: boolean
}

/** `GET /api/assignments/course/{id}/my-grades` (F-47). */
export type MyGrades = {
  courseId: string
  assessments: GradeColumn[]
  grades: GradeCell[]
  weightedTotal: number
  gradedWeight: number
}

export type SubmissionVersion = {
  versionNo: number
  fileUrl: string | null
  textContent: string | null
  submittedAt: string
  late: boolean
  submittedBy: string | null
}

export const TYPE_LABEL: Record<AssignmentType, string> = {
  HOMEWORK: 'Ödev',
  PROJECT: 'Proje',
  QUIZ: 'Kısa sınav',
  LAB: 'Laboratuvar',
  MIDTERM: 'Ara sınav',
  FINAL: 'Final',
  OTHER: 'Diğer',
}

export const AI_POLICY: Record<AiPolicy, { label: string; description: string }> = {
  NONE: { label: 'Yapay zekâ kullanılamaz', description: 'Bu çalışmada yapay zekâ araçları kullanılmamalı.' },
  GUIDANCE: {
    label: 'Yalnız rehberlik',
    description: 'Konuyu anlamak için yapay zekâya danışabilirsiniz; teslim ettiğiniz metin ve kod size ait olmalı.',
  },
  ALLOWED_WITH_DISCLOSURE: {
    label: 'Serbest, beyan zorunlu',
    description: 'Yapay zekâ kullanabilirsiniz; teslimde kullanıp kullanmadığınızı ve nasıl kullandığınızı belirtin.',
  },
}

export const assignmentKeys = {
  mine: ['assignments', 'mine'] as const,
  grades: (courseId: string) => ['assignments', 'grades', courseId] as const,
  versions: (submissionId: string) => ['assignments', 'versions', submissionId] as const,
}

export function useMyAssignments() {
  return useQuery({
    queryKey: assignmentKeys.mine,
    queryFn: async () => (await api.get<MyAssignment[]>('/assignments/my-assignments')).data,
  })
}

export function useMyGrades(courseId: string, enabled = true) {
  return useQuery({
    queryKey: assignmentKeys.grades(courseId),
    enabled,
    queryFn: async () => (await api.get<MyGrades>(`/assignments/course/${courseId}/my-grades`)).data,
  })
}

/** Birden çok dersin notları (ders karnesi): her ders ayrı sorgu, önbellek ders sayfasıyla paylaşılır. */
export function useGradesFor(courseIds: string[]) {
  return useQueries({
    queries: courseIds.map((id) => ({
      queryKey: assignmentKeys.grades(id),
      queryFn: async () => (await api.get<MyGrades>(`/assignments/course/${id}/my-grades`)).data,
    })),
  })
}

export function useSubmissionVersions(submissionId: string | undefined) {
  return useQuery({
    queryKey: assignmentKeys.versions(submissionId ?? ''),
    enabled: !!submissionId,
    queryFn: async () => (await api.get<SubmissionVersion[]>(`/assignments/submissions/${submissionId}/versions`)).data,
  })
}

export type SubmitInput = { file: File | null; text: string; aiUsed: boolean | null; aiNote: string }

/** Teslim (F-48, F-79): multipart; dosya ve/veya metin, beyan alanları. Yeniden teslim yeni sürüm ekler. */
export function useSubmitAssignment(assignmentId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ file, text, aiUsed, aiNote }: SubmitInput) => {
      const body = new FormData()
      if (file) body.append('file', file)
      if (text.trim()) body.append('text', text)
      if (aiUsed !== null) {
        body.append('aiUsed', String(aiUsed))
        if (aiUsed && aiNote.trim()) body.append('aiNote', aiNote)
      }
      await api.post(`/assignments/${assignmentId}/submit`, body)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: ['assignments'] }),
  })
}

export const SUBMISSION_TEXT_MAX = 20000
export const AI_NOTE_MAX = 1000
