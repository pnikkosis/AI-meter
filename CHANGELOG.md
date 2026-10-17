# Changelog

## [1.0.0] AI Usage Widget (fork) - 2026-10-17

Fork personal de CodexMeter 0.2.4 (`2f7bf52`). Cambios respecto del original:

### Identidad y alcance
- Nombre "AI Usage Widget", applicationId `io.github.pnikkosis.aiusagewidget`, firma propia,
  versión 1.0.0 (código 1). El paquete Kotlin `com.kmnexus.codexmeter` se conserva para que el
  código siga siendo comparable con el original.
- Solo Codex y Claude. Se eliminaron Antigravity, Cursor, DeepSeek, Grok, Kimi, Kimi Code, MiniMax,
  Z.ai y Z.ai balance, con sus clientes, pantallas de API key/cookies, textos e íconos.
- Se eliminó el comprobador de actualizaciones (consultaba los releases de GitHub del original y
  descargaba APK) y el workflow de releases del original.
- Se desactivó la conversión de monedas: se borró el cliente de open.er-api.com y el selector de
  moneda/umbrales de saldo de Ajustes.
- Pantalla "Acerca de" con la licencia MIT de CodexMeter y las licencias de terceros.
- Interfaz traducida al español (`values-es`); el inglés y el chino del original se conservan.

### Claude
- Inicio de sesión en el navegador (Custom Tab) con OAuth 2.0 + PKCE S256 y retorno a
  `http://localhost:<puerto>/callback`, como Claude Code, en lugar del WebView que falseaba el
  User-Agent. Receptor loopback solo en 127.0.0.1/::1, validación de `state` en tiempo constante,
  sin repetir el código en la respuesta, cierre al terminar o a los 10 minutos.
- Permiso pedido: solo `user:profile` (antes `org:create_api_key user:profile user:inference`).
- Endpoints actuales de Claude Code: `platform.claude.com/v1/oauth/token`; uso en
  `api.anthropic.com/api/oauth/usage` leyendo las filas `limits[]` (con los campos antiguos como
  respaldo). User-Agent propio.
- Renovación del token antes de expirar o tras un 401, guardando los tokens rotados antes de usarlos.
- La cuenta se identifica por el uuid de Anthropic; re-iniciar sesión con otra cuenta no cambia nada.
- Aviso previo sobre el cliente "Claude Code", el permiso (sin presentarlo como de solo lectura), el
  almacenamiento, la política de Anthropic y la ausencia de revocación remota.

### Estados, errores y widgets
- Nuevos tipos de error: límite de peticiones (429), proveedor no disponible (5xx u otro estado) y
  respuesta ilegible, además de sin conexión y sesión vencida. Cada uno tiene su texto.
- Una respuesta sin ventanas no se guarda como "consumo vacío": es un error.
- Widget: nombre del proveedor, "· disponible" ("· left") en cada campo, hora de los datos, estado
  con el tipo de error, valores atenuados cuando no son actuales, ventanas agotadas visibles como 0 %, mensaje en
  lugar de celdas vacías, botón de actualización manual.
- Home y Cuenta indican que el porcentaje es lo disponible.
- El refresco periódico también corre sin red para registrar "sin conexión"; ni la falta de red ni
  un 429 provocan reintentos con backoff, y estar sin conexión no genera alertas de fallos repetidos.

### Desconexión
- Codex: revocación RFC 7009 en `auth.openai.com/oauth/revoke` (como `codex logout`) antes del
  borrado local, que ocurre siempre. Claude: solo borrado local (Anthropic no ofrece revocación).
- El diálogo explica qué se borra y qué se revoca; luego se informa el resultado real (pedido de
  revocación aceptado, no confirmado o no disponible).

### Red y almacenamiento
- Sin redirecciones automáticas; solo HTTPS; hosts permitidos por proveedor (Codex:
  auth.openai.com, chatgpt.com; Claude: platform.claude.com, api.anthropic.com).
- Se quitó la excepción de tráfico en claro a 127.0.0.1.
- Renovaciones de token sin reintentos de transporte.
- Claves AES del Keystore de 256 bits explícitos.

### Compilación
- La firma de release se lee de fuera del repositorio; sin ella el release queda sin firmar.
- `scripts/build-release.sh`, `scripts/sign-exported-apk.sh`, workflow de CI y workflow manual de
  exportación del release sin firmar.


## [0.2.4] - 2026-10-08

### Added
- 新增 "Kimi Code API" 接入面（providerId `kimi_code`）：在 Kimi Code 控制台创建长期有效的 API Key，选择中国站（`https://api.kimi.com`，默认）或国际站（`https://api.kimi.ai`）后应用内输入即可添加账号，App 调 `GET /coding/v1/usages` 采集 5 小时 / 每周 / 每月额度。比例池优先、计数回退不推断周期；key 加密存储，不进日志与诊断。原有 Kimi Cookie 账号不受影响。

## [0.2.3] - 2026-10-07

### Fixed
- 修复 Kimi 内嵌登录页白屏及重定向后登录窗口无法打开的问题。
- 补充 Kimi 网页会话与路径 Cookie 提取，修复登录后提示未找到会话的问题。
- Kimi 短期额度按接口周期显示；周期不明的长期额度改为“周期额度”，不再误标为周额度。保留实际重置时间、额度数值和已有窗口选择。
