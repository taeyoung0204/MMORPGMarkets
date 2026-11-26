import { request, type Row } from "@/api";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { money } from "@/lib/utils";
import { useCallback, useEffect, useState, type ReactNode } from "react";

function Info({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex items-center justify-between border-b py-2 text-sm">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-medium">{value ?? "-"}</span>
    </div>
  );
}

type ProfileProps = {
  logout: () => void;
  loading: boolean;
  setLoading: (value: boolean) => void;
  setError: (value: string) => void;
  post: (
    path: string,
    body: Row,
    onSuccess?: () => void | Promise<void>,
  ) => Promise<void>;
};
export default function Profile({
  logout,
  loading,
  setLoading,
  setError,
  post,
}: ProfileProps) {
  const [profile, setProfile] = useState<Row | null>(null);
  const [nickname, setNickname] = useState("");
  const [oldPassword, setOldPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");

  const load = useCallback(async () => {
    setLoading(true);
    setError("");

    try {
      const result = await request(`/me`);
      setProfile(result.profile ?? null);
      setNickname(String(result.profile?.nickname ?? ""));
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
    setProfile,
    setNickname,
    setLoading,
    setError,
    logout,
  ]);

  useEffect(() => {
    void load();
  }, []);

  return (
    <div className="grid gap-5 lg:grid-cols-2">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">회원 정보</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {profile ? (
            <>
              <Info label="회원 ID" value={profile.memberId} />
              <Info label="로그인 ID" value={profile.loginId} />
              <Info label="닉네임" value={profile.nickname} />
              <Info label="보유 골드" value={money(profile.goldBalance)} />
              <Info label="가입일" value={profile.joinedAt} />
            </>
          ) : (
            <p className="text-sm text-muted-foreground">
              정보를 불러오지 못했습니다.
            </p>
          )}
          <div className="space-y-2 pt-3">
            <Label>닉네임 변경</Label>
            <div className="flex gap-2">
              <Input
                value={nickname}
                onChange={(e) => setNickname(e.target.value)}
                placeholder="새 닉네임"
              />
              <Button
                disabled={loading || !nickname.trim()}
                onClick={() => void post("/me/nickname", { nickname }, load)}
              >
                변경
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>
      <Card>
        <CardHeader>
          <CardTitle className="text-base">비밀번호 변경</CardTitle>
        </CardHeader>
        <CardContent>
          <form
            className="space-y-3"
            onSubmit={(e) => {
              e.preventDefault();

              void post(
                "/me/password",
                { oldPassword, newPassword, confirmPassword },
                load,
              );
              setOldPassword("");
              setNewPassword("");
              setConfirmPassword("");
            }}
          >
            <div className="space-y-2">
              <Label>현재 비밀번호</Label>
              <Input
                type="password"
                value={oldPassword}
                onChange={(e) => setOldPassword(e.target.value)} required
              />
            </div>
            <div className="space-y-2">
              <Label>새 비밀번호</Label>
              <Input
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label>새 비밀번호 확인</Label>
              <Input
                type="password"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                required
              />
            </div>
            <Button type="submit" disabled={loading}>
              비밀번호 변경
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
