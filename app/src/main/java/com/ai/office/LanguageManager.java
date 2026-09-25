package com.ai.office;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 语言管理器：
 *   1) custom_ 开头的 id → filesDir/lang/<id>.json
 *   2) 其余 id → assets/lang/<id>.json
 *   3) 读不到 → 代码内置兜底（保证功能一定可用）
 *
 * t(ctx, key, default) 找不到 key 返回 default。
 */
public class LanguageManager {

    private static final String PREFS = "ai_office_config";
    private static final String KEY_LANG = "language_code";

    public static final String LANG_ZH = "zh-CN";
    public static final String LANG_EN = "en-US";

    private static volatile JSONObject sCurrent = null;
    private static volatile String sCurrentId = null;
    private static volatile String sCurrentSource = "";

    /**
     * 内置英文语言包（assets 读不到时的兜底）。
     * 格式：一行一条，"key|value"。
     * 约束：value 中绝不出现 | " \ 及换行，避免任何转义问题。
     */
    private static final String[] BUILTIN_EN = {
        "app_name|Ai Office",
        "common_ok|OK",
        "common_cancel|Cancel",
        "common_close|Close",
        "common_delete|Delete",
        "common_share|Share",
        "common_copy|Copy",
        "common_edit|Edit",
        "common_save|Save",
        "common_add|Add",
        "common_rename|Rename",
        "common_done|Done",
        "common_know|Got it",
        "common_yes|Yes",
        "common_no|No",
        "settings|Settings",
        "settings_section_ai|AI & Models",
        "settings_section_voice|Voice",
        "settings_section_app|App",
        "settings_section_data|Data",
        "settings_section_system|System",
        "settings_row_ai|AI Access",
        "settings_row_vision|Vision API",
        "settings_row_search|Web Search",
        "settings_row_tts|TTS",
        "settings_row_realtime|Realtime Call",
        "settings_row_generate|Generation",
        "settings_row_appearance|Appearance",
        "settings_row_toggle|Toggles",
        "settings_row_memory|Long-term Memory",
        "settings_row_prompt|System Prompt",
        "settings_row_plugin|Plugins",
        "settings_row_permission|Permissions",
        "settings_row_balance|Balance",
        "settings_row_language|Language",
        "lang_title|Language",
        "lang_builtin|Built-in Languages",
        "lang_custom|Custom Language Packs",
        "lang_import|Import language pack (.json)",
        "lang_export|Export current language pack",
        "lang_delete|Delete language pack",
        "lang_hint|Language pack format: JSON with name and strings. Export a built-in pack as a template, translate it, then re-import. Switching applies to Settings immediately; other pages take effect when reopened.",
        "lang_none_custom|(No custom language packs)",
        "lang_switched|Language switched",
        "lang_export_done|Exported to: ",
        "lang_import_done|Language pack imported: ",
        "lang_import_fail|Import failed: ",
        "lang_delete_title|Delete language pack %s?",
        "lang_share|Share language pack",
        "lang_choose_file|Choose language pack",
        "lang_no_picker|Cannot open file picker: ",
        "ai_title|AI Access",
        "ai_section_connection|Connection",
        "ai_provider|AI Provider",
        "ai_base_url|API URL",
        "ai_api_key|API Key",
        "ai_default_model|Default Model",
        "ai_model_presets|Switchable Model List",
        "ai_hint_presets|Model list uses comma separation; tap the model name in the top bar to switch quickly.",
        "ai_section_protocol|Protocol",
        "ai_protocol|Protocol",
        "ai_hint_protocol|The protocol is set automatically when switching providers. Anthropic uses /v1/messages; others use /chat/completions.",
        "ai_pick_provider|Choose AI Provider",
        "ai_edit_base_url|API URL",
        "ai_edit_api_key|API Key",
        "ai_edit_model|Default Model",
        "ai_edit_presets|Model list (comma-separated)",
        "ai_unset|(Not set)",
        "vision_title|Vision API",
        "vision_section|Vision Model",
        "vision_model|Vision Model",
        "vision_base_url|Vision API URL",
        "vision_api_key|Vision API Key",
        "vision_protocol|Vision Protocol",
        "vision_hint|Leave blank to reuse the main AI config. When an image is sent, the app switches to the vision model automatically.",
        "vision_edit_model|Vision Model",
        "vision_edit_base_url|Vision API URL",
        "vision_edit_api_key|Vision API Key",
        "vision_pick_protocol|Vision Protocol",
        "search_title|Web Search",
        "search_section_engine|Search Engine",
        "search_provider|Search Provider",
        "search_api_key|Search API Key",
        "search_override_url|Request URL (overridable)",
        "search_override_body|Request Body Template",
        "search_count|Result Count",
        "search_section_reader|Web Reader (fetch_url)",
        "search_reader_format|Return Format",
        "search_section_custom|Custom Provider Parameters",
        "search_custom_method|Request Method",
        "search_custom_url|Request URL",
        "search_custom_body|Request Body",
        "search_custom_auth_header|Auth Header Name",
        "search_custom_auth_prefix|Auth Header Prefix",
        "search_custom_result_path|Result Array Path",
        "search_custom_title_key|Title Field",
        "search_custom_content_key|Content Field",
        "search_custom_link_key|Link Field",
        "search_pick_provider|Choose Search Engine",
        "search_edit_override_url|Request URL (empty = built-in)",
        "search_edit_override_body|Request body template (empty = built-in)",
        "search_edit_count|Result count (1-50)",
        "search_edit_custom_url|Request URL (supports query, count, api_key placeholders)",
        "search_edit_custom_body|Request body (supports query_raw, count, api_key placeholders)",
        "search_edit_custom_auth_header|Auth header name (e.g. Authorization)",
        "search_edit_custom_auth_prefix|Auth header prefix (e.g. Bearer space)",
        "search_edit_custom_result_path|Result array path (e.g. web.results)",
        "search_edit_custom_title_key|Title Field",
        "search_edit_custom_content_key|Content Field",
        "search_edit_custom_link_key|Link Field",
        "search_hint|Bing / DuckDuckGo are free and need no key. For other providers, set the key in Search API Key.",
        "realtime_title|Realtime Call",
        "realtime_section|Realtime Model",
        "realtime_provider|Provider",
        "realtime_url|WebSocket URL",
        "realtime_model|Model",
        "realtime_api_key|API Key",
        "realtime_protocol|Protocol",
        "realtime_voice|Voice",
        "realtime_hint|Empty URL / model / key will fall back to provider presets; if key is still empty, the main AI key is used. GLM-Realtime: wss://open.bigmodel.cn/api/paas/v4/realtime, model glm-realtime-flash. OpenAI Realtime: wss://api.openai.com/v1/realtime, model gpt-4o-realtime-preview.",
        "realtime_pick_provider|Choose Realtime Provider",
        "realtime_edit_url|WebSocket URL (empty = built-in)",
        "realtime_edit_model|Model (empty = built-in)",
        "realtime_edit_key|Realtime API Key (empty = main config)",
        "realtime_edit_voice|Voice",
        "tts_title|TTS",
        "tts_section|TTS",
        "tts_enabled|Enable TTS",
        "tts_auto_read|Auto-read AI replies",
        "tts_api_url|TTS API URL",
        "tts_api_key|TTS API Key",
        "tts_model|Model",
        "tts_voice|Voice",
        "tts_format|Return Format",
        "tts_speed|Speed",
        "tts_hint|Enable TTS is the master switch. Auto-read AI replies controls whether each reply is read aloud; when off, tap the Speak button next to a reply. Zhipu glm-tts example: URL https://open.bigmodel.cn/api/paas/v4/audio/speech, model glm-tts, voice tongtong, format wav. Leave API URL empty to use system TTS.",
        "tts_unset|(Not set, will use system TTS)",
        "tts_edit_url|TTS API URL (empty = system TTS)",
        "tts_edit_key|TTS API Key",
        "tts_edit_model|TTS Model",
        "tts_edit_voice|TTS Voice",
        "tts_pick_format|TTS Return Format",
        "tts_edit_speed|Speed (empty = not sent)",
        "tts_speed_default|(Default)",
        "gen_title|Generation",
        "gen_section_thinking|Thinking and Tools",
        "gen_thinking|Thinking Depth",
        "gen_max_rounds|Max Tool Rounds",
        "gen_retry|Retry Count",
        "gen_delete_sec|Delete Confirmation Seconds",
        "gen_startup|Open on Launch",
        "gen_section_context|Context",
        "gen_auto_compress|Auto-compress Context",
        "gen_compress_rounds|Compress Every N Rounds",
        "gen_hint|Choose None to suppress reasoning output for models that support it. Max rounds 0 means unlimited.",
        "gen_edit_max_rounds|Max rounds (0 = unlimited)",
        "gen_edit_retry|Retry count",
        "gen_edit_delete_sec|Delete confirmation seconds (0 = no confirm)",
        "gen_edit_compress_rounds|Compress every N rounds",
        "gen_pick_thinking|Thinking Depth",
        "gen_pick_startup|Open on Launch",
        "gen_startup_last|Last chat",
        "gen_startup_new|New chat",
        "gen_thinking_default|Default",
        "gen_thinking_off|Off",
        "appearance_title|Appearance",
        "appearance_section|Appearance",
        "appearance_theme|Theme",
        "appearance_font|Font Size",
        "appearance_bg|Chat Background",
        "appearance_bg_set|Set",
        "appearance_bg_unset|Not set",
        "appearance_pick_bg|Chat Background",
        "appearance_from_gallery|Choose from gallery",
        "appearance_clear_bg|Clear background",
        "appearance_pick_theme|Theme (fully effective after restart)",
        "appearance_pick_font|Font Size",
        "appearance_theme_system|Follow system",
        "appearance_theme_light|Light",
        "appearance_theme_dark|Dark",
        "appearance_font_system|Follow system",
        "appearance_font_small|Small",
        "appearance_font_standard|Standard",
        "appearance_font_large|Large",
        "appearance_font_xl|Extra Large",
        "appearance_font_xxl|Huge",
        "appearance_bg_cleared|Cleared",
        "appearance_bg_set_ok|Background set",
        "appearance_bg_fail|Background save failed: ",
        "appearance_no_gallery|Cannot open gallery",
        "appearance_choose_bg|Choose Chat Background",
        "toggle_title|Toggles",
        "toggle_section_behavior|Behavior",
        "toggle_notify|Background completion notification",
        "toggle_image_as_file|Send image as local file path",
        "toggle_section_advanced|Advanced Permissions",
        "toggle_script|Allow script plugins",
        "toggle_shell|Allow AI to run commands (shell / JS)",
        "toggle_a11y|Allow AI phone control (accessibility)",
        "toggle_hint|Script plugins can execute shell commands. Enable only if you trust the source.",
        "toggle_script_on|Script plugins enabled. Restart the app to take effect.",
        "toggle_script_off|Script plugins disabled",
        "toggle_shell_on|Enabled. Effective after returning to main screen.",
        "toggle_shell_off|Command execution disabled",
        "toggle_a11y_on|Enabled. Please enable the accessibility service in Permissions.",
        "toggle_a11y_off|Phone control disabled",
        "memory_title|Long-term Memory",
        "memory_section|Long-term Memory",
        "memory_manage|Manage entries",
        "memory_hint|AI refers to these memories in every conversation. You can add entries manually here, or tap an entry to delete it.",
        "memory_picker_title|Long-term Memory (%d entries)",
        "memory_add_new|+ Add memory",
        "memory_edit_title|Edit Memory",
        "memory_add_title|Add Memory",
        "memory_key_hint|Key (letters/digits, e.g. user_pref)",
        "memory_value_hint|Content",
        "memory_saved|Saved",
        "memory_deleted|Deleted",
        "memory_delete_confirm|Delete memory %s?",
        "memory_key_required|Key cannot be empty",
        "memory_edit|Edit",
        "memory_delete|Delete",
        "prompt_title|System Prompt",
        "prompt_section|System Prompt",
        "prompt_edit|Edit system prompt",
        "prompt_default|Default",
        "prompt_custom|Customized",
        "prompt_hint|Leave empty to use the built-in default prompt.",
        "prompt_dialog_title|System Prompt (empty = default)",
        "plugin_title|Plugins",
        "plugin_section_loaded|Loaded Plugins",
        "plugin_view|View loaded plugins",
        "plugin_section_ui|UI Plugin Actions",
        "plugin_section_override|UI Overrides",
        "plugin_view_override|View current UI overrides",
        "plugin_clear_override|Clear all UI overrides",
        "plugin_hint|Place .json plugins under /sdcard/AI/plugins/ and restart the app. UI plugins with settings_item extension appear above. Plugins with action.kind = ui_change can override colors.",
        "plugin_override_none|(None)",
        "plugin_override_count|%d items",
        "plugin_clear_confirm_title|Clear all UI overrides?",
        "plugin_clear_confirm_msg|Colors and sizes will be restored to defaults (other settings are unaffected). Tip: return to the main screen to see the effect.",
        "plugin_cleared|Cleared. Return to main screen to take effect.",
        "plugin_read_fail|Read failed: ",
        "plugin_result_title|Plugin Result",
        "plugin_exec_script_title|Run script?",
        "plugin_exec_script_msg|This plugin will run a local shell script. Only continue if you trust the source.",
        "plugin_exec|Run",
        "permission_title|Permissions",
        "permission_section|System Permissions",
        "permission_all_files|Request All Files Access",
        "permission_a11y|Enable Accessibility Service",
        "permission_hint|Android 11+ needs All Files Access to read/write /sdcard; AI phone control needs the accessibility service.",
        "permission_no_a11y|Cannot open accessibility settings",
        "permission_no_all_files|Cannot open permission settings",
        "permission_not_needed|Not needed on this Android version",
        "balance_title|Balance",
        "balance_section|Balance",
        "balance_url|Balance API URL",
        "balance_query|Query now",
        "balance_hint|Usually the provider balance endpoint, e.g. DeepSeek /user/balance.",
        "balance_edit_url|Balance API URL",
        "balance_result|Balance Result",
        "balance_querying|Querying...",
        "balance_url_required|Please set Balance API URL first",
        "balance_fail|Query failed: ",
        "main_welcome_title|Hi, how can I help?",
        "main_welcome_sub1|I can read/write files, search the web, process photos",
        "main_welcome_sub2|Type @ to reference local files",
        "main_input_hint|Send message to AI Office, use @ to reference files",
        "main_send|Send",
        "main_stop|Stop",
        "main_new_chat|+ New Chat",
        "main_drawer_title|Chats",
        "main_search_session|Search chats",
        "main_drawer_hint|Tap to switch, long-press to rename/delete/export",
        "main_no_sessions|No chat history",
        "main_no_match_sessions|No matching chats",
        "main_remove_image|Remove image",
        "main_quote_cancel|Cancel quote",
        "main_generating_please_stop|Generating, please stop first",
        "main_load_earlier|%d earlier messages, tap to load",
        "more_title|More",
        "more_export_md|Export current chat as Markdown",
        "more_share_session|Share full chat",
        "more_copy_last|Copy last reply",
        "more_import_md|Import Markdown chat",
        "more_import_json|Import JSON chat",
        "more_compress|Compress context",
        "more_restore_ctx|Restore full context (clear summary)",
        "more_clear_session|Clear current chat",
        "more_open_settings|Open settings",
        "session_menu_switch|Switch to this chat",
        "session_menu_rename|Rename",
        "session_menu_export|Export as Markdown",
        "session_menu_share|Share full text",
        "session_menu_delete|Delete",
        "session_rename_title|Rename Chat",
        "session_delete_confirm|Delete this chat record?",
        "session_default_title|Chat",
        "session_new_chat|New Chat",
        "session_loading|Loading...",
        "msg_menu_edit|Edit and resend",
        "msg_menu_copy|Copy text",
        "msg_menu_view_image|View image",
        "msg_menu_delete|Delete this and after",
        "msg_menu_title|This message",
        "msg_delete_confirm|Delete this message and all after it?",
        "msg_edit_title|Edit my message",
        "msg_edit_title_with_image|Edit my message (with image)",
        "msg_resend|Resend",
        "msg_empty|Content cannot be empty",
        "ai_menu_title|This reply",
        "ai_menu_copy_reply|Copy this reply",
        "ai_menu_copy_reasoning|Copy reasoning",
        "ai_menu_share|Share this reply",
        "ai_menu_regen|Regenerate from here",
        "ai_menu_delete_round|Delete this round",
        "tool_panel_call|Call tool ",
        "tool_panel_result|Tool result ",
        "tool_panel_collapse| (tap to collapse)",
        "reasoning_thinking|Thinking deeply...",
        "reasoning_done|Thought deeply",
        "reasoning_done_sec|Thought for %d seconds",
        "stopped_note|Generation stopped",
        "stopped_recover|Last generation was interrupted, partial content preserved",
        "stopped_continue|Continue",
        "stopped_regen|Regenerate",
        "stopped_error|Error",
        "action_row_quote|Quote",
        "action_row_speak|Speak",
        "action_row_copy|Copy",
        "action_row_regen|Regenerate",
        "action_row_more|More",
        "ask_dialog_title|AI is asking",
        "ask_dialog_hint|Type your answer here...",
        "ask_answer|Answer",
        "ask_self_input|Type my own",
        "ask_cancel|Cancel answer",
        "delete_confirm_title|Confirm Delete",
        "delete_confirm_now|Delete now",
        "delete_confirm_reject|Reject",
        "file_picker_up|..  Back to parent",
        "file_picker_done|Done",
        "file_picker_clear|Clear selection",
        "file_picker_cleared|Cleared, you can reselect",
        "file_picker_selected|%d file(s) selected",
        "file_picker_ref|Referenced %d file(s)",
        "file_picker_no_browser|Cannot open file browser",
        "attach_title|Add Content",
        "attach_gallery|Choose from gallery",
        "attach_camera|Take photo",
        "attach_ref_file|Reference local file (same as @)",
        "attach_import|Import chat",
        "attach_realtime|Realtime call",
        "attach_no_gallery|Cannot open gallery: ",
        "attach_no_camera|No camera app found",
        "attach_no_camera2|Cannot open camera: ",
        "realtime_not_configured_title|Realtime call is not fully configured",
        "realtime_not_configured_msg|Please configure the WebSocket URL / model / API key under Settings, Voice, Realtime Call first. Go configure now?",
        "realtime_go_config|Configure",
        "realtime_no_open|Cannot open call screen: ",
        "image_dialog_hint|Tap anywhere to close. Long-press to save to AI/exports",
        "image_saved|Saved: ",
        "image_save_fail|Save failed: ",
        "image_decode_fail|Image decode failed",
        "image_read_fail|Image read failed",
        "image_process_fail|Image processing failed",
        "image_attached|Image attached. Add more or send now.",
        "image_count|%d image(s) selected",
        "image_none|No image selected",
        "model_picker_title|Choose Model",
        "model_picker_go_settings|Edit in Settings",
        "model_switched|Switched to ",
        "model_no_picker|Cannot open model list",
        "tts_not_enabled_title|TTS is not enabled",
        "tts_not_enabled_msg|Please enable TTS under Settings, TTS. If no API URL is set, the system TTS will be used.",
        "tts_go_enable|Enable now",
        "tts_start|Speaking...",
        "tts_stopped|Stopped",
        "tts_fail|Speak failed: ",
        "tts_no_content|This reply has no content to speak",
        "quote_no_text|This reply has no text content",
        "quote_prefix|Quote #%d: ",
        "input_required|Please enter content",
        "generating|Generating, please stop first",
        "generating_short|Generating",
        "no_context|No usable context",
        "round_deleted|Round deleted",
        "no_reply_to_copy|No reply to copy yet",
        "no_reasoning|This reply has no reasoning",
        "no_export_content|Nothing to export",
        "no_share_content|Nothing to share",
        "export_done|Exported",
        "export_fail|Export failed: ",
        "share_fail|Share failed: ",
        "share_to|Share to",
        "chat_empty|Clear current chat content?",
        "chat_clear|Clear",
        "err_init|Init failed: ",
        "err_too_short|Chat too short to compress",
        "err_setting_load|Settings load failed: ",
        "err_no_open_settings|Cannot open settings: ",
        "err_no_web|Cannot open browser: ",
        "err_no_file_picker|Cannot open file picker: ",
        "err_no_import_menu|Cannot open import menu: ",
        "err_md_parse|Parse Markdown failed: ",
        "err_import_empty|Imported chat is empty",
        "err_import_ok|Chat imported",
        "err_import_read|Failed to read chat content",
        "import_title|Import Chat",
        "import_md|Import Markdown chat",
        "import_json|Import JSON chat",
        "import_choose_md|Choose Markdown file",
        "import_choose_json|Choose JSON file",
        "compress_note|Context is long, compressing early messages into a summary...",
        "compress_done|Context compressed: original chat preserved; AI will see summary plus recent part.",
        "compress_fail|Context compression failed, continuing with full context",
        "ctx_restored|Full context restored (compression summary cleared)",
        "ref_image_invalid|Chat background is no longer valid, please choose again in Settings",
        "no_bg|Not set",
        "more_menu_view_loaded_plugins|View loaded plugins",
        "plugin_count_ai|AI tool plugins: %d",
        "plugin_count_ui|UI extension plugins: %d",
        "plugin_section_ai_header|-- AI Tool Plugins --",
        "plugin_section_ui_header|-- UI Extension Plugins --",
        "plugin_dir|Plugin dir: /sdcard/AI/plugins/",
        "plugin_ext_list|extension options: main_menu, message_long_press, input_plus, settings_item, toolbar",
        "plugin_kind_list|action.kind options: prompt, http, script, ui_change",
        "ui_override_list_title|Current UI Overrides",
        "ui_override_none|(No overrides)",
        "plugin_dialog_empty|(This plugin has no parameters. Tap OK to run.)",
        "plugin_dialog_exec|Run",
        "plugin_dialog_required|Required fields cannot be empty: ",
        "plugin_dialog_collect_fail|Collect args failed: ",
        "plugin_dialog_open_fail|Open plugin dialog failed: ",
        "more_menu_new_file|New file",
        "loading|Loading...",
        "stats_input|In",
        "stats_output|Out",
        "stats_cache|Cache",
        "stats_turns|Turns",
        "md_role_me|Me",
        "md_role_ai|AI Office",
        "md_role_ai_short|AI",
        "md_role_user_short|User",
        "md_reasoning|Reasoning",
        "md_tool_result|Tool result",
        "md_role_chat|Conversation",
        "card_search_results|Search results",
        "card_reader|Web page: ",
        "card_open_browser|Open in browser",
        "realtime_status_connecting|Connecting...",
        "realtime_status_connected|Connected",
        "realtime_status_mic_off|Mic off",
        "realtime_status_in_call|In call",
        "realtime_status_cam_off|Camera off",
        "realtime_status_cam_on|Video on",
        "realtime_status_screen_off|Screen share stopped",
        "realtime_status_screen_on|Sharing screen",
        "realtime_system_prompt|You are an AI office assistant running on a phone. The user speaks to you by voice, please reply concisely and naturally. When the user needs to read files, search the web, or control the phone, you may call tools. This is a real-time call, keep replies short."
    };

    private static File langDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "lang");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static String getCurrentId(Context ctx) {
        try {
            String v = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, LANG_ZH);
            if (v == null || v.trim().length() == 0) return LANG_ZH;
            return v.trim();
        } catch (Throwable t) { return LANG_ZH; }
    }

    public static void setCurrentId(Context ctx, String id) {
        try {
            if (id == null || id.trim().length() == 0) id = LANG_ZH;
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
               .putString(KEY_LANG, id.trim()).commit();
            sCurrent = null;
            sCurrentId = null;
            sCurrentSource = "";
        } catch (Throwable t) {}
    }

    public static List<String[]> listBuiltin() {
        List<String[]> out = new ArrayList<String[]>();
        out.add(new String[]{LANG_ZH, "简体中文"});
        out.add(new String[]{LANG_EN, "English"});
        return out;
    }

    public static List<String[]> listCustom(Context ctx) {
        List<String[]> out = new ArrayList<String[]>();
        try {
            File[] fs = langDir(ctx).listFiles();
            if (fs == null) return out;
            for (int i = 0; i < fs.length; i++) {
                if (!fs[i].isFile()) continue;
                if (!fs[i].getName().endsWith(".json")) continue;
                try {
                    String txt = readFile(fs[i], 2 * 1024 * 1024);
                    if (txt == null) continue;
                    JSONObject o = new JSONObject(txt);
                    String id = fs[i].getName().substring(0, fs[i].getName().length() - 5);
                    String name = o.optString("name", id);
                    out.add(new String[]{id, name});
                } catch (Throwable t) {}
            }
        } catch (Throwable t) {}
        return out;
    }

    /** 诊断：返回 {id, source, count}，source ∈ custom / assets / builtin / err */
    public static String[] diagnose(Context ctx) {
        try {
            ensureLoaded(ctx);
            int count = sCurrent == null ? 0 : sCurrent.length();
            return new String[]{
                sCurrentId == null ? "" : sCurrentId,
                sCurrentSource == null ? "" : sCurrentSource,
                String.valueOf(count)};
        } catch (Throwable t) {
            return new String[]{"", "err", "0"};
        }
    }

    private static synchronized void ensureLoaded(Context ctx) {
        String id = getCurrentId(ctx);
        if (sCurrent != null && id.equals(sCurrentId)) return;

        JSONObject o = null;
        String source = "";

        // 1) 自定义
        if (id.startsWith("custom_")) {
            try {
                File f = new File(langDir(ctx), id + ".json");
                if (f.exists()) {
                    String txt = readFile(f, 2 * 1024 * 1024);
                    if (txt != null) { o = new JSONObject(txt); source = "custom"; }
                }
            } catch (Throwable t) { o = null; }
        }

        // 2) assets
        if (o == null) {
            try {
                InputStream is = ctx.getAssets().open("lang/" + id + ".json");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                is.close();
                o = new JSONObject(new String(bos.toByteArray(), "UTF-8"));
                source = "assets";
            } catch (Throwable t) { o = null; }
        }

        // 3) 内置兜底
        if (o == null) {
            o = builtinPack(id);
            source = "builtin";
        }

        JSONObject strings = o == null ? null : o.optJSONObject("strings");
        sCurrent = strings == null ? new JSONObject() : strings;
        sCurrentId = id;
        sCurrentSource = source;
    }

    private static JSONObject builtinPack(String id) {
        JSONObject strings = LANG_EN.equals(id) ? builtinEnStrings() : builtinZhStrings();
        JSONObject o = new JSONObject();
        try { o.put("strings", strings); } catch (Throwable t) {}
        return o;
    }

    public static String t(Context ctx, String key, String def) {
        try {
            if (key == null || key.length() == 0) return def;
            ensureLoaded(ctx);
            if (sCurrent == null) return def;
            String v = sCurrent.optString(key, "");
            if (v.length() == 0) return def;
            return v;
        } catch (Throwable t) { return def; }
    }

    public static String importFromUri(Context ctx, Uri uri) {
        try {
            if (uri == null) return "错误: 未选择文件";
            InputStream is = ctx.getContentResolver().openInputStream(uri);
            if (is == null) return "错误: 无法打开文件";
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = is.read(buf)) > 0) {
                total += n;
                if (total > 4 * 1024 * 1024) { is.close(); return "错误: 文件过大 (>4MB)"; }
                bos.write(buf, 0, n);
            }
            is.close();

            String txt = new String(bos.toByteArray(), "UTF-8");
            JSONObject o = new JSONObject(txt);
            JSONObject strings = o.optJSONObject("strings");
            if (strings == null || strings.length() == 0) return "错误: 缺少 strings 字段或内容为空";

            String name = o.optString("name", "未命名语言");
            String id = "custom_" + System.currentTimeMillis();
            o.put("id", id);
            o.put("name", name);

            File f = new File(langDir(ctx), id + ".json");
            FileOutputStream fos = new FileOutputStream(f);
            fos.write(o.toString().getBytes("UTF-8"));
            fos.close();
            return id;
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        }
    }

    public static String exportCurrent(Context ctx) {
        try {
            String id = getCurrentId(ctx);
            File dir = new File(android.os.Environment.getExternalStorageDirectory(), "AI/lang");
            if (!dir.exists()) dir.mkdirs();
            File dst = new File(dir, id + ".json");

            byte[] data = null;
            if (id.startsWith("custom_")) {
                File src = new File(langDir(ctx), id + ".json");
                if (src.exists()) data = readBytes(src);
            }
            if (data == null) {
                try {
                    InputStream is = ctx.getAssets().open("lang/" + id + ".json");
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                    is.close();
                    data = bos.toByteArray();
                } catch (Throwable t) { data = null; }
            }
            if (data == null) {
                JSONObject strings = LANG_EN.equals(id) ? builtinEnStrings() : builtinZhStrings();
                JSONObject o = new JSONObject();
                o.put("id", id);
                o.put("name", LANG_EN.equals(id) ? "English" : "简体中文");
                o.put("author", "AI Office");
                o.put("version", 1);
                o.put("strings", strings);
                data = o.toString().getBytes("UTF-8");
            }
            FileOutputStream fos = new FileOutputStream(dst);
            fos.write(data);
            fos.close();
            return dst.getAbsolutePath();
        } catch (Throwable t) {
            return "错误: " + t.getMessage();
        }
    }

    public static boolean deleteCustom(Context ctx, String id) {
        try {
            if (id == null || !id.startsWith("custom_")) return false;
            File f = new File(langDir(ctx), id + ".json");
            boolean ok = f.delete();
            if (getCurrentId(ctx).equals(id)) setCurrentId(ctx, LANG_ZH);
            return ok;
        } catch (Throwable t) { return false; }
    }

    // ============================================================
    // 内置兜底语言包
    // ============================================================

    private static JSONObject builtinEnStrings() {
        JSONObject s = new JSONObject();
        try {
            for (int i = 0; i < BUILTIN_EN.length; i++) {
                String line = BUILTIN_EN[i];
                int sep = line.indexOf('|');
                if (sep <= 0) continue;
                String k = line.substring(0, sep);
                String v = line.substring(sep + 1);
                s.put(k, v);
            }
        } catch (Throwable t) {}
        return s;
    }

    private static JSONObject builtinZhStrings() {
        JSONObject s = new JSONObject();
        try {
            // 中文兜底时，其实 t(ctx, key, 中文默认) 已经能用，
            // 这里只填少量关键项，其余走 t() 的第二参数默认值。
            s.put("settings", "设置");
            s.put("lang_title", "语言");
            s.put("lang_switched", "已切换语言");
            s.put("main_send", "发送");
            s.put("main_stop", "停止");
            s.put("common_cancel", "取消");
            s.put("common_ok", "确定");
            s.put("common_save", "保存");
        } catch (Throwable t) {}
        return s;
    }

    // ============================================================
    // 工具
    // ============================================================

    private static String readFile(File f, int maxBytes) {
        try {
            long len = f.length();
            if (len <= 0) return null;
            if (len > maxBytes) len = maxBytes;
            FileInputStream fis = new FileInputStream(f);
            byte[] buf = new byte[(int) len];
            int off = 0;
            while (off < buf.length) { int r = fis.read(buf, off, buf.length - off); if (r < 0) break; off += r; }
            fis.close();
            return new String(buf, 0, off, "UTF-8");
        } catch (Throwable t) { return null; }
    }

    private static byte[] readBytes(File f) {
        try {
            FileInputStream fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = fis.read(buf)) > 0) bos.write(buf, 0, n);
            fis.close();
            return bos.toByteArray();
        } catch (Throwable t) { return null; }
    }
}