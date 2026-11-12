import { Component, Suspense, lazy, useEffect, useRef, useState, useSyncExternalStore, type ReactNode } from 'react'
import { useReducedMotion } from 'motion/react'
import { LINES } from '@/design/lines'
import { cn } from '@/lib/cn'
import { BLOCKS, labelId, type BlockKey } from './blocks'
import type { ModulesPalette } from './ModulesScene'

const ModulesScene = lazy(() => import('./ModulesScene'))

const WIDE = '(min-width: 64rem)'

function useMediaQuery(query: string) {
  return useSyncExternalStore(
    (cb) => {
      const mql = window.matchMedia(query)
      mql.addEventListener('change', cb)
      return () => mql.removeEventListener('change', cb)
    },
    () => window.matchMedia(query).matches,
    () => false,
  )
}

/** Sahne renkleri token'lardan okunur; panel her temada koyu. */
function readPalette(el: HTMLElement): ModulesPalette {
  const css = getComputedStyle(el)
  const v = (name: string) => css.getPropertyValue(name).trim()
  return {
    stage: v('--ec-canvas'),
    ceramic: v('--ec-ink'),
    colors: { ders: v('--ec-ders'), kulup: v('--ec-kulup'), etkinlik: v('--ec-etkinlik'), topluluk: v('--ec-topluluk') },
  }
}

/** WebGL açılamazsa sahne sessizce düşer; metin ve düğmeler kalır. */
class SceneBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  render() {
    return this.state.failed ? null : this.props.children
  }
}

/**
 * Giriş ekranının sağ paneli: dört alanın simgesi çapraz köşelerden sarmalla birleşip EduConnect işaretini oluşturur.
 * Halkanın ortasına gelince (dokununca ya da alttaki düğmelere odaklanınca) parçalar yeniden ayrılır ve adları görünür.
 */
export function ModulesPanel() {
  const ref = useRef<HTMLElement>(null)
  const labels = useRef(new Map<string, HTMLElement>())
  const wide = useMediaQuery(WIDE)
  const reducedMotion = useReducedMotion() ?? false
  const [palette, setPalette] = useState<ModulesPalette | null>(null)
  const [split, setSplit] = useState(false)
  const [focus, setFocus] = useState<BlockKey | null>(null)

  useEffect(() => {
    if (ref.current) setPalette(readPalette(ref.current))
  }, [])

  const show = (key: BlockKey | null) => {
    setSplit(true)
    setFocus(key)
  }
  const reset = () => {
    setSplit(false)
    setFocus(null)
  }

  return (
    <aside
      ref={ref}
      data-theme="dark"
      aria-labelledby="moduller-baslik"
      onPointerLeave={reset}
      className="relative hidden overflow-hidden bg-canvas text-ink lg:block"
    >
      {wide && palette && (
        <SceneBoundary>
          <Suspense fallback={null}>
            <div className="absolute inset-0 animate-fade-in">
              <ModulesScene
                palette={palette}
                split={split}
                onRingHover={() => show(null)}
                onRingPress={() => (split ? reset() : show(null))}
                reducedMotion={reducedMotion}
                labels={labels}
              />
              {/* Simge etiketleri: konumlarını sahne her karede yazar. Aynı bilgi alttaki düğmelerde de var. */}
              <div aria-hidden className="pointer-events-none absolute inset-0 overflow-hidden">
                {BLOCKS.map(({ key, summary }, i) => (
                  <div
                    key={key}
                    ref={(el) => {
                      if (el) labels.current.set(labelId(key), el)
                      else labels.current.delete(labelId(key))
                    }}
                    className="absolute top-0 left-0 will-change-transform"
                    style={{ transform: 'translate3d(-9999px, 0, 0)' }}
                  >
                    <div
                      className={cn(
                        'w-max max-w-[15rem] text-center transition-[opacity,translate] duration-300 ease-rail',
                        split ? 'translate-y-0' : '-translate-y-2 opacity-0',
                        split && (focus === null || focus === key ? 'opacity-100' : 'opacity-40'),
                      )}
                      style={{ transitionDelay: split ? `${220 + i * 60}ms` : '0ms' }}
                    >
                      <p className="flex items-center justify-center gap-2 text-lg font-heavy">
                        <span aria-hidden className="size-2.5 rounded-[2px]" style={{ background: LINES[key].stroke }} />
                        {LINES[key].label}
                      </p>
                      <p className="mt-0.5 text-sm text-ink-2">{summary}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </Suspense>
        </SceneBoundary>
      )}

      <div className="pointer-events-none absolute inset-x-0 bottom-0 p-10 xl:p-14">
        <h2 id="moduller-baslik" className="max-w-[16ch] text-4xl">
          Dört alan, tek platform
        </h2>
        <p className="mt-4 max-w-[46ch] text-base text-ink-2">
          Dersler, kulüpler, etkinlikler ve topluluk aynı hesapta, aynı yerde birleşir. Ayrıntılar için halkanın üzerine
          gelin.
        </p>
        <ul className="pointer-events-auto mt-7 flex flex-wrap gap-2" aria-label="Alanlar">
          {BLOCKS.map(({ key, summary }) => (
            <li key={key}>
              <button
                type="button"
                aria-describedby={`ozet-${key}`}
                onMouseEnter={() => show(key)}
                onFocus={() => show(key)}
                onBlur={reset}
                className={cn(
                  'flex h-9 items-center gap-2 rounded-md px-3 text-sm transition-colors duration-200',
                  focus === key ? 'bg-surface text-ink' : focus === null ? 'text-ink' : 'text-ink-3',
                )}
              >
                <span aria-hidden className="size-2.5 rounded-[2px]" style={{ background: LINES[key].stroke }} />
                {LINES[key].label}
                <span id={`ozet-${key}`} className="sr-only">
                  {summary}
                </span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  )
}
