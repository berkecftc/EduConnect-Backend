import { useEffect, useState, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MotionConfig } from 'motion/react'
import { Toaster } from 'sonner'
import { Tooltip } from 'radix-ui'
import { ApiError } from '@/lib/api/problem'
import { subscribe, getSession } from '@/lib/auth/session'
import { useTheme } from '@/lib/theme'

function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 30_000,
        refetchOnWindowFocus: true,
        // 4xx yeniden denenmez; ağ ve 5xx hatası iki kez denenir. 429'da sunucunun söylediği süre beklenir.
        retry: (count, err) => {
          if (!(err instanceof ApiError)) return count < 2
          if (err.status === 429) return count < 1
          return (err.status === 0 || err.status >= 500) && count < 2
        },
        retryDelay: (attempt, err) =>
          err instanceof ApiError && err.retryAfter ? err.retryAfter * 1000 : Math.min(1000 * 2 ** attempt, 8000),
      },
      mutations: { retry: false },
    },
  })
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient)
  const theme = useTheme()

  // Oturum kapanınca önbellekteki kullanıcı verisini sil.
  useEffect(() => {
    let hadSession = !!getSession()
    return subscribe(() => {
      const has = !!getSession()
      if (hadSession && !has) queryClient.clear()
      hadSession = has
    })
  }, [queryClient])

  return (
    <QueryClientProvider client={queryClient}>
      <MotionConfig reducedMotion="user">
        <Tooltip.Provider delayDuration={300}>
          {children}
          <Toaster
            position="bottom-center"
            theme={theme}
            toastOptions={{
              classNames: {
                toast: '!bg-surface !text-ink !border-rule !rounded-md !font-sans !shadow-pop',
                description: '!text-ink-2',
                error: '!border-danger/50',
                success: '!border-success/50',
              },
            }}
          />
        </Tooltip.Provider>
      </MotionConfig>
    </QueryClientProvider>
  )
}
