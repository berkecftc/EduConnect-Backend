import { useRef, useState, type ReactNode } from 'react'
import type { ApiError } from '@/lib/api/problem'
import { formatNumber } from '@/lib/format'
import { nowLocalIso } from '@/lib/time'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { FormSection } from '@/components/ui/FormSection'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Select } from '@/components/ui/Select'
import { Textarea } from '@/components/ui/Textarea'
import { AI_POLICY, TYPE_LABEL, type AiPolicy, type AssignmentType } from './api'
import { validateAssignmentDraft, type AssignmentDraft, type AssignmentDraftErrors, type GroupSet } from './staff'

const FILE_MAX_BYTES = 20 * 1024 * 1024

/** Backend hata kodu → alan (F-45, F-48, F-51). */
const CODE_FIELD: Record<string, keyof AssignmentDraft> = {
  WEIGHT_EXCEEDED: 'weight',
  INVALID_LATE_UNTIL: 'lateUntil',
  GRADES_EXCEED_MAX: 'maxPoints',
  INVALID_GROUP_SET: 'groupSetId',
  ASSIGNMENT_HAS_SUBMISSIONS: 'groupSetId',
}

/**
 * Değerlendirme formu (ekleme ve düzenleme): bölümler solda başlıklı. Saatler Türkiye saati (F-48).
 * İstemci ön kontrol yapar; sunucu hataları ilgili alanın altına yazılır.
 */
export function AssignmentForm({
  initial,
  isNew,
  remainingWeight,
  groupSets,
  submitLabel,
  busy,
  failure,
  extra,
  onSubmit,
}: {
  initial: AssignmentDraft
  isNew: boolean
  /** Bu değerlendirme dışında kalan ağırlık yüzdesi. */
  remainingWeight: number
  groupSets: GroupSet[]
  submitLabel: string
  busy: boolean
  failure: ApiError | null
  /** Gönder düğmesinin yanına (ör. Vazgeç bağlantısı). */
  extra?: ReactNode
  onSubmit: (draft: AssignmentDraft, file: File | null) => void
}) {
  const [d, setD] = useState(initial)
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<AssignmentDraftErrors>({})
  const formRef = useRef<HTMLFormElement>(null)
  const set =
    <K extends keyof AssignmentDraft>(k: K) =>
    (v: AssignmentDraft[K]) => {
      setD((x) => ({ ...x, [k]: v }))
      setErrors((e) => (e[k] ? { ...e, [k]: undefined } : e))
    }
  const text = (k: 'title' | 'description' | 'dueDate' | 'lateUntil' | 'latePenaltyPercent' | 'weight' | 'maxPoints') => ({
    value: d[k],
    onChange: (ev: { target: { value: string } }) => set(k)(ev.target.value),
  })

  const serverField = failure?.code ? CODE_FIELD[failure.code] : undefined
  const err = (k: keyof AssignmentDraft) =>
    errors[k] ?? (serverField === k ? failure?.message : undefined) ?? failure?.fieldErrors.find((f) => f.field === k)?.message
  const general = failure && !serverField && failure.fieldErrors.length === 0

  const submit = (ev: React.FormEvent) => {
    ev.preventDefault()
    const next = validateAssignmentDraft(d, { remainingWeight, now: isNew ? nowLocalIso() : undefined })
    setErrors(next)
    if (Object.keys(next).length === 0) onSubmit(d, file)
    else requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="flex flex-col">
      <FormSection title="Değerlendirme" hint="Öğrenciler türü, başlığı ve açıklamayı ödev sayfasında görür.">
        <Field label="Tür" required>
          <Select value={d.type} onChange={(e) => set('type')(e.target.value as AssignmentType)} className="max-w-[16rem]">
            {(Object.keys(TYPE_LABEL) as AssignmentType[]).map((t) => (
              <option key={t} value={t}>
                {TYPE_LABEL[t]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Başlık" required error={err('title')}>
          <Input maxLength={255} {...text('title')} />
        </Field>
        <Field label="Açıklama" hint="İsteğe bağlı. Görev, teslim biçimi ve değerlendirme ölçütleri." error={err('description')}>
          <Textarea rows={6} maxLength={10000} valueLength={d.description.length} {...text('description')} />
        </Field>
        {isNew && (
          <FileField
            label="Ek dosya"
            hint="İsteğe bağlı, en fazla 20 MB. Ör. ödev metni ya da başlangıç kodu."
            maxBytes={FILE_MAX_BYTES}
            value={file}
            onChange={setFile}
          />
        )}
      </FormSection>

      <FormSection
        title="Teslim"
        hint="Saatler Türkiye saatidir. Geç teslim bitişi boşsa teslim son tarihte kapanır; doluysa o zamana kadar kesintiyle kabul edilir."
      >
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Son teslim" required error={err('dueDate')}>
            <Input type="datetime-local" {...text('dueDate')} />
          </Field>
          <Field label="Geç teslim bitişi" hint="İsteğe bağlı." error={err('lateUntil')}>
            <Input type="datetime-local" {...text('lateUntil')} />
          </Field>
        </div>
        {d.lateUntil && (
          <Field
            label="Geç teslim kesintisi (%)"
            hint="Geç teslimde puandan düşülecek yüzde. Boşsa kesinti yok."
            error={err('latePenaltyPercent')}
          >
            <Input inputMode="decimal" maxLength={6} className="max-w-[8rem]" {...text('latePenaltyPercent')} />
          </Field>
        )}
      </FormSection>

      <FormSection title="Puanlama" hint="Dersin bütün değerlendirmelerinin ağırlık toplamı en fazla %100 olabilir.">
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Ağırlık (%)" hint={`0 ise ortalamaya katılmaz. Kalan %${formatNumber(remainingWeight)}.`} error={err('weight')}>
            <Input inputMode="decimal" maxLength={6} {...text('weight')} />
          </Field>
          <Field label="Azami puan" required hint="Puanlar 0 ile bu değer arasında verilir." error={err('maxPoints')}>
            <Input inputMode="decimal" maxLength={7} {...text('maxPoints')} />
          </Field>
        </div>
      </FormSection>

      <FormSection title="Yapay zekâ" hint="Öğrenci teslim ederken bu kuralı görür; beyan zorunluysa teslimde sorulur.">
        <ChoiceGroup
          layout="rows"
          label="Kural"
          value={d.aiPolicy}
          onChange={(v) => set('aiPolicy')(v as AiPolicy)}
          choices={(Object.keys(AI_POLICY) as AiPolicy[]).map((k) => ({
            value: k,
            title: AI_POLICY[k].label,
            description: AI_POLICY[k].description,
          }))}
        />
      </FormSection>

      {(groupSets.length > 0 || d.groupSetId) && (
        <FormSection title="Grup" hint="Grup ödevinde her grup tek teslim yapar; puan grubun bütün üyelerine yazılır.">
          <Field label="Teslim" error={err('groupSetId')}>
            <Select value={d.groupSetId} onChange={(e) => set('groupSetId')(e.target.value)} className="max-w-[24rem]">
              <option value="">Bireysel</option>
              {groupSets.map((s) => (
                <option key={s.id} value={s.id}>
                  Grup: {s.name} ({s.groups.length} grup)
                </option>
              ))}
            </Select>
          </Field>
        </FormSection>
      )}

      <div className="flex flex-col gap-5 border-t border-ink pt-6">
        {general && (
          <Notice variant="line" tone="danger" title="Kaydedilemedi">
            {failure.message}
          </Notice>
        )}
        <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
          <Button type="submit" variant="primary" size="lg" loading={busy}>
            {submitLabel}
          </Button>
          {extra}
        </div>
      </div>
    </form>
  )
}
