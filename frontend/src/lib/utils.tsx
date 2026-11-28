import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export { cn } from "cn";

const money = (v?: number) => `${Number(v ?? 0).toLocaleString("ko-KR")} G`;
const dateText = (v?: string) => (v ? v : "-");
function MemberFilter({
  value,
  onChange,
  loading,
}: {
  value: string;
  onChange: (v: string) => void;
  loading: boolean;
}) {
  return (
    <div className="space-y-2">
      <Label>대상 회원 ID</Label>
      <Input
        className="w-44"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={loading}
      />
    </div>
  );
}

const labels: Record<string, string> = {
  itemId: "품목 ID",
  itemName: "아이템",
  category: "분류",
  addedAt: "등록일",
  ownedCount: "보유 수량",
  memberId: "회원 ID",
  nickname: "닉네임",
  soldBundles: "판매 묶음",
  grossSales: "총 판매액",
  totalFee: "수수료",
  netSales: "정산액",
  orderId: "주문 ID",
  buyerId: "구매자 ID",
  requestedBundles: "요청 묶음",
  confirmedAvgPrice: "확정 평균가",
  orderStatus: "주문 상태",
  buyerNickname: "구매자",
  filledBundles: "체결 묶음",
  unfilledBundles: "미체결 묶음",
  maxPayment: "최대 결제액",
  actualPayment: "실제 결제액",
  refund: "환불액",
  goldBalance: "보유 골드",
  tradeDate: "거래일",
  averagePrice: "평균 가격",
};

export { money, dateText, MemberFilter, labels };
