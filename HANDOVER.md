# 交接说明（给后续会话）

本文件记录项目现状、构建方式、已发布位置和踩过的坑。接手时先读这里，再看 README。

## 项目是什么

ServerResolveFix（中文名：服务器解析修复）。Minecraft 客户端 mod，修复原版解析 DNS SRV 时的缺陷：只读第一条记录、忽略 priority/weight、失败不回退。上游问题单 MC-86722 / MC-49826。

- mod_id: serverresolvefix
- 包名: com.emptyvessel（mixin 在 com.emptyvessel.mixin）
- 主类: ServerResolveFix
- 许可: MIT
- 仓库: https://github.com/EmptyVessel-000/ServerResolveFix

## 代码结构

- src/main/java/com/emptyvessel/SrvResolver.java —— 复刻原版 resolveAddress 流程，只在多条 SRV 分支改成按 RFC 顺序探测（TCP 两路并发，3 秒预算，单次 800ms）
- src/main/java/com/emptyvessel/SrvRecords.java —— JNDI 取全部 SRV 记录、按 priority 分组、组内加权
- src/main/java/com/emptyvessel/mixin/ServerNameResolverMixin.java —— 注入 resolveAddress HEAD
- src/main/java/com/emptyvessel/mixin/ServerStatusPingerMixin.java —— ping 前 refresh 缓存
- 缓存以 "host:port" 为 key，存 ResolvedServerAddress；解析失败不入缓存

行为边界：端口不是 25565 时不查 SRV（符合 RFC 与实现惯例）；SRV 目标全部不可达时退回域名自身 A 记录。

## 构建

三条版本线，用 -Pmc 选择目标。JDK 21 在 C:\Program Files\Java\jdk-21.0.12。

```
$env:JAVA_HOME="C:\Program Files\Java\jdk-21.0.12"
.\gradlew.bat --project-prop=mc=1.21.1 build "-Djavax.net.ssl.trustStoreType=WINDOWS-ROOT" --console=plain
```

可选 mc=1.20.4 / 1.20.6 / 1.21.1。

踩过的坑：
- PowerShell 里 `-Djavax...` 必须加引号，否则被拆成 "\.net.ssl..." 当任务名。
- 必须用 `--project-prop=mc=1.20.4`，`-Pmc=1.20.4` 会被截断成 `-Pmc=1`。
- 本机有 TLS 拦截代理，不加 trustStoreType=WINDOWS-ROOT 会 PKIX 报错。
- maven.neoforged.net 需要开代理（TUN 模式）才通。
- 首次切目标版本要下载依赖，可能几分钟；之后 10 秒内完成。

## 产物命名

按实际支持的 MC 范围命名，不是按构建目标：

| 文件 | 覆盖版本 | NeoForge |
|---|---|---|
| serverresolvefix-1.0.0+mc1.20.2-1.20.4.jar | 1.20.2–1.20.4 | 20.2.0+ |
| serverresolvefix-1.0.0+mc1.20.6.jar | 1.20.6 | 20.6.0+ |
| serverresolvefix-1.0.0+mc1.21.x.jar | 1.21–1.21.11（不含 1.21.2） | 21.0.0+ |

build.gradle 里 targets 的 label 字段决定名字，jar 任务的 archiveVersion 置空避免重复版本号。产物手动复制到 dist/（没有自动复制任务）。

NeoForge 依赖范围按线放宽到 20.2.0+ / 20.6.0+ / 21.0.0+（原来分别卡在构建目标 20.4.253 / 20.6.144 / 21.1.265），minecraft 的 versionRange 是 [1.20.2,1.20.5) / [1.20.6,1.21) / [1.21,1.22)。1.21.x 那条不逐版本实测，按上游 API 稳定处理。

## 已发布位置

- GitHub Release v1.0.0：三个资产已是新命名。updates.json 里的下载地址带 %2B 转义，实测 200。
- Modrinth：modrinth.com/mod/serverresolvefix，项目 ID AACAzI86。三个版本已按新文件名重建，状态 processing（审核中）。
- MC百科：条目已由用户填报并提交，等待审核。作者栏留空（百科里没有 EmptyVessel 作者条目，"新建作者"按钮点了没反应）。

## 待办

1. Modrinth 审核通过后：把 Source code / Issue tracker 两条外链加回去，再把描述里的仓库地址恢复成链接。当初因为 Modrinth 的链接校验一直报 could not be verified、卡住提交，才临时清空。
2. MC百科审核若要求补作者，需要先建作者条目再认领。
3. 1.19.x 与 Forge 1.20.1 未覆盖（要换 net.neoforged:forge 坐标，用户当时明确不做）。

## 踩过的坑（平台侧）

- Modrinth 设置页的链接输入框：清空后会有 saved changes 提示，但刷新后又回来，保存不生效。改用 API PATCH /v2/project/{id}（source_url / issues_url 置 null）才成功。
- Modrinth 建版本的表单在 .modal-root 里，页面级同名按钮被遮挡。
- Modrinth 切页会触发 beforeunload 对话框，需要处理。
- Modrinth API 建版本时 project_id 必须填真实 ID（AACAzI86），填 slug 会报 Base62 decoding overflowed。
- 上传文件给 playwright MCP 时必须放在 C:\Users\EmptyVessel\.dsh 下。
- MC百科的 MODID、标签、关键词都是 bootstrap-tagsinput，直接改 value 会被清掉，必须用真实键盘输入 + 回车。封面的隐藏 file input 直接塞文件只得到本地预览，必须走 label 触发 file chooser 再点"更新封面"。
- MC百科验证码是图形识别题，模型不可靠，需要人来填。
## mixin 兼容级别

按档生成：src/main/templates/serverresolvefix.mixins.json 里写 ${mixin_compat}，build.gradle 的 targets 各档给 mixinCompat（1.20.4 档 JAVA_17，另两档 JAVA_21）。必须与编译出的 class 文件版本一致，否则 Mixin 拒绝加载。

1.20.5 与 1.21.2 被排除：这两个版本的 NeoForge 只有 -beta 发布（20.5.x 全 beta，21.2.x 仅两个 beta），不对它们做兼容声明。
