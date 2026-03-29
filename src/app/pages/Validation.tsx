import { useEffect, useState } from "react";
import { AlertCircle, CheckCircle, Play, Wrench, XCircle } from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { ValidationResult } from "../api/types";
import { ConfirmActionButton } from "../components/ConfirmActionButton";
import { useProjectContext } from "../context/ProjectContext";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";

export function Validation() {
  const { selectedProjectId } = useProjectContext();
  const [results, setResults] = useState<ValidationResult[]>([]);
  const [filter, setFilter] = useState("all");
  const [query, setQuery] = useState("");

  const refresh = async () => {
    if (!selectedProjectId) return;
    const validationResults = await api.getValidations(selectedProjectId);
    setResults(validationResults);
  };

  useEffect(() => {
    if (!selectedProjectId) return;
    void refresh();
  }, [selectedProjectId]);

  const handleRunValidation = async () => {
    if (!selectedProjectId) return;
    const validationResults = await api.runValidations(selectedProjectId);
    setResults(validationResults);
    toast.success("검증을 다시 실행했습니다.");
  };

  const handleResolve = async (id: number) => {
    const updated = await api.resolveValidation(id, "프런트 화면에서 해결 처리");
    setResults((prev) => prev.map((item) => (item.id === id ? updated : item)));
    toast.success("검증 항목을 해결 처리했습니다.");
  };

  const filtered = results.filter((item) => {
    if (filter !== "all" && item.severity !== filter) return false;
    if (!query) return true;
    const normalized = query.toLowerCase();
    return `${item.type} ${item.category} ${item.description}`.toLowerCase().includes(normalized);
  });

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">검증</h1>
          <p className="mt-1 text-sm text-gray-600">오류와 경고를 다시 실행하고 해결 처리합니다.</p>
        </div>
        <Button className="gap-2" onClick={() => void handleRunValidation()}>
          <Play className="h-4 w-4" />
          검증 실행
        </Button>
      </div>

      <Card>
        <CardContent className="flex gap-3 pt-6">
          <Input placeholder="검증 항목 검색" value={query} onChange={(event) => setQuery(event.target.value)} />
          <Button variant={filter === "all" ? "default" : "outline"} onClick={() => setFilter("all")}>전체</Button>
          <Button variant={filter === "VALID" ? "default" : "outline"} onClick={() => setFilter("VALID")}>정상</Button>
          <Button variant={filter === "WARNING" ? "default" : "outline"} onClick={() => setFilter("WARNING")}>경고</Button>
          <Button variant={filter === "ERROR" ? "default" : "outline"} onClick={() => setFilter("ERROR")}>오류</Button>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <SummaryCard title="정상" count={results.filter((item) => item.severity === "VALID").length} icon={<CheckCircle className="h-6 w-6 text-green-600" />} />
        <SummaryCard title="경고" count={results.filter((item) => item.severity === "WARNING").length} icon={<AlertCircle className="h-6 w-6 text-yellow-600" />} />
        <SummaryCard title="오류" count={results.filter((item) => item.severity === "ERROR").length} icon={<XCircle className="h-6 w-6 text-red-600" />} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>검증 결과</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {filtered.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500">
              조건에 맞는 검증 결과가 없습니다.
            </div>
          ) : (
            filtered.map((item) => (
              <div key={item.id} className="flex items-center justify-between rounded-lg border border-gray-200 p-4">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <div className="font-medium text-gray-900">{item.type}</div>
                    <Badge variant="outline">{item.category}</Badge>
                    <Badge variant="outline">{item.severity}</Badge>
                    {item.resolved ? <Badge variant="outline" className="border-green-200 bg-green-50 text-green-700">해결됨</Badge> : null}
                  </div>
                  <div className="text-sm text-gray-600">{item.description}</div>
                  <div className="text-xs text-gray-500">{item.date}{item.resolvedDate ? ` · 해결일 ${item.resolvedDate}` : ""}</div>
                </div>
                <div className="flex gap-2">
                  {!item.resolved && item.severity !== "VALID" ? (
                    <ConfirmActionButton
                      title="검증 항목 해결 처리"
                      description="이 항목을 해결 처리 상태로 변경합니다."
                      actionLabel="해결 처리"
                      onConfirm={() => void handleResolve(item.id)}
                      trigger={
                        <Button variant="outline" className="gap-2">
                          <Wrench className="h-4 w-4" />
                          해결 처리
                        </Button>
                      }
                    />
                  ) : null}
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}

function SummaryCard({ title, count, icon }: { title: string; count: number; icon: React.ReactNode }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between pt-6">
        <div>
          <div className="text-sm text-gray-600">{title}</div>
          <div className="mt-1 text-3xl font-semibold text-gray-900">{count}</div>
        </div>
        {icon}
      </CardContent>
    </Card>
  );
}
