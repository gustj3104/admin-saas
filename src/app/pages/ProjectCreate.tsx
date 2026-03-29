import { useState } from "react";
import { useNavigate } from "react-router";
import type { ProjectStatus } from "../api/types";
import { api } from "../api/client";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Textarea } from "../components/ui/textarea";
import { PROJECT_STATUS_OPTIONS } from "../constants/project";
import { useProjectContext } from "../context/ProjectContext";

export function ProjectCreate() {
  const navigate = useNavigate();
  const { refreshProjects } = useProjectContext();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [totalBudget, setTotalBudget] = useState("");
  const [status, setStatus] = useState<ProjectStatus>("DRAFT");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCreate = async () => {
    setSaving(true);
    setError(null);
    try {
      const project = await api.createProject({
        name,
        description,
        totalBudget: Number(totalBudget || 0),
        status,
        startDate,
        endDate,
      });
      await refreshProjects();
      navigate(`/projects/${project.id}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : "프로젝트 생성에 실패했습니다.");
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-6 p-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">프로젝트 생성</h1>
        <p className="mt-1 text-sm text-gray-600">새 프로젝트를 등록하고 기본 정보를 설정합니다.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>기본 정보</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="project-name">프로젝트명</Label>
            <Input id="project-name" value={name} onChange={(event) => setName(event.target.value)} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="project-description">설명</Label>
            <Textarea
              id="project-description"
              rows={4}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="project-budget">총 예산</Label>
              <Input
                id="project-budget"
                type="number"
                value={totalBudget}
                onChange={(event) => setTotalBudget(event.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="project-status">상태</Label>
              <Select value={status} onValueChange={(value) => setStatus(value as ProjectStatus)}>
                <SelectTrigger id="project-status">
                  <SelectValue placeholder="상태를 선택하세요" />
                </SelectTrigger>
                <SelectContent>
                  {PROJECT_STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="project-start">시작일</Label>
              <Input id="project-start" type="date" value={startDate} onChange={(event) => setStartDate(event.target.value)} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="project-end">종료일</Label>
              <Input id="project-end" type="date" value={endDate} onChange={(event) => setEndDate(event.target.value)} />
            </div>
          </div>
          {error && <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          <div className="flex justify-end gap-2">
            <Button variant="outline" onClick={() => navigate("/projects")}>
              취소
            </Button>
            <Button onClick={() => void handleCreate()} disabled={saving}>
              {saving ? "생성 중..." : "프로젝트 생성"}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
