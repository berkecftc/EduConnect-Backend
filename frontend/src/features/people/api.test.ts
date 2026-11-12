import { describe, expect, it } from 'vitest'
import { isAcademician, isStudent } from './api'

const person = (p: Partial<Parameters<typeof isAcademician>[0] & Parameters<typeof isStudent>[0]>) => ({
  affiliations: null,
  academicTitle: null,
  staffCategory: null,
  studentNumber: null,
  programName: null,
  ...p,
})

describe('isAcademician / isStudent', () => {
  it('bağlılıktan ya da alanlardan anlaşılır; öğrenci numarası gizli olsa da öğrenci tanınır', () => {
    expect(isAcademician(person({ affiliations: ['ACADEMICIAN'] }))).toBe(true)
    expect(isAcademician(person({ academicTitle: 'PROFESSOR' }))).toBe(true)
    expect(isStudent(person({ programName: 'Matematik' }))).toBe(true)
    expect(isStudent(person({ affiliations: ['STUDENT'] }))).toBe(true)
    const dual = person({ affiliations: ['ACADEMICIAN', 'STUDENT'] })
    expect(isAcademician(dual) && isStudent(dual)).toBe(true)
    expect(isStudent(person({ affiliations: ['ACADEMICIAN'], academicTitle: 'LECTURER' }))).toBe(false)
  })
})
