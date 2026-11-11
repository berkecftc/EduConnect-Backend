import { describe, expect, it } from 'vitest'
import { assistantFailure, parseSse } from './api'

describe('parseSse', () => {
  it('tamamlanan olayları ayırır, yarım kalanı saklar', () => {
    expect(parseSse('data:Merhaba\n\ndata: dünya\n\ndata:yar')).toEqual({ chunks: ['Merhaba', ' dünya'], rest: 'data:yar' })
  })

  it('çok satırlı veriyi satır sonuyla birleştirir', () => {
    expect(parseSse('data:1. ödev\ndata:2. ödev\n\n').chunks).toEqual(['1. ödev\n2. ödev'])
  })

  it('CRLF ayırıcıyı ve veri dışı alanları tanır', () => {
    expect(parseSse('event:message\r\ndata:tamam\r\n\r\n').chunks).toEqual(['tamam'])
  })
})

describe('assistantFailure', () => {
  it('hız sınırını ayrı anlatır', () => {
    expect(assistantFailure(429, '')).toMatch(/çok soru/)
  })

  it('sunucunun cümlesini kullanır, yoksa genel cümleye düşer', () => {
    expect(assistantFailure(400, '{"message":"Mesaj boş olamaz"}')).toBe('Mesaj boş olamaz')
    expect(assistantFailure(503, '<html>')).toMatch(/yanıt veremiyor/)
  })
})
