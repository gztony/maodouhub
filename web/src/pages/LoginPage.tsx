import { useState } from "react";
import { login, register, setToken } from "../lib/api";

export function LoginPage({ onLogin }: { onLogin: () => void }) {
  const [userId, setUserId] = useState("");
  const [password, setPassword] = useState("");
  const [userName, setUserName] = useState("");
  const [isRegister, setIsRegister] = useState(false);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setLoading(true);

    try {
      if (isRegister) {
        const res = await register(userId, userName || userId, password);
        if (!res.ok) { setError(res.error); return; }
        // 注册成功后自动登录
        const loginRes = await login(userId, password);
        if (!loginRes.ok) { setError(loginRes.error); return; }
        setToken(loginRes.token);
      } else {
        const res = await login(userId, password);
        if (!res.ok) { setError(res.error); return; }
        setToken(res.token);
      }
      onLogin();
    } catch {
      setError("网络错误，请检查服务是否运行");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-logo">🫛</div>
        <h1>小毛豆</h1>
        <p className="login-subtitle">政务办公移动助理</p>

        <form onSubmit={handleSubmit}>
          <input
            type="text"
            placeholder="用户 ID"
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
            autoFocus
          />
          {isRegister && (
            <input
              type="text"
              placeholder="姓名"
              value={userName}
              onChange={(e) => setUserName(e.target.value)}
            />
          )}
          <input
            type="password"
            placeholder="密码"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {error && <div className="login-error">{error}</div>}
          <button type="submit" disabled={!userId || !password || loading}>
            {loading ? "请稍候..." : isRegister ? "注册" : "登录"}
          </button>
        </form>

        <button className="login-switch" onClick={() => { setIsRegister(!isRegister); setError(""); }}>
          {isRegister ? "已有账号？去登录" : "没有账号？去注册"}
        </button>
      </div>
    </div>
  );
}
