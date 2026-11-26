import { request, type ApiResult, type Row, type User } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { dateText } from "@/lib/utils";
import { useCallback, useState } from "react";

const labels: Record<string, string> = {
  itemId: "품목 ID",
  itemName: "아이템",
  category: "분류",
  addedAt: "등록일",
};
const columns = ["itemId", "itemName", "category", "addedAt"];

type FavoritesProps = {
  user: User | null;
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
  onRequireLogin: () => void;
  post: (
    path: string,
    body: Row,
    onSuccess?: () => void | Promise<void>,
  ) => Promise<void>;
};
export default function Favorites({
  user,
  logout,
  loading,
  setLoading,
  setError,
  onRequireLogin,
  post,
}: FavoritesProps) {
  const [view, setView] = useState("all");
  const [data, setData] = useState<ApiResult>({ rows: [] });
  const [specifiedMemberId, setSpecifiedMemberId] = useState(
    `${user.memberId}`,
  );
  const load = useCallback(async () => {
    if (!user) {
      onRequireLogin();
      return;
    }

    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();

      params.set("view", view);
      params.set("memberId", `${specifiedMemberId}`);
      const result = await request(`/favorites?${params}`);
      setData(result);
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
    setData,
    setLoading,
    setError,
    logout,
    onRequireLogin,
    view,
    specifiedMemberId,
  ]);

  const MemberFilter = useCallback(
    ({ value, onChange }: { value: string; onChange: (v: string) => void }) => {
      return (
        <div className="space-y-2">
          <Label>대상 회원 ID</Label>
          <Input
            className="w-44"
            value={value}
            onChange={(e) => onChange(e.target.value)}
          />
        </div>
      );
    },
    [],
  );

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
                {specifiedMemberId === `${user.memberId}` && <th>관리</th>}
              </tr>
            </thead>
            <tbody>
              {rowsToShow.map((row) => (
                <tr key={`${row.itemId}`}>
                  {cols.map((c) => (
                    <td key={c}>{row[c]}</td>
                  ))}

                  {specifiedMemberId === `${user.memberId}` && (
                    <td>
                      <Button
                        size="sm"
                        variant="outline"
                        disabled={loading}
                        onClick={() =>
                          void post(
                            "/favorites/remove",
                            {
                              itemId: row.itemId,
                              memberId: user.memberId,
                            },
                            load,
                          )
                        }
                      >
                        삭제
                      </Button>
                    </td>
                  )}
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
            disabled={loading}
          >
            <option value="all">전체</option>
            <option value="available">구매 가능</option>
          </select>
        </div>
        {user?.admin && (
          <MemberFilter
            value={specifiedMemberId}
            onChange={setSpecifiedMemberId}
          />
        )}
        <Button onClick={() => void load()} disabled={loading}>
          조회
        </Button>
      </div>
      <Card>
        <CardContent className="p-0">{table(data.rows, columns)}</CardContent>
      </Card>
      <p className="text-xs text-muted-foreground">
        관심 품목 추가는 거래소 매물 상세에서 할 수 있습니다.
        {data.filters?.asOf &&
          ` 조회 기준시각: ${dateText(data.filters?.asOf)}`}
      </p>
    </>
  );
}
