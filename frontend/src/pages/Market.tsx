import { useCallback, useEffect, useState } from "react";
import { Search } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent } from "@/components/ui/card";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { type User, type Row, type ApiResult, request } from "@/api";

type MarketProps = {
  user: User | null;
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
  onRequireLogin: () => void;
};

const categories = ["강화 재료", "각인서", "전투 용품", "생활", "모험의 서"];

const money = (v?: number) => `${Number(v ?? 0).toLocaleString("ko-KR")} G`;

export default function Market({
  user,
  logout,
  loading,
  setLoading,
  setError,
  onRequireLogin,
}: MarketProps) {
  const [category, setCategory] = useState("강화 재료");
  const [name, setName] = useState("");
  const [data, setData] = useState<ApiResult>({ rows: [] });

  const [detail, setDetail] = useState<Row | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const rows = data.rows ?? [];

  const load = useCallback(async () => {
    if (!user) {
      onRequireLogin();
      return;
    }

    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams();
      params.set("category", category);

      if (name.trim()) {
        params.set("name", name.trim());
      }

      const result = await request(`/market?${params}`);
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
    category,
    name,
    setData,
    setLoading,
    setError,
    logout,
    onRequireLogin,
  ]);

  useEffect(() => {
    setData({ rows: [] });
    setDetail(null);
    setDetailOpen(false);
  }, [user]);

  async function openItem(id: number) {
    setLoading(true);
    setError("");

    try {
      const params = new URLSearchParams({
        itemId: String(id),
      });

      const result = await request(`/market/item?${params}`);

      setDetail({
        ...(result.item ?? {}),
        rows: result.rows ?? [],
        asOf: result.filters?.asOf,
      });

      setDetailOpen(true);
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
  }

  return (
    <>
      {/* 검색 조건 */}
      <Card>
        <CardContent className="grid gap-4 p-4 md:grid-cols-[190px_minmax(0,1fr)_auto] md:items-end">
          <div className="space-y-2">
            <Label htmlFor="category">분류</Label>
            <select
              id="category"
              className="h-10 w-full rounded-md border bg-background px-3 text-sm"
              value={category}
              onChange={(e) => setCategory(e.target.value)}
            >
              {categories.map((c) => (
                <option key={c}>{c}</option>
              ))}
            </select>
          </div>

          <div className="space-y-2">
            <Label htmlFor="item-name">아이템 이름</Label>
            <div className="relative">
              <Search
                size={16}
                className="absolute left-3 top-3 text-muted-foreground"
              />
              <Input
                id="item-name"
                className="pl-9"
                placeholder="검색어를 입력하세요"
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && void load()}
              />
            </div>
          </div>

          <Button onClick={() => void load()} disabled={loading}>
            검색
          </Button>
        </CardContent>
      </Card>

      {/* 검색 결과 */}
      <div className="flex items-center justify-between">
        <h2 className="text-base font-semibold">검색 결과</h2>
        <span className="text-xs text-muted-foreground">
          {rows.length}개 품목
        </span>
      </div>

      <Card>
        <CardContent className="p-0">
          {rows.length ? (
            <div className="overflow-x-auto">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>품목 ID</th>
                    <th>아이템 이름</th>
                    <th>분류</th>
                    <th>묶음 크기</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.itemId}>
                      <td>{row.itemId}</td>
                      <td className="font-medium">{row.itemName}</td>
                      <td>
                        <Badge variant="secondary">{row.category}</Badge>
                      </td>
                      <td>{Number(row.bundleSize).toLocaleString("ko-KR")}</td>
                      <td>
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => void openItem(row.itemId)}
                        >
                          매물 보기
                        </Button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="empty-state">
              {loading ? "조회 중..." : "조회 결과가 없습니다."}
            </div>
          )}
        </CardContent>
      </Card>

      {/* 매물 상세 Dialog */}
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{detail?.itemName ?? "매물 상세"}</DialogTitle>
            <DialogDescription>
              품목 ID {detail?.itemId} · 묶음 크기 {detail?.bundleSize} · 조회
              기준시각 {detail?.asOf ?? "-"}
            </DialogDescription>
          </DialogHeader>

          <div className="overflow-x-auto">
            <table className="data-table">
              <thead>
                <tr>
                  <th>매물 ID</th>
                  <th>판매자</th>
                  <th>묶음 가격</th>
                  <th>남은 묶음</th>
                  <th>등록 시각</th>
                </tr>
              </thead>
              <tbody>
                {(detail?.rows ?? []).map((r: Row) => (
                  <tr key={r.listingId}>
                    <td>{r.listingId}</td>
                    <td>{r.sellerNickname}</td>
                    <td>{money(r.bundlePrice)}</td>
                    <td>{r.remainingBundles}</td>
                    <td>{r.listedAt}</td>
                  </tr>
                ))}
              </tbody>
            </table>

            {!detail?.rows?.length && (
              <div className="empty-state">매물 없음</div>
            )}
          </div>

          <DialogFooter className="items-center sm:justify-between">
            <Button variant="outline" onClick={() => setDetailOpen(false)}>
              닫기
            </Button>

            <Button disabled={!user || loading} onClick={() => {}}>
              관심 등록
            </Button>

            <Button disabled={!user || loading} onClick={() => {}}>
              구매하기
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
