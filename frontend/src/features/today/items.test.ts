import { describe, expect, it } from 'vitest'
import type { MyAssignment } from '@/features/assignments/api'
import type { MyRegistration } from '@/features/events/api'
import { summarize, type TodayItem } from './items'

const NOW = '2026-10-05T09:00:00'

const due = (at: string): TodayItem => ({ kind: 'assignment', at, assignment: { id: at, title: 'Ödev' } as MyAssignment })
const event = (at: string, title: string): TodayItem => ({ kind: 'event', at, event: { eventId: at, eventTitle: title } as MyRegistration })

describe('summarize', () => {
  it('bugünkü teslim sayısını söyler', () => {
    expect(summarize([due('2026-10-05T23:59:00')], NOW)).toBe('Bugün 1 teslim var.')
  })

  it('bugün teslim yoksa en yakın teslime kalan süreyi söyler', () => {
    expect(summarize([due('2026-10-08T09:00:00')], NOW)).toBe('Bugün teslim yok. En yakın teslim 3 gün sonra.')
  })

  it('yarınki etkinliği adına ek eklemeden yazar', () => {
    expect(summarize([event('2026-10-06T18:30:00', 'Arduino atölyesi'), due('2026-10-08T09:00:00')], NOW)).toBe(
      'Bugün teslim yok. En yakın teslim 3 gün sonra. Yarın kayıtlı olduğunuz bir etkinlik var: Arduino atölyesi.',
    )
  })

  it('boşken sakin bir cümle kurar', () => {
    expect(summarize([], NOW)).toBe('Önümüzdeki iki hafta için teslim ya da kayıtlı etkinlik yok.')
  })
})
