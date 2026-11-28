import { request, type Row, type User } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { labels, MemberFilter } from "@/lib/utils";
import { useCallback, useState } from "react";

const columns = [
  "memberId",
  "nickname",
  "soldBundles",
  "grossSales",
  "totalFee",
  "netSales",
];

type SalesProps = {
  user: User;
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
};
export default function Sales({
  user,
  logout,
  loading,
  setLoading,
  setError,
}: SalesProps) {
  const [rows, setRows] = useState<Row[]>([]);
  const [specifiedMemberId, setSpecifiedMemberId] = useState(
    `${user.memberId}`,
  );

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();
      if (user.admin && specifiedMemberId)
        params.set("memberId", specifiedMemberId);
      const result = await request(
        `/me/sales${params.size ? `?${params}` : ""}`,
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
    user,
    setRows,
    setLoading,
    setError,
    logout,
    specifiedMemberId,
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
      <div className="flex flex-wrap items-end gap-3">
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
