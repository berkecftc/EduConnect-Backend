import { useRef, useState, type ReactNode } from 'react'
import type { ApiError } from '@/lib/api/problem'
import { nowLocalIso } from '@/lib/time'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { FormSection } from '@/components/ui/FormSection'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Textarea } from '@/components/ui/Textarea'
import { validateDraft, type DraftErrors, type EventDraft } from './manage'

const POSTER_MAX_BYTES = 5 * 1024 * 1024

/** Backend hata kodu → ilgili alan (F-61, F-62). */
const CODE_FIELD: Record<string, keyof EventDraft> = {
  EVENT_IN_PAST: 'startsAt',
  EVENT_TOO_SOON: 'startsAt',
  INVALID_EVENT_TIMES: 'endsAt',
  INVALID_REGISTRATION_WINDOW: 'registrationClosesAt',
}

/**
 * Etkinlik formu (oluşturma ve düzenleme): bölümler ince çizgilerle ayrılır, her bölümün solunda başlığı.
 * İstemci yalnız ön kontrol yapar; backend'in hata kodları ilgili alanın altına yazılır.
 */
export function EventForm({
  initial,
  submitLabel,
  busy,
  failure,
  withPoster,
  campus = false,
  extra,
  onSubmit,
}: {
  initial: EventDraft
  submitLabel: string
  busy: boolean
  failure: ApiError | null
  withPoster: boolean
  /** Kampüs etkinliği: düzenleyen birim sorulur, kitle seçimi yok (herkese açık). */
  campus?: boolean
  /** Formun sonuna eklenecek alan (ör. düzeltme notu). */
  extra?: ReactNode
  onSubmit: (draft: EventDraft, poster: File | null) => void
}) {
  const [d, setD] = useState(initial)
  const [poster, setPoster] = useState<File | null>(null)
  const [errors, setErrors] = useState<DraftErrors>({})
  const formRef = useRef<HTMLFormElement>(null)
  const set = <K extends keyof EventDraft>(k: K) => (v: EventDraft[K]) => setD((x) => ({ ...x, [k]: v }))
  const text = (k: keyof EventDraft) => ({ value: d[k] as string, onChange: (ev: { target: { value: string } }) => set(k)(ev.target.value as never) })

  // Sunucu hatası bir alana aitse o alanın altına, değilse formun sonuna.
  const serverField = failure?.code ? CODE_FIELD[failure.code] : undefined
  const err = (k: keyof EventDraft) => errors[k] ?? (serverField === k ? failure?.message : undefined) ?? failure?.fieldErrors.find((f) => f.field === k)?.message

  const submit = (ev: React.FormEvent) => {
    ev.preventDefault()
    const next = validateDraft(d, nowLocalIso())
    if (campus && !d.organizerName.trim()) next.organizerName = 'Düzenleyen birimi yazın.'
    setErrors(next)
    if (Object.keys(next).length === 0) onSubmit(d, poster)
    // Hata varsa odak ilk hatalı alana: klavye ve ekran okuyucu kullanıcısı nerede olduğunu bilsin.
    else requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
  }

  return (
    <form ref={formRef} onSubmit={submit} noValidate className="flex flex-col">
      <FormSection title="Etkinlik" hint="Başlık ve açıklama etkinlik sayfasında, konuşmacılar başlığın altında görünür.">
        <Field label="Başlık" required error={err('title')}>
          <Input maxLength={255} {...text('title')} />
        </Field>
        <Field label="Açıklama" hint="İsteğe bağlı.">
          <Textarea rows={5} {...text('description')} />
        </Field>
        {campus && (
          <Field label="Düzenleyen" required error={err('organizerName')} hint="Etkinlik sayfasında ve kartlarda görünür. Ör. Kariyer Merkezi">
            <Input maxLength={200} {...text('organizerName')} />
          </Field>
        )}
        <Field label="Konuşmacılar" hint="İsteğe bağlı. Ör. Prof. Dr. Nil Ersoy, Can Erdem">
          <Input maxLength={2000} {...text('speakers')} />
        </Field>
      </FormSection>

      <FormSection title="Zaman ve yer" hint="Saatler Türkiye saatidir. Bitiş boş kalırsa başlangıçtan iki saat sonra sayılır.">
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Başlangıç" required error={err('startsAt')}>
            <Input type="datetime-local" {...text('startsAt')} />
          </Field>
          <Field label="Bitiş" error={err('endsAt')}>
            <Input type="datetime-local" {...text('endsAt')} />
          </Field>
        </div>
        <Field label="Yer" hint="Ör. B Blok 204">
          <Input maxLength={255} {...text('location')} />
        </Field>
      </FormSection>

      <FormSection
        title="Katılım"
        hint={
          campus
            ? 'Kampüs etkinliği herkese açıktır. Kontenjan dolunca yeni katılımcılar bekleme listesine alınır.'
            : 'Kontenjan dolunca yeni katılımcılar bekleme listesine alınır.'
        }
      >
        {!campus && (
          <ChoiceGroup
            layout="rows"
            label="Kimler katılabilir"
            value={d.audience}
            onChange={set('audience')}
            choices={[
              { value: 'ALL_STUDENTS', title: 'Tüm öğrenciler' },
              { value: 'MEMBERS_ONLY', title: 'Yalnız kulüp üyeleri' },
            ]}
          />
        )}
        <ChoiceGroup
          layout="rows"
          label="Kabul"
          value={d.admission}
          onChange={set('admission')}
          choices={[
            { value: 'AUTO_CONFIRM', title: 'Otomatik', description: 'Katılan herkesin kaydı hemen tamamlanır, bilet gelir.' },
            { value: 'APPROVAL_REQUIRED', title: 'Onaylı', description: 'Katılım istekleri yönetimden onay bekler.' },
          ]}
        />
        <Field label="Kontenjan" hint="Boş bırakırsanız sınırsız." error={err('capacity')}>
          <Input inputMode="numeric" className="max-w-[10rem]" {...text('capacity')} />
        </Field>
      </FormSection>

      <FormSection title="Kayıt dönemi" hint="Hepsi isteğe bağlı. Boş bırakırsanız kayıt yayımlandığı anda açılır ve etkinlik başlayana kadar sürer.">
        <div className="grid gap-5 sm:grid-cols-2">
          <Field label="Kayıt açılışı" error={err('registrationOpensAt')}>
            <Input type="datetime-local" {...text('registrationOpensAt')} />
          </Field>
          <Field label="Kayıt kapanışı" error={err('registrationClosesAt')}>
            <Input type="datetime-local" {...text('registrationClosesAt')} />
          </Field>
        </div>
        <Field label="İptal son tarihi" hint="Bu saatten sonra katılımcılar kaydını iptal edemez." error={err('cancelUntil')}>
          <Input type="datetime-local" className="sm:max-w-[calc(50%-0.625rem)]" {...text('cancelUntil')} />
        </Field>
      </FormSection>

      {withPoster && (
        <FormSection title="Afiş" hint="İsteğe bağlı. Afişsiz etkinlikte sayfada yalnız metin görünür.">
          <FileField label="Afiş görseli" hint="JPG, PNG ya da WebP; en fazla 5 MB." accept="image/jpeg,image/png,image/webp" maxBytes={POSTER_MAX_BYTES} value={poster} onChange={setPoster} />
        </FormSection>
      )}

      {extra && <FormSection title="Not">{extra}</FormSection>}

      <div className="flex flex-col gap-4 border-t-2 border-ink pt-6">
        {failure && !serverField && (
          <Notice variant="line" tone={failure.status === 409 ? 'warning' : 'danger'} title="Kaydedilemedi">
            {failure.message}
          </Notice>
        )}
        <div>
          <Button type="submit" variant="primary" size="lg" loading={busy}>
            {submitLabel}
          </Button>
        </div>
      </div>
    </form>
  )
}
