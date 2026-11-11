/**
 * Akademisyen önizlemesi, üçüncü parça: danışmanlık. Mehmet Kaya Robotik Kulübü'nün ve başkanı olmayan
 * Matematik Topluluğu'nun danışmanı; bir kuruluş başvurusu, bir danışmanlık teklifi ve görev değişiklikleri bekliyor.
 * Bellekte tutulur; yalnız `/onizleme?rol=akademisyen` içindir.
 */
import type { InternalAxiosRequestConfig } from 'axios'
import { localStamp, nowLocalIso } from '@/lib/time'
import type { AdvisorOffer, ClubCreationRequest, RoleChangeRequest } from '@/features/clubs/advise'
import type { ClubDetails, ClubMember, ClubStatus } from '@/features/clubs/api'
import type { ApprovalRequest, ClubAccess } from '@/features/clubs/manage'
import type { CampusEvent } from '@/features/events/api'
import * as fx from './fixtures'
import { ME_ID } from './teachPreview'

type Handler = (m: RegExpMatchArray, config: InternalAxiosRequestConfig) => unknown
type ClubAdvisorClub = { status: ClubStatus; members: ClubMember[]; closedAt?: string | null; closureReason?: string | null }

const ago = (hours: number) => new Date(Date.now() - hours * 3_600_000).toISOString()
function at(days: number, hh: number, mm = 0): string {
  const d = new Date(localStamp(`${nowLocalIso().slice(0, 10)}T00:00:00`) + days * 86_400_000)
  return `${d.toISOString().slice(0, 10)}T${String(hh).padStart(2, '0')}:${String(mm).padStart(2, '0')}:00`
}
function fail(config: InternalAxiosRequestConfig, status: number, errorCode: string, message: string): never {
  throw Object.assign(new Error(message), {
    isAxiosError: true,
    config,
    response: { status, statusText: '', headers: {}, config, data: { status, errorCode, message } },
  })
}
const body = <T>(config: InternalAxiosRequestConfig) => (config.data ? (JSON.parse(String(config.data)) as T) : ({} as T))

const ADVISE_PERMISSIONS: ClubAccess['permissions'] = ['VIEW_MEMBERS', 'VIEW_MANAGEMENT_DATA', 'VIEW_DECISIONS', 'ADVISE']

const member = (studentId: string, firstName: string, lastName: string, role = 'MEMBER'): ClubMember => ({
  studentId,
  firstName,
  lastName,
  role,
  isActive: true,
})

/** Danışmanı olunan kulüpler: ayrıntı ve üyeler (danışman bütün üyeleri adıyla görür). */
export const advised: Record<string, ClubAdvisorClub> = {
  k1: {
    status: 'ACTIVE',
    members: [
      member('st2', 'Can', 'Erdem', 'PRESIDENT'),
      member('st4', 'Mert', 'Aksu', 'VICE_PRESIDENT'),
      member('u1', 'Elif', 'Demir', 'EVENT_COORDINATOR'),
      member('st3', 'Ece', 'Yalçın'),
      member('st10', 'Onur', 'Polat'),
      member('st11', 'Gizem', 'Kurt'),
    ],
  },
  k9: {
    status: 'ACTIVE',
    members: [
      member('st12', 'Yiğit', 'Özkan', 'GENERAL_SECRETARY'),
      member('st13', 'İrem', 'Tunç'),
      member('st14', 'Furkan', 'Şahin'),
      member('st15', 'Melis', 'Koç'),
    ],
  },
}

const detail = (id: string): ClubDetails => {
  const base = fx.clubDetail(id)!
  const a = advised[id]!
  return {
    ...base,
    academicAdvisorId: ME_ID,
    advisorName: 'Mehmet Kaya',
    advisorTitle: 'Dr. Öğr. Üyesi',
    members: a.members.map((m) => ({ ...m })),
    status: a.status,
    closedAt: a.closedAt ?? null,
    closureReason: a.closureReason ?? null,
  }
}

const access = (clubId: string): ClubAccess => {
  const mine = !!advised[clubId] && advised[clubId]!.status !== 'CLOSED'
  return {
    clubId,
    userId: ME_ID,
    position: null,
    member: false,
    actingPresident: false,
    advisor: mine,
    permissions: mine ? ADVISE_PERMISSIONS : [],
    clubName: fx.clubs.find((c) => c.id === clubId)?.name ?? null,
  }
}

/** Danışman onayı bekleyen kulüp kaydı: Robotik Kulübü'nün profil güncellemesi. */
const inbox: ApprovalRequest[] = [
  {
    id: 'ap-k1-adv',
    clubId: 'k1',
    clubName: 'Robotik Kulübü',
    type: 'CLUB_PROFILE_UPDATE',
    status: 'PENDING_ADVISOR',
    preparedBy: 'st2',
    preparedByName: 'Can Erdem',
    subjectUserId: null,
    subjectUserName: null,
    currentPosition: null,
    requestedPosition: null,
    note: 'Laboratuvar adresi ve web sitesi güncellendi.',
    rejectionReason: null,
    responseNote: null,
    presidentDecidedByName: 'Can Erdem',
    presidentDecidedAt: ago(20),
    decidedByName: null,
    decidedAt: null,
    profileChange: {
      about: null,
      category: null,
      contactEmail: null,
      websiteUrl: 'https://robotik.ornek.edu.tr',
      instagramUrl: null,
      xUrl: null,
      linkedinUrl: null,
      logoUrl: null,
    },
    announcement: null,
    createdAt: ago(26),
  },
]

const eventQueue: CampusEvent[] = [
  {
    ...fx.managedEvents[0]!,
    id: 'e-k1-adv',
    title: 'Çizgi izleyen robot yarışması',
    description: 'Kulüp içi yarışma; takımlar en fazla üç kişi.',
    startsAt: at(18, 14),
    endsAt: at(18, 18),
    location: 'B Blok 204',
    clubId: 'k1',
    clubName: 'Robotik Kulübü',
    status: 'PENDING',
  },
]

export const creationRequests: ClubCreationRequest[] = [
  {
    id: 'cr1',
    clubName: 'Siber Güvenlik Topluluğu',
    about:
      'CTF yarışmalarına hazırlanmak, güvenli yazılım geliştirme atölyeleri ve sektörden konuşmacılarla söyleşiler düzenlemek istiyoruz.',
    requestingStudentId: 'st20',
    suggestedAdvisorId: ME_ID,
    status: 'PENDING',
    requestDate: ago(50),
    rejectionReason: null,
    processedAt: null,
    clubId: null,
    founders: [
      { studentId: 'st20', firstName: 'Kaan', lastName: 'Tekin', status: 'CONFIRMED', respondedAt: ago(50) },
      { studentId: 'st21', firstName: 'Derya', lastName: 'Bulut', status: 'CONFIRMED', respondedAt: ago(48) },
      { studentId: 'st22', firstName: 'Umut', lastName: 'Karaca', status: 'CONFIRMED', respondedAt: ago(45) },
    ],
  },
]

export const offers: AdvisorOffer[] = [
  {
    id: 'of1',
    clubId: 'k6',
    clubName: 'Gönüllüler Topluluğu',
    proposedAdvisorId: ME_ID,
    requestedBy: 'st30',
    message: 'Danışmanımız emekliye ayrıldı. Dönem boyunca iki kampüs içi bağış kampanyası planlıyoruz; danışmanımız olmanızı çok isteriz.',
    status: 'PENDING_ADVISOR',
    rejectionReason: null,
    createdAt: ago(30),
    decidedAt: null,
  },
]

export const roleChanges: RoleChangeRequest[] = [
  {
    id: 'rc1',
    clubId: 'k1',
    clubName: 'Robotik Kulübü',
    studentId: 'st3',
    studentName: 'Ece Yalçın',
    currentRole: 'ROLE_MEMBER',
    requestedRole: 'ROLE_TREASURER',
    requesterId: 'st2',
    requesterName: 'Can Erdem',
    status: 'PENDING',
    rejectionReason: null,
    createdAt: ago(8),
    processedAt: null,
  },
  {
    id: 'rc2',
    clubId: 'k1',
    clubName: 'Robotik Kulübü',
    studentId: 'st10',
    studentName: 'Onur Polat',
    currentRole: 'ROLE_MEMBER',
    requestedRole: 'ROLE_COMMUNICATIONS_OFFICER',
    requesterId: 'st2',
    requesterName: 'Can Erdem',
    status: 'PENDING',
    rejectionReason: null,
    createdAt: ago(30),
    processedAt: null,
  },
]

const decide = <T extends { status: string; rejectionReason: string | null }>(
  list: T[],
  id: string,
  status: string,
  config: InternalAxiosRequestConfig,
) => {
  const r = list.find((x) => (x as unknown as { id: string }).id === id)!
  Object.assign(r, {
    status,
    rejectionReason: status === 'REJECTED' ? (body<{ rejectionReason?: string | null }>(config).rejectionReason ?? null) : null,
  })
  return { ...r }
}

export const GET: [RegExp, Handler][] = [
  [
    /^\/clubs\/my-access$/,
    () =>
      Object.keys(advised)
        .filter((id) => advised[id]!.status !== 'CLOSED')
        .map(access),
  ],
  [/^\/clubs\/approvals\/inbox$/, () => inbox.filter((r) => r.status === 'PENDING_ADVISOR').map((r) => ({ ...r }))],
  [/^\/events\/advisor\/pending$/, () => eventQueue.filter((e) => e.status === 'PENDING').map((e) => ({ ...e }))],
  [/^\/events\/president\/pending$/, () => []],
  [/^\/academician\/club-creation-requests$/, () => creationRequests.map((r) => ({ ...r }))],
  [/^\/academician\/advisor-change-requests$/, () => offers.filter((o) => o.status.startsWith('PENDING')).map((o) => ({ ...o }))],
  [/^\/academician\/role-change-requests$/, () => roleChanges.filter((r) => r.status === 'PENDING').map((r) => ({ ...r }))],
  [/^\/clubs\/([^/]+)\/my-access$/, (m) => access(m[1]!)],
  [/^\/clubs\/([^/]+)\/approvals$/, (m) => inbox.filter((r) => r.clubId === m[1]).map((r) => ({ ...r }))],
  [/^\/clubs\/([^/]+)\/membership-requests\/pending$/, () => []],
  [
    /^\/clubs\/([^/]+)$/,
    (m, config) => (advised[m[1]!] ? detail(m[1]!) : (fx.clubDetail(m[1]!) ?? fail(config, 404, 'CLUB_NOT_FOUND', 'Kulüp bulunamadı'))),
  ],
]

export const WRITE: [string, RegExp, Handler][] = [
  ['put', /^\/academician\/club-creation-requests\/([^/]+)\/approve$/, (m, config) => decide(creationRequests, m[1]!, 'APPROVED', config)],
  ['put', /^\/academician\/club-creation-requests\/([^/]+)\/reject$/, (m, config) => decide(creationRequests, m[1]!, 'REJECTED', config)],
  [
    'put',
    /^\/academician\/advisor-change-requests\/([^/]+)\/accept$/,
    (m, config) => {
      const o = decide(offers, m[1]!, 'APPROVED', config)
      advised[o.clubId] = { status: 'ACTIVE', members: [member('st30', 'Nazlı', 'Güneş', 'PRESIDENT'), member('st31', 'Tolga', 'Yıldız')] }
      return o
    },
  ],
  ['put', /^\/academician\/advisor-change-requests\/([^/]+)\/reject$/, (m, config) => decide(offers, m[1]!, 'REJECTED', config)],
  [
    'put',
    /^\/academician\/role-change-requests\/([^/]+)\/approve$/,
    (m, config) => {
      const r = decide(roleChanges, m[1]!, 'APPROVED', config)
      const mem = advised[r.clubId]?.members.find((x) => x.studentId === r.studentId)
      if (mem) mem.role = r.requestedRole
      return r
    },
  ],
  ['put', /^\/academician\/role-change-requests\/([^/]+)\/reject$/, (m, config) => decide(roleChanges, m[1]!, 'REJECTED', config)],
  [
    'put',
    /^\/academician\/clubs\/([^/]+)\/president$/,
    (m, config) => {
      const club = advised[m[1]!]!
      if (club.members.some((x) => x.role === 'PRESIDENT')) fail(config, 409, 'PRESIDENT_EXISTS', 'Kulübün zaten bir başkanı var.')
      const mem = club.members.find((x) => x.studentId === body<{ studentId: string }>(config).studentId)!
      mem.role = 'PRESIDENT'
      return { ...mem }
    },
  ],
  [
    'delete',
    /^\/academician\/clubs\/([^/]+)\/president$/,
    (m) => {
      const club = advised[m[1]!]!
      const president = club.members.find((x) => x.role === 'PRESIDENT')
      const vice = club.members.find((x) => x.role === 'VICE_PRESIDENT')
      if (president) president.role = 'MEMBER'
      if (vice) vice.role = 'PRESIDENT'
      return 'Başkan görevden alındı'
    },
  ],
  [
    'post',
    /^\/academician\/clubs\/([^/]+)\/close$/,
    (m, config) => {
      Object.assign(advised[m[1]!]!, {
        status: 'CLOSED',
        closedAt: new Date().toISOString(),
        closureReason: body<{ reason: string }>(config).reason,
      })
      return detail(m[1]!)
    },
  ],
  [
    'post',
    /^\/academician\/clubs\/([^/]+)\/advisor\/resign$/,
    (m) => {
      delete advised[m[1]!]
      return null
    },
  ],
]
