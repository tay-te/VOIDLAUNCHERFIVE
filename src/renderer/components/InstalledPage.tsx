import { Package } from "lucide-react";

export function InstalledPage() {
  return (
    <div className="p-6 space-y-4">
      <h1 className="text-2xl font-bold text-(--color-text-primary)">
        Installed Mods
      </h1>

      <div className="flex flex-col items-center justify-center py-20 text-(--color-text-secondary)">
        <Package size={48} strokeWidth={1} />
        <p className="mt-4 text-sm">No mods installed yet</p>
        <p className="text-xs mt-1">
          Browse and install mods to see them here
        </p>
      </div>
    </div>
  );
}
