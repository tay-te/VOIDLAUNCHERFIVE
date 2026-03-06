import { useState, useEffect, useMemo } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { SkinViewer3D } from "./SkinViewer";
import { Sparkles } from "lucide-react";

interface WelcomeScreenProps {
  onComplete: () => void;
}

function getGreetingWord() {
  const h = new Date().getHours();
  if (h < 5) return "Good night";
  if (h < 12) return "Good morning";
  if (h < 17) return "Good afternoon";
  if (h < 21) return "Good evening";
  return "Good night";
}

// Floating particle dots
function Particles() {
  const particles = useMemo(() => {
    return Array.from({ length: 40 }, (_, i) => ({
      id: i,
      x: Math.random() * 100,
      y: Math.random() * 100,
      size: Math.random() * 3 + 1,
      delay: Math.random() * 5,
      duration: Math.random() * 4 + 4,
      opacity: Math.random() * 0.4 + 0.1,
    }));
  }, []);

  return (
    <div className="absolute inset-0 pointer-events-none overflow-hidden">
      {particles.map((p) => (
        <div
          key={p.id}
          className="absolute rounded-full bg-(--color-accent) welcome-particle"
          style={{
            left: `${p.x}%`,
            top: `${p.y}%`,
            width: p.size,
            height: p.size,
            opacity: p.opacity,
            animationDelay: `${p.delay}s`,
            animationDuration: `${p.duration}s`,
          }}
        />
      ))}
    </div>
  );
}

export const WelcomeScreen = observer(({ onComplete }: WelcomeScreenProps) => {
  const { auth, instances } = useStore();
  const [phase, setPhase] = useState(0);
  // 0 = initial (skin zoom in)
  // 1 = greeting revealed
  // 2 = stats cascade
  // 3 = exit dissolve

  const greeting = useMemo(() => getGreetingWord(), []);
  const modCount = instances.instances.reduce(
    (s, i) => s + (i.installedMods?.length ?? 0),
    0
  );

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 600),
      setTimeout(() => setPhase(2), 1400),
      setTimeout(() => setPhase(3), 3200),
      setTimeout(() => onComplete(), 4000),
    ];
    return () => timers.forEach(clearTimeout);
  }, [onComplete]);

  return (
    <div
      className={`h-screen flex items-center justify-center bg-(--color-surface) relative overflow-hidden transition-opacity duration-700 ${
        phase >= 3 ? "opacity-0 scale-105" : "opacity-100"
      }`}
      style={{ transition: "opacity 0.7s ease, transform 0.7s ease" }}
    >
      <div className="drag-region absolute top-0 left-0 right-0 h-8" />

      {/* Ambient glows */}
      <div className="absolute inset-0 pointer-events-none">
        <div
          className="absolute top-1/2 left-1/3 -translate-x-1/2 -translate-y-1/2 w-[500px] h-[500px] rounded-full bg-(--color-accent)/6 blur-3xl transition-all duration-[2s]"
          style={{
            transform: `translate(-50%, -50%) scale(${phase >= 1 ? 1.3 : 0.8})`,
            opacity: phase >= 1 ? 1 : 0.3,
          }}
        />
        <div
          className="absolute top-1/2 right-1/4 -translate-y-1/2 w-80 h-80 rounded-full bg-(--color-accent)/4 blur-3xl transition-all duration-[2s] delay-300"
          style={{ opacity: phase >= 2 ? 1 : 0 }}
        />
      </div>

      <Particles />

      {/* Main content */}
      <div className="relative z-10 flex items-center gap-16 px-16">
        {/* 3D Skin — zooms in */}
        <div
          className="transition-all duration-[1.2s] ease-out"
          style={{
            transform: phase >= 1 ? "scale(1) translateX(0)" : "scale(0.6) translateX(40px)",
            opacity: phase >= 1 ? 1 : 0,
          }}
        >
          <div className="relative">
            {/* Glow ring behind skin */}
            <div
              className="absolute inset-0 -m-12 rounded-full bg-(--color-accent)/10 blur-3xl transition-all duration-[1.5s]"
              style={{ opacity: phase >= 1 ? 1 : 0, transform: `scale(${phase >= 2 ? 1.2 : 0.8})` }}
            />
            <SkinViewer3D
              username={auth.username}
              className="w-[420px] h-[580px] relative"
            />
          </div>
        </div>

        {/* Right side — text & stats */}
        <div className="flex flex-col max-w-sm">
          {/* Greeting */}
          <div
            className="transition-all duration-700 ease-out"
            style={{
              transform: phase >= 1 ? "translateY(0)" : "translateY(20px)",
              opacity: phase >= 1 ? 1 : 0,
            }}
          >
            <div className="flex items-center gap-2 mb-2">
              <Sparkles size={14} className="text-(--color-accent)" />
              <span className="text-xs font-semibold uppercase tracking-widest text-(--color-accent)">
                Welcome back
              </span>
            </div>
            <p className="text-sm text-(--color-text-secondary) font-medium">
              {greeting},
            </p>
          </div>

          {/* Username — big entrance */}
          <div
            className="transition-all duration-700 ease-out delay-200"
            style={{
              transform: phase >= 1 ? "translateY(0) scale(1)" : "translateY(30px) scale(0.9)",
              opacity: phase >= 1 ? 1 : 0,
            }}
          >
            <h1 className="text-5xl font-black tracking-tight text-(--color-text-primary) leading-none mt-1">
              {auth.username}
            </h1>
          </div>

          {/* Divider line that draws in */}
          <div className="mt-5 mb-5 h-px bg-(--color-border) relative overflow-hidden">
            <div
              className="absolute inset-y-0 left-0 bg-(--color-accent)/40 transition-all duration-700 ease-out"
              style={{ width: phase >= 2 ? "100%" : "0%" }}
            />
          </div>

          {/* Stats — cascade in one by one */}
          <div className="space-y-3">
            {[
              { label: "Instances", value: instances.instances.length, delay: 0 },
              { label: "Installed Mods", value: modCount, delay: 150 },
            ].map(({ label, value, delay }) => (
              <div
                key={label}
                className="flex items-center justify-between transition-all duration-500 ease-out"
                style={{
                  transform: phase >= 2 ? "translateX(0)" : "translateX(-20px)",
                  opacity: phase >= 2 ? 1 : 0,
                  transitionDelay: `${delay}ms`,
                }}
              >
                <span className="text-sm text-(--color-text-secondary) font-medium">{label}</span>
                <span className="text-lg font-black text-(--color-text-primary) tabular-nums">
                  {value}
                </span>
              </div>
            ))}
          </div>

          {/* "Entering the Void" status */}
          <div
            className="mt-8 flex items-center gap-2.5 transition-all duration-500 ease-out"
            style={{
              opacity: phase >= 2 ? 1 : 0,
              transform: phase >= 2 ? "translateY(0)" : "translateY(10px)",
              transitionDelay: "400ms",
            }}
          >
            <div className="w-1.5 h-1.5 rounded-full bg-(--color-accent) welcome-status-dot" />
            <span className="text-xs font-medium text-(--color-text-secondary)">
              Entering the Void...
            </span>
          </div>
        </div>
      </div>
    </div>
  );
});
