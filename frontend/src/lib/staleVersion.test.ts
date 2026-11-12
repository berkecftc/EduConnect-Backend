import { beforeEach, describe, expect, it, vi } from 'vitest'
import { canReloadForNewVersion, isStaleChunkError, reloadForNewVersion } from './staleVersion'

describe('isStaleChunkError', () => {
  it('tarayıcıların paket yükleme hatalarını tanır', () => {
    expect(isStaleChunkError(new TypeError('Failed to fetch dynamically imported module: https://x/a.js'))).toBe(true)
    expect(isStaleChunkError(new TypeError('error loading dynamically imported module: https://x/a.js'))).toBe(true)
    expect(isStaleChunkError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(isStaleChunkError(new Error('Unable to preload CSS for /assets/a.css'))).toBe(true)
  })

  it('başka hataları karıştırmaz', () => {
    expect(isStaleChunkError(new TypeError("Cannot read properties of undefined (reading 'x')"))).toBe(false)
    expect(isStaleChunkError('Failed to fetch dynamically imported module')).toBe(false)
    expect(isStaleChunkError(null)).toBe(false)
  })
})

describe('yeni sürüm için yenileme', () => {
  beforeEach(() => sessionStorage.clear())

  it('bir kez yeniler; kısa sürede ikinci kez denemez, süre geçince yeniden dener', () => {
    const reload = vi.fn()
    expect(canReloadForNewVersion(1_000_000)).toBe(true)
    reloadForNewVersion(reload, 1_000_000)
    expect(reload).toHaveBeenCalledOnce()
    expect(canReloadForNewVersion(1_010_000)).toBe(false)
    expect(canReloadForNewVersion(1_031_000)).toBe(true)
  })
})
