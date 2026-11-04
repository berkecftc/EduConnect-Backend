import { useEffect, useMemo, useRef, useState } from 'react'
import { NavLink, useLocation, useNavigate, useOutlet } from 'react-router-dom'
import { Bell, LogOut, Menu, X } from 'lucide-react'
import { Dialog } from 'radix-ui'
import { AnimatePresence, motion } from 'motion/react'
import { LINES } from '@/design/lines'
import { ease, transition } from '@/design/motion'
import { logout } from '@/lib/api/client'
import { setPanel, usePanel, type Panel } from '@/lib/auth/panel'
import { hasRole, useSession, type Session } from '@/lib/auth/session'
import { cn } from '@/lib/cn'
import { Button } from '@/components/ui/Button'
import { ThemeSwitch } from '@/components/ui/ThemeSwitch'
import { Wordmark } from '@/components/ui/Wordmark'
import { displayName, useMe } from '@/features/me/useMe'
import { useUnreadCount } from '@/features/notifications/api'
import { useClubAccesses } from '@/features/clubs/manage'
import { buildNav, roleLabel, type NavGroup } from './nav'

/**
 * Uygulama kabuğu: koyu "gece" kenar menüsü (hat haritası), üst çubuk ve sayfa geçişleri.
 * Dar ekranda menü sağdan açılan bir çekmecede.
 */
export function AppShell() {
  const session = useSession()
  const [open, setOpen] = useState(false)
  const location = useLocation()
  const [lastPath, setLastPath] = useState(location.pathname)
  const mainRef = useRef<HTMLElement>(null)

  // Sayfa değişince mobil menüyü kapat.
  if (lastPath !== location.pathname) {
    setLastPath(location.pathname)
    if (open) setOpen(false)
  }

  // Sayfa değişince odağı içeriğe taşı: ekran okuyucu yeni sayfayı baştan okur (ilk yüklemede değil).
  const firstRender = useRef(true)
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false
      return
    }
    mainRef.current?.focus({ preventScroll: true })
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  if (!session) return null

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[17rem_minmax(0,1fr)]">
      <a
        href="#icerik"
        className="sr-only focus:not-sr-only focus:fixed focus:top-3 focus:left-3 focus:z-50 focus:rounded-md focus:bg-ink focus:px-3 focus:py-2 focus:text-on-ink"
      >
        İçeriğe geç
      </a>

      <aside className="sticky top-0 hidden h-dvh flex-col border-r border-rule bg-canvas text-ink lg:flex">
        <div className="px-6 pt-7 pb-10">
          <NavLink to="/" className="rounded-sm" aria-label="EduConnect ana sayfa">
            <Wordmark />
          </NavLink>
        </div>
        <SideNav session={session} indicatorId="yan" />
        <UserBlock session={session} />
      </aside>

      <div className="flex min-w-0 flex-col">
        <header className="sticky top-0 z-30 flex h-16 items-center justify-between gap-3 border-b border-rule bg-canvas px-4 lg:justify-end lg:px-10">
          <NavLink to="/" className="rounded-sm lg:hidden" aria-label="EduConnect ana sayfa">
            <Wordmark />
          </NavLink>
          <div className="flex items-center gap-2">
            <NotificationBell />
            <Dialog.Root open={open} onOpenChange={setOpen}>
              <Dialog.Trigger asChild>
                <Button variant="ghost" size="sm" className="size-11 px-0 lg:hidden" aria-label="Menüyü aç">
                  <Menu className="size-5" aria-hidden />
                </Button>
              </Dialog.Trigger>
              <AnimatePresence>
                {open && (
                  <Dialog.Portal forceMount>
                    <Dialog.Overlay asChild forceMount>
                      <motion.div
                        className="fixed inset-0 z-40 bg-scrim"
                        initial={{ opacity: 0 }}
                        animate={{ opacity: 1 }}
                        exit={{ opacity: 0 }}
                        transition={transition.base}
                      />
                    </Dialog.Overlay>
                    <Dialog.Content asChild forceMount>
                      <motion.div
                        className="fixed inset-y-0 right-0 z-50 flex w-[min(20rem,88vw)] flex-col border-l border-rule bg-canvas text-ink"
                        initial={{ x: '100%' }}
                        animate={{ x: 0 }}
                        exit={{ x: '100%' }}
                        transition={transition.slow}
                      >
                        <div className="flex items-center justify-between px-5 pt-4 pb-3">
                          <Dialog.Title className="text-lg font-heavy">Menü</Dialog.Title>
                          <Dialog.Close asChild>
                            <Button variant="ghost" size="sm" className="size-11 px-0" aria-label="Menüyü kapat">
                              <X className="size-5" aria-hidden />
                            </Button>
                          </Dialog.Close>
                        </div>
                        <Dialog.Description className="sr-only">EduConnect bölümleri</Dialog.Description>
                        <SideNav session={session} indicatorId="cekmece" />
                        <UserBlock session={session} />
                      </motion.div>
                    </Dialog.Content>
                  </Dialog.Portal>
                )}
              </AnimatePresence>
            </Dialog.Root>
          </div>
        </header>

        <main ref={mainRef} id="icerik" tabIndex={-1} className="flex-1 px-4 pt-8 pb-16 outline-none sm:px-6 lg:px-12 lg:pt-10">
          <PageTransition />
        </main>
      </div>
    </div>
  )
}

/**
 * Sayfa geçişi: eski sayfa hafifçe yukarı kaybolur, yenisi aşağıdan gelir.
 * Çıkan sayfanın içeriği `useOutlet` ile dondurulur; hareket azaltılmışsa MotionConfig geçişi kısaltır.
 */
function PageTransition() {
  const location = useLocation()
  const outlet = useOutlet()
  // Aynı sayfa içindeki sekme ve filtre değişiklikleri (yalnız arama parametresi) geçiş tetiklemez.
  return (
    <AnimatePresence mode="wait" initial={false}>
      <motion.div
        key={location.pathname}
        initial={{ opacity: 0, y: 12 }}
        animate={{ opacity: 1, y: 0, transition: { duration: 0.32, ease: ease.rail } }}
        exit={{ opacity: 0, y: -8, transition: { duration: 0.16, ease: ease.swap } }}
      >
        {outlet}
      </motion.div>
    </AnimatePresence>
  )
}

/** Yoldaki en uzun eşleşen menü öğesi etkindir: /courses/c1/assignments/a1 → "Derslerim". */
function useActivePath(groups: NavGroup[]) {
  const { pathname } = useLocation()
  return useMemo(() => {
    const paths = ['/', ...groups.flatMap((g) => g.items.map((i) => i.to))]
    let best = pathname === '/' ? '/' : ''
    for (const p of paths) {
      if (p === '/') continue
      if ((pathname === p || pathname.startsWith(p + '/')) && p.length > best.length) best = p
    }
    return best
  }, [groups, pathname])
}

function SideNav({ session, indicatorId }: { session: Session; indicatorId: string }) {
  const accesses = useClubAccesses(hasRole(session, 'ROLE_STUDENT', 'ROLE_CLUB_OFFICIAL'))
  const approver = (accesses.data ?? []).some((a) => a.permissions.includes('APPROVE_AS_PRESIDENT'))
  const panel = usePanel()
  const groups = useMemo(() => buildNav(session, { approver }, panel), [session, approver, panel])
  const active = useActivePath(groups)
  const indicator = `nav-gosterge-${indicatorId}`

  return (
    <nav aria-label="Ana menü" className="flex-1 overflow-y-auto px-6 pb-8">
      <IndexLink to="/" label="Bugün" on={active === '/'} indicator={indicator} color="var(--ec-ink)" large />
      <ul className="mt-8 flex flex-col gap-7">
        {groups.map((g) => (
          <li key={g.line}>
            <p className="flex items-center gap-2 text-sm text-ink-3">
              <span aria-hidden className="h-[3px] w-3" style={{ background: LINES[g.line].stroke }} />
              {g.label}
            </p>
            <ul className="mt-2">
              {g.items.map((item) => (
                <li key={item.to}>
                  <IndexLink
                    to={item.to}
                    label={item.label}
                    on={active === item.to}
                    indicator={indicator}
                    color={LINES[g.line].stroke}
                  />
                </li>
              ))}
            </ul>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/**
 * Dizin bağlantısı: ikon ve nokta yok, yalnız tipografi. Etkin sayfayı hat renginde kalın bir dikey çizgi gösterir;
 * çizgi sayfadan sayfaya kayar (paylaşılan düzen animasyonu).
 */
function IndexLink({
  to,
  label,
  on,
  indicator,
  color,
  large = false,
}: {
  to: string
  label: string
  on: boolean
  indicator: string
  color: string
  large?: boolean
}) {
  return (
    <NavLink
      to={to}
      end={to === '/'}
      aria-current={on ? 'page' : undefined}
      className={cn(
        'group relative -ml-6 flex items-center pl-6 transition-colors duration-200',
        large ? 'h-11 text-xl font-heavy' : 'h-9 text-lg',
        on ? 'text-ink' : 'text-ink-3 hover:text-ink',
        !large && on && 'font-semibold',
      )}
    >
      {on && (
        <motion.span
          layoutId={indicator}
          transition={transition.base}
          aria-hidden
          className="absolute top-1.5 bottom-1.5 left-0 w-[4px]"
          style={{ background: color }}
        />
      )}
      <span className="transition-transform duration-300 ease-rail group-hover:translate-x-1">{label}</span>
    </NavLink>
  )
}

function NotificationBell() {
  const unread = useUnreadCount()
  const count = unread.data ?? 0
  return (
    <Button variant="ghost" size="sm" asChild className="relative h-11 gap-2 px-3">
      <NavLink to="/notifications" aria-label={count > 0 ? `Bildirimler, ${count} okunmamış` : 'Bildirimler'}>
        <Bell className="size-5" aria-hidden />
        {count > 0 && (
          <span aria-hidden className="tabular text-md font-heavy text-etkinlik">
            {count > 9 ? '9+' : count}
          </span>
        )}
      </NavLink>
    </Button>
  )
}

const PANELS: { value: Panel; label: string }[] = [
  { value: 'academician', label: 'Personel' },
  { value: 'student', label: 'Öğrenci' },
]

/** Hem personel hem öğrenci olan hesapta görünüm seçimi (F-54); menü değiştiği için Bugün ekranına dönülür. */
function PanelSwitch({ panel }: { panel: Panel }) {
  const navigate = useNavigate()
  return (
    <div role="group" aria-label="Görünüm" className="mt-3 flex items-baseline gap-4 text-sm">
      <span className="text-ink-3">Görünüm</span>
      {PANELS.map((p) => (
        <button
          key={p.value}
          type="button"
          aria-pressed={panel === p.value}
          onClick={() => {
            if (panel === p.value) return
            setPanel(p.value)
            navigate('/')
          }}
          className={cn(
            'font-semibold underline-offset-4 hover:underline',
            panel === p.value ? 'text-ink underline decoration-2' : 'text-ink-3 hover:text-ink',
          )}
        >
          {p.label}
        </button>
      ))}
    </div>
  )
}

function UserBlock({ session }: { session: Session }) {
  // Profil yoksa (yönetici/görevli) e-posta ve JWT rolleriyle devam edilir (F-83).
  const { data: me } = useMe()
  const panel = usePanel()
  return (
    <div className="border-t border-rule px-6 py-5">
      <NavLink to="/profile" className="group block min-w-0">
        <span className="sr-only">Profilim: </span>
        <span className="block truncate text-md font-semibold group-hover:underline">{displayName(me, session.email)}</span>
        <span className="block truncate text-sm text-ink-3">{me?.programName ?? roleLabel(session)}</span>
      </NavLink>
      {panel && <PanelSwitch panel={panel} />}
      <NavLink
        to="/settings"
        className={({ isActive }) => cn('mt-2 inline-block text-sm font-semibold underline-offset-4 hover:underline', isActive ? 'text-ink' : 'text-ink-2')}
      >
        Ayarlar
      </NavLink>
      <div className="mt-4 flex items-center justify-between gap-2">
        <ThemeSwitch />
        <Button variant="ghost" size="sm" onClick={() => void logout()}>
          <LogOut className="size-4" aria-hidden />
          Çıkış yap
        </Button>
      </div>
    </div>
  )
}
