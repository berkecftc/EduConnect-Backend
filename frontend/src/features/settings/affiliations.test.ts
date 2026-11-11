import { describe, expect, it } from 'vitest'
import { staffRecordRequest, studentRecordRequest, validateStaffRecord, validateStudentRecord } from './affiliations'

const file = new File(['x'], 'belge.pdf', { type: 'application/pdf' })
const student = { studentNumber: '2026 1004', programId: 'p1', entryYear: '2024', department: 'Bilgisayar Mühendisliği', file }
const staff = { title: 'RESEARCH_ASSISTANT', departmentId: 'd1', department: 'Matematik', officeNumber: ' A-12 ', file }

describe('validateStudentRecord', () => {
  it('katalogda numara, bölüm, program, yıl ve belge zorunlu', () => {
    expect(validateStudentRecord(student, { catalog: true, needsProgram: true })).toEqual({})
    expect(
      Object.keys(validateStudentRecord({ studentNumber: ' ', programId: '', entryYear: '', department: '', file: null }, { catalog: true, needsProgram: true })),
    ).toEqual(['studentNumber', 'department', 'programId', 'entryYear', 'file'])
  })

  it('programı olmayan bölümde program, katalog yoksa seçim istenmez', () => {
    expect(validateStudentRecord({ ...student, programId: '' }, { catalog: true, needsProgram: false })).toEqual({})
    expect(validateStudentRecord({ ...student, programId: '', department: 'Fizik' }, { catalog: false, needsProgram: false })).toEqual({})
    expect(validateStudentRecord({ ...student, programId: '', department: ' ' }, { catalog: false, needsProgram: false }).department).toBe('Bölümünüzü yazın.')
  })
})

describe('validateStaffRecord', () => {
  it('unvan, bölüm ve kimlik kartı zorunlu; ofis isteğe bağlı', () => {
    expect(validateStaffRecord({ ...staff, officeNumber: '' }, { catalog: true })).toEqual({})
    expect(Object.keys(validateStaffRecord({ title: '', departmentId: '', department: '', officeNumber: '', file: null }, { catalog: true }))).toEqual(['title', 'departmentId', 'file'])
  })
})

describe('istek gövdeleri', () => {
  it('sunucunun beklediği biçime çevrilir', () => {
    expect(studentRecordRequest(student)).toEqual({ studentNumber: '20261004', programId: 'p1', entryYear: 2024, department: 'Bilgisayar Mühendisliği' })
    expect(staffRecordRequest(staff)).toEqual({ title: 'RESEARCH_ASSISTANT', departmentId: 'd1', department: 'Matematik', officeNumber: 'A-12' })
    expect(staffRecordRequest({ ...staff, departmentId: '', officeNumber: ' ' })).toMatchObject({ departmentId: null, officeNumber: null })
  })
})
