import { useCallback, useEffect, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import {
  Activity,
  ArrowDownUp,
  Box,
  Heart,
  LogOut,
  Package,
  ShieldCheck,
  ShoppingBag,
  UserRound,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import "./index.css";

type User = { memberId: number; loginId: string; admin: boolean };
type Page =
  | "market"
  | "favorites"
  | "inventory"
  | "orders"
  | "sales"
  | "profile"
  | "admin"
  | "prices";
type Row = Record<string, any>;
type ApiResult = {
  rows?: Row[];
  filters?: Row;
  item?: Row;
  profile?: Row;
  loginUser?: User | null;
  csrfToken?: string;
  message?: string;
  error?: string;
};

const API = "/mmorpg-markets/api";

async function request(
  path: string,
  options: RequestInit = {},
  csrf?: string,
): Promise<ApiResult> {
  const headers = new Headers(options.headers);
  headers.set("Accept", "application/json");
  if (options.body)
    headers.set("Content-Type", "application/json; charset=UTF-8");
  if (options.method === "POST" && csrf) headers.set("X-CSRF-Token", csrf);
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers,
    credentials: "same-origin",
  });
  const data = (await response.json().catch(() => ({}))) as ApiResult;
  if (!response.ok) {
    const error = new Error(
      data.error || `요청에 실패했습니다. (HTTP ${response.status})`,
    );
    (error as Error & { status?: number }).status = response.status;
    throw error;
  }
  return data;
}

function App() {
  const [page, setPage] = useState<Page>("market");
  const [user, setUser] = useState<User | null>(null);
  const [csrf, setCsrf] = useState("");
  const [sessionReady, setSessionReady] = useState(false);
  const [loginOpen, setLoginOpen] = useState(false);
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  const flash = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 3500);
  };

  const loadSession = useCallback(async () => {
    try {
      const result = await request("/session");
      setUser(result.loginUser ?? null);
      setCsrf(result.csrfToken ?? "");
      return result.csrfToken ?? null;
    } catch (e) {
      setError((e as Error).message);
      return null;
    } finally {
      setSessionReady(true);
    }
  }, []);

  useEffect(() => {
    void loadSession();
  }, [loadSession]);

  useEffect(() => {
    if (!sessionReady) return;
    if (!user) setLoginOpen(true);
  }, [sessionReady, user, page]);

  const navigate = (next: Page) => {
    setPage(next);
    setError("");
  };

  async function submitLogin(e: FormEvent) {
    e.preventDefault();
    setLoading(true);
    setError("");
  
    try {
      const token = csrf || (await loadSession());
      if (!token) return;
  
      const result = await request(
        "/login",
        {
          method: "POST",
          body: JSON.stringify({ loginId, password }),
        },
        token,
      );
  
      setUser(result.loginUser ?? null);
      setCsrf(result.csrfToken ?? "");
      setPassword("");
      setLoginOpen(false);
      flash(result.message || "로그인했습니다.");
      setPage("market");
    } catch (e) {
      const error = e as Error & { status?: number };
  
      if (error.status === 403) {
        setCsrf("");
        if (await loadSession()) {
          setError("세션을 갱신했습니다. 다시 로그인해 주세요.");
        }
      } else {
        setError(error.message);
      }
    } finally {
      setLoading(false);
    }
  }

  async function logout() {
    try {
      const result = await request(
        "/logout",
        { method: "POST", body: "{}" },
        csrf,
      );
      flash(result.message || "로그아웃했습니다.");
      setUser(null);
      setCsrf("");
      setPage("market");
      void loadSession();
    } catch (e) {
      setError((e as Error).message);
    }
  }

  const pageTitle: Record<Page, string> = {
    market: "아이템 거래소",
    favorites: "관심 목록",
    inventory: "내 인벤토리",
    orders: "주문 내역",
    sales: "판매 집계",
    profile: "내 정보",
    admin: "회원 관리",
    prices: "아이템 시세",
  };

  const navItems: {
    id: Page;
    label: string;
    icon: ReactNode;
    admin?: boolean;
  }[] = [
    { id: "market", label: "거래소", icon: <ShoppingBag size={16} /> },
    { id: "favorites", label: "관심 목록", icon: <Heart size={16} /> },
    { id: "inventory", label: "인벤토리", icon: <Package size={16} /> },
    { id: "orders", label: "주문 내역", icon: <Activity size={16} /> },
    { id: "sales", label: "판매 집계", icon: <ArrowDownUp size={16} /> },
    { id: "prices", label: "시세 조회", icon: <Activity size={16} /> },
    { id: "profile", label: "내 정보", icon: <UserRound size={16} /> },
    {
      id: "admin",
      label: "관리자",
      icon: <ShieldCheck size={16} />,
      admin: true,
    },
  ];

  if (!sessionReady)
    return <div className="loading-screen">세션을 확인하고 있습니다...</div>;

  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b bg-card">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <button
            className="flex items-center gap-2 font-semibold tracking-tight"
            onClick={() => navigate("market")}
          >
            <span className="grid size-8 place-items-center rounded-md bg-primary text-primary-foreground">
              <Box size={18} />
            </span>
            <span>게이머즈 MARKET</span>
          </button>
          <div className="flex items-center gap-3">
            {user ? (
              <>
                <span className="hidden text-sm text-muted-foreground sm:inline">
                  {user.loginId}
                  {user.admin ? " · 관리자" : ""}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => void logout()}
                >
                  <LogOut size={15} className="mr-2" />
                  로그아웃
                </Button>
              </>
            ) : (
              <Button size="sm" onClick={() => setLoginOpen(true)}>
                로그인
              </Button>
            )}
          </div>
        </div>
      </header>
      <div className="mx-auto grid max-w-7xl gap-6 px-4 py-6 sm:px-6 lg:grid-cols-[220px_minmax(0,1fr)]">
        <aside className="space-y-1">
          <div className="mb-3 px-3 text-xs font-medium uppercase tracking-wider text-muted-foreground">
            메뉴
          </div>
          {navItems
            .filter((n) => !(n.admin && !user?.admin))
            .map((n) => (
              <Button
                key={n.id}
                variant={page === n.id ? "secondary" : "ghost"}
                className="w-full justify-start"
                onClick={() => navigate(n.id)}
              >
                {n.icon}
                <span className="ml-2">{n.label}</span>
              </Button>
            ))}
        </aside>
        <main className="min-w-0 space-y-5">
          <div className="flex flex-wrap items-start justify-between gap-3">
            <div>
              <h1 className="text-2xl font-semibold tracking-tight">
                {pageTitle[page]}
              </h1>
            </div>
          </div>
          {notice && (
            <div className="rounded-md border bg-muted px-4 py-3 text-sm">
              {notice}
            </div>
          )}
          {error && (
            <div
              role="alert"
              className="rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm text-destructive"
            >
              {error}
            </div>
          )}
        </main>
      </div>

      <Dialog open={loginOpen} onOpenChange={setLoginOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>로그인</DialogTitle>
            <DialogDescription>
              게이머즈 거래소 계정으로 로그인하세요.
            </DialogDescription>
          </DialogHeader>
          <form className="space-y-4" onSubmit={submitLogin}>
            <div className="space-y-2">
              <Label htmlFor="login-id">로그인 ID</Label>
              <Input
                id="login-id"
                autoComplete="username"
                value={loginId}
                onChange={(e) => setLoginId(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="login-password">비밀번호</Label>
              <Input
                id="login-password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            <DialogFooter>
              <Button className="w-full" type="submit" disabled={loading}>
                로그인
              </Button>
            </DialogFooter>
            <div className="mt-2">
              {notice && (
                <div className="space-y-2 rounded-md border bg-muted px-4 py-3 text-sm">
                  {notice}
                </div>
              )}
              {error && (
                <div
                  role="alert"
                  className="space-y-2 rounded-md border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm text-destructive"
                >
                  {error}
                </div>
              )}
            </div>
          </form>
        </DialogContent>
      </Dialog>

      {loading && (
        <div className="fixed bottom-4 right-4 rounded-md border bg-card px-3 py-2 text-xs text-muted-foreground shadow-sm">
          처리 중...
        </div>
      )}
    </div>
  );
}

export default App;
