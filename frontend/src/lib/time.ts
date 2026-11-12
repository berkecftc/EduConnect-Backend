/**
 * Zaman biçimleri (F-24, F-81):
 * - Kayıt anları (`createdAt` …) UTC `...Z` gelir → Türkiye saatine çevrilerek gösterilir.
 * - Planlanan saatler (`startsAt`, `dueDate` …) bölgesiz yerel saattir → dönüştürülmeden gösterilir.
 */

export const TIME_ZONE = 'Europe/Istanbul'
const LOCALE = 'tr-TR'

type Style = 'date' | 'datetime' | 'time' | 'short' | 'long'

const OPTIONS: Record<Style, Intl.DateTimeFormatOptions> = {
  date: { day: 'numeric', month: 'long', year: 'numeric' },
  datetime: { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' },
  time: { hour: '2-digit', minute: '2-digit' },
  short: { day: 'numeric', month: 'short' },
  long: { weekday: 'long', day: 'numeric', month: 'long' },
}

const LOCAL_RE = /^(\d{4})-(\d{2})-(\d{2})(?:T(\d{2}):(\d{2})(?::(\d{2}))?)?/

/** Bölgesiz yerel ISO metnini, saat dilimi dönüşümü yapmadan biçimlemek için UTC Date'e çevirir. */
function localToUtcDate(value: string): Date | null {
  const m = LOCAL_RE.exec(value)
  if (!m) return null
  const [, y, mo, d, h = '0', mi = '0', s = '0'] = m
  return new Date(Date.UTC(+y!, +mo! - 1, +d!, +h, +mi, +s))
}

/** UTC kayıt anını Türkiye saatiyle gösterir. */
export function formatInstant(value: string | null | undefined, style: Style = 'datetime'): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat(LOCALE, { ...OPTIONS[style], timeZone: TIME_ZONE }).format(date)
}

/** Bölgesiz planlanan saati olduğu gibi gösterir. */
export function formatLocal(value: string | null | undefined, style: Style = 'datetime'): string {
  if (!value) return ''
  const date = localToUtcDate(value)
  if (!date) return ''
  return new Intl.DateTimeFormat(LOCALE, { ...OPTIONS[style], timeZone: 'UTC' }).format(date)
}

/**
 * Etkinlik aralığı: aynı gün "12 Eki 18:30–20:30", farklı günler "12 Eki 18:30 – 14 Eki 17:00" (F-61).
 */
export function formatLocalRange(start: string, end?: string | null): string {
  const s = localToUtcDate(start)
  if (!s) return ''
  const e = end ? localToUtcDate(end) : null
  const day = (d: Date) => formatLocal(d.toISOString().slice(0, 19), 'short')
  const time = (d: Date) => formatLocal(d.toISOString().slice(0, 19), 'time')
  if (!e) return `${day(s)} ${time(s)}`
  if (s.toISOString().slice(0, 10) === e.toISOString().slice(0, 10)) {
    return `${day(s)} ${time(s)}–${time(e)}`
  }
  return `${day(s)} ${time(s)} – ${day(e)} ${time(e)}`
}

/** `<input type="datetime-local">` değerini backend'in beklediği bölgesiz ISO'ya çevirir (F-48). */
export function toLocalIso(inputValue: string): string {
  return inputValue.length === 16 ? `${inputValue}:00` : inputValue
}

/** Şu anki Türkiye saatini bölgesiz ISO olarak verir; planlanan saatlerle karşılaştırmak için. */
export function nowLocalIso(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('sv-SE', {
    timeZone: TIME_ZONE,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).format(now)
  return parts.replace(' ', 'T')
}

const relative = new Intl.RelativeTimeFormat(LOCALE, { numeric: 'auto' })

/** UTC kayıt anını göreli yazar: "az önce", "5 dakika önce", "2 saat önce", "dün"; bir haftadan eskiyse tarih. */
export function formatRelative(value: string | null | undefined, now: number = Date.now()): string {
  if (!value) return ''
  const t = new Date(value).getTime()
  if (Number.isNaN(t)) return ''
  const minutes = Math.round((t - now) / 60_000)
  if (Math.abs(minutes) < 1) return 'az önce'
  if (Math.abs(minutes) < 60) return relative.format(minutes, 'minute')
  const hours = Math.round(minutes / 60)
  if (Math.abs(hours) < 24) return relative.format(hours, 'hour')
  const days = Math.round(hours / 24)
  if (Math.abs(days) < 7) return relative.format(days, 'day')
  return formatInstant(value, 'short')
}

/** Günün saatine göre selam (Türkiye saati). */
export function greeting(now: string = nowLocalIso()): string {
  const h = Number(now.slice(11, 13))
  if (h < 5) return 'İyi geceler'
  if (h < 12) return 'Günaydın'
  if (h < 18) return 'İyi günler'
  return 'İyi akşamlar'
}

/** Bölgesiz yerel ISO'yu karşılaştırılabilir zaman damgasına çevirir (yalnız farklar için; saat dilimi önemsizdir). */
export function localStamp(value: string): number {
  return localToUtcDate(value)?.getTime() ?? Number.NaN
}

/** İki bölgesiz yerel saat arasındaki fark (ms): `to - from`. */
export function localDiffMs(from: string, to: string): number {
  return localStamp(to) - localStamp(from)
}

/** Kalan süreyi kısa Türkçe metne çevirir: "45 dakika", "5 saat", "3 gün". Süre geçtiyse `null`. */
export function formatRemaining(ms: number): string | null {
  if (!(ms > 0)) return null
  const minutes = Math.floor(ms / 60_000)
  if (minutes < 60) return `${Math.max(1, minutes)} dakika`
  const hours = Math.floor(minutes / 60)
  if (hours < 48) return `${hours} saat`
  return `${Math.floor(hours / 24)} gün`
}

/** Yerel günün etiketi: "Bugün", "Yarın" ya da "8 Ekim Perşembe". */
export function dayLabel(localIso: string, now: string = nowLocalIso()): string {
  const day = localIso.slice(0, 10)
  const today = now.slice(0, 10)
  const tomorrow = new Date(localStamp(`${today}T00:00:00`) + 86_400_000).toISOString().slice(0, 10)
  if (day === today) return 'Bugün'
  if (day === tomorrow) return 'Yarın'
  return formatLocal(localIso, 'long')
}
