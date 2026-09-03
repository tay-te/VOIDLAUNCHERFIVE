import { useState } from "react";
import { User } from "lucide-react";

function avatarUrl(username: string, size: number) {
  return `https://minotar.net/helm/${encodeURIComponent(username)}/${size}`;
}

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
        className={`rounded-panel bg-(--color-surface-tertiary) flex items-center justify-center ${className ?? ""}`}
        style={{ width: size, height: size }}
      >
        {username ? (
          <span
            className="font-strong text-(--color-text-secondary)"
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
      className={`rounded-panel shadow-md ${className ?? ""}`}
      style={{ width: size, height: size, imageRendering: "pixelated" }}
      draggable={false}
      onError={() => setError(true)}
    />
  );
}
