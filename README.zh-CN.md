# JSearch

[![Tests](https://github.com/uhuntu/JSearch/actions/workflows/tests.yml/badge.svg)](https://github.com/uhuntu/JSearch/actions/workflows/tests.yml)
[![License: GPL-2.0](https://img.shields.io/badge/License-GPL--2.0-blue.svg)](LICENSE)
[![JDK 11+](https://img.shields.io/badge/JDK-11%2B-green.svg)](https://adoptium.net/)
[![Archive](https://img.shields.io/badge/Status-Archive-yellow.svg)](README.md)

> "JSearch - turns search Engines into FIND engines - Programming in JAVA"
> Copyright (C) 1999-2002 Hunt Lin \<huntlin@public.xm.fj.cn\>
> 基于 GNU 通用公共许可证第二版（GPL-2.0）或更高版本分发。详见 `Releases/COPYING.TXT`。

**[English](README.md) | 简体中文**

JSearch 是一个诞生于 1999–2002 年的 Java **Applet** 元搜索引擎程序。它能够将搜索请求并发分发到多个搜索引擎（如 Google、早期百度等），抓取并解析返回的 HTML 结果区块，最终合并为统一去重、包含标题、链接与摘要的搜索结果列表。原项目使用微软 **Visual J++ 6.0** 开发，并严格依据中国国家软件文档标准 **GB8567-88** 完成全套软件工程文档编制。

本代码仓库是一个**历史软件档案（Archive）**，外加一个活着的后代。原始程序已无法按照当年的方式运行——浏览器插件与 2001 年的引擎均已消亡（详见[为何无法直接复活](#为何无法直接复活)）；[runner/](runner/README.md) 可在本地以垫片方式原样运行它。`modern/` 是对照 2002 年设计的可编译教学草图与本地缺陷演示，不是可部署的元搜索服务。[`jsearxng/`](jsearxng/) 则是其中真正能用的那一部分：同一套设计，Java 实现，对面是会还手的引擎（详见[活着的后代](#活着的后代jsearxng)）。

---

## 首要须知：文件字符编码

**本仓库中几乎所有原始代码与数据文件均采用 GBK (GB2312) 编码，而非 UTF-8。**
Java 源码、引擎规则定义文件以及捕获的 HTML 测试页面均包含以传统中文编码存储的注释和标签。

如果使用现代编辑器以默认编码（如 UTF-8）打开这些文件，会导致内容乱码（mojibake）；更危险的是，**若直接保存将会永久性损坏这些珍贵的历史字节**。

```sh
# 在终端中直接读取（不改写原文件）：
iconv -f GBK -t UTF-8 Sources/JSApplet.java | less

# 转换为临时 UTF-8 文件查看：
iconv -f GBK -t UTF-8 Sources/JSApplet.java -o /tmp/JSApplet.utf8.java
```

仓库中的历史文件故意**未做批量转码**，因为修改原始编码会破坏其作为历史文物的完整性。本归档工程中新增的文件（如本 README、`.gitignore` 及现代参考实现）均采用 UTF-8 编码。

---

## 历史世代快照

仓库完整保全了该程序的 4 个重要演进快照。它们放置于 `versions/` 目录下以便对照研究；当前根目录的活动代码区（`Sources/`、`Releases/`、`ENGINES/`）对应 2002 年 3 月的最终发布修订版本。

| 路径 | 快照标识 | 日期 | `JSApplet.java` 行数 | 版本号 |
|---|---|---|---|---|
| `versions/2000-08/` | `JSApplet` | 2000 年 8 月 | 1,512 行 | **1.2.3** |
| `versions/2001-12/JSApplet/` | `JSearch` | 2001 年 12 月 | 1,529 行 | **1.2.3** |
| `versions/2002-01/` | `JSearch` | 2002 年 1 月 | 906 行 | **2.0.0.0** |
| `Sources/` (当前代码) | `JSearch` | 2002 年 3 月 | 906 行 | **2.0.0.0 + 补丁** |

项目的演化过程**并非单调增长**：代码体积从 2000 年的 54 KB 逐渐增长到 2001 年 12 月峰值的 ~55 KB，随后在 2002 年 1 月经历了一次**彻底重构与精简**，体积缩减至 ~28 KB，并在 2002 年 3 月追加了修复补丁。`Sources/JSApplet.java` 与 `versions/2002-01/Sources/JSApplet.java` 保持字节一致，1 月与 3 月之间仅修改了 `SearchThread.java`。

### 这是同一个程序的四个演进阶段，而非两套独立项目

如果仅从目录名表面观察容易产生误解。2000 年目录中的 `JSApplet` 并不是一个恰好同名的独立程序，`JSearch` 也不是吞并它的超集，两者是同一代码库在不同生命周期的形态。

主类的命名历经多次修订从未改变：

```
versions/2000-08/JSApplet.java               public class JSApplet extends Applet
versions/2001-12/JSApplet/JSApplet.java      public class JSApplet extends Applet
versions/2002-01/Sources/JSApplet.java       public class JSApplet extends Applet
Sources/JSApplet.java                        public class JSApplet extends Applet
```

`JSApplet` 是类名，而非子组件名。该命名始于项目最初作为纯 Applet 的时期并一直沿用。而产品本身的名称**从一开始就叫 JSearch** —— 2000 年代码的第一行注释便写有 *"JSearch - turns search Engines into FIND engines"*，版本标为 `1.2.3`。

因此，2002 年正式发布的版本同时包含这两个名称：

```html
<TITLE>JSearch 2.0.0.0 - [huntlin@public.xm.fj.cn]</TITLE>
<OBJECT CABBASE=JSearch.cab CODE=JSApplet.class WIDTH=758 HEIGHT=403>
```

`JSearch.cab`、`JSearch 2.0.0.0` 部署着名为 `JSApplet.class` 的类文件。更名调整了工程名、Cabinet 打包名、窗口标题与版本字符串，但没有重命名核心类。

---

## 目录结构

```
README.md                    英文自述文件
README.zh-CN.md              中文自述文件（本文件）
.gitignore
Sources/                     v2.0.0.0 源码（2002年3月原版 + 2处并发竞态修复）
  JSApplet.java                Applet 界面外壳、UI 布局、线程控制
  SearchThread.java            搜索执行线程与 HTML 抓取解析工作线程
  JSearch.sln / .sln / .vjp    Microsoft Visual J++ 6.0 项目工程文件
  codebase.dat                 Applet 类路径配置文件
Releases/                    当年发布的完整文件包（JSearch.cab + JSearch.html + JSEngines.txt）
ENGINES/                     当时手工记录的引擎分析笔记与 2001 年保存的 HTML 抓取样本
docs/                        依据 GB8567-88 国家标准编制的 8 份完整工程文档（.doc 及 txt 备份）
refs/                        GB8567-88 标准参考资料、软件使用说明书等历史文献
artifacts/Classes/           编译产物 .class 文件（历史增量构建遗存）
modern/                      现代参考实现、Web 交互演示界面（含 live 实时模式）与 57 项自动化测试套件
runner/                      在本地以垫片原样运行 2002 年 Applet（演示引擎 + 可选 SearXNG 桥）
jsearxng/                    活着的后代：Java 单文件真实元搜索，2002 年界面，六个引擎
versions/                    历史保存的其余三个版本快照（2000-08、2001-12、2002-01）
```

---

## 历史技术分析与缺陷反思

深入的技术逆向与架构缺陷分析详见 **[docs/ANALYSIS.md](docs/ANALYSIS.md)**：

- **1.2.3 到 2.0.0.0 的重构得失：** 删除了 20 多处低效的 `finalize()` 强制 GC 调用；用同步网络验证替代了原有的异步 Ping 线程；简化了线程记账逻辑。
- **未生效的“死锁补丁”：** 2002 年 3 月补丁试图解决线程死锁，将方法修饰为 `synchronized void showResult()` 并注释掉了内部锁。然而 `SearchThread` 每次实例化都是不同对象，实例方法锁只锁住 `this`，导致**多线程之间完全失去了互斥保护**。代码注释中甚至写有 *"让死锁有时间释放"*，试图依靠 `Thread.sleep` 缓解并发冲突。
- **以 URL 为键的哈希表缺陷：** `getEngData()` 将引擎根 URL 作为 `Hashtable` 的键。“英文 Google”与“中文 Google”的 URL 行仅差一个尾随空格——正是这个看不见的字节让两把键从未真正相撞（运行原始代码已实测证实，见 `runner/`）。一旦去掉它——去空白、重新编码，任何编辑器都可能顺手做的事——后者就会覆盖前者，中文 Google 将从界面中无声消失。缺陷真实存在，只是发行数据碰巧躲过了它。
- **滑动窗口子串巧合：** 4 字符滑动窗口将 `href=` 错位匹配为 `ref=` 纯属子串偶然。

现代工程应如何优雅解决上述问题，请参考 **[modern/](modern/README.md)** 中的现代参考实现。

---

## 历史遗留陷阱：硬编码的作者开发机路径

每个世代的代码均硬编码了作者当时计算机的绝对磁盘路径。Applet 通过 `getParameter("currUrl") + "JSENGINES.TXT"` 加载引擎配置文件，一旦路径不匹配，Applet 将静默失败并不显示任何可用引擎：

| 快照世代 | 硬编码路径 |
|---|---|
| 2000-08 | `file:/E:\Developing Software\JSApplet` (`CODEBASE.DAT`) |
| 2001-12 | `file:///D:/DevSofts/JSearch/Baks/JSApplet/` |
| 2002-01 | `file:///D:/DevSofts/JSearch/Releases/` |
| 最终发布 | `file:///E:/DevSofts/JSearch/Releases/` |

---

## 为何无法直接复活

1. **Java Applet 技术已被彻底淘汰：** 主流浏览器在 2015–2021 年间彻底移除了 NPAPI 插件支持；JDK 自身也在收缩——Applet API 于 JDK 9 废弃（JEP 289）、JDK 17 标记为待移除（JEP 398）、JDK 26 正式移除（JEP 504），`appletviewer` 工具更早在 JDK 11 就被删除（JDK-8200549）。
2. **抓取的目标引擎已发生巨大变化：** 当时的抓取依赖于固定字符滑动窗口精确比对 2001 年的 HTML 标记（如 `<p><`、`k - `、`ble>`）。现代 Google 与百度不仅 HTML 结构历经多次迭代，且普遍具备严格的自动化防爬措施。
3. **缺少现代构建体系：** 原始工程仅依赖 Visual J++ 6.0 专有格式工程文件。
4. **`Releases/JSearch.html` 不可移植：** 参见上文的"历史遗留陷阱"一节。

一个部分例外，且它是全新的运行环境而非复活：[runner/](runner/README.md) 可在本地以垫片运行未经改动的原始字节码，其可选的 SearXNG 桥（`run-searxng.sh`）追加了一个引擎记录——一个小翻译器把结果改写回 2002 年的标记方言，未经改动的滑动窗口解析器因此能解析真实的网络结果，反爬对抗由桥另一侧的程序承担。但发行版本自身的配置，依然如上四条所述般无法直接运行。

---

## 活着的后代：`jsearxng/`

这副骨架比档案活得更久。[`jsearxng/`](jsearxng/) 是同一套设计——扇出、抓取、
去重、聚合，并如实报告每个引擎的失败——在 Java 里真实运行，对面是会还手的引擎。
它保留了 2002 年 applet 的面孔（钢蓝外框、结果列表、`>>预览` / `>>状态` /
`>>消息` 三个窗格，以及 `smsCh`、`smlCh` 两个真控件），并把它们指向本地
SearXNG 的引擎池和几个无需密钥的公开 API。它内部的 JSON 解析器复制自
`modern/`——血脉在帮自己的后代。

```sh
cd jsearxng && java JSearXNG.java     # http://127.0.0.1:8092/
cd jsearxng && ./test.sh              # 153 项检查，不出网
```

这是本仓库唯一以"当下"为主题的目录，也因此是唯一会随上游变动而改动的目录——
它的提交会写明是什么坏了。`versions/` 与 `Sources/` 保存实物，`modern/` 是设计
对照，`runner/` 证明原物还能跑，而 `jsearxng/` 是这套设计的归宿。它的 14 个提交
从独立的 [`uhuntu/JSearXNG`](https://github.com/uhuntu/JSearXNG) 仓库合并而来，
那个仓库现在只负责指回这里。

---

## 现代参考草图 (`modern/`)

`modern/` 用 JDK 11+ 标准库对照说明 2002 年设计本可如何拆分（零第三方依赖）。默认测试使用仓库内 2001 年 HTML 夹具，不访问现网搜索引擎。`.\build.ps1 -Web` 只是本地演示当年用 URL 做引擎主键离“丢掉中文 Google”只差一个空格，不是对外 API。要运行未经改动的原始 2002 程序本身，见 **[runner/](runner/README.md)**。

```powershell
cd modern
.\build.ps1 -Test
```

---

## 验证边界

CI 全绿，并不代表所有历史结论都被重新播放了一遍。当前验证分三级：

| 级别 | 含义 | 例子 |
|---|---|---|
| **重新计算** | CI 从当前目录树重新计算数值，文档漂移即失败 | `EncodingGuard`、`DocsGuard` |
| **离线执行** | 脚本使用本地夹具真实运行代码 | `modern` 测试套件；`xvfb` 下的 12 项 `runner/verify.sh` 断言；不出网的 `jsearxng/test.sh` |
| **仅作记录** | 历史结论，在当前仓库条件下无法重新复现 | 见下方清单 |

目前**不在本仓库中复现**的内容：

- **`artifacts/Classes/` 的确切构建谱系。** 该目录是混合增量构建产物：24 个 `.class` 中有 5 个与 `versions/2000-08/` 匹配，但其余 19 个若没有当年的 Visual J++ 6.0 工具链，就无法追溯到具体源码修订。这里的“5/24”是字节比较结论，不是编译器复现结论。
- **真实引擎可用性与反爬表现。** `jsearxng/test.sh` 和 `runner/verify.sh` 的夹具模式刻意不联网。某个公开引擎今天是否还能返回结果、返回什么标记，不在 CI 边界内；可选的 SearXNG 桥还依赖本地服务和当前上游页面。
- **原始浏览器运行环境。** 2002 年的 Internet Explorer / ActiveX 部署无法重新播放。`runner/` 证明的是 applet 逻辑在现代 JDK 和垫片下仍能运行，不是当年的部署方式仍可用。
- **Visual J++ 工程作为构建系统。** `.vjp` 与 `.sln` 仅作为历史实物保存；当前没有受支持的路径能从它们重新生成当年的 `.cab`。

---

## 进一步阅读

- **[ANALYSIS.md](docs/ANALYSIS.md)** — 详细设计缺陷分析与并发死锁剖析
- **[GENERATIONS.md](docs/GENERATIONS.md)** — 四个 `versions/` 快照的逐项实测对比
- **[ARCHITECTURE.md](docs/ARCHITECTURE.md)** — 原始设计与现代设计的架构对比图解
- **[modern/README.md](modern/README.md)** — 现代参考实现设计文档与使用指南
- **[INDEX.md](INDEX.md)** — 文档导航与建议阅读顺序
- **[CONTRIBUTING.md](CONTRIBUTING.md)** — 贡献范围：接受什么，不接受什么
- **[GLOSSARY.md](GLOSSARY.md)** — 术语表：GB8567-88、GBK、Applet 等
- **[PROVENANCE.md](PROVENANCE.md)** — 数字化抢救与版本溯源记录
- **[jsearxng/](jsearxng/)** — 活着的后代：这副骨架在真实世界里跑起来的样子，Java 单文件，六个引擎
