import { useEffect, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "../components/ui/card";
import { Progress } from "../components/ui/progress";
import { Badge } from "../components/ui/badge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../components/ui/table";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from "recharts";
import { TrendingUp, AlertCircle, CheckCircle, XCircle, Clock } from "lucide-react";
import { api } from "../api/client";
import { useProjectContext } from "../context/ProjectContext";
import type { BudgetRule, DashboardOverview, Expense, ValidationResult } from "../api/types";

export function Dashboard() {
  const { selectedProjectId } = useProjectContext();
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [budgetRules, setBudgetRules] = useState<BudgetRule[]>([]);
  const [recentReceipts, setRecentReceipts] = useState<Expense[]>([]);
  const [validationAlerts, setValidationAlerts] = useState<ValidationResult[]>([]);

  useEffect(() => {
    if (!selectedProjectId) return;
    const load = async () => {
      const [overviewData, budgetRuleData, expensesData, validationsData] = await Promise.all([
        api.getDashboardOverview(selectedProjectId),
        api.getBudgetRules(selectedProjectId),
        api.getExpenses(selectedProjectId),
        api.getValidations(selectedProjectId),
      ]);
      setOverview(overviewData);
      setBudgetRules(budgetRuleData);
      setRecentReceipts(expensesData.slice(0, 5));
      setValidationAlerts(validationsData.slice(0, 4));
    };
    void load();
  }, [selectedProjectId]);

  const executionRate =
    overview && overview.totalBudget > 0
      ? Math.round((overview.usedBudget / overview.totalBudget) * 100)
      : 0;

  const summaryData = [
    {
      title: "총 예산",
      value: `₩${(overview?.totalBudget ?? 0).toLocaleString()}`,
      subtext: "승인된 금액",
      bgColor: "bg-blue-50",
      textColor: "text-blue-700",
      borderColor: "border-blue-200",
    },
    {
      title: "집행 금액",
      value: `₩${(overview?.usedBudget ?? 0).toLocaleString()}`,
      subtext: `지출 ${overview?.expenseCount ?? 0}건`,
      bgColor: "bg-green-50",
      textColor: "text-green-700",
      borderColor: "border-green-200",
      icon: TrendingUp,
    },
    {
      title: "잔액",
      value: `₩${(overview?.remainingBudget ?? 0).toLocaleString()}`,
      subtext: `검증 ${overview?.validationCount ?? 0}건`,
      bgColor: "bg-gray-50",
      textColor: "text-gray-700",
      borderColor: "border-gray-200",
    },
    {
      title: "집행률",
      value: `${executionRate}%`,
      subtext: `경고 ${overview?.warningCount ?? 0}건 / 오류 ${overview?.errorCount ?? 0}건`,
      bgColor: "bg-yellow-50",
      textColor: "text-yellow-700",
      borderColor: "border-yellow-200",
    },
  ];

  const budgetByCategory = Object.values(
    budgetRules.reduce<
      Record<
        string,
        {
          category: string;
          allocated: number;
          used: number;
          remaining: number;
          rate: number;
          status: "valid" | "warning" | "error";
          limit: number;
        }
      >
    >((acc, rule) => {
      const current = acc[rule.category] ?? {
        category: rule.category,
        allocated: 0,
        used: 0,
        remaining: 0,
        rate: 0,
        status: "valid" as const,
        limit: 0,
      };
      const used = recentReceipts
        .filter((expense) => expense.category === rule.category)
        .reduce((sum, expense) => sum + expense.amount, 0);
      const allocated = current.allocated + rule.allocated;
      const remaining = allocated - used;
      const rate = allocated > 0 ? Math.round((used / allocated) * 100) : 0;
      const status =
        rule.status === "ERROR"
          ? "error"
          : rule.status === "WARNING"
          ? "warning"
          : current.status;

      acc[rule.category] = {
        category: rule.category,
        allocated,
        used,
        remaining,
        rate,
        status,
        limit: Math.max(current.limit, rule.percentage),
      };
      return acc;
    }, {})
  );

  const chartData = budgetByCategory.map((item) => ({
    id: item.category,
    category: item.category,
    allocated: item.allocated,
    used: item.used,
  }));

  const totalAllocated = budgetByCategory.reduce(
    (sum, item) => sum + item.allocated,
    0
  );
  const totalUsed = budgetByCategory.reduce((sum, item) => sum + item.used, 0);

  return (
    <div className="space-y-6 bg-gray-50 p-4 md:p-6">
      {/* Page Header */}
      <div className="mb-6">
        <h1 className="text-2xl font-semibold text-gray-900">대시보드</h1>
        <p className="text-sm text-gray-600 mt-1">
          프로젝트 진행 현황 및 주요 지표
        </p>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {summaryData.map((item) => {
          const Icon = item.icon;
          return (
            <Card key={item.title} className={`${item.borderColor} border-2`}>
              <CardContent className={`pt-6 ${item.bgColor}`}>
                <div className="flex items-start justify-between">
                  <div className="flex-1">
                    <p
                      className={`text-sm font-medium ${item.textColor} opacity-80`}
                    >
                      {item.title}
                    </p>
                    <p className={`text-3xl font-bold ${item.textColor} mt-2`}>
                      {item.value}
                    </p>
                    <p className="text-xs text-gray-600 mt-2">
                      {item.subtext}
                    </p>
                  </div>
                  {Icon && (
                    <Icon className={`w-8 h-8 ${item.textColor} opacity-30`} />
                  )}
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      {/* Budget vs Actual Progress Bars */}
      <Card>
        <CardHeader>
          <CardTitle>카테고리별 예산 대비 실제 사용</CardTitle>
        </CardHeader>
        <CardContent className="space-y-6">
          {budgetByCategory.map((item) => {
            const usedPercentage = (item.used / item.allocated) * 100;
            const overLimit = usedPercentage > 90;
            const underUsed = usedPercentage < 50;

            return (
              <div key={item.category} className="space-y-2">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <span className="min-w-0 text-sm font-medium text-gray-900 sm:min-w-[160px]">
                      {item.category}
                    </span>
                    <Badge
                      variant="outline"
                      className={`text-xs ${
                        item.status === "valid"
                          ? "bg-green-50 text-green-700 border-green-200"
                          : item.status === "warning"
                          ? "bg-yellow-50 text-yellow-700 border-yellow-200"
                          : "bg-red-50 text-red-700 border-red-200"
                      }`}
                    >
                      {item.rate}%
                    </Badge>
                  </div>
                  <div className="hidden items-center gap-6 text-sm sm:flex">
                    <span className="text-gray-600">
                      ₩{item.used.toLocaleString()} / ₩{item.allocated.toLocaleString()}
                    </span>
                    <span
                      className={`font-medium min-w-[100px] text-right ${
                        overLimit ? "text-red-600" : underUsed ? "text-yellow-600" : "text-gray-900"
                      }`}
                    >
                      ₩{item.remaining.toLocaleString()}
                    </span>
                  </div>
                </div>
                <div className="relative">
                  <Progress
                    value={item.rate}
                    className={`h-3 ${
                      overLimit
                        ? "[&>div]:bg-red-500"
                        : underUsed
                        ? "[&>div]:bg-yellow-500"
                        : "[&>div]:bg-green-500"
                    }`}
                  />
                  {item.limit && (
                    <div
                      className="absolute top-0 w-0.5 h-3 bg-gray-400"
                      style={{ left: `${item.limit}%` }}
                    />
                  )}
                </div>
              </div>
            );
          })}

          {/* Total Summary */}
          <div className="pt-4 border-t-2 border-gray-300">
            <div className="mb-2 flex items-center justify-between gap-3">
              <span className="text-sm font-bold text-gray-900">합계</span>
              <div className="hidden items-center gap-6 text-sm font-bold sm:flex">
                <span className="text-gray-900">
                  ₩{totalUsed.toLocaleString()} / ₩{totalAllocated.toLocaleString()}
                </span>
                <span className="text-blue-600 min-w-[100px] text-right">
                  ₩{(totalAllocated - totalUsed).toLocaleString()}
                </span>
              </div>
            </div>
            <Progress
              value={(totalUsed / totalAllocated) * 100}
              className="h-4 [&>div]:bg-blue-600"
            />
          </div>
        </CardContent>
      </Card>

      {/* Chart and Table Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Category-wise Spending Chart */}
        <Card>
          <CardHeader>
            <CardTitle>카테고리별 지출</CardTitle>
          </CardHeader>
          <CardContent>
            <ResponsiveContainer width="100%" height={320}>
              <BarChart
                data={chartData}
                margin={{ top: 20, right: 30, left: 20, bottom: 5 }}
              >
                <CartesianGrid strokeDasharray="3 3" stroke="#e5e7eb" />
                <XAxis
                  dataKey="category"
                  tick={{ fontSize: 12 }}
                  angle={-15}
                  textAnchor="end"
                  height={60}
                />
                <YAxis tick={{ fontSize: 12 }} />
                <Tooltip
                  formatter={(value: number) => `₩${value.toLocaleString()}`}
                  contentStyle={{
                    backgroundColor: "white",
                    border: "1px solid #e5e7eb",
                    borderRadius: "8px",
                  }}
                />
                <Legend />
                <Bar
                  key="allocated-bar"
                  dataKey="allocated"
                  fill="#93c5fd"
                  name="배정액"
                  radius={[4, 4, 0, 0]}
                />
                <Bar
                  key="used-bar"
                  dataKey="used"
                  fill="#3b82f6"
                  name="사용액"
                  radius={[4, 4, 0, 0]}
                />
              </BarChart>
            </ResponsiveContainer>
          </CardContent>
        </Card>

        {/* Category Table */}
        <Card>
          <CardHeader>
            <CardTitle>예산 배분 요약</CardTitle>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>카테고리</TableHead>
                  <TableHead className="text-right">배정액</TableHead>
                  <TableHead className="text-right">사용액</TableHead>
                  <TableHead className="text-right">잔액</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {budgetByCategory.map((row) => (
                  <TableRow key={row.category}>
                    <TableCell className="font-medium text-sm">
                      {row.category}
                    </TableCell>
                    <TableCell className="text-right text-sm">
                      ₩{row.allocated.toLocaleString()}
                    </TableCell>
                    <TableCell className="text-right text-sm">
                      ₩{row.used.toLocaleString()}
                    </TableCell>
                    <TableCell className="text-right text-sm">
                      <span
                        className={
                          row.rate > 90
                            ? "text-red-600 font-semibold"
                            : row.rate < 50
                            ? "text-yellow-600"
                            : "text-gray-900"
                        }
                      >
                        ₩{row.remaining.toLocaleString()}
                      </span>
                    </TableCell>
                  </TableRow>
                ))}
                <TableRow className="bg-gray-50 font-semibold border-t-2">
                  <TableCell>합계</TableCell>
                  <TableCell className="text-right">
                    ₩{totalAllocated.toLocaleString()}
                  </TableCell>
                  <TableCell className="text-right">
                    ₩{totalUsed.toLocaleString()}
                  </TableCell>
                  <TableCell className="text-right text-blue-600">
                    ₩{(totalAllocated - totalUsed).toLocaleString()}
                  </TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      </div>

      {/* Recent Activity Section */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Recent Receipts */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>최근 영수증</span>
              <Badge variant="outline" className="font-normal">
                {recentReceipts.length}건
              </Badge>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {recentReceipts.map((receipt) => (
                <div
                  key={receipt.id}
                  className="flex items-center justify-between p-3 rounded-lg border border-gray-200 hover:bg-gray-50 transition-colors"
                >
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono text-gray-500">
                        {receipt.expenseCode}
                      </span>
                      <Badge
                        variant="outline"
                        className={`text-xs ${
                          receipt.status === "PROCESSED"
                            ? "bg-green-50 text-green-700 border-green-200"
                            : "bg-yellow-50 text-yellow-700 border-yellow-200"
                        }`}
                      >
                        {receipt.status === "PROCESSED" ? (
                          <CheckCircle className="w-3 h-3 mr-1" />
                        ) : (
                          <Clock className="w-3 h-3 mr-1" />
                        )}
                        {receipt.status === "PROCESSED" ? "처리됨" : "대기중"}
                      </Badge>
                    </div>
                    <div className="font-medium text-sm text-gray-900 mt-1 truncate">
                      {receipt.itemName}
                    </div>
                    <div className="text-xs text-gray-500 mt-1">
                      {receipt.vendor} · {receipt.paymentDate}
                    </div>
                  </div>
                  <div className="text-right ml-4">
                    <div className="font-semibold text-sm text-gray-900">
                      ₩{receipt.amount.toLocaleString()}
                    </div>
                    <div className="text-xs text-gray-500 mt-1">
                      {receipt.category}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>

        {/* Recent Validation Alerts */}
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              <span>검증 알림</span>
              <div className="flex gap-2">
                <Badge
                  variant="outline"
                  className="bg-red-50 text-red-700 border-red-200 text-xs"
                >
                  오류 {validationAlerts.filter((alert) => alert.severity === "ERROR").length}건
                </Badge>
                <Badge
                  variant="outline"
                  className="bg-yellow-50 text-yellow-700 border-yellow-200 text-xs"
                >
                  경고 {validationAlerts.filter((alert) => alert.severity === "WARNING").length}건
                </Badge>
              </div>
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {validationAlerts.map((alert) => (
                <div
                  key={alert.id}
                  className={`flex items-start gap-3 p-3 rounded-lg border ${
                    alert.severity === "ERROR"
                      ? "border-red-200 bg-red-50"
                      : alert.severity === "WARNING"
                      ? "border-yellow-200 bg-yellow-50"
                      : "border-green-200 bg-green-50"
                  }`}
                >
                  <div className="flex-shrink-0 mt-0.5">
                    {alert.severity === "ERROR" ? (
                      <XCircle className="w-5 h-5 text-red-600" />
                    ) : alert.severity === "WARNING" ? (
                      <AlertCircle className="w-5 h-5 text-yellow-600" />
                    ) : (
                      <CheckCircle className="w-5 h-5 text-green-600" />
                    )}
                  </div>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                      <span
                        className={`text-xs font-semibold ${
                          alert.severity === "ERROR"
                            ? "text-red-900"
                            : alert.severity === "WARNING"
                            ? "text-yellow-900"
                            : "text-green-900"
                        }`}
                      >
                        {alert.category}
                      </span>
                      <span className="text-xs text-gray-500">
                        {alert.date}
                      </span>
                    </div>
                    <p
                      className={`text-sm ${
                        alert.severity === "ERROR"
                          ? "text-red-800"
                          : alert.severity === "WARNING"
                          ? "text-yellow-800"
                          : "text-green-800"
                      }`}
                    >
                      {alert.description}
                    </p>
                  </div>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
