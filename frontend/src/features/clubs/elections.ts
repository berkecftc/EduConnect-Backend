import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { ApprovalRequest } from './manage'

/**
 * Genel kurul seçimi (F-85): genel sekreter ya da başkan seçimi açar; üyeler başkanlık, yönetim kurulu ya da
 * denetim kurulu için aday olur (bir seçimde tek adaylık), oylamada her kurul için koltuk sayısı kadar adaya oy verir.
 * Oylama kapanınca oylar sayılır ve sonuç onaya gider; onaylanınca mevcut bütün görevler sona erer, seçilenler göreve başlar.
 */

export type ElectionStatus = 'CANDIDACY' | 'VOTING' | 'AWAITING_APPROVAL' | 'COMPLETED' | 'CANCELLED'
export type ElectionBallot = 'PRESIDENT' | 'BOARD' | 'AUDIT'

export type ElectionCandidate = {
  /** Aday kaydının kimliği; oy bu kimlikle verilir. */
  id: string
  ballot: ElectionBallot
  studentId: string
  firstName: string | null
  lastName: string | null
  /** Oylama kapanana kadar null. */
  votes: number | null
  elected: boolean
}

export type Election = {
  id: string
  clubId: string
  status: ElectionStatus
  boardSeats: number
  auditSeats: number
  note: string | null
  openedBy: string
  openedByName?: string | null
  openedAt: string
  votingStartedAt: string | null
  closedAt: string | null
  eligibleVoters: number | null
  voters: number | null
  /** Sonucun onay kaydı (oylama kapanınca). */
  requestId: string | null
  completedAt: string | null
  candidates: ElectionCandidate[]
  /** Oy kullandığım kurullar. */
  myBallots: ElectionBallot[] | null
}

export const BALLOTS: { code: ElectionBallot; label: string; position: string }[] = [
  { code: 'PRESIDENT', label: 'Başkanlık', position: 'Başkan' },
  { code: 'BOARD', label: 'Yönetim kurulu', position: 'Yönetim Kurulu Üyesi' },
  { code: 'AUDIT', label: 'Denetim kurulu', position: 'Denetim Kurulu Üyesi' },
]

export const ELECTION_STATUS: Record<ElectionStatus, { label: string; tone: 'warning' | 'success' | 'neutral' | 'info' }> = {
  CANDIDACY: { label: 'Aday başvurusu', tone: 'info' },
  VOTING: { label: 'Oylama sürüyor', tone: 'warning' },
  AWAITING_APPROVAL: { label: 'Sonuç onayda', tone: 'warning' },
  COMPLETED: { label: 'Tamamlandı', tone: 'success' },
  CANCELLED: { label: 'İptal edildi', tone: 'neutral' },
}

export const BOARD_SEATS_MAX = 6
export const AUDIT_SEATS_MAX = 3
export const ELECTION_NOTE_MAX = 1000

export const seatsOf = (e: Pick<Election, 'boardSeats' | 'auditSeats'>, ballot: ElectionBallot) =>
  ballot === 'PRESIDENT' ? 1 : ballot === 'BOARD' ? e.boardSeats : e.auditSeats

/** Koltuğu olan kurullar (başkanlık her zaman). */
export const ballotsOf = (e: Pick<Election, 'boardSeats' | 'auditSeats'>) => BALLOTS.filter((b) => seatsOf(e, b.code) > 0)

export const isRunning = (s: ElectionStatus) => s === 'CANDIDACY' || s === 'VOTING' || s === 'AWAITING_APPROVAL'

export const candidateName = (c: Pick<ElectionCandidate, 'firstName' | 'lastName'>) =>
  [c.firstName, c.lastName].filter(Boolean).join(' ') || 'Üye'

/** Kurulun adayları: sayımdan sonra oya göre (eşitlikte başvuru sırası korunur), öncesinde başvuru sırasıyla. */
export function candidatesOf(e: Election, ballot: ElectionBallot): ElectionCandidate[] {
  const list = e.candidates.filter((c) => c.ballot === ballot)
  return list.some((c) => c.votes != null) ? [...list].sort((a, b) => (b.votes ?? 0) - (a.votes ?? 0)) : list
}

/** Oylama sürüyorsa oy vermediğim (adayı olan) kurullar. */
export function ballotsToVote(e: Election): ElectionBallot[] {
  if (e.status !== 'VOTING') return []
  return ballotsOf(e)
    .map((b) => b.code)
    .filter((b) => !(e.myBallots ?? []).includes(b) && e.candidates.some((c) => c.ballot === b))
}

export const electionKeys = { list: (clubId: string) => ['clubs', clubId, 'elections'] as const }

/** Kulübün seçimleri, yeniden eskiye (VIEW_MEMBERS: üyeler, görevliler ve danışman). */
export function useClubElections(clubId: string, enabled = true) {
  return useQuery({
    queryKey: electionKeys.list(clubId),
    enabled,
    retry: false,
    queryFn: async () => (await api.get<Election[]>(`/clubs/${clubId}/elections`)).data,
  })
}

/** Yanıttaki seçimi listede yerine koyar; ayrıca kulüp verileri (görevler, kararlar) tazelenir. */
function useElectionUpdate(clubId: string) {
  const qc = useQueryClient()
  return (e?: Election) => {
    if (e)
      qc.setQueryData<Election[]>(electionKeys.list(clubId), (old) =>
        old?.some((x) => x.id === e.id) ? old.map((x) => (x.id === e.id ? e : x)) : [e, ...(old ?? [])],
      )
    void qc.invalidateQueries({ queryKey: ['clubs', clubId] })
    void qc.invalidateQueries({ queryKey: ['clubs', 'approvals'] })
  }
}

export function useOpenElection(clubId: string) {
  const update = useElectionUpdate(clubId)
  return useMutation({
    mutationFn: async (v: { boardSeats: number; auditSeats: number; note: string }) =>
      (await api.post<Election>(`/clubs/${clubId}/elections`, { ...v, note: v.note.trim() || null })).data,
    onSuccess: update,
  })
}

/** Aday ol ya da adaylıktan çekil. */
export function useCandidacy(clubId: string) {
  const update = useElectionUpdate(clubId)
  return useMutation({
    mutationFn: async (v: { electionId: string; ballot: ElectionBallot | null }) => {
      const url = `/clubs/${clubId}/elections/${v.electionId}/candidacy`
      return (v.ballot ? await api.post<Election>(url, { ballot: v.ballot }) : await api.delete<Election>(url)).data
    },
    onSuccess: update,
  })
}

export function useVote(clubId: string) {
  const update = useElectionUpdate(clubId)
  return useMutation({
    mutationFn: async (v: { electionId: string; ballot: ElectionBallot; candidateIds: string[] }) =>
      (await api.post<Election>(`/clubs/${clubId}/elections/${v.electionId}/votes`, { ballot: v.ballot, candidateIds: v.candidateIds }))
        .data,
    onSuccess: update,
  })
}

/** Oylamayı başlat, iptal et ya da oylamayı kapat (sonuç onaya gider; yanıt onay kaydıdır). */
export function useElectionStep(clubId: string) {
  const update = useElectionUpdate(clubId)
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (v: { electionId: string; step: 'start-voting' | 'cancel' | 'close' }) =>
      (await api.post<Election | ApprovalRequest>(`/clubs/${clubId}/elections/${v.electionId}/${v.step}`)).data,
    onSuccess: (data, v) => {
      if (v.step === 'close') {
        void qc.invalidateQueries({ queryKey: electionKeys.list(clubId) })
        update()
      } else update(data as Election)
    },
  })
}
