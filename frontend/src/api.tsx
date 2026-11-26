type User = { memberId: number; loginId: string; admin: boolean };

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
  return data as ApiResult;
}

export { type User, type Row, type ApiResult, request };
