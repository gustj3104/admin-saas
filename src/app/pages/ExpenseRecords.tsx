
import type { DragEvent } from "react";
import { useEffect, useMemo, useState } from "react";
import { AlertCircle, CheckCircle, Download, Eye, FileText, Plus, Save, Trash2, Upload } from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { BudgetRule, EvidencePreview, ProjectFile, ReceiptOcrLineItem, ReceiptOcrResult, TemplateFieldAnalysis } from "../api/types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
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

type TemplateFieldDraft = {
  key: string;
  value: string;
  source: string;
  edited: boolean;
};

type EditableLineItem = {
  id: string;
  itemName: string;
  spec: string;
  quantity: string;
  unit: string;
  unitPrice: string;
  amount: string;
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

const PLACEHOLDER_ALIASES: Record<string, string[]> = {
  projectName: ["projectname", "project", "프로젝트명", "사업명"],
  paymentDate: ["paymentdate", "date", "transactiondate", "지출일자", "결제일자", "작성일"],
  vendor: ["vendor", "supplier", "store", "company", "거래처", "납품처", "판매처"],
  itemName: ["itemname", "item", "product", "description", "품명", "항목", "내역"],
  quantity: ["quantity", "qty", "count", "수량", "개수"],
  unitPrice: ["unitprice", "price", "단가", "가격"],
  amount: ["amount", "total", "sum", "금액", "총액", "합계"],
  category: ["category", "카테고리", "예산항목"],
  subcategory: ["subcategory", "detailcategory", "세부항목", "하위카테고리"],
  paymentMethod: ["paymentmethod", "method", "지불방법", "결제수단"],
  notes: ["notes", "note", "remark", "memo", "비고", "메모"],
};

const FIELD_LABELS: Record<string, string> = {
  projectName: "프로젝트명",
  paymentDate: "지출일자",
  date: "지출일자",
  vendor: "거래처명",
  itemName: "항목명",
  quantity: "수량",
  unitPrice: "단가",
  amount: "금액",
  category: "예산 카테고리",
  subcategory: "세부 항목",
  paymentMethod: "지불 방법",
  notes: "메모",
  title: "건명",
  inspector: "검수자",
  contractAmount: "계약금액",
  spec: "규격",
  unit: "단위",
};

function toInputNumber(value: number | null): string {
  return value === null ? "" : String(value);
}

function createEmptyLineItem(): EditableLineItem {
  return {
    id: crypto.randomUUID(),
    itemName: "",
    spec: "",
    quantity: "",
    unit: "",
    unitPrice: "",
    amount: "",
  };
}

function toEditableLineItem(lineItem: ReceiptOcrLineItem): EditableLineItem {
  return {
    id: crypto.randomUUID(),
    itemName: lineItem.itemName ?? "",
    spec: lineItem.spec ?? "",
    quantity: lineItem.quantity !== null ? String(lineItem.quantity) : "",
    unit: lineItem.unit ?? "",
    unitPrice: lineItem.unitPrice !== null ? String(lineItem.unitPrice) : "",
    amount: lineItem.amount !== null ? String(lineItem.amount) : "",
  };
}

function toNumberOrNull(value: string) {
  if (!value.trim()) {
    return null;
  }
  const parsed = Number(value.replaceAll(",", ""));
  return Number.isFinite(parsed) ? parsed : null;
}

function syncFormFromLineItems(items: EditableLineItem[], previousForm: ExpenseFormState): ExpenseFormState {
  const firstItem = items[0];
  const totalAmount = items.reduce((sum, item) => sum + (toNumberOrNull(item.amount) ?? 0), 0);
  if (!firstItem) {
    return {
      ...previousForm,
      itemName: "",
      quantity: "1",
      unitPrice: "",
      amount: previousForm.amount,
    };
  }

  return {
    ...previousForm,
    itemName: firstItem.itemName || previousForm.itemName,
    quantity: firstItem.quantity || previousForm.quantity,
    unitPrice: firstItem.unitPrice || previousForm.unitPrice,
    amount: totalAmount > 0 ? String(totalAmount) : firstItem.amount || previousForm.amount,
  };
}

function buildCategoryOptions(rules: BudgetRule[]) {
  const categoryMap = new Map<string, string[]>();
  for (const rule of rules) {
    const category = rule.category?.trim();
    const subcategory = rule.subcategory?.trim();
    if (!category) continue;
    const current = categoryMap.get(category) ?? [];
    if (subcategory && !current.includes(subcategory)) current.push(subcategory);
    categoryMap.set(category, current);
  }
  return Array.from(categoryMap.entries()).map(([category, subcategories]) => ({ category, subcategories }));
}

function normalizePlaceholderKey(key: string) {
  return key.replace(/[{}]/g, "").replace(/\s+/g, "").toLowerCase();
}

function getSuggestionForPlaceholder(key: string, form: ExpenseFormState, projectName: string) {
  const normalizedKey = normalizePlaceholderKey(key);
  const candidates = [
    { aliases: PLACEHOLDER_ALIASES.projectName, value: projectName, source: "프로젝트명" },
    { aliases: PLACEHOLDER_ALIASES.paymentDate, value: form.paymentDate, source: "OCR/지출일자" },
    { aliases: PLACEHOLDER_ALIASES.vendor, value: form.vendor, source: "OCR/거래처" },
    { aliases: PLACEHOLDER_ALIASES.itemName, value: form.itemName, source: "OCR/항목명" },
    { aliases: PLACEHOLDER_ALIASES.quantity, value: form.quantity, source: "OCR/수량" },
    { aliases: PLACEHOLDER_ALIASES.unitPrice, value: form.unitPrice, source: "OCR/단가" },
    { aliases: PLACEHOLDER_ALIASES.amount, value: form.amount, source: "OCR/금액" },
    { aliases: PLACEHOLDER_ALIASES.category, value: form.category, source: "예산 카테고리" },
    { aliases: PLACEHOLDER_ALIASES.subcategory, value: form.subcategory, source: "예산 세부 항목" },
    { aliases: PLACEHOLDER_ALIASES.paymentMethod, value: form.paymentMethod, source: "OCR/지불 방법" },
    { aliases: PLACEHOLDER_ALIASES.notes, value: form.notes, source: "메모" },
  ];
  for (const candidate of candidates) {
    if (candidate.aliases.some((alias) => normalizedKey.includes(alias.toLowerCase()))) {
      return { value: candidate.value, source: candidate.source };
    }
  }
  return { value: "", source: "수동 입력" };
}

function syncTemplateFieldDrafts(previousDrafts: TemplateFieldDraft[], placeholders: string[], form: ExpenseFormState, projectName: string) {
  const existingMap = new Map(previousDrafts.map((draft) => [draft.key, draft]));
  return placeholders.map((key) => {
    const existing = existingMap.get(key);
    const suggestion = getSuggestionForPlaceholder(key, form, projectName);
    if (!existing) return { key, value: suggestion.value, source: suggestion.source, edited: false };
    if (existing.edited) return existing;
    return { ...existing, value: suggestion.value, source: suggestion.source };
  });
}

function buildFieldValues(drafts: TemplateFieldDraft[]) {
  return drafts.reduce<Record<string, string>>((acc, draft) => {
    acc[draft.key] = draft.value;
    return acc;
  }, {});
}

function getFieldLabel(key: string) {
  return FIELD_LABELS[key] ?? key;
}

export function ExpenseRecords() {
  const { selectedProjectId, selectedProject } = useProjectContext();
  const [showPreview, setShowPreview] = useState(false);
  const [receiptFiles, setReceiptFiles] = useState<ProjectFile[]>([]);
  const [templateFiles, setTemplateFiles] = useState<ProjectFile[]>([]);
  const [ocrResult, setOcrResult] = useState<ReceiptOcrResult | null>(null);
  const [budgetRules, setBudgetRules] = useState<BudgetRule[]>([]);
  const [templateAnalysis, setTemplateAnalysis] = useState<TemplateFieldAnalysis | null>(null);
  const [templateFieldDrafts, setTemplateFieldDrafts] = useState<TemplateFieldDraft[]>([]);
  const [previewData, setPreviewData] = useState<EvidencePreview | null>(null);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [analyzingTemplate, setAnalyzingTemplate] = useState(false);
  const [downloadingWord, setDownloadingWord] = useState(false);
  const [downloadingHwp, setDownloadingHwp] = useState(false);
  const [receiptPreviewUrl, setReceiptPreviewUrl] = useState<string | null>(null);
  const [dragActive, setDragActive] = useState(false);
  const [form, setForm] = useState<ExpenseFormState>(EMPTY_FORM);
  const [lineItems, setLineItems] = useState<EditableLineItem[]>([]);

  const categoryOptions = useMemo(() => buildCategoryOptions(budgetRules), [budgetRules]);
  const subcategoryOptions = useMemo(() => categoryOptions.find((option) => option.category === form.category)?.subcategories ?? [], [categoryOptions, form.category]);
  const latestReceipt = receiptFiles[0] ?? null;
  const latestTemplate = templateFiles[0] ?? null;
  const confidenceLabel = ocrResult?.confidence ? `${Math.round(ocrResult.confidence * 100)}%` : "N/A";

  const refresh = async () => {
    if (!selectedProjectId) return;
    const [receiptFileData, templateFileData, ruleData] = await Promise.all([
      api.getProjectFiles(selectedProjectId, "receipt"),
      api.getProjectFiles(selectedProjectId, "evidence-template"),
      api.getBudgetRules(selectedProjectId),
    ]);
    setReceiptFiles(receiptFileData);
    setTemplateFiles(templateFileData);
    setBudgetRules(ruleData);
  };

  useEffect(() => {
    if (!selectedProjectId) return;
    void refresh();
  }, [selectedProjectId]);

  useEffect(() => {
    if (form.category && !subcategoryOptions.includes(form.subcategory)) {
      setForm((prev) => ({ ...prev, subcategory: subcategoryOptions[0] ?? "" }));
    }
  }, [form.category, form.subcategory, subcategoryOptions]);

  useEffect(() => {
    if (!latestTemplate || !selectedProjectId) {
      setTemplateAnalysis(null);
      setTemplateFieldDrafts([]);
      return;
    }
    setAnalyzingTemplate(true);
    void api
      .inspectProjectTemplateFields(selectedProjectId, latestTemplate.relativePath)
      .then((analysis) => {
        setTemplateAnalysis(analysis);
        setTemplateFieldDrafts((prev) => syncTemplateFieldDrafts(prev, analysis.placeholders, form, selectedProject?.name ?? ""));
      })
      .catch((error) => {
        setTemplateAnalysis(null);
        setTemplateFieldDrafts([]);
        toast.error(error instanceof Error ? error.message : "템플릿 필드 분석에 실패했습니다.");
      })
      .finally(() => setAnalyzingTemplate(false));
  }, [latestTemplate, selectedProjectId, selectedProject?.name]);

  useEffect(() => {
    if (!templateAnalysis) return;
    setTemplateFieldDrafts((prev) => syncTemplateFieldDrafts(prev, templateAnalysis.placeholders, form, selectedProject?.name ?? ""));
  }, [form, templateAnalysis, selectedProject?.name]);

  useEffect(() => {
    return () => {
      if (receiptPreviewUrl) URL.revokeObjectURL(receiptPreviewUrl);
    };
  }, [receiptPreviewUrl]);

  const updateForm = <K extends keyof ExpenseFormState>(key: K, value: ExpenseFormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const updateTemplateFieldDraft = (key: string, value: string) => {
    setTemplateFieldDrafts((prev) => prev.map((draft) => (draft.key === key ? { ...draft, value, source: "직접 수정", edited: true } : draft)));
    switch (key) {
      case "paymentDate":
      case "date":
        setForm((prev) => ({ ...prev, paymentDate: value }));
        break;
      case "vendor":
        setForm((prev) => ({ ...prev, vendor: value }));
        break;
      case "itemName":
        setForm((prev) => ({ ...prev, itemName: value }));
        break;
      case "quantity":
        setForm((prev) => ({ ...prev, quantity: value }));
        break;
      case "unitPrice":
        setForm((prev) => ({ ...prev, unitPrice: value }));
        break;
      case "amount":
      case "contractAmount":
        setForm((prev) => ({ ...prev, amount: value }));
        break;
      case "category":
        setForm((prev) => ({ ...prev, category: value }));
        break;
      case "subcategory":
        setForm((prev) => ({ ...prev, subcategory: value }));
        break;
      case "paymentMethod":
        setForm((prev) => ({ ...prev, paymentMethod: value }));
        break;
      case "notes":
        setForm((prev) => ({ ...prev, notes: value }));
        break;
      default:
        break;
    }
  };

  const applyOcrResult = (result: ReceiptOcrResult) => {
    const primaryLineItem = result.fields.lineItems[0] ?? null;
    const detectedCategory = result.fields.category;
    const matchedCategory = detectedCategory && categoryOptions.some((option) => option.category === detectedCategory) ? detectedCategory : "";
    const matchedSubcategory = matchedCategory && result.fields.subcategory && categoryOptions.find((option) => option.category === matchedCategory)?.subcategories.includes(result.fields.subcategory) ? result.fields.subcategory : "";
    setForm((prev) => ({
      ...prev,
      paymentDate: result.fields.paymentDate ?? prev.paymentDate,
      vendor: result.fields.vendor ?? prev.vendor,
      itemName: primaryLineItem?.itemName ?? result.fields.itemName ?? prev.itemName,
      quantity: primaryLineItem?.quantity !== null && primaryLineItem?.quantity !== undefined
        ? toInputNumber(primaryLineItem.quantity)
        : result.fields.quantity !== null ? toInputNumber(result.fields.quantity) : prev.quantity,
      unitPrice: primaryLineItem?.unitPrice !== null && primaryLineItem?.unitPrice !== undefined
        ? toInputNumber(primaryLineItem.unitPrice)
        : result.fields.unitPrice !== null ? toInputNumber(result.fields.unitPrice) : prev.unitPrice,
      amount: result.fields.amount !== null ? toInputNumber(result.fields.amount) : prev.amount,
      category: matchedCategory || prev.category,
      subcategory: matchedSubcategory || prev.subcategory,
      paymentMethod: result.fields.paymentMethod ?? prev.paymentMethod,
    }));
    setLineItems(result.fields.lineItems.length > 0 ? result.fields.lineItems.map(toEditableLineItem) : []);
  };

  const updateLineItems = (updater: (previous: EditableLineItem[]) => EditableLineItem[]) => {
    setLineItems((previous) => {
      const next = updater(previous);
      setForm((currentForm) => syncFormFromLineItems(next, currentForm));
      return next;
    });
  };

  const updateLineItemField = (id: string, key: keyof Omit<EditableLineItem, "id">, value: string) => {
    updateLineItems((previous) => previous.map((item) => {
      if (item.id !== id) {
        return item;
      }

      const nextItem = { ...item, [key]: value };
      if (key === "quantity" || key === "unitPrice") {
        const quantity = toNumberOrNull(key === "quantity" ? value : nextItem.quantity);
        const unitPrice = toNumberOrNull(key === "unitPrice" ? value : nextItem.unitPrice);
        if (quantity !== null && unitPrice !== null) {
          nextItem.amount = String(quantity * unitPrice);
        }
      }
      return nextItem;
    }));
  };

  const addLineItem = () => {
    updateLineItems((previous) => [...previous, createEmptyLineItem()]);
  };

  const removeLineItem = (id: string) => {
    updateLineItems((previous) => {
      const next = previous.filter((item) => item.id !== id);
      return next.length > 0 ? next : [];
    });
  };

  const handleReceiptUpload = async (file: File) => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }
    setUploadingReceipt(true);
    try {
      if (receiptPreviewUrl) URL.revokeObjectURL(receiptPreviewUrl);
      setReceiptPreviewUrl(file.type.startsWith("image/") ? URL.createObjectURL(file) : null);
      const result = await api.uploadReceiptForOcr(selectedProjectId, file);
      setOcrResult(result);
      applyOcrResult(result);
      await refresh();
      if (result.status === "COMPLETED") {
        toast.success(`영수증 OCR 추출이 완료되었습니다.${result.fields.lineItems.length > 0 ? ` 품목 ${result.fields.lineItems.length}건을 인식했습니다.` : ""}`);
      } else {
        toast.warning(result.warnings[0] ?? "영수증은 업로드되었지만 OCR 결과를 확인하지 못했습니다.");
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
    if (file) void handleReceiptUpload(file);
  };

  const handleSaveExpense = async () => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }
    if (!form.paymentDate || !form.vendor || !form.itemName || !form.category || !form.subcategory || !form.amount) {
      toast.warning("지출일자, 거래처, 항목명, 카테고리, 세부 항목, 금액을 입력하세요.");
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
    toast.success("지출 정보가 저장되었습니다. 증빙 문서 로그에서도 바로 확인할 수 있습니다.");
  };

  const buildExportPayload = () => ({
    projectId: selectedProjectId,
    templateCategory: "evidence-template",
    projectName: selectedProject?.name ?? "",
    paymentDate: form.paymentDate,
    vendor: form.vendor,
    itemName: form.itemName,
    category: form.category,
    subcategory: form.subcategory,
    paymentMethod: form.paymentMethod,
    amount: form.amount,
    notes: form.notes,
    fieldValues: buildFieldValues(templateFieldDrafts),
    lineItems: lineItems.map((lineItem) => ({
      itemName: lineItem.itemName,
      spec: lineItem.spec,
      quantity: lineItem.quantity,
      unit: lineItem.unit,
      unitPrice: lineItem.unitPrice,
      amount: lineItem.amount,
    })),
  });

  const handleDownloadWord = async () => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }
    setDownloadingWord(true);
    try {
      await api.downloadEvidenceWord(buildExportPayload());
      toast.success("지출증빙서류 Word 파일을 다운로드했습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Word 다운로드에 실패했습니다.");
    } finally {
      setDownloadingWord(false);
    }
  };

  const handleDownloadHwp = async () => {
    if (!selectedProjectId) {
      toast.warning("먼저 프로젝트를 선택하세요.");
      return;
    }
    setDownloadingHwp(true);
    try {
      await api.downloadEvidenceHwp(buildExportPayload());
      toast.success("지출증빙서류 HWP 파일을 다운로드했습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "HWP 다운로드에 실패했습니다.");
    } finally {
      setDownloadingHwp(false);
    }
  };

  const handlePreview = async () => {
    setShowPreview(true);
    try {
      const preview = await api.previewEvidenceWord(buildExportPayload());
      setPreviewData(preview);
    } catch (error) {
      setPreviewData(null);
      toast.error(error instanceof Error ? error.message : "미리보기를 불러오지 못했습니다.");
    }
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">지출 기록</h1>
          <p className="mt-1 text-sm text-gray-600">영수증 OCR 결과를 템플릿 필드에 자동 반영하고, 수정한 값으로 지출증빙서류를 다운로드할 수 있습니다.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle>영수증 업로드</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <div
                className={`cursor-pointer rounded-lg border-2 border-dashed p-12 text-center transition-colors ${dragActive ? "border-blue-500 bg-blue-50" : "border-gray-300 bg-gray-50 hover:border-blue-400"}`}
                onClick={() => document.getElementById("receipt-upload")?.click()}
                onDragEnter={(event) => { event.preventDefault(); setDragActive(true); }}
                onDragOver={(event) => { event.preventDefault(); if (!dragActive) setDragActive(true); }}
                onDragLeave={(event) => { event.preventDefault(); setDragActive(false); }}
                onDrop={handleDropReceipt}
              >
                <Upload className="mx-auto mb-3 h-12 w-12 text-gray-400" />
                <p className="mb-1 text-sm font-medium text-gray-900">클릭해서 업로드하거나 파일을 끌어오세요</p>
                <p className="text-xs text-gray-500">PNG, JPG, PDF 최대 10MB</p>
              </div>
              <Button className="w-full gap-2" onClick={() => document.getElementById("receipt-upload")?.click()} disabled={uploadingReceipt}>
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
                  if (file) void handleReceiptUpload(file);
                  event.target.value = "";
                }}
              />
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>영수증 미리보기</CardTitle></CardHeader>
            <CardContent>
              <div className="flex aspect-[3/4] items-center justify-center rounded-lg bg-gray-100">
                {receiptPreviewUrl ? (
                  <img src={receiptPreviewUrl} alt="업로드한 영수증 미리보기" className="h-full w-full rounded-lg object-contain" />
                ) : latestReceipt ? (
                  <div className="text-center text-gray-700">
                    <FileText className="mx-auto mb-2 h-16 w-16 text-blue-500" />
                    <p className="text-sm font-medium">{latestReceipt.originalFilename}</p>
                    <p className="mt-1 text-xs text-gray-500">{(latestReceipt.size / 1024).toFixed(1)} KB</p>
                  </div>
                ) : (
                  <div className="text-center text-gray-500">
                    <FileText className="mx-auto mb-2 h-16 w-16 text-gray-400" />
                    <p className="text-sm">업로드된 영수증이 없습니다.</p>
                  </div>
                )}
              </div>
              <div className="mt-4 rounded-lg border border-blue-200 bg-blue-50 p-3">
                <div className="flex items-center justify-between">
                  <span className="text-sm font-medium text-blue-900">OCR 신뢰도</span>
                  <Badge variant="outline" className="bg-white">{confidenceLabel}</Badge>
                </div>
              </div>
              {ocrResult?.warnings.length ? (
                <div className="mt-3 rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800">{ocrResult.warnings[0]}</div>
              ) : null}
            </CardContent>
          </Card>

        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader><CardTitle>업로드된 지출증빙 템플릿</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              {latestTemplate ? (
                <>
                  <div className="rounded-lg border border-gray-200 p-4">
                    <div className="text-sm font-medium text-gray-900">{latestTemplate.originalFilename}</div>
                    <div className="mt-1 text-xs text-gray-500">{(latestTemplate.size / 1024).toFixed(1)} KB · {latestTemplate.uploadedAt}</div>
                  </div>
                  <div className="flex items-center gap-2 text-sm">
                    {analyzingTemplate ? (
                      <><Badge variant="outline">분석 중</Badge><span className="text-gray-600">템플릿 필드를 읽는 중입니다.</span></>
                    ) : templateAnalysis?.supported ? (
                      <><Badge variant="outline" className="border-green-200 bg-green-50 text-green-700">분석 완료</Badge><span className="text-gray-600">필드 {templateAnalysis.placeholders.length}개를 불러왔습니다.</span></>
                    ) : (
                      <><Badge variant="outline" className="border-yellow-200 bg-yellow-50 text-yellow-700">확인 필요</Badge><span className="text-gray-600">placeholder 기반 템플릿인지 확인하세요.</span></>
                    )}
                  </div>
                  {templateAnalysis?.warnings.length ? <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3 text-sm text-yellow-800">{templateAnalysis.warnings[0]}</div> : null}
                </>
              ) : (
                <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500">먼저 프로젝트 상세 화면에서 지출증빙 템플릿 파일을 업로드하세요.</div>
              )}
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle>자동 추출</CardTitle></CardHeader>
            <CardContent className="space-y-4">
              <div className="rounded-lg border border-yellow-200 bg-yellow-50 p-3">
                <div className="flex items-start gap-2">
                  <AlertCircle className="mt-0.5 h-5 w-5 flex-shrink-0 text-yellow-600" />
                  <div className="text-sm text-yellow-800">
                    <p className="font-medium">OCR 확인 필요</p>
                    <p className="mt-1">OCR 결과와 현재 입력값을 기준으로 템플릿 필드가 자동 채워집니다. 각 값은 바로 수정할 수 있고, 수정한 값으로 다운로드됩니다.</p>
                  </div>
                </div>
              </div>
              <Table>
                <TableHeader>
                  <TableRow><TableHead>필드</TableHead></TableRow>
                </TableHeader>
                <TableBody>
                  {templateFieldDrafts.length > 0 ? (
                    templateFieldDrafts.map((draft) => (
                      <TableRow key={draft.key}>
                        <TableCell className="space-y-2">
                          <div>
                            <div className="font-medium">{getFieldLabel(draft.key)}</div>
                            <div className="text-xs text-gray-500">{draft.key}</div>
                          </div>
                          <Input value={draft.value} onChange={(event) => updateTemplateFieldDraft(draft.key, event.target.value)} placeholder="값을 입력하세요" />
                        </TableCell>
                      </TableRow>
                    ))
                  ) : (
                    <TableRow><TableCell className="text-center text-sm text-gray-500">템플릿 필드를 불러오면 여기에서 OCR 값과 사용자 수정값을 함께 검토할 수 있습니다.</TableCell></TableRow>
                  )}
                </TableBody>
              </Table>
              <div className="grid gap-4 md:grid-cols-2">
                <div className="space-y-2">
                  <Label htmlFor="expense-category">예산 카테고리</Label>
                  <Select
                    value={form.category || "__empty"}
                    onValueChange={(value) => updateForm("category", value === "__empty" ? "" : value)}
                  >
                    <SelectTrigger id="expense-category">
                      <SelectValue placeholder="카테고리를 선택하세요" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__empty">선택 안 함</SelectItem>
                      {categoryOptions.map((option) => (
                        <SelectItem key={option.category} value={option.category}>
                          {option.category}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="expense-subcategory">세부 항목</Label>
                  <Select
                    value={form.subcategory || "__empty"}
                    onValueChange={(value) => updateForm("subcategory", value === "__empty" ? "" : value)}
                    disabled={!form.category || subcategoryOptions.length === 0}
                  >
                    <SelectTrigger id="expense-subcategory">
                      <SelectValue placeholder={form.category ? "세부 항목을 선택하세요" : "먼저 카테고리를 선택하세요"} />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="__empty">선택 안 함</SelectItem>
                      {subcategoryOptions.map((subcategory) => (
                        <SelectItem key={subcategory} value={subcategory}>
                          {subcategory}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              </div>
              <div className="space-y-2">
                <div className="flex gap-2">
                  <Button variant="outline" className="flex-1 gap-2" onClick={() => void handleSaveExpense()}><Save className="h-4 w-4" />지출 저장</Button>
                  <Button variant="outline" className="flex-1 gap-2" onClick={() => void handlePreview()}><Eye className="h-4 w-4" />미리보기</Button>
                </div>
                <div className="flex gap-2">
                  <Button variant="outline" className="flex-1 gap-2" onClick={() => void handleDownloadWord()} disabled={!selectedProjectId || downloadingWord}><Download className="h-4 w-4" />{downloadingWord ? "Word 생성 중..." : "Word 다운로드"}</Button>
                  <Button variant="outline" className="flex-1 gap-2" onClick={() => void handleDownloadHwp()} disabled={!selectedProjectId || downloadingHwp}><Download className="h-4 w-4" />{downloadingHwp ? "HWP 생성 중..." : "HWP 다운로드"}</Button>
                </div>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <Card>
        <CardHeader>
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <CardTitle>인식된 품목</CardTitle>
              <p className="mt-1 text-sm text-gray-600">OCR 결과가 부족해도 여기서 품목을 직접 추가, 수정, 삭제할 수 있습니다.</p>
            </div>
            <Button type="button" variant="outline" size="sm" className="gap-2 self-start md:self-auto" onClick={addLineItem}>
              <Plus className="h-4 w-4" />
              품목 추가
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {lineItems.length > 0 ? (
            <div className="overflow-x-auto rounded-lg border border-gray-200">
              <table className="min-w-full border-collapse text-sm">
                <thead className="bg-gray-50 text-gray-700">
                  <tr>
                    <th className="border-b border-r border-gray-200 px-3 py-2 text-left">품명</th>
                    <th className="border-b border-r border-gray-200 px-3 py-2 text-left">규격</th>
                    <th className="border-b border-r border-gray-200 px-3 py-2 text-right">수량</th>
                    <th className="border-b border-r border-gray-200 px-3 py-2 text-left">단위</th>
                    <th className="border-b border-r border-gray-200 px-3 py-2 text-right">단가</th>
                    <th className="border-b border-r border-gray-200 px-3 py-2 text-right">금액</th>
                    <th className="border-b border-gray-200 px-3 py-2 text-center">관리</th>
                  </tr>
                </thead>
                <tbody>
                  {lineItems.map((lineItem) => (
                    <tr key={lineItem.id} className="border-b border-gray-200 last:border-b-0">
                      <td className="border-r border-gray-200 px-2 py-2"><Input value={lineItem.itemName} onChange={(event) => updateLineItemField(lineItem.id, "itemName", event.target.value)} placeholder="품명" className="min-w-40" /></td>
                      <td className="border-r border-gray-200 px-2 py-2"><Input value={lineItem.spec} onChange={(event) => updateLineItemField(lineItem.id, "spec", event.target.value)} placeholder="규격" className="min-w-32" /></td>
                      <td className="border-r border-gray-200 px-2 py-2"><Input value={lineItem.quantity} onChange={(event) => updateLineItemField(lineItem.id, "quantity", event.target.value)} placeholder="수량" inputMode="decimal" className="min-w-24 text-right" /></td>
                      <td className="border-r border-gray-200 px-2 py-2"><Input value={lineItem.unit} onChange={(event) => updateLineItemField(lineItem.id, "unit", event.target.value)} placeholder="단위" className="min-w-24" /></td>
                      <td className="border-r border-gray-200 px-2 py-2"><Input value={lineItem.unitPrice} onChange={(event) => updateLineItemField(lineItem.id, "unitPrice", event.target.value)} placeholder="단가" inputMode="decimal" className="min-w-28 text-right" /></td>
                      <td className="border-r border-gray-200 px-2 py-2"><Input value={lineItem.amount} onChange={(event) => updateLineItemField(lineItem.id, "amount", event.target.value)} placeholder="금액" inputMode="decimal" className="min-w-28 text-right" /></td>
                      <td className="px-2 py-2 text-center">
                        <Button type="button" variant="ghost" size="icon" onClick={() => removeLineItem(lineItem.id)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500">
              OCR 품목이 없으면 여기서 직접 추가할 수 있습니다. 첫 번째 품목과 총 금액은 상단 입력값에도 자동 반영됩니다.
            </div>
          )}
        </CardContent>
      </Card>

      {showPreview && (
        <Card className="border-blue-300 bg-blue-50/30">
          <CardHeader>
            <div className="flex items-center justify-between">
              <CardTitle className="flex items-center gap-2"><FileText className="h-5 w-5" />지출증빙서류 미리보기</CardTitle>
              <Button variant="ghost" size="sm" onClick={() => setShowPreview(false)}>미리보기 닫기</Button>
            </div>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="rounded-lg border-2 border-gray-200 bg-white p-6 shadow-sm">
              {previewData ? (
                <div className="space-y-4">
                  {previewData.paragraphs.map((paragraph, index) => (
                    <p key={`paragraph-${index}`} className="text-sm text-gray-900">
                      {paragraph}
                    </p>
                  ))}
                  {previewData.tables.map((table, tableIndex) => (
                    <div key={`table-${tableIndex}`} className="overflow-hidden rounded-lg border border-gray-200">
                      <table className="w-full border-collapse text-sm">
                        <tbody>
                          {table.map((row, rowIndex) => (
                            <tr key={`row-${tableIndex}-${rowIndex}`} className="border-b border-gray-200 last:border-b-0">
                              {row.map((cell, cellIndex) => (
                                <td key={`cell-${tableIndex}-${rowIndex}-${cellIndex}`} className="border-r border-gray-200 px-3 py-2 last:border-r-0">
                                  {cell || "-"}
                                </td>
                              ))}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="space-y-4">
                  <div className="border-b border-gray-200 pb-4">
                    <h2 className="text-lg font-semibold">템플릿에 채워질 필드</h2>
                    <p className="mt-1 text-sm text-gray-600">다운로드 시 아래 값이 업로드한 지출증빙 템플릿 placeholder에 반영됩니다.</p>
                  </div>
                  <div className="grid gap-3 md:grid-cols-2">
                    {templateFieldDrafts.length > 0 ? templateFieldDrafts.map((draft) => (
                      <div key={draft.key} className="rounded-lg border border-gray-200 p-3">
                        <div className="text-xs text-gray-500">{getFieldLabel(draft.key)}</div>
                        <div className="mt-1 font-medium text-gray-900">{draft.value || "-"}</div>
                      </div>
                    )) : <div className="text-sm text-gray-500">템플릿 필드가 아직 없습니다.</div>}
                  </div>
                </div>
              )}
            </div>
            <div className="flex gap-2">
              <Button variant="outline" className="flex-1 gap-2" onClick={() => void handleSaveExpense()}><Save className="h-4 w-4" />지출 저장</Button>
              <Button variant="outline" className="flex-1 gap-2" onClick={() => void handleDownloadWord()}><Download className="h-4 w-4" />Word 다운로드</Button>
              <Button className="flex-1 gap-2" onClick={() => void handleDownloadHwp()}><CheckCircle className="h-4 w-4" />HWP 다운로드</Button>
            </div>
          </CardContent>
        </Card>
      )}

    </div>
  );
}
