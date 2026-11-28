import { request, type Row } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { labels } from "@/lib/utils";
import { useCallback, useState } from "react";

const columns = ["memberId", "nickname", "goldBalance"];

type AdminProps = {
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
};
export default function Admin({
  logout,
  loading,
  setLoading,
  setError,
}: AdminProps) {
  const [rows, setRows] = useState<Row[]>([]);
  const [minimumGold, setMinimumGold] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();

      if (minimumGold) params.set("minimumGold", minimumGold)
      const result = await request(`/admin${params.size ? `?${params}` : ""}`);
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
    minimumGold
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
                <tr key={`${row.memberId}`}>
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
          <Label>최소 보유 골드</Label>
          <Input
            value={minimumGold}
            onChange={(e) => setMinimumGold(e.target.value)}
            placeholder="제한 없음"
            disabled={loading}
          />
        </div>
        <Button onClick={() => void load()} disabled={loading}>
          검색
        </Button>
      </div>
      <Card>
        <CardContent className="p-0">{table(rows, columns)}</CardContent>
      </Card>
    </>
  );
}
