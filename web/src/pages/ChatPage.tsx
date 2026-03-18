import { useState, useEffect, useRef, useCallback } from "react";
import { sendMessage, getMessages, pollMessages, getPcStatus, uploadFile, getFileDownloadUrl, setToken } from "../lib/api";
import type { ChatMessage, StreamingState } from "../lib/types";

const POLL_INTERVAL = 3000; // 3 秒轮询

export function ChatPage() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [sending, setSending] = useState(false);
  const [pcOnline, setPcOnline] = useState<boolean | null>(null);
  const [streaming, setStreaming] = useState<StreamingState | null>(null);
  const [uploading, setUploading] = useState(false);
  const [pendingFiles, setPendingFiles] = useState<Array<{ fileId: string; fileName: string }>>([]);

  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const pollTimerRef = useRef<number | null>(null);

  // 滚动到底部
  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, []);

  // 加载历史消息
  useEffect(() => {
    (async () => {
      const statusRes = await getPcStatus();
      setPcOnline(statusRes.online ?? false);

      const res = await getMessages();
      if (res.ok) {
        setMessages(res.messages || []);
        setTimeout(scrollToBottom, 100);
      }
    })();
  }, [scrollToBottom]);

  // 轮询新消息
  useEffect(() => {
    const poll = async () => {
      const lastId = messages.length > 0 ? messages[messages.length - 1].messageId : undefined;
      try {
        const res = await pollMessages(lastId);
        if (res.ok) {
          setPcOnline(res.pcOnline);
          if (res.messages && res.messages.length > 0) {
            setMessages(prev => {
              // 把所有 delivering/sending 状态的用户消息标记为 completed（因为收到了回复说明 PC 已处理）
              const updated = prev.map(m =>
                (m.role === "user" && (m.status === "delivering" || m.status === "sending"))
                  ? { ...m, status: "completed" }
                  : m
              );
              return [...updated, ...res.messages];
            });
            setTimeout(scrollToBottom, 100);
          }
          setStreaming(res.streaming || null);
        }
      } catch { /* ignore */ }
    };

    pollTimerRef.current = window.setInterval(poll, POLL_INTERVAL);
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current);
    };
  }, [messages, scrollToBottom]);

  // 发送消息
  const handleSend = async () => {
    const text = input.trim();
    if (!text && pendingFiles.length === 0) return;
    if (!pcOnline) return;

    setInput("");
    setSending(true);

    // 乐观更新：先在本地显示用户消息
    const optimistic: ChatMessage = {
      messageId: `temp-${Date.now()}`,
      role: "user",
      content: text,
      status: "sending",
      createdAt: new Date().toISOString(),
    };
    setMessages(prev => [...prev, optimistic]);
    setTimeout(scrollToBottom, 50);

    try {
      const fileIds = pendingFiles.map(f => f.fileId);
      const res = await sendMessage(text, fileIds.length > 0 ? fileIds : undefined);
      if (res.ok) {
        // 替换乐观消息的状态
        setMessages(prev =>
          prev.map(m => m.messageId === optimistic.messageId
            ? { ...m, messageId: res.messageId, status: "delivering" }
            : m
          )
        );
      } else {
        setMessages(prev =>
          prev.map(m => m.messageId === optimistic.messageId
            ? { ...m, status: "failed", content: text + "\n\n❌ " + (res.error || "发送失败") }
            : m
          )
        );
      }
    } catch {
      setMessages(prev =>
        prev.map(m => m.messageId === optimistic.messageId
          ? { ...m, status: "failed", content: text + "\n\n❌ 网络错误" }
          : m
        )
      );
    } finally {
      setSending(false);
      setPendingFiles([]);
    }
  };

  // 上传文件
  const handleFileSelect = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setUploading(true);
    try {
      const res = await uploadFile(file);
      if (res.ok) {
        setPendingFiles(prev => [...prev, { fileId: res.fileId, fileName: res.fileName }]);
      }
    } catch { /* ignore */ }
    setUploading(false);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  // 退出登录
  const handleLogout = () => {
    setToken(null);
    window.location.reload();
  };

  return (
    <div className="chat-page">
      {/* 顶栏 */}
      <header className="chat-header">
        <div className="chat-header-left">
          <span className="chat-title">🫛 小毛豆 · 工作助理</span>
        </div>
        <div className="chat-header-right">
          <span className={`pc-status ${pcOnline ? "online" : "offline"}`}>
            {pcOnline === null ? "检查中..." : pcOnline ? "🟢 PC 在线" : "🔴 PC 离线"}
          </span>
          <button className="logout-btn" onClick={handleLogout}>退出</button>
        </div>
      </header>

      {/* 消息列表 */}
      <div className="chat-messages">
        {messages.length === 0 && (
          <div className="chat-empty">
            <div className="chat-empty-icon">🫛</div>
            <p>和毛豆开始工作吧</p>
            <p className="chat-empty-hint">发送消息，毛豆会在 PC 上为你处理</p>
          </div>
        )}

        {messages.map((msg) => (
          <div key={msg.messageId} className={`chat-bubble ${msg.role}`}>
            <div className="bubble-content">
              {msg.content}
              {msg.status === "sending" && <span className="bubble-status">发送中...</span>}
              {msg.status === "delivering" && <span className="bubble-status">处理中...</span>}
              {msg.status === "failed" && <span className="bubble-status error">发送失败</span>}
            </div>
          </div>
        ))}

        {/* 流式进度 */}
        {streaming?.active && (
          <div className="chat-bubble assistant streaming">
            <div className="bubble-content">
              <span className="streaming-indicator">⏳</span>
              {streaming.content || `${streaming.phase === "thinking" ? "思考中..." : "执行中..."}`}
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {/* PC 离线提示 */}
      {pcOnline === false && (
        <div className="pc-offline-banner">
          你的毛豆 PC 当前不在线，请先在电脑上启动毛豆
        </div>
      )}

      {/* 待上传文件预览 */}
      {pendingFiles.length > 0 && (
        <div className="pending-files">
          {pendingFiles.map((f) => (
            <span key={f.fileId} className="pending-file-tag">
              📎 {f.fileName}
              <button onClick={() => setPendingFiles(prev => prev.filter(p => p.fileId !== f.fileId))}>×</button>
            </span>
          ))}
        </div>
      )}

      {/* 输入栏 */}
      <div className="chat-composer">
        <input type="file" ref={fileInputRef} onChange={handleFileSelect} hidden />
        <button
          className="attach-btn"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading || !pcOnline}
        >
          {uploading ? "⏳" : "📎"}
        </button>
        <input
          type="text"
          className="chat-input"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && !e.shiftKey && handleSend()}
          placeholder={pcOnline ? "输入消息..." : "PC 离线，无法发送"}
          disabled={!pcOnline || sending}
        />
        <button
          className="send-btn"
          onClick={handleSend}
          disabled={(!input.trim() && pendingFiles.length === 0) || !pcOnline || sending}
        >
          发送
        </button>
      </div>
    </div>
  );
}
