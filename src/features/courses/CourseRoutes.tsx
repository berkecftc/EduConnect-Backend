import { lazy, Suspense } from 'react'
import { Link, useParams } from 'react-router-dom'
import { usePanel, viewRoles } from '@/lib/auth/panel'
import { hasRole, useSession } from '@/lib/auth/session'
import { EmptyState, Skeleton } from '@/components/ui/States'
import { useTeachingCourses } from './teach'

const CoursesPage = lazy(() => import('./CoursesPage').then((m) => ({ default: m.CoursesPage })))
const TeachingPage = lazy(() => import('./TeachingPage').then((m) => ({ default: m.TeachingPage })))
const CoursePage = lazy(() => import('./CoursePage').then((m) => ({ default: m.CoursePage })))
const CourseManagePage = lazy(() => import('./CourseManagePage').then((m) => ({ default: m.CourseManagePage })))

/** `/courses`: akademisyene verdiği dersler, öğrenciye kayıtlı olduğu dersler. */
export function CoursesIndex() {
  const session = useSession()
  const { academician } = viewRoles(session, usePanel())
  return <Suspense fallback={<Skeleton rows={5} />}>{academician ? <TeachingPage /> : <CoursesPage />}</Suspense>
}

/** Akademisyen, kadrosunda olmadığı bir derse geldiğinde (ör. kadrodan ayrıldıktan sonra ya da paylaşılan bağlantıyla). */
export function NotOnStaff() {
  return (
    <div className="mx-auto max-w-[56rem]">
      <EmptyState title="Bu dersin kadrosunda değilsiniz">
        Dersi yönetmek için koordinatörün sizi kadroya eklemesi gerekir.{' '}
        <Link to="/courses" className="font-semibold underline-offset-4 hover:underline">
          Verdiğim dersler
        </Link>
      </EmptyState>
    </div>
  )
}

/**
 * `/courses/:courseId`: kişi dersin kadrosundaysa yönetim sayfası (göreviyle), değilse öğrenci sayfası.
 * Kadro bilgisi "verdiğim dersler" listesinden gelir (`staffRole`, F-43). Öğrencilik kaydı olmayan akademisyen
 * kadroda değilse öğrenci sayfası yerine açıklama görür.
 */
export function CourseRoute() {
  const { courseId = '' } = useParams()
  const session = useSession()
  const academician = hasRole(session, 'ROLE_ACADEMICIAN')
  const teaching = useTeachingCourses(academician)
  if (academician && teaching.isPending) return <Skeleton rows={5} />
  const mine = teaching.data?.find((c) => c.id === courseId)
  if (academician && !mine && !hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')) return <NotOnStaff />
  return <Suspense fallback={<Skeleton rows={5} />}>{mine ? <CourseManagePage key={courseId} teaching={mine} /> : <CoursePage />}</Suspense>
}
