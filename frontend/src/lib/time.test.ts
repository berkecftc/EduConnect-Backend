import { describe, expect, it } from 'vitest'
import { formatInstant, formatLocal, formatLocalRange, nowLocalIso, toLocalIso } from './time'

describe('zaman biçimleri', () => {
  it('UTC kayıt anını Türkiye saatine çevirir', () => {
    expect(formatInstant('2026-09-28T10:40:25.582Z', 'time')).toBe('13:40')
  })

  it('planlanan yerel saati dönüştürmeden gösterir', () => {
    expect(formatLocal('2026-10-05T18:30:00', 'time')).toBe('18:30')
    expect(formatLocal('2026-10-05T18:30:00', 'long')).toBe('5 Ekim Pazartesi')
  })

  it('aynı gün aralığını tek tarihle yazar', () => {
    expect(formatLocalRange('2026-10-12T18:30:00', '2026-10-12T20:30:00')).toBe('12 Eki 18:30–20:30')
  })

  it('çok günlü aralıkta iki tarihi yazar', () => {
    expect(formatLocalRange('2026-10-12T18:30:00', '2026-10-14T17:00:00')).toBe('12 Eki 18:30 – 14 Eki 17:00')
  })

  it('geçersiz değerde boş döner', () => {
    expect(formatInstant('bozuk')).toBe('')
    expect(formatLocal(undefined)).toBe('')
  })

  it('datetime-local değerine saniye ekler', () => {
    expect(toLocalIso('2026-12-31T23:59')).toBe('2026-12-31T23:59:00')
  })

  it('şu anı Türkiye saatiyle verir', () => {
    expect(nowLocalIso(new Date('2026-10-04T21:30:00Z'))).toBe('2026-10-05T00:30:00')
  })
})
