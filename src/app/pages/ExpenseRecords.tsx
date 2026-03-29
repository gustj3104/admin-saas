import type { DragEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import {
  AlertCircle,
  CheckCircle,
  Download,
  Eye,
  FileText,
  Save,
  Upload,
} from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { BudgetRule, Expense, ProjectFile, ReceiptOcrResult } from "../api/types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "../components/ui/select";
import { useProjectContext } from "../context/ProjectContext";

type ExpenseFormState = {
  paymentDate: string;
  vendor: string;
  itemName: string;
  quantity: string;
  unitPrice: string;
  amount: string;
  category: string;
  subcategory: string;
  paymentMethod: string;
  notes: string;
};

const EMPTY_FORM: ExpenseFormState = {
  paymentDate: "",
  vendor: "",
  itemName: "",
  quantity: "1",
  unitPrice: "",
  amount: "",
  category: "",
  subcategory: "",
  paymentMethod: "",
  notes: "",
};

function toInputNumber(value: number | null): string {
  return value === null ? "" : String(value);
}

function buildCategoryOptions(rules: BudgetRule[]) {
  const categoryMap = new Map<string, string[]>();

  for (const rule of rules) {
    const category = rule.category?.trim();
    const subcategory = rule.subcategory?.trim();
    if (!category) continue;

    const current = categoryMap.get(category) ?? [];
    if (subcategory && !current.includes(subcategory)) {
      current.push(subcategory);
    }
    categoryMap.set(category, current);
  }

  return Array.from(categoryMap.entries()).map(([category, subcategories]) => ({
    category,
    subcategories,
  }));
}

function formatCurrency(value: string) {
  const numeric = Number(value || 0);
  return numeric.toLocaleString();
}

export function ExpenseRecords() {
  const { selectedProjectId, selectedProject } = useProjectContext();
  const [showPreview, setShowPreview] = useState(false);
  const [receiptFiles, setReceiptFiles] = useState<ProjectFile[]>([]);
  const [ocrResult, setOcrResult] = useState<ReceiptOcrResult | null>(null);
  const [budgetRules, setBudgetRules] = useState<BudgetRule[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [receiptPreviewUrl, setReceiptPreviewUrl] = useState<string | null>(null);
  const [dragActive, setDragActive] = useState(false);
  const [form, setForm] = useState<ExpenseFormState>(EMPTY_FORM);

  const categoryOptions = useMemo(() => buildCategoryOptions(budgetRules), [budgetRules]);
  const subcategoryOptions = useMemo(
    () => categoryOptions.find((option) => option.category === form.category)?.subcategories ?? [],
    [categoryOptions, form.category],
  );

  const latestReceipt = receiptFiles[0] ?? null;
  const confidenceLabel = ocrResult?.confidence ? `${Math.round(ocrResult.confidence * 100)}%` : "N/A";

  const refresh = async () => {
    if (!selectedProjectId) return;

    const [expenseData, fileData, ruleData] = await Promise.all([
      api.getExpenses(selectedProjectId),
      api.getProjectFiles(selectedProjectId, "receipt"),
      api.getBudgetRules(selectedProjectId),
    ]);

    setExpenses(expenseData);
    setReceiptFiles(fileData);
    setBudgetRules(ruleData);
  };

  useEffect(() => {
    if (!selectedProjectId) return;
    void refresh();
  }, [selectedProjectId]);

  useEffect(() => {
    if (form.category && !subcategoryOptions.includes(form.subcategory)) {
      setForm((prev) => ({
        ...prev,
        subcategory: subcategoryOptions[0] ?? "",
      }));
    }
  }, [form.category, form.subcategory, subcategoryOptions]);

  useEffect(() => {
    return () => {
      if (receiptPreviewUrl) {
        URL.revokeObjectURL(receiptPreviewUrl);
      }
    };
  }, [receiptPreviewUrl]);

  const updateForm = <K extends keyof ExpenseFormState>(key: K, value: ExpenseFormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const applyOcrResult = (result: ReceiptOcrResult) => {
    const detectedCategory = result.fields.category;
    const matchedCategory =
      detectedCategory && categoryOptions.some((option) => option.category === detectedCategory)
        ? detectedCategory
        : "";

    const matchedSubcategory =
      matchedCategory &&
      result.fields.subcategory &&
      categoryOptions
        .find((option) => option.category === matchedCategory)
        ?.subcategories.includes(result.fields.subcategory)
        ? result.fields.subcategory
        : "";

    setForm((prev) => ({
      ...prev,
      paymentDate: result.fields.paymentDate ?? prev.paymentDate,
      vendor: result.fields.vendor ?? prev.vendor,
      itemName: result.fields.itemName ?? prev.itemName,
      quantity: result.fields.quantity !== null ? toInputNumber(result.fields.quantity) : prev.quantity,
      unitPrice: result.fields.unitPrice !== null ? toInputNumber(result.fields.unitPrice) : prev.unitPrice,
      amount: result.fields.amount !== null ? toInputNumber(result.fields.amount) : prev.amount,
      category: matchedCategory || prev.category,
      subcategory: matchedSubcategory || prev.subcategory,
      paymentMethod: result.fields.paymentMethod ?? prev.paymentMethod,
    }));
  };

  const handleReceiptUpload = async (file: File) => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }

    setUploadingReceipt(true);
    try {
      if (receiptPreviewUrl) {
        URL.revokeObjectURL(receiptPreviewUrl);
      }

      setReceiptPreviewUrl(file.type.startsWith("image/") ? URL.createObjectURL(file) : null);
      const result = await api.uploadReceiptForOcr(selectedProjectId, file);
      setOcrResult(result);
      applyOcrResult(result);
      await refresh();

      if (result.status === "COMPLETED") {
        toast.success("영수증 OCR 추출이 완료되었습니다.");
      } else {
        toast.warning(result.warnings[0] ?? "영수증 업로드는 완료됐지만 OCR 결과가 없습니다.");
      }
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "영수증 업로드에 실패했습니다.");
    } finally {
      setUploadingReceipt(false);
    }
  };

  const handleDropReceipt = (event: DragEvent<HTMLDivElement>) => {
    event.preventDefault();
    setDragActive(false);
    const file = event.dataTransfer.files?.[0];
    if (file) {
      void handleReceiptUpload(file);
    }
  };

  const handleSaveExpense = async () => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }

    if (!form.paymentDate || !form.vendor || !form.itemName || !form.category || !form.subcategory || !form.amount) {
      toast.warning("지불 일자, 판매자명, 품목명, 카테고리, 하위 카테고리, 총 금액을 입력하세요.");
      return;
    }

    await api.createExpense({
      projectId: selectedProjectId,
      expenseCode: `EXP-${Date.now()}`,
      paymentDate: form.paymentDate,
      vendor: form.vendor,
      itemName: form.itemName,
      quantity: form.quantity ? Number(form.quantity) : null,
      unitPrice: form.unitPrice ? Number(form.unitPrice) : null,
      category: form.category,
      subcategory: form.subcategory,
      amount: Number(form.amount),
      paymentMethod: form.paymentMethod || "card",
      notes: form.notes,
      status: "PENDING",
    });

    await refresh();
    toast.success("지출 정보를 저장했습니다.");
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">지출 기록</h1>
          <p className="mt-1 text-sm text-gray-600">지출 입력, 영수증 업로드, 문서 미리보기</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="space-y-6">
          <Card>
            <CardHeader>
              <CardTitle>영수증 업로드</CardTitle>
            </CardHeader>
            <CardContent className="space-y-4">
              <div
                className={`cursor-pointer rounded-lg border-2 border-dashed p-12 text-center transition-colors ${
                  dragActive ? "border-blue-500 bg-blue-50" : "border-gray-300 bg-gray-50 hover:border-blue-400"
                }`}
                onClick={() => document.getElementById("receipt-upload")?.click()}
                onDragEnter={(event) => {
                  event.preventDefault();
                  setDragActive(true);
                }}
                onDragOver={(event) => {
                  event.preventDefault();
                  if (!dragActive) setDragActive(true);
                }}
                onDragLeave={(event) => {
                  event.preventDefault();
                  setDragActive(false);
                }}
                onDrop={handleDropReceipt}
              >
                <Upload className="mx-auto mb-3 h-12 w-12 text-gray-400" />
                <p className="mb-1 text-sm font-medium text-gray-900">클릭하여 업로드하거나 드래그 앤 드롭</p>
                <p className="text-xs text-gray-500">PNG, JPG, PDF 최대 10MB</p>
              </div>

              <Button
                className="w-full gap-2"
                onClick={() => document.getElementById("receipt-upload")?.click()}
                disabled={uploadingReceipt}
              >
                <Upload className="h-4 w-4" />
                {uploadingReceipt ? "업로드 중..." : "파일 선택"}
              </Button>

              <input
                id="receipt-upload"
                type="file"
                accept=".png,.jpg,.jpeg,.bmp,.tif,.tiff,.pdf"
                className="hidden"
                onChange={(event) => {
                  const file = event.target.files?.[0];
                  if (file) {
                    void handleReceiptUpload(file);
                  }
                  event.target.value = "";
                }}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>영수증 미리보기</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="flex aspect-[3/4] items-center justify-center rounded-lg bg-gray-100">
                {receiptPreviewUrl ? (
                  <img
                    src={receiptPreviewUrl}
                    alt="업로드한 영수증 미리보기"
                    className="h-full w-full rounded-lg object-contain"
                  />
                ) : latestReceipt ? (
                  <div className="text-center text-gray-700">
                    <FileText className="mx-auto mb-2 h-16 w-16 text-blue-500" />
                    <p className="text-sm font-medium">{latestReceipt.originalFilename}</p>
                    <p className="mt-1 text-xs text-gray-500">{(latestReceipt.size / 1024).toFixed(1)} KB</p>
                  </div>
                ) : (
                  <div className="text-center text-gray-500">
                    <FileText className="mx-auto mb-2 h-16 w-16 text-gray-400" />
                    <p className="text-sm">영수증이 업로드되지 않았습니다</p>
                  </div>
                )}
              </div>

              <div className="mt-4 rounded-lg border border-blue-200 bg-blue-50 p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-blue-900">파싱 신뢰도</span>
                  <Badge variant="outline" className="bg-white">
                    {confidenceLabel}
                  </Badge>
                </div>
              </div>

              {ocrResult?.warnings.length ? (
                <div className="mt-3 rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800">
                  {ocrResult.warnings[0]}
                </div>
              ) : null}
            </CardContent>
          </Card>
        </div>

        <Card>
          <CardHeader>
            <CardTitle>지출 정보</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="payment-date">지불 일자</Label>
              <Input
                id="payment-date"
                type="date"
                value={form.paymentDate}
                onChange={(event) => updateForm("paymentDate", event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="vendor">판매자명</Label>
              <Input
                id="vendor"
                placeholder="판매자명 입력"
                value={form.vendor}
                onChange={(event) => updateForm("vendor", event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="item-name">품목명 / 설명</Label>
              <Input
                id="item-name"
                placeholder="품목 설명 입력"
                value={form.itemName}
                onChange={(event) => updateForm("itemName", event.target.value)}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="quantity">수량</Label>
                <Input
                  id="quantity"
                  type="number"
                  placeholder="1"
                  value={form.quantity}
                  onChange={(event) => updateForm("quantity", event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="unit-price">단가 (원)</Label>
                <Input
                  id="unit-price"
                  type="number"
                  placeholder="0"
                  value={form.unitPrice}
                  onChange={(event) => updateForm("unitPrice", event.target.value)}
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="amount">총 금액 (원)</Label>
              <Input
                id="amount"
                type="number"
                placeholder="0"
                className="font-semibold"
                value={form.amount}
                onChange={(event) => updateForm("amount", event.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="category">예산 카테고리</Label>
              <Select
                value={form.category}
                onValueChange={(value) => {
                  setForm((prev) => ({
                    ...prev,
                    category: value,
                    subcategory: "",
                  }));
                }}
              >
                <SelectTrigger id="category">
                  <SelectValue placeholder="카테고리 선택" />
                </SelectTrigger>
                <SelectContent>
                  {categoryOptions.length === 0 ? (
                    <SelectItem value="__empty__" disabled>
                      등록된 카테고리가 없습니다
                    </SelectItem>
                  ) : (
                    categoryOptions.map((option) => (
                      <SelectItem key={option.category} value={option.category}>
                        {option.category}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="subcategory">하위 카테고리</Label>
              <Select value={form.subcategory} onValueChange={(value) => updateForm("subcategory", value)}>
                <SelectTrigger id="subcategory">
                  <SelectValue placeholder="하위 카테고리 선택" />
                </SelectTrigger>
                <SelectContent>
                  {subcategoryOptions.length === 0 ? (
                    <SelectItem value="__empty__" disabled>
                      먼저 카테고리를 선택하세요
                    </SelectItem>
                  ) : (
                    subcategoryOptions.map((subcategory) => (
                      <SelectItem key={subcategory} value={subcategory}>
                        {subcategory}
                      </SelectItem>
                    ))
                  )}
                </SelectContent>
              </Select>
            </div>

            <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3">
              <div className="flex items-start gap-2">
                <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-yellow-600" />
                <div className="text-sm text-yellow-800">
                  <p className="font-medium">OCR 확인 필요</p>
                  <p className="mt-1">자동 추출값은 초안입니다. 저장 전에 영수증 원본과 맞는지 확인하세요.</p>
                </div>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="payment-method">지불 방법</Label>
              <Select value={form.paymentMethod} onValueChange={(value) => updateForm("paymentMethod", value)}>
                <SelectTrigger id="payment-method">
                  <SelectValue placeholder="지불 방법 선택" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="card">신용카드</SelectItem>
                  <SelectItem value="bank">계좌 이체</SelectItem>
                  <SelectItem value="cash">현금</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="notes">메모 (선택)</Label>
              <Input
                id="notes"
                placeholder="추가 메모"
                value={form.notes}
                onChange={(event) => updateForm("notes", event.target.value)}
              />
            </div>

            <div className="flex gap-2 pt-4">
              <Button variant="outline" className="flex-1 gap-2" onClick={() => void handleSaveExpense()}>
                <Save className="h-4 w-4" />
                지출 저장
              </Button>
              <Button className="flex-1 gap-2" onClick={() => setShowPreview(true)}>
                <Eye className="h-4 w-4" />
                문서 미리보기
              </Button>
            </div>
          </CardContent>
        </Card>
      </div>

      {showPreview && (
        <Card className="border-blue-300 bg-blue-50/30">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2">
                <FileText className="h-5 w-5" />
                증빙 문서 미리보기
              </CardTitle>
              <Button variant="ghost" size="sm" onClick={() => setShowPreview(false)}>
                미리보기 닫기
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <div className="rounded-lg border-2 border-gray-200 bg-white p-8 shadow-sm">
              <div className="space-y-6">
                <div className="border-b border-gray-200 pb-4 text-center">
                  <h2 className="text-lg font-semibold">지출 증빙 문서</h2>
                  <p className="mt-1 text-sm text-gray-600">{selectedProject?.name ?? "프로젝트 미선택"}</p>
                </div>

                <div className="space-y-4 text-sm">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <span className="text-gray-600">일자:</span>
                      <span className="ml-2 font-medium">{form.paymentDate || "-"}</span>
                    </div>
                    <div>
                      <span className="text-gray-600">문서 번호:</span>
                      <span className="ml-2 font-medium">EV-{Date.now().toString().slice(-6)}</span>
                    </div>
                  </div>

                  <div className="border-t border-gray-200 pt-4">
                    <table className="w-full text-sm">
                      <tbody>
                        <tr className="border-b border-gray-200">
                          <td className="w-1/3 py-2 text-gray-600">판매자</td>
                          <td className="py-2 font-medium">{form.vendor || "-"}</td>
                        </tr>
                        <tr className="border-b border-gray-200">
                          <td className="py-2 text-gray-600">품목</td>
                          <td className="py-2 font-medium">{form.itemName || "-"}</td>
                        </tr>
                        <tr className="border-b border-gray-200">
                          <td className="py-2 text-gray-600">수량 / 단가</td>
                          <td className="py-2 font-medium">
                            {form.quantity || "-"} / {form.unitPrice ? `${formatCurrency(form.unitPrice)}원` : "-"}
                          </td>
                        </tr>
                        <tr className="border-b border-gray-200">
                          <td className="py-2 text-gray-600">카테고리</td>
                          <td className="py-2 font-medium">
                            {form.category || "-"}
                            {form.subcategory ? ` > ${form.subcategory}` : ""}
                          </td>
                        </tr>
                        <tr className="border-b border-gray-200">
                          <td className="py-2 text-gray-600">금액</td>
                          <td className="py-2 text-lg font-medium">{formatCurrency(form.amount)}원</td>
                        </tr>
                      </tbody>
                    </table>
                  </div>

                  <div className="border-t border-gray-200 pt-4">
                    <p className="mb-2 text-gray-600">목적:</p>
                    <p className="font-medium">{form.notes || "지출 목적을 입력하세요."}</p>
                  </div>

                  <div className="border-t border-gray-200 pt-4">
                    <div className="flex justify-between">
                      <div>
                        <p className="text-xs text-gray-600">요청자</p>
                        <p className="mt-1 font-medium">__________</p>
                      </div>
                      <div>
                        <p className="text-xs text-gray-600">승인자</p>
                        <p className="mt-1 font-medium">__________</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <div className="mt-4 flex gap-2">
              <Button variant="outline" className="flex-1 gap-2">
                <Save className="h-4 w-4" />
                임시 저장
              </Button>
              <Button variant="outline" className="flex-1 gap-2">
                <Download className="h-4 w-4" />
                Word 다운로드
              </Button>
              <Button className="flex-1 gap-2" onClick={() => void handleSaveExpense()}>
                <CheckCircle className="h-4 w-4" />
                확인 및 저장
              </Button>
            </div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>최근 지출 기록</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="space-y-3">
            {expenses.length === 0 ? (
              <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500">
                등록된 지출 기록이 없습니다.
              </div>
            ) : (
              expenses.map((expense) => (
                <div
                  key={expense.id}
                  className="flex items-center justify-between rounded-lg border border-gray-200 p-4 transition-colors hover:bg-gray-50"
                >
                  <div className="flex-1">
                    <div className="text-sm font-medium text-gray-900">{expense.itemName}</div>
                    <div className="mt-1 text-xs text-gray-600">
                      {expense.vendor} · {expense.paymentDate}
                    </div>
                    <div className="mt-1 text-xs text-gray-500">
                      {expense.category}
                      {expense.subcategory ? ` > ${expense.subcategory}` : ""}
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-sm font-medium">{expense.amount.toLocaleString()}원</div>
                    <Badge
                      variant="outline"
                      className={`mt-1 ${
                        expense.status === "PROCESSED"
                          ? "border-green-200 bg-green-50 text-green-700"
                          : "border-yellow-200 bg-yellow-50 text-yellow-700"
                      }`}
                    >
                      {expense.status === "PROCESSED" ? "처리됨" : "대기중"}
                    </Badge>
                  </div>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
