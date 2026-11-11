import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { toast } from 'sonner'
import { toApiError, type ApiError } from '@/lib/api/problem'
import { useSession } from '@/lib/auth/session'
import { newPasswordField } from '@/features/auth/schemas'
import { useChangePassword } from '@/features/profile/api'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { PasswordInput } from '@/components/ui/Input'
import { Notice } from '@/components/ui/Notice'
import { Panel } from '@/components/ui/Panel'
import { AffiliationSettings } from './AffiliationSettings'

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Mevcut şifrenizi girin'),
    newPassword: newPasswordField,
    confirmationPassword: z.string().min(1, 'Yeni şifreyi tekrar girin'),
  })
  .refine((v) => v.newPassword === v.confirmationPassword, { path: ['confirmationPassword'], message: 'Şifreler eşleşmiyor' })
  .refine((v) => v.newPassword !== v.currentPassword, { path: ['newPassword'], message: 'Yeni şifre eskisiyle aynı olamaz' })
type PasswordValues = z.infer<typeof passwordSchema>

/** Hesap: şifre değişikliği; kurum e-posta adresi yalnız gösterilir, değiştirilemez. */
export function AccountSettings() {
  return (
    <div className="flex flex-col gap-20">
      <AffiliationSettings />
      <PasswordForm />
      <EmailAddress />
    </div>
  )
}

function PasswordForm() {
  const change = useChangePassword()
  const [failure, setFailure] = useState<ApiError | null>(null)
  const form = useForm<PasswordValues>({ resolver: zodResolver(passwordSchema), defaultValues: { currentPassword: '', newPassword: '', confirmationPassword: '' } })
  const { errors } = form.formState

  const onSubmit = form.handleSubmit((v) => {
    setFailure(null)
    change.mutate(v, {
      onSuccess: () => {
        toast.success('Şifreniz değiştirildi')
        form.reset()
      },
      onError: (err) => {
        const ae = toApiError(err)
        if (ae.code === 'WRONG_CURRENT_PASSWORD') form.setError('currentPassword', { message: ae.message })
        else if (ae.code === 'PASSWORD_UNCHANGED') form.setError('newPassword', { message: ae.message })
        else if (ae.code === 'PASSWORD_CONFIRMATION_MISMATCH') form.setError('confirmationPassword', { message: ae.message })
        else setFailure(ae)
      },
    })
  })

  return (
    <Panel title="Şifre">
      <form onSubmit={onSubmit} noValidate className="flex max-w-[28rem] flex-col gap-5">
        <Field label="Mevcut şifre" required error={errors.currentPassword?.message}>
          <PasswordInput autoComplete="current-password" {...form.register('currentPassword')} />
        </Field>
        <Field label="Yeni şifre" required hint="En az 10 karakter; harf, rakam ve işaretleri karıştırın." error={errors.newPassword?.message}>
          <PasswordInput autoComplete="new-password" {...form.register('newPassword')} />
        </Field>
        <Field label="Yeni şifre (tekrar)" required error={errors.confirmationPassword?.message}>
          <PasswordInput autoComplete="new-password" {...form.register('confirmationPassword')} />
        </Field>
        {failure && (
          <Notice variant="line" tone="danger" title="Şifre değiştirilemedi">
            {failure.message}
          </Notice>
        )}
        <div>
          <Button type="submit" variant="primary" loading={change.isPending}>
            Şifreyi değiştir
          </Button>
        </div>
      </form>
    </Panel>
  )
}

/** Kurum e-postası okul tarafından verilir; giriş ve bildirimler bu adresi kullanır, değiştirilemez. */
function EmailAddress() {
  const session = useSession()
  return (
    <Panel title="E-posta adresi">
      <p className="text-xl font-semibold break-all">{session?.email}</p>
      <p className="mt-2 max-w-[60ch] text-md text-ink-2">
        Kurum e-posta adresiniz okul tarafından verilir ve değiştirilemez. Girişte ve bildirim e-postalarında bu adres kullanılır.
      </p>
    </Panel>
  )
}
