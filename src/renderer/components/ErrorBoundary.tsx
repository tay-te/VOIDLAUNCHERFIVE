import { Component, type ReactNode } from "react";
import { AlertTriangle, RotateCcw } from "lucide-react";

interface Props {
  children: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex flex-col items-center justify-center h-full py-20 px-8">
          <div className="w-14 h-14 rounded-2xl bg-red-500/10 flex items-center justify-center mb-5">
            <AlertTriangle size={24} className="text-red-500" />
          </div>
          <h2 className="text-lg font-bold text-(--color-text-primary)">
            Something went wrong
          </h2>
          <p className="text-sm text-(--color-text-secondary) mt-1.5 text-center max-w-sm">
            This page encountered an unexpected error. Your data is safe.
          </p>
          {this.state.error && (
            <p className="text-xs text-(--color-text-secondary) mt-3 px-4 py-2.5 rounded-xl bg-(--color-surface-tertiary) font-mono max-w-md truncate">
              {this.state.error.message}
            </p>
          )}
          <button
            onClick={this.handleReset}
            className="flex items-center gap-2 mt-6 px-5 py-2.5 rounded-xl bg-(--color-accent) hover:bg-(--color-accent-hover) text-white text-sm font-semibold transition-all cursor-pointer shadow-sm shadow-(--color-accent)/20"
          >
            <RotateCcw size={14} />
            Try Again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}
