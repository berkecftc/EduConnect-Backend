import type { LineKey } from '@/design/lines'

export type BlockKey = Exclude<LineKey, 'yonetim'>

/**
 * Logonun dört kolu; her biri bir alan. `angle` kolun merkezden yönü (derece; 0 = sağ, 90 = üst).
 * Logo: solda Dersler, üstte Kulüpler, sağda Etkinlikler, altta Topluluk; ortada aktarma halkası.
 * Ayrıkken simgeler kolun 45° gerisindeki çapraz köşede durur.
 */
export const BLOCKS: { key: BlockKey; angle: number; summary: string }[] = [
  { key: 'ders', angle: 180, summary: 'Kayıt, materyal, ödev ve notlar' },
  { key: 'kulup', angle: 90, summary: 'Üyelik, görevler ve onaylar' },
  { key: 'etkinlik', angle: 0, summary: 'Kayıt, bilet ve yoklama' },
  { key: 'topluluk', angle: -90, summary: 'Sorular, cevaplar ve duyurular' },
]

/** Sahne etiketlerinin anahtarı. */
export const labelId = (key: BlockKey) => `etiket-${key}`
