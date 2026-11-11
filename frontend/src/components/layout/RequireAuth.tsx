import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { hasPermission, hasRole, useSession, type Permission, type Role } from '@/lib/auth/session'
import { NotAllowedPage } from '@/pages/StatusPages'

/**
 * Oturum yoksa girişe yönlendirir; `roles`/`permissions` verilirse en az biri gerekir.
 * Yetkisiz kullanıcı yönlendirilmez, ne olduğunu söyleyen sayfayı görür.
 */
export function RequireAuth({
  children,
  roles,
  permissions,
}: {
  children: ReactNode
  roles?: Role[]
  permissions?: Permission[]
}) {
  const session = useSession()
  const location = useLocation()

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  }
  const needsCheck = roles || permissions
  const allowed =
    !needsCheck || hasRole(session, ...(roles ?? [])) || hasPermission(session, ...(permissions ?? []))
  if (!allowed) return <NotAllowedPage />
  return <>{children}</>
}
