import { observer } from "mobx-react-lite";
import { useStore } from "../stores";
import { Shield, LogOut } from "lucide-react";
import { PlayerAvatar } from "./SkinViewer";

export const AuthPage = observer(
  ({ onBack }: { onBack: () => void }) => {
    const { auth } = useStore();

    if (auth.isAuthenticated) {
      return (
        <div className="flex items-center justify-center h-full">
          <div className="w-full max-w-sm space-y-6">
            <div className="rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) p-8 text-center">
              <div className="mx-auto mb-4 w-14 h-14">
                <PlayerAvatar
                  uuid={auth.uuid}
                  username={auth.username}
                  size={56}
                  className="rounded-full ring-2 ring-(--color-accent)/25"
                />
              </div>
              <h1 className="text-2xl font-black tracking-tight text-(--color-text-primary)">
                {auth.username}
              </h1>
              <p className="text-sm text-(--color-text-secondary) mt-1">
                Signed in with Microsoft
              </p>
              {auth.mcAccount?.xboxAccount?.gamertag && (
                <p className="text-xs text-(--color-text-secondary) mt-1">
                  Xbox: {auth.mcAccount.xboxAccount.gamertag}
                </p>
              )}
            </div>

            <button
              onClick={async () => {
                await auth.signOut();
                onBack();
              }}
              className="w-full py-3 rounded-xl bg-(--color-surface-secondary) border border-(--color-border) hover:bg-(--color-surface-tertiary) text-(--color-text-primary) text-sm font-bold transition-all cursor-pointer flex items-center justify-center gap-2"
            >
              <LogOut size={16} />
              Sign Out
            </button>
          </div>
        </div>
      );
    }

    return (
      <div className="flex items-center justify-center h-full">
        <div className="w-full max-w-sm space-y-6">
          <div className="rounded-2xl bg-(--color-surface-secondary) border border-(--color-border) p-8 text-center">
            <div className="w-12 h-12 rounded-xl bg-(--color-accent)/10 flex items-center justify-center mx-auto mb-4">
              <Shield size={22} className="text-(--color-accent)" />
            </div>
            <h1 className="text-2xl font-black tracking-tight text-(--color-text-primary)">
              Enter the <span className="void-text">Void</span>
            </h1>
            <p className="text-sm text-(--color-text-secondary) mt-1.5">
              Sign in with your Microsoft account to play Minecraft
            </p>
          </div>

          {auth.error && (
            <p className="text-xs text-red-500 px-1 text-center">{auth.error}</p>
          )}

          <button
            onClick={() => auth.signInWithMicrosoft()}
            disabled={auth.loggingIn}
            className="w-full py-3 rounded-xl bg-[#2F2F2F] hover:bg-[#404040] text-white text-sm font-bold transition-colors cursor-pointer flex items-center justify-center gap-3 shadow-md shadow-black/15 disabled:opacity-50"
          >
            <svg width="18" height="18" viewBox="0 0 21 21" fill="none">
              <rect x="1" y="1" width="9" height="9" fill="#F25022" />
              <rect x="11" y="1" width="9" height="9" fill="#7FBA00" />
              <rect x="1" y="11" width="9" height="9" fill="#00A4EF" />
              <rect x="11" y="11" width="9" height="9" fill="#FFB900" />
            </svg>
            {auth.loggingIn ? "Signing in..." : "Sign in with Microsoft"}
          </button>
        </div>
      </div>
    );
  }
);
