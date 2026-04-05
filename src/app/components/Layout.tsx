import { useState } from "react";
import { Outlet, Link, useLocation } from "react-router";
import {
  Bell,
  CheckCircle,
  ChevronDown,
  FileBarChart,
  FileSpreadsheet,
  FileText,
  FolderKanban,
  LayoutDashboard,
  Menu,
  Receipt,
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
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetTrigger } from "./ui/sheet";
import { useProjectContext } from "../context/ProjectContext";

type NavigationItem = {
  name: string;
  href: string;
  icon: typeof LayoutDashboard;
};

type NavigationGroup = {
  label: string;
  items: NavigationItem[];
};

const navigationGroups: NavigationGroup[] = [
  {
    label: "프로젝트",
    items: [
      { name: "대시보드", href: "/", icon: LayoutDashboard },
      { name: "프로젝트 관리", href: "/projects", icon: FolderKanban },
      { name: "검증", href: "/validation", icon: CheckCircle },
    ],
  },
  {
    label: "지출",
    items: [
      { name: "지출 기록", href: "/expense-records", icon: Receipt },
      { name: "증빙 문서", href: "/evidence-documents", icon: FileText },
      { name: "정산 보고서", href: "/final-settlement", icon: FileBarChart },
    ],
  },
];

function NavigationLinks({
  pathname,
  onNavigate,
}: {
  pathname: string;
  onNavigate?: () => void;
}) {
  return (
    <nav className="flex flex-1 flex-col overflow-y-auto px-3 py-4">
      {navigationGroups.map((group) => (
        <div key={group.label} className="mb-5">
          <div className="px-3 pb-2 text-xs font-semibold uppercase tracking-[0.18em] text-gray-400">
            {group.label}
          </div>
          <div className="flex flex-col gap-1">
            {group.items.map((item) => {
              const isActive =
                pathname === item.href ||
                (item.href !== "/" && pathname.startsWith(item.href));
              const Icon = item.icon;

              return (
                <Link
                  key={item.name}
                  to={item.href}
                  onClick={onNavigate}
                  className={`flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
                    isActive ? "bg-blue-50 text-blue-700" : "text-gray-700 hover:bg-gray-100"
                  }`}
                >
                  <Icon className="h-5 w-5" />
                  <span>{item.name}</span>
                </Link>
              );
            })}
          </div>
        </div>
      ))}
    </nav>
  );
}

export function Layout() {
  const location = useLocation();
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const {
    projects,
    selectedProject,
    selectedProjectId,
    setSelectedProjectId,
    loading,
  } = useProjectContext();

  const statusLabel = {
    ACTIVE: "진행 중",
    COMPLETED: "완료",
    DRAFT: "초안",
  } as const;

  const statusClassName = {
    ACTIVE: "bg-green-50 text-green-700 border-green-200",
    COMPLETED: "bg-blue-50 text-blue-700 border-blue-200",
    DRAFT: "bg-gray-50 text-gray-700 border-gray-200",
  } as const;

  return (
    <div className="flex min-h-svh bg-gray-50">
      <aside className="hidden w-64 flex-col border-r border-gray-200 bg-white md:flex">
        <div className="flex h-16 items-center border-b border-gray-200 px-6">
          <h1 className="text-lg font-semibold text-gray-900">Unnies Accounting Agent</h1>
        </div>

        <NavigationLinks pathname={location.pathname} />

        <div className="border-t border-gray-200 p-4 text-xs text-gray-500">
          2026 Unnies Accounting
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col overflow-hidden">
        <header className="flex min-h-16 flex-wrap items-center justify-between gap-3 border-b border-gray-200 bg-white px-4 py-3 md:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <div className="md:hidden">
              <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
                <SheetTrigger asChild>
                  <Button variant="outline" size="icon">
                    <Menu className="h-5 w-5" />
                  </Button>
                </SheetTrigger>
                <SheetContent side="left" className="p-0">
                  <SheetHeader className="border-b border-gray-200 px-6 py-4 text-left">
                    <SheetTitle>Unnies Accounting Agent</SheetTitle>
                  </SheetHeader>
                  <div className="flex h-full flex-col">
                    <NavigationLinks
                      pathname={location.pathname}
                      onNavigate={() => setMobileNavOpen(false)}
                    />
                  </div>
                </SheetContent>
              </Sheet>
            </div>

            <div className="min-w-0">
              <div className="text-xs font-medium uppercase tracking-[0.16em] text-gray-400 md:hidden">
                Admin SaaS
              </div>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <button className="flex max-w-[min(72vw,20rem)] items-center gap-2 rounded-md border border-gray-300 bg-white px-3 py-2 text-left hover:bg-gray-50">
                    <span className="truncate font-medium">
                      {loading ? "프로젝트 불러오는 중..." : selectedProject?.name ?? "프로젝트 없음"}
                    </span>
                    <ChevronDown className="h-4 w-4 shrink-0" />
                  </button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="start" className="w-64">
                  {projects.map((project) => (
                    <DropdownMenuItem
                      key={project.id}
                      onClick={() => setSelectedProjectId(project.id)}
                      className={project.id === selectedProjectId ? "bg-blue-50 text-blue-700" : ""}
                    >
                      {project.name}
                    </DropdownMenuItem>
                  ))}
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <Link to="/projects">모든 프로젝트 보기</Link>
                  </DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>

            {selectedProject && (
              <Badge variant="outline" className={`hidden sm:inline-flex ${statusClassName[selectedProject.status]}`}>
                {statusLabel[selectedProject.status]}
              </Badge>
            )}
          </div>

          <div className="flex w-full items-center justify-end gap-2 sm:w-auto sm:gap-3">
            <Link to="/expense-records" className="hidden sm:block">
              <Button variant="outline" size="sm">
                + 지출 등록
              </Button>
            </Link>

            <Button variant="ghost" size="icon" className="relative">
              <Bell className="h-5 w-5" />
              <span className="absolute right-1 top-1 h-2 w-2 rounded-full bg-red-500" />
            </Button>

            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon">
                  <User className="h-5 w-5" />
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

        <main className="min-w-0 flex-1 overflow-y-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
