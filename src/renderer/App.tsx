import { Suspense, lazy, useState, useCallback, useEffect } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "./stores";
import { Sidebar } from "./components/BottomNav";
import { ErrorBoundary } from "./components/ErrorBoundary";

const HomePage = lazy(() =>
  import("./components/HomePage").then((module) => ({ default: module.HomePage }))
);
const BrowsePage = lazy(() =>
  import("./components/BrowsePage").then((module) => ({ default: module.BrowsePage }))
);
const InstancesPage = lazy(() =>
  import("./components/InstancesPage").then((module) => ({ default: module.InstancesPage }))
);
const SettingsPage = lazy(() =>
  import("./components/SettingsPage").then((module) => ({ default: module.SettingsPage }))
);
const AuthPage = lazy(() =>
  import("./components/AuthPage").then((module) => ({ default: module.AuthPage }))
);
const ModPage = lazy(() =>
  import("./components/ModPage").then((module) => ({ default: module.ModPage }))
);
const FriendsPage = lazy(() =>
  import("./components/FriendsPage").then((module) => ({ default: module.FriendsPage }))
);
const LoginScreen = lazy(() =>
  import("./components/LoginScreen").then((module) => ({ default: module.LoginScreen }))
);
const WelcomeScreen = lazy(() =>
  import("./components/WelcomeScreen").then((module) => ({ default: module.WelcomeScreen }))
);
const ImportShareCodeModal = lazy(() =>
  import("./components/ImportShareCodeModal").then((module) => ({
    default: module.ImportShareCodeModal,
  }))
);
const DownloadToast = lazy(() =>
  import("./components/DownloadToast").then((module) => ({ default: module.DownloadToast }))
);
const UpdateOverlay = lazy(() =>
  import("./components/UpdateOverlay").then((module) => ({ default: module.UpdateOverlay }))
);

function preloadCommonViews() {
  void import("./components/BrowsePage");
  void import("./components/InstancesPage");
  void import("./components/SettingsPage");
  void import("./components/FriendsPage");
  void import("./components/ModPage");
}

function FullScreenLoader() {
  return (
    <div className="h-screen flex items-center justify-center bg-(--color-surface) text-sm font-medium text-(--color-text-secondary)">
      <div className="drag-region absolute top-0 left-0 right-0 h-8" />
      Loading...
    </div>
  );
}

function ContentLoader() {
  return (
    <div className="h-full flex items-center justify-center text-sm font-medium text-(--color-text-secondary)">
      Loading...
    </div>
  );
}

const App = observer(() => {
  const { auth, instances, sharing, notifications, installs } = useStore();
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

  useEffect(() => {
    if (!auth.isAuthenticated || !welcomeDone) return;

    const scheduler =
      "requestIdleCallback" in window
        ? window.requestIdleCallback(() => preloadCommonViews(), { timeout: 1500 })
        : window.setTimeout(preloadCommonViews, 600);

    return () => {
      if (typeof scheduler === "number") {
        window.clearTimeout(scheduler);
      } else if ("cancelIdleCallback" in window) {
        window.cancelIdleCallback(scheduler);
      }
    };
  }, [auth.isAuthenticated, welcomeDone]);

  // Scope instances to the authenticated user
  useEffect(() => {
    if (auth.isAuthenticated && auth.uuid) {
      instances.setUser(auth.uuid, auth.username);
      void installs.reconcileAllInstances();
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
    return (
      <Suspense fallback={<FullScreenLoader />}>
        <LoginScreen />
      </Suspense>
    );
  }

  // Fancy welcome animation after login
  if (!welcomeDone) {
    return (
      <Suspense fallback={<FullScreenLoader />}>
        <WelcomeScreen onComplete={finishWelcome} />
      </Suspense>
    );
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
    if (page !== "browse") {
      installs.clearPreferredInstance();
    }
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
          <Suspense fallback={<ContentLoader />}>
            <ErrorBoundary>
              {renderPage()}
            </ErrorBoundary>
          </Suspense>
        </main>
      </div>

      {/* Import modal */}
      {showImportModal && (
        <Suspense fallback={null}>
          <ImportShareCodeModal
            onClose={() => setShowImportModal(false)}
            onImported={() => navigate("instances")}
          />
        </Suspense>
      )}

      {/* Download progress toast */}
      <Suspense fallback={null}>
        <DownloadToast />
      </Suspense>

      {/* Update overlay */}
      <Suspense fallback={null}>
        <UpdateOverlay />
      </Suspense>
    </div>
  );
});

export default App;
