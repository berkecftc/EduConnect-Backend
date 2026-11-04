import { describe, expect, it } from 'vitest'
import { isDualAffiliation, panelOf, viewRoles } from './panel'
import type { Role, Session } from './session'

const session = (...roles: Role[]) => ({ roles }) as Session

describe('çift bağlılık görünümü', () => {
  it('yalnız personel ve öğrenci kaydı birlikte varsa çift sayılır', () => {
    expect(isDualAffiliation(session('ROLE_ACADEMICIAN', 'ROLE_STUDENT'))).toBe(true)
    expect(isDualAffiliation(session('ROLE_ACADEMICIAN', 'ROLE_CLUB_OFFICIAL'))).toBe(true)
    expect(isDualAffiliation(session('ROLE_ACADEMICIAN', 'ROLE_PENDING_STUDENT'))).toBe(false)
    expect(isDualAffiliation(session('ROLE_STUDENT'))).toBe(false)
    expect(isDualAffiliation(null)).toBe(false)
  })

  it('seçim yoksa personel görünümü açılır; tek bağlılıkta seçim yok sayılır', () => {
    const dual = session('ROLE_ACADEMICIAN', 'ROLE_STUDENT')
    expect(panelOf(dual, null)).toBe('academician')
    expect(panelOf(dual, 'student')).toBe('student')
    expect(panelOf(session('ROLE_STUDENT'), 'academician')).toBeNull()
  })

  it('görünüm yalnız seçilen tarafın ekranlarını açar', () => {
    const dual = session('ROLE_ACADEMICIAN', 'ROLE_STUDENT')
    expect(viewRoles(dual, 'academician')).toEqual({ student: false, academician: true })
    expect(viewRoles(dual, 'student')).toEqual({ student: true, academician: false })
  })

  it('tek bağlılıkta rolün kendisi; görevli ikisini de görmez', () => {
    expect(viewRoles(session('ROLE_STUDENT'), null)).toEqual({ student: true, academician: false })
    expect(viewRoles(session('ROLE_ACADEMICIAN'), null)).toEqual({ student: false, academician: true })
    expect(viewRoles(session('ROLE_STAFF'), null)).toEqual({ student: false, academician: false })
  })
})
