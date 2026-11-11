import { hasPermission, hasRole, useSession, type Session } from '@/lib/auth/session'
import { useClubAccesses, type ClubAccess } from '@/features/clubs/manage'
import { useTeachingCourses, type TeachingCourse } from '@/features/courses/teach'

/**
 * Resmî duyurunun kimin adına yayımlanabileceği (F-67). Kural sunucuda; burada yalnız seçenekler gösterilir:
 * kulüp başkanı ve danışmanı doğrudan, duyuru hazırlama yetkisi olan görevli başkan onayıyla yayımlar; ders duyurusunu
 * koordinatör ve hoca, kampüs duyurusunu kampüs yayıncısı ve yönetici yayımlar.
 */

export type Publisher =
  | { key: string; type: 'CLUB'; clubId: string; label: string; needsApproval: boolean }
  | { key: string; type: 'COURSE'; courseId: string; label: string }
  | { key: 'campus'; type: 'CAMPUS'; label: string }

export function publishersOf(session: Session | null, accesses: ClubAccess[], teaching: TeachingCourse[]): Publisher[] {
  const clubs = accesses
    .filter((a) => a.actingPresident || a.advisor || a.permissions.includes('PREPARE_ANNOUNCEMENT'))
    .map(
      (a): Publisher => ({
        key: `club:${a.clubId}`,
        type: 'CLUB',
        clubId: a.clubId,
        label: a.clubName ?? 'Kulüp',
        needsApproval: !a.actingPresident && !a.advisor,
      }),
    )
  const courses = teaching
    .filter((c) => c.staffRole !== 'ASSISTANT' && c.status !== 'ARCHIVED' && c.status !== 'DRAFT')
    .map((c): Publisher => ({ key: `course:${c.id}`, type: 'COURSE', courseId: c.id, label: `${c.code} ${c.title}` }))
  const campus: Publisher[] =
    hasRole(session, 'ROLE_ADMIN') || hasPermission(session, 'PERM_CAMPUS_PUBLISHER')
      ? [{ key: 'campus', type: 'CAMPUS', label: 'Kampüs birimi' }]
      : []
  return [...clubs, ...courses, ...campus]
}

export function usePublishers() {
  const session = useSession()
  const academician = hasRole(session, 'ROLE_ACADEMICIAN')
  const accesses = useClubAccesses(academician || hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL'))
  const teaching = useTeachingCourses(academician)
  return {
    publishers: publishersOf(session, accesses.data ?? [], teaching.data ?? []),
    loading: accesses.isLoading || teaching.isLoading,
  }
}
