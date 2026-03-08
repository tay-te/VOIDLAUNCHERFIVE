import { useState, useCallback, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "./stores";
import { Sidebar } from "./components/BottomNav";
import { HomePage } from "./components/HomePage";
import { BrowsePage } from "./components/BrowsePage";
import { InstancesPage } from "./components/InstancesPage";
import { SettingsPage } from "./components/SettingsPage";
import { AuthPage } from "./components/AuthPage";
import { ModPage } from "./components/ModPage";
import { FriendsPage } from "./components/FriendsPage";
import { LoginScreen } from "./components/LoginScreen";
import { WelcomeScreen } from "./components/WelcomeScreen";
import { ImportShareCodeModal } from "./components/ImportShareCodeModal";
import { DownloadToast } from "./components/DownloadToast";
import { UpdateOverlay } from "./components/UpdateOverlay";
import { CustomCursor } from "./components/CustomCursor";
import { ErrorBoundary } from "./components/ErrorBoundary";

const App = observer(() => {
  const { auth, instances, sharing, notifications } = useStore();
  const [activePage, setActivePage] = useState("home");
  const [selectedModId, setSelectedModId] = useState<string | null>(null);
  const [modReturnPage, setModReturnPage] = useState("home");
  const [welcomeDone, setWelcomeDone] = useState(false);
  const [showImportModal, setShowImportModal] = useState(false);

  const finishWelcome = useCallback(() => setWelcomeDone(true), []);

  // Initialize launch IPC listeners once
  useEffect(() => {
    instances.initLaunchListeners();
    return () => instances.disposeLaunchListeners();
  }, []);

  // Scope instances to the authenticated user
  useEffect(() => {
    if (auth.isAuthenticated && auth.uuid) {
      instances.setUser(auth.uuid, auth.username);
      // Ensure Supabase profile exists for sharing features
      sharing.ensureProfile(auth.uuid, auth.username).then(async () => {
        if (!sharing.profileId) return;
        // Wire notification polling once profile is ready
        notifications.setProfileId(sharing.profileId);
        // Check for cloud instances not installed on this device
        instances.setLoadingCloud(true);
        try {
          const [owned, sharedWithMe] = await Promise.all([
            sharing.getMySharedInstances(),
            sharing.getSharedWithMe(),
          ]);
          const all = [...owned];
          for (const s of sharedWithMe) {
            if (!all.some((a) => a.id === s.id)) all.push(s);
          }
          instances.setCloudInstances(all);
        } catch {
          instances.setLoadingCloud(false);
        }
      });
    } else {
      instances.clearUser();
    }
  }, [auth.isAuthenticated, auth.uuid, auth.username]);

  // Gate: show login/loading screen until authenticated
  if (auth.loading || !auth.isAuthenticated) {
    return <><CustomCursor /><LoginScreen /></>;
  }

  // Fancy welcome animation after login
  if (!welcomeDone) {
    return <><CustomCursor /><WelcomeScreen onComplete={finishWelcome} /></>;
  }

  const openMod = (id: string) => {
    setModReturnPage(activePage);
    setSelectedModId(id);
  };

  const closeMod = () => {
    setSelectedModId(null);
    setActivePage(modReturnPage);
  };

  const navigate = (page: string) => {
    setSelectedModId(null);
    setActivePage(page);
  };

  const renderPage = () => {
    if (selectedModId) {
      return <ModPage modId={selectedModId} onBack={closeMod} />;
    }
    switch (activePage) {
      case "home":
        return <HomePage onOpenMod={openMod} onNavigate={navigate} />;
      case "browse":
        return <BrowsePage onOpenMod={openMod} />;
      case "instances":
        return <InstancesPage onNavigate={navigate} />;
      case "friends":
        return (
          <FriendsPage
            onImportCode={() => setShowImportModal(true)}
          />
        );
      case "settings":
        return <SettingsPage />;
      case "auth":
        return <AuthPage onBack={() => navigate("home")} />;
      default:
        return <HomePage onOpenMod={openMod} onNavigate={navigate} />;
    }
  };

  return (
    <div className="flex h-screen overflow-hidden bg-(--color-surface)">
      <CustomCursor />
      {/* Sidebar column */}
      <div className="flex flex-col flex-shrink-0 bg-(--color-sidebar) border-r border-(--color-border)">
        <div className="drag-region h-8" />
        <Sidebar
          activePage={selectedModId ? "" : activePage}
          onNavigate={navigate}
        />
      </div>

      {/* Content column */}
      <div className="flex flex-col flex-1 overflow-hidden">
        <div className="drag-region h-8 flex-shrink-0" />
        <main className="flex-1 overflow-y-auto">
          <ErrorBoundary>
            {renderPage()}
          </ErrorBoundary>
        </main>
      </div>

      {/* Import modal */}
      {showImportModal && (
        <ImportShareCodeModal
          onClose={() => setShowImportModal(false)}
          onImported={() => navigate("instances")}
        />
      )}

      {/* Download progress toast */}
      <DownloadToast />

      {/* Update overlay */}
      <UpdateOverlay />
    </div>
  );
});

export default App;
