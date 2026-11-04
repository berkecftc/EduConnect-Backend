import { render, screen } from '@testing-library/react'
import { RouterProvider, createMemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reloadForNewVersion } from '@/lib/staleVersion'
import { RouteErrorPage } from './StatusPages'

function renderCrash(error: Error) {
  const Crash = (): never => {
    throw error
  }
  const router = createMemoryRouter([{ path: '/', element: <Crash />, errorElement: <RouteErrorPage /> }])
  render(<RouterProvider router={router} />)
}

describe('RouteErrorPage', () => {
  beforeEach(() => {
    sessionStorage.clear()
    vi.spyOn(console, 'error').mockImplementation(() => {})
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })
  afterEach(() => vi.restoreAllMocks())

  it('beklenmeyen hatada Türkçe açıklama ve çıkış yolları gösterir', () => {
    renderCrash(new TypeError("Cannot read properties of undefined (reading 'x')"))
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Bu sayfa açılamadı' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sayfayı yenile' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Bugün ekranına dön' })).toHaveAttribute('href', '/')
  })

  it('eski sürümde yenileme yakın zamanda denendiyse döngüye girmez, kullanıcıya bırakır', () => {
    reloadForNewVersion(() => {})
    renderCrash(new TypeError('Failed to fetch dynamically imported module: /assets/a.js'))
    expect(screen.getByRole('heading', { name: 'Uygulama güncellendi' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Sayfayı yenile' })).toBeInTheDocument()
  })
})
