import { useState, useEffect, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { Loader2 } from "lucide-react";

// Generate stable particle configs once
function createParticles(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    x: Math.random() * 100,
    y: Math.random() * 100,
    size: Math.random() * 2.5 + 1,
    duration: Math.random() * 20 + 15,
    delay: Math.random() * -20,
    drift: (Math.random() - 0.5) * 30,
    opacity: Math.random() * 0.4 + 0.1,
  }));
}

function createOrbs(count: number) {
  return Array.from({ length: count }, (_, i) => ({
    id: i,
    x: 20 + Math.random() * 60,
    y: 20 + Math.random() * 60,
    size: Math.random() * 200 + 100,
    duration: Math.random() * 15 + 20,
    delay: Math.random() * -10,
    moveX: (Math.random() - 0.5) * 40,
    moveY: (Math.random() - 0.5) * 30,
  }));
}

export const LoginScreen = observer(() => {
  const { auth } = useStore();
  const [mounted, setMounted] = useState(false);
  const [ready, setReady] = useState(false);

  const particles = useMemo(() => createParticles(40), []);
  const orbs = useMemo(() => createOrbs(5), []);

  useEffect(() => {
    requestAnimationFrame(() => setMounted(true));
    const timer = setTimeout(() => setReady(true), 300);
    return () => clearTimeout(timer);
  }, []);

  return (
    <div className="h-screen flex flex-col items-center justify-center bg-(--color-surface) relative overflow-hidden">
      <div className="drag-region absolute top-0 left-0 right-0 h-8 z-50" />

      {/* ── Layer 1: Animated gradient orbs ── */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {orbs.map((orb) => (
          <div
            key={orb.id}
            className="absolute rounded-full login-orb"
            style={{
              left: `${orb.x}%`,
              top: `${orb.y}%`,
              width: orb.size,
              height: orb.size,
              background: `radial-gradient(circle, var(--color-accent) 0%, transparent 70%)`,
              opacity: 0.06,
              filter: "blur(40px)",
              animationDuration: `${orb.duration}s`,
              animationDelay: `${orb.delay}s`,
              "--orb-x": `${orb.moveX}px`,
              "--orb-y": `${orb.moveY}px`,
            } as React.CSSProperties}
          />
        ))}
      </div>

      {/* ── Layer 2: Grid lines ── */}
      <div
        className="absolute inset-0 pointer-events-none login-grid"
        style={{
          opacity: mounted ? 0.03 : 0,
          transition: "opacity 2s ease",
          backgroundImage:
            "linear-gradient(var(--color-accent) 1px, transparent 1px), linear-gradient(90deg, var(--color-accent) 1px, transparent 1px)",
          backgroundSize: "60px 60px",
          maskImage: "radial-gradient(ellipse 50% 50% at 50% 50%, black 20%, transparent 70%)",
          WebkitMaskImage: "radial-gradient(ellipse 50% 50% at 50% 50%, black 20%, transparent 70%)",
        }}
      />

      {/* ── Layer 3: Floating particles ── */}
      <div className="absolute inset-0 pointer-events-none overflow-hidden">
        {particles.map((p) => (
          <div
            key={p.id}
            className="absolute rounded-full login-particle"
            style={{
              left: `${p.x}%`,
              bottom: "-5%",
              width: p.size,
              height: p.size,
              background: "var(--color-accent)",
              opacity: 0,
              animationDuration: `${p.duration}s`,
              animationDelay: `${p.delay}s`,
              "--drift": `${p.drift}px`,
              "--particle-opacity": p.opacity,
            } as React.CSSProperties}
          />
        ))}
      </div>

      {/* ── Layer 4: Center vignette glow ── */}
      <div
        className="absolute pointer-events-none"
        style={{
          width: 500,
          height: 500,
          left: "50%",
          top: "50%",
          transform: `translate(-50%, -50%) scale(${mounted ? 1 : 0.6})`,
          opacity: mounted ? 1 : 0,
          transition: "all 1.5s cubic-bezier(0.16, 1, 0.3, 1)",
          background: "radial-gradient(circle, var(--color-accent-glow) 0%, transparent 65%)",
        }}
      />

      {/* ── Content ── */}
      <div className="relative z-10 flex flex-col items-center">
        {/* Logo with ring animation */}
        <div className="relative mb-8">
          {/* Outer pulse ring */}
          <div
            className="absolute inset-0 login-ring"
            style={{
              opacity: mounted ? 1 : 0,
              transition: "opacity 0.8s ease 0.6s",
            }}
          >
            <div className="absolute -inset-5 rounded-3xl border border-(--color-accent)/20 login-ring-pulse" />
            <div className="absolute -inset-10 rounded-[28px] border border-(--color-accent)/10 login-ring-pulse" style={{ animationDelay: "1s" }} />
          </div>

          {/* Logo */}
          <div
            className="relative w-20 h-20 rounded-2xl bg-gradient-to-br from-(--color-accent) to-indigo-600 flex items-center justify-center text-white text-3xl font-black shadow-2xl"
            style={{
              transform: mounted ? "scale(1) translateY(0)" : "scale(0.5) translateY(20px)",
              opacity: mounted ? 1 : 0,
              transition: "all 0.8s cubic-bezier(0.34, 1.56, 0.64, 1)",
              boxShadow: mounted
                ? "0 0 60px var(--color-accent-glow), 0 20px 40px rgba(0,0,0,0.15)"
                : "none",
            }}
          >
            V
            {/* Shine sweep */}
            <div className="absolute inset-0 rounded-2xl overflow-hidden">
              <div className="login-logo-shine" />
            </div>
          </div>
        </div>

        {auth.loading ? (
          <div
            className="flex items-center gap-2.5 text-(--color-text-secondary)"
            style={{
              opacity: mounted ? 1 : 0,
              transform: mounted ? "translateY(0)" : "translateY(10px)",
              transition: "all 0.5s ease 0.4s",
            }}
          >
            <Loader2 size={14} className="animate-spin text-(--color-accent)" />
            <span className="text-sm font-medium">Loading...</span>
          </div>
        ) : (
          <>
            {/* Title */}
            <h1
              className="text-3xl font-black tracking-tight text-(--color-text-primary) mb-1"
              style={{
                opacity: ready ? 1 : 0,
                transform: ready ? "translateY(0)" : "translateY(12px)",
                transition: "all 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.1s",
              }}
            >
              <span className="void-text login-title-glow">VOID</span>{" "}
              <span className="font-light text-(--color-text-secondary)">Launcher</span>
            </h1>

            {/* Subtitle */}
            <p
              className="text-xs text-(--color-text-secondary) mb-10 tracking-wide"
              style={{
                opacity: ready ? 1 : 0,
                transform: ready ? "translateY(0)" : "translateY(10px)",
                transition: "all 0.6s cubic-bezier(0.16, 1, 0.3, 1) 0.2s",
              }}
            >
              Sign in to enter the Void
            </p>

            {/* Error message */}
            {auth.error && (
              <div
                className="mb-5 px-4 py-2 rounded-xl bg-red-500/10 border border-red-500/20 text-xs text-red-400 text-center max-w-xs"
                style={{ animation: "login-shake 0.4s ease" }}
              >
                {auth.error}
              </div>
            )}

            {/* Sign in button */}
            <div
              style={{
                opacity: ready ? 1 : 0,
                transform: ready ? "translateY(0) scale(1)" : "translateY(16px) scale(0.95)",
                transition: "all 0.7s cubic-bezier(0.16, 1, 0.3, 1) 0.35s",
              }}
            >
              <button
                onClick={() => auth.signInWithMicrosoft()}
                disabled={auth.loggingIn}
                className="group relative px-8 py-3.5 rounded-2xl text-white text-sm font-semibold transition-all duration-300 flex items-center gap-3 disabled:opacity-50 login-sign-in-btn"
                style={{
                  background: "linear-gradient(135deg, #2a2a3a 0%, #1a1a2e 100%)",
                }}
              >
                {/* Hover glow */}
                <div className="absolute -inset-px rounded-2xl bg-gradient-to-r from-(--color-accent)/0 via-(--color-accent)/0 to-(--color-accent)/0 group-hover:from-(--color-accent)/20 group-hover:via-(--color-accent)/30 group-hover:to-(--color-accent)/20 transition-all duration-500 opacity-0 group-hover:opacity-100" />
                {/* Border */}
                <div className="absolute inset-0 rounded-2xl border border-white/[0.06] group-hover:border-white/[0.12] transition-colors duration-300" />
                {/* Shimmer */}
                <div className="absolute inset-0 rounded-2xl overflow-hidden">
                  <div className="login-btn-shimmer" />
                </div>

                {auth.loggingIn ? (
                  <div className="relative flex items-center gap-2.5">
                    <Loader2 size={15} className="animate-spin" />
                    <span>Signing in...</span>
                  </div>
                ) : (
                  <div className="relative flex items-center gap-3">
                    <svg width="16" height="16" viewBox="0 0 21 21" fill="none" className="transition-transform duration-300 group-hover:scale-110">
                      <rect x="1" y="1" width="9" height="9" fill="#F25022" />
                      <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
                      <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
                      <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
                    </svg>
                    <span className="tracking-wide">Sign in with Microsoft</span>
                  </div>
                )}
              </button>
            </div>

            {/* Bottom tagline */}
            <p
              className="mt-10 text-[10px] text-(--color-text-secondary)/40 tracking-widest uppercase"
              style={{
                opacity: ready ? 1 : 0,
                transition: "opacity 1s ease 0.8s",
              }}
            >
              Minecraft Modding Reimagined
            </p>
          </>
        )}
      </div>
    </div>
  );
});
