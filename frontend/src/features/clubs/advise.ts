import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { clubKeys, type ClubDetails, type ClubRole } from './api'
import { useClubAccesses } from './manage'

/**
 * Danışman tarafı (F-28, F-85): danışmanı olduğum kulüpler, kulüp kuruluş başvuruları, danışmanlık teklifleri,
 * görev değişikliği istekleri; başkan atama/görevden alma, kulübü kapatma, danışmanlığı bırakma.
 */

export type FounderStatus = 'INVITED' | 'CONFIRMED' | 'DECLINED'

/** `GET /api/academician/club-creation-requests` öğesi. Danışmana kurucular onayladıktan sonra (PENDING) gelir. */
export type ClubCreationRequest = {
  id: string
  clubName: string
  about: string | null
  requestingStudentId: string
  /** Ad gelmezse kurucular listesinden bulunur. */
  requestingStudentName?: string | null
  suggestedAdvisorId: string | null
  suggestedAdvisorName?: string | null
  status: 'PENDING_FOUNDERS' | 'PENDING' | 'APPROVED' | 'REJECTED'
  requestDate: string
  rejectionReason: string | null
  processedAt: string | null
  clubId: string | null
  founders: { studentId: string; firstName: string | null; lastName: string | null; status: FounderStatus; respondedAt: string | null }[]
}

/** Danışmanlık teklifi: başkan sizi kulübe danışman olarak önerdi. */
export type AdvisorOffer = {
  id: string
  clubId: string
  clubName: string
  proposedAdvisorId: string
  requestedBy: string
  requestedByName?: string | null
  message: string | null
  status: string
  rejectionReason: string | null
  createdAt: string
  decidedAt: string | null
}

/** Görev değişikliği isteği: başkan bir üyeye görev verilmesini ya da görevinin değişmesini istiyor. */
export type RoleChangeRequest = {
  id: string
  clubId: string
  clubName: string
  studentId: string
  studentName: string | null
  currentRole: ClubRole | null
  requestedRole: ClubRole
  requesterId: string
  requesterName: string | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  rejectionReason: string | null
  createdAt: string
  processedAt: string | null
}

export const FOUNDER_STATUS_LABEL: Record<FounderStatus, string> = {
  INVITED: 'Yanıt bekleniyor',
  CONFIRMED: 'Onayladı',
  DECLINED: 'Reddetti',
}

export const adviseKeys = {
  creation: ['clubs', 'advise', 'creation'] as const,
  offers: ['clubs', 'advise', 'offers'] as const,
  roleChanges: ['clubs', 'advise', 'role-changes'] as const,
}

/** Danışmanı olduğum kulüplerin ayrıntıları (durum, üyeler); erişimlerden süzülür. */
export function useAdvisedClubs(enabled = true) {
  const accesses = useClubAccesses(enabled)
  const ids = (accesses.data ?? []).filter((a) => a.advisor).map((a) => a.clubId)
  const details = useQueries({
    queries: ids.map((id) => ({
      queryKey: clubKeys.detail(id),
      queryFn: async () => (await api.get<ClubDetails>(`/clubs/${id}`)).data,
    })),
  })
  return { accesses, ids, details }
}

export function useCreationRequests(enabled = true) {
  return useQuery({
    queryKey: adviseKeys.creation,
    enabled,
    queryFn: async () => (await api.get<ClubCreationRequest[]>('/academician/club-creation-requests')).data,
  })
}

export function useAdvisorOffers(enabled = true) {
  return useQuery({
    queryKey: adviseKeys.offers,
    enabled,
    queryFn: async () => (await api.get<AdvisorOffer[]>('/academician/advisor-change-requests')).data,
  })
}

export function useRoleChangeRequests(enabled = true) {
  return useQuery({
    queryKey: adviseKeys.roleChanges,
    enabled,
    queryFn: async () => (await api.get<RoleChangeRequest[]>('/academician/role-change-requests')).data,
  })
}

function useClubsRefresh() {
  const qc = useQueryClient()
  return () => void qc.invalidateQueries({ queryKey: ['clubs'] })
}

export type AdviseDecision =
  | { kind: 'creation'; id: string; approve: boolean; reason?: string }
  | { kind: 'offer'; id: string; approve: boolean; reason?: string }
  | { kind: 'roleChange'; id: string; approve: boolean; reason?: string }

/** Başvuru, teklif ya da görev değişikliği kararı; ret gerekçesi isteğe bağlı. */
export function useAdviseDecision() {
  const refresh = useClubsRefresh()
  return useMutation({
    mutationFn: async (d: AdviseDecision) => {
      const base =
        d.kind === 'creation'
          ? `/academician/club-creation-requests/${d.id}`
          : d.kind === 'offer'
            ? `/academician/advisor-change-requests/${d.id}`
            : `/academician/role-change-requests/${d.id}`
      if (d.approve) await api.put(`${base}/${d.kind === 'offer' ? 'accept' : 'approve'}`)
      else await api.put(`${base}/reject`, { rejectionReason: d.reason?.trim() || null })
    },
    onSuccess: refresh,
  })
}

export type ClubAction =
  | { kind: 'appoint'; studentId: string }
  | { kind: 'removePresident'; reason: string }
  | { kind: 'close'; reason: string }
  | { kind: 'resign'; reason: string }

/** Kulüp üzerinde danışman işlemleri (ADVISE). */
export function useAdvisorClubAction(clubId: string) {
  const refresh = useClubsRefresh()
  return useMutation({
    mutationFn: async (a: ClubAction) => {
      const base = `/academician/clubs/${clubId}`
      switch (a.kind) {
        case 'appoint':
          return void (await api.put(`${base}/president`, { studentId: a.studentId }))
        case 'removePresident':
          return void (await api.delete(`${base}/president`, { data: { rejectionReason: a.reason.trim() || null } }))
        case 'close':
          return void (await api.post(`${base}/close`, { reason: a.reason.trim() }))
        case 'resign':
          return void (await api.post(`${base}/advisor/resign`, { rejectionReason: a.reason.trim() || null }))
      }
    },
    onSuccess: refresh,
  })
}
