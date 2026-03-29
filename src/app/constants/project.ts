import type { BudgetItemStatus, ProjectStatus } from "../api/types";

export const PROJECT_STATUS_OPTIONS: Array<{ value: ProjectStatus; label: string }> = [
  { value: "DRAFT", label: "초안" },
  { value: "ACTIVE", label: "진행중" },
  { value: "COMPLETED", label: "완료" },
];

export const BUDGET_RULE_STATUS_OPTIONS: Array<{ value: BudgetItemStatus; label: string }> = [
  { value: "VALID", label: "유효" },
  { value: "WARNING", label: "경고" },
  { value: "ERROR", label: "오류" },
];

export function getBudgetRuleStatusLabel(status: BudgetItemStatus) {
  return BUDGET_RULE_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status;
}
