import { Component, type ErrorInfo, type ReactNode } from "react";

export class ErrorBoundary extends Component<{ children: ReactNode }, { error?: Error }> {
  state: { error?: Error } = {};

  static getDerivedStateFromError(error: Error) { return { error }; }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Route planner failed", error, info.componentStack);
  }

  render() {
    if (!this.state.error) return this.props.children;
    return <main className="fatal-error">
      <p className="eyebrow">OSRS Travel</p>
      <h1>The route planner hit a problem</h1>
      <p>Your saved account has not been changed.</p>
      <button type="button" className="primary-button" onClick={() => window.location.reload()}>Reload planner</button>
    </main>;
  }
}
