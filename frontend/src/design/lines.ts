/**
 * Hatlar: EduConnect'in alanları. Renk yalnız alanı anlatır.
 * Tailwind sınıfları derleme anında taranır; bu yüzden tam sınıf adları burada sabit yazılı.
 */
export type LineKey = 'ders' | 'kulup' | 'etkinlik' | 'topluluk' | 'yonetim'

export const LINES: Record<
  LineKey,
  { label: string; letter: string; fill: string; text: string; soft: string; border: string; stroke: string }
> = {
  ders: {
    label: 'Dersler',
    letter: 'D',
    fill: 'bg-ders text-ders-on',
    text: 'text-ders',
    soft: 'bg-ders-soft',
    border: 'border-ders',
    stroke: 'var(--ec-ders)',
  },
  kulup: {
    label: 'Kulüpler',
    letter: 'K',
    fill: 'bg-kulup text-kulup-on',
    text: 'text-kulup',
    soft: 'bg-kulup-soft',
    border: 'border-kulup',
    stroke: 'var(--ec-kulup)',
  },
  etkinlik: {
    label: 'Etkinlikler',
    letter: 'E',
    fill: 'bg-etkinlik text-etkinlik-on',
    text: 'text-etkinlik',
    soft: 'bg-etkinlik-soft',
    border: 'border-etkinlik',
    stroke: 'var(--ec-etkinlik)',
  },
  topluluk: {
    label: 'Topluluk',
    letter: 'T',
    fill: 'bg-topluluk text-topluluk-on',
    text: 'text-topluluk',
    soft: 'bg-topluluk-soft',
    border: 'border-topluluk',
    stroke: 'var(--ec-topluluk)',
  },
  yonetim: {
    label: 'Yönetim',
    letter: 'Y',
    fill: 'bg-yonetim text-yonetim-on',
    text: 'text-yonetim',
    soft: 'bg-yonetim-soft',
    border: 'border-yonetim',
    stroke: 'var(--ec-yonetim)',
  },
}
