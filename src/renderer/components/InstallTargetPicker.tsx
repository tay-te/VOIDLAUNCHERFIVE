import { useMemo, useState } from "react";
import { Check, ChevronDown, Laptop2 } from "lucide-react";
import type { Instance } from "../stores/InstanceStore";

interface InstallTargetPickerProps {
  instances: Instance[];
  selectedInstanceId: string | null;
  onChange: (instanceId: string | null) => void;
  compact?: boolean;
}

export function InstallTargetPicker({
  instances,
  selectedInstanceId,
  onChange,
  compact = false,
}: InstallTargetPickerProps) {
  const [open, setOpen] = useState(false);

  const selectedInstance = useMemo(
    () => instances.find((instance) => instance.id === selectedInstanceId) ?? null,
    [instances, selectedInstanceId]
  );

  return (
    <div className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        className={`flex w-full items-center justify-between gap-3 rounded-panel border border-(--color-border) bg-(--color-surface) text-left transition-colors hover:bg-(--color-surface-tertiary) cursor-pointer ${
          compact ? "px-3 py-2.5" : "px-4 py-3"
        }`}
      >
        <div className="min-w-0">
          <p className="text-micro font-strong uppercase tracking-[0.16em] text-(--color-text-secondary)">
            Install Target
          </p>
          <p className="mt-1 truncate text-sm font-emphasis text-(--color-text-primary)">
            {selectedInstance ? selectedInstance.name : "Auto-select best profile"}
          </p>
          <p className="text-caption text-(--color-text-secondary)">
            {selectedInstance
              ? `${selectedInstance.version} · ${selectedInstance.loader}`
              : "Use the best compatible profile, or create one automatically"}
          </p>
        </div>
        <ChevronDown
          size={14}
          className={`flex-shrink-0 text-(--color-text-secondary) transition-transform ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>

      {open && (
        <div className="absolute left-0 top-full z-50 mt-2 w-full rounded-panel border border-(--color-border) bg-(--color-surface-secondary) p-1.5 shadow-xl shadow-black/10 picker-dropdown">
          <button
            type="button"
            onClick={() => {
              onChange(null);
              setOpen(false);
            }}
            className={`flex w-full items-center gap-3 rounded-[0.875rem] px-3 py-3 text-left transition-colors cursor-pointer ${
              selectedInstanceId === null
                ? "bg-(--color-accent)/10 text-(--color-accent)"
                : "hover:bg-(--color-surface-tertiary)"
            }`}
          >
            <Laptop2 size={14} className="flex-shrink-0" />
            <div className="min-w-0 flex-1">
              <p className="truncate text-sm font-emphasis">Auto-select best profile</p>
              <p className="text-caption text-(--color-text-secondary)">
                Installs into the best compatible profile, or creates one
              </p>
            </div>
            {selectedInstanceId === null && <Check size={14} className="flex-shrink-0" />}
          </button>

          {instances.map((instance) => (
            <button
              key={instance.id}
              type="button"
              onClick={() => {
                onChange(instance.id);
                setOpen(false);
              }}
              className={`mt-1 flex w-full items-center gap-3 rounded-[0.875rem] px-3 py-3 text-left transition-colors cursor-pointer ${
                selectedInstanceId === instance.id
                  ? "bg-(--color-accent)/10 text-(--color-accent)"
                  : "hover:bg-(--color-surface-tertiary)"
              }`}
            >
              <div
                className="flex h-8 w-8 flex-shrink-0 items-center justify-center rounded-control text-xs font-strong"
                style={{
                  backgroundColor: `${instance.iconColor}18`,
                  color: instance.iconColor,
                }}
              >
                {instance.name[0]?.toUpperCase() ?? "?"}
              </div>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-emphasis text-(--color-text-primary)">
                  {instance.name}
                </p>
                <p className="text-caption text-(--color-text-secondary)">
                  {instance.version} · {instance.loader}
                </p>
              </div>
              {selectedInstanceId === instance.id && <Check size={14} className="flex-shrink-0" />}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
