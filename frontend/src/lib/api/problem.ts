import { isAxiosError } from 'axios'

/** Backend alan doğrulama hatası (RFC 9457 `errors[]`). */
export type FieldError = { field: string; message: string }

/** Backend'in tüm hata gövdeleri bu biçimde gelir (API-KONVANSIYONLARI §5). */
export type ProblemDetail = {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  errorCode?: string
  message?: string
  timestamp?: string
  errors?: FieldError[]
}

/**
 * Arayüzün tek hata türü. Mantık `code` ile kurulur, kullanıcıya `message` gösterilir.
 * Ağ hatasında `status` 0'dır.
 */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  readonly fieldErrors: FieldError[]
  readonly traceId?: string
  /** 429'da yeniden denemeye kalan saniye. */
  readonly retryAfter?: number

  constructor(init: {
    status: number
    code: string
    message: string
    fieldErrors?: FieldError[]
    traceId?: string
    retryAfter?: number
  }) {
    super(init.message)
    this.name = 'ApiError'
    this.status = init.status
    this.code = init.code
    this.fieldErrors = init.fieldErrors ?? []
    this.traceId = init.traceId
    this.retryAfter = init.retryAfter
  }

  /** Belirli bir alanın hata mesajı; form alanlarının altına yazılır (F-11). */
  fieldMessage(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message
  }
}

const FALLBACK: Record<number, { code: string; message: string }> = {
  0: { code: 'NETWORK_ERROR', message: 'Sunucuya ulaşılamadı. Bağlantınızı kontrol edip tekrar deneyin.' },
  401: { code: 'UNAUTHENTICATED', message: 'Oturumunuzun süresi doldu. Yeniden giriş yapın.' },
  403: { code: 'ACCESS_DENIED', message: 'Bu işlem için yetkiniz yok.' },
  404: { code: 'NOT_FOUND', message: 'Aradığınız kayıt bulunamadı.' },
  413: { code: 'PAYLOAD_TOO_LARGE', message: 'Dosya çok büyük. Daha küçük bir dosya seçin.' },
  429: { code: 'RATE_LIMITED', message: 'Çok fazla istek gönderildi. Biraz bekleyip tekrar deneyin.' },
  503: { code: 'SERVICE_UNAVAILABLE', message: 'Bu hizmet şu an yanıt vermiyor. Birkaç dakika sonra tekrar deneyin.' },
}

const GENERIC = { code: 'INTERNAL_ERROR', message: 'Beklenmeyen bir hata oluştu. Sorun sürerse destek ekibine bildirin.' }

function isProblem(data: unknown): data is ProblemDetail {
  return typeof data === 'object' && data !== null && 'status' in data
}

function parseRetryAfter(value: unknown): number | undefined {
  if (typeof value !== 'string' && typeof value !== 'number') return undefined
  const seconds = Number(value)
  return Number.isFinite(seconds) && seconds >= 0 ? seconds : undefined
}

/** Her türlü hatayı `ApiError`'a çevirir. */
export function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err

  if (isAxiosError(err)) {
    const res = err.response
    if (!res) return new ApiError({ status: 0, ...FALLBACK[0]! })

    const status = res.status
    const fallback = FALLBACK[status] ?? (status >= 500 ? GENERIC : { code: 'BAD_REQUEST', message: GENERIC.message })
    const traceId = (res.headers?.['x-trace-id'] as string | undefined) ?? undefined
    const retryAfter = parseRetryAfter(res.headers?.['retry-after'])
    const body = res.data

    if (isProblem(body)) {
      return new ApiError({
        status,
        code: body.errorCode ?? fallback.code,
        message: body.message ?? body.detail ?? fallback.message,
        fieldErrors: Array.isArray(body.errors) ? body.errors : [],
        traceId,
        retryAfter,
      })
    }
    return new ApiError({ status, ...fallback, traceId, retryAfter })
  }

  return new ApiError({ status: -1, ...GENERIC })
}
