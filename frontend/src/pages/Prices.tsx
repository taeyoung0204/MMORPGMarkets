import { request, type Row } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { labels } from "@/lib/utils";
import { useCallback, useState } from "react";

const columns = [
  "itemId",
  "itemName",
  "tradeDate",
  "averagePrice",
  "soldBundles",
];

type PricesProps = {
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
};
export default function Prices({
  logout,
  loading,
  setLoading,
  setError,
}: PricesProps) {
  const [itemId, setItemId] = useState("");
  const [from, setFrom] = useState("");
  const [until, setUntil] = useState("");
  const [rows, setRows] = useState<Row[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();
      if (itemId) params.set("itemId", itemId);
      if (from) params.set("from", from);
      if (until) params.set("until", until);
      const result = await request(
        `/market/prices${params.size ? `?${params}` : ""}`,
      );
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
    setRows,
    setLoading,
    setError,
    logout,
    itemId,
    from,
    until,
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
              {rowsToShow.map((row, i) => (
                <tr
                  key={`${row.itemId}-${row.tradeDate}-${row.soldBundles}-${i}`}
                >
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
      <Card>
        <CardContent className="grid gap-4 p-4 md:grid-cols-4 md:items-end">
          <div className="space-y-2">
            <Label>품목 ID</Label>
            <Input
              value={itemId}
              onChange={(e) => setItemId(e.target.value)}
              placeholder="선택 사항"
            />
          </div>
          <div className="space-y-2">
            <Label>시작 시각</Label>
            <Input
              type="datetime-local"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>종료 시각</Label>
            <Input
              type="datetime-local"
              value={until}
              onChange={(e) => setUntil(e.target.value)}
            />
          </div>
          <Button onClick={() => void load()} disabled={loading}>
            시세 조회
          </Button>
        </CardContent>
      </Card>
      <Card>
        <CardContent className="p-0">{table(rows, columns)}</CardContent>
      </Card>
    </>
  );
}
