import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { formatNumber } from '@/lib/format'
import { toLocalIso } from '@/lib/time'
import type { AiPolicy, AssignmentType, GradeCell, GradeColumn } from './api'

/** Kadro tarafı (F-45…F-48): değerlendirme ekleme ve düzenleme, teslimler, puanlama, ilan, uzatma, not defteri. */

/** `GET /api/assignments/course/{id}` öğesi (AssignmentResponse). Tarihler bölgesiz yerel saat. */
export type StaffAssignment = {
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
  gradesPublishedAt: string | null
  lateUntil: string | null
  latePenaltyPercent: number | null
  groupSetId: string | null
}

/** Grup teslimindeki üye (F-87): kişisel puan varsa `grade`/`finalGrade` onu yansıtır. */
export type GroupMemberGrade = {
  studentId: string
  studentName: string | null
  studentNumber: string | null
  personalGrade: number | null
  grade: number | null
  finalGrade: number | null
}

/** Teslim listesi öğesi (SubmissionSummaryDTO). Grup tesliminde `members` dolu. */
export type SubmissionSummary = {
  submissionId: string
  studentId: string
  studentName: string | null
  studentNumber: string | null
  submissionFileUrl: string | null
  submittedAt: string
  grade: number | null
  finalGrade: number | null
  textContent: string | null
  groupId: string | null
  groupName: string | null
  late: boolean
  aiUsed: boolean | null
  aiNote: string | null
  feedback: string | null
  members?: GroupMemberGrade[] | null
}

export type GradebookRow = {
  studentId: string
  studentName: string | null
  studentNumber: string | null
  grades: GradeCell[]
  weightedTotal: number
  gradedWeight: number
  missing: number
}

/** `GET /api/assignments/course/{id}/gradebook` (F-47). */
export type Gradebook = { courseId: string; totalWeight: number; assessments: GradeColumn[]; students: GradebookRow[] }

export type AssignmentChange = {
  field: string
  oldValue: string | null
  newValue: string | null
  changedBy: string | null
  changedAt: string
}

export type Extension = {
  studentId: string
  studentName: string | null
  studentNumber: string | null
  dueDate: string
  lateUntil: string | null
  reason: string | null
  grantedBy: string | null
  grantedAt: string
}

export type GradeChange = {
  oldGrade: number | null
  newGrade: number | null
  feedbackChanged: boolean
  afterPublication: boolean
  reason: string | null
  changedBy: string | null
  changedAt: string
  studentId: string | null
}

export type GroupSet = {
  id: string
  courseId: string
  name: string
  selfSignup: boolean
  maxMembers: number | null
  signupClosesAt: string | null
  signupOpen: boolean
  myGroupId?: string | null
  /** Kadro bütün üyeleri görür; öğrenci yalnız kendi grubunun üyelerini. */
  groups: {
    id: string
    name: string
    memberCount: number
    members: { studentId: string; name: string | null; studentNumber: string | null; joinedAt: string }[]
  }[]
}

/** Değişiklik geçmişindeki alan adları. */
export const CHANGE_FIELD_LABEL: Record<string, string> = {
  title: 'Başlık',
  description: 'Açıklama',
  type: 'Tür',
  weight: 'Ağırlık',
  maxPoints: 'Azami puan',
  dueDate: 'Son teslim',
  lateUntil: 'Geç teslim bitişi',
  latePenalty: 'Geç teslim kesintisi',
  aiPolicy: 'Yapay zekâ kuralı',
  groupSet: 'Grup seti',
}

export const staffKeys = {
  course: (courseId: string) => ['assignments', 'course', courseId] as const,
  gradebook: (courseId: string) => ['assignments', 'course', courseId, 'gradebook'] as const,
  groupSets: (courseId: string) => ['assignments', 'course', courseId, 'group-sets'] as const,
  submissions: (id: string) => ['assignments', id, 'submissions'] as const,
  changes: (id: string) => ['assignments', id, 'changes'] as const,
  extensions: (id: string) => ['assignments', id, 'extensions'] as const,
  gradeHistory: (submissionId: string) => ['assignments', 'grade-history', submissionId] as const,
}

export function useCourseAssignments(courseId: string) {
  return useQuery({
    queryKey: staffKeys.course(courseId),
    queryFn: async () => (await api.get<StaffAssignment[]>(`/assignments/course/${courseId}`)).data,
  })
}

/** Birden çok dersin değerlendirmeleri (akademisyenin Bugün sayfası). */
export function useCourseAssignmentsFor(courseIds: string[]) {
  return useQueries({
    queries: courseIds.map((id) => ({
      queryKey: staffKeys.course(id),
      queryFn: async () => (await api.get<StaffAssignment[]>(`/assignments/course/${id}`)).data,
    })),
  })
}

/** Birden çok dersin not defteri: notlanmayı bekleyen teslim sayıları için. */
export function useGradebooksFor(courseIds: string[]) {
  return useQueries({
    queries: courseIds.map((id) => ({
      queryKey: staffKeys.gradebook(id),
      queryFn: async () => (await api.get<Gradebook>(`/assignments/course/${id}/gradebook`)).data,
    })),
  })
}

/** Not defterinde teslim edilmiş ama puanlanmamış teslim sayısı; grup teslimi üye sayısı kadar değil, bir kez sayılır. */
export function ungradedCount(book: Gradebook | undefined) {
  const ids = new Set<string>()
  for (const r of book?.students ?? []) for (const g of r.grades) if (g.status === 'SUBMITTED' && g.submissionId) ids.add(g.submissionId)
  return ids.size
}

export function useGradebook(courseId: string, enabled = true) {
  return useQuery({
    queryKey: staffKeys.gradebook(courseId),
    enabled,
    queryFn: async () => (await api.get<Gradebook>(`/assignments/course/${courseId}/gradebook`)).data,
  })
}

export function useGroupSets(courseId: string, enabled = true) {
  return useQuery({
    queryKey: staffKeys.groupSets(courseId),
    enabled,
    queryFn: async () => (await api.get<GroupSet[]>(`/assignments/course/${courseId}/group-sets`)).data,
  })
}

export function useSubmissions(assignmentId: string) {
  return useQuery({
    queryKey: staffKeys.submissions(assignmentId),
    queryFn: async () => (await api.get<SubmissionSummary[]>(`/assignments/${assignmentId}/submissions`)).data,
  })
}

export function useAssignmentChanges(assignmentId: string, enabled: boolean) {
  return useQuery({
    queryKey: staffKeys.changes(assignmentId),
    enabled,
    queryFn: async () => (await api.get<AssignmentChange[]>(`/assignments/${assignmentId}/changes`)).data,
  })
}

export function useExtensions(assignmentId: string) {
  return useQuery({
    queryKey: staffKeys.extensions(assignmentId),
    queryFn: async () => (await api.get<Extension[]>(`/assignments/${assignmentId}/extensions`)).data,
  })
}

export function useGradeHistory(submissionId: string | undefined) {
  return useQuery({
    queryKey: staffKeys.gradeHistory(submissionId ?? ''),
    enabled: !!submissionId,
    queryFn: async () => (await api.get<GradeChange[]>(`/assignments/submissions/${submissionId}/grade-history`)).data,
  })
}

// ——— Form ———

export type AssignmentDraft = {
  type: AssignmentType
  title: string
  description: string
  dueDate: string
  lateUntil: string
  latePenaltyPercent: string
  weight: string
  maxPoints: string
  aiPolicy: AiPolicy
  groupSetId: string
}

export type AssignmentDraftErrors = Partial<Record<keyof AssignmentDraft, string>>

export const EMPTY_ASSIGNMENT: AssignmentDraft = {
  type: 'HOMEWORK',
  title: '',
  description: '',
  dueDate: '',
  lateUntil: '',
  latePenaltyPercent: '',
  weight: '',
  maxPoints: '100',
  aiPolicy: 'GUIDANCE',
  groupSetId: '',
}

/** Ondalık giriş: "17,5" ve "17.5" aynı; boş ya da sayı değilse null. */
export function parseDecimal(value: string): number | null {
  const v = value.trim().replace(',', '.')
  if (!/^\d+(\.\d+)?$/.test(v)) return null
  return Number(v)
}

const twoDecimals = (n: number) => Math.round(n * 100) === n * 100

/** `datetime-local` değeri ("2026-10-20T23:59") ya da backend değeri ("2026-10-20T23:59:00") → input değeri. */
export const toInput = (local: string | null | undefined) => (local ? local.slice(0, 16) : '')

/**
 * Ön kontrol; sınırlar AssignmentRequest ile aynı. `remainingWeight`: bu değerlendirme dışındaki ağırlıklarla kalan yüzde
 * (backend 409 WEIGHT_EXCEEDED ile de denetler). `now` bölgesiz yerel saat; yeni değerlendirmede son teslim geçmişte olamaz.
 */
export function validateAssignmentDraft(d: AssignmentDraft, opts: { remainingWeight: number; now?: string }): AssignmentDraftErrors {
  const e: AssignmentDraftErrors = {}
  if (!d.title.trim()) e.title = 'Başlık yazın.'
  if (!d.dueDate) e.dueDate = 'Son teslim tarihini seçin.'
  else if (opts.now && toLocalIso(d.dueDate) <= opts.now) e.dueDate = 'Son teslim ileri bir tarih olmalı.'
  if (d.lateUntil && d.dueDate && d.lateUntil <= d.dueDate) e.lateUntil = 'Geç teslim bitişi son teslimden sonra olmalı.'
  const penalty = d.latePenaltyPercent.trim() ? parseDecimal(d.latePenaltyPercent) : 0
  if (penalty === null || penalty > 100 || !twoDecimals(penalty)) e.latePenaltyPercent = 'Kesinti 0 ile 100 arasında olmalı.'
  const weight = d.weight.trim() ? parseDecimal(d.weight) : 0
  if (weight === null || weight > 100 || !twoDecimals(weight)) e.weight = 'Ağırlık 0 ile 100 arasında olmalı.'
  else if (weight > opts.remainingWeight) e.weight = `Dersin ağırlık toplamı %100'ü geçemez; kalan %${formatPercent(opts.remainingWeight)}.`
  const max = parseDecimal(d.maxPoints)
  if (max === null || max <= 0 || max > 1000 || !twoDecimals(max)) e.maxPoints = 'Azami puan 0 ile 1000 arasında olmalı.'
  return e
}

const formatPercent = (n: number) => String(Math.round(n * 100) / 100).replace('.', ',')

export function assignmentToDraft(a: StaffAssignment): AssignmentDraft {
  return {
    type: a.type,
    title: a.title,
    description: a.description ?? '',
    dueDate: toInput(a.dueDate),
    lateUntil: toInput(a.lateUntil),
    latePenaltyPercent: a.latePenaltyPercent ? String(a.latePenaltyPercent).replace('.', ',') : '',
    weight: String(a.weight).replace('.', ','),
    maxPoints: String(a.maxPoints).replace('.', ','),
    aiPolicy: a.aiPolicy,
    groupSetId: a.groupSetId ?? '',
  }
}

function draftToBody(d: AssignmentDraft) {
  return {
    title: d.title.trim(),
    description: d.description.trim() || null,
    type: d.type,
    dueDate: toLocalIso(d.dueDate),
    lateUntil: d.lateUntil ? toLocalIso(d.lateUntil) : null,
    latePenaltyPercent: d.latePenaltyPercent.trim() ? parseDecimal(d.latePenaltyPercent) : 0,
    weight: d.weight.trim() ? parseDecimal(d.weight) : 0,
    maxPoints: parseDecimal(d.maxPoints),
    aiPolicy: d.aiPolicy,
    groupSetId: d.groupSetId || null,
  }
}

function useInvalidateCourse(courseId: string) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: ['assignments', 'course', courseId] })
    void qc.invalidateQueries({ queryKey: ['assignments', 'mine'] })
  }
}

/** Değerlendirme ekle: multipart `assignment` (JSON) + isteğe bağlı dosya. */
export function useCreateAssignment(courseId: string) {
  const invalidate = useInvalidateCourse(courseId)
  return useMutation({
    mutationFn: async ({ draft, file }: { draft: AssignmentDraft; file: File | null }) => {
      const form = new FormData()
      form.append('assignment', new Blob([JSON.stringify({ ...draftToBody(draft), courseId })], { type: 'application/json' }))
      if (file) form.append('file', file)
      return (await api.post<StaffAssignment>('/assignments', form)).data
    },
    onSuccess: invalidate,
  })
}

/** Düzenle: yalnız değişen alanlar gider; geç teslim bitişi ve grup seti boşaltılırsa `clear*` bayrağıyla. */
export function useUpdateAssignment(assignment: StaffAssignment) {
  const invalidate = useInvalidateCourse(assignment.courseId)
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (draft: AssignmentDraft) => {
      const before = draftToBody(assignmentToDraft(assignment))
      const after = draftToBody(draft)
      const body: Record<string, unknown> = {}
      for (const k of Object.keys(after) as (keyof typeof after)[]) if (after[k] !== before[k]) body[k] = after[k]
      if ('lateUntil' in body && body.lateUntil === null) {
        delete body.lateUntil
        body.clearLateUntil = true
      }
      if ('groupSetId' in body && body.groupSetId === null) {
        delete body.groupSetId
        body.clearGroupSet = true
      }
      if (Object.keys(body).length === 0) return assignment
      return (await api.put<StaffAssignment>(`/assignments/${assignment.id}`, body)).data
    },
    onSuccess: () => {
      invalidate()
      void qc.invalidateQueries({ queryKey: staffKeys.changes(assignment.id) })
    },
  })
}

export function useDeleteAssignment(courseId: string) {
  const invalidate = useInvalidateCourse(courseId)
  return useMutation({
    mutationFn: async (id: string) => {
      await api.delete(`/assignments/${id}`)
    },
    onSuccess: invalidate,
  })
}

/** Puan ver (F-45, F-46, F-87): ilan edilmiş puan değişirken gerekçe zorunlu. Geri bildirim her zaman gönderilir; boş metin siler. */
export function useGradeSubmission(assignment: StaffAssignment) {
  const invalidate = useInvalidateCourse(assignment.courseId)
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { submissionId: string; grade: number; feedback: string; reason: string }) => {
      await api.put(`/assignments/submissions/${v.submissionId}/grade`, {
        grade: v.grade,
        feedback: v.feedback.trim(),
        reason: v.reason.trim() || null,
      })
    },
    onSuccess: (_d, v) => {
      invalidate()
      void qc.invalidateQueries({ queryKey: staffKeys.submissions(assignment.id) })
      void qc.invalidateQueries({ queryKey: staffKeys.gradeHistory(v.submissionId) })
    },
  })
}

export function usePublishGrades(assignment: StaffAssignment) {
  const invalidate = useInvalidateCourse(assignment.courseId)
  return useMutation({
    mutationFn: async () => (await api.post<StaffAssignment>(`/assignments/${assignment.id}/publish-grades`)).data,
    onSuccess: invalidate,
  })
}

export function useGrantExtension(assignment: StaffAssignment) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { studentId: string; dueDate: string; reason: string }) =>
      (
        await api.put<Extension>(`/assignments/${assignment.id}/extensions/${v.studentId}`, {
          dueDate: toLocalIso(v.dueDate),
          reason: v.reason.trim() || null,
        })
      ).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: staffKeys.extensions(assignment.id) }),
  })
}

export function useRevokeExtension(assignment: StaffAssignment) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (studentId: string) => {
      await api.delete(`/assignments/${assignment.id}/extensions/${studentId}`)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: staffKeys.extensions(assignment.id) }),
  })
}

/** Grup tesliminde üyeye kişisel puan (F-51): gerekçe zorunlu; kaldırılınca üye grubun puanını alır. */
export function useMemberGrade(assignment: StaffAssignment) {
  const invalidate = useInvalidateCourse(assignment.courseId)
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { submissionId: string; studentId: string; grade: number | null; reason: string }) => {
      const url = `/assignments/submissions/${v.submissionId}/members/${v.studentId}/grade`
      if (v.grade === null) await api.delete(url)
      else await api.put(url, { grade: v.grade, reason: v.reason.trim() })
    },
    onSuccess: (_d, v) => {
      invalidate()
      void qc.invalidateQueries({ queryKey: staffKeys.submissions(assignment.id) })
      void qc.invalidateQueries({ queryKey: staffKeys.gradeHistory(v.submissionId) })
    },
  })
}

// ——— Grup setleri (F-50) ———

export type GroupSetDraft = { name: string; selfSignup: boolean; maxMembers: string; signupClosesAt: string }

function useGroupSetsRefresh(courseId: string) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: staffKeys.groupSets(courseId) })
    void qc.invalidateQueries({ queryKey: ['assignments', 'mine'] })
  }
}

const groupSetBody = (d: GroupSetDraft) => ({
  name: d.name.trim(),
  selfSignup: d.selfSignup,
  maxMembers: d.maxMembers.trim() ? Number(d.maxMembers) : null,
  signupClosesAt: d.selfSignup && d.signupClosesAt ? toLocalIso(d.signupClosesAt) : null,
})

/** Set oluştur ya da düzenle: ad, öğrenciler kendi seçsin mi, grup kontenjanı, seçim kapanışı. */
export function useSaveGroupSet(courseId: string) {
  const refresh = useGroupSetsRefresh(courseId)
  return useMutation({
    mutationFn: async ({ id, draft }: { id?: string; draft: GroupSetDraft }) => {
      if (id) await api.put(`/assignments/group-sets/${id}`, groupSetBody(draft))
      else await api.post(`/assignments/course/${courseId}/group-sets`, groupSetBody(draft))
    },
    onSuccess: refresh,
  })
}

/** Grup yapısı işlemleri: set sil, grup ekle/sil, öğrenciyi gruba ata (taşır) ya da çıkar. */
export function useGroupAction(courseId: string) {
  const refresh = useGroupSetsRefresh(courseId)
  return useMutation({
    mutationFn: async (
      a:
        | { kind: 'deleteSet'; setId: string }
        | { kind: 'addGroup'; setId: string; name: string }
        | { kind: 'deleteGroup'; groupId: string }
        | { kind: 'assign'; groupId: string; studentId: string }
        | { kind: 'unassign'; groupId: string; studentId: string },
    ) => {
      switch (a.kind) {
        case 'deleteSet':
          return void (await api.delete(`/assignments/group-sets/${a.setId}`))
        case 'addGroup':
          return void (await api.post(`/assignments/group-sets/${a.setId}/groups`, { name: a.name.trim() }))
        case 'deleteGroup':
          return void (await api.delete(`/assignments/groups/${a.groupId}`))
        case 'assign':
          return void (await api.put(`/assignments/groups/${a.groupId}/members/${a.studentId}`))
        case 'unassign':
          return void (await api.delete(`/assignments/groups/${a.groupId}/members/${a.studentId}`))
      }
    },
    onSuccess: refresh,
  })
}

// ——— Görünüm yardımcıları ———

/** Not defterinden bir değerlendirmenin teslim ve notlama sayıları. */
export function columnProgress(book: Gradebook | undefined, assignmentId: string) {
  let submitted = 0
  let graded = 0
  // Notlanmayı bekleyen teslimler: grup teslimi bir kez sayılır.
  const waiting = new Set<string>()
  for (const row of book?.students ?? []) {
    const cell = row.grades.find((c) => c.assignmentId === assignmentId)
    if (cell?.status === 'SUBMITTED') {
      submitted++
      if (cell.submissionId) waiting.add(cell.submissionId)
    }
    if (cell?.status === 'GRADED') {
      submitted++
      graded++
    }
  }
  return { students: book?.students.length ?? 0, submitted, graded, waiting: waiting.size }
}

/** Kalan ağırlık: dersteki diğer değerlendirmelerin toplamı 100'den düşülür. */
export function remainingWeight(all: StaffAssignment[], exceptId?: string) {
  const used = all.filter((a) => a.id !== exceptId).reduce((sum, a) => sum + a.weight, 0)
  return Math.max(0, Math.round((100 - used) * 100) / 100)
}

/** Puan metni: geç kesinti varsa "80 → 64". */
export function gradeText(s: SubmissionSummary, max: number) {
  if (s.grade == null) return null
  const final = s.finalGrade ?? s.grade
  return final !== s.grade
    ? `${formatNumber(s.grade)} → ${formatNumber(final)} / ${formatNumber(max)}`
    : `${formatNumber(s.grade)} / ${formatNumber(max)}`
}
