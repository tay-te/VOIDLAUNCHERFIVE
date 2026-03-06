import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { Loader2 } from "lucide-react";

export const LoginScreen = observer(() => {
  const { auth } = useStore();

  return (
    <div className="h-screen flex flex-col items-center justify-center bg-(--color-surface) relative overflow-hidden">
      <div className="drag-region absolute top-0 left-0 right-0 h-8" />

      {/* Subtle background glow */}
      <div className="absolute inset-0 pointer-events-none">
        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-96 h-96 rounded-full bg-(--color-accent)/5 blur-3xl" />
      </div>

      <div className="relative z-10 flex flex-col items-center">
        {/* Logo */}
        <div className="w-16 h-16 rounded-2xl bg-(--color-accent) flex items-center justify-center text-white text-2xl font-black shadow-lg shadow-(--color-accent)/25 mb-6">
          V
        </div>

        {auth.loading ? (
          <div className="flex items-center gap-2.5 text-(--color-text-secondary)">
            <Loader2 size={14} className="animate-spin text-(--color-accent)" />
            <span className="text-sm font-medium">Loading...</span>
          </div>
        ) : (
          <>
            <h1 className="text-2xl font-black tracking-tight text-(--color-text-primary) mb-1">
              <span className="void-text">VOID</span>{" "}
              <span className="font-light text-(--color-text-secondary)">Launcher</span>
            </h1>
            <p className="text-xs text-(--color-text-secondary) mb-8">
              Sign in to continue
            </p>

            {auth.error && (
              <p className="text-xs text-red-500 mb-4">{auth.error}</p>
            )}

            <button
              onClick={() => auth.signInWithMicrosoft()}
              disabled={auth.loggingIn}
              className="px-8 py-3 rounded-xl bg-[#2F2F2F] hover:bg-[#3a3a3a] text-white text-sm font-semibold transition-all cursor-pointer flex items-center gap-3 disabled:opacity-50"
            >
              {auth.loggingIn ? (
                <>
                  <Loader2 size={15} className="animate-spin" />
                  Signing in...
                </>
              ) : (
                <>
                  <svg width="16" height="16" viewBox="0 0 21 21" fill="none">
                    <rect x="1" y="1" width="9" height="9" fill="#F25022" />
                    <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
                    <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
                    <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
                  </svg>
                  Sign in with Microsoft
                </>
              )}
            </button>
          </>
        )}
      </div>
    </div>
  );
});
