import { usePageTitle } from '@/lib/usePageTitle'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link, useSearchParams } from 'react-router-dom'
import { api } from '@/lib/api/client'
import { applyFieldErrors } from '@/lib/api/form'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { PasswordInput } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { AuthLayout } from './AuthLayout'
import { PASSWORD_MIN, resetSchema, type ResetValues } from './schemas'

/**
 * Şifre sıfırlama ve görevli hesabı kurulumu (F-57: `?setup=1` ile başlık "Şifrenizi belirleyin").
 */
export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const isSetup = params.get('setup') === '1'
  const [done, setDone] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const form = useForm<ResetValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })
  const { register, handleSubmit, formState } = form
  const title = isSetup ? 'Şifrenizi belirleyin' : 'Yeni şifre belirleyin'
  usePageTitle(title)

  const onSubmit = handleSubmit(async (values) => {
    setError(null)
    try {
      await api.post('/auth/reset-password', { token, ...values })
      setDone(true)
    } catch (err) {
      const e = toApiError(err)
      if (!applyFieldErrors(e, form.setError, ['newPassword', 'confirmPassword'])) setError(e)
    }
  })

  if (!token) {
    return (
      <AuthLayout>
        <h1 className="text-3xl">Bağlantı eksik</h1>
        <p className="mt-3 text-ink-2">
          Bu sayfaya e-postadaki bağlantıyla gelmeniz gerekiyor. Bağlantının süresi dolduysa yenisini isteyin.
        </p>
        <Button asChild variant="primary" className="mt-8">
          <Link to="/forgot-password">Yeni bağlantı iste</Link>
        </Button>
      </AuthLayout>
    )
  }

  if (done) {
    return (
      <AuthLayout>
        <h1 className="text-3xl">{isSetup ? 'Şifreniz belirlendi' : 'Şifreniz değişti'}</h1>
        <p className="mt-3 text-ink-2">Yeni şifrenizle giriş yapabilirsiniz.</p>
        <Button asChild variant="primary" size="lg" className="mt-8">
          <Link to="/login">Giriş yap</Link>
        </Button>
      </AuthLayout>
    )
  }

  return (
    <AuthLayout>
      <h1 className="text-3xl">{title}</h1>
      <p className="mt-2 text-ink-2">Hesabınız için kullanacağınız şifreyi iki kez yazın.</p>
      <form onSubmit={onSubmit} noValidate className="mt-8 flex flex-col gap-5">
        <Field
          label="Yeni şifre"
          hint={`En az ${PASSWORD_MIN} karakter. E-posta adresinizi içermesin.`}
          error={formState.errors.newPassword?.message}
        >
          <PasswordInput autoComplete="new-password" maxLength={128} {...register('newPassword')} />
        </Field>
        <Field label="Yeni şifre (tekrar)" error={formState.errors.confirmPassword?.message}>
          <PasswordInput autoComplete="new-password" maxLength={128} {...register('confirmPassword')} />
        </Field>
        {error && (
          <Notice
            tone="danger"
            title={error.message}
            action={
              error.status === 400 ? (
                <Link to="/forgot-password" className="text-sm font-semibold text-ders underline-offset-2 hover:underline">
                  Yeni bağlantı iste
                </Link>
              ) : undefined
            }
          />
        )}
        <Button type="submit" variant="primary" size="lg" loading={formState.isSubmitting} className="w-full">
          {isSetup ? 'Şifreyi belirle' : 'Şifreyi değiştir'}
        </Button>
      </form>
    </AuthLayout>
  )
}
