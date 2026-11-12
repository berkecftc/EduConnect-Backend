import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api/problem'
import { LoginPage } from './LoginPage'

const login = vi.fn()
vi.mock('@/lib/api/client', () => ({
  login: (...args: unknown[]) => login(...args),
  api: { post: vi.fn().mockResolvedValue({ data: {} }) },
}))

function renderPage() {
  render(
    <MemoryRouter>
      <LoginPage />
    </MemoryRouter>,
  )
}

async function submit() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('E-posta'), 'ayse@ogr.universite.edu.tr')
  await user.type(screen.getByLabelText('Şifre'), 'gizli-sifre-123')
  await user.click(screen.getByRole('button', { name: 'Giriş yap' }))
}

describe('LoginPage hata yönlendirmesi (F-83)', () => {

  it('onay bekleyen hesapta inceleme ekranını gösterir', async () => {
    login.mockImplementation(async () => {
      throw new ApiError({ status: 403, code: 'ACCOUNT_PENDING_APPROVAL', message: 'Hesabınız henüz onaylanmadı.' })
    })
    renderPage()
    await submit()
    expect(await screen.findByRole('heading', { name: 'Başvurunuz inceleniyor' })).toBeInTheDocument()
    expect(screen.getByRole('list', { name: 'Hesap başvurusu durumu' })).toBeInTheDocument()
  })

  it('doğrulanmamış e-postada yeniden gönderme düğmesi sunar', async () => {
    login.mockImplementation(async () => {
      throw new ApiError({ status: 403, code: 'EMAIL_NOT_VERIFIED', message: 'E-posta adresinizi doğrulamanız gerekiyor.' })
    })
    renderPage()
    await submit()
    expect(await screen.findByRole('button', { name: 'Doğrulama bağlantısını yeniden gönder' })).toBeInTheDocument()
  })

  it('kilitte Retry-After ile geri sayar ve gönderimi durdurur', async () => {
    login.mockImplementation(async () => {
      throw new ApiError({ status: 429, code: 'LOGIN_LOCKED', message: 'Çok fazla deneme.', retryAfter: 125 })
    })
    renderPage()
    await submit()
    expect(await screen.findByText('02:05')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Giriş yap' })).toBeDisabled()
  })
})
