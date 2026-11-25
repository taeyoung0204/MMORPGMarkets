export { cn } from "cn";

const money = (v?: number) => `${Number(v ?? 0).toLocaleString("ko-KR")} G`;
const dateText = (v?: string) => (v ? v : "-");

export { money, dateText };
