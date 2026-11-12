import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import type { Course } from '@/features/courses/api'
import type { Me } from '@/features/me/useMe'

/**
 * Başkasının profili (F-54, F-92): `GET /api/users/profile/{id}` kimlik ve akademik bilgiler; e-posta, öğrenci numarası
 * ve kayıt durumu görünürlüğü sunucuda (personelin e-postası herkese; öğrencininkiler yalnız dersini veren ya da
 * kulübünü yöneten kişiye). `…/aggregated` son gönderileri, puanı ve rozetleri ekler; liderlik tablosundan çıkan
 * kişinin puanı `null`, rozetleri boş gelir.
 */

export type PersonProfile = Me

export type PersonBadge = { badgeType: string; name: string; description: string | null; imageUrl: string | null; earnedAt: string | null }

export type PersonExtras = {
  gamification: { totalPoints: number | null; badges: PersonBadge[] } | null
  recentPosts: { id: string; title: string; content: string; createdAt: string }[]
}

export const personKeys = {
  profile: (id: string) => ['people', id] as const,
  extras: (id: string) => ['people', id, 'extras'] as const,
  courses: (id: string) => ['people', id, 'courses'] as const,
}

/** Koordinatör ya da hoca olduğu, taslak olmayan dersler; bütün dönemler. */
export function usePersonCourses(id: string) {
  return useQuery({
    queryKey: personKeys.courses(id),
    staleTime: 60_000,
    queryFn: async () => (await api.get<Course[]>(`/courses/instructor/${id}`)).data,
  })
}

export function usePersonProfile(id: string) {
  return useQuery({
    queryKey: personKeys.profile(id),
    retry: false,
    staleTime: 60_000,
    queryFn: async () => (await api.get<PersonProfile>(`/users/profile/${id}`)).data,
  })
}

/** Son gönderiler ve rozetler; gelmezse sayfa bunlarsız açılır. */
export function usePersonExtras(id: string, enabled: boolean) {
  return useQuery({
    queryKey: personKeys.extras(id),
    enabled,
    retry: false,
    staleTime: 60_000,
    queryFn: async () => (await api.get<PersonExtras>(`/users/profile/${id}/aggregated`)).data,
  })
}

export const isAcademician = (p: Pick<PersonProfile, 'affiliations' | 'academicTitle' | 'staffCategory'>) =>
  !!p.affiliations?.includes('ACADEMICIAN') || !!p.academicTitle || !!p.staffCategory

export const isStudent = (p: Pick<PersonProfile, 'affiliations' | 'studentNumber' | 'programName'>) =>
  !!p.affiliations?.includes('STUDENT') || !!p.studentNumber || !!p.programName

/** Profil bağlantısı; kendi kimliğiniz için Profilim. */
export const personPath = (id: string) => `/people/${id}`
