import { useSearchParams } from 'react-router-dom'

/** Hata ekranını denemek için bilerek hata atar (yalnız geliştirme). `?tur=surum`: eski sürüm paketi hatası. */
export function ThrowPage(): never {
  const [params] = useSearchParams()
  throw new TypeError(
    params.get('tur') === 'surum' ? 'Failed to fetch dynamically imported module: /assets/eski.js' : 'Önizleme için bilerek atılan hata',
  )
}
