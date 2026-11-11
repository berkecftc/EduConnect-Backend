import { useEffect, useMemo, useRef, useState } from 'react'
import { useForm, type FieldPath } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { BriefcaseBusiness, GraduationCap } from 'lucide-react'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import { Button } from '@/components/ui/Button'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { Input, PasswordInput } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Select } from '@/components/ui/Select'
import { Spinner } from '@/components/ui/Spinner'
import { StationLine, type StationState } from '@/components/ui/StationLine'
import { AuthLayout } from '../AuthLayout'
import { PASSWORD_MIN } from '../schemas'
import {
  DOCUMENT_ACCEPT,
  DOCUMENT_MAX_BYTES,
  ENTRY_YEARS,
  LEVEL_LABEL,
  submitApplication,
  useCatalog,
  useTitles,
  type ApplicationRequest,
} from './api'
import { SERVER_FIELD, SERVER_FIELD_ALIAS, STEPS, applicationSchema, stepOf, type ApplicationValues } from './schema'

const DRAFT_KEY = 'ec-register-draft'
/** Taslakta saklanmayan alanlar: şifreler ve arayüz bayrakları. */
const NOT_DRAFTED = new Set(['password', 'confirmPassword', 'catalogAvailable', 'programRequired'])

const EMPTY: ApplicationValues = {
  kind: undefined as unknown as ApplicationValues['kind'],
  firstName: '',
  lastName: '',
  email: '',
  studentId: '',
  title: '',
  officeNumber: '',
  facultyId: '',
  departmentId: '',
  programId: '',
  entryYear: '',
  departmentText: '',
  catalogAvailable: true,
  programRequired: false,
  password: '',
  confirmPassword: '',
}

function readDraft(): ApplicationValues {
  try {
    const raw = sessionStorage.getItem(DRAFT_KEY)
    return raw ? { ...EMPTY, ...(JSON.parse(raw) as Partial<ApplicationValues>) } : EMPTY
  } catch {
    return EMPTY
  }
}

/**
 * Hesap başvurusu (F-52, F-53, F-54): dört adım, her adım ilerlemeden önce doğrulanır.
 * Taslak (şifre ve dosya hariç) oturum boyunca saklanır; sunucu hataları ilgili alana ve adıma döner.
 */
export function RegisterPage() {
  usePageTitle('Hesap başvurusu')
  const form = useForm<ApplicationValues>({
    resolver: zodResolver(applicationSchema),
    mode: 'onTouched',
    defaultValues: readDraft(),
  })
  const { register, watch, setValue, trigger, handleSubmit, setError, setFocus, formState } = form
  const [step, setStep] = useState(0)
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string | undefined>()
  const [failure, setFailure] = useState<ApiError | null>(null)
  const [sentTo, setSentTo] = useState<string | null>(null)
  const pendingFocus = useRef<FieldPath<ApplicationValues> | null>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const firstRender = useRef(true)

  const kind = watch('kind')
  const facultyId = watch('facultyId')
  const departmentId = watch('departmentId')
  const student = kind === 'student'

  const catalog = useCatalog()
  const titles = useTitles(kind === 'academician')
  const faculties = useMemo(() => catalog.data ?? [], [catalog.data])
  const faculty = faculties.find((f) => f.id === facultyId)
  const department = faculty?.departments.find((d) => d.id === departmentId)

  // Katalog boşsa ya da yüklenemediyse serbest bölüm adına düşülür (başvuru yine yapılabilsin);
  // seçilen bölümde program varsa öğrenci için zorunlu olur.
  useEffect(() => {
    if (catalog.isSuccess) setValue('catalogAvailable', faculties.length > 0)
    else if (catalog.isError) setValue('catalogAvailable', false)
  }, [catalog.isSuccess, catalog.isError, faculties.length, setValue])
  const catalogAvailable = watch('catalogAvailable')
  useEffect(() => {
    setValue('programRequired', (department?.programs.length ?? 0) > 0)
  }, [department, setValue])

  // Taslak: şifre ve dosya dışındaki alanlar sekme kapanana kadar saklanır.
  useEffect(() => {
    const sub = watch((values) => {
      try {
        const draft = Object.fromEntries(Object.entries(values).filter(([k]) => !NOT_DRAFTED.has(k)))
        sessionStorage.setItem(DRAFT_KEY, JSON.stringify(draft))
      } catch {
        // Depolama kapalıysa taslak tutulmaz.
      }
    })
    return () => sub.unsubscribe()
  }, [watch])

  // Adım değişince odak adım başlığına; sunucu hatasıyla dönülmüşse hatalı alana.
  // Bekleyen alan ref'te tutulur: durum olsaydı sıfırlanması efekti yeniden tetikleyip odağı başlığa çalardı.
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    const field = pendingFocus.current
    pendingFocus.current = null
    if (field) setFocus(field)
    else heading.current?.focus()
  }, [step, setFocus])

  const last = step === STEPS.length - 1

  const next = async () => {
    const ok = await trigger([...STEPS[step]!.fields])
    if (ok) setStep((s) => s + 1)
  }

  const fileMissing = () => {
    if (file) return false
    setFileError(student ? 'Öğrenci belgenizi ekleyin' : 'Personel kimlik kartınızı ekleyin')
    return true
  }

  // Geçersiz gönderimde de belge kontrol edilir: bütün hatalar tek seferde görünsün.
  const onSubmit = handleSubmit(async (v) => {
    setFailure(null)
    if (!file) {
      fileMissing()
      return
    }
    const request: ApplicationRequest = {
      email: v.email,
      password: v.password,
      firstName: v.firstName,
      lastName: v.lastName,
      department: department?.name ?? (v.departmentText || undefined),
      ...(student
        ? { studentId: v.studentId.replace(/\s+/g, ''), programId: v.programId || undefined, entryYear: Number(v.entryYear) || undefined }
        : { title: v.title, departmentId: v.departmentId || undefined, officeNumber: v.officeNumber || undefined }),
    }
    try {
      await submitApplication(v.kind, request, file)
      try {
        sessionStorage.removeItem(DRAFT_KEY)
      } catch {
        // yok say
      }
      setSentTo(v.email)
    } catch (err) {
      applyServerError(toApiError(err))
    }
  }, () => void fileMissing())

  /** Sunucu hatasını ilgili alana yazar ve o alanın adımına döner. */
  const applyServerError = (e: ApiError) => {
    const fields: { field: keyof ApplicationValues; message: string }[] = []
    for (const fe of e.fieldErrors) {
      const field = (SERVER_FIELD_ALIAS[fe.field] ?? fe.field) as keyof ApplicationValues
      if (field in EMPTY) fields.push({ field, message: fe.message })
    }
    const mapped = SERVER_FIELD[e.code]
    if (mapped) fields.push({ field: mapped.field, message: mapped.message ?? e.message })

    if (e.status === 413) {
      setFileError('Dosya en fazla 5 MB olabilir.')
      return
    }
    if (fields.length === 0) {
      setFailure(e)
      return
    }
    for (const f of fields) setError(f.field, { type: 'server', message: f.message })
    const target = fields.reduce((a, b) => (stepOf(b.field) < stepOf(a.field) ? b : a))
    const targetStep = stepOf(target.field)
    if (targetStep === step) {
      setFocus(target.field)
    } else {
      pendingFocus.current = target.field
      setStep(targetStep)
    }
  }

  if (sentTo) return <Submitted email={sentTo} />

  const stations = STEPS.map((s, i) => ({
    label: s.title,
    state: (i < step ? 'done' : i === step ? 'current' : 'pending') as StationState,
  }))
  const err = formState.errors

  return (
    <AuthLayout wide>
      <h1 className="text-3xl">Hesap başvurusu</h1>
      <p className="mt-2 text-ink-2">
        Başvurunuz kurum yetkilisi tarafından onaylandıktan sonra hesabınız açılır.{' '}
        <span className="text-ink-3">Yıldızlı alanlar zorunlu.</span>
      </p>

      <StationLine className="mt-8" line="yonetim" label="Başvuru adımları" stations={stations} />

      <form onSubmit={last ? onSubmit : (e) => (e.preventDefault(), void next())} noValidate className="mt-8">
        <h2 ref={heading} tabIndex={-1} className="text-xl outline-none">
          <span className="sr-only">
            Adım {step + 1} / {STEPS.length}:{' '}
          </span>
          {STEPS[step]!.title}
        </h2>

        <div className="mt-5 flex flex-col gap-5">
          {step === 0 && (
            <ChoiceGroup
              label="Kurumdaki rolünüz"
              value={kind}
              onChange={(v) => setValue('kind', v, { shouldValidate: true })}
              error={err.kind?.message}
              choices={[
                {
                  value: 'student',
                  title: 'Öğrenci',
                  description: 'Ön lisans, lisans veya lisansüstü öğrencisi',
                  icon: <GraduationCap className="size-5" aria-hidden />,
                },
                {
                  value: 'academician',
                  title: 'Akademik personel',
                  description: 'Öğretim üyesi, öğretim görevlisi veya araştırma görevlisi',
                  icon: <BriefcaseBusiness className="size-5" aria-hidden />,
                },
              ]}
            />
          )}

          {step === 1 && (
            <>
              <div className="grid gap-5 sm:grid-cols-2">
                <Field label="Ad" required error={err.firstName?.message}>
                  <Input autoComplete="given-name" maxLength={255} {...register('firstName')} />
                </Field>
                <Field label="Soyad" required error={err.lastName?.message}>
                  <Input autoComplete="family-name" maxLength={255} {...register('lastName')} />
                </Field>
              </div>
              <Field
                label="Kurum e-postası"
                required
                hint={student ? 'Öğrenci e-posta adresiniz, ör. ad.soyad@ogr.universite.edu.tr' : 'Personel e-posta adresiniz'}
                error={err.email?.message}
              >
                <Input type="email" inputMode="email" autoComplete="email" maxLength={255} {...register('email')} />
              </Field>
              {student ? (
                <Field label="Öğrenci numarası" required hint="Öğrenci kimlik kartınızdaki numara" error={err.studentId?.message}>
                  <Input inputMode="numeric" autoComplete="off" maxLength={255} {...register('studentId')} />
                </Field>
              ) : (
                <>
                  <Field label="Unvan" required error={err.title?.message}>
                    <Select placeholder={titles.isPending ? 'Unvanlar yükleniyor' : 'Seçin'} disabled={!titles.data} {...register('title')}>
                      {titles.data?.map((t) => (
                        <option key={t.code} value={t.code}>
                          {t.label}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  {titles.isError && (
                    <LoadError what="Unvan listesi" error={toApiError(titles.error)} onRetry={() => void titles.refetch()} />
                  )}
                  <Field label="Ofis" hint="İsteğe bağlı, ör. B Blok 312" error={err.officeNumber?.message}>
                    <Input autoComplete="off" maxLength={255} {...register('officeNumber')} />
                  </Field>
                </>
              )}
            </>
          )}

          {step === 2 && (
            <>
              {catalog.isPending && (
                <p className="flex items-center gap-2 text-ink-2">
                  <Spinner /> Akademik birimler yükleniyor
                </p>
              )}
              {catalog.isError && (
                <LoadError what="Akademik birimler" error={toApiError(catalog.error)} onRetry={() => void catalog.refetch()} />
              )}
              {catalog.isSuccess && faculties.length > 0 && (
                <>
                  <Field label="Fakülte veya yüksekokul" required error={err.facultyId?.message}>
                    <Select
                      placeholder="Seçin"
                      {...register('facultyId', {
                        onChange: () => {
                          setValue('departmentId', '')
                          setValue('programId', '')
                        },
                      })}
                    >
                      {faculties.map((f) => (
                        <option key={f.id} value={f.id}>
                          {f.name}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  <Field label="Bölüm" required error={err.departmentId?.message}>
                    <Select
                      placeholder={faculty ? 'Seçin' : 'Önce fakülteyi seçin'}
                      disabled={!faculty}
                      {...register('departmentId', { onChange: () => setValue('programId', '') })}
                    >
                      {faculty?.departments.map((d) => (
                        <option key={d.id} value={d.id}>
                          {d.name}
                        </option>
                      ))}
                    </Select>
                  </Field>
                  {student && (department?.programs.length ?? 0) > 0 && (
                    <Field label="Program" required error={err.programId?.message}>
                      <Select placeholder="Seçin" {...register('programId')}>
                        {department!.programs.map((p) => (
                          <option key={p.id} value={p.id}>
                            {p.name} ({LEVEL_LABEL[p.level]})
                          </option>
                        ))}
                      </Select>
                    </Field>
                  )}
                </>
              )}
              {!catalogAvailable && !catalog.isPending && (
                <Field
                  label="Bölüm"
                  required
                  hint={
                    catalog.isError
                      ? 'Listeyi yükleyemezseniz bölümünüzü yazarak devam edebilirsiniz.'
                      : 'Akademik birim listesi henüz tanımlı değil; bölümünüzü yazın.'
                  }
                  error={err.departmentText?.message}
                >
                  <Input autoComplete="off" maxLength={255} {...register('departmentText')} />
                </Field>
              )}
              {student && (
                <Field label="Giriş yılı" required hint="Programa kayıt olduğunuz yıl" error={err.entryYear?.message}>
                  <Select placeholder="Seçin" {...register('entryYear')}>
                    {ENTRY_YEARS.map((y) => (
                      <option key={y} value={y}>
                        {y}
                      </option>
                    ))}
                  </Select>
                </Field>
              )}
            </>
          )}

          {step === 3 && (
            <>
              <Field
                label="Şifre"
                required
                hint={`En az ${PASSWORD_MIN} karakter. E-posta adresinizi içermesin.`}
                error={err.password?.message}
              >
                <PasswordInput autoComplete="new-password" maxLength={128} {...register('password')} />
              </Field>
              <Field label="Şifre (tekrar)" required error={err.confirmPassword?.message}>
                <PasswordInput autoComplete="new-password" maxLength={128} {...register('confirmPassword')} />
              </Field>
              <FileField
                label={student ? 'Öğrenci belgesi' : 'Personel kimlik kartı'}
                hint="PDF, JPEG, PNG veya WEBP; en fazla 5 MB. Yalnız başvurunuzu değerlendiren yetkili görür."
                accept={DOCUMENT_ACCEPT}
                maxBytes={DOCUMENT_MAX_BYTES}
                value={file}
                onChange={(f) => {
                  setFile(f)
                  setFileError(undefined)
                }}
                error={fileError}
                required
              />
            </>
          )}

          {failure && (
            <Notice tone={failure.status === 429 || failure.status === 503 ? 'warning' : 'danger'} title="Başvuru gönderilemedi">
              {failure.message}
            </Notice>
          )}
        </div>

        <div className="mt-8 flex items-center justify-between gap-3">
          {step > 0 ? (
            <Button variant="ghost" onClick={() => setStep((s) => s - 1)}>
              Geri
            </Button>
          ) : (
            <span />
          )}
          <Button type="submit" variant="primary" size="lg" loading={formState.isSubmitting}>
            {last ? 'Başvuruyu gönder' : 'Devam et'}
          </Button>
        </div>
      </form>

      <p className="mt-8 text-md text-ink-2">
        Hesabınız var mı?{' '}
        <Link to="/login" className="font-semibold text-ders underline-offset-2 hover:underline">
          Giriş yapın
        </Link>
      </p>
    </AuthLayout>
  )
}

function LoadError({ what, error, onRetry }: { what: string; error: ApiError; onRetry: () => void }) {
  return (
    <Notice
      tone="warning"
      title={`${what} yüklenemedi`}
      action={
        <Button size="sm" onClick={onRetry}>
          Tekrar dene
        </Button>
      }
    >
      {error.message}
    </Notice>
  )
}

/** Başarı ekranı: sıradaki adımlar durak çizgisiyle. */
function Submitted({ email }: { email: string }) {
  return (
    <AuthLayout wide>
      <h1 className="text-3xl">Başvurunuz alındı</h1>
      <p className="mt-3 text-ink-2">
        <span className="font-semibold text-ink">{email}</span> adresine bir doğrulama bağlantısı gönderdik. Bağlantıya
        tıkladıktan sonra başvurunuz yetkili onayına düşer; onaylanınca e-postayla haber veririz.
      </p>
      <StationLine
        className="mt-8"
        line="yonetim"
        label="Başvurunun sıradaki adımları"
        stations={[
          { label: 'Başvuru', state: 'done' },
          { label: 'E-posta doğrulama', state: 'current', detail: '24 saat geçerli' },
          { label: 'Yetkili onayı', state: 'pending' },
          { label: 'Hesap etkin', state: 'pending' },
        ]}
      />
      <div className="mt-10 flex flex-wrap items-center gap-4">
        <Button asChild variant="primary">
          <Link to="/login">Girişe dön</Link>
        </Button>
        <Link to="/verify-email" className="text-md font-semibold text-ders underline-offset-2 hover:underline">
          Doğrulama e-postası gelmedi mi?
        </Link>
      </div>
    </AuthLayout>
  )
}
