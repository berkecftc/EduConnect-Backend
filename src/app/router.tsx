import { createBrowserRouter, type RouteObject } from 'react-router-dom'
import { AppShell } from '@/components/layout/AppShell'
import { RequireAuth } from '@/components/layout/RequireAuth'
import { LoginPage } from '@/features/auth/LoginPage'
import { NotFoundPage, RouteErrorPage } from '@/pages/StatusPages'

/**
 * Sayfalar rota bazlı bölünür: yalnız açıldığında indirilir. Giriş sayfası ilk açılış olduğu için pakette kalır.
 * `page(() => import(...), 'Ad')` dosyanın adlı dışa aktarımını Component olarak verir.
 */
function page<M extends Record<string, unknown>>(load: () => Promise<M>, name: keyof M): Pick<RouteObject, 'lazy'> {
  return { lazy: async () => ({ Component: (await load())[name] as React.ComponentType }) }
}

/** Oturum gerektiren sayfalar; hepsi uygulama kabuğunun içinde açılır. */
const shellRoutes: RouteObject[] = [
  { index: true, ...page(() => import('@/pages/HomePage'), 'HomePage') },
  { path: 'courses', ...page(() => import('@/features/courses/CourseRoutes'), 'CoursesIndex') },
  { path: 'courses/catalog', ...page(() => import('@/features/courses/CatalogPage'), 'CatalogPage') },
  { path: 'courses/new', ...page(() => import('@/features/courses/CourseCreatePage'), 'CourseCreatePage') },
  { path: 'courses/:courseId', ...page(() => import('@/features/courses/CourseRoutes'), 'CourseRoute') },
  {
    path: 'courses/:courseId/assignments/new',
    ...page(() => import('@/features/assignments/AssignmentCreatePage'), 'AssignmentCreatePage'),
  },
  {
    path: 'courses/:courseId/assignments/:assignmentId',
    ...page(() => import('@/features/assignments/AssignmentRoutes'), 'AssignmentRoute'),
  },
  { path: 'clubs', ...page(() => import('@/features/clubs/ClubsPage'), 'ClubsPage') },
  { path: 'clubs/approvals', ...page(() => import('@/features/clubs/ApprovalsPage'), 'ApprovalsPage') },
  { path: 'clubs/advised', ...page(() => import('@/features/clubs/AdvisedClubsPage'), 'AdvisedClubsPage') },
  { path: 'clubs/mine', ...page(() => import('@/features/clubs/MyClubsPage'), 'MyClubsPage') },
  { path: 'clubs/new', ...page(() => import('@/features/clubs/ClubCreatePage'), 'ClubCreatePage') },
  { path: 'clubs/:clubId', ...page(() => import('@/features/clubs/ClubPage'), 'ClubPage') },
  { path: 'events', ...page(() => import('@/features/events/EventsPage'), 'EventsPage') },
  { path: 'events/:eventId', ...page(() => import('@/features/events/EventPage'), 'EventPage') },
  { path: 'events/:eventId/yonetim', ...page(() => import('@/features/events/EventManagePage'), 'EventManagePage') },
  { path: 'events/:eventId/kapi', ...page(() => import('@/features/events/EventGatePage'), 'EventGatePage') },
  { path: 'clubs/:clubId/kayitlar', ...page(() => import('@/features/clubs/ClubRecordsPage'), 'ClubRecordsPage') },
  { path: 'clubs/:clubId/events/new', ...page(() => import('@/features/events/EventCreatePage'), 'EventCreatePage') },
  { path: 'me/tickets', ...page(() => import('@/features/events/TicketsPage'), 'TicketsPage') },
  { path: 'posts', ...page(() => import('@/features/posts/PostsPage'), 'PostsPage') },
  { path: 'posts/new', ...page(() => import('@/features/posts/PostComposerPage'), 'PostComposerPage') },
  { path: 'posts/:postId', ...page(() => import('@/features/posts/PostPage'), 'PostPage') },
  { path: 'posts/:postId/edit', ...page(() => import('@/features/posts/PostComposerPage'), 'PostComposerPage') },
  { path: 'leaderboard', ...page(() => import('@/features/gamification/LeaderboardPage'), 'LeaderboardPage') },
  { path: 'profile', ...page(() => import('@/features/profile/ProfilePage'), 'ProfilePage') },
  { path: 'people/:userId', ...page(() => import('@/features/people/PersonPage'), 'PersonPage') },
  { path: 'notifications', ...page(() => import('@/features/notifications/NotificationsPage'), 'NotificationsPage') },
  { path: 'settings', ...page(() => import('@/features/settings/SettingsPage'), 'SettingsPage') },
  { path: 'settings/:section', ...page(() => import('@/features/settings/SettingsPage'), 'SettingsPage') },
  { path: 'assignments', ...page(() => import('@/features/assignments/AssignmentsPage'), 'AssignmentsPage') },
  { path: 'manage/students', ...page(() => import('@/features/manage/RegistrationRequestsPage'), 'StudentRequestsPage') },
  { path: 'manage/academicians', ...page(() => import('@/features/manage/RegistrationRequestsPage'), 'AcademicianRequestsPage') },
  { path: 'manage/accounts', ...page(() => import('@/features/manage/AccountsPage'), 'AccountsPage') },
  { path: 'manage/staff-accounts', ...page(() => import('@/features/manage/StaffAccountsPage'), 'StaffAccountsPage') },
  { path: 'manage/moderation', ...page(() => import('@/features/manage/ModerationPage'), 'ModerationPage') },
  { path: 'manage/campus-events', ...page(() => import('@/features/manage/CampusEventsPage'), 'CampusEventsPage') },
  { path: 'manage/campus-events/new', ...page(() => import('@/features/manage/CampusEventsPage'), 'CampusEventCreatePage') },
  { path: 'manage/terms', ...page(() => import('@/features/manage/TermsPage'), 'TermsPage') },
  { path: 'manage/academic-structure', ...page(() => import('@/features/manage/AcademicStructurePage'), 'AcademicStructurePage') },
  { path: 'manage/courses', ...page(() => import('@/features/manage/CoursesAdminPage'), 'CoursesAdminPage') },
  { path: 'manage/change-requests', ...page(() => import('@/features/manage/ChangeRequestsPage'), 'ChangeRequestsPage') },
  ...(import.meta.env.DEV ? [{ path: 'dev/hata', ...page(() => import('@/dev/ThrowPage'), 'ThrowPage') }] : []),
  { path: '*', element: <NotFoundPage /> },
]

export const router = createBrowserRouter([
  {
    // Kabuğun dışındaki hatalar: giriş sayfaları ve kabuğun kendisi.
    errorElement: <RouteErrorPage standalone />,
    children: [
      { path: '/login', element: <LoginPage /> },
      { path: '/register', ...page(() => import('@/features/auth/register/RegisterPage'), 'RegisterPage') },
      { path: '/forgot-password', ...page(() => import('@/features/auth/ForgotPasswordPage'), 'ForgotPasswordPage') },
      { path: '/reset-password', ...page(() => import('@/features/auth/ResetPasswordPage'), 'ResetPasswordPage') },
      { path: '/verify-email', ...page(() => import('@/features/auth/VerifyEmailPage'), 'VerifyEmailPage') },
      // E-postadaki abonelikten çıkma bağlantısı: oturumsuz açılır (F-74).
      { path: '/notifications/unsubscribe', ...page(() => import('@/features/notifications/UnsubscribePage'), 'UnsubscribePage') },
      ...(import.meta.env.DEV
        ? [
            { path: '/design', ...page(() => import('@/pages/DesignSystemPage'), 'DesignSystemPage') },
            // Örnek veriyle önizleme: oturum bellekte, istekler ağa gitmez (yalnız geliştirme).
            { path: '/onizleme', ...page(() => import('@/dev/PreviewPage'), 'PreviewPage') },
            { path: '/dev/hata-disarida', ...page(() => import('@/dev/ThrowPage'), 'ThrowPage') },
          ]
        : []),
      {
        path: '/',
        element: (
          <RequireAuth>
            <AppShell />
          </RequireAuth>
        ),
        // Sayfa hatası kabuğun içinde gösterilir; menü çalışır kalır, başka sayfaya geçince hata kalkar.
        children: [{ errorElement: <RouteErrorPage />, children: shellRoutes }],
      },
    ],
  },
])
