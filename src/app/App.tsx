import { Suspense } from "react";
import { RouterProvider } from "react-router";
import { router } from "./routes";
import { ProjectProvider } from "./context/ProjectContext";
import { Toaster } from "./components/ui/sonner";

export default function App() {
  return (
    <ProjectProvider>
      <Suspense fallback={<div className="p-6 text-sm text-gray-500">화면을 불러오는 중입니다.</div>}>
        <RouterProvider router={router} />
      </Suspense>
      <Toaster />
    </ProjectProvider>
  );
}
