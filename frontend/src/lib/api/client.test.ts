import { beforeEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'
import { getSession, setSession, type Session } from '@/lib/auth/session'
import { refreshSession } from './client'

function jwt(expSeconds: number) {
  const b64 = (o: object) => btoa(JSON.stringify(o)).replace(/=+$/, '')
  return `${b64({ alg: 'none' })}.${b64({ exp: expSeconds, roles: 'ROLE_STUDENT,PERM_MODERATOR' })}.sig`
}

const expired: Session = {
  token: 'old',
  refreshToken: 'r1',
  userId: 'u1',
  email: 'a@ogr.edu.tr',
  roles: ['ROLE_STUDENT'],
  primaryRole: 'ROLE_STUDENT',
  pendingRequests: [],
  permissions: [],
  expiresAt: 0,
}

describe('refreshSession', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    setSession(expired)
  })

  it('eşzamanlı çağrıları tek yenileme isteğine indirger', async () => {
    const post = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        token: jwt(Math.floor(Date.now() / 1000) + 900),
        refreshToken: 'r2',
        userId: 'u1',
        username: 'a@ogr.edu.tr',
        roles: ['ROLE_STUDENT'],
        primaryRole: 'ROLE_STUDENT',
        pendingRequests: [],
      },
    })
    const [a, b, c] = await Promise.all([refreshSession('old'), refreshSession('old'), refreshSession('old')])
    expect(post).toHaveBeenCalledTimes(1)
    expect(post).toHaveBeenCalledWith('/api/auth/refresh', { refreshToken: 'r1' })
    expect(a?.refreshToken).toBe('r2')
    expect(b).toBe(a)
    expect(c).toBe(a)
    expect(getSession()?.permissions).toEqual(['PERM_MODERATOR'])
  })

  it('geçersiz refresh jetonunda oturumu kapatır', async () => {
    vi.spyOn(axios, 'post').mockRejectedValue(
      Object.assign(new Error('x'), {
        isAxiosError: true,
        response: { status: 401, data: { status: 401, errorCode: 'INVALID_REFRESH_TOKEN', message: 'Invalid refresh token' }, headers: {} },
      }),
    )
    await expect(refreshSession('old')).rejects.toMatchObject({ code: 'INVALID_REFRESH_TOKEN' })
    expect(getSession()).toBeNull()
  })

  it('başka sekme yenilediyse istek atmadan onun jetonunu kullanır', async () => {
    const fresh = { ...expired, token: 'new', refreshToken: 'r9', expiresAt: Date.now() + 600_000 }
    localStorage.setItem('ec-session', JSON.stringify(fresh))
    const post = vi.spyOn(axios, 'post')
    const s = await refreshSession('old')
    expect(post).not.toHaveBeenCalled()
    expect(s?.token).toBe('new')
  })
})
