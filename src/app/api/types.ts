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
