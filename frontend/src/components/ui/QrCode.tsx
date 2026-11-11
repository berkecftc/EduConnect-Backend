import { useMemo } from 'react'
import QR from 'qrcode'
import { cn } from '@/lib/cn'

/**
 * QR kod: kütüphaneden yalnız modül matrisi alınır, SVG yolu burada çizilir (HTML enjeksiyonu yok).
 * Okutulabilsin diye koyu modüller her temada siyah, zemin beyaz; çevresinde 2 modül sessiz alan.
 */
export function QrCode({ value, label, className }: { value: string; label: string; className?: string }) {
  const { size, path } = useMemo(() => {
    const { modules } = QR.create(value, { errorCorrectionLevel: 'M' })
    let d = ''
    for (let y = 0; y < modules.size; y++) {
      for (let x = 0; x < modules.size; x++) {
        if (modules.get(x, y)) d += `M${x + 2} ${y + 2}h1v1h-1z`
      }
    }
    return { size: modules.size + 4, path: d }
  }, [value])

  return (
    <svg role="img" aria-label={label} viewBox={`0 0 ${size} ${size}`} shapeRendering="crispEdges" className={cn('block bg-[#fff]', className)}>
      <rect width={size} height={size} fill="#fff" />
      <path d={path} fill="#000" />
    </svg>
  )
}
