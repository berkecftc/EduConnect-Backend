/**
 * Görevli ve yönetici önizlemesi (`/onizleme?rol=gorevli` ya da `?rol=yonetici`): doğrulama kuyrukları, profil talepleri,
 * hesaplar ve görevli hesapları. Görevli ve yöneticinin profili yoktur (`/users/me` 404, F-83).
 * Bellekte tutulur; yalnız geliştirme içindir.
 */
import type { InternalAxiosRequestConfig } from 'axios'
import type { AffiliationStatus, ManagedAccount, StaffAccount } from '@/features/manage/accounts'
import type { AppealItem, ModerationRecord, QueueItem, ReportItem } from '@/features/manage/moderation'
import type { AcademicianRequest, ChangeRequest, StudentRequest } from '@/features/manage/verification'
import { localStamp } from '@/lib/time'
import type { CatalogFaculty, CatalogProgram } from '@/features/auth/register/api'
import type { Term } from '@/features/courses/api'
import type { AcademicianHit } from '@/features/courses/teach'
import * as fx from './fixtures'

type Handler = (m: RegExpMatchArray, config: InternalAxiosRequestConfig) => unknown

/** Önizlemenin yönetim rolü; PreviewPage ayarlar. */
export const state: { role: 'staff' | 'admin' | null } = { role: null }

export const STAFF = { id: 'g1', email: 'dogrulama@ornek.edu.tr' }

const ago = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString()

function fail(config: InternalAxiosRequestConfig, status: number, errorCode: string, message: string): never {
  throw Object.assign(new Error(message), {
    isAxiosError: true,
    config,
    response: { status, statusText: '', headers: {}, config, data: { status, errorCode, message } },
  })
}

const body = <T>(config: InternalAxiosRequestConfig) => (config.data ? (JSON.parse(String(config.data)) as T) : ({} as T))

/** Önizleme belgesi: gerçekte MinIO'nun 15 dakikalık bağlantısı. */
const doc = (title: string, lines: string[]) =>
  `data:image/svg+xml,${encodeURIComponent(
    `<svg xmlns="http://www.w3.org/2000/svg" width="600" height="400"><rect width="600" height="400" fill="#fff"/><text x="40" y="70" font-family="sans-serif" font-size="26" font-weight="700">${title}</text>${lines
      .map((l, i) => `<text x="40" y="${130 + i * 40}" font-family="sans-serif" font-size="20">${l}</text>`)
      .join('')}</svg>`,
  )}`

// ——— Doğrulama ———

export const students: StudentRequest[] = [
  {
    id: 101,
    firstName: 'Zeynep',
    lastName: 'Aydın',
    email: 'zeynep.aydin@ogr.ornek.edu.tr',
    studentNumber: '20261004',
    department: 'Bilgisayar Mühendisliği',
    studentDocumentUrl: doc('Öğrenci belgesi', ['Zeynep Aydın', 'No: 20261004', 'Bilgisayar Mühendisliği (Lisans)']),
    emailVerified: true,
    additionalAffiliation: false,
    programId: 'p1',
  },
  {
    id: 102,
    firstName: 'Emre',
    lastName: 'Kılıç',
    email: 'emre.kilic@ogr.ornek.edu.tr',
    studentNumber: '20261011',
    department: 'Bilgisayar Mühendisliği',
    studentDocumentUrl: doc('Öğrenci belgesi', ['Emre Kılıç', 'No: 20261011']),
    emailVerified: false,
    additionalAffiliation: false,
    programId: 'p1',
  },
  {
    id: 103,
    firstName: 'Deniz',
    lastName: 'Yılmaz',
    email: 'deniz.yilmaz@ornek.edu.tr',
    studentNumber: '20262201',
    department: 'Bilgisayar Mühendisliği',
    studentDocumentUrl: doc('Öğrenci belgesi', ['Deniz Yılmaz', 'No: 20262201', 'Doktora']),
    emailVerified: true,
    additionalAffiliation: true,
    programId: 'p1',
  },
]

export const academicians: AcademicianRequest[] = [
  {
    id: 201,
    userId: 'a-201',
    firstName: 'Selin',
    lastName: 'Koç',
    title: 'Dr. Öğr. Üyesi',
    department: 'Bilgisayar Mühendisliği',
    officeNumber: 'B-318',
    idCardImageUrl: doc('Personel kimlik kartı', ['Dr. Öğr. Üyesi Selin Koç', 'Bilgisayar Mühendisliği']),
    emailVerified: true,
    additionalAffiliation: false,
  },
]

export const changes: ChangeRequest[] = [
  {
    id: 'cr-1',
    userId: 'u1',
    currentName: 'Elif Demir',
    firstName: null,
    lastName: 'Demir Yalçın',
    academicTitle: null,
    titleLabel: null,
    programId: null,
    departmentId: null,
    reason: 'Evlilik nedeniyle soyadım değişti; nüfus cüzdanımın fotokopisini öğrenci işlerine teslim ettim.',
    status: 'PENDING',
    reviewNote: null,
    reviewedAt: null,
    createdAt: ago(30),
  },
  {
    id: 'cr-2',
    userId: 's5',
    currentName: 'Selin Koç',
    firstName: null,
    lastName: null,
    academicTitle: 'ASSOCIATE_PROFESSOR',
    titleLabel: 'Doç. Dr.',
    programId: null,
    departmentId: null,
    reason: 'Doçentlik unvanım 1 Ekim itibarıyla YÖK tarafından onaylandı.',
    status: 'PENDING',
    reviewNote: null,
    reviewedAt: null,
    createdAt: ago(8),
  },
  {
    id: 'cr-3',
    userId: 'st3',
    currentName: 'Ece Yalçın',
    firstName: 'Ecem',
    lastName: null,
    academicTitle: null,
    titleLabel: null,
    programId: null,
    departmentId: null,
    reason: 'Adım sistemde eksik yazılmış.',
    status: 'REJECTED',
    reviewNote: 'Nüfus kaydındaki adınız Ece; değişiklik için resmî belge gerekli.',
    reviewedAt: ago(24 * 6),
    createdAt: ago(24 * 8),
  },
]

const byStatus = (config: InternalAxiosRequestConfig) => {
  const status = (config.params as { status?: string } | undefined)?.status ?? 'PENDING'
  return changes.filter((c) => c.status === status).map((c) => ({ ...c }))
}

const decideChange = (id: string, approve: boolean, config: InternalAxiosRequestConfig) => {
  const c = changes.find((x) => x.id === id)!
  if (c.status !== 'PENDING') fail(config, 409, 'CHANGE_REQUEST_CLOSED', 'Bu talep zaten sonuçlandırılmış.')
  const note = approve ? null : (body<{ note?: string | null }>(config).note ?? null)
  Object.assign(c, { status: approve ? 'APPROVED' : 'REJECTED', reviewNote: note, reviewedAt: new Date().toISOString() })
  return { ...c }
}

// ——— Hesaplar ———

const managed = (a: Pick<ManagedAccount, 'id' | 'email' | 'roles'> & Partial<ManagedAccount>): ManagedAccount => ({
  status: 'ACTIVE',
  studentNumber: null,
  studentStatus: null,
  staffStatus: null,
  closureDueAt: null,
  ...a,
})

export const accounts: ManagedAccount[] = [
  managed({
    id: 'u1',
    email: 'elif.demir@ogr.ornek.edu.tr',
    roles: ['ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL'],
    studentNumber: '20231045',
    studentStatus: 'ACTIVE',
  }),
  managed({
    id: 'st2',
    email: 'can.erdem@ogr.ornek.edu.tr',
    roles: ['ROLE_STUDENT'],
    studentNumber: '20221187',
    studentStatus: 'ON_LEAVE',
  }),
  managed({
    id: 'st9',
    email: 'selin.ak@ogr.ornek.edu.tr',
    roles: ['ROLE_STUDENT'],
    status: 'SUSPENDED',
    studentNumber: '20211230',
    studentStatus: 'ACTIVE',
  }),
  managed({
    id: 'st40',
    email: 'mert.yalcin@ogr.ornek.edu.tr',
    roles: ['ROLE_STUDENT'],
    studentNumber: '20191004',
    studentStatus: 'GRADUATED',
    closureDueAt: ago(-24 * 21),
  }),
  managed({ id: 's1', email: 'mehmet.kaya@ornek.edu.tr', roles: ['ROLE_ACADEMICIAN'], staffStatus: 'ACTIVE' }),
  managed({
    id: 's2',
    email: 'deniz.yilmaz@ornek.edu.tr',
    roles: ['ROLE_ACADEMICIAN', 'ROLE_STUDENT'],
    studentNumber: '20252210',
    studentStatus: 'ACTIVE',
    staffStatus: 'ACTIVE',
  }),
]

const affiliationHistory: Record<string, AffiliationStatus['history']> = {
  st2: [
    {
      affiliation: 'STUDENT',
      previousStatus: 'ACTIVE',
      status: 'ON_LEAVE',
      effectiveDate: '2026-09-01',
      reason: 'Sağlık nedeniyle bir dönem kayıt dondurma; YK 2026/31.',
      changedBy: STAFF.id,
      changedAt: ago(24 * 48),
    },
  ],
  st40: [
    {
      affiliation: 'STUDENT',
      previousStatus: 'ACTIVE',
      status: 'GRADUATED',
      effectiveDate: '2026-07-15',
      reason: null,
      changedBy: STAFF.id,
      changedAt: ago(24 * 9),
    },
  ],
}

const account = (id: string) => accounts.find((a) => a.id === id)!
const ENDED = ['GRADUATED', 'WITHDRAWN', 'EXPELLED', 'TRANSFERRED_OUT', 'RETIRED', 'RESIGNED', 'TERMINATED']

// ——— Görevli hesapları ———

export const staffAccounts: StaffAccount[] = [
  {
    id: 'g1',
    email: 'dogrulama@ornek.edu.tr',
    displayName: 'Öğrenci İşleri',
    status: 'ACTIVE',
    grants: [
      { permission: 'STUDENT_VERIFIER', facultyId: null },
      { permission: 'STAFF_VERIFIER', facultyId: null },
      { permission: 'ACCOUNT_MANAGER', facultyId: null },
    ],
  },
  {
    id: 'g2',
    email: 'muhendislik.ogrenci@ornek.edu.tr',
    displayName: 'Mühendislik Fakültesi Öğrenci İşleri',
    status: 'ACTIVE',
    grants: [{ permission: 'STUDENT_VERIFIER', facultyId: 'f1' }],
  },
  {
    id: 'g3',
    email: 'moderasyon@ornek.edu.tr',
    displayName: 'Topluluk Moderasyonu',
    status: 'ACTIVE',
    grants: [{ permission: 'MODERATOR', facultyId: null }],
  },
  {
    id: 'g4',
    email: 'sks@ornek.edu.tr',
    displayName: 'Sağlık, Kültür ve Spor',
    status: 'SUSPENDED',
    grants: [{ permission: 'CAMPUS_PUBLISHER', facultyId: null }],
  },
]

type GrantBody = { grants: { permission: StaffAccount['grants'][number]['permission']; facultyIds: string[] }[] }
const flatten = (b: GrantBody): StaffAccount['grants'] =>
  b.grants.flatMap((g): StaffAccount['grants'] =>
    g.facultyIds.length
      ? g.facultyIds.map((facultyId) => ({ permission: g.permission, facultyId }))
      : [{ permission: g.permission, facultyId: null }],
  )

// ——— Moderasyon (post-service VIA_DTO sayfası) ———

const queue: QueueItem[] = [
  {
    targetType: 'POST',
    id: 'p20',
    postId: 'p20',
    title: 'Hoca ödev puanlarını adaletsiz veriyor',
    content:
      'BİL 301’de bu hafta verilen puanlar çok tutarsız. Aynı kodu yazan iki kişiden biri 90 diğeri 40 aldı. Bu hocanın derslerini almayın, tam bir rezalet.',
    authorId: 'st31',
    authorName: 'Tolga Yıldız',
    status: 'IN_REVIEW',
    flag: 'Otomatik kontrol zorbalık veya hakaret şüphesi bildirdi.',
    submittedAt: ago(3),
  },
  {
    targetType: 'POST',
    id: 'p21',
    postId: 'p21',
    title: 'Satılık ikinci el çizim tableti',
    content: 'Az kullanılmış çizim tableti, kutusuyla. Fiyat için mesaj atın.',
    authorId: 'st32',
    authorName: 'Ilgın Güneş',
    status: 'IN_REVIEW',
    flag: 'Otomatik kontrol karar veremedi.',
    submittedAt: ago(9),
  },
  {
    targetType: 'COMMENT',
    id: 'cm30',
    postId: 'p1',
    title: null,
    content: 'Bu soruyu soran herkes dersten kalsın zaten, kitapta yazıyor.',
    authorId: 'st33',
    authorName: 'Oğuz Kurt',
    status: 'IN_REVIEW',
    flag: 'Otomatik kontrol zorbalık veya hakaret şüphesi bildirdi.',
    submittedAt: ago(5),
  },
]

const reports: ReportItem[] = [
  {
    id: 'r1',
    targetType: 'COMMENT',
    targetId: 'cm31',
    postId: 'p4',
    reason: 'HARASSMENT',
    reasonLabel: 'Taciz',
    sensitive: true,
    details: 'Bana özelden de yazıyor; burada da adımı verip alay ediyor.',
    reporterId: 'st7',
    status: 'OPEN',
    openReportsOnTarget: 1,
    targetStatus: 'PUBLISHED',
    targetContent: 'Burcu yine kütüphanede yer kapmış, herkes biliyor ne yaptığını. Kendisine bir bakın.',
    createdAt: ago(2),
  },
  {
    id: 'r2',
    targetType: 'POST',
    targetId: 'p22',
    postId: 'p22',
    reason: 'ACADEMIC_INTEGRITY',
    reasonLabel: 'Akademik dürüstlük',
    sensitive: false,
    details: 'Ara sınav sorularının çözümleri paylaşılmış.',
    reporterId: 'st3',
    status: 'OPEN',
    openReportsOnTarget: 2,
    targetStatus: 'PUBLISHED',
    targetContent: 'Geçen haftaki ara sınavın bütün çözümleri ekte; bu dönem de aynı sorular gelir.',
    createdAt: ago(20),
  },
  {
    id: 'r3',
    targetType: 'POST',
    targetId: 'p3',
    postId: 'p3',
    reason: 'SPAM',
    reasonLabel: 'İstenmeyen içerik',
    sensitive: false,
    details: null,
    reporterId: 'st9',
    status: 'OPEN',
    openReportsOnTarget: 1,
    targetStatus: 'PUBLISHED',
    targetContent: 'Bahar dönemindeki robot yarışması için takım seçmeleri gelecek hafta laboratuvarda.',
    createdAt: ago(30),
  },
]

const appealQueue: AppealItem[] = [
  {
    id: 'ap-m1',
    targetType: 'POST',
    targetId: 'p6',
    postId: 'p6',
    appellantId: 'u1',
    appellantName: 'Elif Demir',
    statement: 'Paylaştığım sorular hocanın dönem başında herkese dağıttığı örnek sorulardı, gerçek sınav soruları değil.',
    appealedAction: 'HIDDEN',
    status: 'OPEN',
    decisionNote: null,
    decidedAt: null,
    createdAt: ago(26),
    targetContent: 'Geçen yılın ara sınav sorularını paylaşıyorum.',
  },
]

const history: Record<string, ModerationRecord[]> = {
  p6: [
    {
      id: 'h1',
      targetType: 'POST',
      targetId: 'p6',
      postId: 'p6',
      action: 'HIDDEN',
      actorType: 'SCOPE_OWNER',
      actorId: 's1',
      reason: 'Sınav soruları paylaşılamaz (akademik dürüstlük).',
      createdAt: ago(24 * 9),
    },
  ],
  p20: [
    {
      id: 'h2',
      targetType: 'POST',
      targetId: 'p20',
      postId: 'p20',
      action: 'SENT_TO_REVIEW',
      actorType: 'AUTOMATIC',
      actorId: null,
      reason: 'Zorbalık veya hakaret şüphesi.',
      createdAt: ago(3),
    },
  ],
}

/** VIA_DTO sayfası: `{content, page:{…}}`. */
function paged<T>(all: T[], config: InternalAxiosRequestConfig) {
  const { page = 0, size = 20 } = (config.params ?? {}) as { page?: number; size?: number }
  return {
    content: all.slice(page * size, page * size + size),
    page: { size, number: page, totalElements: all.length, totalPages: Math.ceil(all.length / size) },
  }
}

const record = (targetType: 'POST' | 'COMMENT', targetId: string, action: string, reason: string | null) =>
  (history[targetId] ??= []).unshift({
    id: 'h-' + Date.now(),
    targetType,
    targetId,
    postId: targetId,
    action,
    actorType: 'MODERATOR',
    actorId: STAFF.id,
    reason,
    createdAt: new Date().toISOString(),
  })

// ——— Kampüs etkinlikleri ———

/** Bitiş verilmeden ertelenen etkinlik süresini korur (backend: yeni başlangıç + eski süre). */
export function keepDuration(startsAt: string, endsAt: string | null | undefined, newStart: string) {
  if (!endsAt) return null
  const ms = localStamp(endsAt) - localStamp(startsAt)
  return new Date(localStamp(newStart) + ms).toISOString().slice(0, 19)
}

const campusList = () => fx.campusEvents.filter((e) => !e.clubId)

// ——— Yönetici: akademik yapı, dönem, koordinatör devri ———

type CatalogTree = CatalogFaculty[]

/** Kapalı birimler dahil ağaç (yönetici görünümü); bir kapalı bölüm ve ikinci fakülte örnek için eklidir. */
const catalogAll: CatalogTree = [
  ...(JSON.parse(JSON.stringify(fx.academicCatalog)) as CatalogTree),
  {
    id: 'f2',
    code: 'FEN',
    name: 'Fen Fakültesi',
    active: true,
    departments: [
      {
        id: 'd3',
        code: 'MAT',
        name: 'Matematik',
        active: true,
        programs: [{ id: 'p4', code: 'MAT-L', name: 'Matematik', level: 'BACHELOR', durationYears: 4, active: true }],
      },
      {
        id: 'd4',
        code: 'IST',
        name: 'İstatistik',
        active: false,
        programs: [{ id: 'p5', code: 'IST-L', name: 'İstatistik', level: 'BACHELOR', durationYears: 4, active: false }],
      },
    ],
  },
]

const allUnits = () => catalogAll.flatMap((f) => [f, ...f.departments.flatMap((d) => [d, ...d.programs])])

type UnitBody = {
  parentId: string | null
  code: string
  name: string
  active: boolean
  level?: CatalogProgram['level']
  durationYears?: number
}

function saveUnit(kind: 'faculties' | 'departments' | 'programs', id: string | undefined, b: UnitBody, config: InternalAxiosRequestConfig) {
  if (allUnits().some((u) => u.code.toUpperCase() === b.code.toUpperCase() && u.id !== id))
    fail(config, 409, 'CODE_EXISTS', 'Bu kodla bir birim var.')
  if (id) {
    const u = allUnits().find((x) => x.id === id)!
    Object.assign(
      u,
      { code: b.code, name: b.name, active: b.active },
      kind === 'programs' ? { level: b.level, durationYears: b.durationYears } : {},
    )
  } else if (kind === 'faculties') {
    catalogAll.push({ id: 'f-' + Date.now(), code: b.code, name: b.name, active: true, departments: [] })
  } else if (kind === 'departments') {
    catalogAll
      .find((f) => f.id === b.parentId)!
      .departments.push({ id: 'd-' + Date.now(), code: b.code, name: b.name, active: true, programs: [] })
  } else {
    catalogAll
      .flatMap((f) => f.departments)
      .find((d) => d.id === b.parentId)!
      .programs.push({ id: 'p-' + Date.now(), code: b.code, name: b.name, active: true, level: b.level!, durationYears: b.durationYears! })
  }
  return JSON.parse(JSON.stringify(catalogAll)) as CatalogTree
}

const SEASON = { FALL: 'Güz', SPRING: 'Bahar', SUMMER: 'Yaz' } as const
type TermBody = Omit<Term, 'id' | 'label'>

function saveTerm(id: string | undefined, b: TermBody, config: InternalAxiosRequestConfig) {
  if (fx.terms.some((t) => t.academicYear === b.academicYear && t.season === b.season && t.id !== id))
    fail(config, 409, 'TERM_EXISTS', 'Bu akademik yıl ve dönem zaten tanımlı.')
  if (b.startsOn >= b.endsOn) fail(config, 400, 'INVALID_TERM_DATES', 'Dönem başlangıcı bitişten önce olmalı.')
  const label = `${b.academicYear - 1}-${b.academicYear} ${SEASON[b.season]}`
  if (id) {
    const t = fx.terms.find((x) => x.id === id)!
    if (t.id === 't1' && (t.academicYear !== b.academicYear || t.season !== b.season))
      fail(config, 409, 'TERM_IN_USE', 'Ders açılmış dönemin yılı ve türü değiştirilemez.')
    Object.assign(t, b, { label })
    return { ...t }
  }
  const t: Term = { id: 't-' + Date.now(), label, ...b }
  fx.terms.push(t)
  return t
}

const hits: AcademicianHit[] = [
  {
    id: 's1',
    firstName: 'Mehmet',
    lastName: 'Kaya',
    title: 'Dr. Öğr. Üyesi',
    academicTitle: 'ASSISTANT_PROFESSOR',
    department: 'Bilgisayar Mühendisliği',
    departmentId: 'd1',
  },
  {
    id: 's3',
    firstName: 'Ayşe',
    lastName: 'Arslan',
    title: 'Doç. Dr.',
    academicTitle: 'ASSOCIATE_PROFESSOR',
    department: 'Bilgisayar Mühendisliği',
    departmentId: 'd1',
  },
  {
    id: 's5',
    firstName: 'Selin',
    lastName: 'Koç',
    title: 'Dr. Öğr. Üyesi',
    academicTitle: 'ASSISTANT_PROFESSOR',
    department: 'Bilgisayar Mühendisliği',
    departmentId: 'd1',
  },
  {
    id: 's6',
    firstName: 'Kemal',
    lastName: 'Aydın',
    title: 'Prof. Dr.',
    academicTitle: 'PROFESSOR',
    department: 'Matematik',
    departmentId: 'd3',
  },
]

// ——— Tablolar ———

export const GET: [RegExp, Handler][] = [
  [/^\/users\/me$/, (_m, config) => fail(config, 404, 'PROFILE_NOT_FOUND', 'Profil bulunamadı.')],
  [/^\/auth\/verification\/students$/, () => students.map((s, i) => ({ ...s, createdAt: ago(24 * (students.length - i) + 3) }))],
  [/^\/auth\/verification\/academicians$/, () => academicians.map((a, i) => ({ ...a, createdAt: ago(24 * (academicians.length - i) + 5) }))],
  [/^\/users\/(?:verification|admin)\/change-requests$/, (_m, config) => byStatus(config)],
  [
    /^\/auth\/account-management\/users$/,
    (_m, config) => {
      const q = String((config.params as { query?: string } | undefined)?.query ?? '')
        .trim()
        .toLocaleLowerCase('tr-TR')
      if (q.length < 3) return []
      return accounts.filter((a) => a.studentNumber === q || a.email.toLocaleLowerCase('tr-TR').includes(q)).map((a) => ({ ...a }))
    },
  ],
  [
    /^\/auth\/account-management\/users\/([^/]+)\/affiliations$/,
    (m) => {
      const a = account(m[1]!)
      return {
        userId: a.id,
        studentStatus: a.studentStatus,
        staffStatus: a.staffStatus,
        closureDueAt: a.closureDueAt,
        history: [...(affiliationHistory[a.id] ?? [])],
      }
    },
  ],
  [/^\/auth\/admin\/staff-accounts$/, () => staffAccounts.map((s) => ({ ...s, grants: [...s.grants] }))],
  [
    /^\/posts\/moderation\/queue$/,
    (_m, config) =>
      paged(
        queue.filter((q) => q.targetType === ((config.params as { type?: string } | undefined)?.type ?? 'POST')),
        config,
      ),
  ],
  [
    /^\/posts\/moderation\/reports$/,
    (_m, config) =>
      paged(
        [...reports].sort((a, b) => Number(b.sensitive) - Number(a.sensitive)),
        config,
      ),
  ],
  [/^\/posts\/moderation\/appeals$/, (_m, config) => paged(appealQueue, config)],
  [/^\/posts\/moderation\/history$/, (_m, config) => [...(history[(config.params as { targetId: string }).targetId] ?? [])]],
  [/^\/events\/campus$/, () => campusList().map((e) => ({ ...e }))],
  [/^\/users\/academic\/catalog\/all$/, () => JSON.parse(JSON.stringify(catalogAll))],
  [
    /^\/users\/search\/academicians$/,
    (_m, config) => {
      const q = String((config.params as { query?: string } | undefined)?.query ?? '').toLocaleLowerCase('tr-TR')
      return hits.filter((h) => `${h.firstName} ${h.lastName}`.toLocaleLowerCase('tr-TR').includes(q))
    },
  ],
]

export const WRITE: [string, RegExp, Handler][] = [
  [
    'post',
    /^\/users\/academic\/(faculties|departments|programs)$/,
    (m, config) => saveUnit(m[1] as 'faculties', undefined, body<UnitBody>(config), config),
  ],
  [
    'put',
    /^\/users\/academic\/(faculties|departments|programs)\/([^/]+)$/,
    (m, config) => saveUnit(m[1] as 'faculties', m[2], body<UnitBody>(config), config),
  ],
  ['post', /^\/courses\/terms$/, (_m, config) => saveTerm(undefined, body<TermBody>(config), config)],
  ['put', /^\/courses\/terms\/([^/]+)$/, (m, config) => saveTerm(m[1], body<TermBody>(config), config)],
  [
    'delete',
    /^\/courses\/terms\/([^/]+)$/,
    (m, config) => {
      if (m[1] === 't1' || m[1] === 't0') fail(config, 409, 'TERM_IN_USE', 'Ders açılmış dönem silinemez.')
      fx.terms.splice(
        fx.terms.findIndex((t) => t.id === m[1]),
        1,
      )
      return null
    },
  ],
  [
    'put',
    /^\/courses\/([^/]+)\/coordinator$/,
    (m, config) => {
      const b = body<{ userId: string; previousCoordinatorRole: 'INSTRUCTOR' | 'ASSISTANT' | null }>(config)
      const list = (fx.staff[m[1]!] ??= [])
      const old = list.find((s) => s.role === 'COORDINATOR')
      if (old?.userId === b.userId) fail(config, 409, 'ALREADY_COORDINATOR', 'Bu kullanıcı zaten dersin koordinatörü.')
      const h = hits.find((x) => x.id === b.userId)!
      if (old) {
        if (b.previousCoordinatorRole) old.role = b.previousCoordinatorRole
        else list.splice(list.indexOf(old), 1)
      }
      const existing = list.find((s) => s.userId === b.userId)
      if (existing) existing.role = 'COORDINATOR'
      else
        list.unshift({
          userId: h.id,
          role: 'COORDINATOR',
          name: `${h.firstName} ${h.lastName}`,
          title: h.title,
          academicTitle: h.academicTitle,
          department: h.department,
          since: new Date().toISOString(),
        })
      for (const c of [...fx.courses, ...fx.catalog(undefined)].filter((x) => x.id === m[1]))
        Object.assign(c, {
          instructorName: `${h.firstName} ${h.lastName}`,
          instructorTitle: h.title,
          instructorAcademicTitle: h.academicTitle,
        })
      return [...list]
    },
  ],
  [
    'post',
    /^\/posts\/moderation\/(posts|comments)\/([^/]+)\/(decision|remove|restore)$/,
    (m, config) => {
      const type = m[1] === 'posts' ? 'POST' : 'COMMENT'
      const b = body<{ action?: 'APPROVE' | 'REJECT'; reason: string | null }>(config)
      if (m[3] === 'decision') {
        if (b.action === 'REJECT' && !b.reason) fail(config, 400, 'REASON_REQUIRED', 'Reddetme gerekçesi zorunludur.')
        queue.splice(
          queue.findIndex((q) => q.id === m[2]),
          1,
        )
        record(type, m[2]!, b.action === 'APPROVE' ? 'PUBLISHED' : 'REJECTED', b.reason)
      } else {
        for (let i = reports.length - 1; i >= 0; i--) if (reports[i]!.targetId === m[2]) reports.splice(i, 1)
        record(type, m[2]!, m[3] === 'remove' ? 'REMOVED' : 'RESTORED', b.reason)
      }
      return null
    },
  ],
  [
    'post',
    /^\/posts\/moderation\/reports\/([^/]+)\/dismiss$/,
    (m, config) => {
      const i = reports.findIndex((r) => r.id === m[1])
      record(reports[i]!.targetType, reports[i]!.targetId, 'REPORT_DISMISSED', body<{ reason: string }>(config).reason)
      reports.splice(i, 1)
      return null
    },
  ],
  [
    'post',
    /^\/posts\/moderation\/appeals\/([^/]+)\/decision$/,
    (m, config) => {
      const i = appealQueue.findIndex((a) => a.id === m[1])
      const a = appealQueue[i]!
      const b = body<{ outcome: 'ACCEPT' | 'REJECT'; reason: string }>(config)
      record(a.targetType, a.targetId, b.outcome === 'ACCEPT' ? 'APPEAL_ACCEPTED' : 'APPEAL_REJECTED', b.reason)
      appealQueue.splice(i, 1)
      return { ...a, status: b.outcome === 'ACCEPT' ? 'ACCEPTED' : 'REJECTED', decisionNote: b.reason, decidedAt: new Date().toISOString() }
    },
  ],
  [
    'post',
    /^\/events\/campus$/,
    async (_m, config) => {
      const d = JSON.parse(await ((config.data as FormData).get('data') as Blob).text()) as Record<string, unknown>
      const e = {
        ...fx.campusEvents.find((x) => !x.clubId)!,
        ...d,
        id: 'e-' + Date.now(),
        audience: 'CAMPUS' as const,
        clubId: null,
        clubName: null,
        imageUrl: null,
        status: 'ACTIVE' as const,
        endsAt: (d.endsAt as string | null) ?? null,
      }
      fx.campusEvents.push(e as (typeof fx.campusEvents)[number])
      return e
    },
  ],
  [
    'post',
    /^\/events\/manage\/([^/]+)\/(postpone|relocate|cancel)$/,
    (m, config) => {
      // Kulüp etkinliği ertelenince onaya döner; kampüs etkinliği yayında kalır.
      const e = fx.findEvent(m[1]!)!
      const b = body<{ startsAt: string | null; endsAt: string | null; location: string | null; reason: string }>(config)
      const kind = m[2] === 'postpone' ? 'POSTPONED' : m[2] === 'relocate' ? 'RELOCATED' : 'CANCELLED'
      const details =
        m[2] === 'postpone'
          ? `${e.startsAt.slice(0, 16)} → ${b.startsAt?.slice(0, 16)}`
          : m[2] === 'relocate'
            ? `${e.location ?? '–'} → ${b.location}`
            : null
      if (m[2] === 'postpone')
        Object.assign(e, {
          startsAt: b.startsAt,
          endsAt: b.endsAt ?? keepDuration(e.startsAt, e.endsAt, b.startsAt!),
          status: e.clubId ? 'PENDING' : 'ACTIVE',
        })
      if (m[2] === 'relocate') e.location = b.location
      if (m[2] === 'cancel') Object.assign(e, { status: 'CANCELLED', cancellationReason: b.reason })
      ;(fx.eventChanges[e.id] ??= []).unshift({
        id: 'ch-' + Date.now(),
        kind,
        details,
        reason: b.reason,
        createdAt: new Date().toISOString(),
      })
      return e
    },
  ],
  [
    'post',
    /^\/auth\/verification\/students\/([^/]+)\/(approve|reject)$/,
    (m, config) => {
      const i = students.findIndex((s) => String(s.id) === m[1])
      if (m[2] === 'approve' && !students[i]!.emailVerified)
        fail(config, 409, 'CONFLICT', 'Başvuru sahibi e-posta adresini henüz doğrulamadı.')
      students.splice(i, 1)
      return m[2] === 'approve' ? 'Öğrenci onaylandı.' : 'Öğrenci başvurusu reddedildi.'
    },
  ],
  [
    'post',
    /^\/auth\/verification\/academicians\/([^/]+)\/(approve|reject)$/,
    (m) => {
      academicians.splice(
        academicians.findIndex((a) => a.userId === m[1]),
        1,
      )
      return m[2] === 'approve' ? 'Akademisyen onaylandı.' : 'Akademisyen başvurusu reddedildi.'
    },
  ],
  ['post', /^\/users\/(?:verification|admin)\/change-requests\/([^/]+)\/approve$/, (m, config) => decideChange(m[1]!, true, config)],
  ['post', /^\/users\/(?:verification|admin)\/change-requests\/([^/]+)\/reject$/, (m, config) => decideChange(m[1]!, false, config)],
  [
    'put',
    /^\/auth\/account-management\/users\/([^/]+)\/(student|staff)-status$/,
    (m, config) => {
      const a = account(m[1]!)
      const b = body<{ status: string; effectiveDate: string | null; reason: string | null }>(config)
      const key = m[2] === 'student' ? 'studentStatus' : 'staffStatus'
      if (a[key] === b.status) fail(config, 409, 'STATUS_UNCHANGED', 'Hesap zaten bu durumda.')
      ;(affiliationHistory[a.id] ??= []).push({
        affiliation: m[2] === 'student' ? 'STUDENT' : 'STAFF',
        previousStatus: a[key],
        status: b.status,
        effectiveDate: b.effectiveDate ?? new Date().toISOString().slice(0, 10),
        reason: b.reason,
        changedBy: STAFF.id,
        changedAt: new Date().toISOString(),
      })
      Object.assign(a, { [key]: b.status })
      const stillActive = [a.studentStatus, a.staffStatus].some((s) => s && !ENDED.includes(s))
      a.closureDueAt = stillActive ? null : ago(-24 * 30)
      return null
    },
  ],
  [
    'put',
    /^\/auth\/account-management\/users\/([^/]+)\/(suspend|reactivate)$/,
    (m) => {
      account(m[1]!).status = m[2] === 'suspend' ? 'SUSPENDED' : 'ACTIVE'
      return m[2] === 'suspend' ? 'Hesap askıya alındı.' : 'Hesap yeniden etkin.'
    },
  ],
  [
    'put',
    /^\/auth\/account-management\/users\/([^/]+)\/email$/,
    (m, config) => {
      const a = account(m[1]!)
      const { newEmail } = body<{ newEmail: string }>(config)
      if (newEmail === a.email) fail(config, 409, 'EMAIL_UNCHANGED', 'Yeni adres mevcut adresle aynı.')
      if (accounts.some((x) => x.email === newEmail)) fail(config, 409, 'EMAIL_TAKEN', 'Bu e-posta adresi kullanılıyor.')
      if (!/@(ogr\.)?ornek\.edu\.tr$/.test(newEmail))
        fail(config, 400, 'EMAIL_DOMAIN_NOT_ALLOWED', 'E-posta adresi kurum alan adlarından biri olmalı: ornek.edu.tr, ogr.ornek.edu.tr')
      a.email = newEmail
      return { ...a }
    },
  ],
  [
    'post',
    /^\/auth\/admin\/staff-accounts$/,
    (_m, config) => {
      const b = body<GrantBody & { email: string; displayName: string }>(config)
      if (staffAccounts.some((s) => s.email === b.email)) fail(config, 409, 'EMAIL_TAKEN', 'Bu e-posta adresi kullanılıyor.')
      const s: StaffAccount = { id: 'g-' + Date.now(), email: b.email, displayName: b.displayName, status: 'ACTIVE', grants: flatten(b) }
      staffAccounts.push(s)
      return s
    },
  ],
  [
    'put',
    /^\/auth\/admin\/staff-accounts\/([^/]+)\/permissions$/,
    (m, config) => {
      const s = staffAccounts.find((x) => x.id === m[1])!
      s.grants = flatten(body<GrantBody>(config))
      return { ...s }
    },
  ],
  [
    'put',
    /^\/auth\/admin\/users\/([^/]+)\/(suspend|reactivate)$/,
    (m) => {
      const s = staffAccounts.find((x) => x.id === m[1])
      if (s) s.status = m[2] === 'suspend' ? 'SUSPENDED' : 'ACTIVE'
      return 'Tamam'
    },
  ],
]
