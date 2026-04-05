import type { ChangeEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { CalendarDays, Download, RefreshCcw, Upload } from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { Expense, ProjectFile } from "../api/types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Calendar } from "../components/ui/calendar";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "../components/ui/dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import { useProjectContext } from "../context/ProjectContext";

const INSPECTION_TEMPLATE_CATEGORY = "inspection-template";
const EVIDENCE_TEMPLATE_CATEGORY = "evidence-template";

function formatDateLabel(value: string | null | undefined) {
  return value || "-";
}

function toDateInputValue(value: Date | undefined) {
  if (!value) return "";
  const year = value.getFullYear();
  const month = String(value.getMonth() + 1).padStart(2, "0");
  const day = String(value.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function buildInspectionWordPayload(
  projectId: number,
  templateCategory: string,
  projectName: string,
  expense: Expense,
) {
  const amount = expense.amount === null ? "" : String(expense.amount);
  const quantity = expense.quantity === null ? "" : String(expense.quantity);
  const unitPrice = expense.unitPrice === null ? "" : String(expense.unitPrice);

  return {
    projectId,
    templateCategory,
    projectName,
    paymentDate: expense.paymentDate,
    vendor: expense.vendor,
    itemName: expense.itemName,
    category: expense.category,
    subcategory: expense.subcategory,
    paymentMethod: expense.paymentMethod,
    amount,
    notes: expense.notes ?? "",
    fieldValues: {
      title: expense.itemName ?? "",
      inspector: "",
      contractAmount: amount,
    },
    lineItems: [
      {
        itemName: expense.itemName,
        spec: "",
        quantity,
        unit: "",
        unitPrice,
        amount,
      },
    ],
  };
}

function toInspectionEntry(expense: Expense, index: number) {
  return {
    receiptId: String(expense.id),
    title: expense.itemName || null,
    paymentDate: expense.paymentDate || null,
    vendor: expense.vendor || null,
    inspector: null,
    contractAmount: expense.amount === null ? null : String(expense.amount),
    receiptImagePath: null,
    receiptOrder: index,
    itemRows: [
      {
        itemName: expense.itemName,
        spec: null,
        quantity: expense.quantity === null ? null : String(expense.quantity),
        unit: null,
        unitPrice: expense.unitPrice === null ? null : String(expense.unitPrice),
        amount: expense.amount === null ? null : String(expense.amount),
      },
    ],
  };
}

function getExecutionStatusLabel(status: Expense["status"]) {
  return status === "PROCESSED" ? "집행 완료" : "집행 전";
}

export function EvidenceDocuments() {
  const { selectedProject, selectedProjectId, refreshProjects } = useProjectContext();
  const [projectTemplate, setProjectTemplate] = useState<ProjectFile | null>(null);
  const [projectTemplateCategory, setProjectTemplateCategory] = useState<string>(INSPECTION_TEMPLATE_CATEGORY);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [loading, setLoading] = useState(false);
  const [downloadingRowId, setDownloadingRowId] = useState<number | null>(null);
  const [executingRowId, setExecutingRowId] = useState<number | null>(null);
  const [savingAll, setSavingAll] = useState(false);
  const [dateDialogOpen, setDateDialogOpen] = useState(false);
  const [selectedDate, setSelectedDate] = useState<Date | undefined>(undefined);

  const sortedExpenses = useMemo(
    () =>
      [...expenses].sort((left, right) => {
        const leftDate = left.paymentDate ?? "";
        const rightDate = right.paymentDate ?? "";
        return rightDate.localeCompare(leftDate) || right.id - left.id;
      }),
    [expenses],
  );

  const refreshPage = async () => {
    if (!selectedProjectId) {
      setProjectTemplate(null);
      setProjectTemplateCategory(INSPECTION_TEMPLATE_CATEGORY);
      setExpenses([]);
      return;
    }

    const [inspectionFiles, evidenceFiles, loadedExpenses] = await Promise.all([
      api.getProjectFiles(selectedProjectId, INSPECTION_TEMPLATE_CATEGORY),
      api.getProjectFiles(selectedProjectId, EVIDENCE_TEMPLATE_CATEGORY),
      api.getExpenses(selectedProjectId),
    ]);
    const resolvedTemplate = inspectionFiles[0] ?? evidenceFiles[0] ?? null;
    setProjectTemplate(resolvedTemplate);
    setProjectTemplateCategory(resolvedTemplate?.category ?? INSPECTION_TEMPLATE_CATEGORY);
    setExpenses(loadedExpenses);
  };

  useEffect(() => {
    setLoading(true);
    void refreshPage()
      .catch((error) => {
        toast.error(error instanceof Error ? error.message : "증빙 문서 정보를 불러오지 못했습니다.");
      })
      .finally(() => setLoading(false));
  }, [selectedProjectId]);

  const handleTemplateUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file || !selectedProjectId) return;

    setLoading(true);
    try {
      await api.uploadProjectFile(selectedProjectId, INSPECTION_TEMPLATE_CATEGORY, file);
      await refreshPage();
      toast.success("물품검수조서 템플릿을 교체했습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "템플릿 업로드에 실패했습니다.");
    } finally {
      setLoading(false);
      event.target.value = "";
    }
  };

  const handleDownloadOne = async (expense: Expense) => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }
    if (!projectTemplate) {
      toast.warning("먼저 물품검수조서 템플릿을 업로드하세요.");
      return;
    }

    setDownloadingRowId(expense.id);
    try {
      await api.downloadEvidenceWord(
        buildInspectionWordPayload(
          selectedProjectId,
          projectTemplateCategory || INSPECTION_TEMPLATE_CATEGORY,
          selectedProject?.name ?? "",
          expense,
        ),
      );
      toast.success(`${expense.itemName || "선택한 건"} Word 파일을 다운로드했습니다.`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Word 저장에 실패했습니다.");
    } finally {
      setDownloadingRowId(null);
    }
  };

  const handleExecuteExpense = async (expense: Expense, status: Expense["status"]) => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }

    setExecutingRowId(expense.id);
    try {
      await api.updateExpenseStatus(expense.id, status);
      await Promise.all([refreshPage(), refreshProjects()]);
      toast.success(`${expense.itemName || "선택한 건"} 상태를 ${getExecutionStatusLabel(status)}로 반영했습니다.`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "집행 상태 반영에 실패했습니다.");
    } finally {
      setExecutingRowId(null);
    }
  };

  const handleSaveAll = async () => {
    if (!selectedProjectId || !projectTemplate) {
      toast.warning("먼저 물품검수조서 템플릿을 확인하세요.");
      return;
    }
    if (!sortedExpenses.length) {
      toast.warning("저장할 지출 기록이 없습니다.");
      return;
    }

    setSavingAll(true);
    try {
      await api.downloadProjectInspectionReportWord(selectedProjectId, projectTemplate.relativePath, {
        mode: "ALL",
        entries: sortedExpenses.map(toInspectionEntry),
      });
      toast.success("전체 저장 Word 파일을 다운로드했습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "전체 저장에 실패했습니다.");
    } finally {
      setSavingAll(false);
    }
  };

  const handleSaveByDate = async () => {
    if (!selectedProjectId || !projectTemplate) {
      toast.warning("먼저 물품검수조서 템플릿을 확인하세요.");
      return;
    }

    const dateKey = toDateInputValue(selectedDate);
    if (!dateKey) {
      toast.warning("저장할 날짜를 선택하세요.");
      return;
    }

    const dateExpenses = sortedExpenses.filter((expense) => expense.paymentDate === dateKey);
    if (!dateExpenses.length) {
      toast.warning("선택한 날짜에 해당하는 지출 기록이 없습니다.");
      return;
    }

    setSavingAll(true);
    try {
      await api.downloadProjectInspectionReportWord(selectedProjectId, projectTemplate.relativePath, {
        mode: "BY_DATE",
        entries: dateExpenses.map(toInspectionEntry),
      });
      setDateDialogOpen(false);
      toast.success(`${dateKey} 날짜별 Word 파일을 다운로드했습니다.`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "날짜별 저장에 실패했습니다.");
    } finally {
      setSavingAll(false);
    }
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-900">증빙 문서</h1>
        <p className="mt-1 text-sm text-gray-600">
          물품검수조서 프로젝트 템플릿을 기준으로 지출 기록 로그를 검토하고 건별, 날짜별, 전체 Word 저장을 진행합니다.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>프로젝트 템플릿</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          {projectTemplate ? (
            <>
              <div className="rounded-lg border border-gray-200 p-4">
                <div className="flex items-start justify-between gap-4">
                  <div className="flex items-start gap-3">
                    <div className="rounded-lg bg-blue-100 p-2">
                      <Upload className="h-5 w-5 text-blue-600" />
                    </div>
                    <div>
                      <p className="text-sm font-medium text-gray-900">{projectTemplate.originalFilename}</p>
                      <p className="mt-1 text-xs text-gray-600">{(projectTemplate.size / 1024).toFixed(2)} KB</p>
                      <div className="mt-2 flex items-center gap-2">
                        <Badge variant="outline" className="border-green-200 bg-green-50 text-green-700">
                          연결 완료
                        </Badge>
                        {projectTemplateCategory !== INSPECTION_TEMPLATE_CATEGORY ? (
                          <Badge variant="outline" className="border-amber-200 bg-amber-50 text-amber-700">
                            기존 프로젝트 템플릿 사용 중
                          </Badge>
                        ) : null}
                        <span className="text-xs text-gray-500">{projectTemplate.uploadedAt}</span>
                      </div>
                    </div>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => document.getElementById("inspection-template-upload")?.click()}
                      disabled={!selectedProjectId || loading}
                    >
                      {loading ? "업로드 중..." : "다시 업로드"}
                    </Button>
                    <Button variant="ghost" size="sm" onClick={() => void refreshPage()} disabled={loading}>
                      <RefreshCcw className="mr-2 h-4 w-4" />
                      새로고침
                    </Button>
                  </div>
                </div>
              </div>
              <input
                id="inspection-template-upload"
                type="file"
                accept=".doc,.docx,.hwp,.hwpx"
                className="hidden"
                onChange={(event) => void handleTemplateUpload(event)}
              />
            </>
          ) : (
            <div className="rounded-lg border-2 border-dashed border-gray-300 bg-gray-50 p-6">
              <div className="flex flex-col items-center gap-3 text-center">
                <Upload className="h-8 w-8 text-blue-600" />
                <div>
                  <p className="text-sm font-medium text-gray-900">프로젝트 템플릿을 업로드하세요</p>
                  <p className="mt-1 text-xs text-gray-600">기존 프로젝트 템플릿이 있으면 자동으로 사용하고, 여기서 교체할 수 있습니다.</p>
                </div>
                <Button
                  onClick={() => document.getElementById("inspection-template-upload")?.click()}
                  disabled={!selectedProjectId || loading}
                >
                  템플릿 업로드
                </Button>
                <input
                  id="inspection-template-upload"
                  type="file"
                  accept=".doc,.docx,.hwp,.hwpx"
                  className="hidden"
                  onChange={(event) => void handleTemplateUpload(event)}
                />
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <CardTitle>지출 기록 로그</CardTitle>
              <p className="mt-1 text-sm text-gray-600">날짜와 건명을 확인하고 집행 상태를 선택하거나 Word 파일로 저장합니다.</p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" onClick={() => setDateDialogOpen(true)} disabled={!projectTemplate || !sortedExpenses.length || savingAll}>
                <CalendarDays className="mr-2 h-4 w-4" />
                날짜별 저장
              </Button>
              <Button onClick={() => void handleSaveAll()} disabled={!projectTemplate || !sortedExpenses.length || savingAll}>
                <Download className="mr-2 h-4 w-4" />
                {savingAll ? "저장 중..." : "전체 저장"}
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>날짜</TableHead>
                <TableHead>건명</TableHead>
                <TableHead className="w-40">집행</TableHead>
                <TableHead className="w-40 text-right">Word 저장</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedExpenses.length ? (
                sortedExpenses.map((expense) => (
                  <TableRow key={expense.id}>
                    <TableCell>{formatDateLabel(expense.paymentDate)}</TableCell>
                    <TableCell>
                      <div className="font-medium text-gray-900">{expense.itemName || "-"}</div>
                      <div className="text-xs text-gray-500">{expense.vendor || "-"}</div>
                    </TableCell>
                    <TableCell>
                      <Select
                        value={expense.status}
                        onValueChange={(value) => void handleExecuteExpense(expense, value as Expense["status"])}
                        disabled={executingRowId === expense.id}
                      >
                        <SelectTrigger>
                          <SelectValue />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="PENDING">집행 전</SelectItem>
                          <SelectItem value="PROCESSED">집행 완료</SelectItem>
                        </SelectContent>
                      </Select>
                    </TableCell>
                    <TableCell className="text-right">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => void handleDownloadOne(expense)}
                        disabled={!projectTemplate || downloadingRowId === expense.id}
                      >
                        <Download className="mr-2 h-4 w-4" />
                        {downloadingRowId === expense.id ? "저장 중..." : "Word 저장"}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              ) : (
                <TableRow>
                  <TableCell colSpan={4} className="text-center text-sm text-gray-500">
                    표시할 지출 기록이 없습니다.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      <Dialog open={dateDialogOpen} onOpenChange={setDateDialogOpen}>
        <DialogContent className="sm:max-w-xl">
          <DialogHeader>
            <DialogTitle>날짜별 저장</DialogTitle>
            <DialogDescription>저장할 결제일자를 선택하면 해당 날짜의 지출 기록만 묶어서 Word 파일을 생성합니다.</DialogDescription>
          </DialogHeader>
          <div className="flex justify-center rounded-lg border border-gray-200">
            <Calendar mode="single" selected={selectedDate} onSelect={setSelectedDate} />
          </div>
          <div className="rounded-lg bg-gray-50 px-4 py-3 text-sm text-gray-600">
            선택한 날짜: <span className="font-medium text-gray-900">{toDateInputValue(selectedDate) || "-"}</span>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDateDialogOpen(false)}>
              취소
            </Button>
            <Button onClick={() => void handleSaveByDate()} disabled={savingAll}>
              {savingAll ? "저장 중..." : "저장 실행"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
