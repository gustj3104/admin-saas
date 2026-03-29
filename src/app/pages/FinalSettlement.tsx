import { useEffect, useState } from "react";
import { Download, FileText, Save, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { BudgetRule, Expense, ProjectFile, Settlement } from "../api/types";
import { ConfirmActionButton } from "../components/ConfirmActionButton";
import { useProjectContext } from "../context/ProjectContext";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Textarea } from "../components/ui/textarea";

export function FinalSettlement() {
  const { selectedProjectId, selectedProject } = useProjectContext();
  const [settlement, setSettlement] = useState<Settlement | null>(null);
  const [budgetRules, setBudgetRules] = useState<BudgetRule[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [templateFiles, setTemplateFiles] = useState<ProjectFile[]>([]);
  const [form, setForm] = useState({
    reportTitle: "",
    reportDate: "",
    preparedBy: "",
    approvedBy: "",
    summaryNotes: "",
    periodStart: "",
    periodEnd: "",
  });

  const refresh = async () => {
    if (!selectedProjectId) return;

    const [latestSettlement, rules, expenseData, files] = await Promise.all([
      api.getLatestSettlement(selectedProjectId).catch(() => null),
      api.getBudgetRules(selectedProjectId),
      api.getExpenses(selectedProjectId),
      api.getProjectFiles(selectedProjectId, "settlement-template"),
    ]);

    setSettlement(latestSettlement);
    setBudgetRules(rules);
    setExpenses(expenseData);
    setTemplateFiles(files);
    setForm({
      reportTitle: latestSettlement?.reportTitle ?? "최종 정산 보고서",
      reportDate: latestSettlement?.reportDate ?? new Date().toISOString().slice(0, 10),
      preparedBy: latestSettlement?.preparedBy ?? "",
      approvedBy: latestSettlement?.approvedBy ?? "",
      summaryNotes: latestSettlement?.summaryNotes ?? "",
      periodStart: selectedProject?.startDate ?? "",
      periodEnd: selectedProject?.endDate ?? "",
    });
  };

  useEffect(() => {
    if (!selectedProjectId) return;
    void refresh();
  }, [selectedProjectId, selectedProject?.startDate, selectedProject?.endDate]);

  const groupedSummary = budgetRules.reduce<Record<string, number>>((acc, rule) => {
    acc[rule.category] = (acc[rule.category] ?? 0) + rule.allocated;
    return acc;
  }, {});

  const summary = Object.entries(groupedSummary).map(([category, allocated]) => {
    const spent = expenses
      .filter((expense) => expense.category === category)
      .reduce((sum, expense) => sum + expense.amount, 0);
    const variance = allocated - spent;
    const itemExecutionRate = allocated === 0 ? 0 : Number(((spent / allocated) * 100).toFixed(1));
    return { category, allocated, spent, variance, executionRate: itemExecutionRate };
  });

  const totalAllocated = summary.reduce((sum, item) => sum + item.allocated, 0);
  const totalSpent = summary.reduce((sum, item) => sum + item.spent, 0);
  const totalVariance = totalAllocated - totalSpent;
  const executionRate = totalAllocated === 0 ? 0 : Number(((totalSpent / totalAllocated) * 100).toFixed(2));

  const handleGenerate = async () => {
    if (!selectedProjectId) return;
    const nextSettlement = await api.generateSettlement(selectedProjectId);
    setSettlement(nextSettlement);
    setForm((prev) => ({
      ...prev,
      reportTitle: nextSettlement.reportTitle,
      reportDate: nextSettlement.reportDate,
      preparedBy: nextSettlement.preparedBy ?? "",
      approvedBy: nextSettlement.approvedBy ?? "",
      summaryNotes: nextSettlement.summaryNotes ?? "",
    }));
    toast.success("정산 보고서를 생성했습니다.");
  };

  const handleSave = async () => {
    if (!settlement?.id) {
      toast.warning("먼저 정산 보고서를 생성해야 합니다.");
      return;
    }

    const updated = await api.updateSettlement(settlement.id, {
      reportTitle: form.reportTitle,
      reportDate: form.reportDate,
      preparedBy: form.preparedBy,
      approvedBy: form.approvedBy,
      summaryNotes: form.summaryNotes,
      totalAllocated,
      totalSpent,
      totalVariance,
      executionRate,
    });

    setSettlement(updated);
    toast.success("정산 보고서 메타데이터를 저장했습니다.");
  };

  const buildExportPayload = () => ({
    reportTitle: form.reportTitle,
    projectName: selectedProject?.name ?? "",
    reportDate: form.reportDate,
    projectPeriod: `${form.periodStart} ~ ${form.periodEnd}`,
    preparedBy: form.preparedBy,
    approvedBy: form.approvedBy,
    summaryNotes: form.summaryNotes,
    totalAllocated: `₩${totalAllocated.toLocaleString()}`,
    totalSpent: `₩${totalSpent.toLocaleString()}`,
    totalVariance: `${totalVariance >= 0 ? "+" : "-"}₩${Math.abs(totalVariance).toLocaleString()}`,
    executionRate: `${executionRate.toFixed(1)}%`,
    items: summary.map((item) => ({
      category: item.category,
      allocated: `₩${item.allocated.toLocaleString()}`,
      spent: `₩${item.spent.toLocaleString()}`,
      variance: `${item.variance >= 0 ? "+" : "-"}₩${Math.abs(item.variance).toLocaleString()}`,
      executionRate: `${item.executionRate.toFixed(1)}%`,
    })),
  });

  const handleDownloadWord = async () => {
    await api.downloadSettlementWord(buildExportPayload());
    toast.success("Word 보고서를 다운로드했습니다.");
  };

  const handleTemplateUpload = async (file: File) => {
    if (!selectedProjectId) return;
    await api.uploadProjectFile(selectedProjectId, "settlement-template", file);
    setTemplateFiles(await api.getProjectFiles(selectedProjectId, "settlement-template"));
    toast.success(`${file.name} 업로드가 완료되었습니다.`);
  };

  const handleDeleteTemplate = async (path: string) => {
    if (!selectedProjectId) return;
    await api.deleteProjectFile(selectedProjectId, path);
    setTemplateFiles(await api.getProjectFiles(selectedProjectId, "settlement-template"));
    toast.success("정산 양식 파일을 삭제했습니다.");
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">정산 보고서</h1>
          <p className="mt-1 text-sm text-gray-600">최종 정산 보고서를 생성하고 저장한 뒤 Word 형식으로 내보냅니다.</p>
        </div>
        <div className="flex gap-2">
          <Button variant="outline" className="gap-2" onClick={() => void handleSave()}>
            <Save className="h-4 w-4" />
            저장
          </Button>
          <Button className="gap-2" onClick={() => void handleGenerate()}>
            <FileText className="h-4 w-4" />
            보고서 생성
          </Button>
        </div>
      </div>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>정산 양식 파일</CardTitle>
          <Button variant="outline" className="gap-2" onClick={() => document.getElementById("settlement-template-upload")?.click()}>
            <Upload className="h-4 w-4" />
            업로드
          </Button>
          <input
            id="settlement-template-upload"
            type="file"
            accept=".pdf,.doc,.docx,.hwp,.hwpx,.xls,.xlsx"
            className="hidden"
            onChange={(event) => {
              const file = event.target.files?.[0];
              if (file) {
                void handleTemplateUpload(file);
              }
            }}
          />
        </CardHeader>
        <CardContent className="space-y-3">
          {templateFiles.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500">
              업로드된 정산 양식 파일이 없습니다.
            </div>
          ) : (
            templateFiles.map((file) => (
              <div key={file.relativePath} className="flex items-center justify-between rounded-lg border border-gray-200 p-4">
                <div>
                  <div className="font-medium text-gray-900">{file.originalFilename}</div>
                  <div className="text-xs text-gray-500">{file.uploadedAt}</div>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" size="sm" onClick={() => void api.downloadProjectFile(selectedProjectId!, file.relativePath, file.originalFilename)}>
                    <Download className="h-4 w-4" />
                  </Button>
                  <ConfirmActionButton
                    title="정산 양식 파일 삭제"
                    description="삭제한 파일은 정산 양식 목록에서 제거됩니다."
                    actionLabel="삭제"
                    onConfirm={() => void handleDeleteTemplate(file.relativePath)}
                    trigger={
                      <Button variant="outline" size="sm">
                        <Trash2 className="h-4 w-4 text-red-600" />
                      </Button>
                    }
                  />
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>보고서 정보</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="settlement-title">보고서 제목</Label>
              <Input id="settlement-title" value={form.reportTitle} onChange={(event) => setForm((prev) => ({ ...prev, reportTitle: event.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="settlement-date">보고일</Label>
              <Input id="settlement-date" type="date" value={form.reportDate} onChange={(event) => setForm((prev) => ({ ...prev, reportDate: event.target.value }))} />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="settlement-prepared-by">작성자</Label>
              <Input id="settlement-prepared-by" value={form.preparedBy} onChange={(event) => setForm((prev) => ({ ...prev, preparedBy: event.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="settlement-approved-by">확인자</Label>
              <Input id="settlement-approved-by" value={form.approvedBy} onChange={(event) => setForm((prev) => ({ ...prev, approvedBy: event.target.value }))} />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="settlement-period-start">사업 시작일</Label>
              <Input id="settlement-period-start" type="date" value={form.periodStart} onChange={(event) => setForm((prev) => ({ ...prev, periodStart: event.target.value }))} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="settlement-period-end">사업 종료일</Label>
              <Input id="settlement-period-end" type="date" value={form.periodEnd} onChange={(event) => setForm((prev) => ({ ...prev, periodEnd: event.target.value }))} />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="settlement-notes">요약 메모</Label>
            <Textarea
              id="settlement-notes"
              rows={4}
              placeholder="정산 검토 결과, 특이사항, 후속 조치 메모를 입력합니다."
              value={form.summaryNotes}
              onChange={(event) => setForm((prev) => ({ ...prev, summaryNotes: event.target.value }))}
            />
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>보고서 미리보기</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
            <div className="border-b border-gray-200 pb-4">
              <h2 className="text-xl font-semibold text-gray-900">{form.reportTitle || "최종 정산 보고서"}</h2>
              <p className="mt-2 text-sm text-gray-600">{selectedProject?.name ?? "선택된 프로젝트 없음"}</p>
              <p className="mt-1 text-sm text-gray-500">보고일 {form.reportDate || "-"}</p>
              <p className="mt-1 text-sm text-gray-500">사업 기간 {form.periodStart || "-"} ~ {form.periodEnd || "-"}</p>
            </div>

            <div className="mt-6 space-y-4 text-sm text-gray-700">
              <div>
                <div className="font-medium text-gray-900">요약</div>
                <p className="mt-2 leading-6">
                  본 정산 보고서는 프로젝트 예산 배정액과 실제 지출액을 비교하여 집행 현황을 정리한 결과입니다.
                  총 배정액은 <span className="font-semibold">₩{totalAllocated.toLocaleString()}</span>이고,
                  총 지출액은 <span className="font-semibold">₩{totalSpent.toLocaleString()}</span>입니다.
                  현재 차액은{" "}
                  <span className={`font-semibold ${totalVariance >= 0 ? "text-green-700" : "text-red-700"}`}>
                    {totalVariance >= 0 ? "+" : "-"}₩{Math.abs(totalVariance).toLocaleString()}
                  </span>
                  이고, 전체 집행률은 <span className="font-semibold">{executionRate.toFixed(1)}%</span>입니다.
                </p>
              </div>

              <div>
                <div className="font-medium text-gray-900">카테고리별 집행 현황</div>
                <div className="mt-3 space-y-3">
                  {summary.length === 0 ? (
                    <div className="rounded-lg border border-dashed border-gray-300 p-4 text-sm text-gray-500">
                      예산 규칙 또는 지출 데이터가 없어 집계가 비어 있습니다.
                    </div>
                  ) : (
                    summary.map((item) => (
                      <div key={item.category} className="rounded-lg border border-gray-200 p-4">
                        <div className="flex items-center justify-between">
                          <div className="font-medium text-gray-900">{item.category}</div>
                          <div className={`font-semibold ${item.variance >= 0 ? "text-green-700" : "text-red-700"}`}>
                            {item.variance >= 0 ? "+" : "-"}₩{Math.abs(item.variance).toLocaleString()}
                          </div>
                        </div>
                        <div className="mt-2 text-xs text-gray-600">
                          배정액 ₩{item.allocated.toLocaleString()} / 지출액 ₩{item.spent.toLocaleString()} / 집행률 {item.executionRate.toFixed(1)}%
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>

              <div>
                <div className="font-medium text-gray-900">검토 및 특이사항</div>
                <p className="mt-2 leading-6 text-gray-700">
                  {form.summaryNotes || "입력된 검토 및 특이사항이 없습니다. 저장 전에 정산 요약 메모를 입력하는 것이 좋습니다."}
                </p>
              </div>

              <div className="grid grid-cols-1 gap-4 border-t border-gray-200 pt-4 md:grid-cols-2">
                <div>
                  <div className="text-xs text-gray-500">작성자</div>
                  <div className="mt-1 font-medium text-gray-900">{form.preparedBy || "-"}</div>
                </div>
                <div>
                  <div className="text-xs text-gray-500">확인자</div>
                  <div className="mt-1 font-medium text-gray-900">{form.approvedBy || "-"}</div>
                </div>
              </div>
            </div>
          </div>

          <div className="flex items-center justify-between rounded-lg border border-blue-200 bg-blue-50 p-4">
            <div>
              <div className="font-medium text-blue-900">내보내기 정책</div>
              <p className="mt-1 text-sm text-blue-800">
                현재 공식 산출물은 Word(.docx)입니다. HWP는 변환기 준비 전까지 사용자 화면에서 비활성 상태로 유지합니다.
              </p>
            </div>
            <Button variant="outline" className="gap-2" onClick={() => void handleDownloadWord()}>
              <Download className="h-4 w-4" />
              Word 다운로드
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
