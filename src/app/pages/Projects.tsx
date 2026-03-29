import { Link } from "react-router";
import { useEffect, useMemo, useState } from "react";
import { Filter, Plus, Search, Trash2 } from "lucide-react";
import { api } from "../api/client";
import type { Project } from "../api/types";
import { AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle, AlertDialogTrigger } from "../components/ui/alert-dialog";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Checkbox } from "../components/ui/checkbox";
import { Input } from "../components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";

export function Projects() {
  const [projects, setProjects] = useState<Project[]>([]);
  const [search, setSearch] = useState("");
  const [loading, setLoading] = useState(true);
  const [selectedIds, setSelectedIds] = useState<number[]>([]);

  useEffect(() => {
    const load = async () => {
      try {
        setProjects(await api.getProjects());
      } finally {
        setLoading(false);
      }
    };
    void load();
  }, []);

  const filteredProjects = useMemo(
    () => projects.filter((project) => project.name.toLowerCase().includes(search.toLowerCase())),
    [projects, search],
  );

  const allSelected =
    filteredProjects.length > 0 && filteredProjects.every((project) => selectedIds.includes(project.id));

  const toggleProjectSelection = (projectId: number, checked: boolean) => {
    setSelectedIds((prev) =>
      checked ? [...new Set([...prev, projectId])] : prev.filter((id) => id !== projectId),
    );
  };

  const toggleSelectAll = (checked: boolean) => {
    setSelectedIds((prev) =>
      checked
        ? [...new Set([...prev, ...filteredProjects.map((project) => project.id)])]
        : prev.filter((id) => !filteredProjects.some((project) => project.id === id)),
    );
  };

  const handleDeleteProjects = async (projectIds: number[]) => {
    await Promise.all(projectIds.map((projectId) => api.deleteProject(projectId)));
    setProjects((prev) => prev.filter((project) => !projectIds.includes(project.id)));
    setSelectedIds((prev) => prev.filter((id) => !projectIds.includes(id)));
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">프로젝트</h1>
          <p className="mt-1 text-sm text-gray-600">회계 프로젝트 및 예산 현황을 관리합니다.</p>
        </div>
        <Link to="/projects/new">
          <Button className="gap-2">
            <Plus className="h-4 w-4" />
            프로젝트 생성
          </Button>
        </Link>
      </div>

      <Card>
        <CardContent className="pt-6">
          <div className="flex gap-3">
            <div className="relative flex-1">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
              <Input
                placeholder="프로젝트 검색..."
                className="pl-10"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
              />
            </div>
            <Button variant="outline" className="gap-2">
              <Filter className="h-4 w-4" />
              필터
            </Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>전체 프로젝트 ({filteredProjects.length})</CardTitle>
          <AlertDialog>
            <AlertDialogTrigger asChild>
              <Button variant="outline" className="gap-2" disabled={selectedIds.length === 0}>
                <Trash2 className="h-4 w-4" />
                선택 삭제 ({selectedIds.length})
              </Button>
            </AlertDialogTrigger>
            <AlertDialogContent>
              <AlertDialogHeader>
                <AlertDialogTitle>선택한 프로젝트를 삭제할까요?</AlertDialogTitle>
                <AlertDialogDescription>
                  선택한 프로젝트와 연관된 예산 규칙, 지출, 증빙 문서, 업로드 파일이 함께 삭제됩니다. 이 작업은 되돌릴 수 없습니다.
                </AlertDialogDescription>
              </AlertDialogHeader>
              <AlertDialogFooter>
                <AlertDialogCancel>취소</AlertDialogCancel>
                <AlertDialogAction onClick={() => void handleDeleteProjects(selectedIds)}>삭제</AlertDialogAction>
              </AlertDialogFooter>
            </AlertDialogContent>
          </AlertDialog>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-12">
                  <Checkbox checked={allSelected} onCheckedChange={(checked) => toggleSelectAll(Boolean(checked))} />
                </TableHead>
                <TableHead>프로젝트명</TableHead>
                <TableHead>상태</TableHead>
                <TableHead>기간</TableHead>
                <TableHead>예산</TableHead>
                <TableHead>집행률</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(loading ? [] : filteredProjects).map((project) => {
                const rate = project.totalBudget > 0 ? Math.round((project.executedBudget / project.totalBudget) * 100) : 0;
                const checked = selectedIds.includes(project.id);
                return (
                  <TableRow key={project.id}>
                    <TableCell>
                      <Checkbox checked={checked} onCheckedChange={(value) => toggleProjectSelection(project.id, Boolean(value))} />
                    </TableCell>
                    <TableCell className="font-medium">
                      <Link to={`/projects/${project.id}`} className="hover:text-blue-600">
                        {project.name}
                      </Link>
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={
                          project.status === "ACTIVE"
                            ? "border-green-200 bg-green-50 text-green-700"
                            : project.status === "COMPLETED"
                              ? "border-blue-200 bg-blue-50 text-blue-700"
                              : "border-gray-200 bg-gray-50 text-gray-700"
                        }
                      >
                        {project.status === "ACTIVE" ? "진행중" : project.status === "COMPLETED" ? "완료" : "초안"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-gray-600">
                      {project.startDate} ~ {project.endDate}
                    </TableCell>
                    <TableCell className="text-right">₩{project.totalBudget.toLocaleString()}</TableCell>
                    <TableCell className="text-right">{rate}%</TableCell>
                  </TableRow>
                );
              })}
              {!loading && filteredProjects.length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="py-8 text-center text-gray-500">
                    검색 결과가 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>
    </div>
  );
}
