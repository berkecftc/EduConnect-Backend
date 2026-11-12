import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api/client'
import { ApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'

/** `GET /api/users/me` yanıtı (UserProfileResponse). */
export type Me = {
  id: string
  firstName: string | null
  lastName: string | null
  email: string | null
  profileImageUrl: string | null
  bio: string | null
  role: string | null
  studentNumber: string | null
  title: string | null
  academicTitle: string | null
  department: string | null
  programId: string | null
  programName: string | null
  programLevel: string | null
  facultyName: string | null
  facultyId: string | null
  departmentId: string | null
  entryYear: number | null
  classYear: number | null
  staffCategory: string | null
  affiliations: string[] | null
  officeNumber: string | null
  officeHours: string | null
  studentStatus: string | null
  staffStatus: string | null
}

export const meQueryKey = ['me'] as const

/**
 * Oturum sahibinin profili (F-83). Yönetici ve görevli hesaplarında profil yoktur (404):
 * bu durumda `null` döner ve arayüz JWT'deki rollerle devam eder.
 */
export function useMe() {
  const session = useSession()
  return useQuery({
    queryKey: [...meQueryKey, session?.userId],
    enabled: !!session,
    staleTime: 5 * 60_000,
    queryFn: async (): Promise<Me | null> => {
      try {
        const { data } = await api.get<Me>('/users/me')
        return data
      } catch (err) {
        if (err instanceof ApiError && err.status === 404) return null
        throw err
      }
    },
  })
}

/** Görünen ad: "Ad Soyad", yoksa e-posta. */
export function displayName(me: Me | null | undefined, fallback: string): string {
  const name = [me?.firstName, me?.lastName].filter(Boolean).join(' ').trim()
  return name || fallback
}

/** Baş harfler (Türkçe büyük harf kurallarıyla). */
export function initials(me: Me | null | undefined, fallback: string): string {
  const parts = [me?.firstName, me?.lastName].filter((p): p is string => !!p && p.trim().length > 0)
  const source = parts.length ? parts.map((p) => p.trim()[0]).join('') : fallback.slice(0, 2)
  return source.toLocaleUpperCase('tr-TR')
}
