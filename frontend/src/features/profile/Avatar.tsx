import { useState } from 'react'
import { cn } from '@/lib/cn'

/**
 * Profil fotoğrafı: kare, ince çizgili. Fotoğraf yoksa ya da yüklenemezse ad ve soyadın baş harfleri.
 * API `profileImageUrl`'i herkese açık depo adresi olarak verir; yalnız tam adres çizilir.
 */
export function Avatar({ name, src, size = 'md' }: { name: string; src?: string | null; size?: 'md' | 'lg' }) {
  const [failed, setFailed] = useState<string | null>(null)
  const usable = !!src && /^(https?:\/\/|blob:|data:image\/)/.test(src) && failed !== src
  const initials =
    name
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((w) => w[0]!.toLocaleUpperCase('tr-TR'))
      .join('') || '?'
  const box = cn('grid shrink-0 place-items-center overflow-hidden border border-rule bg-sunken', size === 'lg' ? 'size-32' : 'size-12')
  return usable ? (
    <span className={box}>
      <img src={src} alt="" onError={() => setFailed(src)} className="size-full object-cover" />
    </span>
  ) : (
    <span aria-hidden className={box}>
      <span className={cn('font-heavy text-ink-2', size === 'lg' ? 'text-5xl' : 'text-lg')}>{initials}</span>
    </span>
  )
}
