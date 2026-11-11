import { useEffect, useMemo, useRef, type ReactElement } from 'react'
import { useFrame } from '@react-three/fiber'
import * as THREE from 'three'
import type { BlockKey } from './blocks'

/**
 * Alan simgeleri: Dersler → kep, Kulüpler → bayrak, Etkinlikler → bilet, Topluluk → konuşma balonu.
 * Hepsi kameraya bakacak biçimde, yaklaşık 1 birim boyunda modellenir.
 */

type IconProps = { color: string; detail: string; reducedMotion: boolean }

function useGloss(color: string) {
  return useMemo(
    () => new THREE.MeshPhysicalMaterial({ color, roughness: 0.24, clearcoat: 1, clearcoatRoughness: 0.08 }),
    [color],
  )
}

function useMatte(color: string) {
  return useMemo(() => new THREE.MeshStandardMaterial({ color, roughness: 0.45 }), [color])
}

function roundedRect<T extends THREE.Shape | THREE.Path>(shape: T, w: number, h: number, r: number): T {
  const x = -w / 2
  const y = -h / 2
  shape.moveTo(x + r, y)
  shape.lineTo(x + w - r, y)
  shape.quadraticCurveTo(x + w, y, x + w, y + r)
  shape.lineTo(x + w, y + h - r)
  shape.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
  shape.lineTo(x + r, y + h)
  shape.quadraticCurveTo(x, y + h, x, y + h - r)
  shape.lineTo(x, y + r)
  shape.quadraticCurveTo(x, y, x + r, y)
  return shape
}

const EXTRUDE = { depth: 0.12, bevelEnabled: true, bevelThickness: 0.04, bevelSize: 0.04, bevelSegments: 5, curveSegments: 28 }

function Mortarboard({ color, detail }: IconProps) {
  const gloss = useGloss(color)
  const matte = useMatte(detail)
  const board = useMemo(() => {
    const g = new THREE.ExtrudeGeometry(roundedRect(new THREE.Shape(), 1.05, 1.05, 0.06), { ...EXTRUDE, depth: 0.05 })
    g.center()
    return g
  }, [])
  return (
    <group rotation={[0.55, 0.6, 0]}>
      <mesh geometry={board} material={gloss} rotation={[-Math.PI / 2, 0, Math.PI / 4]} position={[0, 0.14, 0]} />
      <mesh material={gloss} position={[0, -0.06, 0]}>
        <cylinderGeometry args={[0.34, 0.38, 0.32, 48]} />
      </mesh>
      <mesh material={matte} position={[0, 0.2, 0]}>
        <sphereGeometry args={[0.065, 24, 16]} />
      </mesh>
      <mesh material={matte} position={[0.36, 0.0, 0.36]}>
        <cylinderGeometry args={[0.014, 0.014, 0.34, 8]} />
      </mesh>
      <mesh material={matte} position={[0.36, -0.2, 0.36]}>
        <coneGeometry args={[0.055, 0.15, 20]} />
      </mesh>
    </group>
  )
}

function Flag({ color, detail, reducedMotion }: IconProps) {
  const matte = useMatte(detail)
  const cloth = useRef<THREE.Mesh>(null)
  const geometry = useMemo(() => {
    const g = new THREE.PlaneGeometry(0.85, 0.55, 28, 10)
    g.translate(0.425, 0, 0)
    return g
  }, [])
  const rest = useMemo(() => Float32Array.from(geometry.attributes.position!.array), [geometry])
  useEffect(() => () => geometry.dispose(), [geometry])

  // Kumaş dalgası: direkten uzaklaştıkça artar.
  useFrame(({ clock }) => {
    if (!cloth.current || reducedMotion) return
    const geo = cloth.current.geometry
    const pos = geo.attributes.position as THREE.BufferAttribute
    const t = clock.elapsedTime
    for (let i = 0; i < pos.count; i++) {
      const x = rest[i * 3]!
      const y = rest[i * 3 + 1]!
      pos.setZ(i, Math.sin(x * 7 - t * 3 + y * 1.5) * 0.07 * (x / 0.85))
    }
    pos.needsUpdate = true
    geo.computeVertexNormals()
  })

  return (
    <group position={[-0.3, 0, 0]}>
      <mesh material={matte}>
        <cylinderGeometry args={[0.03, 0.035, 1.3, 16]} />
      </mesh>
      <mesh material={matte} position={[0, 0.68, 0]}>
        <sphereGeometry args={[0.06, 24, 16]} />
      </mesh>
      <mesh ref={cloth} geometry={geometry} position={[0.03, 0.34, 0]}>
        <meshPhysicalMaterial color={color} roughness={0.4} clearcoat={0.7} side={THREE.DoubleSide} />
      </mesh>
    </group>
  )
}

function Ticket({ color, detail }: IconProps) {
  const gloss = useGloss(color)
  const matte = useMatte(detail)
  const geometry = useMemo(() => {
    const w = 1.2
    const h = 0.66
    const s = roundedRect(new THREE.Shape(), w, h, 0.08)
    const left = new THREE.Path()
    left.absarc(-w / 2, 0, 0.11, 0, Math.PI * 2, false)
    const right = new THREE.Path()
    right.absarc(w / 2, 0, 0.11, 0, Math.PI * 2, false)
    s.holes.push(left, right)
    const g = new THREE.ExtrudeGeometry(s, EXTRUDE)
    g.center()
    return g
  }, [])
  return (
    <group rotation={[0, 0, 0.14]}>
      <mesh geometry={geometry} material={gloss} />
      {[-0.22, -0.11, 0, 0.11, 0.22].map((y) => (
        <mesh key={y} material={matte} position={[0.24, y, 0.105]}>
          <circleGeometry args={[0.024, 16]} />
        </mesh>
      ))}
      <mesh material={matte} position={[-0.13, 0.09, 0.105]}>
        <planeGeometry args={[0.46, 0.065]} />
      </mesh>
      <mesh material={matte} position={[-0.18, -0.07, 0.105]}>
        <planeGeometry args={[0.36, 0.055]} />
      </mesh>
    </group>
  )
}

function Bubble({ color, detail }: IconProps) {
  const gloss = useGloss(color)
  const matte = useMatte(detail)
  const geometry = useMemo(() => {
    const w = 1.1
    const h = 0.74
    const r = 0.22
    const x = -w / 2
    const y = -h / 2
    const s = new THREE.Shape()
    s.moveTo(x + r, y)
    s.lineTo(x + 0.32, y)
    s.lineTo(x + 0.12, y - 0.24)
    s.lineTo(x + 0.5, y)
    s.lineTo(x + w - r, y)
    s.quadraticCurveTo(x + w, y, x + w, y + r)
    s.lineTo(x + w, y + h - r)
    s.quadraticCurveTo(x + w, y + h, x + w - r, y + h)
    s.lineTo(x + r, y + h)
    s.quadraticCurveTo(x, y + h, x, y + h - r)
    s.lineTo(x, y + r)
    s.quadraticCurveTo(x, y, x + r, y)
    const g = new THREE.ExtrudeGeometry(s, { ...EXTRUDE, depth: 0.16 })
    g.center()
    return g
  }, [])
  return (
    <group>
      <mesh geometry={geometry} material={gloss} />
      {[-0.24, 0, 0.24].map((x) => (
        <mesh key={x} material={matte} position={[x, 0.06, 0.13]}>
          <sphereGeometry args={[0.07, 24, 16]} />
        </mesh>
      ))}
    </group>
  )
}

const ICONS: Record<BlockKey, (p: IconProps) => ReactElement> = {
  ders: Mortarboard,
  kulup: Flag,
  etkinlik: Ticket,
  topluluk: Bubble,
}

/** Alanın simgesi. */
export function DomainIcon({ kind, ...props }: IconProps & { kind: BlockKey }) {
  const Icon = ICONS[kind]
  return <Icon {...props} />
}
