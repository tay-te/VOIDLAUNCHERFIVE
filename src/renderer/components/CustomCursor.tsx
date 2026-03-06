import { useEffect, useRef, useState } from "react";

export function CustomCursor() {
  const dotRef = useRef<HTMLDivElement>(null);
  const ringRef = useRef<HTMLDivElement>(null);
  const pos = useRef({ x: -100, y: -100 });
  const ringPos = useRef({ x: -100, y: -100 });
  const visible = useRef(false);
  const [hovering, setHovering] = useState(false);
  const [clicking, setClicking] = useState(false);

  useEffect(() => {
    const onMove = (e: MouseEvent) => {
      pos.current.x = e.clientX;
      pos.current.y = e.clientY;
      if (!visible.current) {
        visible.current = true;
        ringPos.current.x = e.clientX;
        ringPos.current.y = e.clientY;
      }
    };

    const onDown = () => setClicking(true);
    const onUp = () => setClicking(false);

    const onOver = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      const isInteractive =
        target.closest("button, a, [role='button'], select, .cursor-pointer, input[type='range']");
      setHovering(!!isInteractive);
    };

    window.addEventListener("mousemove", onMove);
    window.addEventListener("mouseover", onOver);
    window.addEventListener("mousedown", onDown);
    window.addEventListener("mouseup", onUp);

    // Animation loop — ring trails behind with spring physics
    let raf: number;
    const animate = () => {
      // Ring lags behind with damping
      const dx = pos.current.x - ringPos.current.x;
      const dy = pos.current.y - ringPos.current.y;
      ringPos.current.x += dx * 0.15;
      ringPos.current.y += dy * 0.15;

      if (dotRef.current) {
        dotRef.current.style.transform = `translate(${pos.current.x}px, ${pos.current.y}px) translate(-50%, -50%)`;
      }
      if (ringRef.current) {
        ringRef.current.style.transform = `translate(${ringPos.current.x}px, ${ringPos.current.y}px) translate(-50%, -50%)`;
      }

      raf = requestAnimationFrame(animate);
    };
    raf = requestAnimationFrame(animate);

    return () => {
      window.removeEventListener("mousemove", onMove);
      window.removeEventListener("mouseover", onOver);
      window.removeEventListener("mousedown", onDown);
      window.removeEventListener("mouseup", onUp);
      cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <>
      {/* Outer ring — trails behind */}
      <div
        ref={ringRef}
        className="fixed top-0 left-0 pointer-events-none z-[99999] rounded-full border transition-[width,height,border-color,opacity] duration-200"
        style={{
          width: hovering ? 44 : 32,
          height: hovering ? 44 : 32,
          borderWidth: hovering ? 2 : 1.5,
          borderColor: hovering
            ? "var(--color-accent)"
            : "color-mix(in srgb, var(--color-accent) 50%, transparent)",
          opacity: clicking ? 0.4 : hovering ? 0.9 : 0.5,
          background: hovering
            ? "color-mix(in srgb, var(--color-accent) 8%, transparent)"
            : "transparent",
        }}
      />
      {/* Inner dot — instant follow */}
      <div
        ref={dotRef}
        className="fixed top-0 left-0 pointer-events-none z-[99999] rounded-full transition-[width,height,opacity] duration-150"
        style={{
          width: clicking ? 3 : hovering ? 6 : 5,
          height: clicking ? 3 : hovering ? 6 : 5,
          background: "var(--color-accent)",
          opacity: clicking ? 1 : 0.9,
          boxShadow: hovering ? "0 0 8px var(--color-accent-glow)" : "none",
        }}
      />
    </>
  );
}
