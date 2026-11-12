import { toast } from 'sonner'
import { toApiError } from '@/lib/api/problem'
import { useMe } from '@/features/me/useMe'
import { useLeaderboardPreference, useUpdateLeaderboardPreference, type LeaderboardPreference } from '@/features/profile/api'
import { ChoiceGroup } from '@/components/ui/ChoiceGroup'
import { Panel } from '@/components/ui/Panel'
import { QueryBoundary } from '@/components/ui/States'
import { Switch } from '@/components/ui/Switch'

/** "Elif Demir" → "E. D." */
function initials(first?: string | null, last?: string | null) {
  return [first, last]
    .filter(Boolean)
    .map((s) => `${s![0]!.toLocaleUpperCase('tr-TR')}.`)
    .join(' ')
}

/** Liderlik tablosu görünürlüğü (F-73): tabloda görünme ve görünen ad biçimi; KVKK açıklaması. */
export function LeaderboardSettings() {
  const pref = useLeaderboardPreference()
  const update = useUpdateLeaderboardPreference()
  const { data: me } = useMe()
  const full = [me?.firstName, me?.lastName].filter(Boolean).join(' ') || 'Adınız Soyadınız'

  const save = (next: LeaderboardPreference, message: string) =>
    update.mutate(next, { onSuccess: () => toast.success(message), onError: (e) => toast.error(toApiError(e).message) })

  return (
    <Panel title="Liderlik tablosu">
      <p className="max-w-[60ch] text-md text-ink-2">
        Topluluktaki katkılarınızdan gelen puanlar liderlik tablosunda sıralanır. Tabloda görünüp görünmeyeceğinizi ve adınızın nasıl yazılacağını
        siz seçersiniz; puanlarınız her durumda sayılmaya devam eder.
      </p>
      <QueryBoundary query={pref} what="Tercih" skeletonRows={2}>
        {(p) => (
          <div className="mt-6 flex max-w-[40rem] flex-col gap-8">
            <div className="flex items-center justify-between gap-6 border-y border-rule py-3">
              <span>
                <span className="block text-lg font-semibold">Tabloda görün</span>
                <span className="block text-sm text-ink-2">{p.visible ? 'Adınız ve puanınız tabloda görünür.' : 'Tabloda görünmezsiniz; yalnız kendi sıranızı görürsünüz.'}</span>
              </span>
              <Switch
                checked={p.visible}
                label="Liderlik tablosunda görün"
                onCheckedChange={(v) => save({ ...p, visible: v }, v ? 'Artık tabloda görünüyorsunuz' : 'Tablodan gizlendiniz')}
              />
            </div>
            <ChoiceGroup
              layout="rows"
              label="Görünen ad"
              value={p.displayMode}
              onChange={(v) => save({ ...p, displayMode: v }, 'Görünen ad güncellendi')}
              choices={[
                { value: 'FULL_NAME', title: 'Ad ve soyad', description: full },
                { value: 'INITIALS', title: 'Baş harfler', description: initials(me?.firstName, me?.lastName) || 'A. S.' },
              ]}
            />
            <p className="text-sm text-ink-3">
              Kişisel verilerin korunması: Tabloda yalnız seçtiğiniz görünen ad, puanınız ve rozetleriniz gösterilir. Öğrenci numaranız ve e-posta adresiniz
              paylaşılmaz. Tercihinizi istediğiniz zaman değiştirebilirsiniz.
            </p>
          </div>
        )}
      </QueryBoundary>
    </Panel>
  )
}
