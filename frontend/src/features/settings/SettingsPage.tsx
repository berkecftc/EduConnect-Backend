import type { ComponentType } from 'react'
import { NavLink, Navigate, useParams } from 'react-router-dom'
import { hasRole, useSession } from '@/lib/auth/session'
import { usePageTitle } from '@/lib/usePageTitle'
import { cn } from '@/lib/cn'
import { useMe } from '@/features/me/useMe'
import { PageHeader, Skeleton } from '@/components/ui/States'
import { AccountSettings } from './AccountSettings'
import { LeaderboardSettings } from './LeaderboardSettings'
import { NotificationSettings } from './NotificationSettings'
import { ProfileSettings } from './ProfileSettings'

type Section = { slug: string; label: string; title: string; Component: ComponentType }

/** Ayarlar: adres `/settings/{bölüm}`; dizin solda (dar ekranda üstte), içerik sağda. Bölümler hesaba göre. */
export function SettingsPage() {
  const { section } = useParams()
  const session = useSession()
  const me = useMe()
  const student = hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL')

  const sections: Section[] = [
    // Yönetici ve görevli hesaplarında profil yoktur (F-83).
    ...(me.data ? [{ slug: 'profile', label: 'Profil', title: 'Profil ayarları', Component: ProfileSettings }] : []),
    { slug: 'account', label: 'Hesap', title: 'Hesap ayarları', Component: AccountSettings },
    { slug: 'notifications', label: 'Bildirimler', title: 'Bildirim ayarları', Component: NotificationSettings },
    ...(student ? [{ slug: 'leaderboard', label: 'Liderlik tablosu', title: 'Liderlik tablosu ayarları', Component: LeaderboardSettings }] : []),
  ]
  const current = sections.find((s) => s.slug === section)
  usePageTitle(current?.title ?? 'Ayarlar')

  if (me.isPending) return <Skeleton rows={5} />
  if (!current) return <Navigate to={`/settings/${sections[0]!.slug}`} replace />
  const { Component } = current

  return (
    <div className="mx-auto max-w-[72rem]">
      <PageHeader title="Ayarlar" />
      <div className="mt-10 grid gap-10 lg:grid-cols-[13rem_minmax(0,1fr)] lg:gap-14">
        <nav aria-label="Ayar bölümleri">
          <ul className="-mx-1 flex gap-1 overflow-x-auto overflow-y-hidden border-b border-rule px-1 lg:mx-0 lg:flex-col lg:border-b-0 lg:border-l lg:px-0">
            {sections.map((s) => (
              <li key={s.slug}>
                <NavLink
                  to={`/settings/${s.slug}`}
                  className={({ isActive }) =>
                    cn(
                      'relative block px-3 py-2.5 text-md font-semibold whitespace-nowrap lg:pl-5',
                      isActive
                        ? 'text-ink after:absolute after:inset-x-3 after:bottom-0 after:h-[3px] after:bg-ink lg:after:inset-x-auto lg:after:inset-y-1 lg:after:-left-px lg:after:h-auto lg:after:w-[3px]'
                        : 'text-ink-3 hover:text-ink',
                    )
                  }
                >
                  {s.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
        <div className="min-w-0">
          <Component />
        </div>
      </div>
    </div>
  )
}
