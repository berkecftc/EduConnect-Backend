import { useSyncExternalStore } from 'react'
import { jwtDecode } from 'jwt-decode'

export type Role =
  | 'ROLE_ADMIN'
  | 'ROLE_STAFF'
  | 'ROLE_ACADEMICIAN'
  | 'ROLE_CLUB_OFFICIAL'
  | 'ROLE_STUDENT'
  | 'ROLE_PENDING_STUDENT'
  | 'ROLE_PENDING_ACADEMICIAN'

export type Permission =
  | 'PERM_STUDENT_VERIFIER'
  | 'PERM_STAFF_VERIFIER'
  | 'PERM_MODERATOR'
  | 'PERM_CAMPUS_PUBLISHER'
  | 'PERM_ACCOUNT_MANAGER'

/** `POST /api/auth/login` ve `/refresh` yanıtı. */
export type AuthResponse = {
  token: string
  refreshToken: string
  userId: string
  /** Kullanıcının e-postası. */
  username: string
  roles: string[]
  primaryRole: string | null
  pendingRequests: string[]
}

export type Session = {
  token: string
  refreshToken: string
  userId: string
  email: string
  /** Backend'in sıraladığı roller (ADMIN, STAFF, ACADEMICIAN, CLUB_OFFICIAL, STUDENT, bekleyenler). */
  roles: Role[]
  primaryRole: Role | null
  pendingRequests: string[]
  permissions: Permission[]
  /** Access token bitişi (ms). */
  expiresAt: number
}

type Claims = { exp?: number; roles?: string }

const STORAGE_KEY = 'ec-session'

export function sessionFromAuthResponse(res: AuthResponse): Session {
  let claims: Claims = {}
  try {
    claims = jwtDecode<Claims>(res.token)
  } catch {
    // Çözülemeyen jeton: yetkiler boş, bitiş hemen → ilk istekte yenilenir.
  }
  const authorities = (claims.roles ?? '').split(',').map((r) => r.trim()).filter(Boolean)
  return {
    token: res.token,
    refreshToken: res.refreshToken,
    userId: res.userId,
    email: res.username,
    roles: (res.roles ?? []) as Role[],
    primaryRole: (res.primaryRole as Role | null) ?? null,
    pendingRequests: res.pendingRequests ?? [],
    permissions: authorities.filter((a) => a.startsWith('PERM_')) as Permission[],
    expiresAt: (claims.exp ?? 0) * 1000,
  }
}

function read(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

let current: Session | null = read()
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((l) => l())
}

export function getSession(): Session | null {
  return current
}

/** Diğer sekmelerin yazdığı güncel oturum; yenileme kilidi içinde okunur. */
export function readStoredSession(): Session | null {
  return read()
}

/**
 * Oturumu ayarlar. `persist: false` yalnız bellekte tutar (geliştirme önizlemesi);
 * sayfa yenilenince kaybolur ve kalıcı oturuma dokunmaz.
 */
export function setSession(next: Session | null, { persist = true }: { persist?: boolean } = {}) {
  current = next
  if (!persist) {
    emit()
    return
  }
  try {
    if (next) localStorage.setItem(STORAGE_KEY, JSON.stringify(next))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Depolama kapalıysa oturum yalnız bu sekmede yaşar.
  }
  emit()
}

/** Açık oturumun alanlarını değiştirir; önizleme oturumu kalıcı depoya yazılmaz. */
export function patchSession(patch: Partial<Session>) {
  if (!current) return
  setSession({ ...current, ...patch }, { persist: read()?.token === current.token })
}

export function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// Başka sekmede giriş, çıkış veya yenileme olursa bu sekme de güncellenir.
if (typeof window !== 'undefined') {
  window.addEventListener('storage', (e) => {
    if (e.key !== STORAGE_KEY) return
    current = read()
    emit()
  })
}

export function useSession(): Session | null {
  return useSyncExternalStore(subscribe, getSession, () => null)
}

export function hasRole(session: Session | null, ...roles: Role[]): boolean {
  return !!session && roles.some((r) => session.roles.includes(r))
}

export function hasPermission(session: Session | null, ...perms: Permission[]): boolean {
  return !!session && perms.some((p) => session.permissions.includes(p))
}
