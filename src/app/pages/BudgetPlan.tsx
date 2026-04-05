import { useEffect, useMemo, useState } from "react";
import { AlertCircle, CheckCircle, Download, Edit2, Save } from "lucide-react";
import { api } from "../api/client";
import type { BudgetRule } from "../api/types";
import { useProjectContext } from "../context/ProjectContext";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";

export function BudgetPlan() {
  const { selectedProjectId, selectedProject } = useProjectContext();
  const [budgetItems, setBudgetItems] = useState<BudgetRule[]>([]);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!selectedProjectId) return;
    void api.getBudgetRules(selectedProjectId).then(setBudgetItems);
  }, [selectedProjectId]);

  const totalAllocated = budgetItems.reduce((sum, item) => sum + item.allocated, 0);
  const totalBudget = selectedProject?.totalBudget ?? 0;
  const executedBudget = selectedProject?.executedBudget ?? 0;
  const remaining = totalBudget - executedBudget;
  const allocationRate = totalBudget > 0 ? (totalAllocated / totalBudget) * 100 : 0;
  const warnings = budgetItems
    .filter((item) => item.status !== "VALID")
    .map((item) => `${item.category} / ${item.subcategory}: ${item.ruleDescription}`);

  const budgetSummary = useMemo(
    () =>
      Array.from(
        budgetItems.reduce<Map<string, { itemCount: number; total: number }>>((acc, item) => {
          const current = acc.get(item.category) ?? { itemCount: 0, total: 0 };
          acc.set(item.category, {
            itemCount: current.itemCount + 1,
            total: current.total + item.allocated,
          });
          return acc;
        }, new Map()),
      ),
    [budgetItems],
  );

  const updateBudgetItem = (
    id: number,
    field: "allocated" | "subcategory" | "ruleDescription" | "percentage",
    value: string | number,
  ) => {
    setBudgetItems((prev) => prev.map((item) => (item.id === id ? { ...item, [field]: value } : item)));
  };

  const handleSaveBudgetRules = async () => {
    if (!selectedProjectId) return;
    await api.replaceBudgetRules(
      selectedProjectId,
      budgetItems.map((item) => ({
        category: item.category,
        subcategory: item.subcategory,
        ruleDescription: item.ruleDescription,
        allocated: item.allocated,
        percentage: item.percentage,
        status: item.status,
      })),
    );
    setSaveMessage("예산 규칙이 저장되었습니다.");
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <p className="mt-1 text-xs text-gray-500">현재 집행 반영 금액: ₩{executedBudget.toLocaleString()}</p>
          <h1 className="text-2xl font-semibold text-gray-900">예산 계획</h1>
          <p className="mt-1 text-sm text-gray-600">예산 배분 검토 및 조정을 진행합니다.</p>
        </div>
        <div className="flex flex-col gap-2 sm:flex-row sm:flex-wrap">
          <Button variant="outline" className="gap-2">
            <Download className="h-4 w-4" />
            Word 내보내기
          </Button>
          <Button variant="outline" className="gap-2">
            <Download className="h-4 w-4" />
            HWP 내보내기
          </Button>
          <Button variant="outline" className="gap-2" onClick={() => void handleSaveBudgetRules()}>
            <Save className="h-4 w-4" />
            임시 저장
          </Button>
          <Button className="gap-2" onClick={() => void handleSaveBudgetRules()}>
            <CheckCircle className="h-4 w-4" />
            예산 확정
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-gray-600">총 예산</div>
            <div className="mt-1 text-2xl font-semibold text-gray-900">₩{totalBudget.toLocaleString()}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-gray-600">배정액</div>
            <div className="mt-1 text-2xl font-semibold text-gray-900">₩{totalAllocated.toLocaleString()}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-gray-600">잔여 예산</div>
            <div className="mt-1 text-2xl font-semibold text-blue-600">₩{remaining.toLocaleString()}</div>
          </CardContent>
        </Card>
        <Card>
          <CardContent className="pt-6">
            <div className="text-sm text-gray-600">배정률</div>
            <div className="mt-1 text-2xl font-semibold text-gray-900">{allocationRate.toFixed(1)}%</div>
          </CardContent>
        </Card>
      </div>

      {warnings.length > 0 && (
        <Card className="border-yellow-200 bg-yellow-50">
          <CardHeader>
            <CardTitle className="flex items-center gap-2 text-yellow-900">
              <AlertCircle className="h-5 w-5" />
              경고
            </CardTitle>
          </CardHeader>
          <CardContent>
            <ul className="space-y-1 text-sm text-yellow-800">
              {warnings.map((warning) => (
                <li key={warning}>• {warning}</li>
              ))}
            </ul>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>카테고리별 예산 배분</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>카테고리</TableHead>
                <TableHead>세부 카테고리</TableHead>
                <TableHead>규칙</TableHead>
                <TableHead className="text-right">배정 금액</TableHead>
                <TableHead className="text-right">전체 비율</TableHead>
                <TableHead>상태</TableHead>
                <TableHead className="w-12" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {budgetItems.map((item) => (
                <TableRow key={item.id}>
                  <TableCell className="font-medium">{item.category}</TableCell>
                  <TableCell>{item.subcategory}</TableCell>
                  <TableCell className="text-sm text-gray-600">{item.ruleDescription}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-2">
                      <span>₩</span>
                      <Input
                        type="number"
                        value={item.allocated}
                        onChange={(event) => updateBudgetItem(item.id, "allocated", Number(event.target.value))}
                        className="w-32 text-right"
                      />
                    </div>
                  </TableCell>
                  <TableCell className="text-right font-medium">{item.percentage}%</TableCell>
                  <TableCell>
                    <Badge
                      variant="outline"
                      className={
                        item.status === "VALID"
                          ? "border-green-200 bg-green-50 text-green-700"
                          : item.status === "WARNING"
                            ? "border-yellow-200 bg-yellow-50 text-yellow-700"
                            : "border-red-200 bg-red-50 text-red-700"
                      }
                    >
                      {item.status === "VALID" ? "유효" : item.status === "WARNING" ? "경고" : "오류"}
                    </Badge>
                  </TableCell>
                  <TableCell>
                    <Button variant="ghost" size="icon" className="h-8 w-8">
                      <Edit2 className="h-4 w-4" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              <TableRow className="bg-gray-50 font-semibold">
                <TableCell colSpan={3}>합계</TableCell>
                <TableCell className="text-right">₩{totalAllocated.toLocaleString()}</TableCell>
                <TableCell className="text-right">{allocationRate.toFixed(1)}%</TableCell>
                <TableCell />
                <TableCell />
              </TableRow>
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>예산 현황</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-3">
              {budgetSummary.map(([category, summary]) => {
                const percentage = totalBudget > 0 ? (summary.total / totalBudget) * 100 : 0;

                return (
                  <div key={category} className="flex items-center justify-between rounded-lg bg-gray-50 p-3">
                    <div>
                      <div className="text-sm font-medium">{category}</div>
                      <div className="mt-1 text-xs text-gray-600">{summary.itemCount}개 항목</div>
                    </div>
                    <div className="text-right">
                      <div className="font-medium">₩{summary.total.toLocaleString()}</div>
                      <div className="text-sm text-gray-600">{percentage.toFixed(1)}%</div>
                    </div>
                  </div>
                );
              })}
              {budgetSummary.length === 0 && (
                <div className="rounded-lg border border-dashed border-gray-300 p-4 text-sm text-gray-500">
                  등록된 예산 카테고리가 없습니다.
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>예산 상태</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex items-center justify-between rounded-lg border border-green-200 bg-green-50 p-4">
              <div className="flex items-center gap-2">
                <CheckCircle className="h-5 w-5 text-green-600" />
                <span className="font-medium text-green-900">유효 항목</span>
              </div>
              <span className="text-2xl font-semibold text-green-600">
                {budgetItems.filter((item) => item.status === "VALID").length}
              </span>
            </div>

            <div className="flex items-center justify-between rounded-lg border border-yellow-200 bg-yellow-50 p-4">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-5 w-5 text-yellow-600" />
                <span className="font-medium text-yellow-900">경고</span>
              </div>
              <span className="text-2xl font-semibold text-yellow-600">
                {budgetItems.filter((item) => item.status === "WARNING").length}
              </span>
            </div>

            <div className="flex items-center justify-between rounded-lg border border-red-200 bg-red-50 p-4">
              <div className="flex items-center gap-2">
                <AlertCircle className="h-5 w-5 text-red-600" />
                <span className="font-medium text-red-900">오류</span>
              </div>
              <span className="text-2xl font-semibold text-red-600">
                {budgetItems.filter((item) => item.status === "ERROR").length}
              </span>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="sticky bottom-6 rounded-lg border border-gray-200 bg-white p-4 shadow-lg">
        <div className="flex items-center justify-between">
          <div className="text-sm text-gray-600">
            {saveMessage ?? "변경 내용을 저장하면 서버 예산 규칙에 반영됩니다."}
          </div>
          <div className="flex gap-2">
            <Button variant="outline" className="gap-2" onClick={() => void handleSaveBudgetRules()}>
              <Save className="h-4 w-4" />
              임시 저장
            </Button>
            <Button className="gap-2" onClick={() => void handleSaveBudgetRules()}>
              <CheckCircle className="h-4 w-4" />
              예산 확정
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
}
