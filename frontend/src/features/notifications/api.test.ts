import { describe, expect, it } from 'vitest'
import { isReminder, notificationShortcut } from './api'

const n = (type: string, link: string | null = '/x/1', title = 'Başlık') => ({ type, link, title })

describe('notificationShortcut', () => {
  it('işin yapıldığı yere götürür', () => {
    expect(notificationShortcut(n('COURSE_APPLICATION', '/courses/c1'))).toEqual({ label: 'Başvuruları gör', to: '/courses/c1?sekme=basvurular' })
    expect(notificationShortcut(n('COURSE_MATERIAL', '/courses/c1'))?.to).toBe('/courses/c1?sekme=materyaller')
    expect(notificationShortcut(n('EVENT_PARTICIPATION_REQUEST', '/events/e1'))?.to).toBe('/events/e1/yonetim?sekme=istekler')
    expect(notificationShortcut(n('EVENT_STAFF_ASSIGNED', '/events/e1'))?.to).toBe('/events/e1/kapi')
    expect(notificationShortcut(n('EVENT_APPROVAL_REQUEST'))?.to).toBe('/clubs/approvals')
  })

  it('moderasyon kararında itiraz eden kararlarda var, kesin sonuçlarda yok', () => {
    for (const type of ['MODERATION_REJECTED', 'MODERATION_HIDDEN', 'MODERATION_REMOVED']) {
      expect(notificationShortcut(n(type, '/posts/p1'))).toEqual({ label: 'İtiraz et', to: '/posts/p1' })
    }
    expect(notificationShortcut(n('MODERATION_PUBLISHED', '/posts/p1'))).toBeNull()
    expect(notificationShortcut(n('MODERATION_APPEAL_REJECTED', '/posts/p1'))).toBeNull()
  })

  it('üyelik bildiriminde yalnız yöneticiye giden yeni başvuruda kısayol olur', () => {
    expect(notificationShortcut(n('CLUB_MEMBERSHIP', '/clubs/k2', 'Satranç Kulübü: Yeni üyelik başvurusu'))?.to).toBe('/clubs/k2?sekme=yonetim')
    expect(notificationShortcut(n('CLUB_MEMBERSHIP', '/clubs/k2', 'Üyelik başvurunuz onaylandı'))).toBeNull()
  })

  it('bağlantı boşsa bağlantıya dayanan kısayol çıkmaz, sabit adresli olan kalır', () => {
    expect(notificationShortcut(n('COURSE_APPLICATION', null))).toBeNull()
    expect(notificationShortcut(n('EVENT_REMINDER', null))?.to).toBe('/me/tickets')
  })

  it('bilinmeyen türde kısayol yok', () => {
    expect(notificationShortcut(n('PASSWORD_CHANGED'))).toBeNull()
  })
})

describe('isReminder', () => {
  it('yalnız hatırlatma türleri', () => {
    expect(isReminder({ type: 'EVENT_REMINDER' })).toBe(true)
    expect(isReminder({ type: 'ASSIGNMENT_REMINDER' })).toBe(true)
    expect(isReminder({ type: 'EVENT_TICKET' })).toBe(false)
  })
})
