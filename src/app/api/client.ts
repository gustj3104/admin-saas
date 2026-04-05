import type {
  BudgetRule,
  DashboardOverview,
  EvidenceDocument,
  Expense,
  NormalizedTemplateContent,
  Project,
  ProjectFile,
  ReceiptOcrResult,
  Settlement,
  EvidencePreview,
  InspectionReportGenerateEntry,
  InspectionReportGenerateResponse,
  TemplateDetail,
  TemplateExtractionResponse,
  TemplateFieldAnalysis,
  InspectionReportExportMode,
  TemplateMappingSchema,
  UploadedTemplate,
  UploadedFile,
  ValidationResult,
} from "./types";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "/api";

type ApiErrorPayload = {
  message?: string;
  details?: string[];
};

async function extractErrorMessage(response: Response, fallback: string): Promise<string> {
  const raw = await response.text();
  if (!raw) {
    return fallback;
  }

  try {
    const payload = JSON.parse(raw) as ApiErrorPayload;
    const details = payload.details?.filter(Boolean) ?? [];
    if (payload.message && details.length > 0) {
      return `${payload.message}: ${details.join(", ")}`;
    }
    if (payload.message) {
      return payload.message;
    }
    if (details.length > 0) {
      return details.join(", ");
    }
  } catch {
    // Fall through to the raw response text for non-JSON errors.
  }

  return raw;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, `요청에 실패했습니다: ${response.status}`));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  return response.json() as Promise<T>;
}

async function upload<T>(path: string, formData: FormData): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    body: formData,
  });

  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, `업로드에 실패했습니다: ${response.status}`));
  }

  return response.json() as Promise<T>;
}

async function download(path: string, init?: RequestInit): Promise<Blob> {
  const response = await fetch(`${API_BASE}${path}`, init);
  if (!response.ok) {
    throw new Error(await extractErrorMessage(response, `다운로드에 실패했습니다: ${response.status}`));
  }
  return response.blob();
}

function triggerFileDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  URL.revokeObjectURL(url);
}

export const api = {
  getProjects: () => request<Project[]>("/projects"),
  getProject: (id: number) => request<Project>(`/projects/${id}`),
  createProject: (payload: Record<string, unknown>) =>
    request<Project>("/projects", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateProject: (id: number, payload: Record<string, unknown>) =>
    request<Project>(`/projects/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  deleteProject: (id: number) =>
    request<void>(`/projects/${id}`, {
      method: "DELETE",
    }),
  getDashboardOverview: (projectId: number) =>
    request<DashboardOverview>(`/dashboard/overview?projectId=${projectId}`),
  getBudgetRules: (projectId: number) =>
    request<BudgetRule[]>(`/projects/${projectId}/budget-rules`),
  getExpenses: (projectId: number) =>
    request<Expense[]>(`/expenses?projectId=${projectId}`),
  createExpense: (payload: Record<string, unknown>) =>
    request<Expense>("/expenses", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateExpense: (id: number, payload: Record<string, unknown>) =>
    request<Expense>(`/expenses/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  updateExpenseStatus: (id: number, status: "PENDING" | "PROCESSED") =>
    request<Expense>(`/expenses/${id}/status`, {
      method: "POST",
      body: JSON.stringify({ status }),
    }),
  deleteExpense: (id: number) =>
    request<void>(`/expenses/${id}`, {
      method: "DELETE",
    }),
  getDocuments: (projectId: number) =>
    request<EvidenceDocument[]>(`/documents?projectId=${projectId}`),
  getDocument: (id: number) =>
    request<EvidenceDocument>(`/documents/${id}`),
  createDocument: (payload: Record<string, unknown>) =>
    request<EvidenceDocument>("/documents", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  getValidations: (projectId: number) =>
    request<ValidationResult[]>(`/validations?projectId=${projectId}`),
  runValidations: (projectId: number) =>
    request<ValidationResult[]>(`/validations/run?projectId=${projectId}`, {
      method: "POST",
    }),
  resolveValidation: (id: number, resolutionNote: string) =>
    request<ValidationResult>(`/validations/${id}/resolve`, {
      method: "POST",
      body: JSON.stringify({ resolutionNote }),
    }),
  getLatestSettlement: (projectId: number) =>
    request<Settlement>(`/settlements/latest?projectId=${projectId}`),
  generateSettlement: (projectId: number) =>
    request<Settlement>(`/settlements/generate?projectId=${projectId}`, {
      method: "POST",
    }),
  updateSettlement: (id: number, payload: Record<string, unknown>) =>
    request<Settlement>(`/settlements/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  replaceBudgetRules: (projectId: number, payload: Record<string, unknown>[]) =>
    request<BudgetRule[]>(`/projects/${projectId}/budget-rules`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  uploadProjectFile: (projectId: number, category: string, file: File) => {
    const formData = new FormData();
    formData.append("category", category);
    formData.append("file", file);
    return upload<UploadedFile>(`/projects/${projectId}/files/upload`, formData);
  },
  getProjectFiles: (projectId: number, category?: string) =>
    request<ProjectFile[]>(`/projects/${projectId}/files${category ? `?category=${encodeURIComponent(category)}` : ""}`),
  inspectProjectTemplateFields: (projectId: number, path: string) =>
    request<TemplateFieldAnalysis>(`/projects/${projectId}/files/template-fields?path=${encodeURIComponent(path)}`),
  extractProjectTemplate: (projectId: number, path: string) =>
    request<TemplateExtractionResponse>(`/projects/${projectId}/files/template-extract?path=${encodeURIComponent(path)}`),
  downloadProjectFile: async (projectId: number, path: string, filename: string) => {
    const blob = await download(`/projects/${projectId}/files/download?path=${encodeURIComponent(path)}`);
    triggerFileDownload(blob, filename);
  },
  deleteProjectFile: (projectId: number, path: string) =>
    request<void>(`/projects/${projectId}/files?path=${encodeURIComponent(path)}`, {
      method: "DELETE",
    }),
  uploadReceiptForOcr: (projectId: number, file: File) => {
    const formData = new FormData();
    formData.append("file", file);
    return upload<ReceiptOcrResult>(`/projects/${projectId}/files/receipt-ocr`, formData);
  },
  uploadTemplate: (file: File, structuredContent?: NormalizedTemplateContent) => {
    const formData = new FormData();
    formData.append("file", file);
    if (structuredContent) {
      formData.append("structuredContent", JSON.stringify(structuredContent));
    }
    return upload<UploadedTemplate>("/templates", formData);
  },
  uploadTemplateJson: (payload: { filename: string; contentType: string; structuredContent: NormalizedTemplateContent }) =>
    request<UploadedTemplate>("/templates", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  extractTemplate: (templateId: string) =>
    request<TemplateExtractionResponse>(`/templates/${templateId}/extract`, {
      method: "POST",
    }),
  updateTemplateMappings: (templateId: string, payload: TemplateMappingSchema) =>
    request<TemplateMappingSchema>(`/templates/${templateId}/mappings`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  getTemplateMappings: (templateId: string) =>
    request<TemplateMappingSchema>(`/templates/${templateId}/mappings`),
  getTemplateDetail: (templateId: string) =>
    request<TemplateDetail>(`/templates/${templateId}`),
  generateInspectionReport: (templateId: string, payload: { mode: InspectionReportExportMode; entries: InspectionReportGenerateEntry[] }) =>
    request<InspectionReportGenerateResponse>(`/templates/${templateId}/generate`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  generateProjectInspectionReport: (projectId: number, path: string, payload: { mode: InspectionReportExportMode; entries: InspectionReportGenerateEntry[] }) =>
    request<InspectionReportGenerateResponse>(`/projects/${projectId}/files/inspection-report/generate?path=${encodeURIComponent(path)}`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  downloadProjectInspectionReportWord: async (
    projectId: number,
    path: string,
    payload: { mode: InspectionReportExportMode; entries: InspectionReportGenerateEntry[] },
  ) => {
    const blob = await download(`/projects/${projectId}/files/inspection-report/export-word?path=${encodeURIComponent(path)}`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    triggerFileDownload(blob, payload.mode === "ALL" ? "물품검수조서_전체.docx" : "물품검수조서.docx");
  },
  downloadEvidenceWord: async (payload: Record<string, unknown>) => {
    const blob = await download("/documents/export/word", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    triggerFileDownload(blob, "evidence-document.docx");
  },
  downloadEvidenceHwp: async (payload: Record<string, unknown>) => {
    const blob = await download("/documents/export/hwp", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    triggerFileDownload(blob, "evidence-document.hwp");
  },
  previewEvidenceWord: (payload: Record<string, unknown>) =>
    request<EvidencePreview>("/documents/preview/word", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  downloadSettlementWord: async (payload: Record<string, unknown>) => {
    const blob = await download("/settlements/export/word", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    triggerFileDownload(blob, "settlement-report.docx");
  },
  downloadSettlementHwp: async (payload: Record<string, unknown>) => {
    const blob = await download("/settlements/export/hwp", {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(payload),
    });
    triggerFileDownload(blob, "settlement-report.hwp");
  },
};
