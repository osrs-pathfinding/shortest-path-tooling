import { Navigate, Route, Routes } from "react-router-dom";
import App from "./App";

function NotFound() {
  return <main className="not-found"><h1>Page not found</h1><a href="/route">Open the route planner</a></main>;
}
export function PublicRoutes() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/route" replace />} />
      <Route path="/route" element={<App />} />
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
