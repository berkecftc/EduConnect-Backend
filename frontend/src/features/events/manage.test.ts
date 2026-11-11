import { describe, expect, it } from 'vitest'
import { EMPTY_DRAFT, draftToPayload, eventToDraft, validateDraft, type EventDraft } from './manage'

const NOW = '2026-10-07T12:00:00'
const draft = (d: Partial<EventDraft>): EventDraft => ({ ...EMPTY_DRAFT, title: 'Atölye', startsAt: '2026-10-12T18:30', ...d })

describe('etkinlik formu', () => {
  it('geçerli taslakta hata yok', () => {
    expect(validateDraft(draft({ endsAt: '2026-10-12T20:30', capacity: '40' }), NOW)).toEqual({})
  })

  it('başlık, geçmiş başlangıç ve ters saatleri yakalar', () => {
    const e = validateDraft(draft({ title: ' ', startsAt: '2026-10-07T11:00', endsAt: '2026-10-07T10:00' }), NOW)
    expect(e.title).toBeDefined()
    expect(e.startsAt).toBeDefined()
    expect(e.endsAt).toBeDefined()
  })

  it('kontenjan pozitif tam sayı olmalı; boşsa sınırsız', () => {
    expect(validateDraft(draft({ capacity: '0' }), NOW).capacity).toBeDefined()
    expect(validateDraft(draft({ capacity: '12.5' }), NOW).capacity).toBeDefined()
    expect(validateDraft(draft({ capacity: '' }), NOW).capacity).toBeUndefined()
  })

  it('kayıt penceresi ve iptal son tarihi etkinlikten önce olmalı', () => {
    const e = validateDraft(draft({ registrationOpensAt: '2026-10-10T10:00', registrationClosesAt: '2026-10-13T10:00', cancelUntil: '2026-10-13T00:00' }), NOW)
    expect(e.registrationClosesAt).toBeDefined()
    expect(e.cancelUntil).toBeDefined()
  })

  it('gönderirken saatleri bölgesiz ISOya, boşları null a, kontenjanı sayıya çevirir', () => {
    const p = draftToPayload(draft({ endsAt: '', capacity: '40', location: '  B Blok 204 ', cancelUntil: '2026-10-12T12:00' }))
    expect(p.startsAt).toBe('2026-10-12T18:30:00')
    expect(p.endsAt).toBeNull()
    expect(p.capacity).toBe(40)
    expect(p.location).toBe('B Blok 204')
    expect(p.cancelUntil).toBe('2026-10-12T12:00:00')
    expect(p.description).toBeNull()
  })

  it('var olan etkinliği forma geri çevirir', () => {
    const d = eventToDraft({
      id: 'e1',
      title: 'Atölye',
      description: null,
      startsAt: '2026-10-12T18:30:00',
      endsAt: null,
      location: null,
      imageUrl: null,
      clubId: 'k1',
      clubName: 'Robotik Kulübü',
      organizerName: null,
      capacity: 40,
      status: 'REJECTED',
    })
    expect(d.startsAt).toBe('2026-10-12T18:30')
    expect(d.capacity).toBe('40')
    expect(d.audience).toBe('ALL_STUDENTS')
  })
})
