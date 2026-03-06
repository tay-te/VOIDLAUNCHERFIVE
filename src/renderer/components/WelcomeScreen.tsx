import { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { PlayerAvatar } from "./SkinViewer";

interface WelcomeScreenProps {
  onComplete: () => void;
}

const loadingMessages = [
  "Entering the Void...",
  "Loading your world...",
  "Preparing the launcher...",
  "Summoning blocks...",
  "Brewing potions...",
  "Enchanting items...",
  "Mining diamonds...",
  "Crafting your experience...",
];

function pickRandom<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

export const WelcomeScreen = observer(({ onComplete }: WelcomeScreenProps) => {
  const { auth } = useStore();
  const [phase, setPhase] = useState(0);
  // 0 = fade in, 1 = visible, 2 = fade out
  const [message] = useState(() => pickRandom(loadingMessages));

  useEffect(() => {
    const timers = [
      setTimeout(() => setPhase(1), 100),
      setTimeout(() => setPhase(2), 2200),
      setTimeout(() => onComplete(), 2800),
    ];
    return () => timers.forEach(clearTimeout);
  }, [onComplete]);

  return (
    <div
      className="h-screen flex items-center justify-center bg-(--color-surface) relative overflow-hidden"
      style={{
        opacity: phase === 0 ? 0 : phase === 2 ? 0 : 1,
        transform: phase === 2 ? "scale(1.02)" : "scale(1)",
        transition: "opacity 0.6s ease, transform 0.6s ease",
      }}
    >
      <div className="drag-region absolute top-0 left-0 right-0 h-8" />

      {/* Subtle accent glow */}
      <div className="absolute inset-0 pointer-events-none">
        <div
          className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-80 h-80 rounded-full bg-(--color-accent)/8 blur-3xl"
          style={{
            transform: `translate(-50%, -50%) scale(${phase >= 1 ? 1.2 : 0.8})`,
            transition: "transform 1.5s ease",
          }}
        />
      </div>

      {/* Center content */}
      <div className="relative z-10 flex flex-col items-center">
        {/* Player head */}
        <div
          style={{
            transform: phase >= 1 ? "translateY(0) scale(1)" : "translateY(10px) scale(0.9)",
            opacity: phase >= 1 ? 1 : 0,
            transition: "all 0.5s ease",
          }}
        >
          <div className="relative">
            <div className="absolute -inset-4 rounded-full bg-(--color-accent)/10 blur-2xl" />
            <PlayerAvatar
              username={auth.username}
              size={80}
              className="relative rounded-2xl shadow-lg shadow-(--color-accent)/20"
            />
          </div>
        </div>

        {/* Loading message */}
        <div
          className="mt-6 flex flex-col items-center gap-2"
          style={{
            transform: phase >= 1 ? "translateY(0)" : "translateY(8px)",
            opacity: phase >= 1 ? 1 : 0,
            transition: "all 0.5s ease 0.15s",
          }}
        >
          <p className="text-sm font-medium text-(--color-text-secondary) welcome-loading-text">
            {message}
          </p>
          <div className="flex gap-1 mt-1">
            <div className="w-1 h-1 rounded-full bg-(--color-accent) welcome-dot" style={{ animationDelay: "0s" }} />
            <div className="w-1 h-1 rounded-full bg-(--color-accent) welcome-dot" style={{ animationDelay: "0.15s" }} />
            <div className="w-1 h-1 rounded-full bg-(--color-accent) welcome-dot" style={{ animationDelay: "0.3s" }} />
          </div>
        </div>
      </div>
    </div>
  );
});
