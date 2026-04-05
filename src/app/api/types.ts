export type ProjectStatus = "DRAFT" | "ACTIVE" | "COMPLETED";
export type BudgetItemStatus = "VALID" | "WARNING" | "ERROR";
export type ExpenseStatus = "PENDING" | "PROCESSED";
export type DocumentStatus = "DRAFT" | "CONFIRMED";
export type ValidationSeverity = "VALID" | "WARNING" | "ERROR";

export interface Project {
  id: number;
  name: string;
  description: string | null;
  totalBudget: number;
  executedBudget: number;
  status: ProjectStatus;
  startDate: string;
  endDate: string;
  lastUpdated: string | null;
}

export interface BudgetRule {
  id: number;
  category: string;
  subcategory: string;
  ruleDescription: string;
  allocated: number;
  percentage: number;
  status: BudgetItemStatus;
}

export interface Expense {
  id: number;
  expenseCode: string;
  projectId: number;
  paymentDate: string;
  vendor: string;
  itemName: string;
  quantity: number | null;
  unitPrice: number | null;
  category: string;
  subcategory: string;
  amount: number;
  paymentMethod: string;
  notes: string | null;
  status: ExpenseStatus;
}

export interface EvidenceDocument {
  id: number;
  expenseCode: string;
  documentType: string;
  vendor: string;
  amount: number;
  createdDate: string;
  status: DocumentStatus;
}

export interface ValidationResult {
  id: number;
  type: string;
  category: string;
  description: string;
  severity: ValidationSeverity;
  date: string;
  linkTo: string | null;
  resolved: boolean;
  resolutionNote: string | null;
  resolvedDate: string | null;
}

export interface Settlement {
  id: number;
  projectId: number;
  reportTitle: string;
  reportDate: string;
  preparedBy: string;
  approvedBy: string;
  summaryNotes: string;
  totalAllocated: number;
  totalSpent: number;
  totalVariance: number;
  executionRate: number;
}

export interface DashboardOverview {
  projectId: number;
  projectName: string;
  totalBudget: number;
  usedBudget: number;
  remainingBudget: number;
  budgetRuleCount: number;
  expenseCount: number;
  validationCount: number;
  warningCount: number;
  errorCount: number;
}

export interface UploadedFile {
  originalFilename: string;
  storedFilename: string;
  size: number;
  path: string;
}

export interface ProjectFile {
  relativePath: string;
  category: string;
  originalFilename: string;
  storedFilename: string;
  size: number;
  uploadedAt: string;
}

export interface TemplateFieldAnalysis {
  originalFilename: string;
  templateType: string;
  supported: boolean;
  placeholders: string[];
  warnings: string[];
}

export interface ReceiptOcrFields {
  paymentDate: string | null;
  vendor: string | null;
  itemName: string | null;
  quantity: number | null;
  unitPrice: number | null;
  amount: number | null;
  paymentMethod: string | null;
  category: string | null;
  subcategory: string | null;
  lineItems: ReceiptOcrLineItem[];
}

export interface ReceiptOcrLineItem {
  itemName: string;
  spec: string | null;
  quantity: number | null;
  unit: string | null;
  unitPrice: number | null;
  amount: number | null;
}

export interface ReceiptOcrResult {
  originalFilename: string;
  storedFilename: string;
  size: number;
  path: string;
  ocrAvailable: boolean;
  status: "COMPLETED" | "UNAVAILABLE" | "UNSUPPORTED";
  rawText: string | null;
  confidence: number | null;
  fields: ReceiptOcrFields;
  warnings: string[];
}

export type TemplateDocumentType = "INSPECTION_REPORT" | "UNKNOWN";
export type TemplateStatus = "UPLOADED" | "EXTRACTED" | "MAPPED";
export type TemplateFieldType = "STRING" | "DATE" | "NUMBER" | "TABLE";
export type TemplateSourceType = "LABEL" | "TABLE" | "TABLE_COLUMN" | "MANUAL";
export type InspectionReportExportMode = "PER_RECEIPT" | "BY_DATE" | "ALL";

export interface NormalizedBlock {
  type: string;
  text: string | null;
  rows: string[][];
}

export interface NormalizedLabel {
  label: string;
  value: string;
}

export interface NormalizedTable {
  title: string;
  headers: string[];
  rows: string[][];
}

export interface NormalizedTemplateContent {
  documentType: string;
  blocks: NormalizedBlock[];
  labels: NormalizedLabel[];
  tables: NormalizedTable[];
}

export interface TemplateFieldMapping {
  id: string;
  key: string;
  displayName: string;
  description: string;
  sourceType: TemplateSourceType;
  sourceName: string;
  fieldType: TemplateFieldType;
  recommended: boolean;
  confirmed: boolean;
  createdBySystem: boolean;
}

export interface TemplateTableColumnMapping {
  id: string;
  key: string;
  displayName: string;
  description: string;
  sourceName: string;
  fieldType: TemplateFieldType;
  recommended: boolean;
  confirmed: boolean;
  createdBySystem: boolean;
}

export interface TemplateTableMapping {
  id: string;
  key: string;
  displayName: string;
  sourceName: string;
  recommended: boolean;
  confirmed: boolean;
  createdBySystem: boolean;
  columns: TemplateTableColumnMapping[];
}

export interface TemplateMappingSchema {
  templateId: string;
  documentType: TemplateDocumentType;
  fields: TemplateFieldMapping[];
  tables: TemplateTableMapping[];
}

export interface TemplateExtractionSummary {
  labelCount: number;
  tableCount: number;
  recommendedFieldCount: number;
  recommendedTableCount: number;
}

export interface UploadedTemplate {
  templateId: string;
  filename: string;
  contentType: string;
  size: number;
  detectedDocumentType: TemplateDocumentType;
  status: TemplateStatus;
  uploadedAt: string;
}

export interface TemplateDetail {
  templateId: string;
  filename: string;
  contentType: string;
  size: number;
  detectedDocumentType: TemplateDocumentType;
  status: TemplateStatus;
  uploadedAt: string;
  extractionSummary: TemplateExtractionSummary;
}

export interface TemplateExtractionResponse {
  schema: TemplateMappingSchema;
  detectedLabels: NormalizedLabel[];
  detectedTables: NormalizedTable[];
  summary: TemplateExtractionSummary;
}

export interface EvidencePreview {
  paragraphs: string[];
  tables: string[][][];
}

export interface InspectionReportGenerateItemRow {
  itemName: string;
  spec: string | null;
  quantity: string | null;
  unit: string | null;
  unitPrice: string | null;
  amount: string | null;
}

export interface InspectionReportGenerateEntry {
  receiptId: string;
  title: string | null;
  paymentDate: string | null;
  vendor: string | null;
  inspector: string | null;
  contractAmount: string | null;
  receiptImagePath: string | null;
  receiptOrder: number;
  itemRows: InspectionReportGenerateItemRow[];
}

export interface InspectionReportGeneratedDocument {
  groupKey: string;
  title: string;
  blockCount: number;
}

export interface InspectionReportGenerateResponse {
  mode: InspectionReportExportMode;
  documentCount: number;
  documents: InspectionReportGeneratedDocument[];
}
