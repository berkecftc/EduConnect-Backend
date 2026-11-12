import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { meQueryKey } from '@/features/me/useMe'

/** Profil, hesap ve görünürlük ayarları (F-54, F-55, F-73). */

/** `GET /api/users/me/aggregated`: profil özeti, puan ve rozetler, son gönderiler. */
export type AggregatedProfile = {
  id: string
  profileCompletionPercentage: number
  gamification: {
    totalPoints: number
    currentStreak: number | null
    highestStreak: number | null
    badges: { type?: string; name?: string; description?: string | null; earnedAt?: string | null }[]
  } | null
  recentPosts: { id: string; title: string; content: string; createdAt: string }[]
}

export function useAggregatedMe(enabled = true) {
  return useQuery({
    queryKey: [...meQueryKey, 'aggregated'],
    enabled,
    retry: false,
    queryFn: async () => (await api.get<AggregatedProfile>('/users/me/aggregated')).data,
  })
}

/** Doğrudan düzenlenebilen alanlar: hakkımda; personelde ofis ve görüşme saatleri. Resmî alanlar talep ister. */
export type ProfileEdits = { bio: string; officeNumber?: string; officeHours?: string }

export function useUpdateProfile(userId: string) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (e: ProfileEdits) => {
      const body: Record<string, string | null> = { bio: e.bio.trim() || null }
      if (e.officeNumber !== undefined) body.officeNumber = e.officeNumber.trim() || null
      if (e.officeHours !== undefined) body.officeHours = e.officeHours.trim() || null
      await api.put(`/users/profile/${userId}`, body)
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: meQueryKey }),
  })
}

export function useUploadAvatar() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (file: File) => {
      const form = new FormData()
      form.append('file', file)
      return (await api.post<string>('/users/me/profile-picture', form)).data
    },
    onSuccess: () => void qc.invalidateQueries({ queryKey: meQueryKey }),
  })
}

// ——— Resmî bilgi değişiklik talepleri (F-55) ———

export type ChangeRequestStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export type ChangeRequest = {
  id: string
  currentName: string | null
  firstName: string | null
  lastName: string | null
  academicTitle: string | null
  titleLabel: string | null
  programId: string | null
  departmentId: string | null
  reason: string
  status: ChangeRequestStatus
  reviewNote: string | null
  reviewedAt: string | null
  createdAt: string
}

export type ChangeRequestInput = {
  firstName?: string
  lastName?: string
  title?: string
  programId?: string
  departmentId?: string
  reason: string
}

const changeKey = ['profile', 'change-requests'] as const

export function useChangeRequests(enabled = true) {
  return useQuery({
    queryKey: changeKey,
    enabled,
    queryFn: async () => (await api.get<ChangeRequest[]>('/users/profile/me/change-requests')).data,
  })
}

export function useSubmitChangeRequest() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: ChangeRequestInput) => (await api.post<ChangeRequest>('/users/profile/me/change-requests', input)).data,
    onSuccess: () => void qc.invalidateQueries({ queryKey: changeKey }),
  })
}

export const CHANGE_STATUS: Record<ChangeRequestStatus, { label: string; tone: 'warning' | 'success' | 'danger' }> = {
  PENDING: { label: 'Beklemede', tone: 'warning' },
  APPROVED: { label: 'Onaylandı', tone: 'success' },
  REJECTED: { label: 'Reddedildi', tone: 'danger' },
}

// ——— Hesap ———

export function useChangePassword() {
  return useMutation({
    mutationFn: async (v: { currentPassword: string; newPassword: string; confirmationPassword: string }) => {
      await api.post('/auth/change-password', v)
    },
  })
}

// ——— Liderlik tablosu görünürlüğü (F-73) ———

export type DisplayMode = 'FULL_NAME' | 'INITIALS'
export type LeaderboardPreference = { visible: boolean; displayMode: DisplayMode }

const leaderboardKey = ['gamification', 'leaderboard-preference'] as const

export function useLeaderboardPreference(enabled = true) {
  return useQuery({
    queryKey: leaderboardKey,
    enabled,
    queryFn: async () => (await api.get<LeaderboardPreference>('/gamification/users/me/leaderboard-preference')).data,
  })
}

export function useUpdateLeaderboardPreference() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (p: LeaderboardPreference) =>
      (await api.put<LeaderboardPreference>('/gamification/users/me/leaderboard-preference', p)).data,
    onSuccess: (p) => qc.setQueryData(leaderboardKey, p),
  })
}

export const STATUS_LABEL: Record<string, string> = {
  ACTIVE: 'Aktif',
  ON_LEAVE: 'Kayıt dondurdu',
  GRADUATED: 'Mezun',
  WITHDRAWN: 'Kaydı silindi',
  EXPELLED: 'İlişiği kesildi',
  TRANSFERRED_OUT: 'Ayrıldı',
  RETIRED: 'Emekli',
  RESIGNED: 'İstifa',
  TERMINATED: 'Görevi sona erdi',
}

export const STAFF_CATEGORY_LABEL: Record<string, string> = {
  FACULTY_MEMBER: 'Öğretim üyesi',
  LECTURER: 'Öğretim görevlisi',
  RESEARCH_ASSISTANT: 'Araştırma görevlisi',
}
