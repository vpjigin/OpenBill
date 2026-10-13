import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { HashRouter } from "react-router-dom";
import "../styles.css";
import { BillingApp } from "./BillingApp";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <HashRouter>
      <BillingApp />
    </HashRouter>
  </StrictMode>,
);
