import { motion } from 'motion/react'
import { ease } from '@/design/motion'

/**
 * İnce ölçek çizgisi: tüm uzunluk `max`; açık ton değerlendirilen kısım, dolu ton kazanılan.
 * Ekran okuyucuya `label` okunur.
 */
export function Meter({
  value,
  evaluated,
  max = 100,
  color = 'var(--ec-ders)',
  label,
}: {
  value: number
  evaluated?: number
  max?: number
  color?: string
  label: string
}) {
  const pct = (v: number) => `${Math.max(0, Math.min(100, (v / max) * 100))}%`
  return (
    <div role="img" aria-label={label} className="relative h-[3px] w-full bg-rule">
      {evaluated !== undefined && <div className="absolute inset-y-0 left-0" style={{ width: pct(evaluated), background: color, opacity: 0.28 }} />}
      <motion.div
        className="absolute inset-y-0 left-0"
        style={{ background: color }}
        initial={{ width: 0 }}
        animate={{ width: pct(value) }}
        transition={{ duration: 0.9, ease: ease.rail }}
      />
    </div>
  )
}
