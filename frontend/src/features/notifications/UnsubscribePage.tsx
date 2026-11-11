import { Link, useSearchParams } from 'react-router-dom'
import { toApiError } from '@/lib/api/problem'
import { usePageTitle } from '@/lib/usePageTitle'
import { AuthLayout } from '@/features/auth/AuthLayout'
import { Button } from '@/components/ui/Button'
import { Notice } from '@/components/ui/Notice'
import { useUnsubscribe } from './api'

/**
 * E-postadaki "abonelikten çık" bağlantısı (F-74): `/notifications/unsubscribe?token=`, oturum gerekmez.
 * E-posta istemcileri bağlantıyı önceden açabildiği için iş otomatik değil, düğmeyle yapılır.
 */
export function UnsubscribePage() {
  usePageTitle('E-postalardan çık')
  const [params] = useSearchParams()
  const token = params.get('token')
  const unsubscribe = useUnsubscribe()
  const error = unsubscribe.error ? toApiError(unsubscribe.error) : null

  return (
    <AuthLayout>
      {unsubscribe.isSuccess ? (
        <>
          <h1 className="text-3xl">E-postalar kapatıldı</h1>
          <p className="mt-3 text-ink-2">
            Artık “{unsubscribe.data.label}” konusunda e-posta almayacaksınız. Bu bildirimler uygulamada görünmeye devam eder; Ayarlar → Bildirimler'den
            yeniden açabilirsiniz.
          </p>
          <Button asChild variant="primary" size="lg" className="mt-8">
            <Link to="/settings/notifications">Bildirim ayarlarına git</Link>
          </Button>
        </>
      ) : (
        <>
          <h1 className="text-3xl">E-postalardan çık</h1>
          <p className="mt-3 text-ink-2">
            Bu bağlantı, aldığınız e-postanın konusundaki bildirimler için e-posta gönderimini kapatır. Uygulama içi bildirimler etkilenmez.
          </p>
          {!token && (
            <Notice variant="line" tone="warning" className="mt-6">
              Bağlantıda gerekli kod yok. E-postadaki bağlantıyı yeniden açın ya da ayarlardan değiştirin.
            </Notice>
          )}
          {error && (
            <Notice variant="line" tone="danger" className="mt-6" title={error.code === 'INVALID_UNSUBSCRIBE_TOKEN' ? 'Bağlantı geçersiz' : 'İşlem yapılamadı'}>
              {error.code === 'INVALID_UNSUBSCRIBE_TOKEN' ? 'Bağlantının süresi dolmuş olabilir. Tercihlerinizi ayarlardan değiştirebilirsiniz.' : error.message}
            </Notice>
          )}
          <div className="mt-8 flex flex-wrap items-center gap-5">
            <Button variant="primary" size="lg" disabled={!token} loading={unsubscribe.isPending} onClick={() => token && unsubscribe.mutate(token)}>
              E-postalardan çık
            </Button>
            <Link to="/settings/notifications" className="text-md font-semibold underline-offset-4 hover:underline">
              Ayarlardan değiştir
            </Link>
          </div>
        </>
      )}
    </AuthLayout>
  )
}
