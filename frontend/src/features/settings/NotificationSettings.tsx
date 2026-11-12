import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { Panel } from '@/components/ui/Panel'
import { QueryBoundary } from '@/components/ui/States'
import { Switch } from '@/components/ui/Switch'
import { CATEGORY_META, usePreferences, useUpdatePreference } from '@/features/notifications/api'

/**
 * Bildirim ayarları (F-74): uygulama içi bildirim her zaman gelir; burada yalnız e-posta seçilir.
 * Zorunlu (hizmet) kategorilerde anahtar kilitlidir ve nedeni yanında yazar.
 */
export function NotificationSettings() {
  const prefs = usePreferences()
  const update = useUpdatePreference()

  return (
    <Panel title="E-posta bildirimleri">
      <p className="max-w-[60ch] text-md text-ink-2">
        Bildirimlerin hepsi uygulamada, zildeki sayıda ve Bildirimler sayfasında görünür; bu kapatılamaz. Aşağıda hangi konuların ayrıca e-posta
        adresinize gönderileceğini seçersiniz.
      </p>
      <QueryBoundary query={prefs} what="Bildirim ayarları" skeletonRows={5}>
        {(list) => (
          <ul className="mt-6 border-t border-rule">
            {list.map((p) => {
              const id = `tercih-${p.category}`
              return (
                <li key={p.category} className="flex items-center justify-between gap-6 border-b border-rule py-3">
                  <span className="flex min-w-0 items-start gap-3">
                    <span aria-hidden className="mt-1.5 h-3 w-[3px] shrink-0" style={{ background: CATEGORY_META[p.category].line }} />
                    <span className="min-w-0">
                      <span id={id} className="block text-lg font-semibold">
                        {p.label}
                      </span>
                      <span className="block text-sm text-ink-2">
                        {p.mandatory ? 'Hizmet bildirimi; e-postası kapatılamaz.' : p.emailEnabled ? 'E-posta gönderilir.' : 'Yalnız uygulamada.'}
                      </span>
                    </span>
                  </span>
                  <Switch
                    checked={p.emailEnabled || p.mandatory}
                    disabled={p.mandatory}
                    label={`${p.label} e-postaları`}
                    onCheckedChange={(v) =>
                      update.mutate(
                        { category: p.category, emailEnabled: v },
                        {
                          onSuccess: () => toast.success(v ? `${p.label} e-postaları açıldı` : `${p.label} e-postaları kapatıldı`),
                          onError: (e) => toast.error(toApiError(e).message),
                        },
                      )
                    }
                  />
                </li>
              )
            })}
          </ul>
        )}
      </QueryBoundary>
    </Panel>
  )
}
