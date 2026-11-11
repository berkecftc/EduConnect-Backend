import { usePageTitle } from '@/lib/usePageTitle'
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '@/lib/api/client'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Spinner } from '@/components/ui/Spinner'
import { AuthLayout } from './AuthLayout'
import { forgotSchema, type ForgotValues } from './schemas'

type State = { kind: 'verifying' } | { kind: 'verified' } | { kind: 'failed'; error: ApiError } | { kind: 'resend' }

/**
 * E-posta doğrulama (F-66). Bağlantı `/verify-email?token=` ile gelir; jetonsuz açılırsa yeni bağlantı formu.
 */
export function VerifyEmailPage() {
  usePageTitle('E-posta doğrulama')
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<State>(token ? { kind: 'verifying' } : { kind: 'resend' })
  const started = useRef(false)

  useEffect(() => {
    // Jeton tek kullanımlık: StrictMode'un çift çalıştırmasında ikinci istek gitmemeli.
    if (!token || started.current) return
    started.current = true
    api
      .post('/auth/verify-email', { token })
      .then(() => setState({ kind: 'verified' }))
      .catch((err) => setState({ kind: 'failed', error: toApiError(err) }))
  }, [token])

  return (
    <AuthLayout>
      {state.kind === 'verifying' && (
        <div className="flex items-center gap-3 text-ink-2">
          <Spinner />
          <p>E-posta adresiniz doğrulanıyor</p>
        </div>
      )}

      {state.kind === 'verified' && (
        <>
          <h1 className="text-3xl">E-posta adresiniz doğrulandı</h1>
          <p className="mt-3 text-ink-2">Hesabınız onaylandıysa artık giriş yapabilirsiniz.</p>
          <Button asChild variant="primary" size="lg" className="mt-8">
            <Link to="/login">Giriş yap</Link>
          </Button>
        </>
      )}

      {state.kind === 'failed' && (
        <>
          <h1 className="text-3xl">
            {state.error.code === 'VERIFICATION_LINK_INVALID' ? 'Bağlantı geçersiz veya süresi dolmuş' : 'Doğrulanamadı'}
          </h1>
          <p className="mt-3 text-ink-2">
            {state.error.code === 'VERIFICATION_LINK_INVALID'
              ? 'E-posta adresinizi yazın, yeni bir doğrulama bağlantısı gönderelim.'
              : state.error.message}
          </p>
          <ResendForm />
        </>
      )}

      {state.kind === 'resend' && (
        <>
          <h1 className="text-3xl">Doğrulama bağlantısı iste</h1>
          <p className="mt-2 text-ink-2">Başvururken kullandığınız e-posta adresini yazın.</p>
          <ResendForm />
        </>
      )}
    </AuthLayout>
  )
}

function ResendForm() {
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const { register, handleSubmit, formState } = useForm<ForgotValues>({
    resolver: zodResolver(forgotSchema),
    defaultValues: { email: '' },
  })

  const onSubmit = handleSubmit(async ({ email }) => {
    setError(null)
    try {
      await api.post('/auth/resend-verification', { email })
      setSentTo(email)
    } catch (err) {
      setError(toApiError(err))
    }
  })

  if (sentTo) {
    return (
      <Notice tone="success" title="Bağlantı gönderildi" className="mt-8">
        {sentTo} adresi kayıtlı ve doğrulanmamışsa yeni bağlantı birkaç dakika içinde gelir.
      </Notice>
    )
  }

  return (
    <form onSubmit={onSubmit} noValidate className="mt-8 flex flex-col gap-5">
      <Field label="E-posta" error={formState.errors.email?.message}>
        <Input type="email" autoComplete="email" inputMode="email" maxLength={255} {...register('email')} />
      </Field>
      {error && <Notice tone={error.status === 429 ? 'warning' : 'danger'} title={error.message} />}
      <Button type="submit" variant="primary" size="lg" loading={formState.isSubmitting} className="w-full">
        Yeni bağlantı gönder
      </Button>
    </form>
  )
}
