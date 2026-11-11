import type { Transition } from 'motion/react'

/** Hareket token'ları; CSS tarafındaki --ease-rail / --ease-swap ile aynı eğriler. */
export const ease = {
  rail: [0.2, 0.8, 0.2, 1] as const,
  swap: [0.65, 0, 0.35, 1] as const,
}

export const duration = {
  fast: 0.12,
  base: 0.2,
  slow: 0.32,
}

export const transition: Record<'fast' | 'base' | 'slow', Transition> = {
  fast: { duration: duration.fast, ease: ease.rail },
  base: { duration: duration.base, ease: ease.rail },
  slow: { duration: duration.slow, ease: ease.rail },
}
