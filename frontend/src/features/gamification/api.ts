import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'

export type Badge = { badgeType: string; name: string; description: string; imageUrl: string | null; earnedAt: string }

/** `GET /api/gamification/users/me/summary`: katkı puanı ve haftalık katkı serisi (F-72). */
export type GamificationSummary = { totalPoints: number; currentStreak: number; highestStreak: number; badges: Badge[] }

export function useMySummary(enabled = true) {
  return useQuery({
    queryKey: ['gamification', 'summary'],
    enabled,
    queryFn: async () => (await api.get<GamificationSummary>('/gamification/users/me/summary')).data,
  })
}

export type LeaderboardPeriod = 'TERM' | 'ALL_TIME'

/** `GET /api/gamification/leaderboard` (F-73): `entries` sıralı; kendi satırınız `me: true`. */
export type Leaderboard = {
  period: LeaderboardPeriod
  termLabel: string | null
  from: string | null
  to: string | null
  facultyId: string | null
  entries: { rank: number; displayName: string; points: number; badges: string[]; me: boolean }[]
  me: { rank: number | null; points: number; visible: boolean }
}

export function useLeaderboard(period: LeaderboardPeriod, facultyId: string | null, limit: number) {
  return useQuery({
    queryKey: ['gamification', 'leaderboard', period, facultyId ?? 'all', limit],
    retry: false,
    placeholderData: (prev) => prev,
    queryFn: async () =>
      (await api.get<Leaderboard>('/gamification/leaderboard', { params: { period, limit, ...(facultyId ? { facultyId } : {}) } })).data,
  })
}

/** Rozet adları (BadgeType); görseli herkese açık uçtan gelir. */
export const BADGE_LABEL: Record<string, string> = {
  FIRST_STEP: 'İlk Adım',
  PROFILE_COMPLETE: 'Profil Ustası',
  POINTS_EXPLORER: 'Puan Kaşifi',
  POINTS_MASTER: 'Puan Ustası',
  WEEK_WARRIOR: 'Hafta Savaşçısı',
  FORTNIGHT_WARRIOR: 'Katkı Ustası',
  STREAK_LEGEND: 'Seri Efsanesi',
}

export const badgeImage = (type: string) => `/api/gamification/badges/${encodeURIComponent(type)}/image`
