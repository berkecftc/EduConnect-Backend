import { describe, expect, it } from 'vitest'
import { isActiveMembership, renewalDue, roleLabel, roleRank, type MyMembership } from './api'

const membership = (m: Partial<MyMembership>): MyMembership => ({
  clubId: 'k1',
  clubName: 'Robotik Kulübü',
  logoUrl: null,
  clubRole: 'MEMBER',
  termStartDate: null,
  clubStatus: 'ACTIVE',
  validUntil: null,
  endedAt: null,
  endReason: null,
  ...m,
})

describe('kulüp görevleri', () => {
  it('yeni ve eski görev adlarını aynı etikete çevirir', () => {
    expect(roleLabel('PRESIDENT')).toBe('Başkan')
    expect(roleLabel('ROLE_CLUB_OFFICIAL')).toBe('Başkan')
    expect(roleLabel('ROLE_SECRETARY')).toBe('Genel Sekreter')
    expect(roleLabel('BILINMEYEN')).toBe('Üye')
  })

  it('başkanı öne, üyeyi sona dizer', () => {
    expect(roleRank('PRESIDENT')).toBeLessThan(roleRank('TREASURER'))
    expect(roleRank('ROLE_MEMBER')).toBe(roleRank('MEMBER'))
    expect(roleRank('EVENT_COORDINATOR')).toBeLessThan(roleRank('MEMBER'))
  })
})

describe('üyelik', () => {
  it('bitişe 30 gün ya da daha az kaldıysa yenileme önerir', () => {
    expect(renewalDue(membership({ validUntil: '2026-11-04' }), '2026-10-05')).toBe(true)
    expect(renewalDue(membership({ validUntil: '2026-11-05' }), '2026-10-05')).toBe(false)
    expect(renewalDue(membership({ validUntil: '2026-09-01' }), '2026-10-05')).toBe(true)
    expect(renewalDue(membership({ validUntil: null }), '2026-10-05')).toBe(false)
  })

  it('etkinlik alanını iki adıyla da okur; bitmiş üyelik etkin sayılmaz', () => {
    expect(isActiveMembership(membership({ active: true }))).toBe(true)
    expect(isActiveMembership(membership({ isActive: false }))).toBe(false)
    expect(isActiveMembership(membership({ active: true, endedAt: '2026-01-01T00:00:00Z' }))).toBe(false)
  })
})
