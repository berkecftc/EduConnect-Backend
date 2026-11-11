import { describe, expect, it } from 'vitest'
import type { MyAssignment, MySubmission } from './api'
import { viewAssignment } from './model'

const base: MyAssignment = {
  id: 'a1',
  title: 'Ödev 2',
  description: null,
  dueDate: '2026-10-08T18:00:00',
  courseId: 'c1',
  fileUrl: null,
  type: 'HOMEWORK',
  aiPolicy: 'GUIDANCE',
  weight: 20,
  maxPoints: 100,
  gradesPublished: false,
  latePenaltyPercent: 20,
  effectiveDueDate: '2026-10-08T18:00:00',
  effectiveLateUntil: '2026-10-10T18:00:00',
  groupSetId: null,
  groupId: null,
  groupName: null,
  submission: null,
}

const submission = (over: Partial<MySubmission>): MySubmission => ({
  submissionId: 's',
  submittedAt: '2026-10-09T10:00:00Z',
  grade: null,
  finalGrade: null,
  textContent: null,
  feedback: null,
  late: false,
  aiUsed: null,
  aiNote: null,
  ...over,
})

describe('viewAssignment', () => {
  it('son tarihten önce açık ve kalan süreyi gösterir', () => {
    const v = viewAssignment(base, '2026-10-05T18:00:00')
    expect(v.phase).toBe('open')
    expect(v.canSubmit).toBe(true)
    expect(v.timing).toBe('3 gün kaldı')
    expect(v.status.label).toBe('Teslim bekliyor')
  })

  it('son 24 saatte uyarı tonuna geçer', () => {
    const v = viewAssignment(base, '2026-10-08T09:00:00')
    expect(v.timing).toBe('9 saat kaldı')
    expect(v.status.tone).toBe('warning')
  })

  it('geç teslim penceresinde açık kalır', () => {
    const v = viewAssignment(base, '2026-10-09T18:00:00')
    expect(v.phase).toBe('late')
    expect(v.canSubmit).toBe(true)
    expect(v.timing).toBe('Geç teslim 24 saat daha açık')
  })

  it('pencere kapanınca teslim edilmedi olur', () => {
    const v = viewAssignment(base, '2026-10-11T00:00:00')
    expect(v.status.label).toBe('Teslim edilmedi')
    expect(v.canSubmit).toBe(false)
  })

  it('geç teslimden sonra yeniden teslimi kapatır', () => {
    const v = viewAssignment({ ...base, submission: submission({ late: true }) }, '2026-10-09T12:00:00')
    expect(v.status.label).toBe('Geç teslim edildi')
    expect(v.canSubmit).toBe(false)
    expect(v.timing).toBe('Puan henüz ilan edilmedi')
  })

  it('geç teslim kesintisini puanda gösterir', () => {
    const v = viewAssignment(
      { ...base, gradesPublished: true, submission: submission({ late: true, grade: 80, finalGrade: 64, feedback: 'İyi' }) },
      '2026-10-20T12:00:00',
    )
    expect(v.graded).toBe(true)
    expect(v.gradeText).toBe('80 → 64 / 100')
    expect(v.penaltyText).toBe('geç teslim %20')
  })

  it('grubu olmayan öğrencide grup ödevini kapatır', () => {
    const v = viewAssignment({ ...base, groupSetId: 'g' }, '2026-10-05T18:00:00')
    expect(v.canSubmit).toBe(false)
    expect(v.blockedReason).toMatch(/gruba katılın/)
  })
})
