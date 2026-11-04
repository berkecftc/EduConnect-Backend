import { describe, expect, it } from 'vitest'
import type { Role, Session } from '@/lib/auth/session'
import { buildNav } from './nav'

const session = (...roles: Role[]) => ({ roles, permissions: [] }) as unknown as Session
const links = (groups: ReturnType<typeof buildNav>) => groups.flatMap((g) => g.items.map((i) => i.label))

describe('buildNav görünüme göre', () => {
  const dual = session('ROLE_ACADEMICIAN', 'ROLE_STUDENT')

  it('personel görünümünde ders verme, öğrenci görünümünde ders alma menüsü', () => {
    const staff = links(buildNav(dual, { approver: false }, 'academician'))
    expect(staff).toContain('Verdiğim dersler')
    expect(staff).not.toContain('Ödevlerim')
    expect(staff).not.toContain('Biletlerim')

    const student = links(buildNav(dual, { approver: false }, 'student'))
    expect(student).toEqual(expect.arrayContaining(['Derslerim', 'Ödevlerim', 'Biletlerim', 'Liderlik tablosu']))
    expect(student).not.toContain('Verdiğim dersler')
  })

  it('tek bağlılıkta görünüm verilmez', () => {
    expect(links(buildNav(session('ROLE_STUDENT')))).toContain('Derslerim')
    expect(links(buildNav(session('ROLE_ACADEMICIAN')))).toContain('Verdiğim dersler')
  })
})
