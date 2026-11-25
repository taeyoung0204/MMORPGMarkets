import { request, type Row, type User } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { MemberFilter } from "@/lib/utils";
import { useCallback, useState } from "react";

const labels: Record<string, string> = {
  itemId: "품목 ID",
  itemName: "아이템",
  ownedCount: "보유 수량",
};

type InventoryProps = {
  user: User;
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
};
export default function Inventory({
  user,
  logout,
  loading,
  setLoading,
  setError,
}: InventoryProps) {
  const [view, setView] = useState("all");
  const [specifiedMemberId, setSpecifiedMemberId] = useState(
    `${user.memberId}`,
  );
  const [rows, setRows] = useState<Row[]>([]);
  const [columns, setColumns] = useState(["itemId", "itemName", "ownedCount"]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();

      params.set("view", view);
      if (user.admin && specifiedMemberId)
        params.set("memberId", specifiedMemberId);
      const result = await request(`/me/inventory?${params}`);
      setRows(result.rows ?? []);
      setColumns([
        "itemId",
        "itemName",
        ...(view === "all" ? ["ownedCount"] : []),
      ]);
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
  }, [user, setRows, setLoading, setError, logout, view, specifiedMemberId]);

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
                <tr key={`${row.itemId}`}>
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
          <Label>보기</Label>
          <select
            className="h-10 rounded-md border bg-background px-3 text-sm"
            value={view}
            onChange={(e) => setView(e.target.value)}
          >
            <option value="all">전체 보유 품목</option>
            <option value="notFavorited">관심 미등록 품목</option>
          </select>
        </div>
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
      <Card>
        <CardContent className="p-0">{table(rows, columns)}</CardContent>
      </Card>
    </>
  );
}
