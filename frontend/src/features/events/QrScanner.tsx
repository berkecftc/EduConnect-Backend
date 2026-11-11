import { useEffect, useRef, useState } from 'react'
import { Camera, CameraOff } from 'lucide-react'
import { Button } from '@/components/ui/Button'

type State = { kind: 'idle' } | { kind: 'starting' } | { kind: 'running' } | { kind: 'error'; message: string }

/** Kare çözme aralığı: telefon işlemcisini yormadan okutmayı anında hissettirecek kadar sık. */
const INTERVAL_MS = 120
/** Çözümde kullanılan en geniş kenar: daha büyüğü yavaşlatır, okuma oranını artırmaz. */
const MAX_SIDE = 640

function cameraError(e: unknown): string {
  const name = e instanceof DOMException ? e.name : ''
  if (name === 'NotAllowedError' || name === 'SecurityError')
    return 'Kamera izni verilmedi. Tarayıcının site ayarlarından kameraya izin verin ya da bilet kodunu aşağıya yazın.'
  if (name === 'NotFoundError' || name === 'OverconstrainedError')
    return 'Bu cihazda kullanılabilir kamera bulunamadı. Bilet kodunu aşağıya yazın.'
  if (name === 'NotReadableError') return 'Kamera başka bir uygulamada açık. Onu kapatıp yeniden deneyin.'
  return 'Kamera açılamadı. Bilet kodunu aşağıya yazabilirsiniz.'
}

/**
 * Kamerayla QR okuyucu: arka kamerayı açar, görüntüyü küçültüp her ~120 ms'de bir `jsqr` ile çözer.
 * `paused` iken çözmez (sonuç gösterilirken aynı bilet tekrar okunmasın). Kamera yalnız düğmeyle açılır
 * (izin istemi kullanıcı eylemine bağlı) ve bileşen kapanınca durdurulur. Kamera yoksa ya da izin yoksa
 * kod elle yazılır; okuyucu bunu söyler.
 */
export function QrScanner({ paused, onCode }: { paused: boolean; onCode: (code: string) => void }) {
  const video = useRef<HTMLVideoElement>(null)
  const canvas = useRef<HTMLCanvasElement>(null)
  const stream = useRef<MediaStream | null>(null)
  const frame = useRef<number | null>(null)
  const pausedRef = useRef(paused)
  const onCodeRef = useRef(onCode)
  const [state, setState] = useState<State>({ kind: 'idle' })

  useEffect(() => {
    pausedRef.current = paused
    onCodeRef.current = onCode
  }, [paused, onCode])

  const stop = () => {
    if (frame.current != null) cancelAnimationFrame(frame.current)
    frame.current = null
    stream.current?.getTracks().forEach((t) => t.stop())
    stream.current = null
    if (video.current) video.current.srcObject = null
  }

  useEffect(() => stop, [])

  const start = async () => {
    if (!navigator.mediaDevices?.getUserMedia) {
      setState({ kind: 'error', message: 'Kamera yalnız güvenli (https) bağlantıda açılır. Bilet kodunu aşağıya yazabilirsiniz.' })
      return
    }
    setState({ kind: 'starting' })
    try {
      const [{ default: jsQR }, media] = await Promise.all([
        import('jsqr'),
        navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false }),
      ])
      stream.current = media
      const v = video.current!
      v.srcObject = media
      await v.play()
      setState({ kind: 'running' })

      let last = 0
      const tick = (t: number) => {
        frame.current = requestAnimationFrame(tick)
        if (t - last < INTERVAL_MS || pausedRef.current || v.readyState < v.HAVE_ENOUGH_DATA) return
        last = t
        const scale = Math.min(1, MAX_SIDE / Math.max(v.videoWidth, v.videoHeight))
        const w = Math.round(v.videoWidth * scale)
        const h = Math.round(v.videoHeight * scale)
        const c = canvas.current!
        if (c.width !== w) c.width = w
        if (c.height !== h) c.height = h
        const ctx = c.getContext('2d', { willReadFrequently: true })
        if (!ctx) return
        ctx.drawImage(v, 0, 0, w, h)
        const found = jsQR(ctx.getImageData(0, 0, w, h).data, w, h, { inversionAttempts: 'dontInvert' })
        if (found?.data) onCodeRef.current(found.data)
      }
      frame.current = requestAnimationFrame(tick)
    } catch (e) {
      stop()
      setState({ kind: 'error', message: cameraError(e) })
    }
  }

  const running = state.kind === 'running'
  return (
    <div>
      <div className="relative aspect-[4/3] w-full overflow-hidden border border-rule bg-sunken">
        <video ref={video} muted playsInline aria-hidden className={running ? 'size-full object-cover' : 'hidden'} />
        <canvas ref={canvas} className="hidden" />
        {running ? (
          // Nişan: ortada kare çerçeve; bilet bu kareye tutulur.
          <span
            aria-hidden
            className="pointer-events-none absolute top-1/2 left-1/2 aspect-square h-[68%] -translate-x-1/2 -translate-y-1/2 border-2 border-white/85"
          />
        ) : (
          <div className="absolute inset-0 grid place-items-center p-6 text-center">
            <div>
              <Camera aria-hidden className="mx-auto size-10 text-ink-3" />
              <p className="mt-3 max-w-[32ch] text-md text-ink-2">
                {state.kind === 'error'
                  ? state.message
                  : 'Kamerayı açın ve bileti çerçeveye tutun. Kod okununca giriş kendiliğinden yapılır.'}
              </p>
              {state.kind !== 'error' || !state.message.includes('https') ? (
                <Button variant="primary" className="mt-5" loading={state.kind === 'starting'} onClick={() => void start()}>
                  {state.kind === 'error' ? 'Yeniden dene' : 'Kamerayı aç'}
                </Button>
              ) : null}
            </div>
          </div>
        )}
        {running && paused && <span aria-hidden className="absolute inset-0 bg-black/30" />}
      </div>
      {running && (
        <button
          type="button"
          onClick={() => {
            stop()
            setState({ kind: 'idle' })
          }}
          className="mt-3 inline-flex items-center gap-2 text-sm font-semibold text-ink-2 underline-offset-4 hover:text-ink hover:underline"
        >
          <CameraOff aria-hidden className="size-4" />
          Kamerayı kapat
        </button>
      )}
    </div>
  )
}
