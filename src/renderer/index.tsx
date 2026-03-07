import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./index.css";

class AppErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  render() {
    if (this.state.error) {
      return (
        <div style={{ padding: 40, fontFamily: "system-ui, sans-serif", color: "#ededf0", background: "#0c0c10", height: "100vh" }}>
          <h1 style={{ fontSize: 20, fontWeight: 700, marginBottom: 8 }}>Something went wrong</h1>
          <p style={{ fontSize: 14, color: "#8888a0", marginBottom: 16 }}>
            The app encountered an error. Try restarting VOID Launcher.
          </p>
          <pre style={{ fontSize: 12, color: "#ef4444", whiteSpace: "pre-wrap", wordBreak: "break-word" }}>
            {this.state.error.message}
          </pre>
          <button
            onClick={() => window.location.reload()}
            style={{ marginTop: 20, padding: "8px 20px", borderRadius: 8, background: "#6366f1", color: "#fff", border: "none", cursor: "pointer", fontSize: 13, fontWeight: 600 }}
          >
            Reload
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <AppErrorBoundary>
      <App />
    </AppErrorBoundary>
  </React.StrictMode>
);
