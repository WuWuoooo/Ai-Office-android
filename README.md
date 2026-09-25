**🌐 [English](README.md) | [简体中文](README_zh.md)**

# AI Office

An AI office assistant running on Android. APK size 323.07 KB, zero third-party dependencies, built purely with Java + native Android APIs.

## Highlights

- APK size 347.65 KB — most AI apps are tens of MB; Ai Office compresses it to less than 0.35 MB
- Zero third-party dependencies — no OkHttp, no Gson, no AndroidX, no Material Design; all code is based on native Android system APIs
- Pure Java + dynamically built UI — layout files contain only skeletons; all interfaces are built at runtime
- AIDE compatible — can compile and modify directly on the phone

## Features

### AI Chat

- Supports streaming output, deep thinking (reasoning_content), parallel tool calls
- Multiple providers: DeepSeek / OpenAI / Anthropic Claude / Moonshot (Kimi) / Zhipu GLM / SiliconFlow / Alibaba Qwen / custom OpenAI-compatible APIs
- Dual protocols: OpenAI-compatible protocol (/chat/completions) + Anthropic native protocol (/v1/messages)
- Independent vision model configuration: the main model handles text; when an image message appears, it automatically switches to a multimodal model; supports cross-provider setups (e.g. main DeepSeek, vision Zhipu GLM-4V)
- Adjustable thinking depth: Default / None / low / high / max
- Automatic context compression: long conversations automatically summarize early content; the original text is fully preserved; full context can be restored at any time

### File Operations (14 tools)

View directory, list directory tree, file info, read file by lines, write file, append, exact replace, create directory, move, rename, copy, delete, search file names, search file contents.

AI can read and write any file on your phone (file permission required).

### Web Search

Supports 8 search engines:

- Bing RSS (no key)
- DuckDuckGo (no key)
- Zhipu Web Search (key required)
- Tavily (key required)
- Brave Search (key required)
- Serper / Google (key required)
- SearXNG self-hosted (no key)
- Custom (depends)

Search results are displayed as cards, collapsible, tap to open the built-in browser. Web page reading also returns structured cards.

### Accessibility Phone Control

AI can operate other apps through the system accessibility service:

- Launch apps, read screen, tap controls, input text, scroll, back, go home
- When controls cannot be read from the screen (apps like WeChat that restrict reading), can switch to visual mode: take screenshot (MediaProjection), analyze element coordinates from the image, then return operation instructions; the app automatically converts them to real screen coordinates
- For sensitive operations such as payment, passwords, deletion, tools require AI to get user consent first

### Real-time Call (GLM-Realtime / OpenAI Realtime)

- Real-time voice: low-latency bidirectional audio over WebSocket; server-side VAD detects end of speech and triggers a reply; no separate ASR + TTS round-trip
- Real-time video: camera preview + periodic frame capture (2 fps) sent to AI; front/back camera switch + flashlight toggle
- Screen sharing: captures the current screen at 1 fps and streams it to AI (reuses ProjectionController)
- Tool calls during a call: AI can invoke any enabled tool (files / search / shell / JS / accessibility / plugins) and speaks the result
- Live captions: user speech and AI speech are transcribed on screen in real time
- Presets: Zhipu GLM-Realtime / OpenAI Realtime / Custom (WebSocket URL, model, protocol, auth style, voice)
- TTS (separate feature): supports custom API (e.g. Zhipu glm-tts) + system TTS fallback; can set "automatically read every reply" or manually tap to read

### Plugin System

Three plugin forms, placed under /sdcard/AI/plugins/ and take effect after restart:

1. AI tool plugins: prompt / http / script types, registered as functions callable by AI
2. UI extension plugins: hook into extension points such as menu / long press / attachment menu / settings page, can add custom entries
3. UI override plugin (ui_change): modify app colors — top bar, input bar, bubbles, code blocks, cards, drawer, send button, etc., 20+ color and size items, with configurable transparency

### Long-term Memory

Saves user preferences, commonly used paths, project background, etc. across sessions; AI refers to it in every conversation. Can be manually managed on the settings page.

### Session Management

Multiple sessions, history search, rename, export Markdown, share full text. You can set whether entering the app opens "last conversation" or "new conversation" by default.

### Interface

- Chat background image full coverage: top bar, status bar, message area, bottom input bar, navigation bar are all filled with the background image; top bar/input bar support transparency settings
- Dark / Light / Follow system, three themes
- Font scaling: Small / Standard / Large / Extra large / Huge
- Markdown rendering: headings, lists, tables, quotes, code blocks, links
- Multi-language: built-in 简体中文 / English; import custom language packs (JSON) to add more languages; export current language pack as template

## Tech Stack

- Language: Java
- Minimum support: Android 4.4 (API 19)
- UI: Android native View (android.widget.* / android.view.*)
- Network: HttpURLConnection
- JSON: org.json
- Markdown: self-developed parser
- Image processing: BitmapFactory / Bitmap
- Visual control: MediaProjection + ImageReader
- Accessibility: AccessibilityService + GestureDescription
- JS execution: built-in WebView (V8)
- TTS: MediaPlayer / TextToSpeech
- Real-time call: hand-written WebSocket (RFC6455) + PCM16 audio + Camera API / MediaProjection frame capture

## Why It Is So Small

1. No dependencies: no OkHttp (about 1 MB), no Gson (about 200 KB), no AndroidX (tens to hundreds of KB as needed), no Material (about 500 KB+)
2. Dynamic UI: layouts are built in code as much as possible; XML only contains skeletons, reducing resource files
3. Self-developed components: Markdown rendering, TTS playback, browser, announcement dialog, data-driven forms, etc. are all hand-written
4. Minimal icon resources: main UI uses Unicode characters + drawable shapes; only the real-time call screen ships a small set of PNG icons (16 files × 5 densities, a few KB in total)

## Installation

### Build from Source

1. Download the source code
2. Open the project with Android Studio / AIDE
3. Compile and run

### Using AIDE (recommended, because the size is small)

1. Install AIDE on your phone
2. Open the project directory and compile directly

## Configuration

First-time use requires configuration:

- AI provider: choose a preset (DeepSeek / OpenAI / Anthropic / Zhipu, etc.) or custom
- API address: presets fill it in automatically, and it can also be modified manually
- API Key: get it from the provider console
- Default model: e.g. deepseek-chat, glm-4-plus, claude-3-5-sonnet-20241022

Optional configuration:

- Vision API: fill in the vision model, independent address, independent Key, independent protocol (leave blank = reuse main configuration)
- Web search: choose a search engine, configure the corresponding Key or custom URL
- TTS speech synthesis: fill in TTS API address, Key, model, voice
- Real-time call: choose a provider (Zhipu GLM-Realtime / OpenAI Realtime / Custom), fill in WebSocket URL, model, key, protocol, voice
- Permissions: all files access permission + accessibility service

## Permission Description

- INTERNET — AI API, web search, web page fetching
- READ/WRITE_EXTERNAL_STORAGE — File read/write
- MANAGE_EXTERNAL_STORAGE — Android 11+ read/write /sdcard
- POST_NOTIFICATIONS — Background completion notifications
- RECORD_AUDIO — Voice calls
- CAMERA — Video calls, photos
- FLASHLIGHT — Flashlight in video mode
- FOREGROUND_SERVICE — Visual control projection session
- FOREGROUND_SERVICE_MEDIA_PROJECTION — Visual control / real-time call screen sharing (mandatory on Android 14+)

## Project Structure

app/src/main/java/com/ai/office/
- MainActivity.java              # Main UI + Agent loop + streaming rendering + session management
- SettingsActivity.java          # Settings home (category cards)
- SettingsDetailActivity.java    # Settings secondary page (dynamically built by category)
- BaseActivity.java              # Base class for font scaling / theme / system bars
- AiClient.java                  # AI streaming client (including tool definitions)
- AIProvider.java                # AI provider presets
- SearchProvider.java            # Search provider presets
- UiOverrides.java               # UI override layer (for plugins to change colors)
- FileToolExecutor.java          # 14 file tools
- WebToolExecutor.java           # Web search + web page fetching
- ShellToolExecutor.java         # Local shell
- JsToolExecutor.java            # JS execution (WebView V8)
- AccessibilityController.java   # Accessibility control
- AutoAccessibilityService.java  # Accessibility service
- ProjectionController.java      # MediaProjection screenshot core
- ProjectionService.java         # Projection foreground service
- PluginManager.java             # Plugin scanning and execution
- UiPluginDialog.java            # Data-driven plugin form
- MemoryStore.java               # Long-term memory
- MarkdownView.java              # Markdown rendering
- Notifier.java                  # Notifications
- TtsHelper.java                 # TTS reading
- VoiceCallActivity.java         # Real-time call (GLM-Realtime / OpenAI Realtime)
- RealtimeConfig.java            # Real-time call provider presets
- RealtimeClient.java            # Realtime event wrapper
- WebSocketClient.java           # Hand-written RFC6455 WebSocket client
- WebViewActivity.java           # Built-in browser
- LanguageManager.java          # Language pack loader (custom / assets / builtin fallback)
- UiUtils.java                   # Utility class

## Plugin Examples

JSON files placed under /sdcard/AI/plugins/.

### AI tool plugin (registered as an AI function)

    {
      "name": "translate",
      "description": "Translate Chinese into English",
      "type": "prompt",
      "parameters": {
        "text": { "type": "string", "description": "Chinese text to translate" }
      },
      "prompt": "Please translate the following Chinese into idiomatic English, output only the translation:\n{{text}}"
    }

### UI extension plugin (adds a menu entry)

    {
      "type": "ui",
      "name": "weather",
      "title": "Check weather",
      "extension": "input_plus",
      "fields": [
        { "key": "city", "label": "City", "type": "text", "required": true }
      ],
      "action": {
        "kind": "prompt",
        "template": "Help me check the weather in {{city}} for the next three days"
      }
    }

### UI override plugin (changes colors)

    {
      "type": "ui",
      "name": "theme_pink",
      "title": "Switch to pink theme with one tap",
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

## Known Limitations

- No Python environment: Android native shell has no Python; when scripts are needed, use the built-in run_js (V8 engine)
- Visual mode depends on MediaProjection: Android 14+ requires a foreground service; an authorization prompt appears on first use
- Accessibility reading is restricted: some apps (such as WeChat) restrict accessibility node reading, so you need to switch to visual mode
- Real-time call depends on server protocol: the target service must implement OpenAI Realtime protocol or Zhipu GLM-Realtime protocol (event names differ slightly; RealtimeConfig handles both)

## Design Principles

- Zero dependencies: do not introduce any third-party libraries, reducing size and lowering conflicts
- Data-driven: plugins, settings items, and menu entries can all be described by JSON, making extension easy
- Secure by default: shell / accessibility / script plugins are disabled by default and must be explicitly enabled by the user
- User-visible: all tool calls are shown in the conversation as collapsible panels, and operations are auditable

## Contact

If you have questions, suggestions, or bug reports, feedback is welcome.

E-mail: 938406403@qq.com

License: MIT License
