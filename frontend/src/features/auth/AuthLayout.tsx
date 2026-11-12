import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { ThemeSwitch } from '@/components/ui/ThemeSwitch'
import { Wordmark } from '@/components/ui/Wordmark'
import { ModulesPanel } from './modules/ModulesPanel'

/** `wide`: uzun formlar (hesap başvurusu) için daha geniş sütun. */
export function AuthLayout({ children, wide = false }: { children: ReactNode; wide?: boolean }) {
  return (
    <div className="grid min-h-dvh lg:h-dvh lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)]">
      <div className="flex flex-col px-4 py-6 sm:px-10 lg:overflow-y-auto lg:px-14 lg:py-10">
        <header className="flex items-center justify-between gap-4">
          <Link to="/login" className="rounded-sm" aria-label="EduConnect giriş sayfası">
            <Wordmark />
          </Link>
          <ThemeSwitch />
        </header>
        <main className="flex flex-1 flex-col justify-center py-12">
          <div className={wide ? 'w-full max-w-[32rem]' : 'w-full max-w-[26rem]'}>{children}</div>
        </main>
      </div>
      <ModulesPanel />
    </div>
  )
}
