import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/lib/cn'
import { useSession } from '@/lib/auth/session'
import { personPath } from './api'

/**
 * Kişi adı → profil. Kimlik yoksa düz metin; kendi adınız Profilim'e gider. Görünüm çevreden gelir
 * (kalınlık, boyut); bağlantı yalnız üzerine gelince altı çizilerek belli olur.
 */
export function PersonLink({ id, children, className }: { id: string | null | undefined; children: ReactNode; className?: string }) {
  const session = useSession()
  if (!id) return <span className={className}>{children}</span>
  return (
    <Link to={session?.userId === id ? '/profile' : personPath(id)} className={cn('underline-offset-4 hover:underline', className)}>
      {children}
    </Link>
  )
}
