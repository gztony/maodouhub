/** 聊天消息 */
export interface ChatMessage {
  messageId: string;
  role: "user" | "assistant" | "system";
  content: string;
  status: string;
  widget?: string;
  createdAt: string;
  attachments?: FileAttachment[];
}

/** 文件附件 */
export interface FileAttachment {
  fileId: string;
  fileName: string;
  fileSize: number;
  downloadUrl?: string;
}

/** 流式进度 */
export interface StreamingState {
  active: boolean;
  content: string;
  phase: string;
}

/** 轮询响应 */
export interface PollResponse {
  ok: boolean;
  messages: ChatMessage[];
  pcOnline: boolean;
  streaming?: StreamingState;
}

/** 用户信息 */
export interface UserInfo {
  userId: string;
  userName: string;
  department: string;
}
