import { request, type Row, type User } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { labels, MemberFilter } from "@/lib/utils";
import { useCallback, useState } from "react";

const statusColumns = [
  "orderId",
  "buyerId",
  "itemId",
  "requestedBundles",
  "confirmedAvgPrice",
  "orderStatus",
];
const summaryColumns = [
  "orderId",
  "buyerNickname",
  "itemName",
  "orderStatus",
  "confirmedAvgPrice",
  "requestedBundles",
  "filledBundles",
  "unfilledBundles",
  "maxPayment",
  "actualPayment",
  "refund",
];

const statuses = ["전량체결", "부분체결", "미체결"];

type OrdersProps = {
  user: User;
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
};
export default function Orders({
  user,
  logout,
  loading,
  setLoading,
  setError,
}: OrdersProps) {
  const [view, setView] = useState("status");
  const [rows, setRows] = useState<Row[]>([]);
  const [specifiedMemberId, setSpecifiedMemberId] = useState(
    `${user.memberId}`,
  );
  const [selectedStatuses, setSelectedStatuses] = useState<string[]>([
    "부분체결",
    "미체결",
  ]);
  const [orderId, setOrderId] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();
      params.set("view", view);
      if (view === "status")
        selectedStatuses.forEach((s) => params.append("status", s));
      if (view === "summary" && orderId) params.set("orderId", orderId);
      if (user.admin && specifiedMemberId)
        params.set("memberId", specifiedMemberId);

      const result = await request(`/me/orders?${params}`);
      setRows(result.rows ?? []);
    } catch (e) {
      const error = e as Error & { status?: number };

      if (error.status === 401) {
        logout();
      } else {
        setError((e as Error).message);
      }
    } finally {
      setLoading(false);
    }
  }, [
    user,
    setRows,
    setLoading,
    setError,
    logout,
    specifiedMemberId,
    view,
    selectedStatuses,
    orderId,
  ]);

  const table = useCallback(
    (rowsToShow: Row[], cols: string[]) => {
      if (!rowsToShow.length)
        return <div className="empty-state">조회 결과가 없습니다.</div>;
      return (
        <div className="overflow-x-auto">
          <table className="data-table">
            <thead>
              <tr>
                {cols.map((c) => (
                  <th key={c}>{labels[c]}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rowsToShow.map((row) => (
                <tr key={`${row.orderId}`}>
                  {cols.map((c) => (
                    <td key={c}>{row[c]}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    },
    [loading],
  );

  return (
    <>
      <div className="flex flex-wrap items-end gap-3">
        <div className="space-y-2">
          <Label>조회 유형</Label>
          <select
            className="h-10 rounded-md border bg-background px-3 text-sm"
            value={view}
            onChange={(e) => setView(e.target.value)}
          >
            <option value="status">주문 상태</option>
            <option value="summary">주문별 체결</option>
          </select>
        </div>
        {view === "summary" && (
          <div className="space-y-2">
            <Label>주문 ID (선택)</Label>
            <Input
              value={orderId}
              onChange={(e) => setOrderId(e.target.value)}
              placeholder="전체 조회"
              disabled={loading}
            />
          </div>
        )}
        {user.admin && (
          <MemberFilter
            value={specifiedMemberId}
            onChange={setSpecifiedMemberId}
            loading={loading}
          />
        )}
        <Button onClick={() => void load()} disabled={loading}>
          조회
        </Button>
      </div>
      {view === "status" && (
        <div className="flex flex-wrap gap-4">
          {statuses.map((s) => (
            <label key={s} className="flex items-center gap-2 text-sm">
              <input
                type="checkbox"
                checked={selectedStatuses.includes(s)}
                onChange={(e) =>
                  setSelectedStatuses((old) =>
                    e.target.checked ? [...old, s] : old.filter((x) => x !== s),
                  )
                }
              />
              {s}
            </label>
          ))}
        </div>
      )}
      <Card>
        <CardContent className="p-0">
          {table(rows, view === "status" ? statusColumns : summaryColumns)}
        </CardContent>
      </Card>
    </>
  );
}
