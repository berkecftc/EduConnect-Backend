import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Tabs as RTabs } from 'radix-ui'
import { motion } from 'motion/react'
import { useSearchParams } from 'react-router-dom'
import { transition } from '@/design/motion'

export type TabDef = { value: string; label: string; count?: number; content: ReactNode }

/**
 * Sekmeler: seçili sekme adres çubuğunda (`?sekme=`) tutulur; bağlantı paylaşılabilir, geri tuşu çalışır.
 * Alt çizgi seçilen sekmeye kayar; içerik kısa bir geçişle gelir. Ok tuşlarıyla gezilir (Radix Tabs).
 */
export function Tabs({ tabs, label, param = 'sekme' }: { tabs: TabDef[]; label: string; param?: string }) {
  const id = useId()
  const [params, setParams] = useSearchParams()
  const first = tabs[0]?.value ?? ''
  const requested = params.get(param)
  const value = tabs.some((t) => t.value === requested) ? requested! : first
  const listRef = useRef<HTMLDivElement>(null)

  // Sayılar sonradan gelince sekmeler genişler; konum o zaman yeniden hesaplanır.
  const widths = tabs.map((t) => t.count).join()

  // Bağlantıyla açılan sekme dar ekranda şeridin dışında kalmasın; yalnız şerit kayar, sayfa yerinde durur.
  useEffect(() => {
    const list = listRef.current
    const active = list?.querySelector('[data-state="active"]')
    if (!list || !active) return
    const a = active.getBoundingClientRect()
    const l = list.getBoundingClientRect()
    if (a.left < l.left) list.scrollLeft -= l.left - a.left
    else if (a.right > l.right) list.scrollLeft += a.right - l.right
  }, [value, widths])

  return (
    <RTabs.Root
      value={value}
      onValueChange={(v) => {
        const next = new URLSearchParams(params)
        if (v === first) next.delete(param)
        else next.set(param, v)
        setParams(next, { replace: true })
      }}
    >
      <RTabs.List ref={listRef} aria-label={label} className="-mx-1 flex gap-1 overflow-x-auto overflow-y-hidden border-b border-rule px-1 [scrollbar-width:thin]">
        {tabs.map((t) => (
          <RTabs.Trigger
            key={t.value}
            value={t.value}
            className="relative flex h-12 shrink-0 items-center gap-1.5 px-3 text-md font-semibold text-ink-3 transition-colors hover:text-ink data-[state=active]:text-ink"
          >
            {t.label}
            {t.count !== undefined && (
              <span className="tabular text-sm text-ink-3">{t.count}</span>
            )}
            {value === t.value && (
              <motion.span
                layoutId={`${id}-alt-cizgi`}
                transition={transition.base}
                aria-hidden
                className="absolute inset-x-2 bottom-0 h-[3px] rounded-full bg-ink"
              />
            )}
          </RTabs.Trigger>
        ))}
      </RTabs.List>
      {tabs.map((t) => (
        <RTabs.Content key={t.value} value={t.value} className="pt-7 outline-none">
          <motion.div initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} transition={transition.base}>
            {t.content}
          </motion.div>
        </RTabs.Content>
      ))}
    </RTabs.Root>
  )
}

