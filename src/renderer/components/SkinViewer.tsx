import React, { useRef, useState, useEffect, useMemo } from "react";
import { Canvas, useFrame, useLoader } from "@react-three/fiber";
import * as THREE from "three";
import {
  BoxGeometry,
  NearestFilter,
  TextureLoader,
  Vector2,
  DoubleSide,
  FrontSide,
  Float32BufferAttribute,
} from "three";
import { User } from "lucide-react";

// ─── Minotar.net skin API ───────────────────────────────────────────────────

function skinTextureUrl(username: string): string {
  return `https://minotar.net/skin/${encodeURIComponent(username)}`;
}

function avatarUrl(username: string, size: number): string {
  return `https://minotar.net/helm/${encodeURIComponent(username)}/${size}`;
}

// ─── UV helpers (ported from skinview3d) ────────────────────────────────────

const TEX_W = 64;
const TEX_H = 64;

function toFaceVerts(x1: number, y1: number, x2: number, y2: number) {
  return [
    new Vector2(x1 / TEX_W, 1 - y2 / TEX_H),
    new Vector2(x2 / TEX_W, 1 - y2 / TEX_H),
    new Vector2(x2 / TEX_W, 1 - y1 / TEX_H),
    new Vector2(x1 / TEX_W, 1 - y1 / TEX_H),
  ];
}

function setSkinUVs(
  box: BoxGeometry,
  u: number,
  v: number,
  width: number,
  height: number,
  depth: number
) {
  const top = toFaceVerts(u + depth, v, u + width + depth, v + depth);
  const bottom = toFaceVerts(u + width + depth, v, u + width * 2 + depth, v + depth);
  const left = toFaceVerts(u, v + depth, u + depth, v + depth + height);
  const front = toFaceVerts(u + depth, v + depth, u + width + depth, v + depth + height);
  const right = toFaceVerts(u + width + depth, v + depth, u + width + depth * 2, v + height + depth);
  const back = toFaceVerts(u + width + depth * 2, v + depth, u + width * 2 + depth * 2, v + height + depth);

  const reorder = (f: Vector2[]) => [f[3], f[2], f[0], f[1]];
  const reorderBottom = (f: Vector2[]) => [f[0], f[1], f[3], f[2]];

  const faces = [
    reorder(right),
    reorder(left),
    reorder(top),
    reorderBottom(bottom),
    reorder(front),
    reorder(back),
  ];

  const uvData: number[] = [];
  for (const face of faces) {
    for (const uv of face) {
      uvData.push(uv.x, uv.y);
    }
  }

  box.setAttribute("uv", new Float32BufferAttribute(uvData, 2));
}

// ─── Body part component ───────────────────────────────────────────────────

interface SkinBoxProps {
  size: [number, number, number];
  uvOrigin: [number, number];
  uvSize: [number, number, number];
  position?: [number, number, number];
  isOverlay?: boolean;
}

function SkinBox({ size, uvOrigin, uvSize, position, isOverlay }: SkinBoxProps) {
  const geo = useMemo(() => {
    const g = new BoxGeometry(...size);
    setSkinUVs(g, uvOrigin[0], uvOrigin[1], uvSize[0], uvSize[1], uvSize[2]);
    return g;
  }, [size, uvOrigin, uvSize]);

  return (
    <mesh geometry={geo} position={position}>
      <meshStandardMaterial
        attach="material"
        side={isOverlay ? DoubleSide : FrontSide}
        transparent={isOverlay}
        alphaTest={isOverlay ? 1e-5 : undefined}
        polygonOffset={true}
        polygonOffsetFactor={isOverlay ? 1 : 0}
        polygonOffsetUnits={isOverlay ? 1 : 0}
      />
    </mesh>
  );
}

// ─── Mouse tracker (maps screen mouse to normalized coords) ────────────────

// Shared mouse state that components inside Canvas can read
const mouseState = {
  x: 0, y: 0,
  vx: 0, vy: 0,       // velocity
  clicked: false,      // single click this frame
  clickTime: 0,        // timestamp of last click
  clickCount: 0,       // rapid click counter
  dragging: false,     // is dragging
  dragX: 0,            // cumulative drag rotation
  isOver: false,       // mouse is over the container
};

function useMouseTracker(containerRef: React.RefObject<HTMLDivElement | null>) {
  useEffect(() => {
    let prevX = 0, prevY = 0;
    let lastMoveTime = performance.now();
    let isDragging = false;

    const onMove = (e: MouseEvent) => {
      const el = containerRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const rawX = ((e.clientX - rect.left) / rect.width) * 2 - 1;
      const rawY = -(((e.clientY - rect.top) / rect.height) * 2 - 1);
      // Clamp to ±1 so off-container mouse doesn't cause extreme rotations
      const nx = Math.max(-1, Math.min(1, rawX));
      const ny = Math.max(-1, Math.min(1, rawY));
      const now = performance.now();
      const dt = Math.max(1, now - lastMoveTime) / 16; // normalize to ~60fps
      mouseState.vx = (nx - prevX) / dt;
      mouseState.vy = (ny - prevY) / dt;
      mouseState.x = nx;
      mouseState.y = ny;
      prevX = nx;
      prevY = ny;
      lastMoveTime = now;

      if (isDragging) {
        mouseState.dragX += e.movementX * 0.01;
      }
    };

    const onDown = (e: MouseEvent) => {
      const el = containerRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      if (e.clientX >= rect.left && e.clientX <= rect.right &&
          e.clientY >= rect.top && e.clientY <= rect.bottom) {
        const now = performance.now();
        if (now - mouseState.clickTime < 400) {
          mouseState.clickCount++;
        } else {
          mouseState.clickCount = 1;
        }
        mouseState.clicked = true;
        mouseState.clickTime = now;
        isDragging = true;
        mouseState.dragging = true;
      }
    };

    const onUp = () => {
      isDragging = false;
      mouseState.dragging = false;
    };

    const onEnter = () => { mouseState.isOver = true; };
    const onLeave = () => { mouseState.isOver = false; };

    const el = containerRef.current;
    if (el) {
      el.addEventListener("mouseenter", onEnter);
      el.addEventListener("mouseleave", onLeave);
    }

    window.addEventListener("mousemove", onMove);
    window.addEventListener("mousedown", onDown);
    window.addEventListener("mouseup", onUp);
    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mousedown", onDown);
      window.removeEventListener("mouseup", onUp);
      if (el) {
        el.removeEventListener("mouseenter", onEnter);
        el.removeEventListener("mouseleave", onLeave);
      }
    };
  }, [containerRef]);
}

// Smooth lerp helper
function lerp(current: number, target: number, factor: number) {
  return current + (target - current) * factor;
}

// ─── Player model ──────────────────────────────────────────────────────────

function PlayerModel({ username, followMouse }: { username: string; followMouse: boolean }) {
  const texture = useLoader(TextureLoader, skinTextureUrl(username));
  const groupRef = useRef<THREE.Group>(null);

  // Smoothed values for fluid animation
  const smooth = useRef({
    headX: 0, headY: 0, headZ: 0,
    bodyY: 0, bodyX: 0, bodyZ: 0,
    rightArmX: 0, rightArmZ: 0,
    leftArmX: 0, leftArmZ: 0,
    rightLegX: 0, leftLegX: 0,
    posY: 0, posX: 0,
    proximity: 0,
    // Click reactions
    jumpVel: 0, jumpY: 0,
    spinAngle: 0, spinVel: 0,
    wavePhase: 0, isWaving: false,
    // Excitement from fast mouse movement
    excitement: 0,
    // Drag rotation
    dragRotation: 0,
    // Squash/stretch
    squash: 1,
    // Head bob from clicks
    headBob: 0,
  });

  // Track last click so we can consume it once
  const lastClickTime = useRef(0);

  useEffect(() => {
    if (texture) {
      texture.magFilter = NearestFilter;
      texture.minFilter = NearestFilter;
      texture.needsUpdate = true;
    }
  }, [texture]);

  useEffect(() => {
    if (!groupRef.current || !texture) return;
    groupRef.current.traverse((child: THREE.Object3D) => {
      const mesh = child as THREE.Mesh;
      if (mesh.isMesh) {
        const mat = mesh.material as THREE.MeshStandardMaterial;
        mat.map = texture;
        mat.needsUpdate = true;
      }
    });
  }, [texture]);

  useFrame(() => {
    if (!groupRef.current) return;
    const t = performance.now() / 1000;
    const s = smooth.current;
    const f = 0.08; // base lerp speed (slightly snappier)

    // ── Consume click events ──
    if (mouseState.clicked && mouseState.clickTime !== lastClickTime.current) {
      lastClickTime.current = mouseState.clickTime;
      mouseState.clicked = false;

      if (mouseState.clickCount >= 3) {
        // Triple+ click: big spin!
        s.spinVel = 12;
        s.jumpVel = 0.06;
      } else if (mouseState.clickCount === 2) {
        // Double click: wave animation
        s.isWaving = true;
        s.wavePhase = 0;
      } else {
        // Single click: jump + head nod
        s.jumpVel = 0.04;
        s.headBob = 0.5;
      }
    }

    // ── Jump physics ──
    s.jumpY += s.jumpVel;
    s.jumpVel -= 0.003; // gravity
    if (s.jumpY <= 0) {
      s.jumpY = 0;
      if (s.jumpVel < 0) {
        // Landing squash (subtle)
        s.squash = 0.94;
        s.jumpVel = 0;
      }
    }
    // Squash recovery
    s.squash = lerp(s.squash, 1, 0.1);

    // ── Spin physics ──
    s.spinAngle += s.spinVel * 0.016;
    s.spinVel = lerp(s.spinVel, 0, 0.04);
    if (Math.abs(s.spinVel) < 0.01) s.spinVel = 0;

    // ── Wave animation ──
    if (s.isWaving) {
      s.wavePhase += 0.05;
      if (s.wavePhase > Math.PI * 4) {
        s.isWaving = false;
        s.wavePhase = 0;
      }
    }

    // ── Head bob decay ──
    s.headBob = lerp(s.headBob, 0, 0.06);

    // ── Excitement from mouse velocity ──
    const speed = Math.sqrt(mouseState.vx ** 2 + mouseState.vy ** 2);
    const excitementTarget = Math.min(1, speed * 3);
    s.excitement = lerp(s.excitement, excitementTarget, 0.05);

    // ── Drag rotation ──
    if (mouseState.dragging) {
      s.dragRotation = lerp(s.dragRotation, mouseState.dragX, 0.15);
    }

    // How close is the mouse to center
    const dist = Math.sqrt(mouseState.x ** 2 + mouseState.y ** 2);
    const proximityTarget = Math.max(0, 1 - dist * 0.8);

    if (followMouse) {
      // ── Head: looks at cursor with excitement amplification ──
      const excitedMult = 1 + s.excitement * 0.5;
      const headTargetX = -mouseState.y * 0.5 * excitedMult + s.headBob * Math.sin(t * 15);
      const headTargetY = mouseState.x * 0.8 * excitedMult;
      const headTargetZ = -mouseState.x * 0.12 + s.excitement * Math.sin(t * 8) * 0.1;

      s.headX = lerp(s.headX, headTargetX, f * 1.6);
      s.headY = lerp(s.headY, headTargetY, f * 1.6);
      s.headZ = lerp(s.headZ, headTargetZ, f * 1.2);

      // ── Body: turns toward mouse, excitement makes it sway ──
      const bodyTargetY = mouseState.x * 0.4;
      const bodyTargetX = -mouseState.y * 0.06 + s.excitement * Math.sin(t * 5) * 0.05;
      const bodyTargetZ = s.excitement * Math.sin(t * 3.5) * 0.04;
      s.bodyY = lerp(s.bodyY, bodyTargetY, f * 0.8);
      s.bodyX = lerp(s.bodyX, bodyTargetX, f * 0.6);
      s.bodyZ = lerp(s.bodyZ, bodyTargetZ, f * 0.6);

      // ── Proximity ──
      s.proximity = lerp(s.proximity, proximityTarget, f * 1.5);

      // ── Arms ──
      const breathe = Math.sin(t * 2) * 0.04;
      const tension = s.proximity * 0.3;
      const rightArmLift = mouseState.x < 0 ? Math.abs(mouseState.x) * 0.2 : 0;
      const leftArmLift = mouseState.x > 0 ? Math.abs(mouseState.x) * 0.2 : 0;
      // Excitement makes arms swing
      const exciteSwing = s.excitement * Math.sin(t * 6) * 0.3;

      let rightArmTargetX = breathe - tension - rightArmLift + exciteSwing;
      let rightArmTargetZ = tension * 0.5 + s.excitement * 0.15;
      let leftArmTargetX = breathe - tension - leftArmLift - exciteSwing;
      let leftArmTargetZ = -tension * 0.5 - s.excitement * 0.15;

      // Wave override for right arm
      if (s.isWaving) {
        rightArmTargetX = -2.5 + Math.sin(s.wavePhase) * 0.4;
        rightArmTargetZ = 0.3;
      }

      s.rightArmX = lerp(s.rightArmX, rightArmTargetX, f * 1.2);
      s.rightArmZ = lerp(s.rightArmZ, rightArmTargetZ, f * 1.2);
      s.leftArmX = lerp(s.leftArmX, leftArmTargetX, f * 1.2);
      s.leftArmZ = lerp(s.leftArmZ, leftArmTargetZ, f * 1.2);

      // ── Legs: weight shift + excitement bounce ──
      const weightShift = s.bodyY * 0.12;
      const exciteBounce = s.excitement * Math.sin(t * 6 + Math.PI) * 0.15;
      s.rightLegX = lerp(s.rightLegX, -weightShift + exciteBounce + Math.sin(t * 1.5) * 0.02, f);
      s.leftLegX = lerp(s.leftLegX, weightShift - exciteBounce + Math.sin(t * 1.5 + Math.PI) * 0.02, f);

      // ── Position: breathing + jump + excitement bounce ──
      const breatheBob = Math.sin(t * 2) * 0.005;
      const tensionCrouch = -s.proximity * 0.02;
      const exciteBob = s.excitement * Math.abs(Math.sin(t * 6)) * 0.015;
      s.posY = lerp(s.posY, breatheBob + tensionCrouch + exciteBob, f);

      // Lean toward mouse
      s.posX = lerp(s.posX, mouseState.x * 0.02, f * 0.3);

    } else {
      // Fallback idle animation
      s.headX = lerp(s.headX, Math.sin(t * 1.5) * 0.08, f);
      s.headY = lerp(s.headY, Math.sin(t * 0.8) * 0.1, f);
      s.headZ = lerp(s.headZ, Math.sin(t * 1.2) * 0.04, f);
      s.bodyY = lerp(s.bodyY, Math.sin(t * 0.6) * 0.05, f);
      s.bodyX = lerp(s.bodyX, 0, f);
      s.bodyZ = lerp(s.bodyZ, 0, f);
      s.rightArmX = lerp(s.rightArmX, Math.sin(t * 1.8) * 0.06, f);
      s.rightArmZ = lerp(s.rightArmZ, 0.05, f);
      s.leftArmX = lerp(s.leftArmX, Math.sin(t * 1.8 + Math.PI) * 0.06, f);
      s.leftArmZ = lerp(s.leftArmZ, -0.05, f);
      s.rightLegX = lerp(s.rightLegX, Math.sin(t * 1.8 + Math.PI) * 0.03, f);
      s.leftLegX = lerp(s.leftLegX, Math.sin(t * 1.8) * 0.03, f);
      s.posY = lerp(s.posY, Math.sin(t * 2) * 0.005, f);
      s.posX = lerp(s.posX, 0, f);
      s.proximity = lerp(s.proximity, 0, f);
      s.excitement = lerp(s.excitement, 0, 0.02);
    }

    // ── Apply to bones ──
    const head = groupRef.current.getObjectByName("head");
    if (head) {
      head.rotation.x = s.headX;
      head.rotation.y = s.headY;
      head.rotation.z = s.headZ;
    }

    const body = groupRef.current.getObjectByName("body");
    if (body) {
      body.rotation.y = s.bodyY * 0.3;
      body.rotation.x = s.bodyX;
      body.rotation.z = s.bodyZ;
    }

    const rightArm = groupRef.current.getObjectByName("rightArm");
    if (rightArm) {
      rightArm.rotation.x = s.rightArmX;
      rightArm.rotation.z = s.rightArmZ;
    }

    const leftArm = groupRef.current.getObjectByName("leftArm");
    if (leftArm) {
      leftArm.rotation.x = s.leftArmX;
      leftArm.rotation.z = s.leftArmZ;
    }

    const rightLeg = groupRef.current.getObjectByName("rightLeg");
    if (rightLeg) rightLeg.rotation.x = s.rightLegX;

    const leftLeg = groupRef.current.getObjectByName("leftLeg");
    if (leftLeg) leftLeg.rotation.x = s.leftLegX;

    // Root: mouse follow + drag rotation + spin
    if (followMouse) {
      const mouseTarget = mouseState.x * 0.25;
      const baseRotation = mouseTarget + s.dragRotation + s.spinAngle;
      groupRef.current.rotation.y = lerp(groupRef.current.rotation.y, baseRotation, f * 0.8);
    } else {
      groupRef.current.rotation.y = lerp(
        groupRef.current.rotation.y,
        s.dragRotation + s.spinAngle + Math.sin(t * 0.4) * 0.1,
        f * 0.6
      );
    }

    // Vertical position: breathing + jump
    groupRef.current.position.y = 0.05 + s.posY + s.jumpY;
    groupRef.current.position.x = s.posX;

    // Subtle squash/stretch on Y only (XZ stay uniform to avoid distortion)
    const base = 1 / 16;
    groupRef.current.scale.set(base, s.squash * base, base);
  });

  const scale = 1 / 16;

  return (
    <group ref={groupRef} scale={[scale, scale, scale]} position={[0, 0.05, 0]}>
      <group position={[0, 8, 0]}>
        <group name="head">
          <SkinBox size={[8, 8, 8]} uvOrigin={[0, 0]} uvSize={[8, 8, 8]} position={[0, 4, 0]} />
          <SkinBox size={[9, 9, 9]} uvOrigin={[32, 0]} uvSize={[8, 8, 8]} position={[0, 4, 0]} isOverlay />
        </group>

        <group name="body" position={[0, -6, 0]}>
          <SkinBox size={[8, 12, 4]} uvOrigin={[16, 16]} uvSize={[8, 12, 4]} />
          <SkinBox size={[8.5, 12.5, 4.5]} uvOrigin={[16, 32]} uvSize={[8, 12, 4]} isOverlay />
        </group>

        <group name="rightArm" position={[-5, -2, 0]}>
          <group position={[-1, -4, 0]}>
            <SkinBox size={[4, 12, 4]} uvOrigin={[40, 16]} uvSize={[4, 12, 4]} />
            <SkinBox size={[4.5, 12.5, 4.5]} uvOrigin={[40, 32]} uvSize={[4, 12, 4]} isOverlay />
          </group>
        </group>

        <group name="leftArm" position={[5, -2, 0]}>
          <group position={[1, -4, 0]}>
            <SkinBox size={[4, 12, 4]} uvOrigin={[32, 48]} uvSize={[4, 12, 4]} />
            <SkinBox size={[4.5, 12.5, 4.5]} uvOrigin={[48, 48]} uvSize={[4, 12, 4]} isOverlay />
          </group>
        </group>

        <group name="rightLeg" position={[-1.9, -12, -0.1]}>
          <group position={[0, -6, 0]}>
            <SkinBox size={[4, 12, 4]} uvOrigin={[0, 16]} uvSize={[4, 12, 4]} />
            <SkinBox size={[4.5, 12.5, 4.5]} uvOrigin={[0, 32]} uvSize={[4, 12, 4]} isOverlay />
          </group>
        </group>

        <group name="leftLeg" position={[1.9, -12, -0.1]}>
          <group position={[0, -6, 0]}>
            <SkinBox size={[4, 12, 4]} uvOrigin={[16, 48]} uvSize={[4, 12, 4]} />
            <SkinBox size={[4.5, 12.5, 4.5]} uvOrigin={[0, 48]} uvSize={[4, 12, 4]} isOverlay />
          </group>
        </group>
      </group>
    </group>
  );
}

// ─── Exported 3D viewer ────────────────────────────────────────────────────

export function SkinViewer3D({
  username,
  className,
  followMouse = true,
}: {
  username: string;
  uuid?: string | null;
  className?: string;
  followMouse?: boolean;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  useMouseTracker(containerRef);

  return (
    <div ref={containerRef} className={className} style={{ cursor: "grab" }}>
      <Canvas
        camera={{ position: [0, 0, 3.2], fov: 50 }}
        gl={{ alpha: true, antialias: true }}
        style={{ background: "transparent" }}
      >
        <ambientLight intensity={0.9} />
        <pointLight position={[5, 5, 5]} intensity={1.3} />
        <pointLight position={[-5, 3, 5]} intensity={0.5} />
        <pointLight position={[0, -3, 4]} intensity={0.3} />

        <ErrorBoundary fallback={null}>
          <React.Suspense fallback={null}>
            <PlayerModel username={username} followMouse={followMouse} />
          </React.Suspense>
        </ErrorBoundary>
      </Canvas>
    </div>
  );
}

// Minimal error boundary for WebGL/texture load failures
class ErrorBoundary extends React.Component<
  { fallback: React.ReactNode; children: React.ReactNode },
  { hasError: boolean }
> {
  state = { hasError: false };
  static getDerivedStateFromError() {
    return { hasError: true };
  }
  render() {
    if (this.state.hasError) return this.props.fallback;
    return this.props.children;
  }
}

// ─── Player avatar head (2D) ──────────────────────────────────────────────

export function PlayerAvatar({
  username,
  size = 64,
  className,
}: {
  uuid?: string | null;
  username?: string;
  size?: number;
  className?: string;
}) {
  const [error, setError] = useState(false);
  const name = username || "MHF_Steve";
  const src = avatarUrl(name, size);

  if (error) {
    return (
      <div
        className={`rounded-2xl bg-(--color-surface-tertiary) flex items-center justify-center ${className ?? ""}`}
        style={{ width: size, height: size }}
      >
        {username ? (
          <span
            className="font-bold text-(--color-text-secondary)"
            style={{ fontSize: size * 0.4 }}
          >
            {username[0]?.toUpperCase()}
          </span>
        ) : (
          <User size={size * 0.4} className="text-(--color-text-secondary)" />
        )}
      </div>
    );
  }

  return (
    <img
      src={src}
      alt="Player avatar"
      className={`rounded-2xl shadow-md ${className ?? ""}`}
      style={{ width: size, height: size, imageRendering: "pixelated" }}
      draggable={false}
      onError={() => setError(true)}
    />
  );
}
