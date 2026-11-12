import { useState } from 'react'
import { cn } from '@/lib/cn'

/** Kulüp adındaki genel sözcükler yedek harfe girmez: "Kulübü", "Topluluğu"… */
const GENERIC = new Set(['kulübü', 'kulüp', 'topluluğu', 've'])

function initial(name: string): string {
  const word = name.split(/\s+/).find((w) => w && !GENERIC.has(w.toLocaleLowerCase('tr-TR'))) ?? name
  return (word[0] ?? '?').toLocaleUpperCase('tr-TR')
}

/**
 * Kulüp logosu: sabit kare alan, satırlar hizalı kalsın diye her zaman yer tutar.
 * API `logoUrl`'i herkese açık depo adresi olarak verir (MinIO, PUBLIC_READ). Logo yoksa ya da
 * yüklenemezse aynı karede kulüp adının baş harfi kulüp renginde görünür.
 */
export function ClubLogo({ name, logoUrl, size = 'md' }: { name: string; logoUrl?: string | null; size?: 'md' | 'lg' }) {
  const [failed, setFailed] = useState<string | null>(null)
  const usable = !!logoUrl && /^(https?:\/\/|data:image\/)/.test(logoUrl) && failed !== logoUrl
  const box = cn('grid shrink-0 place-items-center overflow-hidden border border-rule bg-surface', size === 'lg' ? 'size-24' : 'size-12')
  if (usable) {
    return (
      <span className={box}>
        <img src={logoUrl} alt="" loading="lazy" onError={() => setFailed(logoUrl)} className="size-full object-contain" />
      </span>
    )
  }
  return (
    <span aria-hidden className={box}>
      <span className={cn('leading-none font-heavy text-kulup', size === 'lg' ? 'text-5xl' : 'text-2xl')}>{initial(name)}</span>
    </span>
  )
}
