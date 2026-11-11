import '@testing-library/jest-dom/vitest'

// jsdom'da matchMedia yok; panel ve hareket tercihleri için dar ekran + hareket serbest varsayılır.
if (!window.matchMedia) {
  window.matchMedia = (query: string) =>
    ({
      matches: false,
      media: query,
      onchange: null,
      addEventListener: () => {},
      removeEventListener: () => {},
      addListener: () => {},
      removeListener: () => {},
      dispatchEvent: () => false,
    }) as MediaQueryList
}

// vitest global kipte değil: Testing Library'nin otomatik temizliğini elle bağla.
import { cleanup } from '@testing-library/react'
import { afterEach } from 'vitest'
afterEach(() => cleanup())

// jsdom'da ResizeObserver yok; Radix bileşenleri ölçüm için kullanır.
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
} as unknown as typeof ResizeObserver
