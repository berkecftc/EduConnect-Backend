import { describe, expect, it } from 'vitest'
import {
  EMPTY_ASSIGNMENT,
  assignmentToDraft,
  columnProgress,
  gradeText,
  parseDecimal,
  remainingWeight,
  ungradedCount,
  validateAssignmentDraft,
  type AssignmentDraft,
  type Gradebook,
  type StaffAssignment,
  type SubmissionSummary,
} from './staff'

const NOW = '2026-10-07T12:00:00'
const draft = (d: Partial<AssignmentDraft>): AssignmentDraft => ({
  ...EMPTY_ASSIGNMENT,
  title: 'Ödev 2',
  dueDate: '2026-10-20T23:59',
  weight: '10',
  ...d,
})

describe('ondalık giriş', () => {
  it('virgül ve nokta aynı', () => {
    expect(parseDecimal('17,5')).toBe(17.5)
    expect(parseDecimal(' 17.5 ')).toBe(17.5)
  })
  it('sayı değilse null', () => {
    expect(parseDecimal('')).toBeNull()
    expect(parseDecimal('-3')).toBeNull()
    expect(parseDecimal('1,2,3')).toBeNull()
  })
})

describe('değerlendirme formu', () => {
  it('geçerli taslakta hata yok', () => {
    expect(validateAssignmentDraft(draft({}), { remainingWeight: 50, now: NOW })).toEqual({})
  })

  it('yeni değerlendirmede geçmiş son tarih reddedilir, düzenlemede denetlenmez', () => {
    expect(validateAssignmentDraft(draft({ dueDate: '2026-10-01T10:00' }), { remainingWeight: 50, now: NOW }).dueDate).toBeDefined()
    expect(validateAssignmentDraft(draft({ dueDate: '2026-10-01T10:00' }), { remainingWeight: 50 })).toEqual({})
  })

  it('kalan ağırlığı aşmaz, mesajda kalanı söyler', () => {
    expect(validateAssignmentDraft(draft({ weight: '30' }), { remainingWeight: 25, now: NOW }).weight).toContain('%25')
  })

  it('geç teslim bitişi son tarihten sonra olmalı; azami puan 0’dan büyük', () => {
    const e = validateAssignmentDraft(draft({ lateUntil: '2026-10-19T10:00', maxPoints: '0' }), { remainingWeight: 50, now: NOW })
    expect(Object.keys(e).sort()).toEqual(['lateUntil', 'maxPoints'])
  })

  it('kayıttan forma: ondalıklar virgülle, tarihler dakikaya kadar', () => {
    const a: StaffAssignment = {
      id: 'a',
      courseId: 'c',
      title: 'Lab',
      description: null,
      dueDate: '2026-10-20T23:59:00',
      fileUrl: null,
      type: 'LAB',
      aiPolicy: 'NONE',
      weight: 7.5,
      maxPoints: 20,
      gradesPublishedAt: null,
      lateUntil: null,
      latePenaltyPercent: null,
      groupSetId: null,
    }
    expect(assignmentToDraft(a)).toMatchObject({
      dueDate: '2026-10-20T23:59',
      weight: '7,5',
      maxPoints: '20',
      lateUntil: '',
      latePenaltyPercent: '',
    })
  })
})

describe('kadro görünümü yardımcıları', () => {
  const book: Gradebook = {
    courseId: 'c',
    totalWeight: 20,
    assessments: [],
    students: [
      {
        studentId: '1',
        studentName: 'A',
        studentNumber: '1',
        weightedTotal: 0,
        gradedWeight: 0,
        missing: 0,
        grades: [{ assignmentId: 'x', submissionId: 's', status: 'GRADED', grade: 10, finalGrade: 10, late: false, submittedAt: null }],
      },
      {
        studentId: '2',
        studentName: 'B',
        studentNumber: '2',
        weightedTotal: 0,
        gradedWeight: 0,
        missing: 0,
        grades: [
          { assignmentId: 'x', submissionId: 't', status: 'SUBMITTED', grade: null, finalGrade: null, late: false, submittedAt: null },
        ],
      },
      {
        studentId: '3',
        studentName: 'C',
        studentNumber: '3',
        weightedTotal: 0,
        gradedWeight: 0,
        missing: 1,
        grades: [
          { assignmentId: 'x', submissionId: null, status: 'NOT_SUBMITTED', grade: null, finalGrade: null, late: false, submittedAt: null },
        ],
      },
    ],
  }

  it('teslim ve notlama sayıları', () => {
    expect(columnProgress(book, 'x')).toEqual({ students: 3, submitted: 2, graded: 1, waiting: 1 })
  })

  it('grup teslimi notlanmayı beklerken bir kez sayılır', () => {
    const cell = {
      assignmentId: 'g',
      submissionId: 'grup-1',
      status: 'SUBMITTED' as const,
      grade: null,
      finalGrade: null,
      late: false,
      submittedAt: null,
    }
    const row = (id: string) => ({
      studentId: id,
      studentName: id,
      studentNumber: id,
      weightedTotal: 0,
      gradedWeight: 0,
      missing: 0,
      grades: [cell],
    })
    const group: Gradebook = { courseId: 'c', totalWeight: 0, assessments: [], students: [row('1'), row('2'), row('3')] }
    expect(ungradedCount(group)).toBe(1)
    expect(columnProgress(group, 'g')).toMatchObject({ submitted: 3, waiting: 1 })
  })

  it('kalan ağırlık düzenlenen değerlendirmeyi saymaz', () => {
    const all = [
      { id: 'a', weight: 30 },
      { id: 'b', weight: 45.5 },
    ] as StaffAssignment[]
    expect(remainingWeight(all)).toBe(24.5)
    expect(remainingWeight(all, 'b')).toBe(70)
  })

  it('geç kesintili puan iki değerle yazılır', () => {
    const s = { grade: 80, finalGrade: 64 } as SubmissionSummary
    expect(gradeText(s, 100)).toBe('80 → 64 / 100')
    expect(gradeText({ grade: null, finalGrade: null } as SubmissionSummary, 100)).toBeNull()
  })
})
