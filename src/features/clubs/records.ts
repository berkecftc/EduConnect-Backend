import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { useClub, type ClubMember, type ClubRole } from './api'
import type { ApprovalRequest, ApprovalStatus, ApprovalType } from './manage'

/**
 * Kulüp kayıtları (F-85): toplantı tutanakları ve karar defteri, mali işler (bütçe, gelir-gider, sponsorluk),
 * dönem raporları (faaliyet, denetim), karar günlüğü ve görev geçmişi. Yazma işleri onay kaydı açar.
 * Akademik yıl bitiş yılıyla anılır: 2027 = "2026-2027"; yılın bittiği gün sunucu ayarı (F-91).
 */

// ——— Akademik yıl ———

/** Kulüp yanıtı henüz gelmemişken kullanılan, sunucunun varsayılanı olan bitiş günü (30 Eylül). */
const DEFAULT_YEAR_END = '0000-09-30'

/**
 * Günün akademik yılı: yılın bitiş gününden sonrası bir sonraki yıla sayılır. `day` yerel takvim günü (YYYY-MM-DD),
 * `yearEnd` kulüp ya da mali özet yanıtındaki `academicYearEnd`.
 */
export function academicYearOf(day: string, yearEnd?: string | null): number {
  const [y, m, d] = day.split('-').map(Number) as [number, number, number]
  const [, endMonth, endDay] = (yearEnd ?? DEFAULT_YEAR_END).split('-').map(Number) as [number, number, number]
  return m > endMonth || (m === endMonth && d > endDay) ? y + 1 : y
}

/** Kulübün yıl hesabı: verilen günün akademik yılı, kulübün bildirdiği bitiş gününe göre. */
export function useAcademicYearOf(clubId: string) {
  const yearEnd = useClub(clubId).data?.academicYearEnd
  return (day: string) => academicYearOf(day, yearEnd)
}

export const yearLabel = (year: number) => `${year - 1}-${year}`

/** Seçilebilir yıllar: içinde bulunulan ve önceki `back` yıl, yeniden eskiye. */
export const recentYears = (current: number, back = 3) => Array.from({ length: back + 1 }, (_, i) => current - i)

// ——— Para ———

const money = new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** Türkçe tutar: 3.200,00 ₺ (simge sonda). */
export const formatMoney = (value: number | null | undefined) => (value == null ? '–' : `${money.format(value)} ₺`)

/**
 * Tutar girişi: "1.250,50", "1250,5" ya da "1250.50". En çok iki ondalık; geçersizse null.
 * Virgül varsa ya da noktalar üçlü grupları ayırıyorsa ("3.200", "1.250.000") nokta binlik ayracı sayılır.
 */
export function parseMoney(input: string): number | null {
  let s = input.trim().replace(/\s|₺|TL/gi, '')
  if (!s) return null
  if (s.includes(',') || /^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, '').replace(',', '.')
  if (!/^\d+(\.\d{1,2})?$/.test(s)) return null
  return Number(s)
}

export const DOCUMENT_TYPES = ['application/pdf', 'image/jpeg', 'image/png', 'image/gif', 'image/webp']
export const DOCUMENT_MAX_BYTES = 10 * 1024 * 1024

export function documentProblem(file: File): string | null {
  if (!DOCUMENT_TYPES.includes(file.type)) return 'Belge PDF, JPEG, PNG, GIF ya da WEBP olmalı.'
  if (file.size > DOCUMENT_MAX_BYTES) return 'Belge en fazla 10 MB olabilir.'
  return null
}

// ——— Tipler ———

export type MeetingDecision = { id: string; order: number; text: string; number: number | null; label: string | null }

export type Meeting = {
  id: string
  meetingAt: string
  academicYear: number
  academicYearLabel: string
  location: string | null
  agenda: string
  minutes: string
  attendeeIds: string[]
  /** Toplantı günkü katılanlar; görevden ayrılanların da adı gelir. */
  attendees?: { id: string; name: string | null }[]
  boardSize: number
  quorumMet: boolean
  decisions: MeetingDecision[]
  preparedBy: string
  preparedByName?: string | null
  createdAt: string
  approvedAt: string | null
  status: ApprovalStatus | null
}

export type DecisionBookEntry = {
  number: number
  label: string
  text: string
  meetingId: string
  meetingAt: string
  approvedAt: string | null
}

export type Budget = {
  id: string
  academicYear: number
  academicYearLabel: string
  plannedIncome: number
  plannedExpense: number
  description: string | null
  preparedBy: string
  createdAt: string
  approvedAt: string | null
  status: ApprovalStatus | null
}

export type FinanceEntryType = 'INCOME' | 'EXPENSE'

export type FinanceEntry = {
  id: string
  type: FinanceEntryType
  amount: number
  description: string
  occurredOn: string
  academicYear: number
  hasDocument: boolean
  documentName: string | null
  budgetExceeded: boolean
  sponsorshipId: string | null
  preparedBy: string
  preparedByName?: string | null
  createdAt: string
  approvedAt: string | null
  status: ApprovalStatus | null
}

export type Sponsorship = {
  id: string
  sponsorName: string
  description: string
  cashAmount: number | null
  inKind: string | null
  startsOn: string
  endsOn: string | null
  hasDocument: boolean
  documentName: string | null
  preparedBy: string
  createdAt: string
  approvedAt: string | null
  status: ApprovalStatus | null
}

export type FinanceSummary = {
  academicYear: number
  academicYearLabel: string
  budget: Budget | null
  income: number
  expense: number
  balance: number
  overallBalance: number
  /** Bu tutarın üstündeki kayıtlar danışman onayına da gider. */
  advisorThreshold?: number | null
  academicYearEnd?: string | null
}

export type ReportType = 'ACTIVITY' | 'AUDIT'
export type ReportStatus = 'DRAFT' | 'SUBMITTED' | 'APPROVED'

export type ReportSnapshot = {
  activeMembers: number | null
  endedMemberships: number | null
  announcements: number | null
  meetings: number | null
  decisions: number | null
  plannedIncome: number | null
  plannedExpense: number | null
  income: number | null
  expense: number | null
  balance: number | null
  events: number | null
  completedEvents: number | null
  cancelledEvents: number | null
  registrations: number | null
  attendances: number | null
}

export type Report = {
  id: string
  clubId: string
  type: ReportType
  academicYear: number
  academicYearLabel: string
  status: ReportStatus
  /** Faaliyet raporunda özet, denetim raporunda bulgular. */
  body: string | null
  financeNote: string | null
  recommendations: string | null
  snapshot: ReportSnapshot | null
  requestId: string | null
  createdAt: string
  updatedAt: string | null
  submittedAt: string | null
  approvedAt: string | null
}

export type DecisionAction = string

export type DecisionLogEntry = {
  id: string
  requestId: string | null
  requestType: ApprovalType | null
  action: DecisionAction
  actorId: string | null
  subjectUserId: string | null
  actorName?: string | null
  subjectUserName?: string | null
  detail: string | null
  createdAt: string
}

export type PositionTerm = {
  clubId: string
  clubName: string | null
  studentId: string
  firstName: string | null
  lastName: string | null
  position: ClubRole
  positionName: string
  startedAt: string
  endedAt: string | null
  endReason: string | null
}

export const REPORT_STATUS: Record<ReportStatus, { label: string; tone: 'neutral' | 'warning' | 'success' }> = {
  DRAFT: { label: 'Taslak', tone: 'neutral' },
  SUBMITTED: { label: 'Onayda', tone: 'warning' },
  APPROVED: { label: 'Onaylandı', tone: 'success' },
}

export const DECISION_ACTION_LABEL: Record<string, string> = {
  SUBMITTED: 'Gönderildi',
  PRESIDENT_APPROVED: 'Başkan onayladı',
  APPROVED: 'Onaylandı',
  REJECTED: 'Reddedildi',
  WITHDRAWN: 'Geri çekildi',
  PRESIDENT_REMOVED: 'Başkan görevden alındı',
  PRESIDENT_APPOINTED: 'Başkan atandı',
  VICE_PRESIDENT_PROMOTED: 'Başkan yardımcısı başkan oldu',
  PRESIDENCY_VACANT: 'Başkanlık boşaldı',
  ADVISOR_RESIGNED: 'Danışman görevi bıraktı',
  ADVISOR_LEFT: 'Danışmanlık sona erdi',
  CLUB_FOUNDED: 'Kulüp kuruldu',
  CLUB_CLOSED: 'Kulüp kapatıldı',
  MEMBERSHIP_REVIEWED: 'Üyelik başvurusuna öneri verildi',
  MEMBER_LEFT: 'Üye ayrıldı',
  MEMBERSHIP_RENEWED: 'Üyelik yenilendi',
  DEFENCE_SUBMITTED: 'Savunma eklendi',
  MEMBERSHIP_APPROVED: 'Üyelik başvurusu kabul edildi',
  MEMBERSHIP_REJECTED: 'Üyelik başvurusu reddedildi',
  ANNOUNCEMENT_REMOVED: 'Duyuru yayından kaldırıldı',
  ELECTION_OPENED: 'Seçim açıldı',
  ELECTION_VOTING_STARTED: 'Oylama başladı',
  ELECTION_CANCELLED: 'Seçim iptal edildi',
  BOARD_HANDOVER: 'Görevler seçimle devredildi',
  MEMBERSHIP_FROZEN: 'Üyelik donduruldu',
  MEMBERSHIP_RESUMED: 'Üyelik yeniden başladı',
  MEMBERSHIP_ENDED_BY_STATUS: 'Öğrencilik bittiği için üyelik sona erdi',
}

/** Yönetim kurulu (yeter sayı için): başkan, başkan yardımcısı, genel sekreter, sayman, yönetim kurulu üyeleri. */
const BOARD = new Set([
  'PRESIDENT',
  'ROLE_CLUB_OFFICIAL',
  'VICE_PRESIDENT',
  'ROLE_VICE_PRESIDENT',
  'GENERAL_SECRETARY',
  'ROLE_SECRETARY',
  'TREASURER',
  'ROLE_TREASURER',
  'BOARD_MEMBER',
  'ROLE_BOARD_MEMBER',
])

export const isBoardRole = (role: ClubRole) => BOARD.has(role)

/** Yeter sayı: katılan yönetim kurulu üyesi kurulun yarısından fazla. */
export function quorum(board: ClubMember[], attendees: string[]) {
  const members = board.filter((m) => isBoardRole(m.role))
  const present = members.filter((m) => attendees.includes(m.studentId)).length
  return { present, size: members.length, met: present * 2 > members.length }
}

// ——— Sorgular ———

export const recordKeys = {
  meetings: (clubId: string, year: number) => ['clubs', clubId, 'meetings', year] as const,
  book: (clubId: string, year: number) => ['clubs', clubId, 'decision-book', year] as const,
  summary: (clubId: string, year: number) => ['clubs', clubId, 'finance', 'summary', year] as const,
  entries: (clubId: string, year: number) => ['clubs', clubId, 'finance', 'entries', year] as const,
  budgets: (clubId: string) => ['clubs', clubId, 'finance', 'budgets'] as const,
  sponsorships: (clubId: string) => ['clubs', clubId, 'finance', 'sponsorships'] as const,
  reports: (clubId: string, year: number) => ['clubs', clubId, 'reports', year] as const,
  preview: (clubId: string, year: number) => ['clubs', clubId, 'reports', 'preview', year] as const,
  log: (clubId: string) => ['clubs', clubId, 'decision-log'] as const,
  history: (clubId: string) => ['clubs', clubId, 'position-history'] as const,
  presidents: (clubId: string) => ['clubs', clubId, 'past-presidents'] as const,
}

const get = async <T>(url: string, params?: Record<string, unknown>) => (await api.get<T>(url, { params })).data

export const useMeetings = (clubId: string, year: number, enabled = true) =>
  useQuery({
    queryKey: recordKeys.meetings(clubId, year),
    enabled,
    queryFn: () => get<Meeting[]>(`/clubs/${clubId}/meetings`, { academicYear: year }),
  })

export const useDecisionBook = (clubId: string, year: number, enabled = true) =>
  useQuery({
    queryKey: recordKeys.book(clubId, year),
    enabled,
    queryFn: () => get<DecisionBookEntry[]>(`/clubs/${clubId}/decision-book`, { academicYear: year }),
  })

export const useFinanceSummary = (clubId: string, year: number, enabled = true) =>
  useQuery({
    queryKey: recordKeys.summary(clubId, year),
    enabled,
    queryFn: () => get<FinanceSummary>(`/clubs/${clubId}/finance/summary`, { academicYear: year }),
  })

export const useFinanceEntries = (clubId: string, year: number, enabled = true) =>
  useQuery({
    queryKey: recordKeys.entries(clubId, year),
    enabled,
    queryFn: () => get<FinanceEntry[]>(`/clubs/${clubId}/finance/entries`, { academicYear: year }),
  })

export const useBudgets = (clubId: string, enabled = true) =>
  useQuery({ queryKey: recordKeys.budgets(clubId), enabled, queryFn: () => get<Budget[]>(`/clubs/${clubId}/finance/budgets`) })

export const useSponsorships = (clubId: string, enabled = true) =>
  useQuery({
    queryKey: recordKeys.sponsorships(clubId),
    enabled,
    queryFn: () => get<Sponsorship[]>(`/clubs/${clubId}/finance/sponsorships`),
  })

export const useReports = (clubId: string, year: number, enabled = true) =>
  useQuery({
    queryKey: recordKeys.reports(clubId, year),
    enabled,
    queryFn: () => get<Report[]>(`/clubs/${clubId}/reports`, { academicYear: year }),
  })

/** Raporun gönderilmeden önceki canlı rakamları (bu ve geçen yıl için). */
export const useReportPreview = (clubId: string, year: number, enabled = true) =>
  useQuery({
    queryKey: recordKeys.preview(clubId, year),
    enabled,
    retry: false,
    queryFn: () => get<ReportSnapshot>(`/clubs/${clubId}/reports/preview/${year}`),
  })

export const useDecisionLog = (clubId: string, enabled = true) =>
  useQuery({ queryKey: recordKeys.log(clubId), enabled, queryFn: () => get<DecisionLogEntry[]>(`/clubs/${clubId}/decision-log`) })

export const usePositionHistory = (clubId: string, enabled = true) =>
  useQuery({
    queryKey: recordKeys.history(clubId),
    enabled,
    queryFn: () => get<PositionTerm[]>(`/clubs/${clubId}/position-history`),
  })

/** Önceki başkanlar (herkese açık). */
export const usePastPresidents = (clubId: string) =>
  useQuery({ queryKey: recordKeys.presidents(clubId), queryFn: () => get<ClubMember[]>(`/clubs/${clubId}/past-presidents`) })

// ——— Yazma ———

/** Kulübün kayıtları ve onay kutusu tazelenir. */
function useRecordsRefresh(clubId: string) {
  const qc = useQueryClient()
  return () => {
    void qc.invalidateQueries({ queryKey: ['clubs', clubId] })
    void qc.invalidateQueries({ queryKey: ['clubs', 'approvals'] })
  }
}

export type MeetingDraft = {
  meetingAt: string
  location: string
  agenda: string
  minutes: string
  attendeeIds: string[]
  decisions: string[]
  note: string
}

export type MeetingDraftErrors = Partial<Record<'meetingAt' | 'agenda' | 'minutes' | 'attendeeIds' | 'decisions', string>>

/** `now` yerel ISO (YYYY-MM-DDTHH:mm). */
export function validateMeetingDraft(d: MeetingDraft, now: string): MeetingDraftErrors {
  const e: MeetingDraftErrors = {}
  if (!d.meetingAt) e.meetingAt = 'Toplantının tarihini ve saatini seçin.'
  else if (d.meetingAt.slice(0, 16) > now.slice(0, 16))
    e.meetingAt = 'Tutanak yapılmış bir toplantı için yazılır; ileri bir tarih seçilemez.'
  if (!d.agenda.trim()) e.agenda = 'Gündemi yazın.'
  if (!d.minutes.trim()) e.minutes = 'Tutanağı yazın.'
  if (d.attendeeIds.length === 0) e.attendeeIds = 'Toplantıya katılanları seçin.'
  if (d.decisions.filter((x) => x.trim()).length > 30) e.decisions = 'En çok 30 karar yazılabilir.'
  return e
}

export function useSubmitMeeting(clubId: string) {
  const refresh = useRecordsRefresh(clubId)
  return useMutation({
    mutationFn: async (d: MeetingDraft) =>
      (
        await api.post<ApprovalRequest>(`/clubs/${clubId}/meetings`, {
          meetingAt: d.meetingAt.length === 16 ? `${d.meetingAt}:00` : d.meetingAt,
          location: d.location.trim() || null,
          agenda: d.agenda.trim(),
          minutes: d.minutes.trim(),
          attendeeIds: d.attendeeIds,
          decisions: d.decisions.map((x) => x.trim()).filter(Boolean),
          note: d.note.trim() || null,
        })
      ).data,
    onSuccess: refresh,
  })
}

export function useSubmitBudget(clubId: string) {
  const refresh = useRecordsRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { academicYear: number; plannedIncome: number; plannedExpense: number; description: string; note: string }) =>
      (
        await api.post<ApprovalRequest>(`/clubs/${clubId}/finance/budgets`, {
          ...v,
          description: v.description.trim() || null,
          note: v.note.trim() || null,
        })
      ).data,
    onSuccess: refresh,
  })
}

/** Çok parçalı form: alanlar düz değer, belge isteğe bağlı (`document`). */
function formOf(fields: Record<string, string | number | null | undefined>, document: File | null) {
  const form = new FormData()
  for (const [k, v] of Object.entries(fields)) if (v !== null && v !== undefined && v !== '') form.append(k, String(v))
  if (document) form.append('document', document)
  return form
}

export function useSubmitEntry(clubId: string) {
  const refresh = useRecordsRefresh(clubId)
  return useMutation({
    mutationFn: async (v: {
      type: FinanceEntryType
      amount: number
      description: string
      occurredOn: string
      note: string
      document: File | null
    }) =>
      (
        await api.post<ApprovalRequest>(
          `/clubs/${clubId}/finance/entries`,
          formOf(
            { type: v.type, amount: v.amount, description: v.description.trim(), occurredOn: v.occurredOn, note: v.note.trim() },
            v.document,
          ),
        )
      ).data,
    onSuccess: refresh,
  })
}

export function useSubmitSponsorship(clubId: string) {
  const refresh = useRecordsRefresh(clubId)
  return useMutation({
    mutationFn: async (v: {
      sponsorName: string
      description: string
      cashAmount: number | null
      inKind: string
      startsOn: string
      endsOn: string
      note: string
      document: File | null
    }) =>
      (
        await api.post<ApprovalRequest>(
          `/clubs/${clubId}/finance/sponsorships`,
          formOf(
            {
              sponsorName: v.sponsorName.trim(),
              description: v.description.trim(),
              cashAmount: v.cashAmount,
              inKind: v.inKind.trim(),
              startsOn: v.startsOn,
              endsOn: v.endsOn,
              note: v.note.trim(),
            },
            v.document,
          ),
        )
      ).data,
    onSuccess: refresh,
  })
}

export type ReportWrite =
  | { kind: 'activity'; summary: string }
  | { kind: 'financeNote'; financeNote: string }
  | { kind: 'audit'; findings: string; recommendations: string }

/** Rapor taslağını yazar (yoksa açar). Boş metin null gider. */
export function useWriteReport(clubId: string, year: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (w: ReportWrite) => {
      const base = `/clubs/${clubId}/reports`
      const t = (s: string) => s.trim() || null
      if (w.kind === 'activity') return (await api.put<Report>(`${base}/activity/${year}`, { summary: t(w.summary) })).data
      if (w.kind === 'financeNote')
        return (await api.put<Report>(`${base}/activity/${year}/finance-note`, { financeNote: t(w.financeNote) })).data
      return (await api.put<Report>(`${base}/audit/${year}`, { findings: t(w.findings), recommendations: t(w.recommendations) })).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: recordKeys.reports(clubId, year) }),
  })
}

export function useSubmitReport(clubId: string, year: number) {
  const refresh = useRecordsRefresh(clubId)
  return useMutation({
    mutationFn: async (v: { type: ReportType; note: string }) =>
      (await api.post<ApprovalRequest>(`/clubs/${clubId}/reports/${v.type.toLowerCase()}/${year}/submit`, { note: v.note.trim() || null }))
        .data,
    onSuccess: refresh,
  })
}
