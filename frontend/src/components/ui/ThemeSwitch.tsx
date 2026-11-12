import { Monitor, Moon, Sun } from 'lucide-react'
import { ToggleGroup } from 'radix-ui'
import { setTheme, useTheme, type ThemePref } from '@/lib/theme'
import { cn } from '@/lib/cn'

const OPTIONS: { value: ThemePref; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Açık tema', icon: Sun },
  { value: 'dark', label: 'Koyu tema', icon: Moon },
  { value: 'system', label: 'Sistem teması', icon: Monitor },
]

export function ThemeSwitch({ className }: { className?: string }) {
  const theme = useTheme()
  return (
    <ToggleGroup.Root
      type="single"
      value={theme}
      onValueChange={(v) => v && setTheme(v as ThemePref)}
      aria-label="Tema"
      className={cn('inline-flex rounded-md border border-rule p-0.5', className)}
    >
      {OPTIONS.map(({ value, label, icon: Icon }) => (
        <ToggleGroup.Item
          key={value}
          value={value}
          aria-label={label}
          title={label}
          className="grid size-10 place-items-center rounded-sm text-ink-3 sm:size-8 transition-colors hover:text-ink data-[state=on]:bg-sunken data-[state=on]:text-ink"
        >
          <Icon className="size-4" aria-hidden />
        </ToggleGroup.Item>
      ))}
    </ToggleGroup.Root>
  )
}
