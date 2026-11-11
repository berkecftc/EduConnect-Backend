/**
 * Akademisyen önizlemesi (`/onizleme?rol=akademisyen`): Dr. Öğr. Üyesi Mehmet Kaya'nın verdiği dersler,
 * başvurular, öğrenciler, duyuru ve materyaller. Bellekte tutulur; sayfa yenilenince sıfırlanır.
 * Biçimler gerçek API ile aynıdır. Yalnız geliştirme önizlemesi içindir.
 */
import type { InternalAxiosRequestConfig } from 'axios'
import { nowLocalIso } from '@/lib/time'
import type { Announcement, Course, CourseStaff, Material, Term } from '@/features/courses/api'
import type { CatalogCourse, EnrolledStudent, EnrollmentEvent, StaffApplication, TeachingCourse } from '@/features/courses/teach'
import type { Me } from '@/features/me/useMe'
import * as fx from './fixtures'

type Handler = (m: RegExpMatchArray, config: InternalAxiosRequestConfig) => unknown

/** Önizleme hangi rolle açıldı; PreviewPage ayarlar, adaptör tabloları buna göre seçer. */
export const state = { academician: false }

export const ME_ID = 's1'
const ME_NAME = 'Dr. Öğr. Üyesi Mehmet Kaya'
const ago = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString()

export const me: Me = {
  ...fx.me,
  id: ME_ID,
  firstName: 'Mehmet',
  lastName: 'Kaya',
  email: 'mehmet.kaya@ornek.edu.tr',
  role: 'Academician',
  studentNumber: null,
  title: 'Dr. Öğr. Üyesi',
  academicTitle: 'ASSISTANT_PROFESSOR',
  programId: null,
  programName: null,
  programLevel: null,
  entryYear: null,
  classYear: null,
  staffCategory: 'FACULTY_MEMBER',
  affiliations: ['ACADEMICIAN'],
  officeNumber: 'B-312',
  officeHours: 'Salı 14.00–16.00',
  studentStatus: null,
  staffStatus: 'ACTIVE',
}

const spring: Term = {
  id: 't2',
  academicYear: 2027,
  season: 'SPRING',
  label: '2026-2027 Bahar',
  startsOn: '2027-02-15',
  endsOn: '2027-06-30',
  enrollmentOpensOn: '2027-02-01',
  enrollmentClosesOn: '2027-02-19',
}
export const terms: Term[] = [...fx.terms, spring]

// ——— Öğrenciler ———

const FIRST = [
  'Ece',
  'Mert',
  'Burcu',
  'Kaan',
  'Zeynep',
  'Emre',
  'Selin',
  'Barış',
  'Defne',
  'Onur',
  'İrem',
  'Cem',
  'Nazlı',
  'Tolga',
  'Aslı',
  'Umut',
  'Gizem',
  'Furkan',
  'Melis',
  'Oğuz',
  'Derya',
  'Serkan',
  'Ilgın',
  'Yiğit',
]
const LAST = [
  'Yalçın',
  'Aksu',
  'Öz',
  'Tekin',
  'Şahin',
  'Kılıç',
  'Aydın',
  'Doğan',
  'Yıldız',
  'Erdem',
  'Polat',
  'Kurt',
  'Özkan',
  'Çetin',
  'Koç',
  'Güneş',
  'Bulut',
  'Karaca',
  'Tunç',
]
const DEPARTMENTS = [
  'Bilgisayar Mühendisliği',
  'Bilgisayar Mühendisliği',
  'Bilgisayar Mühendisliği',
  'Yazılım Mühendisliği',
  'Elektrik-Elektronik Mühendisliği',
]

/** Belirlenimli öğrenci listesi: aynı ders her açılışta aynı adlarla gelir. */
function roster(courseId: string, n: number, offset: number, year: number): EnrolledStudent[] {
  return Array.from({ length: n }, (_, i) => {
    const k = i + offset
    const firstName = FIRST[(k * 7) % FIRST.length]!
    const lastName = LAST[(k * 5 + 3) % LAST.length]!
    const number = `${year}${String(1000 + ((k * 37) % 900)).padStart(4, '0')}`
    return {
      studentId: `${courseId}-o${i}`,
      firstName,
      lastName,
      studentNumber: number,
      email:
        `${firstName}.${lastName}`
          .toLocaleLowerCase('tr-TR')
          .replace(/[çğıöşü]/g, (c) => ({ ç: 'c', ğ: 'g', ı: 'i', ö: 'o', ş: 's', ü: 'u' })[c]!) + '@ogr.ornek.edu.tr',
      department: DEPARTMENTS[k % DEPARTMENTS.length]!,
      enrollmentDate: ago(24 * (18 + (k % 6))),
    }
  })
}

export const students: Record<string, EnrolledStudent[]> = {
  c1: roster('c1', 54, 0, 2023),
  c21: roster('c21', 87, 11, 2025),
  c22: [],
  c23: roster('c23', 48, 5, 2024),
  c24: roster('c24', 40, 17, 2023),
}

const fullName = (s: EnrolledStudent) => `${s.firstName} ${s.lastName}`

// ——— Dersler ———

type Stored = Omit<TeachingCourse, 'enrolledStudentCount' | 'pendingApplicationCount'> & { coordinatorName: string }

const stored = (c: Partial<Stored> & Pick<Stored, 'id' | 'code' | 'title' | 'status' | 'staffRole' | 'capacity'>): Stored => ({
  description: null,
  credit: 3,
  termId: 't1',
  termLabel: '2026-2027 Güz',
  section: '1',
  ects: 5,
  coordinatorName: ME_NAME,
  ...c,
})

export const teaching: Stored[] = [
  stored({
    id: 'c1',
    code: 'BİL 301',
    title: 'Veri Yapıları ve Algoritmalar',
    status: 'ACTIVE',
    staffRole: 'COORDINATOR',
    capacity: 60,
    credit: 4,
    ects: 6,
    description: fx.courses[0]!.description,
  }),
  stored({
    id: 'c21',
    code: 'BİL 101',
    title: 'Programlamaya Giriş',
    status: 'ACTIVE',
    staffRole: 'INSTRUCTOR',
    capacity: 90,
    section: '3',
    coordinatorName: 'Doç. Dr. Ayşe Arslan',
    description:
      'Python ile programlamanın temelleri: değişkenler, koşullar, döngüler, fonksiyonlar, listeler ve dosyalar. Haftalık laboratuvar oturumları.',
  }),
  stored({
    id: 'c22',
    code: 'BİL 499',
    title: 'Bitirme Projesi I',
    status: 'DRAFT',
    staffRole: 'COORDINATOR',
    capacity: 20,
    termId: 't2',
    termLabel: '2026-2027 Bahar',
    ects: 8,
  }),
  stored({
    id: 'c23',
    code: 'BİL 202',
    title: 'Nesneye Yönelik Programlama',
    status: 'COMPLETED',
    staffRole: 'COORDINATOR',
    capacity: 50,
    termId: 't0',
    termLabel: '2025-2026 Bahar',
  }),
  stored({
    id: 'c24',
    code: 'BİL 102',
    title: 'Algoritma ve Programlama',
    status: 'ARCHIVED',
    staffRole: 'COORDINATOR',
    capacity: 45,
    termId: 't-1',
    termLabel: '2024-2025 Bahar',
  }),
]

// ——— Başvurular ———

const applicant = (
  courseId: string,
  i: number,
  status: StaffApplication['status'],
  hours: number,
  extra: Partial<StaffApplication> = {},
): StaffApplication => {
  const s = roster(courseId, 1, 200 + i, 2024)[0]!
  const c = teaching.find((x) => x.id === courseId)!
  return {
    id: `${courseId}-b${i}`,
    courseId,
    courseTitle: c.title,
    courseCode: c.code,
    studentId: `${courseId}-a${i}`,
    studentName: fullName(s),
    studentNumber: s.studentNumber,
    studentEmail: s.email,
    status,
    applicationDate: ago(hours),
    processedDate: status === 'PENDING' ? null : ago(hours - 20),
    rejectionReason: null,
    ...extra,
  }
}

export const applications: StaffApplication[] = [
  applicant('c1', 1, 'PENDING', 4),
  applicant('c1', 2, 'PENDING', 30),
  applicant('c1', 3, 'PENDING', 52),
  applicant('c1', 4, 'APPROVED', 24 * 19),
  applicant('c1', 5, 'REJECTED', 24 * 18, { rejectionReason: 'Önkoşul olan BİL 201 dersini henüz tamamlamadınız.' }),
  applicant('c1', 6, 'WITHDRAWN', 24 * 17),
  applicant('c21', 7, 'PENDING', 9),
]

// ——— Kayıt geçmişi ———

const event = (
  courseId: string,
  s: EnrolledStudent,
  type: EnrollmentEvent['type'],
  hours: number,
  extra: Partial<EnrollmentEvent> = {},
): EnrollmentEvent => ({
  id: `${courseId}-k-${s.studentId}-${type}`,
  studentId: s.studentId,
  studentName: fullName(s),
  studentNumber: s.studentNumber,
  type,
  reason: null,
  occurredAt: ago(hours),
  afterEnrollmentPeriod: false,
  ...extra,
})

const gone = roster('c1', 3, 300, 2023)
export const history: Record<string, EnrollmentEvent[]> = {
  c1: [
    event('c1', gone[0]!, 'WITHDRAWN', 24 * 3, { reason: 'Çift anadal programımla çakışıyor.', afterEnrollmentPeriod: true }),
    event('c1', gone[1]!, 'REMOVED', 24 * 9, { reason: 'Önkoşul dersi transkriptte görünmüyor; öğrenci işleriyle görüşüldü.' }),
    event('c1', gone[2]!, 'WITHDRAWN', 24 * 16),
    ...students.c1!.slice(0, 8).map((s, i) => event('c1', s, 'ENROLLED', 24 * (18 + i))),
  ],
}

// ——— Kadro, duyuru, materyal ———

export const staff: Record<string, CourseStaff[]> = {
  c1: fx.staff.c1!.map((s) => ({ ...s })),
  c21: [
    {
      userId: 's3',
      role: 'COORDINATOR',
      name: 'Ayşe Arslan',
      title: 'Doç. Dr.',
      academicTitle: 'ASSOCIATE_PROFESSOR',
      department: 'Bilgisayar Mühendisliği',
      since: ago(24 * 60),
    },
    {
      userId: ME_ID,
      role: 'INSTRUCTOR',
      name: 'Mehmet Kaya',
      title: 'Dr. Öğr. Üyesi',
      academicTitle: 'ASSISTANT_PROFESSOR',
      department: 'Bilgisayar Mühendisliği',
      since: ago(24 * 50),
    },
  ],
}
/** Dersin kadrosu; tanımsızsa yalnız koordinatör (Mehmet Kaya). Yazma işlemleri bu diziyi değiştirir. */
export const staffOf = (id: string): CourseStaff[] => (staff[id] ??= [{ ...fx.staff.c1![0]! }])

export const announcements: Record<string, Announcement[]> = {
  c1: fx.announcements.c1!.map((a) => ({ ...a })),
  c21: [
    {
      id: 'n21',
      courseId: 'c21',
      title: 'Laboratuvar grupları',
      content: 'Laboratuvar grupları ders sayfasında. Grubunuzu değiştirmek isterseniz cuma gününe kadar yazın.',
      createdAt: ago(70),
      createdByName: 'Doç. Dr. Ayşe Arslan',
    },
  ],
}

export const materials: Record<string, Material[]> = {
  c1: [
    ...fx.materials.c1!.map((m) => ({ ...m })),
    {
      id: 'm4',
      courseId: 'c1',
      title: 'Ara sınav çalışma soruları',
      description: 'Ara sınavdan bir hafta önce açılacak.',
      section: 'Hafta 6',
      sortOrder: 1,
      kind: 'FILE',
      fileName: 'ara-sinav-calisma.pdf',
      linkUrl: null,
      visible: false,
      createdAt: ago(20),
      updatedAt: ago(20),
    },
  ],
}

// ——— Ders kataloğu ———

const catalogCourses: CatalogCourse[] = [
  { id: 'k101', code: 'BİL 101', title: 'Programlamaya Giriş', credit: 3, ects: 5 },
  { id: 'k102', code: 'BİL 102', title: 'Algoritma ve Programlama', credit: 3, ects: 5 },
  { id: 'k201', code: 'BİL 201', title: 'Ayrık Matematik', credit: 3, ects: 5 },
  { id: 'k202', code: 'BİL 202', title: 'Nesneye Yönelik Programlama', credit: 3, ects: 5 },
  { id: 'k301', code: 'BİL 301', title: 'Veri Yapıları ve Algoritmalar', credit: 4, ects: 6 },
  { id: 'k304', code: 'BİL 304', title: 'Veri Tabanı Sistemleri', credit: 3, ects: 5 },
  { id: 'k310', code: 'BİL 310', title: 'İşletim Sistemleri', credit: 3, ects: 5 },
  { id: 'k342', code: 'BİL 342', title: 'Yapay Zekâ', credit: 3, ects: 6 },
  { id: 'k405', code: 'BİL 405', title: 'Derin Öğrenme', credit: 3, ects: 6 },
  { id: 'k499', code: 'BİL 499', title: 'Bitirme Projesi I', credit: 3, ects: 8 },
]
const norm = (s: string) => s.trim().replace(/\s+/g, ' ').toLocaleUpperCase('tr-TR').replace(/İ/g, 'I')

// ——— Görünümler ———

const pendingCount = (id: string) => applications.filter((a) => a.courseId === id && a.status === 'PENDING').length

function view(c: Stored): TeachingCourse {
  const rest: Partial<Stored> = { ...c }
  delete rest.coordinatorName
  return { ...(rest as TeachingCourse), enrolledStudentCount: students[c.id]?.length ?? 0, pendingApplicationCount: pendingCount(c.id) }
}

function detail(c: Stored): Course {
  return {
    id: c.id,
    title: c.title,
    code: c.code,
    description: c.description,
    credit: c.credit,
    semester: c.termLabel,
    termId: c.termId,
    termLabel: c.termLabel,
    section: c.section,
    ects: c.ects,
    status: c.status,
    imageUrl: null,
    instructorId: c.staffRole === 'COORDINATOR' ? ME_ID : 's3',
    instructorName: c.staffRole === 'COORDINATOR' ? 'Mehmet Kaya' : 'Ayşe Arslan',
    instructorTitle: c.staffRole === 'COORDINATOR' ? 'Dr. Öğr. Üyesi' : 'Doç. Dr.',
    capacity: c.capacity,
    enrolledStudentCount: students[c.id]?.length ?? 0,
  }
}

const find = (id: string) => teaching.find((c) => c.id === id)

function fail(config: InternalAxiosRequestConfig, status: number, errorCode: string, message: string): never {
  throw Object.assign(new Error(message), {
    isAxiosError: true,
    config,
    response: { status, statusText: '', headers: {}, config, data: { status, errorCode, message } },
  })
}

const body = <T>(config: InternalAxiosRequestConfig) => JSON.parse(String(config.data)) as T
const part = async <T>(config: InternalAxiosRequestConfig, name: string) =>
  JSON.parse(await ((config.data as FormData).get(name) as Blob).text()) as T

// ——— Tablolar ———

export const GET: [RegExp, Handler][] = [
  [/^\/users\/me$/, () => ({ ...me })],
  [/^\/courses\/instructor\/me\/courses$/, () => teaching.map(view)],
  [/^\/courses\/terms$/, () => terms],
  [
    /^\/courses\/catalog$/,
    (_m, config) => {
      const q = norm(String((config.params as { q?: string } | undefined)?.q ?? ''))
      return catalogCourses.filter((c) => norm(c.code).includes(q) || norm(c.title).includes(q))
    },
  ],
  [
    /^\/courses\/([^/]+)\/applications\/pending$/,
    (m) => applications.filter((a) => a.courseId === m[1] && a.status === 'PENDING').map((a) => ({ ...a })),
  ],
  [/^\/courses\/([^/]+)\/applications$/, (m) => applications.filter((a) => a.courseId === m[1]).map((a) => ({ ...a }))],
  [/^\/courses\/([^/]+)\/enrolled-students$/, (m) => [...(students[m[1]!] ?? [])]],
  [/^\/courses\/([^/]+)\/enrollment-history$/, (m) => [...(history[m[1]!] ?? [])].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt))],
  [/^\/courses\/([^/]+)\/staff$/, (m) => staffOf(m[1]!).map((s) => ({ ...s }))],
  [/^\/courses\/([^/]+)\/materials$/, (m) => (materials[m[1]!] ?? []).map((x) => ({ ...x }))],
  [/^\/courses\/([^/]+)\/announcements$/, (m) => [...(announcements[m[1]!] ?? [])]],
  [
    /^\/courses\/([^/]+)$/,
    (m, config) => {
      const c = find(m[1]!)
      return c ? detail(c) : (fx.courseDetail(m[1]!) ?? fail(config, 404, 'COURSE_NOT_FOUND', 'Ders bulunamadı.'))
    },
  ],
]

export const WRITE: [string, RegExp, Handler][] = [
  [
    'post',
    /^\/courses$/,
    async (_m, config) => {
      const b = await part<{
        code: string
        title: string
        description: string | null
        credit: number
        ects: number | null
        capacity: number
        termId: string | null
        section: string | null
        draft: boolean
      }>(config, 'course')
      const term = terms.find((t) => t.id === b.termId) ?? fx.term
      const today = nowLocalIso().slice(0, 10)
      if (term.endsOn < today) fail(config, 409, 'TERM_ENDED', 'Bitmiş bir döneme ders açılamaz.')
      const catalog = catalogCourses.find((c) => norm(c.code) === norm(b.code))
      const code = catalog?.code ?? norm(b.code)
      const section = (b.section ?? '1').toLocaleUpperCase('tr-TR')
      if (teaching.some((c) => norm(c.code) === code && c.termId === term.id && c.section === section)) {
        fail(config, 409, 'DUPLICATE_COURSE_CODE', `${code} dersi ${term.label} döneminde ${section}. şubeyle zaten açılmış.`)
      }
      const c = stored({
        id: 'c-' + Date.now(),
        code,
        title: catalog?.title ?? b.title,
        credit: catalog?.credit ?? b.credit,
        ects: catalog ? catalog.ects : b.ects,
        description: b.description,
        capacity: b.capacity,
        termId: term.id,
        termLabel: term.label,
        section,
        staffRole: 'COORDINATOR',
        status: b.draft ? 'DRAFT' : today < term.startsOn ? 'OPEN' : 'ACTIVE',
      })
      teaching.unshift(c)
      students[c.id] = []
      return detail(c)
    },
  ],
  [
    'put',
    /^\/courses\/applications\/([^/]+)\/approve$/,
    (m, config) => {
      const a = applications.find((x) => x.id === m[1])!
      const c = find(a.courseId)!
      const list = students[c.id]!
      if (list.length >= c.capacity) fail(config, 400, 'COURSE_CAPACITY_FULL', 'Ders kapasitesi dolmuş. Onaylama yapılamaz.')
      Object.assign(a, { status: 'APPROVED', processedDate: new Date().toISOString() })
      const [first, ...last] = (a.studentName ?? '').split(' ')
      const s: EnrolledStudent = {
        studentId: a.studentId,
        firstName: first ?? '',
        lastName: last.join(' '),
        studentNumber: a.studentNumber,
        email: a.studentEmail,
        department: 'Bilgisayar Mühendisliği',
        enrollmentDate: new Date().toISOString(),
      }
      list.push(s)
      ;(history[c.id] ??= []).push(event(c.id, s, 'ENROLLED', 0))
      return { ...a }
    },
  ],
  [
    'put',
    /^\/courses\/applications\/([^/]+)\/reject$/,
    (m, config) => {
      const a = applications.find((x) => x.id === m[1])!
      Object.assign(a, {
        status: 'REJECTED',
        processedDate: new Date().toISOString(),
        rejectionReason: body<{ rejectionReason: string | null }>(config).rejectionReason,
      })
      return { ...a }
    },
  ],
  [
    'put',
    /^\/courses\/([^/]+)$/,
    (m, config) => {
      const c = find(m[1]!)!
      const b = body<{ description: string | null; capacity: number }>(config)
      if (c.status === 'COMPLETED' || c.status === 'ARCHIVED')
        fail(config, 409, 'COURSE_READ_ONLY', 'Tamamlanmış veya arşivlenmiş ders değiştirilemez.')
      if (b.capacity < (students[c.id]?.length ?? 0))
        fail(config, 409, 'CAPACITY_BELOW_ENROLLED', 'Kontenjan kayıtlı öğrenci sayısının altına indirilemez.')
      Object.assign(c, { description: b.description, capacity: b.capacity })
      return detail(c)
    },
  ],
  [
    'post',
    /^\/courses\/([^/]+)\/publish$/,
    (m) => {
      const c = find(m[1]!)!
      const term = terms.find((t) => t.id === c.termId) ?? fx.term
      c.status = nowLocalIso().slice(0, 10) < term.startsOn ? 'OPEN' : 'ACTIVE'
      return detail(c)
    },
  ],
  [
    'post',
    /^\/courses\/([^/]+)\/archive$/,
    (m) => {
      const c = find(m[1]!)!
      c.status = 'ARCHIVED'
      return detail(c)
    },
  ],
  [
    'delete',
    /^\/courses\/announcements\/([^/]+)$/,
    (m) => {
      for (const list of Object.values(announcements)) {
        const i = list.findIndex((a) => a.id === m[1])
        if (i >= 0) list.splice(i, 1)
      }
      return null
    },
  ],
  [
    'delete',
    /^\/courses\/([^/]+)$/,
    (m, config) => {
      const i = teaching.findIndex((c) => c.id === m[1])
      if (teaching[i]?.status !== 'DRAFT')
        fail(
          config,
          409,
          'COURSE_NOT_DELETABLE',
          'Yalnızca kaydı ve başvurusu olmayan taslak ders silinebilir; diğer dersler dönem sonunda arşivlenir.',
        )
      teaching.splice(i, 1)
      return null
    },
  ],
  [
    'post',
    /^\/courses\/([^/]+)\/students\/([^/]+)\/remove$/,
    (m, config) => {
      const list = students[m[1]!]!
      const i = list.findIndex((s) => s.studentId === m[2])
      const [s] = list.splice(i, 1)
      ;(history[m[1]!] ??= []).push(event(m[1]!, s!, 'REMOVED', 0, { reason: body<{ reason: string }>(config).reason }))
      return null
    },
  ],
  [
    'post',
    /^\/courses\/([^/]+)\/announcements$/,
    (m, config) => {
      const b = body<{ title: string; content: string }>(config)
      const a: Announcement = {
        id: 'n-' + Date.now(),
        courseId: m[1]!,
        title: b.title,
        content: b.content,
        createdAt: new Date().toISOString(),
        createdByName: ME_NAME,
      }
      ;(announcements[m[1]!] ??= []).unshift(a)
      return a
    },
  ],
  [
    'post',
    /^\/courses\/([^/]+)\/materials$/,
    async (m, config) => {
      const b = await part<{
        title: string
        description: string | null
        section: string | null
        kind: 'FILE' | 'LINK'
        linkUrl: string | null
        visible: boolean
      }>(config, 'material')
      if (b.kind === 'LINK' && !/^https?:\/\/\S+$/.test(b.linkUrl ?? ''))
        fail(config, 400, 'INVALID_MATERIAL_LINK', 'Bağlantı http veya https ile başlayan geçerli bir adres olmalı.')
      const file = (config.data as FormData).get('file') as File | null
      const list = (materials[m[1]!] ??= [])
      const now = new Date().toISOString()
      const x: Material = {
        id: 'm-' + Date.now(),
        courseId: m[1]!,
        title: b.title,
        description: b.description,
        section: b.section,
        sortOrder: list.filter((y) => (y.section ?? '') === (b.section ?? '')).length + 1,
        kind: b.kind,
        fileName: file?.name ?? null,
        linkUrl: b.linkUrl,
        visible: b.visible,
        createdAt: now,
        updatedAt: now,
      }
      list.push(x)
      return x
    },
  ],
  [
    'put',
    /^\/courses\/([^/]+)\/materials\/([^/]+)$/,
    (m, config) => {
      const x = materials[m[1]!]!.find((y) => y.id === m[2])!
      Object.assign(x, body<Partial<Material>>(config), { updatedAt: new Date().toISOString() })
      return { ...x }
    },
  ],
  [
    'delete',
    /^\/courses\/([^/]+)\/materials\/([^/]+)$/,
    (m) => {
      const list = materials[m[1]!]!
      list.splice(
        list.findIndex((y) => y.id === m[2]),
        1,
      )
      return null
    },
  ],
]
