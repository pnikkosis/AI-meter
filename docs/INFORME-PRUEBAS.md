# Informe de pruebas — AI Usage Widget 1.0.2

Qué se comprobó, cómo y qué falta comprobar en un teléfono con cuentas reales. Cada resultado indica
su tipo:

- **Simulado:** pruebas unitarias en la JVM con servidores HTTP falsos (MockWebServer), dobles de
  prueba o Robolectric (framework Android simulado).
- **Emulador:** emulador Android oficial (imagen `google_apis` x86_64) en un runner de GitHub
  Actions. Es Android real, pero no un teléfono, sin cuentas y sin launcher configurado a mano.
- **Servidor real sin credenciales:** peticiones a los servidores de OpenAI o Anthropic con valores
  falsos (código, token) para ver cómo responden. No usan ninguna cuenta.
- **Pendiente (teléfono):** solo se puede comprobar con un teléfono y cuentas reales. **No se hizo.**

Base: commit `d0c28d718a69a3f6b3955e01bb18904bc452b676` (d0c28d7) de la rama `ccr-4fef7446-jet13v`,
del que se compiló el APK 1.0.2; los commits posteriores solo cambian documentación y el script de la
prueba en el emulador. La 1.0.1 salió del commit e5a3795 y la 1.0.0 del d0d3d06. Las corridas del
emulador anteriores a la 13 usaron commits previos del mismo trabajo.

## 1. Compilación y análisis (GitHub Actions)

| Comprobación | Resultado |
| --- | --- |
| JDK Temurin 21.0.12, Gradle 9.5.1, AGP 9.2.1, SDK 37.0 / build-tools 37.0.0 | Usados en todas las ejecuciones |
| Pruebas unitarias `:app:testDebugUnitTest` | 749 pruebas en 119 clases, 0 fallos (CI del commit d0c28d7) |
| APK debug y release (R8 + `lintVitalRelease`) | Compilan |
| Lint (`lintDebug`) | 0 errores; 78 advertencias y 5 sugerencias (versiones de dependencias, recursos sin usar, estilo; una `InlinedApi` por usar la constante del permiso de notificaciones de Android 13, que solo se pide en Android 13 o posterior). La única relacionada con seguridad, `ExportedContentProvider`, señala `androidx.startup.InitializationProvider`, que en el manifiesto final queda `exported=false` |
| Permisos del APK release (`aapt2 dump permissions`) | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (solo se usa si se toca **Permitir** en Ajustes), y `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` que agrega WorkManager, más el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que AndroidX declara para sus receptores internos |
| Dependencias de ejecución del release | AndroidX, Kotlin/kotlinx, OkHttp/Okio, `listenablefuture`, `jspecify` y `com.qmdeve.liquidglass:core`. Ningún SDK de analíticas, publicidad, crash reporting ni Google Play Services |
| Reproducibilidad | El commit d0c28d7 compilado en tres runners distintos (CI, exportación y el emulador) dio el mismo APK sin firmar, `7175085e142648e88fb300f19f083b1aeae5e9461b4bc8621b6fc5c713eb54a3` (lo mismo pasó con e5a3795 para la 1.0.1 y con d0d3d06 para la 1.0.0). Firmar dos veces da el mismo APK firmado, con las mismas 1008 entradas que el sin firmar |

Durante el desarrollo también se usó un arnés JVM local (sin SDK de Android, que no se puede
descargar en el entorno de trabajo): 513 pruebas en 68 clases, todas correctas. Las pruebas que
necesitan Robolectric solo corren en GitHub Actions.

## 2. Qué cubren las pruebas automáticas (simulado)

- **PKCE y `state`:** el desafío S256 coincide con el ejemplo del RFC 7636; verificador y `state` de
  32 bytes aleatorios, nuevos en cada intento; comparación exacta; `toString()` no los revela.
- **Receptor loopback de Claude (sockets reales en la JVM):** devuelve el código solo con el `state`
  correcto y nunca lo repite en la página; ignora `state` falsos sin cortar el intento; rechaza
  parámetros repetidos, otras rutas (404) y métodos (405); errores del proveedor con `state`
  correcto terminan el intento con un código saneado, y con `state` falso se ignoran; se cierra al
  terminar, al cancelar y a los 10 minutos; una conexión previa ociosa del navegador no lo bloquea.
- **URL de autorización y endpoints de Claude:** parámetros de Claude Code con scope solo
  `user:profile`; redirect `http://localhost:<puerto>/callback`; endpoints HTTPS en los hosts de
  Anthropic.
- **Tokens de Claude (MockWebServer):** cuerpo JSON del intercambio y de la renovación; `invalid_grant`
  → volver a iniciar sesión, sin secretos en el diagnóstico; 429 / 5xx / respuesta ilegible con su
  propio tipo; redirecciones no seguidas; fallo de conexión = sin conexión.
- **Conexión de cuenta Claude:** intercambio → validación leyendo el uso → guardado cifrado; no se
  guarda nada si falla cualquier paso, si la respuesta no trae medidores o si al re-iniciar sesión
  entra otra cuenta.
- **Uso de Claude:** filas `limits[]` (sesión, semanal, por modelo, gasto) y campos antiguos; un
  porcentaje ausente o por encima del límite nunca se muestra como disponible; renovación del token
  antes de vencer y tras un 401 (una vez), guardando los tokens rotados antes de usarlos; un fallo
  de red al renovar no se informa como sesión perdida.
- **Inicio de sesión de Codex en el navegador (1.0.2):** URL de autorización con los parámetros de
  `codex login` (PKCE S256, `state`, `id_token_add_organizations`, `codex_cli_simplified_flow`,
  scopes `openid profile email offline_access`, sin `originator`) y retorno
  `http://127.0.0.1:<puerto>/auth/callback`; el receptor toma el primer puerto libre entre 1455 y
  1457 y falla si los dos están ocupados; el canje lleva el verificador PKCE y ese mismo retorno; al
  volver a iniciar sesión, otra cuenta no se guarda; no se guarda nada si falla el canje, la
  validación o el guardado; el canje espera a que la pantalla esté al frente y se reenvía una vez si
  no encontró red; `access_denied` con `missing_codex_entitlement` en la descripción se informa
  como cuenta sin acceso a Codex.
- **Permiso de notificaciones (1.0.2):** los interruptores se ven encendidos solo si Android deja
  notificar; en Android 13+ sin permiso, encenderlos lo pide; en Android 12 o con las notificaciones
  bloqueadas en el sistema, se abren los ajustes de notificaciones de la app; tras un rechazo, esos
  ajustes solo se abren si Android ya no muestra su diálogo.
- **Rótulos de ventanas (1.0.2):** un límite semanal de Claude para otro modelo o producto se ve como
  “7 días · <nombre>” y un medidor desconocido como “Límite · <nombre>”; Opus, Sonnet, el uso
  adicional y las ventanas de Codex no cambian.
- **Codex:** cliente de uso, flujo de código de dispositivo (pruebas del proyecto original más las
  de este fork: `expires_at` con `Z` o con desplazamiento `+00:00`) y revocación al desconectar:
  refresh token + client id como `codex logout`, access token si no hay refresh token, y error/sin
  conexión/sesión ilegible = revocación no confirmada.
- **Errores HTTP de Codex:** una respuesta del servidor (429, 5xx, página de desafío de Cloudflare)
  nunca se informa como “sin conexión” ni cierra la sesión; solo 401, un 403 de la API o un error
  terminal de OAuth piden volver a iniciar sesión.
- **Desconexión:** se revoca primero y después se borra todo lo local de esa cuenta (sesión cifrada,
  historial, intentos, alertas, estado de widgets) aunque la revocación falle; otras cuentas no se
  tocan. La app no usa WebView, así que no hay cookies propias que borrar.
- **Red:** los clientes solo aceptan `https://` hacia los hosts de su proveedor (un token de Codex no
  puede ir a Anthropic ni al revés); las redirecciones se devuelven, no se siguen.
- **Widget:** un error nunca se muestra como 0 %; una ventana agotada se ve como 0 % en rojo; los
  valores que no son actuales se atenúan y el estado dice el tipo de error; sin datos el widget lo
  dice; la hora de los datos acompaña al estado.
- **Segundo plano:** la tarea periódica no exige red (para registrar “sin conexión”); ni la falta de
  red ni un 429 provocan reintentos con backoff; estar sin conexión no genera alertas de fallos
  repetidos.
- **Idioma:** un sistema en español de cualquier región usa la traducción española.

## 3. Emulador

Workflow manual **Emulator check (manual)**: emulador oficial de Android con imagen `google_apis`
x86_64 en un runner de GitHub Actions, en dos configuraciones: **Android 15 (API 35) en español
(es-AR)** y **Android 12 (API 31) en inglés**. Se instaló el APK release procesado por R8 (firmado con
una clave descartable creada en el job, nunca con la de release) y el debug para las pruebas
instrumentadas. Hasta la corrida 12 (commit e5a3795, versión 1.0.1) se usaron las dos
configuraciones, con todo correcto. La corrida 13 (commit d0c28d7, versión 1.0.2) usó solo Android 15
en español, para ahorrar minutos de GitHub Actions; sus resultados están en “Corrida 13”, al final
de esta sección.

**Widget renderizado en Android (prueba instrumentada).** 15 estados en inglés y español,
generados con la misma fábrica de estados y el mismo layout Glance que usa el widget real, en ambas
versiones de Android. Revisé las imágenes una por una:

- El encabezado nombra siempre al proveedor; el estado lleva la hora de los datos
  (`Normal · Actualizado 8:09`, en tamaños chicos `Normal · 8:09`).
- Cada ventana dice que el número es lo **disponible** (`5 horas · disponible`, `5h quota · left`) y
  muestra el reinicio (solo la hora si es hoy, el día de la semana si es esta semana).
- Sin conexión, límite de peticiones, error del proveedor, datos ilegibles y sesión vencida se
  nombran en el estado y los valores viejos se ven atenuados; sin datos, el widget lo dice en texto.
  Ningún error se muestra como 0 %.
- Una ventana agotada se ve como `0%` en rojo.
- Revisar estas imágenes llevó a tres correcciones: un estado largo tapaba el nombre del proveedor,
  en 3×1 y 3×2 se cortaba la hora de los datos, y algunas etiquetas en español no entraban.
- Limitaciones visibles: con formato de 12 horas, algunas celdas cortan el final (`8:12 a. …`), y las
  ventanas por modelo de Claude cortan la palabra (`7 días · Opus · disponi…`).
- Esta prueba dibuja el layout fuera del launcher. Colocar el widget en una pantalla de inicio real
  (configuración, redimensionado, botón ↻) no se probó en el emulador: queda para el teléfono.

**Prueba instrumentada de Codex** (`CodexDeviceCodeExpiryTest`): decodifica una respuesta de código
de dispositivo con `expires_at` en formato `Z` y con desplazamiento, usando el java.time de cada
versión de Android. Correcta en Android 12 y 15 con el código corregido (ver Codex, abajo).

**APK release recorrido con adb y uiautomator** (`scripts/ci/emulator_smoke.py`):

- Arranca y navega Inicio, Cuentas, Ajustes, selector de proveedor, pantallas de Claude y Codex,
  sin cierres inesperados en el registro de fallos de Android.
- **Claude:** la app abre la autorización en una pestaña de Chrome (Custom Tab) que llega a
  `claude.ai`; mientras espera, escucha solo en `127.0.0.1` y `::1`. Desde fuera del teléfono
  (adb forward) y con valores falsos: `state` incorrecto → 400, la respuesta nunca repite el código,
  `Cache-Control: no-store`, CSP, parámetros repetidos → 400, POST → 405, otra ruta → 404, los
  retornos falsos no cortan el intento, y cancelar cierra el receptor. En Android 15 esta prueba
  encontró un defecto real (la respuesta se cortaba si no se leían los encabezados) que se corrigió.
  No se completó un inicio de sesión real: el emulador no tiene cuenta.
- **Red de la app detrás del navegador** (corrida 12): con la pestaña de Claude abierta 25 s,
  `dumpsys netpolicy` informa en Android 15 `blocked_state={blocked=APP_BACKGROUND,…,
  effective=APP_BACKGROUND}` (la app no tiene red) y, al volver al frente, `effective=NONE` (la red
  vuelve). En Android 12 no hay esa restricción (`rules=0 (NONE)`). Es la causa del “Sin conexión”
  de Claude en el teléfono (sección 5) y lo que aprovecha el arreglo de 1.0.1.
- **Codex:** la pantalla pide un código de dispositivo a `auth.openai.com` al abrirse. En las
  corridas 11 y 12 las dos versiones mostraron el código (el script solo informa si aparece, no lo
  captura) y cancelar lo descartó. Esta prueba encontró un defecto real heredado del original: en
  Android 12 la app decía “Sin conexión” (corrida 9) y, ya corregida la clasificación de errores,
  que la respuesta no se podía leer (corrida 10). La causa: OpenAI envía `expires_at` con
  desplazamiento (`+00:00`, ver sección 4) y `Instant.parse` del java.time de Android 12 solo acepta
  `Z`, así que **el inicio de sesión de Codex fallaba siempre en Android 12** (probablemente también
  en 13; no se probó). Corregido en d0d3d06 y comprobado en las corridas 11 y 12. Ningún código se
  aprobó: el emulador no tiene cuenta.
- **Registros:** las líneas de log que escribe la app no contienen valores con forma de credencial
  (0 en ambas versiones, corridas 11 y 12). El filtro también enmascaró algunos valores en líneas del
  sistema que nombran la app (identificadores de ventanas o de tareas): falsos positivos.
- **Incidente durante las pruebas:** en una corrida intermedia, la pantalla de Codex pidió un código
  de dispositivo al abrirse y una captura lo incluyó en el registro del job. Era el código de una
  sesión descartable del emulador, nunca autorizada y vencida a los 15 minutos, sin relación con
  ninguna cuenta; aun así borré los registros de esa corrida en GitHub y las copias locales, y el
  script dejó de capturar esa pantalla mientras puede mostrar un código.

**Corrida 13 (versión 1.0.2, Android 15 en español):**

- **Codex en el navegador:** al tocar **Continuar en el navegador**, Chrome abrió
  `auth.openai.com` con la página de inicio de sesión normal de OpenAI (“Te damos la bienvenida de
  nuevo”, correo, Google, Apple, Microsoft, teléfono), sin página de error: OpenAI aceptó el pedido
  de autorización de la app (cliente de Codex CLI, retorno `http://127.0.0.1:1455/auth/callback`,
  permisos sin conectores, sin `originator`) hasta el paso de iniciar sesión. Mientras esperaba, la
  app escuchaba solo en `127.0.0.1:1455` y `::1:1455`. Desde fuera, con valores falsos, en
  `/auth/callback`: `state` incorrecto → 400, sin repetir el código, `no-store`, CSP, parámetros
  repetidos → 400, POST → 405, `/favicon.ico` y `/callback` → 404, y los retornos falsos no cortaron
  el intento. Con la pestaña de OpenAI delante, Android le quitó la red a la app
  (`effective=APP_BACKGROUND`), y se la devolvió al volver al frente (`effective=NONE`). Cancelar
  cerró el receptor. No se inició sesión: el emulador no tiene cuenta.
- **Código de dispositivo (alternativa):** desde la misma pantalla se llegó a la pantalla de código
  de dispositivo de las versiones anteriores, que mostró un código (no se capturó); cancelar lo
  descartó.
- **Notificaciones:** con la app recién instalada (sin permiso), Ajustes mostró el interruptor de
  la notificación persistente apagado y el aviso “Android tiene bloqueadas las notificaciones…”. Al
  encenderlo apareció la explicación de la app y después el diálogo de Android; con **Permitir**, el
  permiso quedó concedido, el aviso desapareció, el interruptor quedó encendido y la app publicó su
  notificación (1, sin cuenta conectada).
- **Claude:** el receptor pasó las mismas comprobaciones, pero esta vez Chrome tardó en arrancar por
  primera vez en el emulador y la pestaña no llegó a abrirse antes de que el script siguiera; el
  script volvió atrás, salió de la pantalla y no encontró el botón de cancelar (la única comprobación
  fallida de la corrida). Es un problema del script, no de la app: la apertura de claude.ai se
  comprobó en la corrida 12 y en el teléfono. El script ahora vuelve a pedir la página si el
  navegador no aparece, y no toca atrás si la app ya está delante.
- **Pruebas instrumentadas:** correctas (estados del widget y `expires_at` de Codex).
- **Registros:** 5 valores enmascarados en líneas de la app; los cinco son fragmentos de los
  identificadores de tareas de WorkManager (UUID) que el filtro confunde con códigos de dispositivo.
  Ninguno es una credencial.
- Sin cierres inesperados.

## 4. Servidores reales, sin credenciales

Hechas desde el entorno de trabajo (Anthropic) y desde GitHub Actions (OpenAI, que el entorno de
trabajo no alcanza), con el formato exacto de las peticiones de la app y valores falsos o sin
credenciales:

| Petición | Respuesta | Qué confirma |
| --- | --- | --- |
| `GET https://claude.com/cai/oauth/authorize?...` (parámetros de la app, `scope=user:profile`) | 307 a `https://claude.ai/oauth/authorize` con los mismos parámetros; claude.ai responde con el desafío de Cloudflare a un cliente sin navegador | El endpoint existe y no rechaza la petición antes del inicio de sesión. **No** confirma que acepte `user:profile` solo: eso se ve después de iniciar sesión |
| `POST https://platform.claude.com/v1/oauth/token` (intercambio, código falso) | 400 `invalid_grant` “Invalid 'code' in request.” | El cuerpo JSON y el client id se aceptan; la app lo trata como “Claude rechazó el código” |
| `POST .../v1/oauth/token` (renovación, refresh token falso, `scope=user:profile`) | 400 `invalid_grant` “Refresh token not found or invalid” | Igual para la renovación; la app pide volver a iniciar sesión |
| `GET https://api.anthropic.com/api/oauth/usage` (token falso) | 401 `authentication_error` | El endpoint existe y exige autenticación; la app lo trata como sesión vencida |
| `GET https://auth.openai.com/oauth/authorize?...` (parámetros de la app, desde el Chrome del emulador, corrida 13) | Página de inicio de sesión de OpenAI (“Te damos la bienvenida de nuevo”), sin error | OpenAI acepta el pedido de la app (cliente de Codex CLI, retorno `127.0.0.1:1455`, permisos sin conectores, sin `originator`) hasta el inicio de sesión. **No** confirma el canje del código ni que la cuenta tenga Codex: eso queda para el teléfono |
| `POST https://auth.openai.com/api/accounts/deviceauth/usercode` (cuerpo y cabeceras de la app; desde un runner de GitHub, `scripts/ci/codex_device_code_shape.py`, y desde los emuladores) | 200 JSON con `device_auth_id`, `user_code`, `interval` (texto) y `expires_at` con desplazamiento y fracciones de segundo (`AAAA-MM-DDThh:mm:ss.ffffff+00:00`). Solo se registró el formato, nunca los valores | El endpoint y el client id de Codex CLI aceptan la petición de la app. El formato de `expires_at` explicó el fallo de Android 12 (sección 3). El código pedido nunca se aprobó y venció sin usarse |

## 5. Pruebas en el teléfono (hechas por el dueño)

### Primera prueba (versión 1.0.0)

- **Claude:** la página de claude.ai mostró “Claude Code quiere conectarse a tu Claude chat account”
  con un solo permiso, “Acceder a su información de perfil de Anthropic”: **aceptó `user:profile`
  solo**. Tras autorizar, el navegador mostró “Sesión iniciada en AI Usage Widget” (el retorno
  loopback funcionó en el teléfono), pero la app terminó en “Sin conexión”. Causa: el código llega
  mientras la pestaña del navegador tapa la app, y Android corta la red de una app que queda detrás
  de otra pantalla (cadena de firewall de segundo plano: la red se quita a los 5–20 s y se devuelve
  sin demora al volver al frente). La app canjeaba el código en ese momento. Corregido en 1.0.1: el
  canje espera a que la app vuelva al frente y se reenvía una vez si no encuentra red (pruebas en
  `ClaudeLoginViewModelTest`). Falta confirmar en el teléfono el canje y la lectura del consumo.
- **Codex:** la app obtuvo el código y abrió la página; OpenAI pidió el código del autenticador y
  después mostró “Activa el inicio de sesión con código de dispositivo para Codex, Excel, PowerPoint
  y Word en la configuración de seguridad de ChatGPT”. Es un ajuste de la cuenta de OpenAI, no de la
  app; la pantalla Seguridad de la app de ChatGPT para Android no lo muestra (ver
  `docs/INSTALAR.md`). Falta activarlo y repetir.

### Segunda prueba (versión 1.0.1)

- **Claude funcionó:** la cuenta se conectó y la app mostró el consumo (“Datos actuales”, 5 horas,
  7 días y un tercer medidor). Con eso quedan confirmados en el teléfono el canje del código al
  volver al frente (el arreglo de 1.0.1) y que el endpoint de uso acepta el token con
  `user:profile` solo.
- **Tercer medidor “Límite · 100%”:** Claude informó un límite semanal propio de un modelo o
  producto distinto de Opus y Sonnet; la app lo mostraba con el rótulo genérico y sin el nombre que
  manda Claude. Corregido en 1.0.2 (sección 2). Qué modelo o producto es lo dirá la app.
- **Notificación persistente:** no aparecía aunque el interruptor se veía encendido. Causa: venía
  activada de fábrica y la app solo pedía el permiso de notificaciones de Android 13+ al tocar el
  interruptor, así que nunca lo pidió y Android la bloqueaba. Corregido en 1.0.2.
- **Codex:** OpenAI volvió a rechazar el código de dispositivo por el ajuste de la cuenta, que el
  dueño no encontró. En 1.0.2 Codex inicia sesión en el navegador, como `codex login`, sin ese
  ajuste.

## 6. Pendiente: comprobar en el teléfono

Fuera de lo que cuenta la sección 5, nada de esto se probó con cuentas reales. Orden sugerido, con
el resultado esperado:

1. **Instalar** `AIUsageWidget-1.0.2.apk` encima de 1.0.1 y comprobar el SHA-256. Esperado: se
   instala como actualización (misma firma, código de versión 3) y Claude sigue conectado.
2. **Notificación persistente:** Ajustes → Notificación persistente. Esperado: el interruptor se ve
   apagado con el aviso “Android tiene bloqueadas las notificaciones…”; al encenderlo, la app explica
   para qué las usa y Android pregunta; con **Permitir**, aparece la notificación con el disponible
   de la cuenta actual.
3. **Codex (1.0.2, en el navegador):** Agregar cuenta → Codex → leer el aviso → Continuar en el
   navegador → iniciar sesión en OpenAI y aprobar → cuando aparezca “Sesión iniciada en AI Usage
   Widget”, cerrar la pestaña. Esperado: “Cuenta conectada.” y en Inicio los límites de Codex con su
   reinicio. **Nunca se probó con una cuenta real.** En el emulador OpenAI aceptó el pedido hasta la
   página de inicio de sesión (sección 3); falta ver con una cuenta el canje del código y la lectura
   del consumo (si algo falla, la pantalla dice el motivo y no guarda nada).
4. **Claude, tercer medidor:** en Inicio, el que decía “Límite” ahora debería decir “7 días · ” y el
   nombre que informa Claude.
5. **Widgets:** agregar uno por cuenta, elegir campos, probar los cuatro tamaños y el botón de
   actualizar. Esperado: proveedor, ventana, “disponible”, hora de los datos y reinicio legibles.
6. **Estados:** modo avión y tocar actualizar → “Sin conexión · Actualizado …” con valores
   atenuados; volver a conectar → valores normales.
7. **Segundo plano:** dejar el teléfono una o dos horas con la pantalla apagada y ver la hora de los
   datos del widget (Android puede demorar el refresco en Doze).
8. **Desconectar Codex:** Cuentas → Desplegar → Desconectar. Esperado: aviso “el proveedor aceptó el
   pedido de revocar…” y la cuenta desaparece de la app y de los widgets.
9. **Desconectar Claude:** igual. Esperado: aviso de borrado solo local; la sesión de claude.ai del
   navegador sigue abierta.
10. **Opcional, tráfico:** con una app como PCAPdroid (sin root) se puede ver a qué hosts se conecta
    la app; deberían aparecer solo `auth.openai.com`, `chatgpt.com`, `platform.claude.com` y
    `api.anthropic.com`.

## 7. Limitaciones conocidas

- **Política de Anthropic:** sus términos reservan el inicio de sesión de Claude.ai para Claude Code y
  las apps de Anthropic. Esta app usa el cliente de Claude Code y guarda el token; Anthropic podría
  restringir la cuenta (ver `docs/SEGURIDAD.md`). Alternativa sin ese riesgo: no conectar Claude y
  ver el uso en la app oficial o en claude.ai.
- **Permiso mínimo de Claude:** confirmado en el teléfono con 1.0.1: la autorización aceptó
  `user:profile` solo y el endpoint de uso respondió con ese token (sección 5). Claude Code pide
  además `user:inference`; si Anthropic dejara de aceptar `user:profile` solo, la app lo mostraría
  como “Claude aceptó el inicio de sesión pero se negó a informar el consumo”.
- **Inicio de sesión de Codex en el navegador sin probar con una cuenta:** sus partes se probaron
  por separado (sección 2 y emulador), pero el recorrido completo con una cuenta real queda para el
  teléfono (sección 6).
- **Endpoints no documentados:** `chatgpt.com/backend-api/wham/usage` y `api.anthropic.com/api/oauth/usage`
  no son APIs públicas; pueden cambiar sin aviso. La app lo mostraría como “Datos ilegibles” o
  “Error del proveedor”, nunca como 0 %.
- **Cliente OAuth de Codex CLI:** OpenAI no documenta su uso por apps de terceros.
- **Exención de batería:** pedirla es opcional; sin ella el refresco puede demorarse con la pantalla
  apagada.
