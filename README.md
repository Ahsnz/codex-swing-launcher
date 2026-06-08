# Codex Swing Launcher

Java 8 + Swing 写的 Codex 桌面启动器，用来在图形界面里控制项目开发任务，不需要每次进入项目目录手动敲 CLI。

## 当前功能

- 手动输入或浏览选择项目目录
- 手动输入或浏览选择 `config.toml` 文件或它所在的目录
- 单独选择 Codex 数据目录
- 自动把选中的 `config.toml` 复制到 Codex 数据目录下再运行
- 如果 `config.toml` 所在目录已有 `sessions`，首次使用新数据目录时会导入旧历史
- 可选择是否保存 Codex 会话
- 可选择“自动执行，不再询问确认”
- 可选择一个 `.md` 规范文档，发送时自动注入给 Codex
- 自动扫描当前 Codex 数据目录下的 `skills/**/SKILL.md`
- 可多选 Skills，并在当前对话中注入对应 skill 的说明片段
- 保存会话时，左侧展示历史对话
- 点击历史对话后，可以恢复该 session 并继续发送问题
- 运行完成后自动恢复输入框和发送按钮
- 支持停止当前 Codex 进程

## 为什么桌面会出现 sqlite 文件

Codex 会把状态、日志、记忆、目标、session 等数据写到 `CODEX_HOME`。

之前启动器把 `CODEX_HOME` 设置成了桌面，因为你的 `config.toml` 在桌面，所以 Codex 就在桌面生成了这些文件：

```text
goals_1.sqlite
logs_2.sqlite
memories_1.sqlite
state_5.sqlite
sessions
skills
tmp
```

现在启动器已经把“配置文件位置”和“Codex 数据目录”分开：

- `config.toml`：可以在桌面，也可以在任意目录
- `Codex 数据目录`：建议用专门目录，例如 `%USERPROFILE%\.codex-launcher\codex-home`

这样 sqlite、sessions、tmp 等状态文件会生成到 Codex 数据目录，不会继续散落在桌面。

如果不想保存 Codex 会话，可以取消勾选“保存 Codex 会话”。启动器会给 Codex 加 `--ephemeral`。注意：这会减少会话记录，但 Codex 仍可能在数据目录生成必要运行状态文件。

如果希望 Codex 写文件、执行命令时不再弹出确认，可以勾选“自动执行，不再询问确认”。启动器会使用：

```text
--dangerously-bypass-approvals-and-sandbox
```

这个选项只建议在你信任的项目目录中使用。它会让 Codex 跳过审批和沙箱限制。

## 规范文档

左侧“规范”页可以选择一个 `.md` 文件。发送问题时，启动器会把该文档作为 `<project_guideline_md>` 注入给 Codex。

为避免单次请求过大，规范文档最多注入前 30000 个字符。

## Skills

左侧 “Skills” 页会扫描：

```text
<Codex 数据目录>\skills\**\SKILL.md
```

你可以多选 skill。发送问题时，启动器会把选中 skill 的名称、描述和路径注入给 Codex，并要求 Codex 在相关时自行读取对应 `SKILL.md`。

为避免上下文过大，skill 不再把完整 `SKILL.md` 直接塞进请求；界面预览最多显示前 4000 个字符。

规范文档和 Skills 上下文会通过 stdin 传给 `codex exec -`，不会作为 Windows 命令行参数传递。

## 运行前准备

确认目标电脑命令行可以运行：

```powershell
codex --version
```

如果 `config.toml` 不在默认 `%USERPROFILE%\.codex`，就在界面里选择它的位置。

## IDEA 打开

用 IntelliJ IDEA 打开：

```text
D:\java\me\codex-swing-launcher
```

运行主类：

```text
com.example.codexlauncher.Main
```

## Maven 打包

```powershell
mvn package
```

生成：

```text
target\codex-swing-launcher-1.0-SNAPSHOT.jar
```

## 打包 exe

项目已经配置 Launch4j Maven 插件。执行：

```powershell
mvn package
```

会生成：

```text
target\codex-launcher.exe
target\codex-swing-launcher-1.0-SNAPSHOT.jar
```

`codex-launcher.exe` 是 GUI 程序入口，不会弹出控制台窗口。

打包到其他电脑使用时，目标电脑至少需要：

- Java 8 或随 exe 附带 JRE
- Codex CLI
- 可用的 `config.toml`
- 一个专门的 Codex 数据目录
