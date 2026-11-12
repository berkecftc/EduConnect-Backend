import { useState } from 'react'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { hasRole, useSession } from '@/lib/auth/session'
import { DOCUMENT_ACCEPT, DOCUMENT_MAX_BYTES, ENTRY_YEARS, LEVEL_LABEL, useCatalog, useTitles } from '@/features/auth/register/api'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { FileField } from '@/components/ui/FileField'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { Select } from '@/components/ui/Select'
import { Badge } from '@/components/ui/Badge'
import {
  AFFILIATION_CODE_FIELD,
  pendingRequestOf,
  staffRecordRequest,
  studentRecordRequest,
  useAddAffiliation,
  validateStaffRecord,
  validateStudentRecord,
  type AffiliationErrors,
  type AffiliationField,
  type AffiliationKind,
} from './affiliations'

const COPY: Record<AffiliationKind, { add: string; intro: string; label: string }> = {
  student: {
    add: 'Öğrenci kaydı ekle',
    label: 'Öğrenci',
    intro: 'Aynı zamanda bu üniversitede öğrenciyseniz öğrenci kaydınızı bu hesaba ekleyin; ayrı hesap açmanız gerekmez.',
  },
  academician: {
    add: 'Personel kaydı ekle',
    label: 'Personel',
    intro: 'Üniversitede görev alıyorsanız (araştırma görevlisi gibi) personel kaydınızı bu hesaba ekleyin; ayrı hesap açmanız gerekmez.',
  },
}

/**
 * Ek kayıt (F-54): tek bağlılıklı hesap diğer kaydı belgeyle başvurur; doğrulayıcı onaylayınca hesap iki görünümlü olur.
 * Görevli ve yönetici hesaplarında yoktur.
 */
export function AffiliationSettings() {
  const session = useSession()
  const academician = hasRole(session, 'ROLE_ACADEMICIAN')
  const student = hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')
  const [adding, setAdding] = useState(false)
  if (!academician && !student) return null

  if (academician && student) {
    return (
      <Panel title="Kayıtlarım">
        <p className="max-w-[60ch] text-md text-ink-2">
          Hesabınızda hem personel hem öğrenci kaydı var. Menünün altındaki "Görünüm" seçimiyle iki taraf arasında geçebilirsiniz.
        </p>
      </Panel>
    )
  }

  const kind: AffiliationKind = academician ? 'student' : 'academician'
  const copy = COPY[kind]
  if (session?.pendingRequests.includes(pendingRequestOf(kind))) {
    return (
      <Panel title="Ek kayıt" action={<Badge tone="warning">Onay bekleniyor</Badge>}>
        <p className="max-w-[60ch] text-md text-ink-2">
          {copy.label} kaydı başvurunuz doğrulayıcıda. Onaylanınca menünün altından iki görünüm arasında geçebilirsiniz.
        </p>
      </Panel>
    )
  }

  return (
    <Panel title="Ek kayıt">
      <p className="max-w-[60ch] text-md text-ink-2">{copy.intro}</p>
      {adding ? (
        kind === 'student' ? (
          <StudentRecordForm onCancel={() => setAdding(false)} />
        ) : (
          <StaffRecordForm onCancel={() => setAdding(false)} />
        )
      ) : (
        <Button className="mt-5" onClick={() => setAdding(true)}>
          {copy.add}
        </Button>
      )}
    </Panel>
  )
}

/** Sunucu hatası: kodu ya da `errors[]` alanı bilinen hata alanın altına, diğeri genel uyarıya gider. */
function serverErrors(e: ApiError): AffiliationErrors | null {
  const field = e.code ? AFFILIATION_CODE_FIELD[e.code] : undefined
  if (field) return { [field]: e.message }
  if (e.fieldErrors.length === 0) return null
  return Object.fromEntries(e.fieldErrors.map((f) => [f.field as AffiliationField, f.message]))
}

function Actions({ loading, onCancel, failure }: { loading: boolean; onCancel: () => void; failure: ApiError | null }) {
  return (
    <>
      {failure && (
        <Notice variant="line" tone="danger" title="Başvuru gönderilemedi">
          {failure.message}
        </Notice>
      )}
      <div className="flex items-center gap-5">
        <Button type="submit" variant="primary" loading={loading}>
          Başvuruyu gönder
        </Button>
        <button type="button" onClick={onCancel} className="text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline">
          Vazgeç
        </button>
      </div>
    </>
  )
}

function StudentRecordForm({ onCancel }: { onCancel: () => void }) {
  const add = useAddAffiliation('student')
  const catalog = useCatalog()
  const faculties = catalog.data ?? []
  const hasCatalog = faculties.length > 0
  const [studentNumber, setStudentNumber] = useState('')
  const [facultyId, setFacultyId] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [departmentText, setDepartmentText] = useState('')
  const [programId, setProgramId] = useState('')
  const [entryYear, setEntryYear] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<AffiliationErrors>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const faculty = faculties.find((f) => f.id === facultyId)
  const department = faculty?.departments.find((d) => d.id === departmentId)

  const submit = (ev: React.FormEvent) => {
    ev.preventDefault()
    const draft = { studentNumber, programId, entryYear, department: hasCatalog ? (department?.name ?? '') : departmentText, file }
    const next = validateStudentRecord(draft, { catalog: hasCatalog, needsProgram: (department?.programs.length ?? 0) > 0 })
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) return
    add.mutate(
      { request: studentRecordRequest(draft), file: file! },
      {
        onSuccess: () => toast.success('Öğrenci kaydı başvurunuz alındı'),
        onError: (e) => {
          const ae = toApiError(e)
          const fields = serverErrors(ae)
          if (fields) setErrors(fields)
          else setFailure(ae)
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 flex max-w-[32rem] flex-col gap-5">
      <Field label="Öğrenci numarası" required error={errors.studentNumber}>
        <Input autoComplete="off" inputMode="numeric" maxLength={32} value={studentNumber} onChange={(e) => setStudentNumber(e.target.value)} />
      </Field>
      {hasCatalog ? (
        <>
          <Field label="Fakülte veya yüksekokul" required>
            <Select
              placeholder="Seçin"
              value={facultyId}
              onChange={(e) => {
                setFacultyId(e.target.value)
                setDepartmentId('')
                setProgramId('')
              }}
            >
              {faculties.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Bölüm" required error={errors.department ?? errors.departmentId}>
            <Select
              placeholder={faculty ? 'Seçin' : 'Önce fakülteyi seçin'}
              disabled={!faculty}
              value={departmentId}
              onChange={(e) => {
                setDepartmentId(e.target.value)
                setProgramId('')
              }}
            >
              {faculty?.departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
          {(department?.programs.length ?? 0) > 0 && (
            <Field label="Program" required error={errors.programId}>
              <Select placeholder="Seçin" value={programId} onChange={(e) => setProgramId(e.target.value)}>
                {department!.programs.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.name} ({LEVEL_LABEL[p.level]})
                  </option>
                ))}
              </Select>
            </Field>
          )}
        </>
      ) : (
        !catalog.isPending && (
          <Field label="Bölüm" required error={errors.department}>
            <Input autoComplete="off" maxLength={255} value={departmentText} onChange={(e) => setDepartmentText(e.target.value)} />
          </Field>
        )
      )}
      <Field label="Giriş yılı" required hint="Programa kayıt olduğunuz yıl" error={errors.entryYear}>
        <Select placeholder="Seçin" value={entryYear} onChange={(e) => setEntryYear(e.target.value)}>
          {ENTRY_YEARS.map((y) => (
            <option key={y} value={y}>
              {y}
            </option>
          ))}
        </Select>
      </Field>
      <FileField
        label="Öğrenci belgesi"
        hint="PDF, JPEG, PNG veya WEBP; en fazla 5 MB. Yalnız başvurunuzu değerlendiren yetkili görür."
        accept={DOCUMENT_ACCEPT}
        maxBytes={DOCUMENT_MAX_BYTES}
        value={file}
        onChange={setFile}
        error={errors.file}
        required
      />
      <Actions loading={add.isPending} onCancel={onCancel} failure={failure} />
    </form>
  )
}

function StaffRecordForm({ onCancel }: { onCancel: () => void }) {
  const add = useAddAffiliation('academician')
  const catalog = useCatalog()
  const titles = useTitles(true)
  const faculties = catalog.data ?? []
  const hasCatalog = faculties.length > 0
  const [title, setTitle] = useState('')
  const [facultyId, setFacultyId] = useState('')
  const [departmentId, setDepartmentId] = useState('')
  const [departmentText, setDepartmentText] = useState('')
  const [officeNumber, setOfficeNumber] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [errors, setErrors] = useState<AffiliationErrors>({})
  const [failure, setFailure] = useState<ApiError | null>(null)
  const faculty = faculties.find((f) => f.id === facultyId)
  const department = faculty?.departments.find((d) => d.id === departmentId)

  const submit = (ev: React.FormEvent) => {
    ev.preventDefault()
    const draft = { title, departmentId, department: hasCatalog ? (department?.name ?? '') : departmentText, officeNumber, file }
    const next = validateStaffRecord(draft, { catalog: hasCatalog })
    setErrors(next)
    setFailure(null)
    if (Object.keys(next).length) return
    add.mutate(
      { request: staffRecordRequest(draft), file: file! },
      {
        onSuccess: () => toast.success('Personel kaydı başvurunuz alındı'),
        onError: (e) => {
          const ae = toApiError(e)
          const fields = serverErrors(ae)
          if (fields) setErrors(fields)
          else setFailure(ae)
        },
      },
    )
  }

  return (
    <form onSubmit={submit} noValidate className="mt-6 flex max-w-[32rem] flex-col gap-5">
      <Field label="Unvan" required error={errors.title}>
        <Select placeholder="Seçin" value={title} disabled={titles.isPending} onChange={(e) => setTitle(e.target.value)}>
          {(titles.data ?? []).map((t) => (
            <option key={t.code} value={t.code}>
              {t.label}
            </option>
          ))}
        </Select>
      </Field>
      {hasCatalog ? (
        <>
          <Field label="Fakülte veya yüksekokul" required>
            <Select
              placeholder="Seçin"
              value={facultyId}
              onChange={(e) => {
                setFacultyId(e.target.value)
                setDepartmentId('')
              }}
            >
              {faculties.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Bölüm" required error={errors.departmentId}>
            <Select placeholder={faculty ? 'Seçin' : 'Önce fakülteyi seçin'} disabled={!faculty} value={departmentId} onChange={(e) => setDepartmentId(e.target.value)}>
              {faculty?.departments.map((d) => (
                <option key={d.id} value={d.id}>
                  {d.name}
                </option>
              ))}
            </Select>
          </Field>
        </>
      ) : (
        !catalog.isPending && (
          <Field label="Bölüm" required error={errors.department}>
            <Input autoComplete="off" maxLength={255} value={departmentText} onChange={(e) => setDepartmentText(e.target.value)} />
          </Field>
        )
      )}
      <Field label="Ofis" hint="İsteğe bağlı" error={errors.officeNumber}>
        <Input autoComplete="off" maxLength={255} value={officeNumber} onChange={(e) => setOfficeNumber(e.target.value)} />
      </Field>
      <FileField
        label="Personel kimlik kartı"
        hint="PDF, JPEG, PNG veya WEBP; en fazla 5 MB. Yalnız başvurunuzu değerlendiren yetkili görür."
        accept={DOCUMENT_ACCEPT}
        maxBytes={DOCUMENT_MAX_BYTES}
        value={file}
        onChange={setFile}
        error={errors.file}
        required
      />
      <Actions loading={add.isPending} onCancel={onCancel} failure={failure} />
    </form>
  )
}
