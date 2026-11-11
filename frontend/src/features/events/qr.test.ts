import { describe, expect, it } from 'vitest'
import QR from 'qrcode'
import jsQR from 'jsqr'
import { ApiError } from '@/lib/api/problem'
import { scanFailure, staffable } from './staff'

/** Biletlerdeki QR'ı (QrCode bileşeninin kullandığı matris, 2 modül sessiz alan) RGBA piksellere çizer. */
function render(value: string, scale = 6) {
  const { modules } = QR.create(value, { errorCorrectionLevel: 'M' })
  const side = (modules.size + 4) * scale
  const data = new Uint8ClampedArray(side * side * 4).fill(255)
  for (let y = 0; y < modules.size; y++)
    for (let x = 0; x < modules.size; x++) {
      if (!modules.get(x, y)) continue
      for (let dy = 0; dy < scale; dy++)
        for (let dx = 0; dx < scale; dx++) {
          const i = (((y + 2) * scale + dy) * side + (x + 2) * scale + dx) * 4
          data[i] = data[i + 1] = data[i + 2] = 0
        }
    }
  return { data, side }
}

describe('bilet QR okuma', () => {
  it('biletteki kod kapıda çözülür', () => {
    const code = 'b6f1c2d4-8e0a-4c3b-9f7e-1a2b3c4d5e6f'
    const { data, side } = render(code)
    expect(jsQR(data, side, side, { inversionAttempts: 'dontInvert' })?.data).toBe(code)
  })
})

describe('scanFailure', () => {
  it('sunucunun mesajını gösterir; bulunamayan bilet ve yetki için kapıda ne yapılacağını söyler', () => {
    const err = (code: string, status = 400, message = 'x') => new ApiError({ status, code, message })
    expect(scanFailure(err('INVALID_TICKET'))).toBe('Bilet bulunamadı. Kodu kontrol edin.')
    expect(scanFailure(err('TICKET_WRONG_EVENT', 400, 'Bu bilet başka bir etkinliğe ait.'))).toBe('Bu bilet başka bir etkinliğe ait.')
    expect(scanFailure(err('FORBIDDEN', 403))).toBe('Bu biletin etkinliğinde giriş yapma yetkiniz yok.')
    expect(scanFailure(err('CHECK_IN_CLOSED', 409, 'Giriş yalnız etkinlikten 60 dakika önce açılır'))).toMatch(/60 dakika/)
  })
})

describe('staffable', () => {
  it('yalnız bitmemiş kulüp etkinlikleri', () => {
    expect(staffable({ status: 'ACTIVE', clubId: 'k1' })).toBe(true)
    expect(staffable({ status: 'PENDING_PRESIDENT', clubId: 'k1' })).toBe(true)
    expect(staffable({ status: 'COMPLETED', clubId: 'k1' })).toBe(false)
    expect(staffable({ status: 'ACTIVE', clubId: null })).toBe(false)
  })
})
