import { usePageTitle } from '@/lib/usePageTitle'
import type { ReactNode } from 'react'
import { toast } from 'sonner'
import { LINES, type LineKey } from '@/design/lines'
import { Button } from '@/components/ui/Button'
import { Field } from '@/components/ui/Field'
import { Input, PasswordInput } from '@/components/ui/Input'
import { LineBullet } from '@/components/ui/LineBullet'
import { Notice } from '@/components/ui/Notice'
import { StationLine } from '@/components/ui/StationLine'
import { ThemeSwitch } from '@/components/ui/ThemeSwitch'
import { Wordmark } from '@/components/ui/Wordmark'

/**
 * Canlı tasarım sistemi: token'lar ve temel bileşenler. Yalnız geliştirmede açılır (/design).
 */
export function DesignSystemPage() {
  usePageTitle('Tasarım sistemi')
  return (
    <div className="mx-auto max-w-[68rem] px-4 py-8 sm:px-8">
      <header className="flex flex-wrap items-center justify-between gap-4 border-b border-rule pb-6">
        <Wordmark />
        <ThemeSwitch />
      </header>
      <h1 className="mt-10 text-4xl">Hat tasarım sistemi</h1>
      <p className="mt-3 max-w-[60ch] text-ink-2">
        Kampüs bir ulaşım ağı gibi okunur: her alan bir hat, her süreç bir durak dizisi. Ekranlar yalnız buradaki
        token'lar ve bileşenlerle kurulur.
      </p>

      <Section title="Hatlar">
        <ul className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {(Object.keys(LINES) as LineKey[]).map((key) => (
            <li key={key} className="flex items-center gap-3 rounded-md border border-rule bg-surface p-3">
              <LineBullet line={key} size="lg" />
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{LINES[key].label}</p>
                <p className={`text-sm ${LINES[key].text}`}>Metin rengi olarak</p>
              </div>
              <span aria-hidden className={`h-9 w-12 rounded-sm ${LINES[key].soft}`} />
            </li>
          ))}
        </ul>
      </Section>

      <Section title="Yüzeyler ve metin">
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
          {[
            ['canvas', 'bg-canvas', 'Zemin'],
            ['surface', 'bg-surface', 'Yüzey'],
            ['sunken', 'bg-sunken', 'Gömük'],
            ['rule', 'bg-rule', 'Çizgi'],
            ['ink-2', 'bg-ink-2', 'İkincil metin'],
            ['ink', 'bg-ink', 'Metin'],
          ].map(([name, cls, label]) => (
            <li key={name} className="text-sm">
              <span aria-hidden className={`block h-14 rounded-sm border border-rule ${cls}`} />
              <span className="mt-1.5 block font-semibold">{label}</span>
              <span className="text-ink-3">{name}</span>
            </li>
          ))}
        </ul>
        <div className="mt-6 grid gap-3 sm:grid-cols-2">
          <Notice tone="info" title="Bilgi">Durum değişikliği yoktur, yalnız bilgilendirir.</Notice>
          <Notice tone="success" title="Başvurunuz onaylandı">Robotik Kulübü üyeliğiniz başladı.</Notice>
          <Notice tone="warning" title="Kayıt 2 gün içinde kapanıyor">Arduino atölyesi, 12/40 dolu.</Notice>
          <Notice tone="danger" title="Teslim süresi doldu">Geç teslim için hocanızdan süre uzatması isteyin.</Notice>
        </div>
      </Section>

      <Section title="Tipografi">
        <div className="flex flex-col gap-4">
          <p className="text-4xl font-heavy">Bahar şenliği standı onaylandı</p>
          <p className="text-3xl font-heavy">Normalizasyon ödevi teslimi</p>
          <p className="text-2xl font-heavy">BİL 304 Veri Tabanı Sistemleri</p>
          <p className="text-xl font-heavy">Kulüp karar defteri, 2025-2026/3</p>
          <p className="text-lg font-semibold">Danışman onayında, 3 Eki 09:42</p>
          <p className="max-w-[65ch] text-base">
            Gövde metni: Ödevin son tarihi 12 Ekim 23:59. Geç teslim 14 Ekim'e kadar açık; her geç teslimde puandan %20
            kesilir. Türkçe karakterler: ğ ü ş ı ö ç İ Ğ Ü Ş Ö Ç.
          </p>
          <p className="text-md text-ink-2">Yoğun liste metni, 14px</p>
          <p className="text-sm text-ink-3">Yardımcı metin ve tarih, 13px</p>
          <p className="tabular text-xl font-semibold">08:30 12:45 17,50 / 20 1000,00 TL</p>
        </div>
      </Section>

      <Section title="Durak çizgisi">
        <div className="grid gap-10">
          <StationLine
            line="kulup"
            label="Bahar şenliği standı onay durumu"
            stations={[
              { label: 'Başvuru', state: 'done', detail: '2 Eki 14:10' },
              { label: 'Başkan', state: 'done', detail: '3 Eki 09:42' },
              { label: 'Danışman', state: 'current', detail: 'Bekliyor' },
              { label: 'Yayın', state: 'pending' },
            ]}
          />
          <StationLine
            line="ders"
            label="Ders başvurusu durumu"
            stations={[
              { label: 'Başvuru', state: 'done', detail: '28 Eyl' },
              { label: 'Hoca onayı', state: 'rejected', detail: 'Kontenjan dolu' },
              { label: 'Kayıt', state: 'pending' },
            ]}
          />
          <StationLine
            line="topluluk"
            label="Gönderi moderasyon durumu"
            stations={[
              { label: 'Gönderildi', state: 'done' },
              { label: 'Moderatör incelemesi', state: 'current' },
              { label: 'Yayında', state: 'pending' },
            ]}
          />
        </div>
      </Section>

      <Section title="Düğmeler">
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="primary">Başvuruyu gönder</Button>
          <Button>Taslak olarak kaydet</Button>
          <Button variant="ghost">Vazgeç</Button>
          <Button variant="danger">Kulübü kapat</Button>
          <Button variant="primary" loading>
            Gönderiliyor
          </Button>
          <Button size="sm">Küçük</Button>
          <Button size="lg" variant="primary">
            Büyük
          </Button>
          <Button onClick={() => toast.success('Puanlar ilan edildi', { description: 'BİL 304, Normalizasyon ödevi' })}>
            Bildirim göster
          </Button>
        </div>
      </Section>

      <Section title="Form alanları">
        <div className="grid max-w-[28rem] gap-5">
          <Field label="Ders kodu" hint="Katalogdaki kodla aynı, ör. BİL 304">
            <Input placeholder="BİL 304" maxLength={255} />
          </Field>
          <Field label="Ağırlık (%)" error="Ders toplamı 100'ü geçiyor. Kalan: %15">
            <Input inputMode="decimal" defaultValue="20" />
          </Field>
          <Field label="Şifre">
            <PasswordInput defaultValue="örnek-şifre" />
          </Field>
          <Field label="Devre dışı">
            <Input disabled defaultValue="Dönem: 2025-2026 Güz" />
          </Field>
        </div>
      </Section>
    </div>
  )
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="mt-14">
      <h2 className="mb-5 border-b border-rule pb-2 text-xl">{title}</h2>
      {children}
    </section>
  )
}
