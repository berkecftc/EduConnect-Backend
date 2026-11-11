/** Sayfalı yanıtın ortak biçimi; arayüz yalnız bunu görür. */
export type Paged<T> = { content: T[]; totalElements: number; totalPages: number; number: number; last: boolean }

type ViaDto<T> = { content: T[]; page: { size: number; number: number; totalElements: number; totalPages: number } }
type Legacy<T> = { content: T[]; totalElements: number; totalPages: number; number: number; last?: boolean }

/**
 * Spring sayfasını tek biçime getirir. post-service `VIA_DTO` kullanır (`{content, page:{…}}`); diğer servisler
 * eski `PageImpl` biçimini (`last`, `number` üstte) döndürür. İkisi de kabul edilir.
 */
export function toPaged<T>(raw: ViaDto<T> | Legacy<T>): Paged<T> {
  if ('page' in raw && raw.page) {
    const p = raw.page
    return {
      content: raw.content,
      totalElements: p.totalElements,
      totalPages: p.totalPages,
      number: p.number,
      last: p.number + 1 >= p.totalPages,
    }
  }
  const r = raw as Legacy<T>
  return {
    content: r.content,
    totalElements: r.totalElements,
    totalPages: r.totalPages,
    number: r.number,
    last: r.last ?? r.number + 1 >= r.totalPages,
  }
}
