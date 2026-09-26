Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog,
and this project adheres to Semantic Versioning.

[1.3.0] - 2026-09-26

文生图（架构级新能力）

· ★ 新增 ImageGenConfig.java：预设 4 家供应商（智谱 CogView / OpenAI DALL-E / SiliconFlow Kolors / 自定义 OpenAI 兼容）；配置项 baseUrl / apiKey / model / size；缺项自动回退到「AI 接入」里的主配置，Key 留空也能用主 Key
· ★ 新增 ImageGenClient.java：POST {baseUrl}/images/generations，同步生成；自动处理 url 与 b64_json 两种返回格式；url 结果自动下载并转 JPEG base64
· ★ 新增 generate_image 工具供 AI 调用：参数 prompt（图像描述）+ size（可选尺寸）；生成的图片自动附到对话（走图片消息渲染管线，可放大 / 长按保存）
· ★ 设置首页「AI 与模型」分组新增「文生图」二级页：供应商 / API 地址 / API Key / 模型 / 默认尺寸；每项留空均回退到预设或主配置
· 支持跨供应商：主 AI 用 DeepSeek，文生图单独用智谱 CogView 亦可

Liquid Glass 重做

· ★ LiquidGlassDrawable 从「屏幕背景裁切 + 高斯模糊」重做为「纯 Canvas 多层渐变」，共 7 层：半透明填充底色 → 顶部主高光（white → transparent 竖向渐变）→ 左上斜向反光条 → 右下暗角（模拟光源方向）→ 内侧白色描边（玻璃边缘折射）→ 底部内侧阴影（厚度感）→ 左上内侧亮边（弧形高光）
· ★ 不再依赖聊天背景图：没有背景图时（默认渐变底）也能呈现玻璃质感；气泡 / 按钮 / 卡片 / 顶栏 / 输入栏 / 抽屉 / 菜单 / 设置卡片全部走新绘制
· ★ UiOverrides 的 4 个 view-aware 重载（bubbleUserBgDrawable / bubbleAiBgDrawable / codeBgDrawable / cardBgDrawable）改为直接转发无 view 版，不再触发背景裁切与模糊；BackdropBlurHelper / GlassBackdropHelper 保留但不再被调用
· 保留 Bitmap 参数的构造函数重载以便兼容旧调用方，Bitmap 参数已被忽略

模型选择菜单（紧凑锚定 + 动画）

· ★ GlassMenuDialog 新增紧凑模式 showCompactItems / showCompactItemsAt：宽度屏宽 60%（上限 240dp，下限 180dp），比标准模式（86% / 320dp）更窄；项高度 10dp 更紧凑；每项右侧可显示 trailing（如当前项 ✓、跳转项 ›）
· ★ 锚定弹出：点击顶栏模型名 → 菜单在模型名正下方 6dp 处展开，不再屏幕居中；左右自动限制在屏幕边距 8dp 内
· ★ 弹出动画：scale 0.7 → 1.0 + alpha 0 → 1，200ms，DecelerateInterpolator，pivot 在顶部中央（视觉从锚点"长出来"）；收起动画：scale → 0.9 + alpha → 0，120ms，结束后再 dismiss 与执行切换
· ★ 修复锚点坐标偏移状态栏高度：dialog window 加 FLAG_LAYOUT_IN_SCREEN / FLAG_LAYOUT_NO_LIMITS，让 window 坐标系与 View.getLocationOnScreen() 对齐
· 标准模式 showItems 保持不动，其它菜单（会话菜单 / 更多菜单 / 附件菜单等）不受影响

关于 app

· ★ 设置首页「系统」分组新增「关于」二级页
· 显示：应用信息（应用名 / 版本号 / 包名 / 作者）、相关链接（GitHub 仓库可点开）、语言（点击切换语言，含内置与自定义语言包）、致谢
· 语言快捷入口：从「关于」页直接切换语言，无需先进入「语言」二级页；切换后设置页立即生效，其它页面下次进入生效
· GitHub 链接：https://github.com/WuWuoooo/Ai-Office-android

语言适配

· ★ zh-CN.json / en-US.json 新增约 30 条 key：settings_row_about / settings_row_image_gen / about_* （13 条）/ imagegen_*（11 条）
· 所有新页面文案全部接入 LanguageManager.t(...)，中英文完整覆盖

Fixed

· ★ 修复 UiOverrides.java 末尾 4 个 view-aware 重载被重复定义两次，导致 AIDE 报「method cannot be applied to (Context)」一类错误
· ★ 修复 MainActivity.addUserBubble 方法尾部残留两段重复的「局部模糊玻璃应用」代码：第一段末尾的 `}` 提前闭合了方法，第二段代码掉到类级别，导致 Unknown entity 'box' / Unexpected end of declaration / Wrong '}' 共 9 个编译错误
· ★ 修复 UiUtils.java 中被误粘入 UiOverrides 的方法（glassEnabled / glassAlphaValue / bubbleRadius / *BgDrawable）：这些方法全部归位到 UiOverrides
· 修复模型选择菜单在状态栏下方展开时位置偏低（相差一个状态栏高度）
· 修复模型选择菜单弹出/收起无动画、视觉生硬

Changed

· 实验性功能 → 毛玻璃效果：描述文案由「对聊天背景做一次高斯模糊」改为「纯 Canvas 绘制的液态玻璃质感」，不再需要 1-2 秒背景处理时间，低端机也能流畅开
· MainActivity.refreshTools() 调用改为 AiClient.buildTools(allowShell, allowA11y, true)，文生图工具默认注册
· AiClient.buildTools 增加三参数重载；两参数版本保留并转发到三参数版（allowImageGen 传 false），兼容旧调用
· 设置首页「AI 与模型」分组由 3 项（AI 接入 / 识图 API / 联网搜索）扩为 4 项（+ 文生图）；「系统」分组由 2 项（权限 / 余额查询）扩为 3 项（+ 关于）

[1.2.0] - 2026-09-25

多语言系统（架构级新能力）

· ★ 新增 LanguageManager.java：三层加载（自定义 filesDir/lang → assets/lang → 代码内置兜底），任意一层命中即可用
· ★ t(key, 中文默认) API：找不到 key 时回退到中文原文，实现「渐进式接入」——未翻译的页面不会变空白
· ★ 内置两套语言：简体中文 zh-CN / 英文 en-US，全部提供完整 key 集（约 350 条）
· ★ 语言包格式：JSON，含 name / author / version / strings 字段；已提供日文 ja-JP 示例
· ★ 导入 / 导出：可从任意来源导入语言包（存到 filesDir/lang/custom_xxx.json），可导出当前语言包到 /sdcard/AI/lang/
· 切换语言后设置页立即生效；主界面、二级页、对话框、Toast 下次进入时生效

设置页新增「语言」分类

· ★ 设置首页「应用」分组新增「语言」入口，显示当前语言名
· ★ 语言二级页：内置语言列表（点选切换）、自定义语言包列表（点选切换 / 长按删除）、导入、导出
· 内置语言包导出后可作为翻译模板，用户翻译后重新导入即生效

界面语言化

· ★ 设置首页 / 设置二级页（13 个分类）全部接入语言包
· ★ 主界面：欢迎语、输入框 hint、发送按钮、抽屉、会话菜单、消息操作行、工具面板、搜索卡片、阅读卡片、各类对话框与 Toast 全部接入
· ★ 实时通话页：状态文字、系统提示接入
· 保持内部数据（AI 内部指令、工具返回值、系统消息）为中文，避免影响模型理解

Fixed

· ★ 修复 AIDE 编译器对 Java 字符串中 `\n` / `\"` 转义处理不一致导致 LanguageManager.java 报 1856 个 Unexpected end of declaration：内置语言包改用 `|` 分隔的 String[] 常量，值内不含任何转义字符
· ★ 修复 assets 中 en-US.json 内容不全导致切换英文后二级页不生效：补全全部 key

Changed

· BaseActivity 不感知语言；由各 Activity 自行调用 LanguageManager.t(...) 取文案，避免全局 Context 包装带来的兼容问题


[1.1.0] - 2026-09-25

实时通话（架构级新能力，替代旧版语音识别方案）

· ★ 新增 RealtimeConfig.java：预设「智谱 GLM-Realtime / OpenAI Realtime / 自定义」三家；配置项含 WebSocket 地址、模型、协议（openai / zhipu）、鉴权方式（header / query）、音色；缺项自动回退到主 AI 配置
· ★ 新增 WebSocketClient.java：RFC6455 协议手写实现，零第三方依赖；支持 wss:// 与 ws://、TLS 握手、Sec-WebSocket-Accept 校验、ping/pong 自动应答、close 帧正常关闭
· ★ 新增 RealtimeClient.java：封装 GLM-Realtime / OpenAI Realtime 事件。客户端 → session.update / input_audio_buffer.append / input_image_buffer.append / conversation.item.create / response.create / response.cancel；服务端 → session.created / response.audio.delta / response.audio_transcript.delta / response.text.delta / input_audio_transcription.completed / response.function_call_arguments.done / response.done / error
· ★ 重写 VoiceCallActivity：全屏 FrameLayout + SurfaceView 铺满；语音 / 视频 / 屏幕共享三开关合并；麦克风采样率回退（24k → 16k → 48k，非 24k 自动线性插值重采样）；AI 音频流式播放；用户语音实时字幕
· ★ 实时通话内工具调用：注册当前启用的全部工具（文件 / 联网 / shell / JS / 无障碍 / 插件），AI 可在通话中直接执行并播报结果
· 视频：Camera API 预览 + 定时拍帧（2fps）发送；支持前置/后置切换、手电筒开关
· 屏幕共享：复用 ProjectionController，1fps 抓屏发送，退出通话即停止投屏
· 设置首页新增「实时通话」分类；附件菜单新增「实时通话」入口

通话界面视觉

· ★ 按钮全部图标化：Emoji 替换为 drawable（ic_microphone_outline_white / ic_microphone_off_white / ic_camera_flip_outline_white / ic_monitor_screenshot_white / ic_flashlight_white / ic_flashlight_off_white / ic_keyboard_backspace_white），全部提供 hdpi / mdpi / xhdpi / xxhdpi / xxxhdpi 五套
· ★ 通话页沉浸式：覆写 applySystemBars，状态栏 / 导航栏全透明，内容延伸到状态栏下；topBar 顶部 padding 按 statusBarHeight 动态加高；禁用 LIGHT_STATUS_BAR 保持白色图标可读
· 关闭 / 返回按钮改为 ImageView + 固定 44dp 方形，背景透明

Fixed

· ★ 修复通话界面未覆盖状态栏（BaseActivity 的浅色方案不适用于黑色通话页）
· ★ 修复返回按钮 oval 背景被拉伸为椭圆（TextView wrap_content 下宽高比失衡）
· ★ 修复主界面导航栏背景不透明导致聊天背景图无法覆盖底部：setNavigationBarColor 改为透明 + LAYOUT_HIDE_NAVIGATION；applyRootInsets / setupKeyboardResize 底部预留 navigation_bar_height，避免输入栏被虚拟按键遮挡

Changed

· 设置 → 语音分组新增「实时通话」二级页；脚本类插件、shell / 无障碍沿用默认关闭的安全模型
· 附件菜单「语音通话 / 视频通话」两项合并为「实时通话」，进入通话页后可随时开启视频 / 屏幕共享

[1.0.0] - 2026-09-25

首个正式版。相比开发阶段（0.x），本版完成整体架构收敛，新增 UI 插件系统、语音交互、设置页重构、多供应商搜索与识图等核心能力。

Added

UI 插件系统（架构级新能力）

· ★ 新增 UiOverrides.java：集中管理可被 UI 插件覆盖的颜色/尺寸（26 个白名单键），提供统一的读取入口与动态 GradientDrawable 生成（气泡、卡片、代码块、输入框、顶栏、输入栏、发送按钮、抽屉）
· ★ PluginManager 新增 ui_change action：插件通过 JSON 里的 changes: {key: value} 修改 App 视觉，写入前经过白名单 + 格式校验，非法键/非法颜色/非法尺寸会被跳过并在返回结果里报告
· ★ 键覆盖范围：ui_color_primary / on_primary / surface / on_surface / outline / error / divider / bubble_user_bg / bubble_user_text / bubble_ai_bg / bubble_ai_text / code_bg / code_text / edit_bg / edit_border / topbar_bg / input_bar_bg / send_btn_bg / send_btn_text / card_bg / drawer_bg，尺寸键 ui_size_text / title / bubble_radius / card_radius / button_radius，透明度键 ui_chrome_alpha
· ★ UI 插件扩展点共 5 处：main_menu（顶栏「⋯」更多菜单）、message_long_press（AI 消息操作行）、input_plus（「+」附件菜单）、settings_item（设置 → 插件二级页）、toolbar（预留）
· 设置 → 数据 → 插件二级页新增「查看当前 UI 覆盖」「清除所有 UI 覆盖」两项
· 提供 5 个示例插件：bubble_theme.json（自定义配色）、theme_pink.json / theme_warm.json / theme_dark.json（一键预设）、theme_reset.json（恢复默认）

设置页重构

· ★ 首页改为「分类卡片列表 + 二级详情页」结构：AI 与模型 / 语音 / 应用 / 数据 / 系统 五个分组共 12 个入口，点击进入 SettingsDetailActivity（按 category 参数动态构建 UI）
· 二级页覆盖：AI 接入、识图 API、联网搜索、语音合成、生成行为、外观、功能开关、长期记忆、系统提示词、插件、权限、余额查询
· 移除底部「完成」按钮，统一用顶部箭头返回

语音与通话

· ★ TTS 朗读：支持自定义 TTS API（如智谱 glm-tts）+ 系统 TTS 回退；tts_enabled 控制总开关，tts_auto_read 控制「AI 回复完成后自动朗读」
· ★ AI 回复操作行新增「▶ 朗读」按钮，可一键朗读该条回复，朗读中再点则停止
· ★ 语音通话 VoiceCallActivity：SpeechRecognizer 识别 → 结果回填主界面并自动发送
· ★ 视频通话：摄像头预览 + 说话时拍一张快照随文本一起发出；支持前置/后置切换、手电筒开关
· 语音识别不可用时自动切到手动输入，并提供「切换为手动输入 / 切换为语音输入」按钮

联网搜索与网页阅读

· ★ SearchProvider.java 预设 8 家搜索引擎：Bing RSS、DuckDuckGo、智谱 Web Search、Tavily、Brave Search、Serper (Google)、SearXNG（自建）、自定义（用户可填 URL / 方法 / 请求体 / 鉴权头 / 字段映射）
· ★ WebToolExecutor 通用搜索请求器：模板占位符 {{query}} / {{query_raw}} / {{count}} / {{api_key}}，支持点路径提取结果数组（如 web.results）
· 搜索结果 / 网页阅读卡片支持折叠，点击条目跳转内置浏览器 WebViewActivity

内置浏览器

· ★ 新增 WebViewActivity：顶部工具栏含返回 / 前进 / 刷新 / 外部浏览器打开 / 关闭，含加载进度条与标题实时同步

AI 接入

· ★ 新增 Anthropic Claude 供应商（原生 /v1/messages 协议）；AiClient 支持 openai / anthropic 双协议分支
· ★ 识图模型独立配置：vision_model / vision_base_url / vision_api_key / vision_protocol 四项全部留空时自动复用主配置；发送含图片的消息时自动切换模型
· 思考深度新增 None 选项：对支持关闭思考的模型不发送 reasoning_effort / thinking
· 供应商预设与模型列表更新（DeepSeek / OpenAI / Anthropic / Moonshot / 智谱 / SiliconFlow / 通义）

长期记忆

· ★ 设置页新增长期记忆管理入口：查看 / 新增 / 编辑 / 删除条目，MemoryStore 补充 delete / clearAll / getAllKeys / getAllEntries 方法

视觉与无障碍操控增强

· ★ accessibility_control 新增动作：long_press（长按）、swipe（自定义起止点滑动）、windows（窗口诊断）、screenshot（MediaProjection 截屏，视觉兜底）、stop_projection（任务完成后主动停止屏幕共享）
· ★ 截图叠加 10% 网格线与边缘像素刻度（drawGridOverlay），便于视觉模型精确定位
· ★ from_vision 坐标换算基准修正为「发给 AI 的图片尺寸 → 屏幕尺寸」，修复系统性偏移
· ★ 投屏空闲 10 分钟自动停止兜底，防止 AI 忘记关闭导致通知常驻耗电
· input 输入三层降级策略：ACTION_SET_TEXT → 剪贴板 + ACTION_PASTE → 文本留在剪贴板引导用户长按粘贴
· 新增 run_js 工具（WebView V8 引擎执行 JS），零依赖

主界面视觉

· ★ 聊天背景图升级为全覆盖：顶栏、状态栏、消息区、底部输入栏、导航栏全部铺满背景图；状态栏 / 导航栏透明，内容通过 applyRootInsets / applyThemeChrome 避免被系统栏遮挡
· ★ ui_chrome_alpha 控制顶栏 / 输入栏的不透明度：0 = 完全透明透出背景图，100 = 不透明；未设置时自动判断（有背景图 = 0，无 = 100）
· 键盘弹出 adjustResize 修复：applySystemBars 去掉 LAYOUT_STABLE / LAYOUT_HIDE_NAVIGATION，新增 setupKeyboardResize 手动监听键盘高度

Fixed

· ★ 修复图片与文字同时发送时文字被裁切：addUserBubble 给文字 TextView 显式设置基于屏幕宽度的 maxWidth，强制多行
· ★ 修复「重新生成 / 删除这一轮」只删最后一条 assistant 消息、保留前面工具调用的问题：改为 findTurnStart 回溯到该轮 user 消息之后整段删除
· ★ 修复 AI 回复空内容时误朗读上一条回复：appendFinalAssistant 返回 boolean，onComplete 只有真的加入新消息才调用 TTS
· ★ 修复搜索结果显示乱码：WebToolExecutor 新增 isLikelyGarbled 检测（CJK 常用字占比 + 典型乱码标记字），过滤 GBK 误解码的脏数据
· ★ 修复语音通话启动 NPE：btnMic 忘记 new TextView(this)
· ★ 修复 UiOverrides 覆盖不生效：ALL_KEYS 数组后多余的 }; 导致类提前闭合
· ★ 修复旧对话不按 time 排序：进入 App 恢复「最近一次会话」改用按 time 字段取最大，而不是取列表末尾
· 修复历史会话图片永久丢失：保存时把图片 base64 落盘到 filesDir/chat_imgs/（确定性文件名），消息存 ref:路径，渲染 / 编辑 / 发送三处统一还原
· 修复 AI 气泡长按菜单与原生「选择复制」冲突：长按交给内部 TextView，菜单改到回复末尾的操作行
· 修复上下文压缩破坏对话原文：摘要改写入 system 消息内部字段 x_ctx_summary / x_ctx_from，UI 与存储原文完整保留
· 修复聊天背景图退出重进后丢失（content:// 授权过期）：选图后立即复制到应用私有目录
· 修复设置项「配置消失」：空字符串不再覆盖配置，关键写入改用 commit() 同步落盘
· 修复联网搜索不可用：多引擎回退（自定义 API → Bing RSS → DuckDuckGo）
· 修复 edit_file 匹配失败：新增 CRLF→LF 归一化 + 忽略行尾空白的整块匹配回退 + 诊断信息
· 修复重复注册背景刷新广播接收器导致的内存泄漏
· 修复会话导入 requestCode（3001/3002）与运行时权限请求冲突，改为 3101/3102

Changed

· ★ BaseActivity 新增 applySystemBars()，所有子 Activity 自动跟随主题设置状态栏 / 导航栏图标色
· ★ MainActivity 消息渲染合并连续 assistant + tool 消息为一个 aiBox，操作行只在整轮回复末尾出现
· 设置启动模式 startup_mode：进入 App 默认打开「上次对话」或「新对话」（默认上次对话）

Security

· UI 插件 ui_change action 采用白名单机制：键必须在 UiOverrides.ALL_KEYS 里，颜色必须匹配 #RRGGBB / #AARRGGBB，尺寸必须为正整数
· shell 工具与无障碍工具沿用「默认关闭 + 设置显式开启 + 面板全程可见」的安全模型
· ShellToolExecutor 危险命令黑名单（rm -rf / / mkfs / dd if= / reboot 等）直接拒绝

---

[0.2.6] - 2026-09-24

Fixed

· ★ 修复图片与文字同时发送时文字显示不完全
· ★ 修复 AI 气泡长按菜单与「选择复制」冲突
· ★ 修复上下文压缩破坏对话

Added

· ★ 视觉操控点击偏移修复：from_vision 坐标换算以「发给 AI 的图片尺寸」为基准
· ★ 截图叠加 10% 网格线与像素刻度
· ★ stop_projection 动作 + 投屏 10 分钟空闲自动停止
· input 输入三层降级策略

---

[0.2.5] - 2026-09-24

Fixed

· ★ 修复 Android 14+ 截屏授权后启动失败：新增 ProjectionService 前台服务（type=mediaProjection）

Added

· Manifest 补充 FOREGROUND_SERVICE / FOREGROUND_SERVICE_MEDIA_PROJECTION 权限
· 投屏会话与前台服务联动，用户从状态栏停止投屏时自动 stopSelf

---

[0.2.4] - 2026-09-23

Added

· ★ 视觉 + 无障碍混合操控：screenshot 动作基于 MediaProjection 截屏，截图以带图消息附到对话
· 手势增强：long_press / swipe
· 窗口诊断 windows 动作
· screen 读不到控件时返回视觉方案引导

---

[0.2.3] - 2026-09-23

Fixed

· 修复「AI 无法使用 shell 和无障碍」：onResume 重建工具列表
· 系统提示词动态注入「本机高级能力」清单

Added

· run_js 工具（WebView V8 引擎执行 JavaScript）
· 无障碍新增 launch_app / sleep / recents / notifications
· shell 工具环境增强：注入完整 PATH / HOME / TMPDIR

---

[0.2.2] - 2026-09-23

Added

· 顶栏菜单 / 设置按钮改用 drawable 图标并随深色模式着色
· 多图发送：相册多选、逐张追加后一次性发送
· @ 引用文件支持多选
· 新工具 run_shell_command / accessibility_control
· 设置页新增 shell / 无障碍开关与入口

Fixed

· 聊天背景图退出重进后丢失
· 设置「配置消失」：空字符串不再覆盖配置
· 联网搜索不可用：多引擎回退
· edit_file 匹配失败：CRLF 归一化 + 忽略行尾空白回退
· 重复注册背景刷新广播接收器导致的内存泄漏
· 会话导入 requestCode 冲突

---

[0.2.1] - 2026-09-22

Added

· 引用追问（挂起 AI 回复 → 输入框上方引用条）
· 用户消息交互分工：单击 = 修改重发；单击图片 = 全屏大图；长按 = 消息菜单
· 多图消息 content 数组格式

---

[0.2.0] - 2026-09-22

Added

· Markdown 渲染强化
· 多会话历史管理 + 会话搜索
· 上下文自动压缩
· 图片附件与处理
· 会话导出 Markdown
· 插件系统（AI 工具插件：prompt / http / script）
· 崩溃恢复草稿
· 深色 / 浅色主题

---

[0.1.0] - 2026-09-22

首个内部开发版本。

Added

· Ai Office 基础框架（Java / Android API 19+）
· 文件读写工具（14 个）
· 联网搜索与网页抓取
· Markdown 渲染
· 多供应商 AI 接入（DeepSeek 为默认）
· 流式输出
· 工具调用系统
· 本地文件 @ 引用语法
· 后台完成通知

---

[Unreleased]

Planned

· 增强 AI 模型支持
· 更完善的文件管理界面
· 云端同步
· 高级搜索能力
· 自定义工具创建
· 与主流云服务集成
· 无障碍改进
· 多语言支持
· 高级导出选项