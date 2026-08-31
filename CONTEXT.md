# MCP 网关

将企业业务服务的 HTTP API 经受控转换后提供给 Agent 调用的统一远程 MCP 服务。

## 术语表

**业务服务**:
企业内部、可由服务注册中心发现，并以 HTTP API 对外提供业务能力的服务。
_Avoid_: 微服务、接口服务

**服务发现**:
从 Nacos 等服务注册中心取得可接入的业务服务及其可用地址的过程。
_Avoid_: 网络扫描、端口探测

**Nacos 连接范围**:
首版 MCP Gateway 固定连接一个 Nacos 集群，并在固定的 Namespace 与 Group 范围内发现和解析服务；管理端不提供多集群、多 Namespace 或多 Group 的配置与切换能力。
_Avoid_: 多注册中心管理、运行时切换

**运行时服务解析**:
每次 MCP Tool 调用时，MCP Gateway 面向 Nacos 当前健康实例集合解析其来源业务服务的目标地址，并采用轮询选择实例，而不绑定 OpenAPI 导入时的固定实例。实例缓存若采用，仅是性能优化，不改变该语义。
_Avoid_: 固定实例绑定、导入地址复用

**网关执行策略**:
MCP Gateway 对一次获准的 Tool 调用只执行一次具有网关级统一 15 秒超时的下游 HTTP 请求，不做实例故障自动重试。超时以 `httpStatus: 504` 与 `gatewayError: TIMEOUT` 的统一 MCP 响应信封返回；Agent 负责根据结果决定是否及如何重试。
_Avoid_: 网关业务编排、网关自动重试、Tool 级超时

**OpenAPI 导入**:
从已发现业务服务的 `/v3/api-docs` 读取 OpenAPI 3 JSON，供管理员选择和配置 API 操作定义。所有 operation 均展示；标记 `deprecated: true` 的接口仅显式标注已废弃，不自动屏蔽或阻止管理员创建 Tool。首版不导入 Swagger 2.0 Document。
_Avoid_: Swagger 2.0 导入、自动暴露、接口抓取

**OpenAPI 导入失败**:
在服务发现、文档获取、JSON/OpenAPI 解析或 OpenAPI 3.x 校验任一环节失败时，工具配置页面以简洁错误冒泡提示原因，例如无健康实例、文档接口超时或返回 4xx/5xx、非 JSON、文档格式无效或版本不支持。失败不创建或修改任何 Tool；管理端不展示“查看详情”、不展示也不持久化下游错误响应正文，管理员可在服务恢复或文档修复后重新获取。
_Avoid_: 部分导入、错误正文展示、导入失败审计

**OpenAPI 引用范围**:
首版支持解析同一份 OpenAPI Document 内的本地 `$ref`，例如 `#/components/schemas/...`。不支持也不主动访问外部 URL、外部文件或跨文档 `$ref`；引用无法在当前文档内解析的 operation 不可创建 Tool。
_Avoid_: 外部文档导入、未知地址访问、跨服务引用

**支持的 OpenAPI Schema**:
首版仅支持由对象、数组、字符串、数字、整数、布尔值、`required`、`enum`、嵌套对象及可在当前文档内解析的本地 `$ref` 构成的常用 JSON Schema，并将其生成 MCP Tool 的输入 schema 与网关参数校验规则。JSON 请求体中声明 `nullable: true` 的字段可显式传 JSON `null`；路径、查询与 Header 参数不接受 `null`。OpenAPI `default` 仅作为 schema 文档信息，Gateway 不自动填充，未传字段的默认行为由下游业务服务处理。包含 `oneOf`、`anyOf`、`allOf`、递归引用或其他组合/复杂 Schema 的 operation 在页面标记为“当前版本不支持”，不可创建 Tool。
_Avoid_: 组合 Schema 映射、递归 Schema、网关默认值注入、部分校验

**OpenAPI 文档路径**:
所有业务服务统一提供 OpenAPI 3 JSON 的固定相对路径 `/v3/api-docs`。MCP Gateway 从 Nacos 发现实例地址后，拼接该路径获取文档；首版不支持逐服务覆盖。
_Avoid_: Swagger UI 地址、服务级覆盖

**API 定义文档**:
描述 HTTP API 操作、参数、请求体和响应的机器可读 JSON 或 YAML 文档。它可以采用 Swagger 2.0 或 OpenAPI 3.x 规范。
_Avoid_: Swagger UI 页面、接口说明网页

**Swagger 2.0 文档**:
Swagger Specification 2.0 格式的 API Definition Document，常由较早的 Swagger 工具链生成。
_Avoid_: Swagger UI、OpenAPI 3 Document

**OpenAPI 3 文档**:
OpenAPI Specification 3.x 格式的 API Definition Document，是 Swagger Specification 2.0 之后的标准演进。
_Avoid_: Swagger 2.0 Document

**OpenAPI 操作 ID**:
OpenAPI 中用于在同一 API Definition Document 内唯一标识一个 operation 的可选 `operationId` 字段。在 Springdoc 生成的文档中，它承载 Controller 方法名。首版不依赖该字段判断 Tool 身份或判断重复映射；新建 Tool 时若该字段存在，可用来源服务名加该方法名初始化 Tool 名称，仅作为可编辑的辅助展示信息。
_Avoid_: MCP Tool 身份、全网关唯一名称、导入前置条件

**工具来源**:
MCP Tool 所映射的业务服务及其单个 OpenAPI operation 的可追溯来源，并保存已确认的 operation 定义快照。首版以来源服务、HTTP 方法和规范化路径共同标识一个来源接口；同一来源接口只能创建一个 MCP Tool。
_Avoid_: 脱离来源的工具定义

**工具更新检查**:
Gateway Administrator 对单个 MCP Tool 发起的人工检查：重新发现其来源业务服务、获取当前 OpenAPI 文档并比对来源 operation。发现变化后，管理员进入与 Tool 创建相同的映射编辑与确认流程，可按最新 OpenAPI 重新生成 HTTP 映射和 MCP 输入 schema；既有 Tool 的名称与说明以管理端配置为准，OpenAPI 的相应文案仅供参考展示，绝不自动覆盖。只有保存才更新既有 Tool；检查本身绝不自动修改 Tool 配置。更新后的映射对所有已分配该 Tool 的 Agent 立即生效，Agent 可重新拉取工具列表取得最新输入 schema。若原来源接口的 HTTP 方法或路径已变化而无法匹配，管理员须新建对应 Tool、禁用旧 Tool，并重新生效受影响 Agent 的 Tool Assignment；网关不猜测或改绑来源。
_Avoid_: 全网关扫描、自动更新、直接覆盖

**MCP 工具**:
由管理员确认一个业务服务的一个 OpenAPI 操作映射后创建并发布，供 MCP Client 调用的原子受管能力。新建时默认以来源服务名加 Controller 方法名（OpenAPI `operationId`）初始化名称，不包含 HTTP 方法，管理员可修改；若文档没有 `operationId`，管理员须手动填写名称。说明默认使用 OpenAPI `summary`，并在有 `description` 时追加；保存后的管理端名称和说明优先于来源文档，后续更新绝不自动覆盖。其对外名称在整个网关范围全局唯一、区分大小写，并遵循 MCP 名称约束：长度 1–128，只允许 ASCII 字母、数字、下划线、连字符和点。名称冲突时管理端即时提示且保存必须被拒绝，管理员需手动更名，系统不自动重命名。输入 schema 与底层 HTTP 映射首版完全来源于 OpenAPI，不支持字段或请求转换；首版仅生成 `inputSchema`，不生成 `outputSchema`，而以统一 JSON 响应信封返回下游原始 JSON body，也不生成只读、幂等或副作用等 MCP 行为提示；复杂业务流程应由业务服务实现为专用 API，而不是在网关编排。
_Avoid_: API 接口、MCP2、组合工具

**HTTP 映射**:
一个 MCP Tool 与其单个来源 OpenAPI operation 之间一对一的受管映射，包含 HTTP 方法、路径、参数、请求体、响应定义、operation 快照与运行时请求映射。它与 MCP Tool 的名称、说明和启用状态分离；首版不支持一个 Tool 映射多个 HTTP operation。
_Avoid_: MCP Tool REST 接口、请求转换

**工具映射草稿**:
由管理员从一个 OpenAPI operation 生成、查看和编辑的待确认 MCP Tool 候选定义，包含初始化名称、说明、输入 schema 和 HTTP Mapping。它在显式提交前不是 MCP Tool，不保存为持久草稿；取消即丢弃。
_Avoid_: 自动发布、持久草稿、已分配 Tool

**支持的 HTTP 载荷**:
首版仅将普通 JSON HTTP API 映射为 MCP Tool，支持 `application/json` 请求体与 JSON 响应。不支持文件上传、`multipart/form-data`、表单编码、二进制下载或流式响应。文档中的非 JSON operation 仍展示为“当前版本不支持”，但不可创建 Tool，也不影响其他 operation 的导入。
_Avoid_: 非 JSON 内容类型、文件处理

**支持的参数序列化**:
首版支持单值 `path` 参数、单值 `query` 参数和单值业务 Header 参数，以及任意 JSON 请求体。`query` 使用 OpenAPI 默认的 `form` 序列化规则；这里的 `form` 指查询参数编码，不是 `application/x-www-form-urlencoded` 请求体。数组或对象作为路径、查询或 Header 参数，以及 `matrix`、`label`、`deepObject` 等复杂序列化不支持，对应 operation 不可创建 Tool。
_Avoid_: 表单请求体、复杂参数序列化、参数转换配置

**支持的 HTTP 方法**:
首版支持将 OpenAPI 中定义的所有标准 HTTP operation（GET、PUT、POST、DELETE、OPTIONS、HEAD、PATCH、TRACE）映射为 MCP Tool；管理员在导入页面按需选择 operation 创建 Tool，不因 HTTP 方法而受限。
_Avoid_: 按方法限制导入、自动全部发布

**OpenAPI Header 参数映射**:
OpenAPI 定义的普通业务 Header 参数与路径、查询和 JSON 请求体参数一样映射为 MCP Tool 输入，由 Agent 决定是否提供及提供何值，Gateway 按映射写入下游请求头。业务接口不将 `X-MCP-Agent-Key`、`X-User-Id`、`X-Tenant-Id` 定义为普通 Header 参数；后两者只作为 Agent MCP 请求携带并由网关透明透传的 User Context。
_Avoid_: Tool 参数覆盖 User Context、业务 Header 特殊鉴权

**OpenAPI 安全声明**:
OpenAPI 的 `securitySchemes` 与 `security` 仅是来源文档中的认证方案描述。首版 MCP Gateway 完全忽略它们：不转为 Tool 输入、不注入凭证、不据此校验或阻止 Tool 创建，也不作为管理端重点展示内容。
_Avoid_: 下游认证代理、自动凭证注入、Tool 安全参数

**工具输入校验**:
MCP Gateway 在调用下游服务前，按当前 MCP Tool 的 OpenAPI 输入 schema 校验 Agent 提供的参数。缺失必填字段、类型不匹配或其他 schema 校验失败时，网关不发起下游 HTTP 请求，而直接以 `isError: true`、`httpStatus: 400`、`gatewayError: INVALID_ARGUMENT` 的 MCP Tool 执行结果拒绝调用。
_Avoid_: 下游业务校验替代、权限校验、错误参数透传

**工具输入结构**:
MCP Tool 的输入 schema 按 HTTP 参数位置分组为 `path`、`query`、`headers` 与 `body` 四个可选对象。每一组内部的字段、类型与必填约束直接来源于 OpenAPI；分组仅保留参数位置、避免不同位置的同名参数冲突，不改变底层 HTTP 映射。
_Avoid_: 参数平铺冲突、字段重命名、参数转换

**HTTP 响应转换**:
MCP Gateway 对业务服务 HTTP 响应进行的机械 MCP 结果适配。所有 MCP Tool 统一返回包含 `httpStatus` 与原始 JSON `body` 的响应信封，作为 MCP `structuredContent` 及其文本表示；HTTP `204 No Content` 或空响应体以 `body: null` 正常返回。网关不解释或改写业务 message、错误码和字段，仅依据 HTTP 状态码填充 MCP 的调用成功或失败语义。若已映射为 JSON 响应的接口在运行时返回非 JSON 内容，则返回实际 `httpStatus`、原始文本及 `gatewayError: UNSUPPORTED_RESPONSE_MEDIA_TYPE`，并标记为 Tool 调用失败。
_Avoid_: 业务错误处理、响应字段转换、非 JSON 信息丢失、HTTP 响应直通

**网关技术错误**:
下游没有产生 HTTP 响应时，由 MCP Gateway 生成的最小技术结果。超时使用 `httpStatus: 504` 与 `gatewayError: TIMEOUT`；来源服务在 Nacos 没有健康实例时使用 `httpStatus: 503` 与 `gatewayError: SERVICE_UNAVAILABLE`；连接、解析等失败使用 `httpStatus: 502` 与稳定的 `gatewayError` 值，均为 MCP Tool 执行失败。
_Avoid_: 伪造业务响应、业务错误码

**调用审计**:
针对 MCP Tool 调用的持久化治理记录。首版明确不实现调用审计，也不保存请求或响应正文；仅保留实现运行所需的短期技术日志。
_Avoid_: 首版功能、请求正文日志、响应正文日志

**智能体编排**:
Agent 基于其可用 MCP Tool 自主决定调用顺序、参数传递和流程分支的过程。
_Avoid_: 网关编排、业务流程编排

**MCP 网关**:
统一远程部署的 MCP Server，负责托管 MCP Tool 并以 MCP 2025-11-25 的 Streamable HTTP 向 Agent 等 MCP Client 提供发现与调用入口。Agent 使用固定 MCP Endpoint URL 与其 Agent Credential 访问网关；网关首版不注册为 Nacos 服务实例，Nacos 用于发现下游业务服务和集中管理非敏感运行配置。首版不支持本地 stdio，也不兼容其他 MCP 协议版本。
_Avoid_: 本地客户端、Studio、旧版 SSE 传输

**MCP 验证台**:
网关管理端内置的人工验收能力，只验证当前 MCP Gateway 及其已受管 Tool。它先以 Agent Credential 完成真实 MCP 连接与协议校验，再通过由大模型驱动的对话测试 Tool 调用；它不接入、探测或调用外部 MCP Server。
_Avoid_: 外部 MCP 探测器、外部 MCP Server 接入、鉴权绕过

**测试 MCP 连接**:
MCP Validation Console 为一次人工验证临时建立的、使用完整 Agent Credential 的真实 MCP Client 连接。它必须通过与运行时相同的凭证校验后才能列出或调用 Tool，且不构成新的 Agent 或长期权限关系。
_Avoid_: 管理端权限替代、虚拟 Agent、Key 校验绕过

**验证模型配置**:
供 MCP Validation Console 驱动 Chatbot 的 OpenAI 模型服务配置。首版仅兼容 OpenAI 模型服务；模型名与 Base URL 是由 Nacos 集中管理的非敏感运行参数，模型 API Key 仅存在于本机或云端的秘密配置中，绝不传递给管理端页面。
_Avoid_: 非 OpenAI 模型兼容层、OpenAPI Document 配置、前端模型密钥、持久化模型密钥

**验证对话记录**:
MCP Validation Console 在当前管理页面会话中暂存的对话、Tool 调用和结果。模型回复以流式方式呈现，Tool 执行状态内嵌展示；每次向模型发起新一轮对话时，仅携带当前页面最近 5 轮用户与助手对话作为上下文。它仅用于即时查看，在页面刷新、断开或关闭后丢弃，不作为调用审计或业务数据保存。
_Avoid_: 调用审计、无限上下文、持久化测试记录

**验证工具执行**:
MCP Validation Console 中由 Chatbot 决定的 MCP Tool 调用，在当前已验证的 Agent Credential 权限范围内自动执行，无需管理员对每次调用再次确认；单条用户对话最多自动执行 3 次 Tool 调用，以避免意外循环或过度调用。单次调用失败时，网关返回的完整错误结果继续作为模型上下文，模型可在剩余额度内决定调整参数、改用其他 Tool 或解释失败；达到上限后不执行第 4 次调用，页面明确提示，模型基于已有结果结束回复。页面应清楚展示调用的 Tool、输入、执行状态和结果。
_Avoid_: 每次调用人工确认、无限 Tool 循环、绕过 Agent Credential、隐藏调用结果

**验证取消**:
当管理员停止流式回复或离开 MCP Validation Console 页面时，网关终止尚未完成的模型请求。已经发出的 MCP Tool 调用及其下游 HTTP 请求不因该前端取消而被网关强制中断，以避免对可能产生业务副作用的请求作出不可靠的取消承诺。
_Avoid_: 强制取消已发出业务请求、取消后继续模型输出

**验证凭证生命周期**:
MCP Validation Console 的完整 Agent Credential 仅保存在浏览器当前页面的内存中。前端每次验证连接或发起对话时临时随请求提交给后端，后端仅在处理该请求期间使用且不保存；刷新、关闭或离开页面即丢失该 Key。
_Avoid_: 服务端会话保存、数据库保存、浏览器持久化存储

**外部 MCP Server 集成**:
将外部 MCP Server 接入 MCP Gateway，并把其提供的 Tool 纳入网关统一管理和分配的未来扩展能力。首版不实现该能力，但架构不得将其排除；它与外部 OpenAPI Document 或外部 `$ref` 导入无关。
_Avoid_: 首版功能、外部 OpenAPI 引用

**网关管理员**:
在网关管理端配置业务服务、MCP Tool、Agent 和工具分配的人工用户。首版默认信任能够访问内网管理端的用户，不实现管理员登录、角色或权限校验。
_Avoid_: Agent、调用者、运行时身份

**智能体**:
由 Gateway Administrator 创建、用于承载 MCP Tool Assignment 的逻辑智能体。它仅有名称和可选说明，不维护独立启用状态；运行时可用性完全由其唯一 Agent Credential 的状态决定。
_Avoid_: Gateway Administrator、独立 Agent 状态

**智能体凭证**:
与唯一一个 Agent 一对一绑定、通过 HTTP `X-MCP-Agent-Key` 请求头传递的 API Key，用于该 Agent 连接 MCP Gateway 时的运行时身份识别。首版仅支持人工启用或禁用；禁用凭证的请求必须被拒绝。
_Avoid_: 用户凭证、共享密钥

**智能体创建**:
Gateway Administrator 以名称和可选说明创建 Agent 的操作。创建成功时系统生成其唯一 Agent Credential，仅向管理员展示一次，然后进入该 Agent 的工具配置。

**智能体凭证重置**:
Gateway Administrator 为 Agent 重置 API Key 的操作。系统以原子替换方式生成并展示新 Key，使其立即有效，同时使旧 Key 立即失效；首版不存在双 Key 共存窗口。

**智能体凭证记录**:
Agent Credential 的不可逆持久化记录，只保存 Key 哈希、可识别前缀、创建与失效状态，不保存明文。一个 Agent 可保留多个已失效记录，但同一时间只能有一个启用记录。
_Avoid_: 明文 Key、并行有效 Key、共享 Key

**MCP 认证方式**:
首版的企业内部自定义认证约定：MCP Gateway 校验 `X-MCP-Agent-Key`，而不实现 MCP OAuth。最终用户上下文通过独立的受控请求 Header 传递，不复用 HTTP `Authorization`。
_Avoid_: MCP OAuth、将 Agent Key 置于 Authorization

**用户上下文**:
由 Agent 在 MCP 请求中可选携带的最终用户身份信息，以 HTTP `X-User-Id` 与 `X-Tenant-Id` 请求头表达。它可用于下游业务服务的日志、审计及数据与操作权限判定，但不参与 MCP Gateway 的 Tool Assignment 判定，网关也不验证其真实性或含义。
_Avoid_: Agent Credential、Tool 参数、LLM 自填用户信息

**用户上下文透传**:
MCP Gateway 在调用业务服务时，通过统一拦截层透明转发 Agent MCP 请求携带的可选 `X-User-Id` 与 `X-Tenant-Id` 请求头的过程。网关不检查其存在、含义或有效性，且不据此拒绝请求；业务服务的 Filter、Interceptor 或切面自行处理、拒绝或写入本服务的 ThreadLocal。网关不得将该上下文暴露为 Tool 输入或复制用户权限规则。
_Avoid_: 任意 Header 透传、Token 写入请求体、网关用户鉴权

**工具分配**:
Gateway Administrator 将一个已发布 MCP Tool 授予一个 Agent 的配置关系。它同时决定该工具对 Agent 的可见性和调用资格。
_Avoid_: 角色、运行时 RBAC

**工具集**:
由 Gateway Administrator 命名、复用和维护的 MCP Tool 模板集合。成员 Tool 可编辑，也可为不同组合新建或删除独立工具集；它只在 Agent 配置阶段用于选择工具，其后续修改或删除不影响已完成分配的 Agent。已禁用 Tool 仍保留为成员，并在管理端标记其状态。
_Avoid_: 角色、一次性批量操作

**智能体工具配置**:
Gateway Administrator 在单次配置操作中为一个 Agent 装配 Tool Collection 和单个 MCP Tool 的临时空间。只有显式生效才按 MCP Tool 身份去重并原子展开为该 Agent 独立的 Tool Assignment 快照；未生效的配置不保存。
_Avoid_: 运行时模板继承、持久草稿、工具列表缓存

**智能体工具快照**:
一个 Agent 当前生效的、直接指向 MCP Tool 的去重 Tool Assignment 集合。它在发布配置时整体替换，不保留历史版本；之后 Tool Collection 的编辑或删除不改变该快照。
_Avoid_: 模板继承、历史快照、局部增量生效

**Nacos 运行配置**:
由 Nacos 集中保存和查看的非敏感应用运行参数。它不包含连接 Nacos 所必需的启动信息，也不包含密码、密钥或 Agent Credential。
_Avoid_: Secret Store、业务数据存储、自动部署覆盖

**工具状态**:
MCP Tool 的全局可用状态，仅有启用和禁用。Tool 一经创建永久保留；被禁用的 Tool 对所有 Agent 均不可见且不可调用，即使其仍存在于某个 Agent 的 Tool Assignment 快照或 Tool Collection 中。首版不支持 Tool Collection 或单 Agent 级别的状态覆盖。
_Avoid_: 物理删除、仅配置隐藏、客户端状态、局部状态覆盖

**工具创建默认值**:
Gateway Administrator 保存确认后的 MCP Tool 默认处于启用状态。启用不代表对任何 Agent 暴露，只有形成 Tool Assignment 后才具备可见性和调用资格。

**运行时授权**:
MCP Gateway 在每次 `tools/list` 和 `tools/call` 请求时，根据有效 Agent Credential、Tool Assignment 和 Tool Status 作出的访问判定。无权或已禁用的 Tool 不对 Agent 展示，且直接调用必须以 JSON-RPC `-32602` 拒绝，不访问下游也不额外暴露 Tool 的存在状态。
_Avoid_: 仅列表过滤、客户端自律

**代码架构分层**:
MCP Gateway 后端统一使用 `api`、`app`、`case`、`domain`、`infrastructure`、`trigger`、`types` 七层组织代码。`app` 只负责应用启动与装配；`api` 只定义稳定的 Java 接口契约，不放 Controller、HTTP DTO、Mapper 或持久化 DTO；`trigger` 承载 REST Controller、MCP Streamable HTTP 等入站协议适配；复杂业务流程由 `case` 编排 `domain` 能力，简单业务功能允许 `trigger` 直接调用 `domain`；`infrastructure` 承载 MyBatis Mapper、持久化 DTO、MySQL/Nacos/OpenAPI/下游 HTTP 等技术实现。跨层协作必须经由明确的接口契约，调用方不得依赖其他层的具体实现；`app` 负责把接口与实现装配起来。其他层的详细职责和依赖方向以对应 ADR 为准。
_Avoid_: Controller 放入 api、Mapper 放入 domain、跨层混放 DTO、跨层依赖具体实现
