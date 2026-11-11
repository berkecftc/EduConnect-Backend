/**
 * Yeni sürüm yayımlanınca eski sekme, artık sunucuda olmayan sayfa paketini ister ve yüklenemez. Bu hata tarayıcıya göre
 * farklı yazılır. Sayfa bir kez kendiliğinden yenilenir; yine olmazsa döngüye girmeden kullanıcıya bırakılır.
 */

const STALE_CHUNK = [
  /Failed to fetch dynamically imported module/i,
  /error loading dynamically imported module/i,
  /Importing a module script failed/i,
  /Unable to preload CSS/i,
]

export const isStaleChunkError = (e: unknown) => e instanceof Error && STALE_CHUNK.some((p) => p.test(e.message))

const KEY = 'educonnect.stale-reload'
const RETRY_AFTER_MS = 30_000

/** Son kısa sürede yenileme denenmediyse doğru. Depolama kapalıysa denenmez; yenileme döngüsü kontrolsüz kalmasın. */
export function canReloadForNewVersion(now = Date.now()): boolean {
  try {
    const last = Number(sessionStorage.getItem(KEY))
    return !last || now - last >= RETRY_AFTER_MS
  } catch {
    return false
  }
}

export function reloadForNewVersion(reload: () => void = () => window.location.reload(), now = Date.now()) {
  try {
    sessionStorage.setItem(KEY, String(now))
  } catch {
    return
  }
  reload()
}
