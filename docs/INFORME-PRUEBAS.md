# Informe de pruebas — AI Usage Widget 1.0.0

Qué se comprobó, cómo y qué falta comprobar en un teléfono con cuentas reales. Cada resultado indica
su tipo:

- **Simulado:** pruebas unitarias en la JVM con servidores HTTP falsos (MockWebServer), dobles de
  prueba o Robolectric (framework Android simulado).
- **Emulador:** emulador Android oficial (imagen `google_apis` x86_64) en un runner de GitHub
  Actions. Es Android real, pero no un teléfono, sin cuentas y sin launcher configurado a mano.
- **Servidor real sin credenciales:** peticiones a los servidores de OpenAI o Anthropic con valores
  falsos (código, token) para ver cómo responden. No usan ninguna cuenta.
- **Pendiente (teléfono):** solo se puede comprobar con un teléfono y cuentas reales. **No se hizo.**

Base: commit `d0d3d0651573012e321f6bad15dde643d7a26b9d` (d0d3d06) de la rama `ccr-4fef7446-jet13v`,
del que se compiló el APK entregado; los commits posteriores solo cambian documentación. Las
corridas del emulador anteriores a la 11 usaron commits previos del mismo trabajo.

## 1. Compilación y análisis (GitHub Actions)

| Comprobación | Resultado |
| --- | --- |
| JDK Temurin 21.0.12, Gradle 9.5.1, AGP 9.2.1, SDK 37.0 / build-tools 37.0.0 | Usados en todas las ejecuciones |
| Pruebas unitarias `:app:testDebugUnitTest` | 716 pruebas en 116 clases, 0 fallos (CI del commit d0d3d06) |
| APK debug y release (R8 + `lintVitalRelease`) | Compilan |
| Lint (`lintDebug`) | 0 errores; 77 advertencias y 5 sugerencias (versiones de dependencias, recursos sin usar, estilo). La única relacionada con seguridad, `ExportedContentProvider`, señala `androidx.startup.InitializationProvider`, que en el manifiesto final queda `exported=false` |
| Permisos del APK release (`aapt2 dump permissions`) | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (solo se usa si se toca **Permitir** en Ajustes), y `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` que agrega WorkManager, más el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que AndroidX declara para sus receptores internos |
| Dependencias de ejecución del release | AndroidX, Kotlin/kotlinx, OkHttp/Okio, `listenablefuture`, `jspecify` y `com.qmdeve.liquidglass:core`. Ningún SDK de analíticas, publicidad, crash reporting ni Google Play Services |
| Reproducibilidad | El commit d0d3d06 compilado en cuatro runners distintos (CI, exportación y los dos emuladores) dio el mismo APK sin firmar, `f4b3534aecf273773084d9ca7e11a58adc1fe3ff1ab11ce7424c38b2ea1ba472`. Firmar dos veces da el mismo APK firmado, con las mismas 1008 entradas que el sin firmar |

Durante el desarrollo también se usó un arnés JVM local (sin SDK de Android, que no se puede
descargar en el entorno de trabajo): 477 pruebas en 64 clases, todas correctas. Las pruebas que
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
instrumentadas. Última corrida: la 11 (commit d0d3d06), correcta en las dos configuraciones.

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
- **Codex:** la pantalla pide un código de dispositivo a `auth.openai.com` al abrirse. En la
  corrida 11 las dos versiones mostraron el código (el script solo informa si aparece, no lo
  captura) y cancelar lo descartó. Esta prueba encontró un defecto real heredado del original: en
  Android 12 la app decía “Sin conexión” (corrida 9) y, ya corregida la clasificación de errores,
  que la respuesta no se podía leer (corrida 10). La causa: OpenAI envía `expires_at` con
  desplazamiento (`+00:00`, ver sección 4) y `Instant.parse` del java.time de Android 12 solo acepta
  `Z`, así que **el inicio de sesión de Codex fallaba siempre en Android 12** (probablemente también
  en 13; no se probó). Corregido en d0d3d06 y comprobado en la corrida 11. Ningún código se aprobó:
  el emulador no tiene cuenta.
- **Registros:** las líneas de log que escribe la app no contienen valores con forma de credencial
  (0 en ambas versiones, corrida 11). El filtro también enmascaró algunos valores en líneas del
  sistema que nombran la app (identificadores de ventanas o de tareas): falsos positivos.
- **Incidente durante las pruebas:** en una corrida intermedia, la pantalla de Codex pidió un código
  de dispositivo al abrirse y una captura lo incluyó en el registro del job. Era el código de una
  sesión descartable del emulador, nunca autorizada y vencida a los 15 minutos, sin relación con
  ninguna cuenta; aun así borré los registros de esa corrida en GitHub y las copias locales, y el
  script dejó de capturar esa pantalla mientras puede mostrar un código.

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
| `POST https://auth.openai.com/api/accounts/deviceauth/usercode` (cuerpo y cabeceras de la app; desde un runner de GitHub, `scripts/ci/codex_device_code_shape.py`, y desde los emuladores) | 200 JSON con `device_auth_id`, `user_code`, `interval` (texto) y `expires_at` con desplazamiento y fracciones de segundo (`AAAA-MM-DDThh:mm:ss.ffffff+00:00`). Solo se registró el formato, nunca los valores | El endpoint y el client id de Codex CLI aceptan la petición de la app. El formato de `expires_at` explicó el fallo de Android 12 (sección 3). El código pedido nunca se aprobó y venció sin usarse |

## 5. Pendiente: comprobar en el teléfono

Nada de esto se probó con cuentas reales. Orden sugerido, con el resultado esperado:

1. **Instalar** `AIUsageWidget-1.0.0.apk` y comprobar el SHA-256. Esperado: se instala en Android 12+.
2. **Codex:** Agregar cuenta → Codex → Iniciar sesión → abrir la página → aprobar con el código.
   Esperado: “Cuenta conectada.” y en Inicio los límites de 5 h y semanal con su reinicio. Anotar
   la versión de Android del teléfono: el arreglo de `expires_at` se comprobó en Android 12 y 15.
3. **Claude:** Agregar cuenta → Claude → leer el aviso → Continuar en el navegador → iniciar sesión
   y autorizar. Puntos a observar:
   - si la página de Anthropic acepta el permiso `user:profile` solo (si no, la app muestra
     “Claude no aceptó el permiso pedido (user:profile)”);
   - si al terminar aparece la página “Sesión iniciada en AI Usage Widget” (retorno loopback) en el
     navegador que use el teléfono (Chrome; si es otro, anotar cuál);
   - si el endpoint de uso acepta el token (si no, la app muestra “Claude aceptó el inicio de sesión
     pero se negó a informar el consumo”).
   Si cualquiera de los dos rechazos aparece, ver la alternativa en la sección 6.
4. **Widgets:** agregar uno por cuenta, elegir campos, probar los cuatro tamaños y el botón de
   actualizar. Esperado: proveedor, ventana, “disponible”, hora de los datos y reinicio legibles.
5. **Estados:** modo avión y tocar actualizar → “Sin conexión · Actualizado …” con valores
   atenuados; volver a conectar → valores normales.
6. **Segundo plano:** dejar el teléfono una o dos horas con la pantalla apagada y ver la hora de los
   datos del widget (Android puede demorar el refresco en Doze).
7. **Desconectar Codex:** Cuentas → Desplegar → Desconectar. Esperado: aviso “el proveedor aceptó el
   pedido de revocar…” y la cuenta desaparece de la app y de los widgets.
8. **Desconectar Claude:** igual. Esperado: aviso de borrado solo local; la sesión de claude.ai del
   navegador sigue abierta.
9. **Opcional, tráfico:** con una app como PCAPdroid (sin root) se puede ver a qué hosts se conecta
   la app; deberían aparecer solo `auth.openai.com`, `chatgpt.com`, `platform.claude.com` y
   `api.anthropic.com`.

## 6. Limitaciones conocidas

- **Política de Anthropic:** sus términos reservan el inicio de sesión de Claude.ai para Claude Code y
  las apps de Anthropic. Esta app usa el cliente de Claude Code y guarda el token; Anthropic podría
  restringir la cuenta (ver `docs/SEGURIDAD.md`). Alternativa sin ese riesgo: no conectar Claude y
  ver el uso en la app oficial o en claude.ai.
- **Permiso mínimo de Claude sin confirmar:** Claude Code solo consulta el uso con tokens que tienen
  `user:inference` y `user:profile`. La app pide solo `user:profile`. Si en el teléfono la
  autorización o el endpoint de uso lo rechazan, la alternativa concreta es pedir también
  `user:inference` (un cambio en `ClaudeOAuthConfig.SCOPE` y en el aviso, y una nueva compilación).
  Ese permiso es más amplio: permite usar el plan para enviar mensajes. No se activó sin acuerdo.
- **Endpoints no documentados:** `chatgpt.com/backend-api/wham/usage` y `api.anthropic.com/api/oauth/usage`
  no son APIs públicas; pueden cambiar sin aviso. La app lo mostraría como “Datos ilegibles” o
  “Error del proveedor”, nunca como 0 %.
- **Cliente OAuth de Codex CLI:** OpenAI no documenta su uso por apps de terceros.
- **Exención de batería:** pedirla es opcional; sin ella el refresco puede demorarse con la pantalla
  apagada.
