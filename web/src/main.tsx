import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { AppProviders } from "./app/AppProviders";
import { ErrorBoundary } from "./app/ErrorBoundary";
import { PublicRoutes } from "./routes";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <ErrorBoundary><AppProviders><BrowserRouter><PublicRoutes /></BrowserRouter></AppProviders></ErrorBoundary>
  </StrictMode>,
);
