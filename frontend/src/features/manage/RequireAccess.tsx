import type { ReactNode } from 'react'
import { hasPermission, hasRole, useSession, type Permission } from '@/lib/auth/session'
import { EmptyState } from '@/components/ui/States'

/**
 * Yönetim sayfası kapısı: yönetici her bölümü görür; görevli yalnız token'daki yetkisinin bölümünü (F-57).
 * `perms` boşsa yalnız yönetici. Yetki yoksa ham 403 yerine açıklama gösterilir.
 */
export function RequireAccess({ perms = [], children }: { perms?: Permission[]; children: ReactNode }) {
  const session = useSession()
  const allowed = hasRole(session, 'ROLE_ADMIN') || (perms.length > 0 && hasPermission(session, ...perms))
  if (allowed) return children
  return (
    <div className="mx-auto max-w-[56rem]">
      <EmptyState title="Bu bölüm için yetkiniz yok">Yetki tanımları sistem yöneticisi tarafından yapılır.</EmptyState>
    </div>
  )
}
