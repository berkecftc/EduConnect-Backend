import { describe, expect, it } from 'vitest'
import type { Session } from '@/lib/auth/session'
import type { ClubAccess } from '@/features/clubs/manage'
import type { TeachingCourse } from '@/features/courses/teach'
import { publishersOf } from './publishers'

const session = (s: Partial<Session> = {}) => ({ roles: ['ROLE_STUDENT'], permissions: [], ...s }) as unknown as Session

const access = (a: Partial<ClubAccess>): ClubAccess => ({
  clubId: 'k',
  userId: 'u',
  position: 'MEMBER',
  member: true,
  actingPresident: false,
  advisor: false,
  permissions: [],
  clubName: 'Satranç',
  ...a,
})

const course = (c: Partial<TeachingCourse>) => ({ id: 'c', code: 'BİL 301', title: 'Veri Yapıları', staffRole: 'COORDINATOR', status: 'ACTIVE', ...c }) as TeachingCourse

describe('publishersOf', () => {
  it('sıradan üye ve öğrenci hiçbir şey adına yayımlayamaz', () => {
    expect(publishersOf(session(), [access({})], [])).toEqual([])
  })

  it('başkan ve danışman doğrudan, duyuru hazırlayan görevli başkan onayıyla yayımlar', () => {
    const list = publishersOf(
      session(),
      [
        access({ clubId: 'a', actingPresident: true }),
        access({ clubId: 'b', advisor: true }),
        access({ clubId: 'c', permissions: ['PREPARE_ANNOUNCEMENT'] }),
      ],
      [],
    )
    expect(list.map((p) => (p.type === 'CLUB' ? [p.clubId, p.needsApproval] : null))).toEqual([
      ['a', false],
      ['b', false],
      ['c', true],
    ])
  })

  it('ders duyurusu koordinatör ve hocaya; asistana, taslak ve arşiv derse yok', () => {
    const list = publishersOf(
      session(),
      [],
      [
        course({ id: 'c1' }),
        course({ id: 'c2', staffRole: 'INSTRUCTOR' }),
        course({ id: 'c3', staffRole: 'ASSISTANT' }),
        course({ id: 'c4', status: 'ARCHIVED' }),
        course({ id: 'c5', status: 'DRAFT' }),
      ],
    )
    expect(list.map((p) => p.key)).toEqual(['course:c1', 'course:c2'])
  })

  it('kampüs duyurusu kampüs yayıncısına ve yöneticiye', () => {
    expect(publishersOf(session({ roles: ['ROLE_STAFF'], permissions: ['PERM_CAMPUS_PUBLISHER'] } as Partial<Session>), [], [])[0]?.type).toBe('CAMPUS')
    expect(publishersOf(session({ roles: ['ROLE_ADMIN'] } as Partial<Session>), [], [])[0]?.type).toBe('CAMPUS')
    expect(publishersOf(session({ roles: ['ROLE_STAFF'], permissions: ['PERM_MODERATOR'] } as Partial<Session>), [], [])).toEqual([])
  })
})
