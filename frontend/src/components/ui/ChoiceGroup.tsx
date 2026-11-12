import type { ReactNode } from 'react'
import { RadioGroup } from 'radix-ui'
import { cn } from '@/lib/cn'

export type Choice<T extends string> = { value: T; title: string; description?: string; icon?: ReactNode }

/**
 * Seçim kartları: tek seçimli, ok tuşlarıyla gezilir (Radix RadioGroup).
 * Seçili kartta kalın kenar ve işaret; renk tek başına bilgi taşımaz.
 * `layout="rows"`: kutusuz, ince çizgili satırlar (uygulama içi tarife düzeni).
 */
export function ChoiceGroup<T extends string>({
  label,
  value,
  onChange,
  choices,
  error,
  className,
  layout = 'cards',
}: {
  label: string
  value: T | undefined
  onChange: (value: T) => void
  choices: Choice<T>[]
  error?: string
  className?: string
  layout?: 'cards' | 'rows'
}) {
  if (layout === 'rows') {
    return (
      <fieldset className={className}>
        <legend className="text-md font-semibold text-ink">{label}</legend>
        <RadioGroup.Root
          value={value ?? ''}
          onValueChange={(v) => onChange(v as T)}
          aria-invalid={error ? true : undefined}
          className={cn('mt-2 border-t', error ? 'border-danger' : 'border-rule')}
        >
          {choices.map((c) => (
            <RadioGroup.Item
              key={c.value}
              value={c.value}
              className="group row-fill flex w-full items-start gap-4 border-b border-rule px-1 py-3.5 text-left"
            >
              <span
                aria-hidden
                className="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border-2 border-control transition-colors group-hover:border-ink-2 group-data-[state=checked]:border-ink"
              >
                <RadioGroup.Indicator className="size-2.5 rounded-full bg-ink" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-ink group-data-[state=checked]:font-heavy">{c.title}</span>
                {c.description && <span className="mt-0.5 block text-sm text-ink-2">{c.description}</span>}
              </span>
            </RadioGroup.Item>
          ))}
        </RadioGroup.Root>
        {error && <p className="mt-1.5 text-sm font-semibold text-danger">{error}</p>}
      </fieldset>
    )
  }
  return (
    <fieldset className={className}>
      <legend className="text-md font-semibold text-ink">{label}</legend>
      <RadioGroup.Root
        value={value ?? ''}
        onValueChange={(v) => onChange(v as T)}
        aria-invalid={error ? true : undefined}
        className="mt-2 grid gap-3 sm:grid-cols-2"
      >
        {choices.map((c) => (
          <RadioGroup.Item
            key={c.value}
            value={c.value}
            className={cn(
              'group relative flex items-start gap-3 rounded-lg border bg-surface p-4 text-left transition-colors duration-150',
              'border-control hover:border-ink-2 data-[state=checked]:border-ink data-[state=checked]:shadow-[inset_0_0_0_1px_var(--ec-ink)]',
            )}
          >
            {c.icon && <span className="mt-0.5 shrink-0 text-ink-2 group-data-[state=checked]:text-ink">{c.icon}</span>}
            <span className="min-w-0 flex-1">
              <span className="block font-semibold text-ink">{c.title}</span>
              {c.description && <span className="mt-0.5 block text-sm text-ink-2">{c.description}</span>}
            </span>
            <span
              aria-hidden
              className="mt-0.5 grid size-5 shrink-0 place-items-center rounded-full border-2 border-control group-data-[state=checked]:border-ink"
            >
              <RadioGroup.Indicator className="size-2.5 rounded-full bg-ink" />
            </span>
          </RadioGroup.Item>
        ))}
      </RadioGroup.Root>
      {error && <p className="mt-1.5 text-sm font-semibold text-danger">{error}</p>}
    </fieldset>
  )
}
