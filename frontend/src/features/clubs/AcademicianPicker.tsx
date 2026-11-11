import { useEffect, useState } from 'react'
import { useAcademicianSearch } from '@/features/courses/teach'
import { withTitle } from '@/features/people/titles'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'

export type PickedAcademician = { id: string; name: string; detail?: string }

/**
 * Akademisyen seç: ad ya da soyadla arar, sonuçlar satır olarak listelenir. Seçilen kişi tek satırda kalır;
 * "Değiştir" aramaya döner. `exclude` listede gösterilmeyecek kişiler (ör. mevcut danışman).
 */
export function AcademicianPicker({
  label,
  hint,
  error,
  value,
  onChange,
  exclude = [],
  autoFocus,
}: {
  label: string
  hint?: string
  error?: string
  value: PickedAcademician | null
  onChange: (v: PickedAcademician | null) => void
  exclude?: (string | null | undefined)[]
  autoFocus?: boolean
}) {
  const [q, setQ] = useState('')
  const [query, setQuery] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setQuery(q), 250)
    return () => clearTimeout(t)
  }, [q])
  const hits = useAcademicianSearch(query)
  const choices = (hits.data ?? [])
    .filter((h) => !exclude.includes(h.id))
    .map((h) => ({
      value: h.id,
      title: withTitle([h.firstName, h.lastName].filter(Boolean).join(' ') || 'Akademisyen', h.title, h.academicTitle),
      description: h.department ?? undefined,
    }))

  if (value) {
    return (
      <div>
        <p className="text-md font-semibold">{label}</p>
        <div className="mt-2 flex flex-wrap items-baseline justify-between gap-x-6 gap-y-1 border-y border-rule py-3">
          <span className="min-w-0">
            <span className="block text-lg font-heavy">{value.name}</span>
            {value.detail && <span className="block text-sm text-ink-3">{value.detail}</span>}
          </span>
          <button
            type="button"
            onClick={() => onChange(null)}
            className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
          >
            Değiştir<span className="sr-only">: {value.name}</span>
          </button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-4">
      <Field label={label} hint={hint ?? 'Ad ya da soyaddan en az iki harf yazın.'} error={error}>
        <Input type="search" autoComplete="off" autoFocus={autoFocus} value={q} onChange={(e) => setQ(e.target.value)} />
      </Field>
      {query.trim().length >= 2 &&
        hits.isSuccess &&
        (choices.length > 0 ? (
          <ChoiceGroup
            layout="rows"
            label="Sonuçlar"
            value={undefined}
            onChange={(id) => {
              const c = choices.find((x) => x.value === id)
              if (c) onChange({ id: c.value, name: c.title, detail: c.description })
            }}
            choices={choices}
          />
        ) : (
          <p className="text-md text-ink-2">“{query.trim()}” ile eşleşen akademisyen yok.</p>
        ))}
    </div>
  )
}
