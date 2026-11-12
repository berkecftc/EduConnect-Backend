import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/lib/api/problem'
import { RegisterPage } from './RegisterPage'

const post = vi.fn()
vi.mock('@/lib/api/client', () => ({
  api: {
    // Katalog boş, unvanlar boş: form serbest bölüm alanına düşer.
    get: vi.fn().mockImplementation(async () => ({ data: [] })),
    post: (...args: unknown[]) => post(...args),
  },
}))

function renderPage() {
  sessionStorage.clear()
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillStudentApplication(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole('radio', { name: /Öğrenci/ }))
  await user.click(screen.getByRole('button', { name: 'Devam et' }))
  await user.type(await screen.findByLabelText(/^Ad/), 'Ayşe')
  await user.type(screen.getByLabelText(/^Soyad/), 'Yılmaz')
  await user.type(screen.getByLabelText(/^Kurum e-postası/), 'ayse.yilmaz@ogr.ornek.edu.tr')
  await user.type(screen.getByLabelText(/^Öğrenci numarası/), '20231045')
  await user.click(screen.getByRole('button', { name: 'Devam et' }))
  await user.type(await screen.findByLabelText(/^Bölüm/), 'Bilgisayar Mühendisliği')
  await user.selectOptions(screen.getByLabelText(/^Giriş yılı/), '2023')
  await user.click(screen.getByRole('button', { name: 'Devam et' }))
  await user.type(await screen.findByLabelText(/^Şifre \*$|^Şifre$/), 'Kampus-Deneme-7391')
  await user.type(screen.getByLabelText(/^Şifre \(tekrar\)/), 'Kampus-Deneme-7391')
  const file = new File(['%PDF-1.4'], 'belge.pdf', { type: 'application/pdf' })
  await user.upload(screen.getByLabelText(/^Öğrenci belgesi/), file)
}

describe('RegisterPage', () => {
  it('başarıda doğrulama ve onay adımlarını gösterir', async () => {
    post.mockImplementation(async () => ({ data: 'ok' }))
    const user = userEvent.setup()
    renderPage()
    await fillStudentApplication(user)
    await user.click(screen.getByRole('button', { name: 'Başvuruyu gönder' }))
    expect(await screen.findByRole('heading', { name: 'Başvurunuz alındı' })).toBeInTheDocument()
    const [url, body] = post.mock.calls[0] as [string, FormData]
    expect(url).toBe('/auth/request/student-account')
    expect(body.get('studentDocument')).toBeInstanceOf(File)
    const request = JSON.parse(await (body.get('request') as Blob).text())
    expect(request).toMatchObject({ studentId: '20231045', entryYear: 2023, department: 'Bilgisayar Mühendisliği' })
    expect(request).not.toHaveProperty('confirmPassword')
  })

  it('öğrenci numarası alınmışsa ilgili adıma döner ve alanın altına yazar', async () => {
    post.mockImplementation(async () => {
      throw new ApiError({ status: 409, code: 'STUDENT_NUMBER_TAKEN', message: 'Bu öğrenci numarasıyla kayıtlı bir hesap veya başvuru var.' })
    })
    const user = userEvent.setup()
    renderPage()
    await fillStudentApplication(user)
    await user.click(screen.getByRole('button', { name: 'Başvuruyu gönder' }))
    expect(await screen.findByRole('heading', { name: /Kişisel bilgiler/ })).toBeInTheDocument()
    expect(screen.getByText('Bu öğrenci numarasıyla kayıtlı bir hesap veya başvuru var.')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByLabelText(/^Öğrenci numarası/)).toHaveFocus())
  })
})
