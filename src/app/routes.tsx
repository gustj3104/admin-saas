import { lazy } from "react";
import { createBrowserRouter } from "react-router";
import { Layout } from "./components/Layout";

const Dashboard = lazy(() => import("./pages/Dashboard").then((module) => ({ default: module.Dashboard })));
const Projects = lazy(() => import("./pages/Projects").then((module) => ({ default: module.Projects })));
const ProjectDetail = lazy(() => import("./pages/ProjectDetail").then((module) => ({ default: module.ProjectDetail })));
const ProjectCreate = lazy(() => import("./pages/ProjectCreate").then((module) => ({ default: module.ProjectCreate })));
const BudgetPlan = lazy(() => import("./pages/BudgetPlan").then((module) => ({ default: module.BudgetPlan })));
const ExpenseRecords = lazy(() => import("./pages/ExpenseRecords").then((module) => ({ default: module.ExpenseRecords })));
const EvidenceDocuments = lazy(() => import("./pages/EvidenceDocuments").then((module) => ({ default: module.EvidenceDocuments })));
const Validation = lazy(() => import("./pages/Validation").then((module) => ({ default: module.Validation })));
const FinalSettlement = lazy(() => import("./pages/FinalSettlement").then((module) => ({ default: module.FinalSettlement })));

export const router = createBrowserRouter([
  {
    path: "/",
    Component: Layout,
    children: [
      { index: true, Component: Dashboard },
      { path: "projects", Component: Projects },
      { path: "projects/new", Component: ProjectCreate },
      { path: "projects/:id", Component: ProjectDetail },
      { path: "budget-plan", Component: BudgetPlan },
      { path: "expense-records", Component: ExpenseRecords },
      { path: "evidence-documents", Component: EvidenceDocuments },
      { path: "validation", Component: Validation },
      { path: "final-settlement", Component: FinalSettlement },
    ],
  },
]);
