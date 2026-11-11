const number = new Intl.NumberFormat('tr-TR', { maximumFractionDigits: 2 })

/** Türkçe sayı biçimi: 17.5 → "17,5". */
export function formatNumber(value: number | null | undefined): string {
  return value === null || value === undefined || Number.isNaN(value) ? '' : number.format(value)
}
