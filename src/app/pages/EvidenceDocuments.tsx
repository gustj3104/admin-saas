import type { ChangeEvent } from "react";
import { useEffect, useState } from "react";
import { AlertCircle, CheckCircle, Download, Eye, FilePlus2, FileText, Trash2, Upload, X } from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { EvidenceDocument, Expense, ProjectFile, TemplateFieldAnalysis } from "../api/types";
import { useProjectContext } from "../context/ProjectContext";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";

type TemplateField = {
  id: string;
  placeholder: string;
  description: string;
  mapped: boolean;
};

const PLACEHOLDER_DESCRIPTIONS: Record<string, string> = {
  date: "지출 또는 증빙 작성일",
  vendor: "거래처 또는 공급사명",
  amount: "총 지출 금액",
  itemName: "품목 또는 지출 항목명",
  category: "예산 카테고리",
  subcategory: "예산 세부 항목",
  paymentMethod: "결제 수단",
  notes: "비고",
  projectName: "프로젝트명",
};

const DEFAULT_TEMPLATE_FIELDS: TemplateField[] = [
  { id: "date", placeholder: "{{ date }}", description: PLACEHOLDER_DESCRIPTIONS.date, mapped: false },
  { id: "vendor", placeholder: "{{ vendor }}", description: PLACEHOLDER_DESCRIPTIONS.vendor, mapped: false },
  { id: "amount", placeholder: "{{ amount }}", description: PLACEHOLDER_DESCRIPTIONS.amount, mapped: false },
];

function toTemplateFields(analysis: TemplateFieldAnalysis | null): TemplateField[] {
  if (!analysis) {
    return DEFAULT_TEMPLATE_FIELDS;
  }

  const extracted = new Set(analysis.placeholders);
  const knownFields = DEFAULT_TEMPLATE_FIELDS.map((field) => ({
    ...field,
    mapped: extracted.has(field.id),
  }));
  const customFields = analysis.placeholders
    .filter((placeholder) => !DEFAULT_TEMPLATE_FIELDS.some((field) => field.id === placeholder))
    .map((placeholder) => ({
      id: placeholder,
      placeholder: `{{ ${placeholder} }}`,
      description: PLACEHOLDER_DESCRIPTIONS[placeholder] ?? "사용자 정의 템플릿 필드",
      mapped: true,
    }));

  return [...knownFields, ...customFields];
}

export function EvidenceDocuments() {
  const { selectedProjectId, selectedProject } = useProjectContext();
  const [documents, setDocuments] = useState<EvidenceDocument[]>([]);
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [templateFiles, setTemplateFiles] = useState<ProjectFile[]>([]);
  const [selectedDocument, setSelectedDocument] = useState<EvidenceDocument | null>(null);
  const [showFieldReview, setShowFieldReview] = useState(false);
  const [templateConfirmed, setTemplateConfirmed] = useState(false);
  const [templateFields, setTemplateFields] = useState<TemplateField[]>(DEFAULT_TEMPLATE_FIELDS);
  const [templateAnalysis, setTemplateAnalysis] = useState<TemplateFieldAnalysis | null>(null);
  const [analyzingTemplate, setAnalyzingTemplate] = useState(false);

  const uploadedTemplate = templateFiles[0] ?? null;

  const refresh = async () => {
    if (!selectedProjectId) return;
    const [documentData, expenseData, fileData] = await Promise.all([
      api.getDocuments(selectedProjectId),
      api.getExpenses(selectedProjectId),
      api.getProjectFiles(selectedProjectId, "evidence-template"),
    ]);
    setDocuments(documentData);
    setExpenses(expenseData);
    setTemplateFiles(fileData);
  };

  useEffect(() => {
    if (!selectedProjectId) return;
    void refresh();
  }, [selectedProjectId]);

  useEffect(() => {
    setSelectedDocument(null);
    setShowFieldReview(false);
    setTemplateConfirmed(false);
    setTemplateAnalysis(null);
    setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
  }, [selectedProjectId]);

  useEffect(() => {
    if (!selectedProjectId || !uploadedTemplate) {
      setTemplateAnalysis(null);
      setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
      return;
    }

    setAnalyzingTemplate(true);
    void api
      .inspectProjectTemplateFields(selectedProjectId, uploadedTemplate.relativePath)
      .then((analysis) => {
        setTemplateAnalysis(analysis);
        setTemplateFields(toTemplateFields(analysis));
        if (analysis.supported && analysis.placeholders.length > 0) {
          setTemplateConfirmed(true);
        }
      })
      .catch((error) => {
        setTemplateAnalysis(null);
        setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
        toast.error(error instanceof Error ? error.message : "템플릿 필드 분석에 실패했습니다.");
      })
      .finally(() => setAnalyzingTemplate(false));
  }, [selectedProjectId, uploadedTemplate]);

  const handleCreateDocument = async () => {
    if (!selectedProjectId) return;
    const targetExpense = expenses[0];
    if (!targetExpense) {
      toast.warning("문서를 만들 지출 데이터가 없습니다.");
      return;
    }

    await api.createDocument({
      projectId: selectedProjectId,
      expenseId: targetExpense.id,
      documentType: "지출 증빙",
      vendor: targetExpense.vendor,
      amount: targetExpense.amount,
      createdDate: new Date().toISOString().slice(0, 10),
      status: "DRAFT",
    });
    await refresh();
    toast.success("증빙 문서 초안을 생성했습니다.");
  };

  const handleView = async (id: number) => {
    const document = await api.getDocument(id);
    setSelectedDocument(document);
  };

  const buildExportPayload = (document: EvidenceDocument) => {
    const expense = expenses.find((item) => item.expenseCode === document.expenseCode);
    return {
      projectId: selectedProjectId,
      projectName: selectedProject?.name ?? "",
      paymentDate: expense?.paymentDate ?? document.createdDate,
      vendor: document.vendor,
      itemName: expense?.itemName ?? document.documentType,
      category: expense?.category ?? "",
      subcategory: expense?.subcategory ?? "",
      paymentMethod: expense?.paymentMethod ?? "",
      amount: `₩${document.amount.toLocaleString()}`,
      notes: expense?.notes ?? "",
    };
  };

  const handleDownloadWord = async (document: EvidenceDocument) => {
    await api.downloadEvidenceWord(buildExportPayload(document));
    toast.success("Word 문서를 다운로드했습니다.");
  };

  const handleTemplateUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    if (!selectedProjectId) return;
    const file = event.target.files?.[0];
    if (!file) return;

    await api.uploadProjectFile(selectedProjectId, "evidence-template", file);
    await refresh();
    setShowFieldReview(false);
    setTemplateConfirmed(false);
    setTemplateAnalysis(null);
    setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
    event.target.value = "";
    toast.success(`${file.name} 템플릿을 업로드했습니다.`);
  };

  const handleRemoveTemplate = async () => {
    if (!selectedProjectId || !uploadedTemplate) return;
    await api.deleteProjectFile(selectedProjectId, uploadedTemplate.relativePath);
    await refresh();
    setShowFieldReview(false);
    setTemplateConfirmed(false);
    setTemplateAnalysis(null);
    setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
    toast.success("템플릿을 제거했습니다.");
  };

  const toggleFieldMapping = (fieldId: string) => {
    setTemplateFields((prev) =>
      prev.map((field) => (field.id === fieldId ? { ...field, mapped: !field.mapped } : field)),
    );
  };

  const updateTemplateField = (fieldId: string, patch: Partial<TemplateField>) => {
    setTemplateFields((prev) =>
      prev.map((field) => (field.id === fieldId ? { ...field, ...patch } : field)),
    );
  };

  const handleRemoveTemplateField = (fieldId: string) => {
    setTemplateFields((prev) => prev.filter((field) => field.id !== fieldId));
  };

  const handleConfirmTemplate = () => {
    setTemplateConfirmed(true);
    setShowFieldReview(false);
    toast.success("템플릿 필드 검토를 완료했습니다.");
  };

  return (
    <div className="space-y-6 p-4 md:p-6">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-semibold text-gray-900">증빙 문서</h1>
          <p className="mt-1 text-sm text-gray-600">문서 초안 생성, 템플릿 관리, Word 다운로드를 처리합니다.</p>
        </div>
        <Button className="w-full gap-2 sm:w-auto" onClick={() => void handleCreateDocument()}>
          <FilePlus2 className="h-4 w-4" />
          문서 생성
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>템플릿 설정</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          <div className="space-y-3">
            <p className="text-sm text-gray-600">
              증빙문서 생성을 위한 워드 또는 한글 템플릿을 업로드하세요. {"{{ date }}"}, {"{{ vendor }}"}, {"{{ amount }}"} 등의 필드가 자동으로 채워집니다.
            </p>

            {!uploadedTemplate ? (
              <div className="rounded-lg border-2 border-dashed border-gray-300 bg-gray-50 p-6 transition-colors hover:border-blue-400 hover:bg-blue-50/50">
                <div className="flex flex-col items-center justify-center gap-3">
                  <div className="rounded-full bg-blue-100 p-3">
                    <Upload className="h-6 w-6 text-blue-600" />
                  </div>
                  <div className="text-center">
                    <p className="text-sm font-medium text-gray-900">문서 템플릿 업로드</p>
                    <p className="mt-1 text-xs text-gray-600">.docx, .doc, .hwp 파일 지원</p>
                  </div>
                  <Button variant="outline" size="sm" onClick={() => document.getElementById("template-upload")?.click()}>
                    <Upload className="mr-2 h-4 w-4" />
                    파일 선택
                  </Button>
                  <input
                    id="template-upload"
                    type="file"
                    accept=".doc,.docx,.hwp,.hwpx"
                    onChange={(event) => void handleTemplateUpload(event)}
                    className="hidden"
                  />
                </div>
              </div>
            ) : (
              <div className="space-y-3">
                <div className="rounded-lg border border-blue-300 bg-blue-50 p-4">
                  <div className="flex items-start justify-between">
                    <div className="flex items-start gap-3">
                      <div className="rounded-lg bg-blue-100 p-2">
                        <FileText className="h-5 w-5 text-blue-600" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">{uploadedTemplate.originalFilename}</p>
                        <p className="mt-1 text-xs text-gray-600">{(uploadedTemplate.size / 1024).toFixed(2)} KB</p>
                        <div className="mt-2 flex items-center gap-2">
                          {analyzingTemplate ? (
                            <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                              필드 분석중
                            </Badge>
                          ) : templateAnalysis?.supported ? (
                            <Badge variant="outline" className="border-green-200 bg-green-50 text-green-700">
                              <CheckCircle className="mr-1 h-3 w-3" />
                              자동 분석 가능
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="border-yellow-200 bg-yellow-50 text-yellow-700">
                              <AlertCircle className="mr-1 h-3 w-3" />
                              자동 분석 미지원
                            </Badge>
                          )}
                          {templateConfirmed && templateAnalysis?.supported && (
                            <Badge variant="outline" className="border-green-200 bg-green-50 text-green-700">
                              확인완료
                            </Badge>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button variant="ghost" size="sm" onClick={() => setShowFieldReview(true)} disabled={analyzingTemplate}>
                        <Eye className="mr-2 h-4 w-4" />
                        필드 검토
                      </Button>
                      <Button variant="ghost" size="sm" onClick={() => document.getElementById("template-upload")?.click()}>
                        변경
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => void handleRemoveTemplate()}
                        className="text-red-600 hover:bg-red-50 hover:text-red-700"
                      >
                        <X className="h-4 w-4" />
                      </Button>
                      <input
                        id="template-upload"
                        type="file"
                        accept=".doc,.docx,.hwp,.hwpx"
                        onChange={(event) => void handleTemplateUpload(event)}
                        className="hidden"
                      />
                    </div>
                  </div>
                </div>

                {templateAnalysis?.warnings?.length ? (
                  <div className="rounded-lg border border-yellow-200 bg-yellow-50 px-4 py-3 text-sm text-yellow-800">
                    {templateAnalysis.warnings[0]}
                  </div>
                ) : null}
              </div>
            )}
          </div>

          {showFieldReview && (
            <div className="space-y-4">
              <h4 className="text-sm font-medium text-blue-900">템플릿 필드 검토</h4>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>필드 플레이스홀더</TableHead>
                    <TableHead>설명</TableHead>
                    <TableHead className="text-right">매핑 여부</TableHead>
                    <TableHead className="w-12" />
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {templateFields.map((field) => (
                    <TableRow key={field.id}>
                      <TableCell>
                        <Input
                          value={field.placeholder}
                          onChange={(event) => updateTemplateField(field.id, { placeholder: event.target.value })}
                          className="font-medium"
                        />
                      </TableCell>
                      <TableCell>
                        <Input
                          value={field.description}
                          onChange={(event) => updateTemplateField(field.id, { description: event.target.value })}
                          className="text-sm"
                        />
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          variant="ghost"
                          size="icon"
                          className="h-8 w-8"
                          onClick={() => toggleFieldMapping(field.id)}
                        >
                          {field.mapped ? (
                            <CheckCircle className="h-3 w-3 text-green-600" />
                          ) : (
                            <X className="h-3 w-3 text-red-600" />
                          )}
                        </Button>
                      </TableCell>
                      <TableCell>
                        <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => handleRemoveTemplateField(field.id)}>
                          <Trash2 className="h-4 w-4 text-red-600" />
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="flex justify-end">
                <Button variant="outline" size="sm" onClick={handleConfirmTemplate}>
                  템플릿 확인
                </Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {selectedDocument && (
        <Card>
          <CardHeader>
            <CardTitle>선택된 문서</CardTitle>
          </CardHeader>
          <CardContent className="grid grid-cols-1 gap-3 md:grid-cols-2">
            <div>문서 ID: {selectedDocument.id}</div>
            <div>지출 코드: {selectedDocument.expenseCode}</div>
            <div>문서 유형: {selectedDocument.documentType}</div>
            <div>거래처: {selectedDocument.vendor}</div>
            <div>금액: ₩{selectedDocument.amount.toLocaleString()}</div>
            <div>생성일: {selectedDocument.createdDate}</div>
          </CardContent>
        </Card>
      )}

      <Card>
        <CardHeader>
          <CardTitle>문서 목록</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {documents.length === 0 ? (
            <div className="rounded-lg border border-dashed border-gray-300 p-6 text-sm text-gray-500">
              생성된 증빙 문서가 없습니다. 상단의 `문서 생성`으로 초안을 만들 수 있습니다.
            </div>
          ) : (
            documents.map((document) => (
              <div key={document.id} className="flex items-center justify-between rounded-lg border border-gray-200 p-4">
                <div>
                  <div className="font-medium text-gray-900">{document.documentType}</div>
                  <div className="mt-1 text-xs text-gray-600">
                    {document.expenseCode} / {document.vendor}
                  </div>
                  <div className="mt-1 text-xs text-gray-500">{document.createdDate}</div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="outline">{document.status}</Badge>
                  <Button variant="outline" size="sm" onClick={() => void handleView(document.id)}>
                    <Eye className="h-4 w-4" />
                  </Button>
                  <Button variant="outline" size="sm" onClick={() => void handleDownloadWord(document)}>
                    <Download className="h-4 w-4" />
                    Word
                  </Button>
                </div>
              </div>
            ))
          )}
        </CardContent>
      </Card>
    </div>
  );
}
