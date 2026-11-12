import { describe, expect, it } from 'vitest'
import { groupRule, isGroupFull, setsAwaitingChoice } from './groups'
import type { GroupSet } from './staff'

const set = (s: Partial<GroupSet> = {}): GroupSet => ({
  id: 's',
  courseId: 'c',
  name: 'Proje grupları',
  selfSignup: true,
  maxMembers: 4,
  signupClosesAt: null,
  signupOpen: true,
  myGroupId: null,
  groups: [{ id: 'g', name: 'Grup 1', memberCount: 2, members: [] }],
  ...s,
})

describe('isGroupFull', () => {
  it('kontenjan dolunca dolu; sınır yoksa hiç dolmaz', () => {
    expect(isGroupFull({ maxMembers: 4 }, { memberCount: 3 })).toBe(false)
    expect(isGroupFull({ maxMembers: 4 }, { memberCount: 4 })).toBe(true)
    expect(isGroupFull({ maxMembers: null }, { memberCount: 40 })).toBe(false)
  })
})

describe('setsAwaitingChoice', () => {
  it('yalnız seçimi açık, grubu olmayan ve grubu kurulmuş setler', () => {
    expect(setsAwaitingChoice([set()])).toHaveLength(1)
    expect(setsAwaitingChoice([set({ myGroupId: 'g' })])).toHaveLength(0)
    expect(setsAwaitingChoice([set({ signupOpen: false })])).toHaveLength(0)
    expect(setsAwaitingChoice([set({ groups: [] })])).toHaveLength(0)
  })
})

describe('groupRule', () => {
  it('seçim açıkken kapanış ve kontenjanı söyler', () => {
    expect(groupRule(set(), true)).toBe('Grubunuzu kendiniz seçersiniz. Grup başına en fazla 4 kişi.')
    expect(groupRule(set({ maxMembers: null, signupClosesAt: '2026-11-20T23:59:00' }), true)).toMatch(/^Grubunuzu kendiniz seçersiniz; seçim .*20.* kapanır\.$/)
  })

  it('kapalı seçim ile hocanın atadığı seti ayırır', () => {
    expect(groupRule(set({ signupOpen: false }), false)).toBe('Grup seçimi kapandı; değişiklik için hocanıza başvurun.')
    expect(groupRule(set({ selfSignup: false, signupOpen: false }), false)).toBe('Grupları hocanız atar.')
  })
})
