# 猫鱿 (CatSquid) 项目评审与 TODO

> 评审基准：当前工作区提交 `f23822d`。范围包括 12 个 Kotlin 文件（约 4100 行）、资源、Manifest、Gradle、GitHub Actions 脚本与四语言 README。
> 方式：静态代码审阅，未在真机上运行，也未执行构建。文中的行号对应当前提交。
> 结论先行：这是一个**有明确气质、隐私取向诚实、动效有巧思**的小工具，但“核心输入链路的可靠性”和“设置/数据架构”明显落后于功能野心。作为个人项目可以打 6 分；若按可上架产品标准，约 4 分。

---

## 1. 总评

| 维度 | 分数 | 一句话判断 |
| --- | ---: | --- |
| 设计理念 | 7.5 / 10 | “桌面陪伴 + 打字反馈”的前提成立，且无网络、无账号，方向干净 |
| 视觉设计 | 6.0 / 10 | 配色与圆角卡片统一，但缺少深色模式、系统化样式与对比度校验 |
| 交互/UX | 5.0 / 10 | 首次进入像配置表单；编辑对象、层级和危险操作都不够清楚 |
| 代码结构 | 4.5 / 10 | 单文件 1600 行的 Activity 与 500 行的 Prefs，职责严重集中 |
| 稳定性/性能 | 5.0 / 10 | 输入链路有多种 ROM/输入法分支，但节流、内存和生命周期存在实质风险 |
| 隐私与合规 | 6.0 / 10 | 不申请网络权限是巨大优点；但无障碍权限的披露、排除名单与 Play 政策准备不足 |
| 工程化 | 3.5 / 10 | 无测试、无 Gradle Wrapper、CI 只出 debug、签名文件名三处不一致 |
| 文档 | 4.0 / 10 | README 干净但缺截图、隐私说明、限制说明、架构与更新日志 |
| **综合** | **5.3 / 10** | 可爱且真诚，但“可长期维护的产品”还差一层地基 |

一句话评价：

> 它最动人的地方是“宠物在键盘边轻轻回应你”；最危险的地方是，为了做到这件事，它把大量实现细节、权限风险和配置成本推到了用户面前。

---

## 2. 设计理念评审

### 2.1 做对了什么

1. **前提小而美**：不做输入法、不做社区、不做账号，只做“打字时的环境反馈”。这是一个边界清晰的情绪型工具。
2. **隐私姿态诚实**：没有 `INTERNET` 权限，没有统计 SDK；输入内容只做即时匹配，不落盘、不上传；密码框有 `event.isPassword` 拦截。`DiagLog` 只记包名、事件数、异常，不记正文。这在同类“无障碍玩具”里很少见。
3. **权限流程有诚意**：三步编号、实时状态 pill、“连接中/切断”区分、诊断面板、日志查看/复制/清除。说明作者知道无障碍服务不可靠，并试图让用户自救。
4. **动效有性格**：弹簧倾斜、加速度惯性摇摆、打字时的 squash & stretch、随机待机帧、按角色保存位置。这些细节比“贴一张 GIF”高一个层次。
5. **降级意识**：缺 `question.gif` 回落待机、用户图片缺失回落内置、GIF 解码失败回落矢量猫、服务起不来仍尽量显示。代码里到处能看出“别让它崩”的意图。
6. **体积克制**：release APK 约 1.5 MB，依赖只有 `kotlin-stdlib` 和 `androidx.core`，在如今的应用里很难得。

### 2.2 建议的产品方向

1. **默认更克制**：`shake` 默认 3 档、每次按键都弹跳 420ms，连续打字时会显得焦躁。默认 1 档或“轻柔模式”，让用户主动调高。

---

## 3. UI / UX 评审

### 3.1 信息架构

- **首屏过重**：`MainActivity.buildUi()`（`MainActivity.kt:167-222`）先渲染“显示角色条 → 权限卡 → 标签页”，权限卡里又塞了诊断、测试按钮、日志入口。第一次进入的视觉重心是“系统设置”，不是宠物。
- **“诊断”不该出现在主路径**：`diagnosticsView()`（`MainActivity.kt:323-393`）展示事件数、最近包名、测试反应、重置、日志。对普通用户是噪音，对排查者又不够（没有输入法名称、没有“上次成功匹配”）。建议折叠到“帮助与诊断”。
- **“预设”和“角色”概念重叠**：标签页叫“预设”，里面是角色管理 + 内置颜色；另一个标签页叫“插画”，编辑的却是“当前角色”的插画。用户很难建立“角色 = 一套插画 + 颜色 + 位置”的模型。建议改名并加一句模型说明：`宠物`（角色列表）/ `外观`（颜色、大小、影子）/ `动作`（待机、输入、触发）。
- **“编辑中”与“显示中”是两个状态，但界面没有持续提示**：`charactersCard()` 用“（编辑中）”pill 标记，`renderShowBar()` 用橙色圈标记“显示中”。如果用户编辑的是未显示的角色，插画页的所有改动都不会出现在屏幕上，且毫无提示。建议：
  - 插画页顶部固定“正在编辑：XX”并可直接切换；
  - 编辑未显示角色时显示“此角色当前未显示，是否显示？”；
  - 或在切换编辑角色时自动将其加入显示。
- **危险操作没有统一确认**：删除角色有确认；删除编号、删除触发、删除单张图片都是立即生效。至少给“删除编号/触发”加确认或 Undo。
- **取消图片选择会留下空壳**：新增触发时先 `all.add(Trigger(keys, emptyList()))` 再打开选择器（`MainActivity.kt:1230-1255`）。用户取消后，列表里留下一个没有图片、永远不会触发的条目。新增编号同理。应在取消时回滚，或允许“先建条目后补图”，但显示“未完成”状态。
- **文字触发无法输入空格**：`showKeysDialog()` 用空白分割（`MainActivity.kt:1279-1283`），因此不能把空格、含空格的短语作为触发。建议改成 chip 输入（逐条添加），并提供“空格”特殊项。
- **步骤顺序不可调整**：编号顺序是核心机制，但 UI 只能追加和删除（`stepsCard()`，`MainActivity.kt:1105-1162`）。应支持上/下移或拖拽排序，并在排序后重新编号。
- **图片不可排序、不可批量删除**：`thumbStrip()` 只支持单张 × 删除（`MainActivity.kt:1295-1332`）。待机图是随机所以影响小，但编号内的“别パターン”和触发图顺序虽随机，仍需要批量清理。
- **缺少序列预览**：用户只能看到静态缩略图，无法知道“1→2→3”实际播放效果。应提供一个小预览窗，按当前步骤循环播放。
- **没有导入/导出**：角色、触发、图片全在本机，换机即丢。建议支持导出为 zip/JSON 并导入；这比再加十个设置项更有价值。

### 3.2 视觉与主题

- **整体语言是成立的**：奶油底 `#F5F4F2`、白卡片、橙色主色、16dp 圆角、细分隔线，配上手绘猫，观感温暖、统一。
- **没有深色模式**：Manifest 固定 `Theme.DeviceDefault.Light.NoActionBar`（`AndroidManifest.xml:12`），建议增加选项(深色模式/浅色模式/跟随系统)
- **平台控件与自绘控件风格不统一**：`AlertDialog`、`Switch`、`EditText` 来自 OEM 的 DeviceDefault 主题，自绘卡片和按钮是另一套语言。不同品牌手机上会出现“圆角卡片 + 方角开关 + 不同字体”的混搭。建议改用 Material 3 / AppCompat 统一主题，或至少统一开关和对话框样式。
- **触摸目标过小**：`restyle()` 把 `minHeight/minWidth` 设为 0，`tag="small"` 按钮只有约 30dp 高（`MainActivity.kt:1578-1583`）；缩略图右上角的 `×` 是 24dp（`MainActivity.kt:1322`）。Android 建议至少 48dp。应增加不可见 padding 或用 `TouchDelegate` 扩大热区。
- **图标依赖 emoji**：语言按钮 “🌐” + “…”（`MainActivity.kt:189`）、检查标记 ✓、删除 ×。不同 ROM 的 emoji 字形差异大，且 TalkBack 读法不可控。建议改用 vector drawable。
- **首屏没有 WindowInsets 处理**：根布局固定 `setPadding(dp(16), dp(28), ...)`（`MainActivity.kt:171`），没有考虑状态栏、刘海、横屏和手势导航。Android 15 起 edge-to-edge 成为默认，建议改用 `WindowInsetsCompat`。
- **说明文字“点开全文”不可发现**：`descLabel()`（`MainActivity.kt:1466-1479`）默认 2 行省略，点击展开，但没有“更多”提示或箭头。用户不知道可以点。要么添加展开按钮，要么全部显示。

### 3.3 交互细节

- **整页重建导致滚动位置丢失**：`renderAll()` 每次清空三个标签页并重建（`MainActivity.kt:653-663`）；`renderBasic()`、`renderPreset()`、`renderImages()` 在每个选项变化后重建当前页。长列表里改一个选项，`ScrollView` 往往回到顶部，用户失去上下文。建议用 RecyclerView/Compose，或在重建前后保存/恢复 `scrollY`，并避免重建未变化的子树。
- **旋转后回到第一个标签页**：`currentTab` 没有写入 `onSaveInstanceState`（只保存了 `pendingCategory/pendingIndex`，`MainActivity.kt:1055-1065`）。旋转、切换语言、`recreate()` 后都会回到“基本设置”。
- **点击“显示角色”但没有悬浮窗权限时无反馈**：`avatarChip` 中只有在 `Settings.canDrawOverlays` 为真时才启动服务（`MainActivity.kt:638-641`），否则什么都不发生。应直接跳转权限页或 Toast 提示。
- **全部角色隐藏后服务不停止**：`OverlayService` 仍然前台运行、通知仍在，`pets` 为空。用户会以为“还在显示”。建议在最后一个角色隐藏时 `stopSelf()`，或至少把通知改成“已暂停”。
- **“常に最前面に固定”是空开关**：`Prefs.getAlwaysOnTop()` 只在 `MainActivity.kt:728` 被读取，服务端从未使用；Manifest 也没有 BOOT_COMPLETED 接收器。它既不会开机自启，也不会“打开其他应用时自动出现”。这是当前最明确的“UI 承诺了但代码没做”的问题。二选一：实现（开机自启 + 期望状态持久化）或删除开关与文案。
- **拖动与点击没有阈值**：`ACTION_DOWN` 即 `dragging = true`，`ACTION_UP` 一定触发一次反应（`OverlayService.kt:314-346`）。轻微误触也会让宠物弹一下并保存位置。建议用 4-8dp 的移动阈值区分 tap 与 drag。
- **多宠物没有层级控制**：每个宠物是独立窗口，按添加顺序叠放；无法把某一只置顶，也没有命名/标识。多宠物时用户很难分清谁是谁。
- **图片导入是主线程同步操作**：`onActivityResult()` 里对每个 URI 调 `importImage()`（`MainActivity.kt:1399-1400`），多选大图会卡 UI，甚至 ANR。应放到 IO 线程并显示进度。
- **缩略图解码是主线程同步操作**：`thumbFor()`（`MainActivity.kt:1334-1339`）在构建列表时直接 `BitmapFactory` 解码。图片多时进入标签页会明显卡顿。
- **`descLabel` 可点但没有 ripple/focus 样式**：可点击区域是文字本身，视觉上不像按钮；TalkBack 也只当普通文本。

### 3.4 无障碍（Accessibility）

- `ImageView` 缩略图、`PetView` 自绘猫、`×` 删除按钮都没有 `contentDescription`；TalkBack 无法说明“这是哪张图/删除哪张”。
- `avatarChip` 的选中状态用“√”文字和透明度表达，没有 `stateDescription`，TalkBack 读不出“已显示/未显示”。
- `switchRow()` 的标题是独立 `TextView`，没有 `labelFor` 关联 `Switch`；TalkBack 聚焦开关时听不到它控制什么。
- 大量 `LinearLayout` 被设为 `isClickable` 但没有 role/描述，TalkBack 的焦点顺序和语义都不可控。
- 小触摸目标与低对比度同样属于无障碍问题。
- 建议：为所有可点击控件补 `contentDescription`/`stateDescription`，用 `labelFor` 关联开关，跑一遍 TalkBack + 200% 字体缩放；必要时用 `AccessibilityDelegate` 补 role。

### 3.5 国际化

- 四种语言的字符串键**完全对齐**（已逐一比对，无缺失/多余），这是加分项。
- 但**服务与通知不跟随应用内语言**：`LocaleHelper.wrap()` 只在 `MainActivity.attachBaseContext()` 使用（`MainActivity.kt:73-75`），`OverlayService`、`PetAccessibilityService`、`CatSquidApp` 都直接用系统 locale 的 `getString()`。结果：应用内选中 `中文`，通知仍是系统语言；`ensureCharacters()` 从服务调用时也可能用错误语言生成默认名。
- **不支持的系统语言会回落到日语**：`values/` 是日语，`fr/de/es` 等没有资源。一个法语用户首次打开会看到日语界面（虽然语言选择器里有 `English`）。建议把 `values/` 改为英语作为国际默认，日语放 `values-ja`；或至少在首次选择前按系统语言做映射。
- 应用名“猫鱿”在所有语言中都不翻译，作为品牌可以接受，但 README/商店页应解释“CatSquid”的由来。
- `show_count` 英文 `%1$d shown`、韩文 `%1$d명 표시 중`；目前没有单复数问题；如果以后改文案，建议使用 `plurals`。
- `label_in_use` 通过 `trim('(', ')', '(', ')')` 去掉括号（`MainActivity.kt:941`），这是本地化字符串的脆弱用法。应拆成不带括号的独立字符串。
- 小建议：可以把"正在显示宠物"改成"正在陪伴"

---

## 4. 代码逻辑与架构评审

### 4.1 明确的逻辑缺陷（按严重度）

#### P0：用户可感知的功能错误

1. **“常に最前面”开关是装饰品**
   - 证据：`Prefs.kt:29-30` 定义；唯一读取点是 `MainActivity.kt:725-731`；`OverlayService` 从未查询；Manifest 无 `BOOT_COMPLETED`。
   - 影响：开关切换无任何行为变化，文案却在描述一个不存在的能力。
   - 建议：实现“期望运行状态 + 开机自启 + 打开其他应用时恢复”，或删除该开关和文案。

2. **只加一张待机图，就会关闭内置的 `!` / `?` / 空格动画**
   - 证据链：
     - `PetView.usesBuiltInArt = !hasCustomImages`（`PetView.kt:80`），只要任意类别有一张自定义图就为 `false`。
     - `OverlayService.sync()` 中 `watchDel = pets.any { it.deleteIdx >= 0 || it.view.usesBuiltInArt }`（`OverlayService.kt:279`）。
     - `PetAccessibilityService.handleEvent()` 在 `maxLen <= 0 && !watchDelete` 时直接 `reactIfRunning()` 并返回（`PetAccessibilityService.kt:138-141`），而 `reactIfRunning()` 调用 `reactAll(null, 0)`（`OverlayService.kt:97-99`），`inserted = null`。
     - `PetView.react()` 只有拿到 `inserted` 才能识别特殊键（`PetView.kt:260, 294-303`）。
   - 影响：内置角色正常；用户仅添加一张待机图后，`!`、`?`、空格不再播放专属动画。这是“加一张图导致另一个功能静默失效”的典型耦合。
   - 建议：把“是否需要监听删除”“是否需要监听特殊键”拆成两个独立标志；或者在无文字触发时也始终把 `inserted` 传下去。

3. **快速输入会被去抖丢弃**
   - 证据：IME 事件路径 `now - lastTextEventAt < 150L` 与 `now - lastImeReactAt < 120L`（`PetAccessibilityService.kt:198-201`、`225-228`）；`ImeWindowWatcher` 轮询间隔 150ms（`ImeWindowWatcher.kt:24`）。
   - 影响：IME 路径的反应上限约 6-8 次/秒。快速打字、日文假名连打、中文拼音连打时，步骤动画会跳帧或漏掉整次输入。对一个“每次按键都有反馈”的产品，这是核心体验问题。
   - 建议：区分“事件去重”（防止同一按键被文本事件和 IME 事件重复计数）与“渲染节流”（合并到下一帧）。不要在事件入口直接丢弃；维护一个小队列/累积计数，按帧消费。轮询间隔可动态调整（打字时 80-100ms，空闲 300-500ms）。

4. **宠物窗口的透明区域仍然拦截触摸**
   - 证据：每个宠物窗口固定为 `sizePx × sizePx` 的方形（`OverlayService.kt:284-303`），`FLAG_NOT_TOUCH_MODAL` 只让“窗口外”的触摸穿透（`OverlayService.kt:214-222`）。窗口内的透明四角仍属于窗口区域。
   - 影响：260dp 的特大宠物会用一个近乎半屏的不可见方形挡住下层应用；用户只能开启“位置锁定”让整块区域穿透，但那样就无法拖动/点击宠物。
   - 建议：把“点击穿透”作为默认模式，另设一个“编辑/拖动模式”或小拖动手柄；缩小窗口到内容包围盒；在 README/FAQ 明确这是 Android 悬浮窗的固有限制。若坚持可点击，至少提供通知栏一键切换“穿透/可交互”。

5. **位置以绝对像素保存，旋转/换屏会跑位**
   - 证据：`defaultX(i) = 40 + ...`、`defaultY(i) = 200 + ...`（`Prefs.kt:290-291`）是裸 px；`setCharPos()` 保存 px（`Prefs.kt:417-427`）；`OverlayService` 没有 `onConfigurationChanged`；`updateWindowPositions()` 使用 `resources.displayMetrics`（`OverlayService.kt:171-197`）。
   - 影响：高密度屏上默认位置挤在左上角；旋转后位置不重新夹取，宠物可能停在屏幕外；折叠屏/分屏切换后同样。
   - 建议：以 dp 或屏幕宽高比例保存；在 `onConfigurationChanged`、`sync()`、窗口尺寸变化时重新 clamp 并 `updateViewLayout`；使用 `WindowMetrics` 而非旧 `displayMetrics`，并扣除系统栏/刘海 insets。

6. **内存没有上限，理论上可 OOM**
   - 证据：`Prefs.kt:87` 明确写“枚数制限なし”；`OverlayService.bitmapCache` 缓存显示中角色的全部图片（`OverlayService.kt:69-70, 245-247`）；`MainActivity.thumbCache` 无上限（`MainActivity.kt:1053, 1334-1339`）；`PetView` 每个实例在构造时解码所有内置 GIF（`PetView.kt:82-93`）。
   - 估算：260dp 在 3x 屏上约 780px，一张 ARGB_8888 约 2.4MB；100 张即约 240MB，足以 OOM。设置页为每个无自定义图的角色创建一个 `PetView` 作头像（`MainActivity.kt:592-596`），每个 `PetView` 又会解码 8 个 GIF；角色多时启动/滚动会明显卡顿并占用大量 native 内存。
   - 建议：用 `LruCache`（按 `maxMemory/8` 估算）代替无界 `HashMap`；按当前显示尺寸解码；`PetView` 懒加载 GIF，并全局共享 `Movie`/`AnimatedImageDrawable`；预览不加载动画；实现 `onTrimMemory` 清缓存。

7. **自定义图片不支持动图，GIF 只显示第一帧**
   - 证据：`ImageLoader` 使用 `BitmapFactory.decodeStream`（`ImageLoader.kt:10-25`），GIF/WebP 动图只会得到首帧；而内置素材全是 GIF，`动画/` 目录也说明这是核心素材形式。
   - 影响：用户按直觉选一个 GIF 当角色，结果宠物不动，产品承诺落空。
   - 建议：检测 GIF/动画 WebP，API 28+ 用 `ImageDecoder` + `AnimatedImageDrawable`，低版本用 `Movie`；在导入时提示“动图支持中/仅首帧”。

#### P1：容易踩到的行为/数据问题

10. **角色数据有两个真相源，崩溃窗口会产生错位**
    - 证据：当前编辑角色的内容存在扁平键 `idleImages/typingSteps/triggers/preset`，快照存在 `characters` JSON。`switchCharacter()` 先 `saveActive()` 再 `applySnapshot()` 再写 `activeChar`（`Prefs.kt:438-446`），不是原子操作；`deleteCharacter()` 也是先写数组再写 active（`Prefs.kt:476-500`）。
    - 影响：进程在两次写入之间被杀，可能出现“active 索引指向旧角色，但扁平键已是新角色内容”的错位；删除时 active 索引还可能越界。
    - 建议：改为单一 JSON/DataStore 模型，一个事务里完成“保存旧角色 + 切换索引”；`getActiveCharacter()` 读取时 clamp 到有效范围；为 schema 加版本号。

11. **旧快照缺少字段时会被写成空，默认触发可能丢失**
    - 证据：`applySnapshot()` 对缺失的 `tr/idle/steps` 直接写空数组（`Prefs.kt:301-310`）；`ensureMigrated()` 的 v4 只在首次运行时补默认 `!`/`?`（`Prefs.kt:188-203`）。
    - 影响：从早期版本升级、且角色快照没有 `tr` 字段时，切换到该角色会把扁平键里的默认触发清空；v4 标志已置位，不会再补。
    - 建议：读取快照时对缺失字段使用“当前值/默认值”而不是空数组；迁移逻辑要有单元测试覆盖 v1→v4。

12. **`getImages(context, category)` 完全忽略 `category`**
    - 证据：`Prefs.kt:207-210` 无论传什么都读 `KEY_IDLE`。目前只有 `CAT_IDLE` 一个类别，所以没出错，但 API 语义是错的，未来加类别会静默读错数据。
    - 建议：删除参数，或按 category 映射到不同 key。

13. **无效/死代码增加理解成本**
    - `Prefs.getMatchKeys()`（`Prefs.kt:251-256`）没有调用方；`getPosX/getPosY/setPos()` 只被 `resetPos()` 使用；`Prefs.getAlwaysOnTop()` 只被 UI 读取；`PetAccessibilityService.lastEventType` 未被使用。
    - 建议：删除或在功能中真正使用；死代码会让后续维护者误以为已有缓存/状态机制。

14. **步骤列表在 UI 与动画中的编号可能不一致**
    - 证据：`PetView.setImages()` 会 `steps.filter { it.isNotEmpty() }`（`PetView.kt:142-148`），而 UI 按原始下标显示“第 N 个”（`MainActivity.kt:1116-1137`）。如果第 2 个编号暂时没有图片，动画会直接跳到第 3 个，但 UI 仍显示“3番目”。
    - 建议：UI 明确标出“空编号会被跳过”，或在添加编号时要求至少一张图；或者过滤后重新编号。

15. **特殊动画与自定义步骤的优先级不透明**
    - 证据：`react()` 先取 `triggerBmp ?: stepBmp`，只有两者都为空才考虑 `specialMovies`（`PetView.kt:257-279`）。
    - 影响：用户只要配置了任意输入步骤图，`!`/`?`/空格的内置动画就永远不会播放；这与 README 的“内置特殊键动画”描述不完全一致。
    - 建议：要么让特殊键优先于普通步骤图，要么在 UI 明确“自定义步骤会覆盖内置特殊动画”，并提供开关。

16. **删除文字时的行为依赖“是否有内置角色”**
    - 证据：`watchDel = ... || it.view.usesBuiltInArt`（`OverlayService.kt:279`）；`reactDelete()` 在自定义图时退化为 `react()`（`PetView.kt:310-331`）。
    - 影响：全自定义角色不会收到 `reactDelete`，删除动作可能只推进普通输入步骤；而“只加一张待机图”的角色又会被判定为非内置。行为难以预测。
    - 建议：把“删除反应”作为独立能力判断（是否注册了 `⌦` 或是否要播放内置删除动画），不要复用 `usesBuiltInArt`。

17. **`onSaveInstanceState` 只保存了图片选择上下文**
    - 证据：`MainActivity.kt:1055-1065` 没有保存 `currentTab`、滚动位置、语言对话框状态。
    - 建议：保存 tab；用 `RecyclerView` 的 state 或手动 `scrollTo` 恢复；旋转不应把用户丢回第一个标签页。

18. **空输入对话框静默失败**
    - 证据：`showTextDialog()` 在输入为空时直接不执行 `onOk`（`MainActivity.kt:1038-1041`），对话框已关闭，用户没有任何反馈。
    - 建议：禁用 OK 按钮或显示错误提示；`showKeysDialog()` 已有 Toast，保持一致性。

19. **缩略图缓存不随文件删除清理**
    - 证据：`thumbCache` 只在 Activity 生命周期内存在（`MainActivity.kt:1053`），删除图片后旧 Bitmap 仍留在内存；重新添加同 URI 的概率低，但批量删除时内存不会立即释放。
    - 建议：删除时 `thumbCache.remove(uri)`；使用 `LruCache`。

20. **`createIconShortcut()` 全尺寸解码，存在 OOM 风险**
    - 证据：`BitmapFactory.decodeStream` 直接解原始尺寸（`MainActivity.kt:1349-1354`），再裁剪成方形；高像素照片可轻松超过内存。
    - 建议：先 `inJustDecodeBounds`，按 launcher 图标所需尺寸（约 108dp）采样解码；捕获 `OutOfMemoryError` 并提示。

21. **图片导入不校验可解码性，也不处理 EXIF 方向**
    - 证据：`importImage()` 只检查文件长度 > 0（`MainActivity.kt:133-148`）；`ImageLoader` 不读 EXIF。
    - 影响：损坏文件会留下空白缩略图；竖拍照片可能横着显示。
    - 建议：导入时用 `inJustDecodeBounds` 校验，读取 `ExifInterface` 方向并在加载时旋转；设置单文件大小/像素上限。

22. **`ImageLoader` 的采样条件会漏掉极端长宽比**
    - 证据：`while (outWidth/(sample*2) >= reqPx && outHeight/(sample*2) >= reqPx)`（`ImageLoader.kt:16-19`）要求宽高同时满足，4000×200 的图不会降采样。
    - 建议：按 `max(width,height)` 或 `min` 的目标边长计算，避免单边过大。

23. **服务启动后没有停止路径，`START_STICKY` 会反复复活**
    - 证据：`onStartCommand()` 返回 `START_STICKY`（`OverlayService.kt:363-366`）；全项目没有 `stopService`/`stopSelf`；通知没有 action。
    - 影响：用户隐藏所有角色后服务仍在；强行停止后系统可能重启；通知常驻但无“关闭”手段。
    - 建议：持久化“用户期望运行状态”；提供 `ACTION_STOP`；无宠物时 `stopForeground(STOP_FOREGROUND_REMOVE)` + `stopSelf()`；用户主动停止后不要再被 STICKY 拉起。

24. **`sync()` 每次设置变化都会把宠物重置到待机**
    - 证据：`sync()` 调用 `pet.view.setImages(...)`，`setImages()` 内部 `goIdle()`（`PetView.kt:142-148`）。改大小、摇晃强度、影子、倾斜感度都会触发 `refreshIfRunning()` → `sync()`。
    - 影响：用户打字过程中调一个设置，宠物会突然回到待机、随机换图；体验割裂。
    - 建议：把“配置更新”和“图片集更新”拆开；图片集未变时不要 `goIdle()`；或保留当前帧/步骤。

25. **服务没有处理配置变化**
    - 证据：全项目无 `onConfigurationChanged`。旋转/分屏后 `params` 仍是旧 px，位置和尺寸可能越界。
    - 建议：在 Service 中重写 `onConfigurationChanged()`，重新计算尺寸、clamp 位置、`updateViewLayout`；同时更新 `MotionController` 的基准。

26. **多宠物没有 z-order / 选中态**
    - 证据：`pets` 是 `LinkedHashMap`，窗口添加顺序即层级（`OverlayService.kt:67, 297`）；没有“置顶某只”的操作。
    - 建议：至少提供“点击宠物置顶”；或在角色列表里提供顺序控制。

27. **前台服务失败被静默吞掉**
    - 证据：`goForeground()` 整个包在 `try/catch` 里，失败无日志（`OverlayService.kt:374-406`）。
    - 影响：某些 ROM 上服务无法前台化，随后被杀，用户只看到宠物消失，诊断里没有线索。
    - 建议：至少 `DiagLog.log` 记录异常；在通知/诊断面板暴露“前台服务未启动”。

28. **无障碍服务读取范围过宽，且缺少应用排除机制**
    - 证据：`accessibility_service_config.xml` 未限制 `packageNames`；`handleEvent()` 处理所有应用的 `TYPE_VIEW_TEXT_CHANGED`（`PetAccessibilityService.kt:99-172`）；`isPassword` 只能覆盖标准密码框。
    - 影响：银行、密码管理器、聊天应用的自定义输入框可能被读取（即使不落盘）；用户无法选择“只在这些应用生效/排除这些应用”。
    - 建议：增加应用白名单/黑名单（默认排除密码管理器、银行、系统设置）；提供一个“只统计按键活动、不读取文本”的隐私模式；在开启前做明确披露。

29. **`ImeWindowWatcher` 150ms 轮询的代价与误报**
    - 证据：固定 `INTERVAL_MS = 150`（`ImeWindowWatcher.kt:24`），每次都遍历最多 80 个节点、深度 8（`:113-128`）；`collect()` 没有 `recycle()`（minSdk 24，API 33 以下建议回收）。
    - 影响：键盘可见时持续唤醒主线程、增加耗电；候选栏刷新、长按弹出、剪贴板建议变化都会被当成“按键”，造成误反应；节点未回收在旧系统上可能泄漏。
    - 建议：仅在键盘窗口可见且没有文本事件时轮询；动态间隔（打字 100ms、空闲 400ms）；仅在键盘窗口内容变化涉及按键区域时触发；对 API < 33 的节点做回收；提供“高精度/省电”开关。

30. **拼音/假名输入的事件语义不稳定**
    - 证据：`findChange()` 依赖 `beforeText`/`afterText` 差分（`PetAccessibilityService.kt:255-279`）；某些输入法只把组合文本或局部文本放进事件。
    - 影响：组合期间可能重复触发、提交时漏触发，或把候选变化当成输入。
    - 建议：在 README 明确支持矩阵；把 `findChange` 提取为纯函数并针对“组合→提交”“候选切换”“选区替换”写单测；增加“输入法兼容性”诊断项（最近事件类型、是否有 beforeText）。

31. **`maxKeyLength`/`watchDelete` 是静态快照，设置变化后可能滞后**
    - 证据：两者在 `sync()` 中重算（`OverlayService.kt:278-280`），而 `refreshIfRunning()` 是异步 post；用户在触发编辑后立刻打字，可能仍用旧值。
    - 影响：极短窗口内新触发不生效/旧触发误触发。
    - 建议：设置变更时先同步更新匹配状态，再异步重建视图；或让匹配器直接读取版本号。

32. **`Prefs.getCharacters()` 每次调用都可能触发迁移/写盘**
    - 证据：`getCharacters()` 先 `ensureCharacters()`（`Prefs.kt:366-368`），后者可能 `writeChars`；服务 `sync()`、设置页 `renderShowBar()` 都会调用。
    - 影响：读操作有副作用，主线程可能发生磁盘写；调试和测试困难。
    - 建议：迁移只在启动时执行一次；读接口保持只读；用显式 `ensureInitialized()`。

33. **双写/双重编码的 JSON 可读性差**
    - 证据：`fillFromCurrent()` 用 `JSONArray(nestedToJson(...))`、`JSONArray(triggersToJson(...))`（`Prefs.kt:293-298`），把 JSON 字符串再包成 `JSONArray`。
    - 影响：数据查看、迁移和测试都更难；容易出现“字符串里再套 JSON”的兼容问题。
    - 建议：直接构造 `JSONArray`/`JSONObject`；定义 schema 并用序列化库或手写 mapper。

34. **`cleanupImages()` 只在 `buildUi()` 执行**
    - 证据：`buildUi()` 调用一次（`MainActivity.kt:168`）；删除角色/图片后不立即清理（`deleteCharacter` 不删文件，`Prefs.kt:476-500`）。
    - 影响：磁盘空间要等到下次冷启动才释放；用户可能以为删干净了。
    - 建议：删除后异步清理；提供“清理未使用图片”入口；导入失败的文件也要清理。

### 4.2 架构评价

**优点**

- 小类划分清楚：`TriggerMatcher`、`MotionController`、`ImeWindowWatcher`、`ImageLoader`、`DiagLog` 都有单一职责，注释解释了“为什么”而不是“是什么”。
- `TriggerMatcher` 是纯函数，极易测试；`findChange` 也是纯逻辑，适合抽出来。
- 降级路径多，异常捕获密集，说明作者对真实设备的不确定性有经验。
- 没有过度设计：没有引入 DI 框架、数据库、网络层，这对 1.5MB 的玩具是正确选择。

**问题**

- **God Activity**：`MainActivity.kt` 1604 行，同时负责 UI 构建、导航、权限、图片选择、缩略图、角色管理、快捷方式、主题、日志对话框。任何新功能都只能继续往里塞。
- **God Prefs**：`Prefs.kt` 501 行，混合了设置读写、JSON 序列化、四代迁移、当前角色状态机、图片类别、显示列表、位置。它是最大的 bug 温床。
- **OverlayService 职责过多**：窗口管理、传感器生命周期、触摸、图片解码、匹配路由、通知、屏幕开关全在一起。
- **UI 与状态强耦合**：每次操作后手动 `renderAll/renderBasic/renderImages`，没有单向数据流；滚动、焦点、动画状态都容易丢。
- **没有 schema 版本**：靠四个布尔迁移标志；新增字段只能继续加标志，无法表达“数据形状”。
- **没有测试边界**：纯逻辑（匹配、差分、采样、迁移）没有被抽出测试，导致上面这些边界问题只能靠真机碰运气。

**建议的目标结构**（不必一次搬完，可按功能渐进）

```
app/src/main/java/com/maoyou/typingpet/
  data/        AppSettings, Character, CharacterStore, ImageStore, Migrations
  reaction/    TextChangeDetector, TriggerMatcher, ReactionRouter, TypingSource
  overlay/     OverlayController, PetWindow, PetRenderer, MotionController
  platform/    PermissionState, LocaleContext, Notifications, Diagnostics
  ui/          onboarding/, settings/, studio/, components/, theme/
```

### 4.3 性能与电量

| 问题 | 证据 | 建议 |
| --- | --- | --- |
| 每个宠物一个窗口、一个 `PetView` | `OverlayService.kt:284-303` | 中期考虑单窗口多精灵；短期至少共享 GIF/位图缓存 |
| 每个 `PetView` 构造时解码全部 GIF | `PetView.kt:82-93` | 懒加载；预览用共享首帧；`Movie` 全局缓存 |
| 设置页为每个角色创建 `PetView` 头像 | `MainActivity.kt:592-596, 883-886` | 用首帧 Bitmap 或矢量图，不创建动画 View |
| 所有图片常驻内存 | `OverlayService.kt:69-70, 245-247` | `LruCache` + 按需解码 + `onTrimMemory` |
| 每帧重新缩放 Bitmap/Movie | `PetView.kt:430-477` | 预缩放到 View 尺寸，尺寸变化时重建 |
| 150ms 无障碍树轮询 | `ImeWindowWatcher.kt:24` | 动态间隔 + 只在键盘可见时 + 提供省电模式 |
| 传感器 50Hz 直接驱动 UI | `MotionController.kt:59-65` | 合并到帧；屏幕关闭/无宠物已停止（做得好），可再加 `SENSOR_DELAY_UI` 降级 |
| 主线程图片解码/导入 | `MainActivity.kt:1399-1400, 1334-1339`；`OverlayService.kt:245-247` | `Dispatchers.IO` + 占位图 + 进度 |
| 设置变更全量重建 UI | `MainActivity.kt:653-663` | 局部更新/RecyclerView/Compose |

---

## 5. 隐私、合规与发布

### 5.1 优势

- 无 `INTERNET` 权限、无统计 SDK、无账号；输入内容不落盘；密码框有显式拦截；日志不记正文。这些应写进 README 和商店页，而不是只存在于代码里。
- `DiagLog` 可查看/复制/清除，用户有知情路径。
- 导入图片复制到应用私有目录，避免 `content://` 权限过期（`MainActivity.kt:133-148`），这是正确的工程判断。

### 5.2 风险与建议

1. **`allowBackup="true"` 与“仅本地”叙事冲突**：`filesDir/images`、`diag.log`、SharedPreferences 可能进入云备份。建议添加 `dataExtractionRules`/`fullBackupContent`，排除图片和日志，或直接 `allowBackup="false"`。
2. **没有 `android:description`**：无障碍服务在系统设置里会显示默认/空白描述。`accessibility_service_config.xml` 应加 `android:description="@string/accessibility_description"`。

---

## 6. 工程化、构建与发布

| 问题 | 证据 | 建议 |
| --- | --- | --- |
| 没有 Gradle Wrapper | 仓库无 `gradlew`/`gradle/wrapper` | 添加 wrapper，CI 用 `./gradlew`，锁定 Gradle 版本 |
| 无测试/lint | `app/src` 只有 `main` | 增加 `test`/`androidTest`，CI 跑 `testDebugUnitTest` + `lint` |
| 无 R8/资源压缩 | `app/build.gradle:41-44` | 开启 `minifyEnabled` + `shrinkResources`；注意 `getIdentifier` 动态引用的 `raw/idle_*` 会被资源压缩误删，需 `res/raw/keep.xml` 的 `tools:keep` |
| 无版本目录 | 根 `build.gradle` 硬编码 AGP/Kotlin | 用 `libs.versions.toml` 统一版本；升级到当前稳定 AGP/Kotlin |
| 无 CHANGELOG / tag | 只有 `version.properties` | 每次 bump 记录变更并打 tag；`dist/` 产物附校验和 |
| 无 LICENSE | 仓库根目录无 LICENSE | 选择 MIT/Apache-2.0 或明确专有；同时确认 GIF/插画素材授权 |
| `package_apk.sh` 假设 release 已构建 | `scripts/package_apk.sh` | 构建前检查文件是否存在并给出提示；可选自动 `assembleRelease` |

---

## 7. 测试建议

当前没有任何测试目录。以下是最值得先补的（按投入产出排序）：

| 测试对象 | 类型 | 关键用例 |
| --- | --- | --- |
| `TriggerMatcher.find()` | JVM 单测 | 多字符、重叠匹配、同结尾取最长、旧文本不触发、空 key、全角/半角、代理对 |
| `findChange()` | JVM 单测 | 中间插入、删除、选区替换、自动更正、组合输入、`beforeText = null`、重复字符 |
| `Prefs` 迁移 | Robolectric/JVM | v1→v4、缺字段快照、active 越界、删除角色、显示列表清理 |
| `ImageLoader` 采样 | JVM/Robolectric | 超宽图、超高图、小图不放大、损坏文件、EXIF 方向 |
| 位置 clamp | JVM | 旋转前后、负坐标、超出屏幕、多宠物 |
| 设置页 | Espresso | 首启语言、权限状态、切换 tab、增删角色、空输入对话框 |
| 悬浮窗 | UiAutomator/手动 | 显示/隐藏、拖动、锁定、通知停止、旋转、分屏 |
| 兼容性 | 手动矩阵 | Gboard/Sogou/Baidu/AOSP/三星/小米；Android 8/10/13/14；密码框；TalkBack；200% 字体 |

CI 最少应包含：`./gradlew testDebugUnitTest lint assembleRelease`。

---

## 8. 修改路线图

### P0: 先修“承诺了但没做到”和“会伤人”的问题（约 1 周）

- [x] **P0-1 处理 `alwaysOnTop` 空开关**：实现开机自启/期望状态，或删除开关与文案。验收：切换后行为可观察，文档一致。
- [x] **P0-2 修复特殊动画被自定义待机图关闭**：拆分 `watchDelete`/`watchSpecial`，无触发时也传递 `inserted`。验收：仅加 1 张待机图时，`!`/`?`/空格仍播放内置动画（或 UI 明确说明不播放）。
- [x] **P0-3 重做快速输入节流**：事件去重与渲染节流分离，IME 轮询动态间隔。验收：10-15 字符/秒连续输入时步骤不整段丢失。
- [x] **P0-4 位置改为 dp/比例并在配置变化时 clamp**：旋转、分屏、折叠屏后宠物仍在屏内。验收：旋转 3 次位置不漂移、不出界。
- [x] **P0-5 内存上限**：`LruCache`、懒加载 GIF、预览不创建动画、后台解码。验收：100 张图片角色不 OOM，设置页滚动不卡顿。
- [x] **P0-6 服务本地化**：`Application`/`Service` 的 `attachBaseContext` 应用语言；通知、默认角色名使用应用内语言。

> P0 已完成并构建为 `1.2.34`（`versionCode=37`），通过编译、签名和 APK 结构校验。真机输入法节流、旋转/折叠、重启恢复、100 张图片内存仍需按上面的验收项实机确认。

### P1: 把“配置工具”拉回“陪伴产品”（约 2-4 周）

- [x] **P1-1 触发输入改为 chip**：支持空格、多字符、重复校验；取消选择时回滚空条目。
- [x] **P1-2 撤销与确认**：删除编号/触发/图片加确认或 Undo；空输入禁用 OK。
- [x] **P1-3 滚动/状态保持**：避免全量重建；保存 tab 和 scrollY；旋转不跳页。（保留全量重建，但已保存/恢复 tab 与 scrollY；旋转不跳页）
- [ ] **P1-4 图片管线**：EXIF、动图、损坏校验、大小上限、后台导入、进度提示、`LruCache` 缩略图。（部分完成：EXIF 方向、损坏/大小校验、后台导入与进度、`LruCache` 缩略图已完成；自定义动图未做）
- [ ] **P1-5 深色模式与主题统一**：`values-night`、Material/AppCompat 主题、系统栏 insets、统一 Switch/Dialog。
- [x] **P1-6 诊断降级**：移入设置页内联“诊断”折叠区（点标题展开，不弹诊断窗口；日志保持原可滚动窗口）；增加输入法名称、最近事件类型、匹配结果、前台服务状态；支持分享脱敏日志。
- [ ] **P1-7 测试与 CI**：先补 `TriggerMatcher`、`findChange`、迁移、位置 clamp；CI 跑单测 + lint。（部分完成：已补 `TriggerMatcher`/位置 clamp 单测并接入 CI 的 `testDebugUnitTest`；`findChange`、迁移测试和 lint 未做）

> 本轮完成 P1-2 / P1-3 / P1-6，并为 P1-7 补了可本地运行的纯 JVM 单测与 CI 单测任务；构建为 `1.2.38`（`versionCode=41`），通过编译、23 项单测、签名和 APK 结构校验。诊断信息为设置页内联折叠区：点“诊断”标题展开，不弹诊断窗口；日志恢复为原来的可滚动窗口；“以当前角度为基准”改为常规大按钮。剩余 P1-1 / P1-5、P1-4 的自定义动图，以及 P1-7 的 `findChange`/迁移/lint 未做。

> P1-4 部分完成并构建为 `1.2.41`（`versionCode=44`）：EXIF 方向、损坏/大小校验、后台导入与进度、`LruCache` 缩略图；自定义动图仍未做。

> P1-1 已完成并构建为 `1.2.42`（`versionCode=45`）：触发输入改为 chip，支持空格/多字符/重复校验；新增触发在选图成功后才创建，取消不会留下空条目。

### P2: 架构与长期能力（按需）

- [ ] **P2-1 拆分 MainActivity**：Fragment/Compose + ViewModel；`ui/` 组件化。
- [ ] **P2-2 拆分 Prefs**：单一 `CharacterStore` + schema 版本 + 原子事务；移除扁平键双真相源。
- [ ] **P2-5 动画升级**：`AnimatedImageDrawable`/`ImageDecoder` 替代 `Movie`；共享帧缓存；支持动态 WebP。
- [x] **P2-6 导入/导出**：角色包 zip（JSON + 图片）、分享预设、版本兼容。
- [ ] **P2-10 性能基准**：Macrobenchmark 启动、内存、电量；把“100 张图”“5 只宠物”“1 小时打字”纳入回归。

> P2-6 已完成并构建为 `1.2.40`（`versionCode=43`）：zip 内含 `backup.json` + `images/`，支持角色、图片、位置、显示状态和主要设置；导入会覆盖当前数据。

---

## 9. 不建议做的事

- **不要为了“功能更多”继续加插画编辑项**，在核心输入链路稳定之前，新增设置只会放大“配置工具”感。
- **不要用无障碍服务收集任何统计/分析数据**；这个项目的信任基础就是“不联网、不保存正文”。
- **不要根据 GIF 文件名猜动画映射**。`AGENTS.md` 已明确 `idle_*`/`typing_*` 是用户刻意分配的，评审和重构都应尊重这一点。
- **不要在开启资源压缩时直接上线**，`getIdentifier("idle_1", "raw", ...)` 是动态引用，必须加 keep 规则，否则内置动画会被静默删掉。

---

## 10. 评审局限

- 本文没有运行 App，也没有在真机上验证输入法、悬浮窗、传感器和通知行为；涉及运行时表现的判断来自代码路径与 Android 平台语义。
- 分数是主观的综合判断，用于排序改进优先级，不是精确度量。
- 未审查 `app/typingpet-release.jks` 等密钥文件内容，也不应审查。
- 行号基于提交 `f23822d`；后续改动后请以符号名为准。
