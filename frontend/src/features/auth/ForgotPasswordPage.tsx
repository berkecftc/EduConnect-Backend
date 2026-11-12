import { usePageTitle } from '@/lib/usePageTitle'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { Link } from 'react-router-dom'
import { api } from '@/lib/api/client'
import { applyFieldErrors } from '@/lib/api/form'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { AuthLayout } from './AuthLayout'
import { forgotSchema, type ForgotValues } from './schemas'

export function ForgotPasswordPage() {
  usePageTitle('Şifre sıfırlama')
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const form = useForm<ForgotValues>({ resolver: zodResolver(forgotSchema), defaultValues: { email: '' } })
  const { register, handleSubmit, formState } = form

  const onSubmit = handleSubmit(async ({ email }) => {
    setError(null)
    try {
      await api.post('/auth/forgot-password', { email })
      setSentTo(email)
    } catch (err) {
      const e = toApiError(err)
      if (!applyFieldErrors(e, form.setError, ['email'])) setError(e)
    }
  })

  return (
    <AuthLayout>
      {sentTo ? (
        <>
          <h1 className="text-3xl">Bağlantıyı gönderdik</h1>
          <p className="mt-3 text-ink-2">
            <span className="font-semibold text-ink">{sentTo}</span> adresi kayıtlıysa şifre sıfırlama bağlantısı birkaç
            dakika içinde gelir. Gelen kutunuzu ve istenmeyen e-posta klasörünü kontrol edin.
          </p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Button asChild variant="primary">
              <Link to="/login">Girişe dön</Link>
            </Button>
            <Button variant="ghost" onClick={() => setSentTo(null)}>
              Başka adres dene
            </Button>
          </div>
        </>
      ) : (
        <>
          <h1 className="text-3xl">Şifrenizi sıfırlayın</h1>
          <p className="mt-2 text-ink-2">Kurum e-postanızı yazın, yeni şifre belirlemeniz için bağlantı gönderelim.</p>
          <form onSubmit={onSubmit} noValidate className="mt-8 flex flex-col gap-5">
            <Field label="E-posta" error={formState.errors.email?.message}>
              <Input type="email" autoComplete="email" inputMode="email" maxLength={255} {...register('email')} />
            </Field>
            {error && <Notice tone={error.status === 429 ? 'warning' : 'danger'} title={error.message} />}
            <Button type="submit" variant="primary" size="lg" loading={formState.isSubmitting} className="w-full">
              Bağlantı gönder
            </Button>
          </form>
          <p className="mt-8 text-md">
            <Link to="/login" className="font-semibold text-ders underline-offset-2 hover:underline">
              Girişe dön
            </Link>
          </p>
        </>
      )}
    </AuthLayout>
  )
}
