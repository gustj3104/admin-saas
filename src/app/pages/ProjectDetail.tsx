import type { ChangeEvent } from "react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { AlertCircle, ArrowLeft, CheckCircle, Eye, Plus, Save, Trash2, Upload, X } from "lucide-react";
import { toast } from "sonner";
import { api } from "../api/client";
import type { BudgetItemStatus, BudgetRule, Project, ProjectFile, TemplateFieldAnalysis } from "../api/types";
import { Badge } from "../components/ui/badge";
import { Button } from "../components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Input } from "../components/ui/input";
import { Label } from "../components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select";
import { Separator } from "../components/ui/separator";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "../components/ui/table";
import { Textarea } from "../components/ui/textarea";
import { PROJECT_STATUS_OPTIONS } from "../constants/project";

type TemplateField = {
  id: string;
  placeholder: string;
  description: string;
  mapped: boolean;
};

type BudgetSubcategory = {
  id: number;
  name: string;
  percentageLimit: number;
  ruleDescription: string;
  status: BudgetItemStatus;
};

type BudgetCategoryRule = {
  id: number;
  category: string;
  subcategories: BudgetSubcategory[];
  allowedTypes: string[];
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

function groupBudgetRules(rules: BudgetRule[]): BudgetCategoryRule[] {
  const grouped = new Map<string, BudgetCategoryRule>();

  for (const rule of rules) {
    const key = rule.category || `category-${rule.id}`;
    const existing = grouped.get(key);
    if (existing) {
      existing.subcategories.push({
        id: rule.id,
        name: rule.subcategory,
        percentageLimit: rule.percentage,
        ruleDescription: rule.ruleDescription,
        status: rule.status,
      });
      if (rule.ruleDescription && !existing.allowedTypes.includes(rule.ruleDescription)) {
        existing.allowedTypes.push(rule.ruleDescription);
      }
      continue;
    }

    grouped.set(key, {
      id: rule.id,
      category: rule.category,
      allowedTypes: rule.ruleDescription ? [rule.ruleDescription] : [],
      subcategories: [
        {
          id: rule.id,
          name: rule.subcategory,
          percentageLimit: rule.percentage,
          ruleDescription: rule.ruleDescription,
          status: rule.status,
        },
      ],
    });
  }

  return Array.from(grouped.values());
}

function buildBudgetPayload(groups: BudgetCategoryRule[], totalBudget: number) {
  return groups.flatMap((group) => {
    return group.subcategories.map((subcategory) => ({
      category: group.category,
      subcategory: subcategory.name,
      ruleDescription:
        subcategory.ruleDescription || `${group.category} > ${subcategory.name} 한도 ${subcategory.percentageLimit}%`,
      allocated: Math.round((totalBudget * subcategory.percentageLimit) / 100),
      percentage: subcategory.percentageLimit,
      status: subcategory.status ?? "VALID",
    }));
  });
}

function createEmptyCategoryRule(id: number): BudgetCategoryRule {
  return {
    id,
    category: "",
    allowedTypes: [],
    subcategories: [
      {
        id: id - 1,
        name: "",
        percentageLimit: 0,
        ruleDescription: "",
        status: "WARNING",
      },
    ],
  };
}

export function ProjectDetail() {
  const { id } = useParams();
  const projectId = Number(id);
  const [project, setProject] = useState<Project | null>(null);
  const [budgetRules, setBudgetRules] = useState<BudgetCategoryRule[]>([]);
  const [templateFiles, setTemplateFiles] = useState<ProjectFile[]>([]);
  const [showFieldReview, setShowFieldReview] = useState(false);
  const [templateConfirmed, setTemplateConfirmed] = useState(false);
  const [templateFields, setTemplateFields] = useState<TemplateField[]>(DEFAULT_TEMPLATE_FIELDS);
  const [templateAnalysis, setTemplateAnalysis] = useState<TemplateFieldAnalysis | null>(null);
  const [analyzingTemplate, setAnalyzingTemplate] = useState(false);
  const [projectForm, setProjectForm] = useState({
    name: "",
    description: "",
    totalBudget: "",
    executedBudget: "",
    status: "DRAFT" as Project["status"],
    startDate: "",
    endDate: "",
  });

  const uploadedTemplate = templateFiles[0] ?? null;

  const refreshTemplateFiles = async () => {
    if (!projectId) return;
    const files = await api.getProjectFiles(projectId, "evidence-template");
    setTemplateFiles(files);
  };

  useEffect(() => {
    if (!projectId) return;
    void Promise.all([
      api.getProject(projectId),
      api.getBudgetRules(projectId),
      api.getProjectFiles(projectId, "evidence-template"),
    ]).then(([projectData, rules, files]) => {
      setProject(projectData);
      setBudgetRules(groupBudgetRules(rules));
      setTemplateFiles(files);
      setProjectForm({
        name: projectData.name,
        description: projectData.description ?? "",
        totalBudget: String(projectData.totalBudget),
        executedBudget: String(projectData.executedBudget),
        status: projectData.status,
        startDate: projectData.startDate,
        endDate: projectData.endDate,
      });
    });
  }, [projectId]);

  useEffect(() => {
    if (!projectId || !uploadedTemplate) {
      setTemplateAnalysis(null);
      setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
      setTemplateConfirmed(false);
      return;
    }

    setAnalyzingTemplate(true);
    void api
      .inspectProjectTemplateFields(projectId, uploadedTemplate.relativePath)
      .then((analysis) => {
        setTemplateAnalysis(analysis);
        setTemplateFields(toTemplateFields(analysis));
        setTemplateConfirmed(analysis.supported && analysis.placeholders.length > 0);
      })
      .catch((error) => {
        setTemplateAnalysis(null);
        setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
        setTemplateConfirmed(false);
        toast.error(error instanceof Error ? error.message : "템플릿 필드 분석에 실패했습니다.");
      })
      .finally(() => setAnalyzingTemplate(false));
  }, [projectId, uploadedTemplate]);

  const handleSaveProject = async () => {
    if (!projectId) return;
    const updated = await api.updateProject(projectId, {
      name: projectForm.name,
      description: projectForm.description,
      totalBudget: Number(projectForm.totalBudget || 0),
      executedBudget: Number(projectForm.executedBudget || 0),
      status: projectForm.status,
      startDate: projectForm.startDate,
      endDate: projectForm.endDate,
    });
    setProject(updated);
    toast.success("프로젝트 기본 정보를 저장했습니다.");
  };

  const handleSaveBudgetRules = async () => {
    if (!projectId) return;
    const totalBudget = Number(projectForm.totalBudget || 0);
    const savedRules = await api.replaceBudgetRules(projectId, buildBudgetPayload(budgetRules, totalBudget));
    setBudgetRules(groupBudgetRules(savedRules));
    toast.success("예산 규칙을 저장했습니다.");
  };

  const handleTemplateUpload = async (event: ChangeEvent<HTMLInputElement>) => {
    if (!projectId) return;
    const file = event.target.files?.[0];
    if (!file) return;

    await api.uploadProjectFile(projectId, "evidence-template", file);
    await refreshTemplateFiles();
    setShowFieldReview(false);
    setTemplateConfirmed(false);
    setTemplateAnalysis(null);
    setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
    event.target.value = "";
    toast.success(`${file.name} 템플릿을 업로드했습니다.`);
  };

  const handleRemoveTemplate = async () => {
    if (!projectId || !uploadedTemplate) return;
    await api.deleteProjectFile(projectId, uploadedTemplate.relativePath);
    await refreshTemplateFiles();
    setShowFieldReview(false);
    setTemplateConfirmed(false);
    setTemplateAnalysis(null);
    setTemplateFields(DEFAULT_TEMPLATE_FIELDS);
    toast.success("템플릿을 제거했습니다.");
  };

  const handleConfirmTemplate = () => {
    setTemplateConfirmed(true);
    setShowFieldReview(false);
    toast.success("템플릿 검토를 완료했습니다.");
  };

  const toggleFieldMapping = (fieldId: string) => {
    setTemplateFields((prev) =>
      prev.map((field) => (field.id === fieldId ? { ...field, mapped: !field.mapped } : field)),
    );
  };

  const handleAddCategory = () => {
    setBudgetRules((prev) => [...prev, createEmptyCategoryRule(-Date.now())]);
  };

  const handleRemoveCategory = (ruleId: number) => {
    setBudgetRules((prev) => prev.filter((rule) => rule.id !== ruleId));
  };

  const handleUpdateCategory = (ruleId: number, category: string) => {
    setBudgetRules((prev) =>
      prev.map((rule) => (rule.id === ruleId ? { ...rule, category } : rule)),
    );
  };

  const handleAddSubcategory = (ruleId: number) => {
    setBudgetRules((prev) =>
      prev.map((rule) =>
        rule.id === ruleId
          ? {
              ...rule,
              subcategories: [
                ...rule.subcategories,
                {
                  id: -Date.now(),
                  name: "",
                  percentageLimit: 0,
                  ruleDescription: "",
                  status: "WARNING",
                },
              ],
            }
          : rule,
      ),
    );
  };

  const handleRemoveSubcategory = (ruleId: number, subcategoryId: number) => {
    setBudgetRules((prev) =>
      prev
        .map((rule) =>
          rule.id === ruleId
            ? { ...rule, subcategories: rule.subcategories.filter((sub) => sub.id !== subcategoryId) }
            : rule,
        )
        .filter((rule) => rule.subcategories.length > 0),
    );
  };

  const handleUpdateSubcategory = (
    ruleId: number,
    subcategoryId: number,
    patch: Partial<BudgetSubcategory>,
  ) => {
    setBudgetRules((prev) =>
      prev.map((rule) => {
        if (rule.id !== ruleId) return rule;
        const subcategories = rule.subcategories.map((sub) =>
          sub.id === subcategoryId ? { ...sub, ...patch } : sub,
        );
        return {
          ...rule,
          subcategories,
          allowedTypes: subcategories
            .map((sub) => sub.ruleDescription || sub.name)
            .filter((value, index, array) => value && array.indexOf(value) === index),
        };
      }),
    );
  };

  return (
    <div className="space-y-6 p-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Link to="/projects">
            <Button variant="ghost" size="icon">
              <ArrowLeft className="h-5 w-5" />
            </Button>
          </Link>
          <div>
            <h1 className="text-2xl font-semibold text-gray-900">프로젝트 상세</h1>
            <p className="mt-1 text-sm text-gray-600">기본 정보, 증빙문서 템플릿, 예산 규칙을 관리합니다.</p>
          </div>
        </div>
        <Button className="gap-2" onClick={() => void handleSaveProject()}>
          <Save className="h-4 w-4" />
          기본 정보 저장
        </Button>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>기본 정보</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="project-name">프로젝트명</Label>
              <Input
                id="project-name"
                placeholder="프로젝트명을 입력하세요"
                value={projectForm.name}
                onChange={(event) => setProjectForm((prev) => ({ ...prev, name: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="total-budget">총 예산 (원)</Label>
              <Input
                id="total-budget"
                type="number"
                placeholder="총 예산을 입력하세요"
                value={projectForm.totalBudget}
                onChange={(event) => setProjectForm((prev) => ({ ...prev, totalBudget: event.target.value }))}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="start-date">시작일</Label>
              <Input
                id="start-date"
                type="date"
                value={projectForm.startDate}
                onChange={(event) => setProjectForm((prev) => ({ ...prev, startDate: event.target.value }))}
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="end-date">종료일</Label>
              <Input
                id="end-date"
                type="date"
                value={projectForm.endDate}
                onChange={(event) => setProjectForm((prev) => ({ ...prev, endDate: event.target.value }))}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
            <div className="space-y-2">
              <Label htmlFor="project-status">상태</Label>
              <Select
                value={projectForm.status}
                onValueChange={(value) => setProjectForm((prev) => ({ ...prev, status: value as Project["status"] }))}
              >
                <SelectTrigger id="project-status">
                  <SelectValue placeholder="상태를 선택하세요" />
                </SelectTrigger>
                <SelectContent>
                  {PROJECT_STATUS_OPTIONS.map((option) => (
                    <SelectItem key={option.value} value={option.value}>
                      {option.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="executed-budget">집행 예산 (원)</Label>
              <Input
                id="executed-budget"
                type="number"
                placeholder="집행 예산을 입력하세요"
                value={projectForm.executedBudget}
                onChange={(event) => setProjectForm((prev) => ({ ...prev, executedBudget: event.target.value }))}
              />
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="description">프로젝트 설명 / 제안서</Label>
            <Textarea
              id="description"
              placeholder="프로젝트 설명을 입력하세요"
              rows={4}
              value={projectForm.description}
              onChange={(event) => setProjectForm((prev) => ({ ...prev, description: event.target.value }))}
            />
          </div>

          <Separator className="my-4" />

          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Label className="text-base font-semibold">증빙문서 템플릿</Label>
              <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                선택사항
              </Badge>
            </div>
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
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => document.getElementById("template-upload")?.click()}
                  >
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
                        <Upload className="h-5 w-5 text-blue-600" />
                      </div>
                      <div>
                        <p className="text-sm font-medium text-gray-900">{uploadedTemplate.originalFilename}</p>
                        <p className="mt-1 text-xs text-gray-600">
                          {(uploadedTemplate.size / 1024).toFixed(2)} KB
                        </p>
                        <div className="mt-2 flex items-center gap-2">
                          {analyzingTemplate ? (
                            <Badge variant="outline" className="border-blue-200 bg-blue-50 text-blue-700">
                              분석중
                            </Badge>
                          ) : templateConfirmed ? (
                            <Badge variant="outline" className="border-green-200 bg-green-50 text-green-700">
                              <CheckCircle className="mr-1 h-3 w-3" />
                              확인완료
                            </Badge>
                          ) : (
                            <Badge variant="outline" className="border-yellow-200 bg-yellow-50 text-yellow-700">
                              <AlertCircle className="mr-1 h-3 w-3" />
                              검토필요
                            </Badge>
                          )}
                        </div>
                      </div>
                    </div>
                    <div className="flex gap-2">
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => setShowFieldReview(true)}
                        disabled={analyzingTemplate}
                      >
                        <Eye className="mr-2 h-4 w-4" />
                        필드 검토
                      </Button>
                      <Button
                        variant="ghost"
                        size="sm"
                        onClick={() => document.getElementById("template-upload")?.click()}
                      >
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
                        <Input value={field.placeholder} className="font-medium" readOnly />
                      </TableCell>
                      <TableCell>
                        <Input value={field.description} className="text-sm" readOnly />
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
                        <Button variant="ghost" size="icon" className="h-8 w-8">
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

          {project?.lastUpdated && <p className="text-xs text-gray-500">마지막 수정: {project.lastUpdated}</p>}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-row items-center justify-between">
          <CardTitle>예산 규칙 설정</CardTitle>
          <Button variant="outline" size="sm" className="gap-2" onClick={handleAddCategory}>
            <Plus className="h-4 w-4" />
            카테고리 추가
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="text-sm text-gray-600">
            예산 카테고리, 세부 항목, 각 항목의 비율 한도를 설정합니다. 각 세부 항목마다 개별 한도를 설정할 수 있습니다.
          </div>

          <div className="space-y-6">
            {budgetRules.map((rule) => {
              const totalSubcategoryLimit = rule.subcategories.reduce((sum, sub) => sum + sub.percentageLimit, 0);

              return (
                <div key={rule.id} className="space-y-4 rounded-lg border border-gray-200 p-4">
                  <div className="flex items-center justify-between">
                    <div className="flex flex-1 items-center gap-3">
                      <Label className="text-sm font-medium">카테고리명:</Label>
                      <Input
                        value={rule.category}
                        placeholder="카테고리명을 입력하세요"
                        className="max-w-xs font-medium"
                        onChange={(event) => handleUpdateCategory(rule.id, event.target.value)}
                      />
                    </div>
                    <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => handleRemoveCategory(rule.id)}>
                      <Trash2 className="h-4 w-4 text-red-600" />
                    </Button>
                  </div>

                  <div className="space-y-2">
                    <Label className="text-sm font-medium">세부 항목 및 한도:</Label>
                    <div className="space-y-2 border-l-2 border-blue-200 pl-4">
                      {rule.subcategories.map((subcategory) => (
                        <div key={subcategory.id} className="flex items-center gap-2">
                          <Input
                            value={subcategory.name}
                            placeholder="세부 항목명"
                            className="flex-1 text-sm"
                            onChange={(event) =>
                              handleUpdateSubcategory(rule.id, subcategory.id, { name: event.target.value })
                            }
                          />
                          <div className="flex items-center gap-2">
                            <Input
                              type="number"
                              value={subcategory.percentageLimit}
                              className="w-20 text-right"
                              onChange={(event) =>
                                handleUpdateSubcategory(rule.id, subcategory.id, {
                                  percentageLimit: Number(event.target.value),
                                })
                              }
                            />
                            <span className="w-4 text-sm text-gray-600">%</span>
                          </div>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-8 w-8"
                            onClick={() => handleRemoveSubcategory(rule.id, subcategory.id)}
                          >
                            <Trash2 className="h-3 w-3 text-red-600" />
                          </Button>
                        </div>
                      ))}
                      <Button variant="outline" size="sm" className="mt-2 gap-2" onClick={() => handleAddSubcategory(rule.id)}>
                        <Plus className="h-3 w-3" />
                        세부 항목 추가
                      </Button>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label className="text-sm font-medium">허용 지출 유형:</Label>
                    <div className="pl-4 text-sm text-gray-600">
                      {rule.allowedTypes.length > 0 ? rule.allowedTypes.join(", ") : "세부 항목명 기반으로 저장됩니다."}
                    </div>
                  </div>

                  <div className="flex items-center justify-between border-t border-gray-200 pt-3">
                    <span className="text-sm text-gray-600">세부 항목 한도 합계:</span>
                    <Badge
                      variant="outline"
                      className="border-blue-200 bg-blue-50 text-blue-700"
                    >
                      {totalSubcategoryLimit}%
                    </Badge>
                  </div>
                </div>
              );
            })}
          </div>

          <Separator className="my-4" />

          <div className="rounded-lg border border-blue-200 bg-blue-50 p-4">
            <h4 className="mb-2 text-sm font-medium text-blue-900">규칙 요약</h4>
            <div className="space-y-1 text-sm text-blue-800">
              <div>총 카테고리 수: {budgetRules.length}개</div>
              <div>
                총 세부 항목 수: {budgetRules.reduce((sum, rule) => sum + rule.subcategories.length, 0)}개
              </div>
              <div>각 카테고리별 한도는 프로젝트 운영 기준에 맞게 자유롭게 설정할 수 있습니다.</div>
            </div>
          </div>

          <div className="flex justify-end">
            <Button onClick={() => void handleSaveBudgetRules()}>예산 규칙 저장</Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
