import { describe, expect, it } from 'vitest'
import { createPostBody, postKindLabel, publisherPath } from './api'

describe('createPostBody', () => {
  const base = { title: ' Başlık ', content: ' İçerik ' }

  it('kulüp duyurusu kulübe bağlanır; ders ve birim adı gönderilmez', () => {
    expect(
      createPostBody({
        ...base,
        category: 'DUYURU',
        commentsDisabled: true,
        publisherName: 'yok sayılır',
        publisher: { key: 'club:k1', type: 'CLUB', clubId: 'k1', label: 'Robotik', needsApproval: true },
      }),
    ).toEqual({
      title: 'Başlık',
      content: 'İçerik',
      category: 'DUYURU',
      publisherType: 'CLUB',
      clubId: 'k1',
      courseId: null,
      publisherName: null,
      commentsDisabled: true,
    })
  })

  it('kampüs duyurusu birim adını taşır, kulübe ya da derse bağlanmaz', () => {
    const body = createPostBody({
      ...base,
      category: 'DUYURU',
      publisherName: ' Öğrenci İşleri ',
      publisher: { key: 'campus', type: 'CAMPUS', label: 'Kampüs birimi' },
    })
    expect(body).toMatchObject({ publisherType: 'CAMPUS', clubId: null, courseId: null, publisherName: 'Öğrenci İşleri', commentsDisabled: false })
  })

  it('ders duyurusu derse bağlanır', () => {
    const body = createPostBody({ ...base, category: 'DUYURU', publisher: { key: 'course:c1', type: 'COURSE', courseId: 'c1', label: 'BİL 301' } })
    expect(body).toMatchObject({ publisherType: 'COURSE', courseId: 'c1', clubId: null })
  })

  it('forum gönderisi öğrenci adına; seçilmiş yayıncı yok sayılır', () => {
    const body = createPostBody({
      ...base,
      category: 'DERS_NOTU',
      courseId: 'c1',
      sharingDeclaration: true,
      publisher: { key: 'club:k1', type: 'CLUB', clubId: 'k1', label: 'Robotik', needsApproval: false },
    })
    expect(body).toEqual({ title: 'Başlık', content: 'İçerik', category: 'DERS_NOTU', publisherType: 'STUDENT', courseId: 'c1', sharingDeclaration: true })
  })
})

describe('postKindLabel ve publisherPath', () => {
  const post = (publisherType: 'STUDENT' | 'CLUB' | 'COURSE' | 'CAMPUS', official = true) => ({
    official,
    publisherType,
    category: official ? ('DUYURU' as const) : ('SORU' as const),
    clubId: publisherType === 'CLUB' ? 'k1' : null,
    courseId: publisherType === 'COURSE' ? 'c1' : null,
  })

  it('resmî duyuruda kimin adına olduğunu söyler', () => {
    expect(postKindLabel(post('CLUB'))).toBe('Kulüp duyurusu')
    expect(postKindLabel(post('COURSE'))).toBe('Ders duyurusu')
    expect(postKindLabel(post('CAMPUS'))).toBe('Kampüs duyurusu')
    expect(postKindLabel(post('STUDENT', false))).toBe('Soru')
  })

  it('kulüp ve derse bağlanır; kampüs biriminin sayfası yok', () => {
    expect(publisherPath(post('CLUB'))).toBe('/clubs/k1')
    expect(publisherPath(post('COURSE'))).toBe('/courses/c1')
    expect(publisherPath(post('CAMPUS'))).toBeNull()
    expect(publisherPath(post('STUDENT', false))).toBeNull()
  })
})
