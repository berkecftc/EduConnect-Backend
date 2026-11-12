import { motion, useReducedMotion } from 'motion/react'
import { ease } from '@/design/motion'
import { cn } from '@/lib/cn'

/**
 * Sayfa başlığı: kelimeler maskenin altından sırayla yukarı kayar. Uygulamanın tek kinetik tipografi anı;
 * yalnız sayfa açılışında oynar, hareket azaltılmışsa başlık doğrudan görünür.
 * Ekran okuyucu metni tek parça okur (kelime parçaları aria-hidden).
 */
export function RevealTitle({
  text,
  as: Tag = 'h1',
  className,
  delay = 0,
}: {
  text: string
  as?: 'h1' | 'h2' | 'p'
  className?: string
  delay?: number
}) {
  const reduced = useReducedMotion()
  const words = text.split(' ')
  return (
    <Tag className={cn('text-balance', className)}>
      <span className="sr-only">{text}</span>
      <span aria-hidden>
        {words.map((w, i) => (
          <span key={`${w}-${i}`} className="-mb-[0.16em] inline-block overflow-hidden pt-[0.34em] pb-[0.16em] align-bottom">
            <motion.span
              className="inline-block"
              initial={reduced ? false : { y: '160%' }}
              animate={{ y: 0 }}
              transition={{ duration: 0.75, ease: ease.rail, delay: delay + i * 0.06 }}
            >
              {w}
              {i < words.length - 1 ? ' ' : ''}
            </motion.span>
          </span>
        ))}
      </span>
    </Tag>
  )
}
