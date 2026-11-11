import { lazy, Suspense } from 'react'
import { useParams } from 'react-router-dom'
import { hasRole, useSession } from '@/lib/auth/session'
import { NotOnStaff } from '@/features/courses/CourseRoutes'
import { useTeachingCourses } from '@/features/courses/teach'
import { Skeleton } from '@/components/ui/States'

const AssignmentPage = lazy(() => import('./AssignmentPage').then((m) => ({ default: m.AssignmentPage })))
const AssignmentManagePage = lazy(() => import('./AssignmentManagePage').then((m) => ({ default: m.AssignmentManagePage })))

/**
 * `/courses/:courseId/assignments/:assignmentId`: kişi dersin kadrosundaysa yönetim sayfası, değilse öğrenci sayfası.
 * Bildirim bağlantıları (F-76) iki taraf için de aynı adresi kullanır.
 */
export function AssignmentRoute() {
  const { courseId = '' } = useParams()
  const session = useSession()
  const academician = hasRole(session, 'ROLE_ACADEMICIAN')
  const teaching = useTeachingCourses(academician)
  if (academician && teaching.isPending) return <Skeleton rows={5} />
  const course = teaching.data?.find((c) => c.id === courseId)
  if (academician && !course && !hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')) return <NotOnStaff />
  return <Suspense fallback={<Skeleton rows={5} />}>{course ? <AssignmentManagePage teaching={course} /> : <AssignmentPage />}</Suspense>
}
