# MaoDouHub 接口规范与数据标准

> 版本：1.0.0
> 日期：2026-03-18
> 状态：Phase 1 实施版

---

## 一、系统架构

```
小毛豆 H5（手机端）         MaoDouHub（服务器）          毛豆 PC（桌面端）
┌──────────────┐          ┌──────────────────┐         ┌──────────────┐
│ React + Vite  │── HTTPS →│ Java SpringBoot  │── HTTP →│ Node.js      │
│ JWT 认证      │← HTTPS ──│ MySQL 存储       │← HTTP ──│ HMAC 签名认证 │
│ 轮询 3s       │          │ 消息路由 + 文件中转│         │ 轮询 5s       │
└──────────────┘          └──────────────────┘         └──────────────┘
```

### 认证方案

| 端 | 认证方式 | 说明 |
|---|---------|------|
| 小毛豆 → Hub | JWT Bearer Token | 登录后获取，24h 有效 |
| 毛豆 PC → Hub | HMAC-SHA256 签名 | 设备注册时获取密钥，每次请求签名 |
| Hub 管理后台 | 无认证 | 开发环境放行，生产环境需加鉴权 |

---

## 二、小毛豆 H5 → Hub 接口

### 2.1 认证

#### POST `/api/auth/register` — 注册

```
认证：无
请求体：
{
  "userId": "string（必需）",
  "userName": "string（必需）",
  "password": "string（必需）",
  "department": "string（可选）"
}

成功响应 200：
{ "ok": true, "userId": "zhangsan" }

失败响应 400：
{ "ok": false, "error": "用户已存在" }
```

#### POST `/api/auth/login` — 登录

```
认证：无
请求体：
{
  "userId": "string（必需）",
  "password": "string（必需）"
}

成功响应 200：
{
  "ok": true,
  "token": "eyJhbGc...",
  "user": {
    "userId": "zhangsan",
    "userName": "张三",
    "department": "办公室"
  }
}

失败响应 401：
{ "ok": false, "error": "用户名或密码错误" }
```

**JWT Token 规格：**
- 签名算法：HMAC-SHA256
- 有效期：24 小时（可配置）
- Payload：`{ sub: userId, name: userName, department: department }`
- 使用方式：`Authorization: Bearer <token>`

---

### 2.2 聊天

#### POST `/api/chat/send` — 发送消息

```
认证：JWT Bearer Token
请求体：
{
  "content": "string（必需）— 消息内容",
  "channel": "string（可选，默认 'work'）— 频道",
  "attachmentFileIds": ["string"]（可选）— 附件文件 ID 列表
}

成功响应 202：
{
  "ok": true,
  "messageId": "msg-xxxxxxxx",
  "status": "delivering"
}

PC 离线响应 200：
{ "ok": false, "error": "你的毛豆 PC 当前不在线，请先启动 PC 端毛豆" }
```

#### GET `/api/chat/messages` — 历史消息

```
认证：JWT Bearer Token
查询参数：
  channel = "work"（必需）
  before = "msg-xxx"（可选，分页游标）
  limit = 20（可选，最大 50）

响应 200：
{
  "ok": true,
  "messages": [
    {
      "messageId": "msg-xxx",
      "role": "user" | "assistant",
      "content": "消息内容",
      "status": "delivering" | "completed" | "failed" | "streaming",
      "createdAt": "2026-03-18T10:00:00Z",
      "widget": "JSON string（可选）",
      "attachments": [
        {
          "fileId": "file-xxx",
          "fileName": "通知.docx",
          "fileSize": 45678,
          "downloadUrl": "/api/files/file-xxx/download"
        }
      ]
    }
  ],
  "hasMore": true
}
```

#### GET `/api/chat/poll` — 轮询新消息

```
认证：JWT Bearer Token
查询参数：
  channel = "work"（必需）
  since = "msg-xxx"（可选，返回此 ID 之后的消息）

响应 200：
{
  "ok": true,
  "messages": [...],
  "pcOnline": true,
  "streaming": {
    "active": true,
    "content": "正在生成...",
    "phase": "executing"
  }
}
```

**轮询间隔：3 秒**

---

### 2.3 文件

#### POST `/api/files/upload` — 上传文件

```
认证：JWT Bearer Token
请求格式：multipart/form-data
字段：file = 文件二进制

响应 200：
{
  "ok": true,
  "fileId": "file-xxxxxxxx",
  "fileName": "照片.png",
  "fileSize": 1048576
}
```

#### GET `/api/files/{fileId}/download` — 下载文件

```
认证：JWT Bearer Token
响应：文件二进制流
  Content-Type: application/octet-stream
  Content-Disposition: attachment; filename="xxx"
```

#### GET `/api/files/{fileId}/info` — 文件元信息

```
认证：JWT Bearer Token
响应 200：
{
  "ok": true,
  "fileId": "file-xxx",
  "fileName": "通知.docx",
  "fileSize": 45678,
  "contentType": "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  "direction": "upload" | "result",
  "createdAt": "2026-03-18T10:00:00Z"
}
```

---

### 2.4 状态

#### GET `/api/status/pc` — PC 在线状态

```
认证：JWT Bearer Token
响应 200：
{
  "ok": true,
  "online": true,
  "deviceName": "张三的MacBook",
  "channelType": "poll",
  "lastHeartbeat": "2026-03-18T10:30:00Z"
}
```

#### GET `/api/status/me` — 当前用户信息

```
认证：JWT Bearer Token
响应 200：
{
  "ok": true,
  "userId": "zhangsan",
  "userName": "张三",
  "department": "办公室",
  "lastLoginAt": "2026-03-18T10:00:00Z"
}
```

---

## 三、毛豆 PC → Hub 接口

### 3.1 HMAC 签名规范

**算法：** HMAC-SHA256

**签名数据：** `body + timestamp`
- `body`：请求体 JSON 字符串（GET 请求为空字符串）
- `timestamp`：Unix 时间戳（秒）

**签名生成（伪代码）：**
```
data = (body || "") + timestamp
signature = hex(HMAC_SHA256(deviceSecret, data))
```

**请求头：**
```
X-Device-Id: device-xxxxxxxx
X-Device-Signature: a1b2c3d4e5f6...（hex 编码）
X-Device-Timestamp: 1710720000
Content-Type: application/json
```

**时间窗口：** ±300 秒（5 分钟），超时拒绝

---

### 3.2 设备管理

#### POST `/api/device/register` — 首次注册

```
认证：无（首次注册无密钥）
请求体：
{
  "userId": "zhangsan",
  "deviceName": "张三的MacBook"
}

响应 200：
{
  "ok": true,
  "deviceId": "device-xxxxxxxx",
  "deviceSecret": "Base64编码的32字节密钥"
}
```

**重要：** `deviceSecret` 仅在注册时返回一次，PC 端必须本地安全保存。

#### POST `/api/device/connect` — 标记在线

```
认证：HMAC 签名
请求体："{}"

响应 200：
{ "ok": true, "status": "connected" }
```

#### POST `/api/device/disconnect` — 标记离线

```
认证：HMAC 签名
请求体："{}"

响应 200：
{ "ok": true, "status": "disconnected" }
```

---

### 3.3 消息轮询与回传

#### GET `/api/device/poll` — 拉取待处理消息

```
认证：HMAC 签名
副作用：更新心跳时间

响应 200：
{
  "ok": true,
  "messages": [
    {
      "requestId": "uuid",
      "messageId": "msg-xxx",
      "userId": "zhangsan",
      "content": "帮我写个通知",
      "channel": "work",
      "attachmentFileIds": ["file-001"],
      "createdAt": "2026-03-18T10:00:00Z"
    }
  ]
}
```

**轮询间隔：5 秒（可配置）**
**队列特性：** 返回后自动清空，下次轮询不会重复

#### POST `/api/device/result` — 回传处理结果

```
认证：HMAC 签名
请求体：
{
  "requestId": "uuid（必需）— 对应的请求 ID",
  "content": "string（必需）— 回复内容",
  "widgetJson": "string（可选）— Widget JSON",
  "status": "string（可选，默认 completed）",
  "attachmentFileIds": ["file-xxx"]（可选）— 结果文件 ID 列表
}

响应 200：
{ "ok": true }
```

#### POST `/api/device/stream` — 流式进度

```
认证：HMAC 签名
请求体：
{
  "requestId": "uuid",
  "phase": "thinking" | "executing",
  "content": "正在分析文件..."
}

响应 200：
{ "ok": true }
```

---

### 3.4 文件操作

#### POST `/api/device/files/upload` — 上传结果文件

```
认证：HMAC 签名
请求格式：multipart/form-data
字段：file = 文件二进制

响应 200：
{
  "ok": true,
  "fileId": "file-xxx",
  "fileName": "通知.docx",
  "fileSize": 45678
}
```

#### GET `/api/device/files/{fileId}/download` — 下载附件

```
认证：HMAC 签名
权限：只能下载同一用户的文件

响应 200：文件二进制流
响应 403：{ "ok": false, "error": "无权访问该文件" }
```

#### GET `/api/device/files/{fileId}/info` — 文件元信息

```
认证：HMAC 签名

响应 200：
{
  "ok": true,
  "fileId": "file-xxx",
  "fileName": "照片.png",
  "fileSize": 1048576,
  "contentType": "image/png"
}
```

---

## 四、Hub 管理后台接口

#### GET `/admin` — 管理后台页面

```
认证：无（生产环境需加鉴权）
响应：HTML 页面
```

#### GET `/admin/api/stats` — 统计数据

```
响应 200：
{
  "totalUsers": 5,
  "totalDevices": 3,
  "onlineDevices": 2,
  "totalMessages": 128,
  "totalFiles": 15,
  "totalFileSizeMB": "23.4",
  "jvmUsedMemoryMB": 156,
  "jvmMaxMemoryMB": 512,
  "uptime": "2h 30m 15s",
  "threadCount": 24
}
```

#### GET `/admin/api/devices` — 设备列表

```
响应 200：
[
  {
    "deviceId": "device-xxx",
    "userId": "zhangsan",
    "deviceName": "张三的MacBook",
    "online": true,
    "channelType": "poll",
    "lastHeartbeat": "2026-03-18T10:30:00Z"
  }
]
```

#### GET `/admin/api/recent-messages` — 最近消息

```
响应 200：最近 20 条消息
[
  {
    "messageId": "msg-xxx",
    "userId": "zhangsan",
    "role": "user",
    "content": "帮我写个通知...（截断至80字）",
    "status": "completed",
    "channel": "work",
    "createdAt": "2026-03-18T10:00:00Z"
  }
]
```

---

## 五、数据模型

### 5.1 HubUser（用户）

| 字段 | 类型 | 说明 |
|------|------|------|
| userId | VARCHAR(64) PK | 用户 ID |
| userName | VARCHAR(128) | 显示名 |
| department | VARCHAR(256) | 部门 |
| passwordHash | VARCHAR(256) | BCrypt 编码密码 |
| mobileToken | VARCHAR(512) | 最后签发的 JWT |
| lastLoginAt | DATETIME | 最后登录时间 |
| createdAt | DATETIME | 创建时间 |
| updatedAt | DATETIME | 更新时间 |

### 5.2 HubDevice（设备）

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | VARCHAR(64) PK | 设备 ID |
| userId | VARCHAR(64) FK | 所属用户 |
| deviceName | VARCHAR(128) | 设备名称 |
| deviceSecret | VARCHAR(256) | HMAC 签名密钥 |
| online | BOOLEAN | 是否在线 |
| channelType | VARCHAR(16) | "poll" 或 "none" |
| lastHeartbeat | DATETIME | 最后心跳时间 |
| createdAt | DATETIME | 创建时间 |
| updatedAt | DATETIME | 更新时间 |

### 5.3 HubMessage（消息）

| 字段 | 类型 | 说明 |
|------|------|------|
| messageId | VARCHAR(64) PK | 消息 ID |
| userId | VARCHAR(64) FK | 所属用户 |
| requestId | VARCHAR(64) | 请求关联 ID |
| channel | VARCHAR(16) | 频道（"work"） |
| role | VARCHAR(16) | "user" 或 "assistant" |
| content | TEXT | 消息内容 |
| widgetJson | TEXT | Widget 配置 |
| status | VARCHAR(16) | 消息状态 |
| streamingPhase | VARCHAR(32) | 流式阶段 |
| streamingContent | TEXT | 流式中间内容 |
| createdAt | DATETIME | 创建时间 |
| updatedAt | DATETIME | 更新时间 |

**消息状态流转：**
```
delivering → streaming → completed
delivering → failed
```

### 5.4 HubFile（文件）

| 字段 | 类型 | 说明 |
|------|------|------|
| fileId | VARCHAR(64) PK | 文件 ID |
| userId | VARCHAR(64) FK | 文件所有者 |
| messageId | VARCHAR(64) | 关联消息（可选） |
| fileName | VARCHAR(256) | 原始文件名 |
| fileSize | BIGINT | 文件大小（字节） |
| contentType | VARCHAR(128) | MIME 类型 |
| storagePath | VARCHAR(512) | 本地存储路径 |
| direction | VARCHAR(8) | "upload" 或 "result" |
| createdAt | DATETIME | 创建时间 |

---

## 六、完整消息流转

```
1. 手机发消息
   POST /api/chat/send → Hub 入库(status=delivering) → 放入 PC 队列

2. PC 轮询拉取
   GET /api/device/poll → Hub 返回队列消息 → 清空队列

3. PC 下载附件
   GET /api/device/files/{id}/info → 获取文件名
   GET /api/device/files/{id}/download → 下载到本地

4. PC 处理中
   POST /api/device/stream → Hub 更新(status=streaming)

5. PC 生成文件
   POST /api/device/files/upload → Hub 存储文件 → 返回 fileId

6. PC 回传结果
   POST /api/device/result → Hub 存回复消息(status=completed) + 关联文件

7. 手机轮询
   GET /api/chat/poll → Hub 返回新消息 + 附件 + 流式状态

8. 手机下载文件
   GET /api/files/{id}/download → 下载文件
```

---

## 七、配置参考

### Hub 服务端 (application.yml)

```yaml
hub:
  jwt:
    secret: "your-secret-key"
    expirationHours: 24
  file:
    storageDir: "/data/maodouhub/files"
    maxSizeMb: 50
    retentionDays: 90
  device:
    heartbeatTimeoutSeconds: 90
    signatureTimeWindowSeconds: 300
  rateLimit:
    messagePerMinute: 10
    filePerHour: 20
    pollIntervalSeconds: 3
```

### 毛豆 PC 环境变量

```bash
MAODOU_HUB_ENABLED=true
MAODOU_HUB_URL=https://hub.example.com
MAODOU_HUB_DEVICE_ID=device-xxxxxxxx
MAODOU_HUB_DEVICE_SECRET=Base64密钥
MAODOU_HUB_POLL_INTERVAL_MS=5000
```

### 小毛豆 H5 环境变量

```bash
VITE_API_BASE=https://hub.example.com
```

---

## 八、安全规范

### 8.1 认证安全

| 安全措施 | 说明 |
|---------|------|
| JWT 过期 | Token 24 小时过期，需重新登录 |
| HMAC 时间窗口 | 签名有效期 ±5 分钟，防重放 |
| 密码存储 | BCrypt 编码，不可逆 |
| deviceSecret | 仅注册时返回一次，不在网络传输 |

### 8.2 数据安全

| 安全措施 | 说明 |
|---------|------|
| 文件权限隔离 | PC 只能下载同一用户的文件 |
| 文件存储权限 | 0o700（目录）/ 0o600（文件） |
| 消息隔离 | 用户只能查看自己的消息 |

### 8.3 生产环境注意事项

- [ ] 管理后台需加认证（当前无认证）
- [ ] JWT secret 使用强随机密钥
- [ ] 启用 HTTPS
- [ ] 文件上传大小限制
- [ ] API 频率限制
- [ ] 日志脱敏（不记录 deviceSecret）

---

## 九、错误码规范

| HTTP 状态码 | 含义 | 典型场景 |
|------------|------|---------|
| 200 | 成功（ok=true）或业务失败（ok=false） | 正常响应 |
| 202 | 消息已接受，异步处理 | 发送消息成功 |
| 401 | 认证失败 | JWT 过期 / HMAC 签名错误 |
| 403 | 无权限 | 访问他人文件 |
| 404 | 资源不存在 | 文件未找到 |
| 500 | 服务器错误 | 内部异常 |

**业务错误统一格式：**
```json
{
  "ok": false,
  "error": "错误描述（中文）"
}
```
