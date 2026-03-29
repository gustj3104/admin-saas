import { Outlet, Link, useLocation } from "react-router";
import {
  LayoutDashboard,
  FolderKanban,
  FileSpreadsheet,
  Receipt,
  FileText,
  CheckCircle,
  FileBarChart,
  Bell,
  ChevronDown,
  User,
} from "lucide-react";
import { Badge } from "./ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "./ui/dropdown-menu";
import { Button } from "./ui/button";
import { useProjectContext } from "../context/ProjectContext";

const navigation = [
  { name: "대시보드", href: "/", icon: LayoutDashboard },
  { name: "프로젝트", href: "/projects", icon: FolderKanban },
  { name: "예산 계획", href: "/budget-plan", icon: FileSpreadsheet },
  { name: "지출 기록", href: "/expense-records", icon: Receipt },
  { name: "증빙 문서", href: "/evidence-documents", icon: FileText },
  { name: "검증", href: "/validation", icon: CheckCircle },
  { name: "정산 보고서", href: "/final-settlement", icon: FileBarChart },
];

export function Layout() {
  const location = useLocation();
  const {
    projects,
    selectedProject,
    selectedProjectId,
    setSelectedProjectId,
    loading,
  } = useProjectContext();

  const statusLabel = {
    ACTIVE: "진행중",
    COMPLETED: "완료",
    DRAFT: "초안",
  } as const;

  const statusClassName = {
    ACTIVE: "bg-green-50 text-green-700 border-green-200",
    COMPLETED: "bg-blue-50 text-blue-700 border-blue-200",
    DRAFT: "bg-gray-50 text-gray-700 border-gray-200",
  } as const;

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Left Sidebar */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
        {/* Logo */}
        <div className="h-16 flex items-center px-6 border-b border-gray-200">
          <h1 className="font-semibold text-lg text-gray-900">
            Unnies Accounting Agent
          </h1>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 py-4 space-y-1 overflow-y-auto">
          {navigation.map((item) => {
            const isActive =
              location.pathname === item.href ||
              (item.href !== "/" && location.pathname.startsWith(item.href));
            const Icon = item.icon;

            return (
              <Link
                key={item.name}
                to={item.href}
                className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? "bg-blue-50 text-blue-700"
                    : "text-gray-700 hover:bg-gray-100"
                }`}
              >
                <Icon className="w-5 h-5" />
                <span>{item.name}</span>
              </Link>
            );
          })}
        </nav>

        {/* Sidebar Footer */}
        <div className="p-4 border-t border-gray-200">
          <div className="text-xs text-gray-500">
            © 2026 Unnies Accounting
          </div>
        </div>
      </aside>

      {/* Main Content Area */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top Header */}
        <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-6">
          {/* Left: Project Selector */}
          <div className="flex items-center gap-4">
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex items-center gap-2 px-4 py-2 border border-gray-300 rounded-md bg-white hover:bg-gray-50 transition-colors">
                  <span className="font-medium">
                    {loading ? "프로젝트 불러오는 중..." : selectedProject?.name ?? "프로젝트 없음"}
                  </span>
                  <ChevronDown className="w-4 h-4" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-64">
                {projects.map((project) => (
                  <DropdownMenuItem
                    key={project.id}
                    onClick={() => setSelectedProjectId(project.id)}
                    className={
                      project.id === selectedProjectId ? "bg-blue-50 text-blue-700" : ""
                    }
                  >
                    {project.name}
                  </DropdownMenuItem>
                ))}
                <DropdownMenuSeparator />
                <DropdownMenuItem>
                  <Link to="/projects">모든 프로젝트 보기</Link>
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>

            {selectedProject && (
              <Badge
                variant="outline"
                className={statusClassName[selectedProject.status]}
              >
                {statusLabel[selectedProject.status]}
              </Badge>
            )}
          </div>

          {/* Right: Actions & User Menu */}
          <div className="flex items-center gap-4">
            {/* Quick Actions */}
            <Button variant="outline" size="sm">
              + 지출 등록
            </Button>

            {/* Notifications */}
            <Button variant="ghost" size="icon" className="relative">
              <Bell className="w-5 h-5" />
              <span className="absolute top-1 right-1 w-2 h-2 bg-red-500 rounded-full"></span>
            </Button>

            {/* User Menu */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon">
                  <User className="w-5 h-5" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem>프로필</DropdownMenuItem>
                <DropdownMenuItem>설정</DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem>로그아웃</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>

        {/* Main Content */}
        <main className="flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
