import { useState, useCallback } from "react";
import { observer } from "mobx-react-lite";
import { useStore } from "./stores";
import { Sidebar } from "./components/BottomNav";
import { HomePage } from "./components/HomePage";
import { BrowsePage } from "./components/BrowsePage";
import { InstancesPage } from "./components/InstancesPage";
import { SettingsPage } from "./components/SettingsPage";
import { AuthPage } from "./components/AuthPage";
import { ModPage } from "./components/ModPage";
import { LoginScreen } from "./components/LoginScreen";
import { WelcomeScreen } from "./components/WelcomeScreen";
import { CustomCursor } from "./components/CustomCursor";

const App = observer(() => {
  const { auth } = useStore();
  const [activePage, setActivePage] = useState("home");
  const [selectedModId, setSelectedModId] = useState<string | null>(null);
  const [modReturnPage, setModReturnPage] = useState("home");
  const [welcomeDone, setWelcomeDone] = useState(false);

  const finishWelcome = useCallback(() => setWelcomeDone(true), []);

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
          {renderPage()}
        </main>
      </div>
    </div>
  );
});

export default App;
