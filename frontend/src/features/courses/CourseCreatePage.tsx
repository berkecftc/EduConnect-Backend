import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { nowLocalIso } from '@/lib/time'
import { usePageTitle } from '@/lib/usePageTitle'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { FormSection } from '@/components/ui/FormSection'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Select } from '@/components/ui/Select'
import { EmptyState, PageHeader } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { useCurrentTerm, useTerms } from './api'
import {
  normCode,
  useCanOpenCourse,
  useCatalogSuggestions,
  useCreateCourse,
  validateCourseDraft,
  type CatalogCourse,
  type CourseDraft,
  type CourseDraftErrors,
} from './teach'

/** Backend hata kodu → alan (F-41). */
const CODE_FIELD: Record<string, keyof CourseDraft> = {
  DUPLICATE_COURSE_CODE: 'section',
  TERM_ENDED: 'termId',
  NO_TERM: 'termId',
}

/** Ders aç (F-41, F-42): katalog önerisi, dönem ve şube; taslak olarak kaydet ya da yayımla. */
export function CourseCreatePage() {
  usePageTitle('Ders aç')
  const session = useSession()
  const canOpen = useCanOpenCourse()

  if (!hasRole(session, 'ROLE_ACADEMICIAN') || !canOpen) {
    return (
      <div className="mx-auto max-w-[56rem]">
        <EmptyState title="Ders açamazsınız">
          {hasRole(session, 'ROLE_ACADEMICIAN')
            ? 'Ders koordinatörü öğretim üyesi ya da öğretim görevlisi olmalı. Koordinatör sizi bir dersin kadrosuna asistan olarak ekleyebilir.'
            : 'Ders açma akademisyenlere açıktır.'}
        </EmptyState>
      </div>
    )
  }
  return (
    <div className="mx-auto max-w-[72rem]">
      <Link to="/courses" className="text-sm font-semibold text-ders underline-offset-2 hover:underline">
        Verdiğim dersler
      </Link>
      <PageHeader
        title="Ders aç"
        meta="Dersin koordinatörü siz olursunuz. Hoca ve asistanları ders açıldıktan sonra kadroya ekleyebilirsiniz."
      />
      <div className="mt-8">
        <CourseForm instructorId={session!.userId} />
      </div>
    </div>
  )
}

function CourseForm({ instructorId }: { instructorId: string }) {
  const navigate = useNavigate()
  const terms = useTerms()
  const current = useCurrentTerm()
  const create = useCreateCourse()
  const formRef = useRef<HTMLFormElement>(null)
  const [d, setD] = useState<CourseDraft>({
    code: '',
    title: '',
    description: '',
    credit: '3',
    ects: '',
    capacity: '',
    termId: '',
    section: '',
  })
  const [errors, setErrors] = useState<CourseDraftErrors>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const [pending, setPending] = useState<'draft' | 'publish' | null>(null)
  // Alan değişince o alanın hatası kalkar; sunucu hatası yeniden göndermeye kadar eski bilgiyle kalmasın.
  const set = (k: keyof CourseDraft) => (ev: { target: { value: string } }) => {
    setD((x) => ({ ...x, [k]: ev.target.value }))
    setErrors((e) => (e[k] ? { ...e, [k]: undefined } : e))
    if (failure) setFailure(null)
  }

  // Kod yazılırken katalog önerisi; her tuşta istek gitmesin diye kısa gecikme.
  const [query, setQuery] = useState('')
  useEffect(() => {
    const t = setTimeout(() => setQuery(d.code), 250)
    return () => clearTimeout(t)
  }, [d.code])
  const suggestions = useCatalogSuggestions(query)
  const match: CatalogCourse | undefined = (suggestions.data ?? []).find((c) => normCode(c.code) === normCode(d.code))
  const options = (suggestions.data ?? []).filter((c) => c !== match).slice(0, 6)

  // Bitmemiş dönemler; varsayılan güncel dönem.
  const today = nowLocalIso().slice(0, 10)
  const openTerms = (terms.data ?? []).filter((t) => t.endsOn >= today).sort((a, b) => a.startsOn.localeCompare(b.startsOn))
  const termId = d.termId || current.data?.id || openTerms[0]?.id || ''
  const term = openTerms.find((t) => t.id === termId)
  const started = term ? term.startsOn <= today : false

  const serverField = failure?.code ? CODE_FIELD[failure.code] : undefined
  const err = (k: keyof CourseDraft) =>
    errors[k] ?? (serverField === k ? failure?.message : undefined) ?? failure?.fieldErrors.find((f) => f.field === k)?.message

  const submit = (asDraft: boolean) => {
    const draft: CourseDraft = match
      ? {
          ...d,
          termId,
          code: match.code,
          title: match.title,
          credit: String(match.credit),
          ects: match.ects == null ? '' : String(match.ects),
        }
      : { ...d, termId }
    const next = validateCourseDraft(draft, !!match)
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) {
      requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
      return
    }
    setPending(asDraft ? 'draft' : 'publish')
    create.mutate(
      { draft, instructorId, asDraft, image: null },
      {
        onSuccess: (c) => {
          toast.success(
            asDraft ? 'Ders taslak olarak kaydedildi' : c.status === 'OPEN' ? 'Ders yayımlandı; kayıtlar açık' : 'Ders yayımlandı',
          )
          navigate(`/courses/${c.id}`)
        },
        onError: (e) => {
          const ae = toApiError(e)
          setFailure(ae)
          if (ae.code && CODE_FIELD[ae.code])
            requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())
        },
        onSettled: () => setPending(null),
      },
    )
  }

  const general = failure && !serverField && failure.fieldErrors.length === 0

  return (
    <form
      ref={formRef}
      noValidate
      onSubmit={(e) => {
        e.preventDefault()
        submit(false)
      }}
      className="flex flex-col"
    >
      <FormSection
        title="Ders"
        hint="Kodu yazarken katalogdaki dersler önerilir. Katalogdaki bir dersi seçerseniz ad, kredi ve AKTS katalogdan gelir."
      >
        <Field label="Ders kodu" required error={err('code')} hint="Ör. BİL 342">
          <Input maxLength={32} autoComplete="off" spellCheck={false} value={d.code} onChange={set('code')} />
        </Field>
        {options.length > 0 && !match && (
          <div>
            <p id="katalog-onerileri" className="text-sm text-ink-3">
              Katalogdaki dersler
            </p>
            <ul aria-labelledby="katalog-onerileri" className="mt-1 divide-y divide-rule border-y border-rule">
              {options.map((c) => (
                <li key={c.id}>
                  <button
                    type="button"
                    onClick={() => setD((x) => ({ ...x, code: c.code }))}
                    className="row-fill grid w-full grid-cols-[6.5rem_minmax(0,1fr)_auto] items-baseline gap-x-4 px-1 py-2.5 text-left"
                  >
                    <span className="tabular font-heavy text-ders">{c.code}</span>
                    <span className="truncate font-semibold">{c.title}</span>
                    <span className="tabular text-sm text-ink-3">
                      {c.credit} kredi{c.ects ? `, ${c.ects} AKTS` : ''}
                    </span>
                  </button>
                </li>
              ))}
            </ul>
          </div>
        )}
        {match ? (
          <div className="border-l-[3px] border-ders pl-4">
            <p className="text-lg font-heavy">{match.title}</p>
            <p className="tabular mt-0.5 text-md text-ink-2">
              {match.credit} kredi{match.ects != null ? `, ${match.ects} AKTS` : ''}
            </p>
            <p className="mt-1 text-sm text-ink-3">Katalogdaki ders. Ad, kredi ve AKTS buradan alınır.</p>
          </div>
        ) : (
          <>
            <Field label="Ders adı" required error={err('title')}>
              <Input maxLength={255} value={d.title} onChange={set('title')} />
            </Field>
            <div className="grid gap-5 sm:grid-cols-2">
              <Field label="Kredi" required error={err('credit')}>
                <Input inputMode="numeric" maxLength={2} value={d.credit} onChange={set('credit')} />
              </Field>
              <Field label="AKTS" hint="İsteğe bağlı." error={err('ects')}>
                <Input inputMode="numeric" maxLength={2} value={d.ects} onChange={set('ects')} />
              </Field>
            </div>
            {d.code.trim().length >= 2 && suggestions.isSuccess && options.length === 0 && normCode(query) === normCode(d.code) && (
              <p className="text-sm text-ink-3">Bu kod katalogda yok; ders kataloğa yeni ders olarak eklenir.</p>
            )}
          </>
        )}
      </FormSection>

      <FormSection title="Dönem ve şube" hint="Aynı dersi birden çok şubeyle açabilirsiniz; her şubenin kontenjanı ve öğrencileri ayrıdır.">
        <Field label="Dönem" required error={err('termId')}>
          <Select value={termId} onChange={set('termId')} disabled={terms.isPending}>
            {openTerms.map((t) => (
              <option key={t.id} value={t.id}>
                {t.label}
                {t.id === current.data?.id ? ' (bu dönem)' : ''}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Şube" hint="Boş bırakırsanız 1. şube olur." error={err('section')}>
          <Input maxLength={10} autoComplete="off" value={d.section} onChange={set('section')} className="max-w-[8rem]" />
        </Field>
      </FormSection>

      <FormSection title="Kayıt" hint="Açıklama katalogda ve ders sayfasında görünür. İkisini de sonradan değiştirebilirsiniz.">
        <Field label="Kontenjan" required error={err('capacity')}>
          <Input inputMode="numeric" maxLength={5} value={d.capacity} onChange={set('capacity')} className="max-w-[8rem]" />
        </Field>
        <Field label="Açıklama" hint="İsteğe bağlı. Dersin konuları, işlenişi ve önkoşulları." error={err('description')}>
          <Textarea rows={6} maxLength={5000} valueLength={d.description.length} value={d.description} onChange={set('description')} />
        </Field>
      </FormSection>

      <div className="flex flex-col gap-5 border-t border-ink pt-6">
        <p className="max-w-[64ch] text-md text-ink-2">
          {started
            ? 'Bu dönem başladı: yayımlanan ders hemen "Devam ediyor" olur ve öğrenciler katalogdan başvurabilir.'
            : 'Yayımlanan ders "Kayıt açık" olarak katalogda görünür. Taslak yalnız size ve kadroya görünür; hazır olunca yayımlarsınız.'}
        </p>
        {general && (
          <Notice variant="line" tone="danger" title="Ders açılamadı">
            {failure.message}
          </Notice>
        )}
        <div className="flex flex-wrap items-center gap-x-5 gap-y-3">
          <Button type="submit" variant="primary" size="lg" loading={pending === 'publish'} disabled={!!pending}>
            Dersi yayımla
          </Button>
          <Button type="button" size="lg" loading={pending === 'draft'} disabled={!!pending} onClick={() => submit(true)}>
            Taslak olarak kaydet
          </Button>
          <Link to="/courses" className="text-md font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline">
            Vazgeç
          </Link>
        </div>
      </div>
    </form>
  )
}
