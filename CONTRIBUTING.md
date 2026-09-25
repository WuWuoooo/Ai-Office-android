Contributing to Ai Office

感谢你对 Ai Office 的兴趣！本文档说明如何为项目做出贡献。

Thank you for your interest in contributing to Ai Office! This document explains how to contribute to the project.

---

English

Before You Start

Ai Office has some strict principles that any contribution must respect. Pull requests that violate these will be rejected regardless of quality:

1. Zero third-party dependencies. No OkHttp, no Gson, no AndroidX, no Material Design, no Retrofit, no Glide, no Room, nothing. Everything must be built on the Android native SDK.
2. Pure Java, no Kotlin. The project is Java-only for AIDE compatibility.
3. No lambda expressions or method references. AIDE's compiler does not support them. All callbacks must be anonymous inner classes (new View.OnClickListener() { ... }).
4. Anonymous inner classes must capture final locals. All local variables and parameters referenced inside anonymous inner classes must be declared final (or be effectively final where AIDE accepts it — when in doubt, add final).
5. Dynamic UI. Layout XML files should contain only skeletons. All complex UI must be built in Java at runtime. Do not add new layout files unless absolutely necessary.
6. APK size matters. The current APK is around 233 KB. Every change should keep it as small as possible. Avoid adding resources, images, or new drawables unless they are tiny shape XML files.

If you are unsure whether a change is acceptable, open an issue and ask before writing code.

Getting Started

Prerequisites

· AIDE (recommended) — the project is designed to compile on-device with AIDE.
· Android Studio (optional) — for larger refactors, but you must ensure AIDE still compiles the result.
· Android SDK API Level 19+ (Android 4.4 KitKat).
· Java 7/8 compatible code only.
· Git.

Setup

1. Fork the repository.
2. Clone your fork:
   ```
   git clone https://github.com/WuWuoooo/Ai-Office-android.git
   cd Ai-Office-android
   ```
3. Add the upstream remote:
   ```
   git remote add upstream https://github.com/originalusername/ai-office.git
   ```
4. Open the project in AIDE or Android Studio.
5. Compile once to confirm the baseline works.

Development Workflow

1. Create a branch

```
git checkout -b feature/your-feature-name
```

Use a descriptive name: feature/voice-input, fix/keyboard-resize, docs/plugin-guide.

2. Write the code

Before writing, ask yourself:

· Does this require a third-party library? If yes, stop. Rewrite using native SDK.
· Does this use a lambda? Rewrite as an anonymous inner class.
· Does this introduce a new layout XML? Try building it in Java instead.
· Does this add a PNG/JPEG/WebP to res/drawable/? Use a shape XML or a Unicode character instead.
· Does this use AndroidX (androidx.*)? Replace with android.*.

3. Test on a real device

There is no unit test suite. Testing is manual:

· Compile with AIDE on a real device.
· Test on at least one API 23+ device and one API 30+ device.
· Test dark mode and light mode.
· Test with and without an active network.
· Test with a real AI provider API key.

4. Commit

Use the conventional commit format:

```
feat: add voice input support
fix: correct status bar color on Android 14
docs: update plugin JSON examples
refactor: split SettingsActivity into two activities
chore: bump version to 1.0.1
```

Type prefixes: feat, fix, docs, style, refactor, perf, test, chore.

5. Push and open a Pull Request

```
git push origin feature/your-feature-name
```

In the PR description include:

· What changed and why.
· Which files were touched.
· How you tested it (device model, Android version).
· Any screenshots or screen recordings for UI changes.

Coding Standards

Java style

· 4-space indentation. No tabs.
· Braces on the same line (if (x) {), not Allman style.
· Method names in lowerCamelCase, class names in UpperCamelCase, constants in UPPER_SNAKE_CASE.
· Keep methods under ~80 lines when possible.
· Wrap risky calls in try { ... } catch (Throwable t) {} where a failure should not crash the app (following the existing pattern throughout the codebase).

AIDE constraints

· No lambdas: view -> { ... } is not allowed. Use new View.OnClickListener() { @Override public void onClick(View v) { ... } }.
· No method references: this::method is not allowed.
· No java.util.function.* or stream API.
· No var (Java 10).
· No text blocks (Java 15).
· All variables captured by anonymous inner classes must be final.

Android constraints

· Do not import androidx.* or com.google.android.material.*.
· Use android.widget.* and android.view.* only.
· Use HttpURLConnection for HTTP. Do not use OkHttp.
· Use org.json.JSONObject / JSONArray for JSON. Do not use Gson.
· Use android.app.Activity, not androidx.appcompat.app.AppCompatActivity.
· Use AlertDialog, not MaterialAlertDialogBuilder.

Resource constraints

· Do not add PNG/JPEG/WebP assets. Use shape drawables or Unicode icons.
· Do not add custom fonts. Use system fonts.
· Keep the number of new XML files to a minimum.

Comments

· Chinese comments are fine; English is also fine. Follow the surrounding file's convention.
· Comment complex logic, especially regex, byte-level encoding handling, and Android version branches.
· Do not leave commented-out code in the final PR.

Bug Reports

When reporting a bug, include:

1. Steps to reproduce.
2. Expected behavior.
3. Actual behavior.
4. Device model and Android version.
5. AI provider and model in use (if relevant).
6. AIDE or Android Studio build log if it is a compile error.
7. Screenshots or screen recordings for UI issues.

Feature Requests

For feature requests, provide:

1. Clear description of the feature.
2. Concrete use case.
3. Whether it can be implemented without third-party dependencies.
4. Rough idea of which files would need to change.
5. Any existing issues or discussions.

Review Process

Every pull request is reviewed against these criteria:

· Does it compile on AIDE?
· Does it use any third-party dependency? (If yes, reject.)
· Does it use lambdas or method references? (If yes, reject.)
· Does it add binary resources? (If yes, justify or reject.)
· Does it change UI in a way that breaks dark mode?
· Does it introduce new permissions? If so, is the permission explained in the README?
· Is the commit message descriptive?

Small, focused PRs are merged faster than large ones. If you are planning a large change, open an issue first to discuss.

Community Guidelines

· Be respectful. Disagree with the code, not the person.
· Help newcomers. AIDE is not a mainstream environment; many people will need help.
· Do not push a PR that changes the project's core principles (zero dependencies, Java only, AIDE compatible) without prior discussion.
· Do not submit AI-generated code that you have not read and understood line by line.

Release Process

· Versioning follows SemVer: MAJOR.MINOR.PATCH.
· Version number is set in AndroidManifest.xml (android:versionCode and android:versionName).
· Every release is documented in CHANGELOG.md.
· Release commits are tagged vX.Y.Z.

Resources

· Android Developer Documentation
· AIDE Website
· HttpURLConnection Guide
· org.json Reference

Thank you for contributing to Ai Office!

---

简体中文

开始之前

Ai Office 有一些必须遵守的原则。违反以下任何一条的 PR 都会被拒绝，无论代码质量如何：

1. 零第三方依赖。 不用 OkHttp、不用 Gson、不用 AndroidX、不用 Material Design、不用 Retrofit、不用 Glide、不用 Room，什么都不用。所有代码必须基于 Android 原生 SDK。
2. 纯 Java，不用 Kotlin。 项目为 AIDE 兼容，仅支持 Java。
3. 禁止 Lambda 表达式和方法引用。 AIDE 的编译器不支持。所有回调必须写成匿名内部类（new View.OnClickListener() { ... }）。
4. 匿名内部类引用的变量必须 final。 匿名内部类里引用的所有局部变量和参数都要加 final（AIDE 对 effectively final 的判定不严格，拿不准就加 final）。
5. 动态 UI。 布局 XML 文件只放骨架，复杂 UI 一律在 Java 里运行时构建。不要随便新增布局文件。
6. APK 体积很重要。 当前 APK 约 233 KB。每次改动都要尽量保持体积不变。不要新增图片资源或大量 drawable，除非是极小的 shape XML。

如果不确定某个改动是否可以接受，先提 issue 讨论，再写代码。

开发环境

前置条件

· AIDE（推荐） —— 项目本身就是为了在手机上用 AIDE 编译而设计的。
· Android Studio（可选） —— 用于较大规模重构，但必须保证改完后 AIDE 依然能编译通过。
· Android SDK API Level 19+（Android 4.4 KitKat）。
· 只能写 Java 7/8 兼容的代码。
· Git。

配置

1. Fork 本仓库。
2. 克隆你的 fork：
   ```
   git clone https://github.com/WuWuoooo/Ai-Office-android.git
   cd Ai-Office-android
   ```
3. 添加上游仓库：
   ```
   git remote add upstream https://github.com/WuWuoooo/Ai-Office-android.git
   ```
4. 用 AIDE 或 Android Studio 打开工程。
5. 先编译一次，确认基线能跑通。

开发流程

1. 建分支

```
git checkout -b feature/your-feature-name
```

分支名要有描述性：feature/voice-input、fix/keyboard-resize、docs/plugin-guide。

2. 写代码

写之前先问自己：

· 是否需要引第三方库？如果是，停下来，用原生 SDK 重写。
· 是否用了 Lambda？改写成匿名内部类。
· 是否新增了 layout XML？尽量改成 Java 里构建。
· 是否往 res/drawable/ 里加了 PNG/JPEG/WebP？改成 shape XML 或 Unicode 字符。
· 是否用了 AndroidX（androidx.*）？换成 android.*。

3. 在真机上测试

项目没有单元测试套件，测试靠手动：

· 用 AIDE 在真机上编译。
· 至少在 API 23+ 和 API 30+ 各一台设备上测过。
· 深色 / 浅色模式都测过。
· 有网 / 无网都测过。
· 用真实的 AI 供应商 Key 测过。

4. 提交

使用 conventional commit 格式：

```
feat: 新增语音输入
fix: 修复 Android 14 上状态栏颜色
docs: 更新插件 JSON 示例
refactor: 把 SettingsActivity 拆成两个 Activity
chore: 版本号升到 1.0.1
```

类型前缀：feat、fix、docs、style、refactor、perf、test、chore。

5. 推送并创建 PR

```
git push origin feature/your-feature-name
```

PR 描述里要写：

· 改了什么、为什么改。
· 涉及哪些文件。
· 怎么测的（设备型号、Android 版本）。
· UI 改动请附截图或录屏。

代码规范

Java 风格

· 4 空格缩进，不用 Tab。
· 大括号跟在行尾（if (x) {），不要 Allman 风格。
· 方法名 lowerCamelCase，类名 UpperCamelCase，常量 UPPER_SNAKE_CASE。
· 单个方法尽量不超过 80 行。
· 易崩的调用按现有代码风格包在 try { ... } catch (Throwable t) {} 里，避免 App 崩溃。

AIDE 约束

· 禁止 Lambda：不能写 view -> { ... }。要写 new View.OnClickListener() { @Override public void onClick(View v) { ... } }。
· 禁止方法引用：不能写 this::method。
· 禁止 java.util.function.* 和 Stream API。
· 禁止 var（Java 10）。
· 禁止文本块（Java 15）。
· 匿名内部类引用的所有变量必须 final。

Android 约束

· 不要 import androidx.* 或 com.google.android.material.*。
· 只用 android.widget.* 和 android.view.*。
· HTTP 用 HttpURLConnection，不用 OkHttp。
· JSON 用 org.json.JSONObject / JSONArray，不用 Gson。
· 用 android.app.Activity，不用 androidx.appcompat.app.AppCompatActivity。
· 用 AlertDialog，不用 MaterialAlertDialogBuilder。

资源约束

· 不要新增 PNG/JPEG/WebP。用 shape drawable 或 Unicode 图标。
· 不要新增自定义字体，用系统字体。
· 新增 XML 文件越少越好。

注释

· 中文注释、英文注释都行，跟随所在文件的风格。
· 复杂逻辑要加注释，尤其是正则、字节层编码处理、Android 版本分支。
· 不要留注释掉的旧代码。

Bug 报告

报告 Bug 时请附上：

1. 复现步骤。
2. 期望行为。
3. 实际行为。
4. 设备型号和 Android 版本。
5. 使用的 AI 供应商和模型（如果相关）。
6. 如果是编译错误，附 AIDE 或 Android Studio 的构建日志。
7. UI 问题请附截图或录屏。

功能请求

提功能请求时请说明：

1. 功能是什么。
2. 具体使用场景。
3. 是否可以不引第三方依赖实现。
4. 大概要改哪些文件。
5. 是否和已有 issue / discussion 相关。

审查流程

每个 PR 都会按以下标准审查：

· 是否能在 AIDE 上编译通过？
· 是否引入了第三方依赖？（有就拒）
· 是否用了 Lambda 或方法引用？（有就拒）
· 是否新增了二进制资源？（有就要求说明理由或拒）
· 是否破坏了深色模式的 UI？
· 是否引入了新权限？如果是，README 里有没有解释？
· commit message 是否清楚？

小范围、聚焦的 PR 比大 PR 更容易合并。如果你计划做大改动，先开 issue 讨论。

社区准则

· 保持尊重。对代码有意见就说代码，不要攻击人。
· 帮助新手。AIDE 不是主流开发环境，很多人会需要帮助。
· 不要在没有事先讨论的情况下提交会改变项目核心原则（零依赖、纯 Java、AIDE 兼容）的 PR。
· 不要提交自己没有逐行读懂的 AI 生成代码。

发布流程

· 版本号遵循 SemVer：MAJOR.MINOR.PATCH。
· 版本号在 AndroidManifest.xml 里设置（android:versionCode 和 android:versionName）。
· 每次发版都要在 CHANGELOG.md 里写。
· 发布 commit 打 tag vX.Y.Z。

参考资源

· Android 开发者文档
· AIDE 官网
· HttpURLConnection 指南
· org.json 参考

感谢你为 Ai Office 做贡献！