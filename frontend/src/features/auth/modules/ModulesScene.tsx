import { useEffect, useMemo, useRef, type RefObject } from 'react'
import { Canvas, useFrame, type ThreeEvent } from '@react-three/fiber'
import { ContactShadows, Environment, Lightformer, RoundedBox } from '@react-three/drei'
import * as THREE from 'three'
import { BLOCKS, labelId, type BlockKey } from './blocks'
import { DomainIcon } from './Icons'

export type ModulesPalette = {
  stage: string
  ceramic: string
  colors: Record<BlockKey, string>
}

type Props = {
  palette: ModulesPalette
  /** Dört parça ayrık mı (simgeler ve etiketler görünür). */
  split: boolean
  /** Halkaya gelinince/dokununca çağrılır. */
  onRingHover: () => void
  onRingPress: () => void
  reducedMotion: boolean
  /** Canvas üstündeki DOM etiketleri; sahne her karede onları simgelerin altına taşır. */
  labels: RefObject<Map<string, HTMLElement>>
}

/** Logo ölçüleri. */
const RING_R = 0.62
const RING_TUBE = 0.2
const ARM_LEN = 1.15
const ARM_THICK = 0.46
/** Ayrıkken simgelerin durduğu elips: yatayda geniş, dikeyde basık (alttaki başlığa yer kalsın). */
const SPLIT_RX = 2.75
const SPLIT_RY = 1.75
/** Ayrık hâlin kola göre açısal kayması: simgeler çapraz köşelerde durur. */
const SWIRL = Math.PI / 4

/** Açılış koreografisi (saniye). */
const APPEAR_AT = 0.3
const APPEAR_STAGGER = 0.12
const APPEAR_DUR = 0.6
const MERGE_AT = 1.7
const MERGE_STAGGER = 0.07

const clamp01 = (x: number) => THREE.MathUtils.clamp(x, 0, 1)
const smooth = (a: number, b: number, x: number) => {
  const t = clamp01((x - a) / (b - a))
  return t * t * (3 - 2 * t)
}
const easeInOut = (t: number) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2)
const easeOutBack = (t: number) => {
  const c = 1.7
  return 1 + (c + 1) * Math.pow(t - 1, 3) + c * Math.pow(t - 1, 2)
}

/**
 * Bir alan: t = 0 iken çapraz köşede simge, t = 1 iken logo kolu.
 * Aradaki yol 45°'lik bir sarmal: simge dönerek merkeze yaklaşır, küçülüp kolun içinde kaybolur, kol uzar.
 */
function Piece({
  blockKey,
  index,
  angleDeg,
  palette,
  split,
  reducedMotion,
}: {
  blockKey: BlockKey
  index: number
  angleDeg: number
  palette: ModulesPalette
  split: boolean
  reducedMotion: boolean
}) {
  const icon = useRef<THREE.Group>(null)
  const arm = useRef<THREE.Group>(null)
  const t = useRef(reducedMotion ? 1 : 0)
  const color = palette.colors[blockKey]
  const armAngle = THREE.MathUtils.degToRad(angleDeg)
  const dir = useMemo(() => new THREE.Vector2(Math.cos(armAngle), Math.sin(armAngle)), [armAngle])
  const vertical = Math.abs(dir.x) < 0.5
  const armCenter = RING_R - 0.08 + ARM_LEN / 2

  useFrame(({ clock }, dt) => {
    if (!icon.current || !arm.current) return
    const time = clock.elapsedTime

    // Açılış: simge köşesinde belirir, sonra sarmalla merkeze akar.
    const appear = reducedMotion ? 1 : easeOutBack(clamp01((time - APPEAR_AT - index * APPEAR_STAGGER) / APPEAR_DUR))
    const merging = reducedMotion || time > MERGE_AT + index * MERGE_STAGGER
    const target = split || !merging ? 0 : 1
    t.current = reducedMotion ? target : THREE.MathUtils.damp(t.current, target, 2.6, dt)
    const p = easeInOut(t.current)

    // Simge: kutupsal yol (açı ve yarıçap birlikte değişir).
    const a = armAngle - SWIRL * (1 - p)
    const rEnd = RING_R + ARM_LEN * 0.75
    const rx = THREE.MathUtils.lerp(SPLIT_RX, rEnd, p)
    const ry = THREE.MathUtils.lerp(SPLIT_RY, rEnd, p)
    const bob = reducedMotion ? 0 : Math.sin(time * 1.3 + index * 1.7) * 0.07 * (1 - p)
    icon.current.position.set(Math.cos(a) * rx, Math.sin(a) * ry + bob, THREE.MathUtils.lerp(0.5, 0, p))
    const iconScale = Math.max(appear * (1 - smooth(0.55, 0.95, t.current)), 0.0001)
    icon.current.scale.setScalar(iconScale)
    icon.current.visible = iconScale > 0.002
    icon.current.rotation.y = (1 - p) * Math.sin(time * 0.6 + index) * 0.3 + p * 1.2

    // Kol: halkadan dışarı doğru uzar.
    const grow = Math.max(smooth(0.45, 1, t.current), 0.0001)
    arm.current.visible = grow > 0.02
    arm.current.scale.set(vertical ? 1 : grow, vertical ? grow : 1, 1)
    const c = armCenter - (ARM_LEN / 2) * (1 - grow)
    arm.current.position.set(dir.x * c, dir.y * c, 0)
  })

  return (
    <>
      <group ref={arm}>
        <RoundedBox
          args={vertical ? [ARM_THICK, ARM_LEN, ARM_THICK] : [ARM_LEN, ARM_THICK, ARM_THICK]}
          radius={ARM_THICK / 2 - 0.01}
          smoothness={8}
        >
          <meshPhysicalMaterial color={color} roughness={0.22} clearcoat={1} clearcoatRoughness={0.06} />
        </RoundedBox>
      </group>
      <group ref={icon} scale={0.0001}>
        <DomainIcon kind={blockKey} color={color} detail={palette.ceramic} reducedMotion={reducedMotion} />
        {/* Etiket çapası: simgenin hemen altı */}
        <Anchor id={labelId(blockKey)} position={[0, -0.78, 0]} />
      </group>
    </>
  )
}

/** Etiket çapaları: görünmez noktalar; LabelProjector bunları ekrana izdüşürür. */
const anchors = new Map<string, THREE.Object3D>()

function Anchor({ id, position }: { id: string; position: [number, number, number] }) {
  return (
    <object3D
      position={position}
      ref={(o) => {
        if (o) anchors.set(id, o)
        else anchors.delete(id)
      }}
    />
  )
}

/** DOM etiketlerini çapalarının ekran konumuna taşır (drei Html yerine; React 19 StrictMode ile uyumlu). */
function LabelProjector({ labels }: { labels: Props['labels'] }) {
  const v = useMemo(() => new THREE.Vector3(), [])
  useFrame(({ camera, size }) => {
    const els = labels.current
    if (!els) return
    anchors.forEach((obj, id) => {
      const el = els.get(id)
      if (!el) return
      obj.getWorldPosition(v).project(camera)
      const x = ((v.x + 1) / 2) * size.width
      const y = ((1 - v.y) / 2) * size.height
      el.style.transform = `translate3d(${x.toFixed(1)}px, ${y.toFixed(1)}px, 0) translate(-50%, 0)`
    })
  })
  return null
}

/** Aktarma halkası: kollar birleşince yaylanarak oturur; ayrıkken hafifçe küçülür ve döner. */
function Ring({
  palette,
  split,
  reducedMotion,
  onRingHover,
  onRingPress,
}: {
  palette: ModulesPalette
  split: boolean
  reducedMotion: boolean
  onRingHover: Props['onRingHover']
  onRingPress: Props['onRingPress']
}) {
  const ring = useRef<THREE.Group>(null)
  const scale = useRef(reducedMotion ? 1 : 0)
  const settledAt = useRef<number | null>(reducedMotion ? 0 : null)

  useFrame(({ clock }, dt) => {
    const g = ring.current
    if (!g) return
    const time = clock.elapsedTime
    // İlk birleşme: son kolun başlamasından kısa süre sonra halka belirir.
    const ringAt = MERGE_AT + MERGE_STAGGER * 3 + 0.75
    if (settledAt.current === null && time > ringAt) settledAt.current = time
    if (settledAt.current === null) {
      g.scale.setScalar(0.0001)
      return
    }
    const intro = reducedMotion ? 1 : easeOutBack(clamp01((time - settledAt.current) / 0.55))
    scale.current = THREE.MathUtils.damp(scale.current, split ? 0.78 : 1, 5, dt)
    g.scale.setScalar(Math.max(intro * scale.current, 0.0001))
    if (!reducedMotion) g.rotation.z += dt * (split ? 0.6 : 0)
  })

  return (
    <group
      ref={ring}
      position={[0, 0, 0.08]}
      onPointerOver={(e: ThreeEvent<PointerEvent>) => {
        e.stopPropagation()
        document.body.style.cursor = 'pointer'
        onRingHover()
      }}
      onPointerOut={() => {
        document.body.style.cursor = ''
      }}
      onClick={(e: ThreeEvent<MouseEvent>) => {
        e.stopPropagation()
        onRingPress()
      }}
    >
      <mesh>
        <torusGeometry args={[RING_R, RING_TUBE, 48, 128]} />
        <meshPhysicalMaterial color={palette.ceramic} roughness={0.3} clearcoat={1} clearcoatRoughness={0.1} />
      </mesh>
      {/* Halkanın içi: hem görsel boşluk hem dokunma/üzerine gelme alanı */}
      <mesh position={[0, 0, -0.04]}>
        <circleGeometry args={[RING_R, 64]} />
        <meshStandardMaterial color={palette.stage} roughness={0.6} />
      </mesh>
    </group>
  )
}

/** Logonun tamamı: imleci hafifçe izler, kendi kendine hafifçe salınır. */
function Assembly({ children, reducedMotion }: { children: React.ReactNode; reducedMotion: boolean }) {
  const group = useRef<THREE.Group>(null)
  useFrame(({ pointer, clock }, dt) => {
    const g = group.current
    if (!g || reducedMotion) return
    const k = 1 - Math.exp(-2.5 * dt)
    const sway = Math.sin(clock.elapsedTime * 0.35) * 0.16
    g.rotation.y = THREE.MathUtils.lerp(g.rotation.y, -0.28 + sway + pointer.x * 0.22, k)
    g.rotation.x = THREE.MathUtils.lerp(g.rotation.x, -0.16 - pointer.y * 0.12, k)
    g.position.y = Math.sin(clock.elapsedTime * 0.8) * 0.05
  })
  return (
    <group ref={group} rotation={[-0.16, -0.28, 0]}>
      {children}
    </group>
  )
}

export default function ModulesScene({ palette, split, onRingHover, onRingPress, reducedMotion, labels }: Props) {
  useEffect(() => () => void (document.body.style.cursor = ''), [])

  return (
    <Canvas
      dpr={[1, 2]}
      camera={{ position: [0, 0, 15.5], fov: 30 }}
      gl={{ antialias: true, toneMapping: THREE.ACESFilmicToneMapping }}
      aria-hidden
    >
      <color attach="background" args={[palette.stage]} />
      <LabelProjector labels={labels} />

      {/* Stüdyo ışığı: yerel ışık panelleri; vernikli yüzeylerde uzun, yumuşak yansımalar. */}
      <Environment resolution={256} frames={1}>
        <Lightformer form="rect" intensity={2.6} position={[0, 6, 3]} rotation-x={Math.PI / 2} scale={[12, 4, 1]} />
        <Lightformer form="rect" intensity={1.5} position={[-6, 0, 4]} rotation-y={Math.PI / 3} scale={[3, 9, 1]} />
        <Lightformer form="rect" intensity={1.1} position={[6, 1, 3]} rotation-y={-Math.PI / 3} scale={[3, 9, 1]} />
      </Environment>
      <directionalLight position={[3, 6, 8]} intensity={1.1} />

      <group position={[0.1, 1.1, 0]}>
        <Assembly reducedMotion={reducedMotion}>
          <Ring
            palette={palette}
            split={split}
            reducedMotion={reducedMotion}
            onRingHover={onRingHover}
            onRingPress={onRingPress}
          />
          {BLOCKS.map((b, i) => (
            <Piece
              key={b.key}
              blockKey={b.key}
              index={i}
              angleDeg={b.angle}
              palette={palette}
              split={split}
              reducedMotion={reducedMotion}
            />
          ))}
        </Assembly>
        <ContactShadows position={[0, -3.1, 0]} scale={9} blur={3} far={4} opacity={0.4} resolution={512} color="#000000" />
      </group>
    </Canvas>
  )
}
