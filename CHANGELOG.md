# Changelog

## [1.1.0] AIMeter - 2026-10-18

Rediseño completo y primeros pasos hacia una versión publicable. La app pasa a llamarse **AIMeter**
(el identificador `io.github.pnikkosis.aiusagewidget` no cambia, para que se instale encima).

### Todas las cuentas a la vez
- Ya no hay “cuenta actual”. Inicio muestra todas las cuentas visibles, cada una con sus ventanas de
  límite (lo disponible en número y barra), su reinicio y si los datos están al día; el botón ↻ o
  deslizar hacia abajo actualiza todas.
- En Cuentas cada cuenta se puede mostrar u ocultar en Inicio y subir o bajar. El orden y lo oculto
  se usan en Inicio, la notificación y el widget de cuentas. Siguen renombrar, volver a iniciar
  sesión y desconectar.
- Notificación persistente con varias cuentas: el título da lo disponible de la ventana que limita a
  cada cuenta (“Disponible: Codex 0% · Claude 67%”) y el cuerpo una línea por cuenta con cada
  ventana; marca datos posiblemente viejos, fallas al actualizar y cuentas para reconectar, y una
  cuenta sin datos lo dice en lugar de mostrar un número. Se puede fijar una sola cuenta en
  Ajustes; ya no se elige una “ventana” para la notificación.
- Ajustes: “Según la cuenta actual” pasa a “Todas las visibles”; el último resultado de la
  actualización en segundo plano resume todas las cuentas; se quitó “Borrar el historial de la
  cuenta actual” (queda borrar todo el historial).

### Widgets
- Nuevo widget **Cuentas**: varias cuentas en uno, por defecto todas las visibles en Inicio o las que
  se elijan para ese widget (también ocultas). En angosto, cada cuenta con la cifra que la limita y
  su barra; en ancho, cada ventana con su barra, sin dejar afuera nunca la que limita. Hasta 8
  filas según el alto, con “+N” si no entran. Funciona sin configurarlo.
- Widget **Cuenta** (el de 1.0.x): mismos tamaños, rediseñado. Una ventana en 0 % ahora dice
  “Agotado” como en Inicio (antes “Escaso”). El diálogo se abre al colocarlo y arranca en la primera
  cuenta de Inicio; ya no menciona una cuenta actual.
- La línea de estado de los widgets es “estado · hora” en todos los tamaños, y en 3×2 va debajo del
  nombre de la cuenta: compartiendo renglón, un estado largo lo dejaba sin lugar.
- Vistas previas propias en el selector de widgets del launcher, y nombres “Cuenta” y “Cuentas”.

### Diseño
- Estilo “Sobria”: superficies planas con borde fino, paleta neutra con un azul de acento y modo
  claro y oscuro. Los niveles (normal, atención, escaso, agotado) usan las mismas palabras y colores
  en la app, la notificación y los widgets.
- Tipografía Geist con cifras tabulares; se quitaron JetBrains Mono, Inter y Geist Mono.
- Gris terciario y colores de estado ajustados para un contraste mínimo AA (4,5:1) sobre las
  tarjetas y los widgets, en claro y en oscuro.
- Sin efecto de vidrio ni brillos, y sin logos de OpenAI ni Anthropic: los proveedores se nombran
  con texto.
- Ícono nuevo: un medidor con su aguja, con capa monocromática para los íconos temáticos de Android
  13+; el ícono de la barra de estado usa el mismo dibujo.

### Preparación para Google Play
- Sin el permiso `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`: el aviso de batería abre la lista de apps
  optimizadas del sistema, donde se puede quitar la optimización a AIMeter.
- “Acerca de” aclara que AIMeter es independiente y no está afiliada a OpenAI ni a Anthropic, y la
  pantalla de licencias muestra completa la SIL Open Font License de Geist (también en
  `licenses/Geist-OFL.txt`).
- Requisitos de Play y términos de cada proveedor estudiados en `docs/PRODUCTO-Y-PLAY-STORE.md`.

### Interno
- La app ya no guarda una “cuenta actual” ni siquiera internamente: al actualizar se borra la que
  dejó 1.0.x en sus preferencias. El diagnóstico que se copia desde Ajustes da el mismo detalle de
  cada cuenta (sesión guardada, fallas seguidas, último intento), con cada identificador reemplazado
  por un hash, en lugar de una sección “CURRENT ACCOUNT”.

### Sin cambios
- Inicio de sesión, cifrado de sesiones, hosts permitidos, borrado al desconectar y revocación remota
  de Codex: igual que en 1.0.2.
- Versión 1.1.0 (código 4): se instala encima de 1.0.x y conserva cuentas, sesiones e historial. Un
  widget ya colocado conserva su cuenta y campos; hasta la primera actualización puede verse con las
  cifras atenuadas.

## [1.0.2] - 2026-10-17

Correcciones tras la segunda prueba en el teléfono.

- Codex: el inicio de sesión pasa a ser en el navegador, como `codex login` de Codex CLI: OAuth 2.0
  con PKCE S256 y `state`, la página de OpenAI (`auth.openai.com/oauth/authorize`) en una pestaña
  del navegador y retorno a `http://127.0.0.1:1455/auth/callback` (`:1457` si el 1455 está
  ocupado), los mismos puertos y la misma dirección que usa Codex CLI. Ya no hace falta activar el
  inicio de sesión con código de dispositivo en ChatGPT. Ese método queda como alternativa en la
  misma pantalla (**Usar un código de dispositivo**). Permisos pedidos: `openid profile email
  offline_access` (Codex CLI pide además `api.connectors.read api.connectors.invoke`, que la app no
  usa); la app no envía el parámetro `originator` con el que Codex CLI se identifica. Si OpenAI
  responde que la cuenta no tiene acceso a Codex, la pantalla lo dice.
- Notificación persistente: venía activada de fábrica (igual que en CodexMeter), pero en Android 13
  o posterior la app solo pedía el permiso de notificaciones al tocar el interruptor, y como ya se
  veía encendido nadie lo tocaba: Android la bloqueaba sin avisar. Ahora los interruptores de
  notificaciones de Ajustes se ven encendidos solo si Android deja notificar; si no, Ajustes lo
  avisa, y al encenderlos se pide el permiso (o se abren los ajustes de notificaciones del sistema
  cuando Android ya no muestra su diálogo).
- Claude: un límite semanal propio de un modelo o producto que no sea Opus ni Sonnet se veía con el
  rótulo genérico “Límite” y sin nombre. Ahora se muestra con el nombre que informa Claude (por
  ejemplo “7 días · <nombre>”) en Inicio, Cuentas, los widgets y Ajustes. Un medidor que esta
  versión no conoce sigue como “Límite”, seguido del nombre si Claude lo informa.
- Versión 1.0.2 (código 3): se instala como actualización y conserva los datos.

## [1.0.1] - 2026-10-17

Correcciones tras la primera prueba en un teléfono real.

- Claude: el inicio de sesión llegaba a "Sesión iniciada en AI Usage Widget" pero la app terminaba
  en "Sin conexión". El código llega mientras la pestaña del navegador tapa la app, y Android corta
  la red de una app que queda detrás de otra pantalla (a los pocos segundos, hasta 20; comprobado
  con `dumpsys netpolicy` en el emulador con Android 15, no pasa en Android 12). Ahora el
  código se canjea recién cuando la app vuelve al frente, y si el canje no encuentra red se reenvía
  una vez al segundo.
- Versión 1.0.1 (código 2): se instala como actualización de 1.0.0 y conserva los datos.
- Documentación: OpenAI exige activar antes el inicio de sesión con código de dispositivo en la
  configuración de seguridad de ChatGPT (su enlace lleva a la versión web; la app de ChatGPT para
  Android no muestra la opción).

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
- El receptor lee la cabecera completa de la petición antes de responder y cierra la conexión de
  forma ordenada, para que el navegador muestre la página final en lugar de un error de conexión.
- Renovación del token antes de expirar o tras un 401, guardando los tokens rotados antes de usarlos.
- La cuenta se identifica por el uuid de Anthropic; re-iniciar sesión con otra cuenta no cambia nada.
- Aviso previo sobre el cliente "Claude Code", el permiso (sin presentarlo como de solo lectura), el
  almacenamiento, la política de Anthropic y la ausencia de revocación remota.

### Estados, errores y widgets
- Nuevos tipos de error: límite de peticiones (429), proveedor no disponible (5xx u otro estado) y
  respuesta ilegible, además de sin conexión y sesión vencida. Cada uno tiene su texto.
- Una respuesta sin ventanas no se guarda como "consumo vacío": es un error.
- Codex: una respuesta HTTP del servidor (429, 5xx, página de desafío de Cloudflare) ya no se
  informa como "sin conexión" ni cierra la sesión; solo un 401, un 403 de la API o un error
  terminal de OAuth piden volver a iniciar sesión.
- Codex: el inicio de sesión fallaba siempre en Android 12 (y en otras versiones con java.time
  anterior a JDK 12): OpenAI envía `expires_at` con desplazamiento (`+00:00`) y `Instant.parse` de
  esas versiones solo acepta `Z`. Ahora se lee con `OffsetDateTime`. Defecto heredado del original,
  encontrado en el emulador con Android 12.
- Widget: nombre del proveedor, "· disponible" ("· left") en cada campo, hora de los datos, estado
  con el tipo de error, valores atenuados cuando no son actuales, ventanas agotadas visibles como 0 %, mensaje en
  lugar de celdas vacías, botón de actualización manual.
- Home y Cuenta indican que el porcentaje es lo disponible.
- Ajustes del widget hechos tras verlo renderizado en emulador: el nombre del proveedor nunca se
  recorta, el botón de actualizar va antes del estado, los tamaños chicos muestran "estado · hora",
  las horas siguen el formato 12/24 h del teléfono y los reinicios de la semana usan el día.
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

### Compilación y verificación
- La firma de release se lee de fuera del repositorio; sin ella el release queda sin firmar.
- `scripts/build-release.sh`, `scripts/sign-exported-apk.sh`, workflow de CI y workflow manual de
  exportación del release sin firmar (con `scripts/extract-exported-apk.py` para reconstruirlo desde
  los registros).
- Workflow manual de verificación en emulador (`scripts/ci/emulator_smoke.py`) y prueba
  instrumentada que renderiza todos los estados del widget.


## [0.2.4] - 2026-10-08

### Added
- 新增 "Kimi Code API" 接入面（providerId `kimi_code`）：在 Kimi Code 控制台创建长期有效的 API Key，选择中国站（`https://api.kimi.com`，默认）或国际站（`https://api.kimi.ai`）后应用内输入即可添加账号，App 调 `GET /coding/v1/usages` 采集 5 小时 / 每周 / 每月额度。比例池优先、计数回退不推断周期；key 加密存储，不进日志与诊断。原有 Kimi Cookie 账号不受影响。

## [0.2.3] - 2026-10-07

### Fixed
- 修复 Kimi 内嵌登录页白屏及重定向后登录窗口无法打开的问题。
- 补充 Kimi 网页会话与路径 Cookie 提取，修复登录后提示未找到会话的问题。
- Kimi 短期额度按接口周期显示；周期不明的长期额度改为“周期额度”，不再误标为周额度。保留实际重置时间、额度数值和已有窗口选择。
