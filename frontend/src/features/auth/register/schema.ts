import { z } from 'zod'
import { emailField, newPasswordField } from '../schemas'

/**
 * Tek form, adım adım doğrulanır. Role göre zorunlu alanlar `superRefine` içinde.
 * Öğrenci numarası biçimi ve e-posta alan adları kurum yapılandırmasında (frontend'e açık değil);
 * bunlar sunucu hatasıyla alanın altına yazılır.
 */
export const applicationSchema = z
  .object({
    kind: z.enum(['student', 'academician'], { message: 'Hesap türünü seçin' }),
    firstName: z.string().trim().min(1, 'Adınızı girin').max(255, 'Ad en fazla 255 karakter olabilir'),
    lastName: z.string().trim().min(1, 'Soyadınızı girin').max(255, 'Soyad en fazla 255 karakter olabilir'),
    email: emailField,
    studentId: z.string().trim().max(255),
    title: z.string(),
    officeNumber: z.string().trim().max(255, 'Ofis en fazla 255 karakter olabilir'),
    facultyId: z.string(),
    departmentId: z.string(),
    programId: z.string(),
    entryYear: z.string(),
    /** Katalog boşsa serbest bölüm adı. */
    departmentText: z.string().trim().max(255, 'Bölüm en fazla 255 karakter olabilir'),
    /** Arayüzün belirlediği bayraklar: katalog var mı, seçilen bölümde program var mı. */
    catalogAvailable: z.boolean(),
    programRequired: z.boolean(),
    password: newPasswordField,
    confirmPassword: z.string().min(1, 'Şifreyi tekrar girin'),
  })
  .superRefine((v, ctx) => {
    const need = (path: keyof typeof v, message: string) => ctx.addIssue({ code: 'custom', path: [path], message })
    const student = v.kind === 'student'

    if (student && !v.studentId) need('studentId', 'Öğrenci numaranızı girin')
    if (!student && !v.title) need('title', 'Unvanınızı seçin')

    if (v.catalogAvailable) {
      if (!v.facultyId) need('facultyId', 'Fakültenizi seçin')
      if (!v.departmentId) need('departmentId', 'Bölümünüzü seçin')
      if (student && v.programRequired && !v.programId) need('programId', 'Programınızı seçin')
    } else if (!v.departmentText) {
      need('departmentText', 'Bölümünüzü yazın')
    }
    if (student && !v.entryYear) need('entryYear', 'Giriş yılınızı seçin')

    // Sunucunun da uyguladığı kural: şifre e-postanın @ öncesini içeremez (4+ karakterse).
    const local = v.email.split('@')[0]?.toLocaleLowerCase('tr-TR') ?? ''
    if (local.length >= 4 && v.password.toLocaleLowerCase('tr-TR').includes(local)) {
      need('password', 'Şifre e-posta adresinizi içeremez')
    }
    if (v.password !== v.confirmPassword) need('confirmPassword', 'Şifreler eşleşmiyor')
  })

export type ApplicationValues = z.infer<typeof applicationSchema>

export const STEPS = [
  { title: 'Hesap türü', fields: ['kind'] },
  { title: 'Kişisel bilgiler', fields: ['firstName', 'lastName', 'email', 'studentId', 'title', 'officeNumber'] },
  { title: 'Akademik birim', fields: ['facultyId', 'departmentId', 'programId', 'entryYear', 'departmentText'] },
  { title: 'Şifre ve belge', fields: ['password', 'confirmPassword'] },
] as const satisfies { title: string; fields: (keyof ApplicationValues)[] }[]

/** Sunucu hata kodu → hangi alan; mesaj boşsa sunucununki gösterilir. */
export const SERVER_FIELD: Record<string, { field: keyof ApplicationValues; message?: string }> = {
  EMAIL_ALREADY_REGISTERED: {
    field: 'email',
    message: 'Bu e-posta adresiyle kayıtlı bir hesap var. Giriş yapın ya da şifrenizi sıfırlayın.',
  },
  STUDENT_REQUEST_ALREADY_EXISTS: { field: 'email' },
  EMAIL_DOMAIN_NOT_ALLOWED: { field: 'email' },
  STUDENT_NUMBER_REQUIRED: { field: 'studentId' },
  INVALID_STUDENT_NUMBER: { field: 'studentId' },
  STUDENT_NUMBER_TAKEN: { field: 'studentId' },
  PROGRAM_NOT_FOUND: { field: 'programId' },
  PROGRAM_INACTIVE: { field: 'programId' },
  DEPARTMENT_NOT_FOUND: { field: 'departmentId' },
  DEPARTMENT_INACTIVE: { field: 'departmentId' },
  TITLE_REQUIRED: { field: 'title' },
  INVALID_TITLE: { field: 'title' },
}

/** Backend `errors[].field` adları (Java alanları) → form alanı. */
export const SERVER_FIELD_ALIAS: Record<string, keyof ApplicationValues> = {
  studentId: 'studentId',
  department: 'departmentText',
}

export function stepOf(field: keyof ApplicationValues): number {
  const i = STEPS.findIndex((s) => (s.fields as readonly string[]).includes(field))
  return i === -1 ? STEPS.length - 1 : i
}
