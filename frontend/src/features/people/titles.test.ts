import { describe, expect, it } from 'vitest'
import { titleLabel, withTitle } from './titles'

describe('unvan', () => {
  it('etiketi koda tercih eder, etiket yoksa koddan türetir', () => {
    expect(titleLabel('Doç. Dr.', 'PROFESSOR')).toBe('Doç. Dr.')
    expect(titleLabel(null, 'RESEARCH_ASSISTANT')).toBe('Arş. Gör.')
    expect(titleLabel('  ', null)).toBe('')
    expect(titleLabel(null, 'BILINMEYEN')).toBe('')
  })

  it('unvanı adın önüne ekler, yoksa yalnız adı döner', () => {
    expect(withTitle('Ayşe Yılmaz', 'Doç. Dr.')).toBe('Doç. Dr. Ayşe Yılmaz')
    expect(withTitle('Ayşe Yılmaz', null, 'ASSISTANT_PROFESSOR')).toBe('Dr. Öğr. Üyesi Ayşe Yılmaz')
    expect(withTitle('Ayşe Yılmaz', null, null)).toBe('Ayşe Yılmaz')
    expect(withTitle(null, 'Doç. Dr.')).toBe('')
  })

  it('ad zaten unvanla başlıyorsa tekrar etmez', () => {
    expect(withTitle('Doç. Dr. Ayşe Yılmaz', 'Doç. Dr.')).toBe('Doç. Dr. Ayşe Yılmaz')
    expect(withTitle('DR. ÖĞR. ÜYESİ Ali Can', 'Dr. Öğr. Üyesi')).toBe('DR. ÖĞR. ÜYESİ Ali Can')
  })
})
