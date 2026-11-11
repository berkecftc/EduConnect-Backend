import { usePageTitle } from '@/lib/usePageTitle'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { api, login } from '@/lib/api/client'
import { applyFieldErrors } from '@/lib/api/form'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input, PasswordInput } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { StationLine } from '@/components/ui/StationLine'
import { AuthLayout } from './AuthLayout'
import { loginSchema, type LoginValues } from './schemas'

/** Kilit kodları: süre `Retry-After` başlığından gelir (F-03, F-83). */
const LOCK_CODES = new Set(['LOGIN_LOCKED', 'RATE_LIMITED', 'TOO_MANY_REQUESTS'])

/** Her saniye güncellenen "şimdi"; yalnız etkinken sayar. */
function useNow(active: boolean): [number, () => void] {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    if (!active) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [active])
  return [now, () => setNow(Date.now())]
}

/** Şu andan itibaren verilen saniye sonrasının zaman damgası. */
function secondsFromNow(seconds: number) {
  return Date.now() + seconds * 1000
}

function formatClock(seconds: number) {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function LoginPage() {
  usePageTitle('Giriş yap')
  const session = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: string } | null)?.from ?? '/'
  const [error, setError] = useState<ApiError | null>(null)
  const [pending, setPending] = useState<string | null>(null)
  const [lockedUntil, setLockedUntil] = useState<number | null>(null)
  const [now, refreshNow] = useNow(lockedUntil !== null)
  const remaining = lockedUntil ? Math.max(0, Math.ceil((lockedUntil - now) / 1000)) : 0
  const locked = remaining > 0

  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: '', password: '' } })
  const { register, handleSubmit, formState, getValues } = form

  if (session) return <Navigate to={from} replace />

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      await login(values.email, values.password)
      navigate(from, { replace: true })
    } catch (err) {
      const e = toApiError(err)
      if (e.code === 'ACCOUNT_PENDING_APPROVAL') {
        setPending(e.message)
        return
      }
      if (LOCK_CODES.has(e.code) && e.retryAfter) {
        setLockedUntil(secondsFromNow(e.retryAfter))
        refreshNow()
      }
      if (!applyFieldErrors(e, form.setError, ['email', 'password'])) setError(e)
    }
  })

  if (pending) {
    return (
      <AuthLayout>
        <h1 className="text-3xl">Başvurunuz inceleniyor</h1>
        <p className="mt-3 text-ink-2">{pending}</p>
        <StationLine
          className="mt-8"
          line="yonetim"
          label="Hesap başvurusu durumu"
          stations={[
            { label: 'Başvuru', state: 'done' },
            { label: 'E-posta', state: 'done' },
            { label: 'Doğrulayıcı onayı', state: 'current' },
            { label: 'Hesap etkin', state: 'pending' },
          ]}
        />
        <p className="mt-8 text-md text-ink-2">Onaylandığında e-postayla haber veririz; ardından aynı bilgilerle giriş yapabilirsiniz.</p>
        <Button variant="secondary" className="mt-6" onClick={() => setPending(null)}>
          Girişe dön
        </Button>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h1 className="text-3xl">Giriş yap</h1>
      <p className="mt-2 text-ink-2">Kurum e-postanız ve şifrenizle devam edin.</p>

      <form onSubmit={onSubmit} noValidate className="mt-8 flex flex-col gap-5">
        <Field label="E-posta" error={formState.errors.email?.message}>
          <Input
            type="email"
            autoComplete="username"
            inputMode="email"
            maxLength={255}
            placeholder="ad.soyad@ogr.universite.edu.tr"
            {...register('email')}
          />
        </Field>
        <Field
          label="Şifre"
          error={formState.errors.password?.message}
          action={
            <Link to="/forgot-password" className="text-sm font-semibold text-ders underline-offset-2 hover:underline">
              Şifremi unuttum
            </Link>
          }
        >
          <PasswordInput autoComplete="current-password" maxLength={128} {...register('password')} />
        </Field>

        {locked ? (
          <Notice tone="warning" title="Giriş geçici olarak durduruldu">
            <span aria-hidden className="tabular font-semibold text-ink">
              {formatClock(remaining)}
            </span>
            <span aria-hidden> sonra tekrar deneyebilirsiniz.</span>
            <span className="sr-only">
              Çok fazla deneme yapıldı. Yaklaşık {Math.max(1, Math.ceil(remaining / 60))} dakika sonra tekrar deneyin.
            </span>
          </Notice>
        ) : (
          error && <LoginError error={error} email={getValues('email')} />
        )}

        <Button
          type="submit"
          variant="primary"
          size="lg"
          loading={formState.isSubmitting}
          disabled={locked}
          className="mt-1 w-full"
        >
          Giriş yap
        </Button>
      </form>

      <p className="mt-8 text-md text-ink-2">
        Hesabınız yok mu?{' '}
        <Link to="/register" className="font-semibold text-ders underline-offset-2 hover:underline">
          Hesap başvurusu yapın
        </Link>
      </p>
    </AuthLayout>
  )
}

/** `errorCode`'a göre hata gösterimi (F-83). Mesaj metni backend'den gelir. */
function LoginError({ error, email }: { error: ApiError; email: string }) {
  if (error.code === 'EMAIL_NOT_VERIFIED') {
    return (
      <Notice tone="warning" title="E-posta adresinizi doğrulayın" action={<ResendVerification email={email} />}>
        {error.message}
      </Notice>
    )
  }
  if (error.code === 'ACCOUNT_SUSPENDED') {
    return (
      <Notice tone="danger" title="Hesabınız askıya alındı">
        {error.message} Bir yanlışlık olduğunu düşünüyorsanız öğrenci işleri ya da personel daire başkanlığıyla
        iletişime geçin.
      </Notice>
    )
  }
  return <Notice tone={error.status === 429 ? 'warning' : 'danger'} title={error.message} />
}

/** Yazılan adrese yeni doğrulama bağlantısı gönderir. */
function ResendVerification({ email }: { email: string }) {
  const [state, setState] = useState<'idle' | 'sending' | 'sent' | ApiError>('idle')

  if (state === 'sent') {
    return <p className="text-sm font-semibold text-success">Bağlantı {email} adresine gönderildi.</p>
  }
  return (
    <div className="flex flex-col gap-1.5">
      <Button
        size="sm"
        loading={state === 'sending'}
        onClick={async () => {
          setState('sending')
          try {
            await api.post('/auth/resend-verification', { email })
            setState('sent')
          } catch (err) {
            setState(toApiError(err))
          }
        }}
        className="self-start"
      >
        Doğrulama bağlantısını yeniden gönder
      </Button>
      {typeof state === 'object' && <p className="text-sm font-semibold text-danger">{state.message}</p>}
    </div>
  )
}
