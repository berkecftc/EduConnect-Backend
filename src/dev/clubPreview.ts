/**
 * Öğrenci önizlemesi, kulüp yönetimi: Elif Demir Satranç Kulübü'nün başkanı (görev verme, üyelikten çıkarma,
 * kulüp bilgileri, logo, danışman önerme, kapatma talebi), Robotik'te etkinlik koordinatörü (istifa).
 * Bir kurucu daveti ve reddedilmiş eski bir kuruluş başvurusu var. Satranç'ta seçim açılabilir (açılınca birkaç üye aday
 * olmuş sayılır, oylama bitince diğer üyelerin oyları eklenir); Robotik'te oylama sürüyor ve Elif henüz oy vermedi.
 * Satranç'ın kayıtları: üç tutanak, bütçeler, gelir-gider (biri danışman onayında), bir sponsorluk, geçen yılın
 * raporları, karar günlüğü ve görev geçmişi. Robotik'in Arduino atölyesinde bir görevli ve bir öneri var; Elif de görevli.
 * Kapıda denenecek bilet kodları: EC-E1-BURCU (geçerli, bir kez), EC-E1-KAAN (iptal edilmiş kayıt).
 * Kişi profilleri: akademisyenlerin e-postası herkese açık; öğrencilerinki ve numarası yalnız Elif'in yönettiği
 * Satranç Kulübü üyelerinde görünür (sunucudaki ilişki kuralı gibi). Üye ve önerilen danışman adları, backend'in
 * bu alanları dolduracağı varsayımıyla gelir. Bellekte tutulur; yalnız `/onizleme` (öğrenci) içindir.
 */
import type { InternalAxiosRequestConfig } from 'axios'
import type { AcademicianHit } from '@/features/courses/teach'
import type { ClubCreationRequest } from '@/features/clubs/advise'
import type { ClubCategory, ClubMember } from '@/features/clubs/api'
import type { AdvisorChange, ProfileDraft } from '@/features/clubs/governance'
import type { Election, ElectionBallot, ElectionCandidate } from '@/features/clubs/elections'
import type { ApprovalRequest } from '@/features/clubs/manage'
import type { EventStaff } from '@/features/events/staff'
import type {
  Budget,
  DecisionLogEntry,
  FinanceEntry,
  Meeting,
  PositionTerm,
  Report,
  ReportSnapshot,
  Sponsorship,
} from '@/features/clubs/records'
import type { PersonProfile } from '@/features/people/api'
import * as fx from './fixtures'
import * as px from './postFixtures'

type Handler = (m: RegExpMatchArray, config: InternalAxiosRequestConfig) => unknown

const ago = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString()
const now = () => new Date().toISOString()
const body = <T>(config: InternalAxiosRequestConfig) => JSON.parse(String(config.data ?? '{}')) as T
let seq = 0
const nextId = (p: string) => `${p}-${++seq}`

export function fail(config: InternalAxiosRequestConfig, status: number, errorCode: string, message: string): never {
  throw Object.assign(new Error(message), {
    isAxiosError: true,
    config,
    response: { status, statusText: '', headers: {}, config, data: { status, errorCode, message } },
  })
}

// ——— Üyeler ———

const member = (studentId: string, firstName: string, lastName: string, role = 'MEMBER'): ClubMember => ({
  studentId,
  firstName,
  lastName,
  role,
})

/** Satranç Kulübü'nün etkin üyeleri (kurul dahil), adlarıyla. */
export const members: Record<string, ClubMember[]> = {
  k2: [
    member('u1', 'Elif', 'Demir', 'ROLE_CLUB_OFFICIAL'),
    member('st5', 'Deniz', 'Kara', 'ROLE_VICE_PRESIDENT'),
    member('st6', 'Oğuz', 'Tan', 'ROLE_MEMBERSHIP_OFFICER'),
    member('st9', 'Selin', 'Ak'),
    member('st10', 'Arda', 'Kılıç'),
    member('st11', 'Zeynep', 'Uçar'),
    member('st12', 'Emre', 'Doğan'),
    member('st13', 'Ceren', 'Aydın'),
    member('st14', 'Baran', 'Şimşek'),
    member('st15', 'İrem', 'Kurt'),
    member('st16', 'Tolga', 'Er'),
    member('st17', 'Nazlı', 'Sezer'),
  ],
}

/** Öğrenci numarası → öğrenci (görev verirken numarayla arama). Burcu Öz üye değil. */
const NUMBERS: Record<string, { id: string; name: string }> = {
  '210101042': { id: 'st9', name: 'Selin Ak' },
  '200101007': { id: 'st10', name: 'Arda Kılıç' },
  '220101015': { id: 'st7', name: 'Burcu Öz' },
}

const LIMIT: Record<string, number> = { VICE_PRESIDENT: 1, GENERAL_SECRETARY: 1, TREASURER: 1, BOARD_MEMBER: 7, AUDITOR: 3 }
const canonical = (r: string | null) =>
  !r ? 'MEMBER' : r === 'ROLE_CLUB_OFFICIAL' ? 'PRESIDENT' : r === 'ROLE_SECRETARY' ? 'GENERAL_SECRETARY' : r.replace(/^ROLE_/, '')

// ——— Danışman adayları ———

const academician = (id: string, firstName: string, lastName: string, title: string, department: string): AcademicianHit => ({
  id,
  firstName,
  lastName,
  title,
  academicTitle: null,
  department,
  departmentId: null,
})
const academicians: AcademicianHit[] = [
  academician('u-c1', 'Mehmet', 'Kaya', 'Dr. Öğr. Üyesi', 'Bilgisayar Mühendisliği'),
  academician('u-c2', 'Ayşe', 'Arslan', 'Doç. Dr.', 'Bilgisayar Mühendisliği'),
  academician('u-c3', 'Hakan', 'Öztürk', 'Prof. Dr.', 'Matematik'),
  academician('u-c7', 'Selin', 'Koç', 'Dr. Öğr. Üyesi', 'Sosyoloji'),
  academician('u-c9', 'Zeynep', 'Çelik', 'Öğr. Gör.', 'Güzel Sanatlar'),
  academician('u-c10', 'Kemal', 'Aydın', 'Prof. Dr.', 'İşletme'),
  academician('u-c12', 'Burak', 'Şahin', 'Doç. Dr.', 'Bilgisayar Mühendisliği'),
]
const searchKey = (s: string) => s.trim().replace(/\s+/g, ' ').toLocaleUpperCase('tr-TR').replace(/İ/g, 'I')
const fullName = (a: AcademicianHit) => [a.title, a.firstName, a.lastName].filter(Boolean).join(' ')

export const advisorChanges: Record<string, AdvisorChange[]> = {
  k2: [
    {
      id: 'ac1',
      clubId: 'k2',
      clubName: 'Satranç Kulübü',
      proposedAdvisorId: 'u-c10',
      proposedAdvisorName: 'Prof. Dr. Kemal Aydın',
      requestedBy: 'u1',
      message: 'Lig maçlarına eşlik edebilecek bir danışman arıyoruz.',
      status: 'REJECTED',
      rejectionReason: 'Bu dönem iki kulübe danışmanlık yapıyorum; yeni bir kulüp alamıyorum.',
      createdAt: ago(24 * 70),
      decidedAt: ago(24 * 66),
    },
  ],
}

// ——— Kuruluş ———

const founder = (
  studentId: string,
  firstName: string,
  lastName: string,
  status: 'INVITED' | 'CONFIRMED' | 'DECLINED',
  respondedAt: string | null,
) => ({
  studentId,
  firstName,
  lastName,
  status,
  respondedAt,
})

/** `educonnect.club.founding.min-members` (F-89). */
const MIN_FOUNDERS = 7

export const invitations: ClubCreationRequest[] = [
  {
    id: 'cr-inv1',
    clubName: 'Bilim Kurgu Okuma Kulübü',
    about: 'Ayda bir kitap seçiyor, son çarşamba akşamı kütüphanede tartışıyoruz. Dönem sonunda öykü yarışması düzenlemek istiyoruz.',
    requestingStudentId: 'st20',
    suggestedAdvisorId: 'u-c7',
    status: 'PENDING_FOUNDERS',
    requestDate: ago(26),
    rejectionReason: null,
    processedAt: null,
    clubId: null,
    founders: [
      founder('st20', 'Kerem', 'Aslan', 'CONFIRMED', ago(26)),
      founder('st21', 'Pınar', 'Ünal', 'CONFIRMED', ago(20)),
      founder('u1', 'Elif', 'Demir', 'INVITED', null),
      founder('st22', 'Mert', 'Yazıcı', 'INVITED', null),
    ],
  },
]

export const creations: ClubCreationRequest[] = [
  {
    id: 'cr-old',
    clubName: 'Go Kulübü',
    about: 'Go oyununu tanıtmak ve haftalık oyun akşamları düzenlemek.',
    requestingStudentId: 'u1',
    suggestedAdvisorId: 'u-c3',
    status: 'REJECTED',
    requestDate: ago(24 * 140),
    rejectionReason: 'Satranç Kulübü içinde bir Go çalışma grubu açmanızı öneriyorum; ayrı kulüp için üye sayısı yetersiz.',
    processedAt: ago(24 * 133),
    clubId: null,
    founders: [founder('u1', 'Elif', 'Demir', 'CONFIRMED', ago(24 * 140))],
  },
]

// ——— Seçimler ———

const cand = (
  id: string,
  ballot: ElectionBallot,
  studentId: string,
  firstName: string,
  lastName: string,
  votes: number | null = null,
  elected = false,
): ElectionCandidate => ({ id, ballot, studentId, firstName, lastName, votes, elected })

export const elections: Record<string, Election[]> = {
  k1: [
    {
      id: 'el-k1',
      clubId: 'k1',
      status: 'VOTING',
      boardSeats: 3,
      auditSeats: 0,
      note: 'Genel kurul 24 Ekim cuma 18.00, B Blok 204. Oylama genel kurulun sonuna kadar açık.',
      openedBy: 'st2',
      openedAt: ago(24 * 9),
      votingStartedAt: ago(30),
      closedAt: null,
      eligibleVoters: null,
      voters: null,
      requestId: null,
      completedAt: null,
      myBallots: [],
      candidates: [
        cand('c-k1-1', 'PRESIDENT', 'st2', 'Can', 'Erdem'),
        cand('c-k1-2', 'PRESIDENT', 'st4', 'Mert', 'Aksu'),
        cand('c-k1-3', 'BOARD', 'st3', 'Ece', 'Yalçın'),
        cand('c-k1-4', 'BOARD', 'st30', 'Bora', 'Tunç'),
        cand('c-k1-5', 'BOARD', 'st31', 'Gizem', 'Ateş'),
        cand('c-k1-6', 'BOARD', 'st32', 'Ozan', 'Kaya'),
      ],
    },
  ],
  k2: [
    {
      id: 'el-k2-old',
      clubId: 'k2',
      status: 'COMPLETED',
      boardSeats: 2,
      auditSeats: 1,
      note: null,
      openedBy: 'st40',
      openedAt: ago(24 * 352),
      votingStartedAt: ago(24 * 340),
      closedAt: ago(24 * 336),
      eligibleVoters: 39,
      voters: 34,
      requestId: 'ap-k2-old',
      completedAt: ago(24 * 330),
      myBallots: ['PRESIDENT', 'BOARD', 'AUDIT'],
      candidates: [
        cand('c-o-1', 'PRESIDENT', 'u1', 'Elif', 'Demir', 23, true),
        cand('c-o-2', 'PRESIDENT', 'st41', 'Kaan', 'Er', 11),
        cand('c-o-3', 'BOARD', 'st5', 'Deniz', 'Kara', 19, true),
        cand('c-o-4', 'BOARD', 'st6', 'Oğuz', 'Tan', 17, true),
        cand('c-o-5', 'BOARD', 'st9', 'Selin', 'Ak', 9),
        cand('c-o-6', 'AUDIT', 'st10', 'Arda', 'Kılıç', 20, true),
      ],
    },
    {
      id: 'el-k2-older',
      clubId: 'k2',
      status: 'CANCELLED',
      boardSeats: 2,
      auditSeats: 0,
      note: null,
      openedBy: 'st40',
      openedAt: ago(24 * 420),
      votingStartedAt: null,
      closedAt: null,
      eligibleVoters: null,
      voters: null,
      requestId: null,
      completedAt: ago(24 * 405),
      myBallots: [],
      candidates: [],
    },
  ],
}

/** Önizlemede diğer üyelerin oyları: aday sırasına göre sabit sayılar; oylama bitince Elif'in oylarına eklenir. */
const OTHERS_VOTES = [14, 9, 12, 7, 11, 5, 8, 6]
const OTHER_VOTERS = 26
const myVotes: Record<string, string[]> = {}

/** Oylama bitmeden oy sayıları gizli: yanıtta null. */
/** Kurul listesinden ad; eski görevlinin adı örnek veride yok. */
const boardName = (clubId: string, id: string) => {
  const m = (fx.clubBoard[clubId] ?? []).find((x) => x.studentId === id)
  return m ? [m.firstName, m.lastName].filter(Boolean).join(' ') : null
}

const electionView = (e: Election): Election => ({
  ...e,
  openedByName: boardName(e.clubId, e.openedBy),
  myBallots: [...(e.myBallots ?? [])],
  candidates: e.candidates.map((c) => ({ ...c, votes: e.closedAt ? c.votes : null, elected: !!e.closedAt && c.elected })),
})

function findElection(config: InternalAxiosRequestConfig, clubId: string, id: string, phase?: Election['status']): Election {
  const e = (elections[clubId] ?? []).find((x) => x.id === id)
  if (!e) fail(config, 404, 'ELECTION_NOT_FOUND', 'Seçim bulunamadı.')
  if (phase && e.status !== phase) fail(config, 409, 'ELECTION_PHASE', 'Seçim bu işlem için uygun aşamada değil.')
  return e
}

const running = (clubId: string) =>
  (elections[clubId] ?? []).some((e) => e.status === 'CANDIDACY' || e.status === 'VOTING' || e.status === 'AWAITING_APPROVAL')

// ——— Kulüp kayıtları (Satranç) ———

const day = (hoursAgo: number) => ago(hoursAgo).slice(0, 10)
const localAt = (hoursAgo: number, hh: number) => `${day(hoursAgo)}T${String(hh).padStart(2, '0')}:00:00`
const yearOf = (d: string) => {
  const [y, m, dd] = d.slice(0, 10).split('-').map(Number) as [number, number, number]
  return m > 9 || (m === 9 && dd > 30) ? y + 1 : y
}
const label = (y: number) => `${y - 1}-${y}`

const meetingOf = (
  id: string,
  hoursAgo: number,
  agenda: string,
  minutes: string,
  attendeeIds: string[],
  decisions: string[],
  numbers: number[],
  location: string | null = 'Kütüphane salonu',
): Meeting => {
  const at = localAt(hoursAgo, 18)
  const y = yearOf(at)
  return {
    id,
    meetingAt: at,
    academicYear: y,
    academicYearLabel: label(y),
    location,
    agenda,
    minutes,
    attendeeIds,
    boardSize: 2,
    quorumMet: attendeeIds.filter((x) => x === 'u1' || x === 'st5').length * 2 > 2,
    decisions: decisions.map((text, i) => ({
      id: `${id}-d${i}`,
      order: i + 1,
      text,
      number: numbers[i] ?? null,
      label: numbers[i] ? `${label(y)}/${numbers[i]}` : null,
    })),
    preparedBy: 'u1',
    createdAt: ago(hoursAgo - 20),
    approvedAt: ago(hoursAgo - 22),
    status: 'APPROVED',
  }
}

export const meetings: Record<string, Meeting[]> = {
  k2: [
    meetingOf(
      'mt1',
      24 * 8,
      'Hızlı satranç turnuvasının hazırlığı. Yeni dönem üye kampanyası.',
      'Turnuva için kütüphane salonu cumartesi günü ayrıldı; tahtalar kulüpten, saatler bölümden ödünç alınacak. Deniz Kara üye kampanyası için tanıtım masası önerdi; kabul edildi.',
      ['u1', 'st5', 'st6'],
      ['Hızlı satranç turnuvası cumartesi 13.00’te kütüphane salonunda yapılacak.', 'Tanıtım haftasında iki gün stant açılacak.'],
      [1, 2],
    ),
    meetingOf(
      'mt2',
      24 * 50,
      'Dönem sonu değerlendirmesi. Lig takımlarının belirlenmesi.',
      'Bahar döneminde 3 turnuva, 11 ders yapıldı. Lig için A ve B takımları belirlendi; B takımının kaptanı Selin Ak.',
      ['u1', 'st5'],
      ['Üniversiteler arası lige iki takımla katılınacak.', 'B takımının kaptanı Selin Ak olacak.'],
      [5, 6],
    ),
    meetingOf(
      'mt3',
      24 * 140,
      'Bahar dönemi etkinlik takvimi.',
      'Her çarşamba serbest oyun, ayda bir hızlı turnuva. Simültane gösterisi nisan sonunda.',
      ['u1', 'st6'],
      ['Bahar takvimi kabul edildi.'],
      [3],
    ),
  ],
}

export const budgets: Record<string, Budget[]> = {
  k2: [
    {
      id: 'bd2',
      academicYear: yearOf(day(0)),
      academicYearLabel: label(yearOf(day(0))),
      plannedIncome: 7000,
      plannedExpense: 6000,
      description:
        'Aidatlar 3.500 ₺, sponsorluk 2.000 ₺, turnuva katılım ücretleri 1.500 ₺. Giderler: ödüller 2.500 ₺, lig yol giderleri 2.500 ₺, malzeme 1.000 ₺.',
      preparedBy: 'u1',
      createdAt: ago(24 * 18),
      approvedAt: ago(24 * 15),
      status: 'APPROVED',
    },
    {
      id: 'bd1',
      academicYear: yearOf(day(0)) - 1,
      academicYearLabel: label(yearOf(day(0)) - 1),
      plannedIncome: 5000,
      plannedExpense: 4500,
      description: null,
      preparedBy: 'st41',
      createdAt: ago(24 * 380),
      approvedAt: ago(24 * 375),
      status: 'APPROVED',
    },
  ],
}

const entry = (
  id: string,
  type: FinanceEntry['type'],
  amount: number,
  description: string,
  hoursAgo: number,
  extra: Partial<FinanceEntry> = {},
): FinanceEntry => ({
  id,
  type,
  amount,
  description,
  occurredOn: day(hoursAgo),
  academicYear: yearOf(day(hoursAgo)),
  hasDocument: false,
  documentName: null,
  budgetExceeded: false,
  sponsorshipId: null,
  preparedBy: 'u1',
  createdAt: ago(hoursAgo),
  approvedAt: ago(hoursAgo - 2),
  status: 'APPROVED',
  ...extra,
})

export const entries: Record<string, FinanceEntry[]> = {
  k2: [
    entry('fe1', 'INCOME', 2050, 'Güz dönemi üye aidatları', 24 * 12),
    entry('fe2', 'EXPENSE', 1200, 'Hızlı turnuva ödülleri', 24 * 6, { hasDocument: true, documentName: 'odul-faturasi.pdf' }),
    entry('fe3', 'EXPENSE', 3200, 'Turnuva saatleri (10 adet)', 30, {
      approvedAt: null,
      status: 'PENDING_ADVISOR',
      hasDocument: true,
      documentName: 'saat-teklifi.pdf',
    }),
    entry('fe4', 'INCOME', 2000, 'Kuzey Kitabevi sponsorluğu', 24 * 40, { sponsorshipId: 'sp1' }),
    entry('fe5', 'EXPENSE', 2700, 'Lig yol giderleri', 24 * 70, { hasDocument: true, documentName: 'otobus-bileti.pdf' }),
    entry('fe6', 'INCOME', 1800, 'Bahar dönemi aidatları', 24 * 200),
    entry('fe7', 'EXPENSE', 950, 'Satranç takımları', 24 * 210),
  ],
}

export const sponsorships: Record<string, Sponsorship[]> = {
  k2: [
    {
      id: 'sp1',
      sponsorName: 'Kuzey Kitabevi',
      description: 'Bahar ligi süresince turnuva ödülleri ve afişlerde kitabevinin adı.',
      cashAmount: 2000,
      inKind: '15 satranç kitabı',
      startsOn: day(24 * 42),
      endsOn: day(-24 * 120),
      hasDocument: true,
      documentName: 'sponsorluk-sozlesmesi.pdf',
      preparedBy: 'u1',
      createdAt: ago(24 * 42),
      approvedAt: ago(24 * 40),
      status: 'APPROVED',
    },
  ],
}

const snapshotOf = (y: number): ReportSnapshot => {
  const approved = (entries.k2 ?? []).filter((e) => e.academicYear === y && e.status === 'APPROVED')
  const sum = (t: FinanceEntry['type']) => approved.filter((e) => e.type === t).reduce((a, e) => a + e.amount, 0)
  const budget = (budgets.k2 ?? []).find((b) => b.academicYear === y && b.status === 'APPROVED')
  const ms = (meetings.k2 ?? []).filter((m) => m.academicYear === y && m.status === 'APPROVED')
  return {
    activeMembers: 41,
    endedMemberships: y === yearOf(day(0)) ? 1 : 6,
    announcements: y === yearOf(day(0)) ? 2 : 14,
    meetings: ms.length,
    decisions: ms.reduce((a, m) => a + m.decisions.length, 0),
    plannedIncome: budget?.plannedIncome ?? null,
    plannedExpense: budget?.plannedExpense ?? null,
    income: sum('INCOME'),
    expense: sum('EXPENSE'),
    balance: sum('INCOME') - sum('EXPENSE'),
    events: y === yearOf(day(0)) ? 2 : 9,
    completedEvents: y === yearOf(day(0)) ? 1 : 8,
    cancelledEvents: y === yearOf(day(0)) ? 0 : 1,
    registrations: y === yearOf(day(0)) ? 34 : 212,
    attendances: y === yearOf(day(0)) ? 21 : 168,
  }
}

const lastYear = yearOf(day(0)) - 1
export const reports: Record<string, Report[]> = {
  k2: [
    {
      id: 'rp1',
      clubId: 'k2',
      type: 'ACTIVITY',
      academicYear: lastYear,
      academicYearLabel: label(lastYear),
      status: 'APPROVED',
      body: 'Yıl boyunca 9 etkinlik düzenlendi; ikisi üniversiteler arası lig maçıydı. Haftalık serbest oyun akşamları ortalama 18 kişiyle sürdü. Simültane gösterisi 60 izleyiciye ulaştı.',
      financeNote: 'Giderler bütçenin altında kaldı; lig yol giderleri sponsorlukla karşılandı.',
      recommendations: null,
      snapshot: null,
      requestId: 'ap-rp1',
      createdAt: ago(24 * 60),
      updatedAt: ago(24 * 55),
      submittedAt: ago(24 * 50),
      approvedAt: ago(24 * 45),
    },
    {
      id: 'rp2',
      clubId: 'k2',
      type: 'AUDIT',
      academicYear: lastYear,
      academicYearLabel: label(lastYear),
      status: 'SUBMITTED',
      body: 'Gelir ve gider kayıtları belgeleriyle karşılaştırıldı; tutarsızlık bulunmadı. Üç toplantı tutanağı imzalı ve karar defterine işlenmiş.',
      financeNote: null,
      recommendations: 'Malzeme alımlarında en az iki teklif alınması önerilir.',
      snapshot: null,
      requestId: 'ap-rp2',
      createdAt: ago(24 * 40),
      updatedAt: ago(24 * 36),
      submittedAt: ago(24 * 35),
      approvedAt: null,
    },
  ],
}
reports.k2![0]!.snapshot = snapshotOf(lastYear)

const log = (id: string, hoursAgo: number, action: string, requestType: string | null, detail: string | null = null): DecisionLogEntry => ({
  id,
  requestId: null,
  requestType,
  action,
  actorId: null,
  subjectUserId: null,
  detail,
  createdAt: ago(hoursAgo),
})

export const decisionLog: Record<string, DecisionLogEntry[]> = {
  k2: [
    log('lg1', 20, 'SUBMITTED', 'CLUB_ANNOUNCEMENT'),
    log('lg2', 30, 'SUBMITTED', 'CLUB_FINANCE_ENTRY'),
    log('lg3', 30, 'PRESIDENT_APPROVED', 'ROLE_CHANGE'),
    log('lg4', 24 * 6, 'APPROVED', 'CLUB_FINANCE_ENTRY'),
    log('lg5', 24 * 8, 'APPROVED', 'CLUB_MEETING_MINUTES', '2 karar deftere işlendi'),
    log('lg6', 24 * 12, 'MEMBERSHIP_APPROVED', null),
    log('lg7', 24 * 15, 'APPROVED', 'CLUB_BUDGET'),
    log('lg8', 24 * 35, 'SUBMITTED', 'CLUB_AUDIT_REPORT'),
    log('lg9', 24 * 45, 'APPROVED', 'CLUB_ACTIVITY_REPORT'),
    log('lg10', 24 * 60, 'MEMBER_LEFT', null),
    log('lg11', 24 * 330, 'BOARD_HANDOVER', null, '3 görev seçimle devredildi'),
    log('lg12', 24 * 340, 'ELECTION_VOTING_STARTED', null),
    log('lg13', 24 * 352, 'ELECTION_OPENED', null),
  ],
}

const term = (
  studentId: string,
  firstName: string,
  lastName: string,
  position: string,
  positionName: string,
  startedAgo: number,
  endedAgo: number | null,
  endReason: string | null = null,
): PositionTerm => ({
  clubId: 'k2',
  clubName: 'Satranç Kulübü',
  studentId,
  firstName,
  lastName,
  position,
  positionName,
  startedAt: ago(startedAgo),
  endedAt: endedAgo == null ? null : ago(endedAgo),
  endReason,
})

export const positionHistory: Record<string, PositionTerm[]> = {
  k2: [
    term('u1', 'Elif', 'Demir', 'ROLE_CLUB_OFFICIAL', 'Kulüp Başkanı', 24 * 330, null),
    term('st5', 'Deniz', 'Kara', 'ROLE_VICE_PRESIDENT', 'Başkan Yardımcısı', 24 * 300, null),
    term('st6', 'Oğuz', 'Tan', 'ROLE_MEMBERSHIP_OFFICER', 'Üyelik Sorumlusu', 24 * 200, null),
    term('st5', 'Deniz', 'Kara', 'ROLE_BOARD_MEMBER', 'Yönetim Kurulu Üyesi', 24 * 330, 24 * 300, 'CHANGED'),
    term('st6', 'Oğuz', 'Tan', 'ROLE_BOARD_MEMBER', 'Yönetim Kurulu Üyesi', 24 * 330, 24 * 200, 'CHANGED'),
    term('st10', 'Arda', 'Kılıç', 'ROLE_AUDITOR', 'Denetim Kurulu Üyesi', 24 * 330, 24 * 90, 'RESIGNED'),
    term('st40', 'Melis', 'Kurt', 'ROLE_CLUB_OFFICIAL', 'Kulüp Başkanı', 24 * 700, 24 * 330, 'HANDOVER'),
    term('u1', 'Elif', 'Demir', 'ROLE_BOARD_MEMBER', 'Yönetim Kurulu Üyesi', 24 * 700, 24 * 330, 'HANDOVER'),
    term('st41', 'Kaan', 'Er', 'ROLE_TREASURER', 'Sayman', 24 * 600, 24 * 330, 'HANDOVER'),
  ],
}

export const pastPresidents: Record<string, ClubMember[]> = {
  k2: [
    {
      studentId: 'st40',
      firstName: 'Melis',
      lastName: 'Kurt',
      role: 'ROLE_CLUB_OFFICIAL',
      isActive: false,
      termStartDate: ago(24 * 700),
      termEndDate: ago(24 * 330),
    },
    {
      studentId: 'st42',
      firstName: 'Barış',
      lastName: 'Yılmaz',
      role: 'ROLE_CLUB_OFFICIAL',
      isActive: false,
      termStartDate: ago(24 * 1100),
      termEndDate: ago(24 * 700),
    },
  ],
}

const previewDocument = () => new Blob(['%PDF-1.4\n% EduConnect önizleme belgesi\n'], { type: 'application/pdf' })

/** Faaliyet raporunu ya da denetim raporunu bulur ya da taslak olarak açar. */
function draftOf(clubId: string, type: Report['type'], y: number, config: InternalAxiosRequestConfig): Report {
  const list = (reports[clubId] ??= [])
  let r = list.find((x) => x.type === type && x.academicYear === y)
  if (r && r.status !== 'DRAFT') fail(config, 409, 'REPORT_LOCKED', 'Gönderilmiş rapor düzenlenemez.')
  if (!r) {
    r = {
      id: nextId('rp'),
      clubId,
      type,
      academicYear: y,
      academicYearLabel: label(y),
      status: 'DRAFT',
      body: null,
      financeNote: null,
      recommendations: null,
      snapshot: null,
      requestId: null,
      createdAt: now(),
      updatedAt: null,
      submittedAt: null,
      approvedAt: null,
    }
    list.push(r)
  }
  r.updatedAt = now()
  return r
}

const yearParam = (config: InternalAxiosRequestConfig) =>
  Number((config.params as { academicYear?: number } | undefined)?.academicYear ?? yearOf(day(0)))

/** Danışman onayındaki gelir-gider kaydı (saat alımı) kulüp kararlarında görünür. */
submit('k2', { type: 'CLUB_FINANCE_ENTRY', status: 'PENDING_ADVISOR', createdAt: ago(30), financeEntry: entries.k2![2]! })

// ——— Etkinlik görevlileri ve kapı ———

const staffOf = (
  eventId: string,
  userId: string,
  firstName: string,
  lastName: string,
  status: EventStaff['status'],
  hoursAgo: number,
): EventStaff => ({
  eventId,
  userId,
  firstName,
  lastName,
  status,
  proposedBy: 'u1',
  approvedBy: status === 'APPROVED' ? 'st2' : null,
  createdAt: ago(hoursAgo),
  decidedAt: status === 'APPROVED' ? ago(hoursAgo - 3) : null,
})

export const eventStaff: Record<string, EventStaff[]> = {
  e1: [
    staffOf('e1', 'u1', 'Elif', 'Demir', 'APPROVED', 48),
    staffOf('e1', 'st3', 'Ece', 'Yalçın', 'APPROVED', 40),
    staffOf('e1', 'st4', 'Mert', 'Aksu', 'PENDING', 6),
  ],
}

/** Kapıda denenecek kodlar → yoklama satırı. */
const GATE_CODES: Record<string, { eventId: string; studentId: string }> = {
  'EC-E1-BURCU': { eventId: 'e1', studentId: 'st7' },
  'EC-E1-KAAN': { eventId: 'e1', studentId: 'st8' },
}

const eventClub = (eventId: string) => fx.findEvent(eventId)?.clubId ?? ''

// ——— Kişi profilleri ———

const blank: PersonProfile = {
  id: '',
  firstName: null,
  lastName: null,
  email: null,
  profileImageUrl: null,
  bio: null,
  role: null,
  studentNumber: null,
  title: null,
  academicTitle: null,
  department: null,
  programId: null,
  programName: null,
  programLevel: null,
  facultyName: null,
  facultyId: null,
  departmentId: null,
  entryYear: null,
  classYear: null,
  staffCategory: null,
  affiliations: null,
  officeNumber: null,
  officeHours: null,
  studentStatus: null,
  staffStatus: null,
}

const academicianProfile = (
  id: string,
  first: string,
  last: string,
  title: string,
  code: string,
  department: string,
  extra: Partial<PersonProfile> = {},
): PersonProfile => ({
  ...blank,
  id,
  firstName: first,
  lastName: last,
  role: 'Academician',
  title,
  academicTitle: code,
  department,
  facultyName: department === 'Matematik' ? 'Fen Fakültesi' : 'Mühendislik Fakültesi',
  staffCategory: code.startsWith('RESEARCH') ? 'RESEARCH_ASSISTANT' : 'FACULTY_MEMBER',
  affiliations: ['ACADEMICIAN'],
  email: `${first.toLocaleLowerCase('tr-TR')}.${last.toLocaleLowerCase('tr-TR')}@educonnect.local`
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/ı/g, 'i'),
  ...extra,
})

/** Öğrenci: e-posta ve numara yalnız Satranç Kulübü üyelerinde (Elif başkan) dolu gelir. */
const studentProfile = (
  id: string,
  first: string,
  last: string,
  number: string,
  program: string,
  classYear: number,
  bio: string | null = null,
): PersonProfile => {
  const related = (members.k2 ?? []).some((m) => m.studentId === id)
  return {
    ...blank,
    id,
    firstName: first,
    lastName: last,
    role: 'Student',
    programName: program,
    programLevel: 'BACHELOR',
    facultyName: program.includes('Matematik') ? 'Fen Fakültesi' : 'Mühendislik Fakültesi',
    classYear,
    affiliations: ['STUDENT'],
    bio,
    studentNumber: related ? number : null,
    email: related
      ? `${first.toLocaleLowerCase('tr-TR')}.${last.toLocaleLowerCase('tr-TR')}@ogr.educonnect.local`
          .normalize('NFD')
          .replace(/[\u0300-\u036f]/g, '')
          .replace(/ı/g, 'i')
      : null,
  }
}

const hakan = academicianProfile('s4', 'Hakan', 'Öztürk', 'Prof. Dr.', 'PROFESSOR', 'Matematik', {
  bio: 'Sayılar kuramı ve kriptografi üzerine çalışıyorum. Satranç Kulübü ve Matematik Topluluğu danışmanıyım.',
  officeNumber: 'Fen Fakültesi A-312',
  officeHours: 'Salı 14.00–16.00\nPerşembe 10.00–11.00 (randevuyla)',
})
const mehmet = academicianProfile('s1', 'Mehmet', 'Kaya', 'Dr. Öğr. Üyesi', 'ASSISTANT_PROFESSOR', 'Bilgisayar Mühendisliği', {
  officeNumber: 'B Blok 210',
  officeHours: 'Çarşamba 13.00–15.00',
})

export const people: Record<string, PersonProfile> = {
  s1: mehmet,
  'u-c1': { ...mehmet, id: 'u-c1' },
  s2: academicianProfile('s2', 'Deniz', 'Yılmaz', '', 'RESEARCH_ASSISTANT', 'Bilgisayar Mühendisliği'),
  s3: academicianProfile('s3', 'Ayşe', 'Arslan', 'Doç. Dr.', 'ASSOCIATE_PROFESSOR', 'Bilgisayar Mühendisliği', {
    officeNumber: 'B Blok 305',
  }),
  s4: hakan,
  'u-c3': { ...hakan, id: 'u-c3' },
  st2: studentProfile(
    'st2',
    'Can',
    'Erdem',
    '2022104077',
    'Bilgisayar Mühendisliği',
    4,
    'Robotik Kulübü başkanı. Yarışma robotlarının gömülü yazılımıyla uğraşıyorum.',
  ),
  st3: studentProfile('st3', 'Ece', 'Yalçın', '2023104050', 'Elektrik-Elektronik Mühendisliği', 3),
  st4: studentProfile('st4', 'Mert', 'Aksu', '2023104022', 'Bilgisayar Mühendisliği', 3),
  st5: studentProfile(
    'st5',
    'Deniz',
    'Kara',
    '2022105011',
    'Matematik',
    4,
    'Satranç Kulübü başkan yardımcısı. Lig takımının A kadrosundayım.',
  ),
  st6: studentProfile('st6', 'Oğuz', 'Tan', '2024105007', 'Matematik', 2),
  st7: studentProfile('st7', 'Burcu', 'Öz', '2024104031', 'Bilgisayar Mühendisliği', 2),
  st9: studentProfile('st9', 'Selin', 'Ak', '2024105019', 'Matematik', 2),
}

const BADGES: Record<string, { badgeType: string; name: string; description: string; imageUrl: null; earnedAt: string }[]> = {
  st2: [
    { badgeType: 'HELPFUL', name: 'Yardımsever', description: '10 cevabı kabul edildi.', imageUrl: null, earnedAt: ago(24 * 60) },
    { badgeType: 'FIRST_POST', name: 'İlk gönderi', description: 'Akışta ilk kez paylaştı.', imageUrl: null, earnedAt: ago(24 * 300) },
  ],
}

// ——— Onay kayıtları ———

function submit(clubId: string, r: Partial<ApprovalRequest> & Pick<ApprovalRequest, 'type' | 'status'>): ApprovalRequest {
  const club = fx.clubs.find((c) => c.id === clubId)
  const saved: ApprovalRequest = {
    id: nextId('ap'),
    clubId,
    clubName: club?.name ?? null,
    preparedBy: 'u1',
    preparedByName: 'Elif Demir',
    subjectUserId: null,
    subjectUserName: null,
    currentPosition: null,
    requestedPosition: null,
    note: null,
    rejectionReason: null,
    responseNote: null,
    createdAt: now(),
    presidentDecidedByName: null,
    presidentDecidedAt: null,
    decidedByName: null,
    decidedAt: null,
    profileChange: null,
    announcement: null,
    ...r,
  }
  ;(fx.clubApprovals[clubId] ??= []).unshift(saved)
  return { ...saved }
}

const POINTS: Record<string, number> = { st2: 340, st3: 215 }
/** Liderlik tablosundan çıkan öğrenci: başkası puanını ve rozetlerini görmez (F-92). */
const LEADERBOARD_OPTED_OUT = new Set(['st4'])

/** Hakkımda açılan talep: Robotik'te başkan, Elif'in üyelikten çıkarılmasını istemiş; danışman kararı bekleniyor (F-89). */
export const requestsAboutMe: ApprovalRequest[] = [
  {
    id: 'ar-mine-1',
    clubId: 'k1',
    clubName: 'Robotik Kulübü',
    type: 'MEMBER_EXPULSION',
    status: 'PENDING_ADVISOR',
    preparedBy: 'st2',
    preparedByName: 'Can Erdem',
    subjectUserId: 'u1',
    subjectUserName: 'Elif Demir',
    currentPosition: 'EVENT_COORDINATOR',
    requestedPosition: null,
    note: 'Son üç yönetim toplantısına ve iki etkinliğe mazeretsiz katılmadı.',
    rejectionReason: null,
    responseNote: null,
    createdAt: ago(30),
    presidentDecidedByName: null,
    presidentDecidedAt: null,
    decidedByName: null,
    decidedAt: null,
    profileChange: null,
    announcement: null,
  },
]

const pendingOf = (clubId: string, type: string, subject?: string) =>
  (fx.clubApprovals[clubId] ?? []).some(
    (r) =>
      r.type === type &&
      (r.status === 'PENDING_PRESIDENT' || r.status === 'PENDING_ADVISOR') &&
      (subject === undefined || r.subjectUserId === subject),
  )

const presides = (clubId: string) => fx.clubAccess(clubId).actingPresident

// ——— Tablolar ———

export const GET: [RegExp, Handler][] = [
  [/^\/clubs\/founding-invitations$/, () => invitations.map((r) => ({ ...r, founders: r.founders.map((f) => ({ ...f })) }))],
  [/^\/clubs\/my-creation-requests$/, () => creations.map((r) => ({ ...r, founders: r.founders.map((f) => ({ ...f })) }))],
  [/^\/clubs\/([^/]+)\/advisor-change-requests$/, (m) => (advisorChanges[m[1]!] ?? []).map((r) => ({ ...r }))],
  [/^\/clubs\/([^/]+)\/elections$/, (m) => (elections[m[1]!] ?? []).map(electionView)],
  [
    /^\/users\/profile\/([^/]+)\/aggregated$/,
    (m, config) => {
      if (!people[m[1]!]) fail(config, 404, 'NOT_FOUND', 'Profile not found')
      const optedOut = LEADERBOARD_OPTED_OUT.has(m[1]!)
      return {
        ...people[m[1]!],
        gamification: {
          totalPoints: optedOut ? null : (POINTS[m[1]!] ?? 0),
          leaderboardVisible: !optedOut,
          badges: optedOut ? [] : (BADGES[m[1]!] ?? []),
        },
        recentPosts: px.posts
          .filter((x) => x.authorId === m[1] && !x.official)
          .slice(0, 5)
          .map((x) => ({ id: x.id, title: x.title, content: x.content, createdAt: x.createdAt })),
      }
    },
  ],
  [
    /^\/users\/profile\/([^/]+)$/,
    (m, config) => (people[m[1]!] ? { ...people[m[1]!] } : fail(config, 404, 'NOT_FOUND', 'Profile not found')),
  ],
  [
    /^\/events\/manage\/staff\/me$/,
    () =>
      Object.entries(eventStaff)
        .filter(([, list]) => list.some((x) => x.userId === 'u1' && x.status === 'APPROVED'))
        .map(([id]) => ({ ...fx.findEvent(id)! })),
  ],
  [/^\/events\/manage\/([^/]+)\/staff$/, (m) => (eventStaff[m[1]!] ?? []).map((x) => ({ ...x }))],
  [
    /^\/clubs\/([^/]+)\/meetings$/,
    (m, config) =>
      (meetings[m[1]!] ?? [])
        .filter((x) => x.academicYear === yearParam(config))
        .map((x) => ({
          ...x,
          attendees: x.attendeeIds.map((id) => ({ id, name: boardName(m[1]!, id) })),
          preparedByName: boardName(m[1]!, x.preparedBy),
        })),
  ],
  [
    /^\/clubs\/([^/]+)\/decision-book$/,
    (m, config) =>
      (meetings[m[1]!] ?? [])
        .filter((x) => x.academicYear === yearParam(config) && x.status === 'APPROVED')
        .flatMap((x) =>
          x.decisions
            .filter((d) => d.number != null)
            .map((d) => ({
              number: d.number!,
              label: d.label!,
              text: d.text,
              meetingId: x.id,
              meetingAt: x.meetingAt,
              approvedAt: x.approvedAt,
            })),
        ),
  ],
  [
    /^\/clubs\/([^/]+)\/finance\/summary$/,
    (m, config) => {
      const y = yearParam(config)
      const all = (entries[m[1]!] ?? []).filter((e) => e.status === 'APPROVED')
      const total = (list: FinanceEntry[], t: FinanceEntry['type']) => list.filter((e) => e.type === t).reduce((a, e) => a + e.amount, 0)
      const inYear = all.filter((e) => e.academicYear === y)
      const budget = (budgets[m[1]!] ?? []).find((b) => b.academicYear === y && b.status === 'APPROVED') ?? null
      return {
        academicYear: y,
        academicYearLabel: label(y),
        budget,
        income: total(inYear, 'INCOME'),
        expense: total(inYear, 'EXPENSE'),
        balance: total(inYear, 'INCOME') - total(inYear, 'EXPENSE'),
        overallBalance: total(all, 'INCOME') - total(all, 'EXPENSE'),
        advisorThreshold: 1000,
        academicYearEnd: `${y}-09-30`,
      }
    },
  ],
  [/^\/clubs\/([^/]+)\/finance\/budgets$/, (m) => (budgets[m[1]!] ?? []).map((x) => ({ ...x }))],
  [
    /^\/clubs\/([^/]+)\/finance\/entries$/,
    (m, config) =>
      (entries[m[1]!] ?? [])
        .map((e) => ({ ...e, preparedByName: boardName(m[1]!, e.preparedBy) }))
        .filter((e) => e.academicYear === yearParam(config))
        .sort((a, b) => b.occurredOn.localeCompare(a.occurredOn))
        .map((x) => ({ ...x })),
  ],
  [/^\/clubs\/([^/]+)\/finance\/(entries|sponsorships)\/([^/]+)\/document$/, () => previewDocument()],
  [/^\/clubs\/([^/]+)\/finance\/sponsorships$/, (m) => (sponsorships[m[1]!] ?? []).map((x) => ({ ...x }))],
  [/^\/clubs\/([^/]+)\/reports\/preview\/(\d+)$/, (m) => snapshotOf(Number(m[2]))],
  [
    /^\/clubs\/([^/]+)\/reports$/,
    (m, config) => (reports[m[1]!] ?? []).filter((r) => r.academicYear === yearParam(config)).map((x) => ({ ...x })),
  ],
  [/^\/clubs\/([^/]+)\/decision-log$/, (m) => (decisionLog[m[1]!] ?? []).map((x) => ({ ...x }))],
  [/^\/clubs\/([^/]+)\/position-history$/, (m) => (positionHistory[m[1]!] ?? []).map((x) => ({ ...x }))],
  [/^\/clubs\/([^/]+)\/past-presidents$/, (m) => (pastPresidents[m[1]!] ?? []).map((x) => ({ ...x }))],
  [/^\/clubs\/(k2)$/, (m) => ({ ...fx.clubDetail(m[1]!)!, members: members[m[1]!]!.map((x) => ({ ...x })) })],
  [
    /^\/users\/search\/academicians$/,
    (_m, config) => {
      const q = searchKey(String((config.params as { query?: string } | undefined)?.query ?? ''))
      if (q.length < 2) return []
      return academicians.filter((a) => searchKey(`${a.firstName} ${a.lastName}`).includes(q))
    },
  ],
]

export const WRITE: [string, RegExp, Handler][] = [
  [
    'post',
    /^\/clubs\/([^/]+)\/approvals\/([^/]+)\/defence$/,
    (m, config) => {
      const r = requestsAboutMe.find((x) => x.id === m[2])
      if (!r) fail(config, 403, 'FORBIDDEN', 'Savunmayı yalnızca hakkında çıkarma talebi açılan üye yapabilir.')
      r.responseNote = body<{ note: string }>(config).note
      return { ...r }
    },
  ],
  [
    'post',
    /^\/clubs\/request-creation$/,
    (_m, config) => {
      const b = body<{ name: string; about: string | null; academicAdvisorId: string; founderStudentNumbers: string[] }>(config)
      if (fx.clubs.some((c) => c.name.toLocaleLowerCase('tr-TR') === b.name.toLocaleLowerCase('tr-TR')))
        fail(config, 409, 'CLUB_NAME_TAKEN', 'Bu isimde bir kulüp zaten var.')
      const unknown = b.founderStudentNumbers.find((n) => !/^\d{7,10}$/.test(n))
      if (unknown) fail(config, 400, 'FOUNDER_STUDENT_NOT_FOUND', `Bu öğrenci numarası kayıtlı değil: ${unknown}`)
      if (b.founderStudentNumbers.length + 1 < MIN_FOUNDERS)
        fail(config, 400, 'FOUNDERS_REQUIRED', `Kulüp kurmak için en az ${MIN_FOUNDERS} kurucu öğrenci gerekir.`)
      creations.unshift({
        id: nextId('cr'),
        clubName: b.name,
        about: b.about,
        requestingStudentId: 'u1',
        requestingStudentName: 'Elif Demir',
        suggestedAdvisorId: b.academicAdvisorId,
        suggestedAdvisorName: 'Prof. Dr. Hakan Öztürk',
        status: 'PENDING_FOUNDERS',
        requestDate: now(),
        rejectionReason: null,
        processedAt: null,
        clubId: null,
        founders: [
          founder('u1', 'Elif', 'Demir', 'CONFIRMED', now()),
          ...b.founderStudentNumbers.map((n) => founder(`st-${n}`, 'Öğrenci', n, 'INVITED', null)),
        ],
      })
      return 'Club creation request submitted. Pending advisor approval.'
    },
  ],
  [
    'post',
    /^\/clubs\/creation-requests\/([^/]+)\/founders\/(confirm|decline)$/,
    (m) => {
      const i = invitations.findIndex((r) => r.id === m[1])
      const r = invitations[i]!
      const f = r.founders.find((x) => x.studentId === 'u1')!
      Object.assign(f, { status: m[2] === 'confirm' ? 'CONFIRMED' : 'DECLINED', respondedAt: now() })
      invitations.splice(i, 1)
      return { ...r }
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/role-change-requests$/,
    (m, config) => {
      const clubId = m[1]!
      const b = body<{ studentId?: string; studentNumber?: string; requestedRole: string }>(config)
      const found = b.studentId ? { id: b.studentId, name: '' } : NUMBERS[b.studentNumber ?? '']
      if (!found) fail(config, 404, 'NOT_FOUND', 'Bu öğrenci numarasıyla kayıtlı öğrenci bulunamadı.')
      const target = (members[clubId] ?? []).find((x) => x.studentId === found.id)
      if (!target) fail(config, 400, 'BAD_REQUEST', 'Bu öğrenci kulübün aktif üyesi değil. Önce üye olmalı.')
      if (canonical(target.role) === b.requestedRole) fail(config, 400, 'BAD_REQUEST', 'Öğrenci zaten bu görevde.')
      if (pendingOf(clubId, 'ROLE_CHANGE', target.studentId))
        fail(config, 409, 'CONFLICT', 'Bu öğrenci için zaten bekleyen bir görev değişikliği talebi var.')
      const limit = LIMIT[b.requestedRole]
      if (limit && (members[clubId] ?? []).filter((x) => canonical(x.role) === b.requestedRole).length >= limit)
        fail(config, 409, 'CONFLICT', 'Bu görev için kontenjan dolu.')
      submit(clubId, {
        type: 'ROLE_CHANGE',
        status: 'PENDING_ADVISOR',
        subjectUserId: target.studentId,
        subjectUserName: `${target.firstName} ${target.lastName}`,
        currentPosition: target.role,
        requestedPosition: b.requestedRole,
      })
      return { id: nextId('rc'), status: 'PENDING' }
    },
  ],
  [
    'delete',
    /^\/clubs\/([^/]+)\/members\/([^/]+)\/role$/,
    (m, config) => {
      const target = (members[m[1]!] ?? []).find((x) => x.studentId === m[2])!
      if (pendingOf(m[1]!, 'ROLE_CHANGE', target.studentId))
        fail(config, 409, 'CONFLICT', 'Bu öğrenci için zaten bekleyen bir görev değişikliği talebi var.')
      submit(m[1]!, {
        type: 'ROLE_CHANGE',
        status: 'PENDING_ADVISOR',
        subjectUserId: target.studentId,
        subjectUserName: `${target.firstName} ${target.lastName}`,
        currentPosition: target.role,
        requestedPosition: 'MEMBER',
      })
      return 'Görevden alma talebi danışman onayına gönderildi.'
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/resignations$/,
    (m, config) => {
      if (pendingOf(m[1]!, 'RESIGNATION', 'u1')) fail(config, 409, 'RESIGNATION_PENDING', 'Bekleyen bir istifa talebiniz var.')
      const position = fx.clubAccess(m[1]!).position
      return submit(m[1]!, {
        type: 'RESIGNATION',
        status: presides(m[1]!) ? 'PENDING_ADVISOR' : 'PENDING_PRESIDENT',
        subjectUserId: 'u1',
        subjectUserName: 'Elif Demir',
        currentPosition: position,
        requestedPosition: 'MEMBER',
        note: body<{ note: string | null }>(config).note,
      })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/expulsion-requests$/,
    (m, config) => {
      const b = body<{ studentId: string; reason: string }>(config)
      if (pendingOf(m[1]!, 'MEMBER_EXPULSION', b.studentId))
        fail(config, 409, 'EXPULSION_PENDING', 'Bu üye için bekleyen bir çıkarma talebi var.')
      const target = (members[m[1]!] ?? []).find((x) => x.studentId === b.studentId)!
      return submit(m[1]!, {
        type: 'MEMBER_EXPULSION',
        status: 'PENDING_ADVISOR',
        subjectUserId: target.studentId,
        subjectUserName: `${target.firstName} ${target.lastName}`,
        currentPosition: target.role,
        note: b.reason,
      })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/profile-change-requests$/,
    (m, config) => {
      if (pendingOf(m[1]!, 'CLUB_PROFILE_UPDATE'))
        fail(config, 409, 'PROFILE_CHANGE_PENDING', 'Kulüp bilgileri için bekleyen bir talep var.')
      const b = body<Partial<ProfileDraft> & { note: string | null }>(config)
      const c = fx.clubDetail(m[1]!)!
      const pick = (k: keyof ProfileDraft, was: string | null) => (b[k] === undefined ? was : b[k] || null)
      return submit(m[1]!, {
        type: 'CLUB_PROFILE_UPDATE',
        status: presides(m[1]!) ? 'PENDING_ADVISOR' : 'PENDING_PRESIDENT',
        note: b.note,
        profileChange: {
          about: pick('about', c.about),
          category: pick('category', c.profile?.category ?? null) as ClubCategory | null,
          contactEmail: pick('contactEmail', c.profile?.contactEmail ?? null),
          websiteUrl: pick('websiteUrl', c.profile?.websiteUrl ?? null),
          instagramUrl: pick('instagramUrl', c.profile?.instagramUrl ?? null),
          xUrl: pick('xUrl', c.profile?.xUrl ?? null),
          linkedinUrl: pick('linkedinUrl', c.profile?.linkedinUrl ?? null),
          logoUrl: null,
        },
      })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/logo-change-requests$/,
    (m, config) => {
      if (pendingOf(m[1]!, 'CLUB_LOGO_CHANGE')) fail(config, 409, 'LOGO_CHANGE_PENDING', 'Bekleyen bir logo değişikliği talebi var.')
      const form = config.data as FormData
      return submit(m[1]!, {
        type: 'CLUB_LOGO_CHANGE',
        status: presides(m[1]!) ? 'PENDING_ADVISOR' : 'PENDING_PRESIDENT',
        note: (form.get('note') as string | null) ?? null,
        profileChange: {
          about: null,
          category: null,
          contactEmail: null,
          websiteUrl: null,
          instagramUrl: null,
          xUrl: null,
          linkedinUrl: null,
          logoUrl: URL.createObjectURL(form.get('file') as File),
        },
      })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/advisor-change-requests$/,
    (m, config) => {
      const list = (advisorChanges[m[1]!] ??= [])
      if (list.some((r) => r.status === 'PENDING_ADVISOR'))
        fail(config, 409, 'ADVISOR_CHANGE_PENDING', 'Bekleyen bir danışman değişikliği talebi var.')
      const b = body<{ proposedAdvisorId: string; message: string | null }>(config)
      const a = academicians.find((x) => x.id === b.proposedAdvisorId)!
      const saved: AdvisorChange = {
        id: nextId('ac'),
        clubId: m[1]!,
        clubName: fx.clubs.find((c) => c.id === m[1])?.name ?? null,
        proposedAdvisorId: a.id,
        proposedAdvisorName: fullName(a),
        requestedBy: 'u1',
        message: b.message,
        status: 'PENDING_ADVISOR',
        rejectionReason: null,
        createdAt: now(),
        decidedAt: null,
      }
      list.unshift(saved)
      return { ...saved }
    },
  ],
  [
    'delete',
    /^\/clubs\/([^/]+)\/advisor-change-requests\/([^/]+)$/,
    (m) => {
      const r = (advisorChanges[m[1]!] ?? []).find((x) => x.id === m[2])!
      Object.assign(r, { status: 'WITHDRAWN', decidedAt: now() })
      return null
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/closure-requests$/,
    (m, config) => {
      if (pendingOf(m[1]!, 'CLUB_CLOSURE')) fail(config, 409, 'CLOSURE_PENDING', 'Kulübün bekleyen bir kapatma talebi var.')
      return submit(m[1]!, { type: 'CLUB_CLOSURE', status: 'PENDING_ADVISOR', note: body<{ reason: string }>(config).reason })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/elections$/,
    (m, config) => {
      const clubId = m[1]!
      if (running(clubId)) fail(config, 409, 'ELECTION_OPEN', 'Kulübün devam eden bir seçimi var.')
      const b = body<{ boardSeats: number; auditSeats: number; note: string | null }>(config)
      // Açıldıktan sonra birkaç üye aday olmuş sayılır.
      const seeded = [
        cand(nextId('c'), 'PRESIDENT', 'st5', 'Deniz', 'Kara'),
        cand(nextId('c'), 'BOARD', 'st9', 'Selin', 'Ak'),
        cand(nextId('c'), 'BOARD', 'st11', 'Zeynep', 'Uçar'),
        cand(nextId('c'), 'BOARD', 'st13', 'Ceren', 'Aydın'),
        cand(nextId('c'), 'AUDIT', 'st10', 'Arda', 'Kılıç'),
        cand(nextId('c'), 'AUDIT', 'st16', 'Tolga', 'Er'),
      ].filter((c) => (c.ballot === 'BOARD' ? b.boardSeats : c.ballot === 'AUDIT' ? b.auditSeats : 1) > 0)
      const e: Election = {
        id: nextId('el'),
        clubId,
        status: 'CANDIDACY',
        boardSeats: b.boardSeats,
        auditSeats: b.auditSeats,
        note: b.note,
        openedBy: 'u1',
        openedAt: now(),
        votingStartedAt: null,
        closedAt: null,
        eligibleVoters: null,
        voters: null,
        requestId: null,
        completedAt: null,
        myBallots: [],
        candidates: seeded,
      }
      ;(elections[clubId] ??= []).unshift(e)
      return electionView(e)
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/elections\/([^/]+)\/candidacy$/,
    (m, config) => {
      const e = findElection(config, m[1]!, m[2]!, 'CANDIDACY')
      if (e.candidates.some((c) => c.studentId === 'u1')) fail(config, 409, 'ALREADY_CANDIDATE', 'Bu seçimde zaten adaysınız.')
      e.candidates.push(cand(nextId('c'), body<{ ballot: ElectionBallot }>(config).ballot, 'u1', 'Elif', 'Demir'))
      return electionView(e)
    },
  ],
  [
    'delete',
    /^\/clubs\/([^/]+)\/elections\/([^/]+)\/candidacy$/,
    (m, config) => {
      const e = findElection(config, m[1]!, m[2]!, 'CANDIDACY')
      e.candidates = e.candidates.filter((c) => c.studentId !== 'u1')
      return electionView(e)
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/elections\/([^/]+)\/start-voting$/,
    (m, config) => {
      const e = findElection(config, m[1]!, m[2]!, 'CANDIDACY')
      if (!e.candidates.some((c) => c.ballot === 'PRESIDENT'))
        fail(config, 400, 'NO_PRESIDENT_CANDIDATE', 'Başkanlık için en az bir aday olmadan oylama başlatılamaz.')
      Object.assign(e, { status: 'VOTING', votingStartedAt: now() })
      return electionView(e)
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/elections\/([^/]+)\/votes$/,
    (m, config) => {
      const e = findElection(config, m[1]!, m[2]!, 'VOTING')
      const b = body<{ ballot: ElectionBallot; candidateIds: string[] }>(config)
      if ((e.myBallots ?? []).includes(b.ballot)) fail(config, 409, 'ALREADY_VOTED', 'Bu oylamada oyunuzu zaten kullandınız.')
      e.myBallots = [...(e.myBallots ?? []), b.ballot]
      ;(myVotes[e.id] ??= []).push(...b.candidateIds)
      return electionView(e)
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/elections\/([^/]+)\/close$/,
    (m, config) => {
      const clubId = m[1]!
      const e = findElection(config, clubId, m[2]!, 'VOTING')
      const mine = myVotes[e.id] ?? []
      e.candidates.forEach((c, i) => (c.votes = (OTHERS_VOTES[i % OTHERS_VOTES.length] ?? 0) + mine.filter((x) => x === c.id).length))
      for (const ballot of ['PRESIDENT', 'BOARD', 'AUDIT'] as ElectionBallot[]) {
        const seats = ballot === 'PRESIDENT' ? 1 : ballot === 'BOARD' ? e.boardSeats : e.auditSeats
        e.candidates
          .filter((c) => c.ballot === ballot)
          .sort((a, b) => (b.votes ?? 0) - (a.votes ?? 0))
          .forEach((c, i) => (c.elected = i < seats && (c.votes ?? 0) > 0))
      }
      const voters = OTHER_VOTERS + ((e.myBallots ?? []).length ? 1 : 0)
      Object.assign(e, { status: 'AWAITING_APPROVAL', closedAt: now(), eligibleVoters: 41, voters })
      const request = submit(clubId, {
        type: 'CLUB_ELECTION',
        status: presides(clubId) ? 'PENDING_ADVISOR' : 'PENDING_PRESIDENT',
        note: `Katılım: ${voters}/41`,
      })
      e.requestId = request.id
      return request
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/elections\/([^/]+)\/cancel$/,
    (m, config) => {
      const e = findElection(config, m[1]!, m[2]!)
      if (e.status !== 'CANDIDACY' && e.status !== 'VOTING')
        fail(config, 409, 'ELECTION_LOCKED', 'Oylaması kapanmış seçim bu yolla iptal edilemez.')
      Object.assign(e, { status: 'CANCELLED', completedAt: now() })
      return electionView(e)
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/meetings$/,
    (m, config) => {
      const clubId = m[1]!
      const b = body<{
        meetingAt: string
        location: string | null
        agenda: string
        minutes: string
        attendeeIds: string[]
        decisions: string[]
        note: string | null
      }>(config)
      const y = yearOf(b.meetingAt)
      const used = (meetings[clubId] ?? []).filter((x) => x.academicYear === y).flatMap((x) => x.decisions.map((d) => d.number ?? 0))
      let next = Math.max(0, ...used)
      const id = nextId('mt')
      const publishes = presides(clubId)
      const saved: Meeting = {
        id,
        meetingAt: b.meetingAt,
        academicYear: y,
        academicYearLabel: label(y),
        location: b.location,
        agenda: b.agenda,
        minutes: b.minutes,
        attendeeIds: b.attendeeIds,
        boardSize: 2,
        quorumMet: b.attendeeIds.filter((x) => x === 'u1' || x === 'st5').length * 2 > 2,
        decisions: b.decisions.map((text, i) => {
          const n = publishes ? ++next : null
          return { id: `${id}-d${i}`, order: i + 1, text, number: n, label: n ? `${label(y)}/${n}` : null }
        }),
        preparedBy: 'u1',
        createdAt: now(),
        approvedAt: publishes ? now() : null,
        status: publishes ? 'APPROVED' : 'PENDING_PRESIDENT',
      }
      ;(meetings[clubId] ??= []).unshift(saved)
      return submit(clubId, {
        type: 'CLUB_MEETING_MINUTES',
        status: saved.status!,
        note: b.note,
        meeting: saved,
        decidedByName: publishes ? 'Elif Demir' : null,
        decidedAt: publishes ? now() : null,
      })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/finance\/budgets$/,
    (m, config) => {
      const clubId = m[1]!
      if ((budgets[clubId] ?? []).some((x) => x.status === 'PENDING_PRESIDENT' || x.status === 'PENDING_ADVISOR'))
        fail(config, 409, 'BUDGET_PENDING', 'Kulübün onay bekleyen bir bütçesi var.')
      const b = body<{
        academicYear: number
        plannedIncome: number
        plannedExpense: number
        description: string | null
        note: string | null
      }>(config)
      const saved: Budget = {
        id: nextId('bd'),
        academicYear: b.academicYear,
        academicYearLabel: label(b.academicYear),
        plannedIncome: b.plannedIncome,
        plannedExpense: b.plannedExpense,
        description: b.description,
        preparedBy: 'u1',
        createdAt: now(),
        approvedAt: null,
        status: presides(clubId) ? 'PENDING_ADVISOR' : 'PENDING_PRESIDENT',
      }
      ;(budgets[clubId] ??= []).unshift(saved)
      return submit(clubId, { type: 'CLUB_BUDGET', status: saved.status!, note: b.note, budget: saved })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/finance\/entries$/,
    (m, config) => {
      const clubId = m[1]!
      const form = config.data as FormData
      const amount = Number(form.get('amount'))
      const occurredOn = String(form.get('occurredOn'))
      const file = form.get('document') as File | null
      const y = yearOf(occurredOn)
      const type = form.get('type') as FinanceEntry['type']
      const budget = (budgets[clubId] ?? []).find((b) => b.academicYear === y && b.status === 'APPROVED')
      const spent = (entries[clubId] ?? [])
        .filter((e) => e.academicYear === y && e.type === 'EXPENSE' && e.status === 'APPROVED')
        .reduce((a, e) => a + e.amount, 0)
      const status = !presides(clubId) ? 'PENDING_PRESIDENT' : amount >= 1000 ? 'PENDING_ADVISOR' : 'APPROVED'
      const saved = entry(nextId('fe'), type, amount, String(form.get('description')), 0, {
        occurredOn,
        academicYear: y,
        createdAt: now(),
        approvedAt: status === 'APPROVED' ? now() : null,
        status,
        hasDocument: !!file,
        documentName: file?.name ?? null,
        budgetExceeded: type === 'EXPENSE' && !!budget && spent + amount > budget.plannedExpense,
      })
      ;(entries[clubId] ??= []).unshift(saved)
      return submit(clubId, {
        type: 'CLUB_FINANCE_ENTRY',
        status,
        note: (form.get('note') as string | null) ?? null,
        financeEntry: saved,
        decidedByName: status === 'APPROVED' ? 'Elif Demir' : null,
        decidedAt: status === 'APPROVED' ? now() : null,
      })
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/finance\/sponsorships$/,
    (m, config) => {
      const clubId = m[1]!
      const form = config.data as FormData
      const text = (k: string) => (form.get(k) as string | null) ?? null
      const file = form.get('document') as File | null
      const saved: Sponsorship = {
        id: nextId('sp'),
        sponsorName: text('sponsorName')!,
        description: text('description')!,
        cashAmount: text('cashAmount') ? Number(text('cashAmount')) : null,
        inKind: text('inKind'),
        startsOn: text('startsOn')!,
        endsOn: text('endsOn'),
        hasDocument: !!file,
        documentName: file?.name ?? null,
        preparedBy: 'u1',
        createdAt: now(),
        approvedAt: null,
        status: presides(clubId) ? 'PENDING_ADVISOR' : 'PENDING_PRESIDENT',
      }
      ;(sponsorships[clubId] ??= []).unshift(saved)
      return submit(clubId, { type: 'CLUB_SPONSORSHIP', status: saved.status!, note: text('note'), sponsorship: saved })
    },
  ],
  [
    'put',
    /^\/clubs\/([^/]+)\/reports\/activity\/(\d+)$/,
    (m, config) => {
      const r = draftOf(m[1]!, 'ACTIVITY', Number(m[2]), config)
      r.body = body<{ summary: string | null }>(config).summary
      return { ...r }
    },
  ],
  [
    'put',
    /^\/clubs\/([^/]+)\/reports\/activity\/(\d+)\/finance-note$/,
    (m, config) => {
      const r = draftOf(m[1]!, 'ACTIVITY', Number(m[2]), config)
      r.financeNote = body<{ financeNote: string | null }>(config).financeNote
      return { ...r }
    },
  ],
  [
    'put',
    /^\/clubs\/([^/]+)\/reports\/audit\/(\d+)$/,
    (m, config) => {
      const r = draftOf(m[1]!, 'AUDIT', Number(m[2]), config)
      const b = body<{ findings: string | null; recommendations: string | null }>(config)
      Object.assign(r, { body: b.findings, recommendations: b.recommendations })
      return { ...r }
    },
  ],
  [
    'post',
    /^\/clubs\/([^/]+)\/reports\/(activity|audit)\/(\d+)\/submit$/,
    (m, config) => {
      const clubId = m[1]!
      const type = m[2] === 'activity' ? 'ACTIVITY' : 'AUDIT'
      const y = Number(m[3])
      const r = (reports[clubId] ?? []).find((x) => x.type === type && x.academicYear === y)
      if (!r) fail(config, 404, 'REPORT_NOT_FOUND', 'Rapor taslağı bulunamadı.')
      if (r.status !== 'DRAFT') fail(config, 409, 'REPORT_LOCKED', 'Rapor zaten gönderilmiş.')
      if (!r.body) fail(config, 400, 'REPORT_INCOMPLETE', 'Rapor metni boş olamaz.')
      Object.assign(r, { status: 'SUBMITTED', submittedAt: now(), snapshot: snapshotOf(y) })
      const request = submit(clubId, {
        type: type === 'ACTIVITY' ? 'CLUB_ACTIVITY_REPORT' : 'CLUB_AUDIT_REPORT',
        status: type === 'ACTIVITY' && !presides(clubId) ? 'PENDING_PRESIDENT' : 'PENDING_ADVISOR',
        note: body<{ note: string | null }>(config).note,
        report: { ...r },
      })
      r.requestId = request.id
      return request
    },
  ],
  [
    'post',
    /^\/events\/manage\/([^/]+)\/staff$/,
    (m, config) => {
      const eventId = m[1]!
      const userId = body<{ userId: string }>(config).userId
      const list = (eventStaff[eventId] ??= [])
      if (list.some((x) => x.userId === userId)) fail(config, 409, 'STAFF_EXISTS', 'Bu üye bu etkinlik için zaten önerildi veya atandı.')
      const clubId = eventClub(eventId)
      const person = [...(fx.clubBoard[clubId] ?? []), ...(members[clubId] ?? [])].find((x) => x.studentId === userId)
      const saved = staffOf(
        eventId,
        userId,
        person?.firstName ?? 'Üye',
        person?.lastName ?? '',
        presides(clubId) ? 'APPROVED' : 'PENDING',
        0,
      )
      list.push(saved)
      return { ...saved }
    },
  ],
  [
    'post',
    /^\/events\/manage\/([^/]+)\/staff\/([^/]+)\/(approve|reject)$/,
    (m) => {
      const list = eventStaff[m[1]!] ?? []
      const i = list.findIndex((x) => x.userId === m[2])
      if (m[3] === 'reject') list.splice(i, 1)
      else Object.assign(list[i]!, { status: 'APPROVED', approvedBy: 'u1', decidedAt: now() })
      return null
    },
  ],
  [
    'delete',
    /^\/events\/manage\/([^/]+)\/staff\/([^/]+)$/,
    (m) => {
      const list = eventStaff[m[1]!] ?? []
      list.splice(
        list.findIndex((x) => x.userId === m[2]),
        1,
      )
      return null
    },
  ],
  [
    'post',
    /^\/events\/manage\/verify-qr$/,
    (_m, config) => {
      const { qrCode, eventId } = body<{ qrCode?: string; eventId?: string }>(config)
      const code = String(qrCode ?? '').trim()
      const own = fx.registrations.find((r) => r.qrCode === code)
      const target = GATE_CODES[code] ?? (own ? { eventId: own.eventId, studentId: 'u1' } : null)
      if (target && eventId && target.eventId !== eventId) fail(config, 400, 'TICKET_WRONG_EVENT', 'Bu bilet başka bir etkinliğe ait.')
      const row = target && (fx.attendance[target.eventId] ?? []).find((r) => r.studentId === target.studentId)
      if (!row) fail(config, 400, 'INVALID_TICKET', 'Geçersiz bilet: QR kod bulunamadı.')
      if (row.status === 'CANCELLED') fail(config, 400, 'TICKET_CANCELLED', 'Bu kayıt iptal edilmiş.')
      if (row.attended) fail(config, 400, 'TICKET_ALREADY_USED', 'Bu bilet daha önce kullanılmış.')
      Object.assign(row, { attended: true, checkedInAt: now(), method: 'QR' })
      return {
        eventId: target!.eventId,
        studentId: row.studentId,
        firstName: row.firstName,
        lastName: row.lastName,
        studentNumber: row.studentNumber,
        checkedInAt: row.checkedInAt,
      }
    },
  ],
]
