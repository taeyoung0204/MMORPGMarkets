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

export { money, dateText, MemberFilter };
