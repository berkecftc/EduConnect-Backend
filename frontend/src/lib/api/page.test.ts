import { describe, expect, it } from 'vitest'
import { toPaged } from './page'

describe('sayfalı yanıt', () => {
  it('post-service VIA_DTO biçimini okur', () => {
    const p = toPaged({ content: [1, 2], page: { size: 2, number: 0, totalElements: 5, totalPages: 3 } })
    expect(p).toEqual({ content: [1, 2], totalElements: 5, totalPages: 3, number: 0, last: false })
    expect(toPaged({ content: [5], page: { size: 2, number: 2, totalElements: 5, totalPages: 3 } }).last).toBe(true)
  })

  it('eski PageImpl biçimini olduğu gibi kabul eder', () => {
    expect(toPaged({ content: [], totalElements: 0, totalPages: 0, number: 0, last: true }).last).toBe(true)
    expect(toPaged({ content: [1], totalElements: 3, totalPages: 3, number: 1 }).last).toBe(false)
  })
})
