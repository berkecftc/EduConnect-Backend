import { describe, expect, it } from 'vitest'
import { myEventState, type MyRegistration, type ParticipationRequest } from './api'

const registration = (r: Partial<MyRegistration>): MyRegistration => ({
  eventId: 'e1',
  eventTitle: 'Atölye',
  eventDescription: null,
  eventDate: '2026-10-06T18:30:00',
  eventLocation: null,
  qrCode: 'kod',
  registrationTime: '2026-10-01T10:00:00Z',
  attended: false,
  registrationStatus: 'REGISTERED',
  eventStatus: 'ACTIVE',
  ...r,
})

const request = (r: Partial<ParticipationRequest>): ParticipationRequest => ({
  id: 'p1',
  eventId: 'e1',
  eventTitle: 'Atölye',
  status: 'PENDING',
  requestDate: '2026-10-01T10:00:00Z',
  processedDate: null,
  message: null,
  rejectionReason: null,
  ...r,
})

describe('etkinlikteki durumum', () => {
  it('etkin kayıt varsa kayıtlıdır; iptal edilmiş kayıt sayılmaz', () => {
    expect(myEventState('e1', [registration({})]).kind).toBe('registered')
    expect(myEventState('e1', [registration({ registrationStatus: 'CANCELLED' })]).kind).toBe('none')
  })

  it('en son isteğe bakar: bekleme listesi, onay bekleyen ya da ret', () => {
    expect(myEventState('e1', [], [request({ status: 'WAITLISTED' })]).kind).toBe('waitlisted')
    expect(
      myEventState('e1', [], [request({ status: 'REJECTED', requestDate: '2026-09-01T00:00:00Z' }), request({ id: 'p2', status: 'PENDING' })]).kind,
    ).toBe('pending')
    expect(myEventState('e1', [], [request({ status: 'REJECTED' })]).kind).toBe('rejected')
  })

  it('geri çekilmiş ya da başka etkinliğe ait istek durumu etkilemez', () => {
    expect(myEventState('e1', [], [request({ status: 'WITHDRAWN' })]).kind).toBe('none')
    expect(myEventState('e1', [], [request({ eventId: 'e2', status: 'PENDING' })]).kind).toBe('none')
  })
})
