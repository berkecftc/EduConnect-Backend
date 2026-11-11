import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios'
import { describe, expect, it } from 'vitest'
import { ApiError, toApiError } from './problem'

function axiosErr(status: number, data: unknown, headers: Record<string, string> = {}) {
  const response = { status, data, headers: new AxiosHeaders(headers), config: {}, statusText: '' } as AxiosResponse
  return new AxiosError('x', 'ERR', undefined, undefined, response)
}

describe('toApiError', () => {
  it('ProblemDetail gövdesinden mesaj, kod ve alan hatalarını alır', () => {
    const e = toApiError(
      axiosErr(400, {
        status: 400,
        errorCode: 'VALIDATION_FAILED',
        message: 'Girilen bilgiler geçersiz. title: Başlık boş olamaz',
        errors: [{ field: 'title', message: 'Başlık boş olamaz' }],
      }, { 'x-trace-id': 'abc123' }),
    )
    expect(e).toBeInstanceOf(ApiError)
    expect(e.code).toBe('VALIDATION_FAILED')
    expect(e.message).toContain('Girilen bilgiler geçersiz')
    expect(e.fieldMessage('title')).toBe('Başlık boş olamaz')
    expect(e.traceId).toBe('abc123')
  })

  it('429 Retry-After başlığını okur', () => {
    const e = toApiError(axiosErr(429, { status: 429, errorCode: 'RATE_LIMITED', message: '12 saniye sonra' }, { 'retry-after': '12' }))
    expect(e.retryAfter).toBe(12)
    expect(e.code).toBe('RATE_LIMITED')
  })

  it('gövdesiz 503 için anlamlı Türkçe mesaj verir', () => {
    const e = toApiError(axiosErr(503, ''))
    expect(e.code).toBe('SERVICE_UNAVAILABLE')
    expect(e.message).toMatch(/hizmet şu an yanıt vermiyor/)
  })

  it('yanıtsız hatayı ağ hatası sayar', () => {
    const e = toApiError(new AxiosError('Network Error', 'ERR_NETWORK'))
    expect(e.status).toBe(0)
    expect(e.code).toBe('NETWORK_ERROR')
  })
})
