import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import {
  type AuthResponse,
  type Session,
  getSession,
  readStoredSession,
  sessionFromAuthResponse,
  setSession,
} from '@/lib/auth/session'
import { toApiError } from './problem'

/** Tüm istekler göreli `/api` üzerinden (F-01); geliştirmede Vite proxy'si, üretimde Caddy yönlendirir. */
export const API_BASE = '/api'

export const api = axios.create({ baseURL: API_BASE })

/** Jeton yenilemeye girmemesi gereken uçlar. */
const PUBLIC_AUTH = ['/auth/login', '/auth/refresh', '/auth/logout']

/** Bitişine bu kadar kalan jeton istekten önce yenilenir. */
const REFRESH_MARGIN_MS = 30_000

let inflight: Promise<Session | null> | null = null

/**
 * Refresh jetonu rotasyonlu: aynı jetonla iki yenileme tüm oturum ailesini iptal eder.
 * Bu yüzden sekme içinde tek uçuş (`inflight`), sekmeler arasında Web Locks kullanılır.
 */
export function refreshSession(failedToken?: string): Promise<Session | null> {
  if (!inflight) {
    inflight = withRefreshLock(() => doRefresh(failedToken)).finally(() => {
      inflight = null
    })
  }
  return inflight
}

function withRefreshLock<T>(fn: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks?.request) {
    return navigator.locks.request('ec-auth-refresh', fn) as Promise<T>
  }
  return fn()
}

async function doRefresh(failedToken?: string): Promise<Session | null> {
  const stored = readStoredSession()
  if (!stored) {
    setSession(null)
    return null
  }
  // Kilidi beklerken başka bir sekme yenilediyse onun jetonunu kullan.
  const isFresh = stored.expiresAt - Date.now() > REFRESH_MARGIN_MS
  if (isFresh && stored.token !== failedToken) {
    setSession(stored)
    return stored
  }
  try {
    const { data } = await axios.post<AuthResponse>(`${API_BASE}/auth/refresh`, {
      refreshToken: stored.refreshToken,
    })
    const next = sessionFromAuthResponse(data)
    setSession(next)
    return next
  } catch (err) {
    const e = toApiError(err)
    // Geçersiz/süresi dolmuş/askıya alınmış: oturum biter. Ağ hatasında oturum korunur.
    if (e.status === 401 || e.status === 400) setSession(null)
    throw e
  }
}

function isPublicAuth(url?: string) {
  return !!url && PUBLIC_AUTH.some((p) => url.startsWith(p))
}

api.interceptors.request.use(async (config) => {
  if (isPublicAuth(config.url)) return config
  let session = getSession()
  if (session && session.expiresAt - Date.now() < REFRESH_MARGIN_MS) {
    session = await refreshSession(session.token).catch(() => getSession())
  }
  if (session) config.headers.set('Authorization', `Bearer ${session.token}`)
  return config
})

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean }

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const config = error.config as RetriableConfig | undefined
  const status = error.response?.status

  // Yalnız 401'de oturum yenilenir; 403 yetki sorunudur ve oturumu kapatmaz.
  if (status === 401 && config && !config._retried && !isPublicAuth(config.url)) {
    config._retried = true
    const sentToken = String(config.headers?.get?.('Authorization') ?? '').replace('Bearer ', '')
    try {
      const next = await refreshSession(sentToken)
      if (next) {
        config.headers.set('Authorization', `Bearer ${next.token}`)
        return api.request(config)
      }
    } catch {
      // Aşağıda özgün hatayla düşer.
    }
  }
  return Promise.reject(toApiError(error))
})

/** Oturum açar; başarıda oturumu kaydeder. */
export async function login(email: string, password: string): Promise<Session> {
  const { data } = await api.post<AuthResponse>('/auth/login', { email, password })
  const session = sessionFromAuthResponse(data)
  setSession(session)
  return session
}

/** Refresh ailesini sunucuda iptal eder; ağ hatası çıkışı engellemez. */
export async function logout(): Promise<void> {
  const session = getSession()
  setSession(null)
  if (!session) return
  await api.post('/auth/logout', { refreshToken: session.refreshToken }).catch(() => undefined)
}

/**
 * Kimlik gerektiren dosyayı indirir; adı `Content-Disposition`'dan alır (F-82).
 */
export async function downloadFile(url: string, fallbackName = 'dosya'): Promise<void> {
  const res = await api.get<Blob>(url, { responseType: 'blob' })
  const disposition = String(res.headers['content-disposition'] ?? '')
  const utf8 = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1]
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1]
  const name = utf8 ? decodeURIComponent(utf8) : (plain ?? fallbackName)
  const href = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = href
  a.download = name
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(href), 1000)
}
