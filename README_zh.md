**🌐 [English](README.md) | [简体中文](README_zh.md)**

# Ai Office

一个运行在 Android 上的 AI 办公助手。安装包 323.07 KB，零第三方依赖，纯 Java + Android 原生 API 构建。

## 亮点

- 安装包 347.65 KB —— 大部分 AI 类 App 动辄数十 MB，Ai Office 把它压缩到不到 0.35 MB
- 零第三方依赖 —— 不用 OkHttp、不用 Gson、不用 AndroidX、不用 Material Design，所有代码基于 Android 系统原生 API
- 纯 Java + 动态构建 UI —— 布局文件只放骨架，所有界面运行时构建
- AIDE 兼容 —— 可在手机上直接编译和修改

## 功能

### AI 对话

- 支持流式输出、深度思考（reasoning_content）、并行工具调用
- 多供应商：DeepSeek / OpenAI / Anthropic Claude / Moonshot (Kimi) / 智谱 GLM / SiliconFlow / 阿里通义千问 / 自定义 OpenAI 兼容接口
- 双协议：OpenAI 兼容协议（/chat/completions）+ Anthropic 原生协议（/v1/messages）
- 识图模型独立配置：主模型处理文字，遇到图片消息自动切换到多模态模型；支持跨供应商（如主用 DeepSeek、识图用智谱 GLM-4V）
- 思考深度可调：默认 / None / low / high / max
- 上下文自动压缩：长对话自动把早期内容摘要，原文完整保留；可随时恢复完整上下文

### 文件操作（14 个工具）

查看目录、列目录树、文件信息、按行读文件、写文件、追加、精确替换、创建目录、移动、重命名、复制、删除、搜索文件名、搜索文件内容。

AI 可以读写你手机上的任意文件（需授予文件权限）。

### 联网搜索

支持 8 家搜索引擎：

- Bing RSS（免费）
- DuckDuckGo（免费）
- 智谱 Web Search（需 Key）
- Tavily（需 Key）
- Brave Search（需 Key）
- Serper / Google（需 Key）
- SearXNG 自建（免费）
- 自定义（视情况）

搜索结果以卡片形式展示，可折叠，点击打开内置浏览器。网页阅读同样返回结构化卡片。

### 无障碍操控手机

AI 可以通过系统无障碍服务操作其他 App：

- 启动应用、读取屏幕、点击控件、输入文本、滚动、返回、回主页
- 屏幕读不到控件时（微信等限制读取的 App），可切换到视觉模式：截屏（MediaProjection），看图分析元素坐标后返回操作指令，App 自动换算为真实屏幕坐标

涉及支付、密码、删除等敏感操作时，工具会要求 AI 先征得用户同意。

### 实时通话（智谱 GLM-Realtime / OpenAI Realtime）

- 实时语音：基于 WebSocket 的双向低延迟音频流；服务端 VAD 检测说话结束并自动触发 AI 回复，不再走"语音识别 + TTS"的往返链路
- 实时视频：摄像头预览 + 定时抓帧（2fps）发送；支持前后摄像头切换 + 手电筒开关
- 屏幕共享：1fps 抓取当前屏幕发送给 AI（复用 ProjectionController）
- 通话中工具调用：AI 可以调用任何已启用的工具（文件 / 搜索 / shell / JS / 无障碍 / 插件）并语音播报结果
- 实时字幕：用户语音与 AI 语音都在屏幕上实时转写显示
- 供应商预设：智谱 GLM-Realtime / OpenAI Realtime / 自定义（WebSocket 地址、模型、协议、鉴权方式、音色）
- TTS（独立功能）：支持自定义 API（如智谱 glm-tts）+ 系统 TTS 回退；可设置"自动朗读每条回复"或手动点击朗读

### 插件系统

三种插件形态，放在 /sdcard/AI/plugins/ 下重启生效：

1. AI 工具插件：prompt / http / script 类型，注册为 AI 可调用的函数
2. UI 扩展插件：挂在菜单 / 长按 / 附件菜单 / 设置页等扩展点，可添加自定义入口
3. UI 覆盖插件（ui_change）：修改 App 配色 —— 顶栏、输入栏、气泡、代码块、卡片、抽屉、发送按钮等 20+ 项颜色与尺寸，可配透明度

### 长期记忆

跨会话保存用户偏好、常用路径、项目背景等，AI 每次对话都会参考。可在设置页手动管理。

### 会话管理

多会话、历史记录搜索、重命名、导出 Markdown、分享全文。可设置进入 App 时默认打开"上次对话"或"新对话"。

### 界面

- 聊天背景图全覆盖：顶栏、状态栏、消息区、底部输入栏、导航栏全部铺满背景图；顶栏/输入栏支持设置透明度
- 深色 / 浅色 / 跟随系统 三种主题
- 字体缩放：小 / 标准 / 大 / 特大 / 超大
- Markdown 渲染：标题、列表、表格、引用、代码块、链接
- 多语言：内置简体中文 / English；可导入自定义语言包（JSON）扩展更多语言；可导出当前语言包作为翻译模板

## 技术栈

- 语言：Java
- 最低支持：Android 4.4 (API 19)
- UI：Android 原生 View（android.widget.* / android.view.*）
- 网络：HttpURLConnection
- JSON：org.json
- Markdown：自研解析器
- 图片处理：BitmapFactory / Bitmap
- 视觉操控：MediaProjection + ImageReader
- 无障碍：AccessibilityService + GestureDescription
- JS 执行：内置 WebView (V8)
- TTS：MediaPlayer / TextToSpeech
- 实时通话：手写 WebSocket（RFC6455）+ PCM16 音频 + Camera API / MediaProjection 抓帧

## 为什么这么小

1. 不引依赖：不用 OkHttp（约 1MB）、Gson（约 200KB）、AndroidX（按需几十到几百 KB）、Material（约 500KB+）
2. 动态 UI：布局尽量在代码里构建，XML 只放骨架，减少资源文件
3. 自研组件：Markdown 渲染、TTS 播放、浏览器、公告对话框、数据驱动的表单等全部手写
4. 图标资源极少：主界面用 Unicode 字符 + drawable shape；仅实时通话页打包了一小组 PNG 图标（16 个 × 5 套分辨率，合计几 KB）

## 安装

### 从源码构建

1. 下载源码
2. 用 Android Studio / AIDE 打开工程
3. 编译运行

### 使用 AIDE（推荐，因为体积小）

1. 在手机上安装 AIDE
2. 打开工程目录，直接编译

## 配置

首次使用需配置：

- AI 供应商：选择预设（DeepSeek / OpenAI / Anthropic / 智谱 等）或自定义
- API 地址：预设会自动填入，也可手动修改
- API Key：从供应商控制台获取
- 默认模型：如 deepseek-flash、glm-5.3-flash、gpt-6-astar

可选配置：

- 识图 API：填写识图模型、独立地址、独立 Key、独立协议（留空 = 复用主配置）
- 联网搜索：选择搜索引擎，配置对应 Key 或自定义 URL
- TTS 语音合成：填写 TTS API 地址、Key、模型、音色
- 实时通话：选择供应商（智谱 GLM-Realtime / OpenAI Realtime / 自定义），填写 WebSocket 地址、模型、Key、协议、音色
- 权限：所有文件访问权限 + 无障碍服务

## 权限说明

- INTERNET —— AI API、联网搜索、网页抓取
- READ/WRITE_EXTERNAL_STORAGE —— 文件读写
- MANAGE_EXTERNAL_STORAGE —— Android 11+ 读写 /sdcard
- POST_NOTIFICATIONS —— 后台完成通知
- RECORD_AUDIO —— 语音通话
- CAMERA —— 视频通话、拍照
- FLASHLIGHT —— 视频模式手电筒
- FOREGROUND_SERVICE —— 视觉操控投屏会话
- FOREGROUND_SERVICE_MEDIA_PROJECTION —— 视觉操控 / 实时通话屏幕共享（Android 14+ 强制）

## 项目结构

app/src/main/java/com/ai/office/
- MainActivity.java              # 主界面 + Agent 循环 + 流式渲染 + 会话管理
- SettingsActivity.java          # 设置首页（分类卡片）
- SettingsDetailActivity.java    # 设置二级页（按分类动态构建）
- BaseActivity.java              # 字体缩放 / 主题 / 系统栏基类
- AiClient.java                  # AI 流式客户端（含工具定义）
- AIProvider.java                # AI 供应商预设
- SearchProvider.java            # 搜索供应商预设
- UiOverrides.java               # UI 覆盖层（供插件改配色）
- FileToolExecutor.java          # 14 个文件工具
- WebToolExecutor.java           # 联网搜索 + 网页抓取
- ShellToolExecutor.java         # 本机 shell
- JsToolExecutor.java            # JS 执行（WebView V8）
- AccessibilityController.java   # 无障碍操控
- AutoAccessibilityService.java  # 无障碍服务
- ProjectionController.java      # MediaProjection 截屏内核
- ProjectionService.java         # 投屏前台服务
- PluginManager.java             # 插件扫描与执行
- UiPluginDialog.java            # 数据驱动的插件表单
- MemoryStore.java               # 长期记忆
- MarkdownView.java              # Markdown 渲染
- Notifier.java                  # 通知
- TtsHelper.java                 # TTS 朗读
- VoiceCallActivity.java         # 实时通话（智谱 GLM-Realtime / OpenAI Realtime）
- RealtimeConfig.java            # 实时通话供应商预设
- RealtimeClient.java            # Realtime 事件封装
- WebSocketClient.java           # 手写 RFC6455 WebSocket 客户端
- WebViewActivity.java           # 内置浏览器
- LanguageManager.java          # 语言包加载（自定义 / assets / 内置兜底）
- UiUtils.java                   # 工具类

## 插件示例

放在 /sdcard/AI/plugins/ 下的 JSON 文件。

### AI 工具插件（注册为 AI 函数）

    {
      "name": "translate",
      "description": "把中文翻译成英文",
      "type": "prompt",
      "parameters": {
        "text": { "type": "string", "description": "要翻译的中文" }
      },
      "prompt": "请把下面这段中文翻译成地道的英文，只输出译文：\n{{text}}"
    }

### UI 扩展插件（添加菜单入口）

    {
      "type": "ui",
      "name": "weather",
      "title": "查天气",
      "extension": "input_plus",
      "fields": [
        { "key": "city", "label": "城市", "type": "text", "required": true }
      ],
      "action": {
        "kind": "prompt",
        "template": "帮我查 {{city}} 未来三天的天气"
      }
    }

### UI 覆盖插件（改配色）

    {
      "type": "ui",
      "name": "theme_pink",
      "title": "一键切换粉色配色",
      "extension": "settings_item",
      "fields": [],
      "action": {
        "kind": "ui_change",
        "changes": {
          "ui_color_primary": "#D81B60",
          "ui_color_bubble_user_bg": "#F8BBD0",
          "ui_color_bubble_ai_bg": "#FCE4EC",
          "ui_color_topbar_bg": "#F8BBD0",
          "ui_chrome_alpha": "0"
        }
      }
    }

## 已知限制

- 不含 Python 环境：Android 原生 shell 无 Python，需要脚本时用内置 run_js（V8 引擎）
- 视觉模式依赖 MediaProjection：Android 14+ 需要前台服务，首次使用会弹授权
- 无障碍读取受限：部分 App（如微信）限制无障碍读取节点，需切换到视觉模式
- 实时通话依赖服务端协议：目标服务需实现 OpenAI Realtime 协议或智谱 GLM-Realtime 协议（事件名略有差异，RealtimeConfig 两者都处理）

## 设计原则

- 零依赖：不引入任何第三方库，减小体积、降低冲突
- 数据驱动：插件、设置项、菜单入口都可以由 JSON 描述，方便扩展
- 安全默认：shell / 无障碍 / script 类插件默认关闭，需用户显式开启
- 用户可见：所有工具调用在对话中以可折叠面板展示，操作可审计

## 联系

有问题、建议、Bug 报告，欢迎反馈。

E-mail: 938406403@qq.com

授权: MIT License