import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api } from "../api/client";
import type { Project } from "../api/types";

interface ProjectContextValue {
  projects: Project[];
  selectedProjectId: number | null;
  selectedProject: Project | null;
  loading: boolean;
  error: string | null;
  setSelectedProjectId: (projectId: number) => void;
  refreshProjects: () => Promise<void>;
}

const ProjectContext = createContext<ProjectContextValue | null>(null);

const STORAGE_KEY = "admin-saas:selected-project-id";

export function ProjectProvider({ children }: { children: ReactNode }) {
  const [projects, setProjects] = useState<Project[]>([]);
  const [selectedProjectId, setSelectedProjectIdState] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const refreshProjects = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.getProjects();
      setProjects(data);

      const stored = window.localStorage.getItem(STORAGE_KEY);
      const storedId = stored ? Number(stored) : null;
      const defaultId = data[0]?.id ?? null;
      const nextId =
        storedId && data.some((project) => project.id === storedId)
          ? storedId
          : defaultId;
      setSelectedProjectIdState(nextId);
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void refreshProjects();
  }, []);

  const setSelectedProjectId = (projectId: number) => {
    setSelectedProjectIdState(projectId);
    window.localStorage.setItem(STORAGE_KEY, String(projectId));
  };

  const selectedProject =
    projects.find((project) => project.id === selectedProjectId) ?? null;

  const value = useMemo(
    () => ({
      projects,
      selectedProjectId,
      selectedProject,
      loading,
      error,
      setSelectedProjectId,
      refreshProjects,
    }),
    [projects, selectedProjectId, selectedProject, loading, error]
  );

  return (
    <ProjectContext.Provider value={value}>{children}</ProjectContext.Provider>
  );
}

export function useProjectContext() {
  const context = useContext(ProjectContext);
  if (!context) {
    throw new Error("useProjectContext must be used within ProjectProvider");
  }
  return context;
}
