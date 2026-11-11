import type { LineKey } from '@/design/lines'
import { viewRoles, type Panel } from '@/lib/auth/panel'
import { hasPermission, hasRole, type Session } from '@/lib/auth/session'

export type NavItem = { to: string; label: string }
export type NavGroup = { line: LineKey; label: string; items: NavItem[] }

/**
 * Rol ve yetkiye göre menü. Her grup bir hat; öğeler o hattın durakları.
 * Rotalar bildirimlerin `link` alanlarıyla aynı (F-74…F-77).
 * `clubs.approver`: herhangi bir kulüpte başkan onayı yetkisi (GET /clubs/my-access, F-85); rol adından tahmin edilmez.
 * `panel`: hem personel hem öğrenci olan hesapta seçili görünüm (F-54).
 */
export function buildNav(session: Session, clubs: { approver: boolean } = { approver: false }, panel: Panel | null = null): NavGroup[] {
  const { student, academician } = viewRoles(session, panel)
  const staff = hasRole(session, 'ROLE_STAFF')
  const admin = hasRole(session, 'ROLE_ADMIN')
  const groups: NavGroup[] = []

  if (student || academician) {
    groups.push({
      line: 'ders',
      label: 'Dersler',
      items: academician
        ? [
            { to: '/courses', label: 'Verdiğim dersler' },
            { to: '/courses/new', label: 'Ders aç' },
          ]
        : [
            { to: '/courses', label: 'Derslerim' },
            { to: '/assignments', label: 'Ödevlerim' },
            { to: '/courses/catalog', label: 'Ders kataloğu' },
          ],
    })
    groups.push({
      line: 'kulup',
      label: 'Kulüpler',
      items: academician
        ? [
            { to: '/clubs/advised', label: 'Danışmanlıklarım' },
            { to: '/clubs/approvals', label: 'Onay bekleyenler' },
            { to: '/clubs', label: 'Tüm kulüpler' },
          ]
        : [
            { to: '/clubs/mine', label: 'Kulüplerim' },
            ...(clubs.approver ? [{ to: '/clubs/approvals', label: 'Onay bekleyenler' }] : []),
            { to: '/clubs', label: 'Tüm kulüpler' },
          ],
    })
    groups.push({
      line: 'etkinlik',
      label: 'Etkinlikler',
      items: [
        { to: '/events', label: 'Etkinlikler' },
        ...(student ? [{ to: '/me/tickets', label: 'Biletlerim' }] : []),
      ],
    })
  }

  groups.push({
    line: 'topluluk',
    label: 'Topluluk',
    items: [
      { to: '/posts', label: 'Akış' },
      ...(student ? [{ to: '/leaderboard', label: 'Liderlik tablosu' }] : []),
      ...(student || academician ? [{ to: '/assistant', label: 'Asistan' }] : []),
    ],
  })

  const manage: NavItem[] = []
  if (admin || hasPermission(session, 'PERM_STUDENT_VERIFIER')) manage.push({ to: '/manage/students', label: 'Öğrenci başvuruları' })
  if (admin || hasPermission(session, 'PERM_STAFF_VERIFIER')) manage.push({ to: '/manage/academicians', label: 'Akademisyen başvuruları' })
  if (admin || hasPermission(session, 'PERM_STUDENT_VERIFIER', 'PERM_STAFF_VERIFIER'))
    manage.push({ to: '/manage/change-requests', label: 'Profil talepleri' })
  if (admin || hasPermission(session, 'PERM_ACCOUNT_MANAGER')) manage.push({ to: '/manage/accounts', label: 'Hesaplar' })
  if (admin || hasPermission(session, 'PERM_MODERATOR')) manage.push({ to: '/manage/moderation', label: 'Moderasyon' })
  if (admin || hasPermission(session, 'PERM_CAMPUS_PUBLISHER')) manage.push({ to: '/manage/campus-events', label: 'Kampüs etkinlikleri' })
  if (admin) {
    manage.push(
      { to: '/manage/staff-accounts', label: 'Görevli hesapları' },
      { to: '/manage/courses', label: 'Dersler' },
      { to: '/manage/academic-structure', label: 'Akademik yapı' },
      { to: '/manage/terms', label: 'Dönemler' },
    )
  }
  if (staff || admin || manage.length) groups.push({ line: 'yonetim', label: 'Yönetim', items: manage })

  return groups.filter((g) => g.items.length > 0)
}

export function roleLabel(session: Session): string {
  switch (session.primaryRole) {
    case 'ROLE_ADMIN':
      return 'Yönetici'
    case 'ROLE_STAFF':
      return 'Görevli'
    case 'ROLE_ACADEMICIAN':
      return 'Akademisyen'
    case 'ROLE_CLUB_OFFICIAL':
      return 'Öğrenci, kulüp yönetimi'
    case 'ROLE_STUDENT':
      return 'Öğrenci'
    default:
      return 'Onay bekleniyor'
  }
}
