import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { usePageTitle } from '@/lib/usePageTitle'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { FormSection } from '@/components/ui/FormSection'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { EmptyState, PageHeader, Skeleton } from '@/components/ui/States'
import { Textarea } from '@/components/ui/Textarea'
import { AcademicianPicker, type PickedAcademician } from './AcademicianPicker'
import {
  ABOUT_MAX,
  CLUB_NAME_MAX,
  parseFounderNumbers,
  useMyCreationRequests,
  useRequestClubCreation,
  validateCreationDraft,
  type ClubCreationErrors,
} from './governance'

/** Backend hata kodu → alan. */
const CODE_FIELD: Record<string, keyof ClubCreationErrors> = {
  CLUB_NAME_TAKEN: 'name',
  ADVISOR_NOT_ELIGIBLE: 'advisorId',
  FOUNDERS_REQUIRED: 'founders',
  FOUNDER_STUDENT_NOT_FOUND: 'founders',
  INVALID_FOUNDER: 'founders',
  TOO_MANY_FOUNDERS: 'founders',
}

/**
 * Kulüp kur (F-28, F-89): ad, tanıtım, kurucular ve danışman. Kurucular davete yanıt verince başvuru danışmana gider;
 * onaylanınca başvuran başkan olur.
 */
export function ClubCreatePage() {
  usePageTitle('Kulüp kur')
  const session = useSession()
  const student = hasRole(session, 'ROLE_STUDENT')
  const mine = useMyCreationRequests(student)
  const open = (mine.data ?? []).find((r) => r.status === 'PENDING' || r.status === 'PENDING_FOUNDERS')

  return (
    <div className="mx-auto max-w-[72rem]">
      <Link to="/clubs/mine" className="text-sm font-semibold text-kulup underline-offset-2 hover:underline">
        Kulüplerim
      </Link>
      <PageHeader
        title="Kulüp kur"
        meta="Kurucular davetinize yanıt verince başvurunuz önerdiğiniz danışmana gider. Danışman onaylarsa kulüp kurulur ve siz başkanı olursunuz."
      />
      <div className="mt-8">
        {!student ? (
          <EmptyState title="Kulüp kuramazsınız">Kulüp kuruluş başvurusunu yalnız öğrenciler yapabilir.</EmptyState>
        ) : mine.isPending ? (
          <Skeleton rows={4} />
        ) : open ? (
          <EmptyState
            title="Bekleyen bir kuruluş başvurunuz var"
            action={
              <Button asChild variant="primary">
                <Link to="/clubs/mine">Başvuruyu gör</Link>
              </Button>
            }
          >
            “{open.clubName}” başvurusu sonuçlanmadan yeni başvuru yapamazsınız.
          </EmptyState>
        ) : (
          <CreateForm />
        )}
      </div>
    </div>
  )
}

function CreateForm() {
  const navigate = useNavigate()
  const create = useRequestClubCreation()
  const formRef = useRef<HTMLFormElement>(null)
  const [name, setName] = useState('')
  const [about, setAbout] = useState('')
  const [advisor, setAdvisor] = useState<PickedAcademician | null>(null)
  const [founders, setFounders] = useState('')
  const founderCount = parseFounderNumbers(founders).length
  const [errors, setErrors] = useState<ClubCreationErrors>({})
  const [failure, setFailure] = useState<ApiError | null>(null)

  const serverField = failure?.code ? CODE_FIELD[failure.code] : undefined
  const err = (k: keyof ClubCreationErrors) =>
    errors[k] ??
    (serverField === k ? failure?.message : undefined) ??
    failure?.fieldErrors.find((f) => f.field === (k === 'advisorId' ? 'academicAdvisorId' : k))?.message
  const general = failure && !serverField && failure.fieldErrors.length === 0
  const clear = (k: keyof ClubCreationErrors) => {
    setErrors((e) => (e[k] ? { ...e, [k]: undefined } : e))
    if (failure) setFailure(null)
  }
  const focusInvalid = () => requestAnimationFrame(() => formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus())

  const onSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    const draft = { name, about, advisorId: advisor?.id, founders }
    const next = validateCreationDraft(draft)
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) return focusInvalid()
    create.mutate(draft, {
      onSuccess: () => {
        toast.success(`“${name.trim()}” başvurunuz alındı`)
        navigate('/clubs/mine')
      },
      onError: (x) => {
        const ae = toApiError(x)
        setFailure(ae)
        if (ae.code && CODE_FIELD[ae.code]) focusInvalid()
      },
    })
  }

  return (
    <form ref={formRef} noValidate onSubmit={onSubmit} className="flex flex-col">
      <FormSection
        title="Kulüp"
        hint="Ad kulüpler arasında benzersiz olmalı. Kategori, iletişim bilgileri ve logo kulüp kurulduktan sonra eklenir."
      >
        <Field label="Kulübün adı" required error={err('name')}>
          <Input
            maxLength={CLUB_NAME_MAX}
            autoComplete="off"
            value={name}
            onChange={(e) => {
              setName(e.target.value)
              clear('name')
            }}
          />
        </Field>
        <Field
          label="Tanıtım"
          hint="İsteğe bağlı. Kulübün amacı ve ne tür çalışmalar yapacağı; danışman başvuruyu buna bakarak değerlendirir."
        >
          <Textarea rows={6} maxLength={ABOUT_MAX} valueLength={about.length} value={about} onChange={(e) => setAbout(e.target.value)} />
        </Field>
      </FormSection>

      <FormSection
        title="Kurucular"
        hint="Sizinle birlikte kulübü kuracak öğrenciler. Her biri davet alır; kulüp kurulursa onaylayanlar üye olur."
      >
        <Field
          label="Kurucu öğrenci numaraları"
          required
          error={err('founders')}
          hint={
            founderCount > 0
              ? `Sizinle birlikte ${founderCount + 1} kurucu. Her satıra bir numara yazın.`
              : 'Her satıra bir numara yazın. Kendi numaranızı eklemeniz gerekmez; siz kurucu sayılırsınız.'
          }
        >
          <Textarea
            rows={5}
            autoComplete="off"
            spellCheck={false}
            value={founders}
            onChange={(e) => {
              setFounders(e.target.value)
              clear('founders')
            }}
          />
        </Field>
      </FormSection>

      <FormSection
        title="Danışman"
        hint="Danışman, kulübün etkinliklerini ve kararlarını onaylayan akademisyendir. Kurucular yanıt verince başvurunuzu o değerlendirir."
      >
        <AcademicianPicker
          label="Danışman olacak akademisyen"
          value={advisor}
          error={err('advisorId')}
          onChange={(v) => {
            setAdvisor(v)
            clear('advisorId')
          }}
        />
      </FormSection>

      <div className="flex flex-col gap-5 border-t border-ink pt-6">
        <p className="max-w-[64ch] text-md text-ink-2">
          Kurucuların yanıtlarını ve danışmanın kararını Kulüplerim sayfasından izleyebilirsiniz; karar verildiğinde bildirim alırsınız.
        </p>
        {general && (
          <Notice variant="line" tone="danger" title="Başvuru gönderilemedi">
            {failure.message}
          </Notice>
        )}
        <div className="flex flex-wrap items-center gap-5">
          <Button type="submit" variant="primary" loading={create.isPending}>
            Başvuruyu gönder
          </Button>
          <Link to="/clubs/mine" className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline">
            Vazgeç
          </Link>
        </div>
      </div>
    </form>
  )
}
