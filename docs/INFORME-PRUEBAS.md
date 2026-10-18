# Informe de pruebas — AIMeter 1.1.0

Qué se comprobó, cómo y qué falta comprobar en un teléfono con cuentas reales. Cada resultado indica
su tipo:

- **Simulado:** pruebas unitarias en la JVM con servidores HTTP falsos (MockWebServer), dobles de
  prueba o Robolectric (framework Android simulado).
- **Emulador:** emulador Android oficial (imagen `google_apis` x86_64) en un runner de GitHub
  Actions. Es Android real, pero no un teléfono, sin cuentas y sin launcher configurado a mano.
- **Servidor real sin credenciales:** peticiones a los servidores de OpenAI o Anthropic con valores
  falsos (código, token) para ver cómo responden. No usan ninguna cuenta.
- **Pendiente (teléfono):** solo se puede comprobar con un teléfono y cuentas reales. **No se hizo.**

Base: commit `69d75f082fb143643feeb606e50bfd3d879439ac` (69d75f0) de la rama `ccr-4fef7446-jet13v`,
del que se compiló el APK 1.1.0. Los commits posteriores son la 1.1.1, que todavía no se compiló
(sección 8). Las versiones 1.0.x se llamaban “AI Usage Widget”: la 1.0.2 salió del commit d0c28d7,
la 1.0.1 del e5a3795 y la 1.0.0 del d0d3d06.

**En resumen:** el rediseño de 1.1.0 (todas las cuentas a la vez, el widget de varias cuentas, la
notificación con varias cuentas, el estilo Sobria, el ícono y los permisos) pasó las pruebas
automáticas, la compilación y lint en GitHub Actions, y el emulador (widgets dibujados por Android y
recorrido de la app). **Ninguna función nueva se probó todavía en un teléfono ni con cuentas
reales.** El inicio de sesión, el cifrado, la red y el borrado al desconectar no cambiaron desde
1.0.2 (salvo el User-Agent, ahora `AIMeter/1.1.0 (Android)`), y esa versión sí se probó en el
teléfono (sección 5). Revisar el emulador encontró un defecto visible en el widget **Cuenta** 3×2 y
dos textos de Ajustes que no entran (sección 3); están corregidos para 1.1.1.

## 1. Compilación y análisis (GitHub Actions)

| Comprobación | Resultado |
| --- | --- |
| JDK Temurin 21.0.12, Gradle 9.5.1, AGP 9.2.1, SDK 37.0 / build-tools 37.0.0 | Usados en todas las ejecuciones |
| Pruebas unitarias `:app:testDebugUnitTest` | 776 pruebas en 128 clases, 0 fallos (CI n.º 13 del commit 69d75f0). En el commit 4e7fa99, antes de los últimos ajustes de colores, del nombre de la pestaña y del arreglo de lint: 776 pruebas en 128 clases, 0 fallos (CI n.º 12) |
| APK debug y release (R8 + `lintVitalRelease`) | Compilan |
| Lint (`lintDebug`) | 0 errores; 78 advertencias y 2 sugerencias. La CI anterior (n.º 12) encontró un error nuevo, `NonObservableLocale`: el formato de las fechas cortas podía leer `Locale.getDefault()` dentro de un composable, que no se actualiza al cambiar el idioma de la app; se corrigió en 7a54395. Entre las advertencias: 24 textos sin usar, los mismos de 1.0.2 (heredados del original); 7 `SmallSp` por los textos de 10 sp de las vistas previas de los widgets, del mismo tamaño que en el widget; `Typos` señala “sign-in in”, que no es un error; `MissingQuantity` pide la forma `many` en dos plurales en español, que Android reemplaza por `other`; y la `InlinedApi` de siempre por la constante del permiso de notificaciones de Android 13, que solo se pide en Android 13 o posterior. La única relacionada con seguridad, `ExportedContentProvider`, señala `androidx.startup.InitializationProvider`, que en el manifiesto final queda `exported=false`. El resto son versiones de dependencias y estilo |
| Permisos del APK release (`aapt2 dump permissions`) | `INTERNET`, `ACCESS_NETWORK_STATE`, `POST_NOTIFICATIONS`, y `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` que agrega WorkManager, más el permiso propio `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` que AndroidX declara para sus receptores internos. Ya no está `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |
| Componentes del manifiesto final | Los receptores de los dos widgets con `exported=false`; sus dos pantallas de configuración con `exported=true`, porque las abre el launcher. Los demás son los de 1.0.2 (AndroidX y la actividad principal) |
| Dependencias de ejecución del release | AndroidX, Kotlin/kotlinx, OkHttp/Okio, `listenablefuture` y `jspecify`. Ya no está `com.qmdeve.liquidglass:core` (se quitó con el efecto de vidrio). Ningún SDK de analíticas, publicidad, crash reporting ni Google Play Services |
| Reproducibilidad | El commit 69d75f0 compilado en cuatro runners distintos (CI, exportación y los dos emuladores) dio el mismo APK sin firmar, `8e773777a0910c75c344a1b5031c3351af70a8353513627bb7635e1dced5234a`. Firmar dos veces da el mismo APK firmado, con las mismas 1004 entradas que el sin firmar |

Durante el desarrollo también se usó un arnés JVM local (sin SDK de Android, que no se puede
descargar en el entorno de trabajo): 690 pruebas, todas correctas salvo 27 que necesitan el runtime
de Android (Robolectric) y que solo corren en GitHub Actions, donde pasan.

## 2. Qué cubren las pruebas automáticas (simulado)

### Novedades de 1.1.0

- **Inicio con todas las cuentas:** cada cuenta visible es una tarjeta, en el orden elegido; las
  ocultas solo se cuentan. Una cuenta sin datos no muestra cifras (nunca 0 %); una ventana en 0 %
  dice “Agotado”; los niveles siguen los umbrales de Ajustes; la hora del encabezado es la de los
  datos más nuevos; el estado de cada tarjeta dice cuánto se puede confiar en sus cifras.
- **Orden y visibilidad (pestaña Cuentas):** sin preferencias, las cuentas quedan en el orden en que
  se guardaron y todas visibles; el orden elegido manda y una cuenta nueva va al final; ocultar y
  mostrar cambian solo esa cuenta; subir y bajar la intercambian con la vecina y no pasan de los
  extremos; si falla el guardado, la lista no cambia. Al desconectar, la cuenta sale del orden y de
  las ocultas, y vuelve a su lugar si el borrado falla. Un orden guardado ilegible se lee como “sin
  orden” en lugar de romper la app.
- **Notificación con varias cuentas:** el título da lo disponible de la ventana que limita a cada
  cuenta, con el nombre de la cuenta cuando hay dos del mismo proveedor; el cuerpo, una línea por
  cuenta con cada ventana. Una ventana en 0 % dice “agotado”, y los datos posiblemente viejos, las
  fallas y la sesión vencida se nombran; una cuenta sin cifras lo dice. Una cuenta elegida en Ajustes
  se muestra aunque esté oculta; si no hay una elegida, solo las visibles. Dos actualizaciones a la
  vez publican en el orden en que leyeron los datos, así que queda la más nueva (esa prueba falla sin
  el bloqueo que se agregó).
- **Widget Cuentas:** cada cuenta es una fila con sus ventanas y la que la limita; las cifras que no
  son actuales conservan el valor pero pierden el color; sin cifras no hay número (nunca 0 %). Las
  filas dependen del alto (al menos una, hasta 8) y las celdas del ancho (dos o tres), y la ventana
  que limita nunca queda afuera. Por defecto sigue las cuentas visibles en el orden de Inicio; las
  elegidas se muestran en ese orden aunque estén ocultas, y las desconectadas se saltean; la
  configuración no deja guardar una elección vacía; lo que el widget guarda se vuelve a leer igual.
- **Widget Cuenta:** usa los mismos niveles que Inicio (una ventana en 0 % dice “Agotado”, ya no
  “Escaso”); las cifras que no son actuales se atenúan.
- **Ajustes:** “Todas las cuentas visibles” para la notificación; el último resultado de la
  actualización en segundo plano resume todas las cuentas (“Correcta” solo si lo fue la de cada una;
  sin cuentas, lo dice); el aviso de batería abre la lista de optimización de batería del sistema.
- **Estilo:** paleta Sobria clara y oscura; colores de los widgets iguales a los de la paleta; la
  licencia de Geist que muestra la app es la del repositorio y nombra el copyright que trae el
  archivo de la fuente; fechas cortas de reinicio (la hora si es hoy, el día de la semana si es
  dentro de la semana).

### Sin cambios desde 1.0.2

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
- **Segundo plano:** la tarea periódica no exige red (para registrar “sin conexión”); ni la falta de
  red ni un 429 provocan reintentos con backoff; estar sin conexión no genera alertas de fallos
  repetidos.
- **Idioma:** un sistema en español de cualquier región usa la traducción española.

## 3. Emulador

Workflow manual **Emulator check (manual)**: emulador oficial de Android con imagen `google_apis`
x86_64 en un runner de GitHub Actions, en dos configuraciones: **Android 15 (API 35) en español
(es-AR)** y **Android 12 (API 31) en inglés**. Se instala el APK release procesado por R8 (firmado
con una clave descartable creada en el job, nunca con la de release) y el debug para las pruebas
instrumentadas.

### Corrida 14 (versión 1.1.0, commit 69d75f0)

Primera compilación de 1.1.0, la que se entrega. Las dos configuraciones usaron el mismo APK sin
firmar que la CI y la exportación.

**Android 15 (API 35) en español:**

- Pruebas instrumentadas correctas (`OK (4 tests)`): todos los estados de los dos widgets en inglés,
  español y español oscuro, dibujados por Android con el mismo layout Glance que el widget real, y
  la lectura de `expires_at` de Codex.
- Recorrido del release correcto (`SMOKE: OK`): Inicio vacío (“Agregá tu primera cuenta”), Cuentas,
  Ajustes hasta Acerca de (versión 1.1.0, aviso de que no está afiliada), selector de proveedor, y los
  inicios de sesión de Claude y Codex en el navegador. El receptor loopback pasó las mismas
  comprobaciones que en 1.0.2 (escucha solo en `127.0.0.1` y `::1`, Codex en el puerto 1455,
  `state` incorrecto → 400, nunca repite el código, `no-store`, CSP, parámetros repetidos → 400,
  POST → 405, otras rutas → 404, los retornos falsos no cortan el intento, cancelar lo cierra). Con
  la pestaña del navegador delante Android le quitó la red a la app y se la devolvió al volver,
  como en 1.0.2. La pantalla de código de dispositivo mostró un código (no se capturó) y se canceló.
- Permiso de notificaciones: sin permiso, Ajustes mostró el interruptor apagado y el aviso; la
  explicación de la app, el diálogo de Android y **Permitir** dejaron el permiso concedido y la app
  publicó su notificación (sin cuentas conectadas).
- Sin cierres inesperados. Registros: 5 valores enmascarados en líneas de la app, los cinco
  fragmentos de identificadores de tareas de WorkManager (UUID); ninguna credencial.

**Android 12 (API 31) en inglés:**

- Pruebas instrumentadas correctas.
- En el recorrido del release pasaron todas las comprobaciones de inicio de sesión, receptor
  loopback y código de dispositivo, sin cierres y con 0 valores enmascarados en líneas de la app.
  El script marcó 4 problemas porque no tocó las pestañas inferiores: daba por “fuera de pantalla”
  un elemento cuyo borde inferior estuviera a menos de 40 px del final, y en este emulador, sin
  barra de navegación, las pestañas del rediseño llegan justo al borde. La captura muestra las
  pestañas en su lugar. Por eso no hay capturas de Cuentas ni Ajustes en inglés en Android 12. El
  script se corrigió (82c251b), pero no se pudo volver a correr (sección 8).

**Revisión de las capturas:**

- Widget **Cuentas** en 3×2, 4×2, 4×3 y 5×2, claro y oscuro: cada cuenta con su nombre, proveedor y
  ventanas; 0 % en rojo; datos viejos atenuados con “Sin conexión · <hora>” en ámbar; sesión vencida
  en rojo y sin cifras; “+1” cuando no entran todas las filas; sin cuentas, o sin cuentas elegidas,
  un texto que lo explica. Falta una captura del diseño angosto (menos de 250 dp de ancho, una
  cifra por cuenta), que puede tocarle a un widget de 3 columnas: todas las capturas medían 252 dp o
  más. Se agregó al test para la próxima corrida.
- Widget **Cuenta** en 3×1, 4×1 y 4×2: correcto. **Defecto en 3×2:** el estado comparte renglón con
  el nombre de la cuenta y lo deja sin lugar (“Cla…” en inglés; en español el nombre desaparece), y
  un estado largo se corta él mismo (“Límite de peticiones · Actualizado '…”). En un teléfono, donde
  ese tamaño puede medir menos que en la captura, es peor. Corregido para 1.1.1.
- **Ajustes en español:** “Según el sistema” no entra en su segmento del selector de tema (dos
  renglones pegados al borde) y “Todas las cuentas visibles” parte “Cuentas en la notificación” en
  tres renglones. Textos acortados para 1.1.1.
- Inicio y Cuentas vacíos, selector de proveedor, avisos de Claude y Codex, Acerca de: sin
  problemas visibles.
- Como en 1.0.x, las capturas dibujan el layout fuera del launcher: colocar el widget en una
  pantalla de inicio real (configuración, redimensionado, botón ↻) queda para el teléfono.

### Corridas de 1.0.x (hasta la 13)

Lo que sigue se comprobó con las versiones 1.0.x, antes del rediseño. Los widgets que describe son los
de 1.0.x; el inicio de sesión, el receptor loopback y la red no cambiaron desde entonces.

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

No se repitieron para 1.1.0, porque las peticiones son las mismas de 1.0.2 salvo el User-Agent.

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

La versión 1.1.0 **todavía no se probó en el teléfono**. Lo que sigue corresponde a 1.0.x.

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

### Tercera prueba (versión 1.0.2)

Según las capturas del dueño:

- **Codex funcionó con el inicio de sesión en el navegador:** la cuenta se conectó y la app muestra
  plan Pro 20x y la ventana de 7 días con 0 % disponible (“Agotado”) y reinicio el 22/10. Queda
  confirmado con una cuenta real el recorrido completo: autorización, canje del código con PKCE,
  lectura del consumo y guardado. También que una ventana agotada no impide conectar la cuenta.
- **Claude** siguió conectado después de instalar la 1.0.2 encima de la 1.0.1.
- **Notificación persistente:** el interruptor quedó encendido (permiso concedido), con la cuenta
  actual y la ventana de 7 días.
- **Widgets:** uno por cuenta en la pantalla de inicio, con datos de las 16:09.
- **Detalles a corregir en el rediseño:** en Inicio, “Datos actuales” aparece en rojo para la cuenta
  agotada, como si fuera un error; el widget de Codex dice “Escaso” con 0 % mientras Inicio dice
  “Agotado”; y la pestaña Cuentas muestra la cuenta actual dos veces.

- **En 1.1.0** esos tres detalles cambiaron: con datos al día, Inicio no muestra ningún texto de
  estado (ni en rojo); el widget dice “Agotado” con 0 %, como Inicio; y la pestaña Cuentas lista
  cada cuenta una sola vez. Falta verlo en el teléfono.

## 6. Pendiente: comprobar en el teléfono

Nada de esto se probó con 1.1.0 en un teléfono ni con cuentas reales. Orden sugerido, con el
resultado esperado:

1. **Actualizar encima de 1.0.2:** instalar el APK nuevo sin desinstalar. Esperado: siguen las dos
   cuentas, sus sesiones y el historial; el ícono y el nombre pasan a AIMeter; los widgets ya
   colocados toman el diseño nuevo (pueden verse atenuados hasta la primera actualización).
2. **Inicio:** las dos cuentas a la vez, cada una con sus ventanas, y ↻ o deslizar hacia abajo
   actualiza ambas. Con la cuenta de Codex agotada: “Agotado” en rojo en esa ventana, sin texto de
   estado en rojo.
3. **Cuentas:** ocultar una cuenta (desaparece de Inicio, de la notificación y del widget Cuentas sin
   configurar), volver a mostrarla, y subir o bajar una (el orden cambia en Inicio, la notificación
   y los widgets). Renombrar una cuenta y ver el nombre nuevo en Inicio y en los widgets.
4. **Widget Cuentas:** agregarlo sin configurar (muestra las cuentas visibles), probar tamaños
   angosto y ancho, y alto suficiente o no (“+N”); luego configurarlo con **Elegir cuentas**; tocar
   ↻ y tocar el resto (abre la app). Ver la vista previa en el selector de widgets.
5. **Widget Cuenta:** al soltarlo se abre la configuración; probar los cuatro tamaños. En 3×2 el
   nombre de la cuenta puede no verse (defecto conocido de 1.1.0, sección 3).
6. **Notificación:** con varias cuentas, el título con lo disponible de cada una y el cuerpo con una
   línea por cuenta; luego elegir una sola cuenta en Ajustes.
7. **Ajustes → Mejorar la actualización en segundo plano → Abrir ajustes:** debe abrir la lista de
   optimización de batería del sistema. Si un teléfono no tuviera esa pantalla, el botón no hace
   nada (la app no se cierra).
8. **Ícono temático:** en Android 13 o posterior, con íconos temáticos activados, el ícono de AIMeter
   debe verse monocromático como los demás.
9. **Modo oscuro:** la app y los widgets cambian de colores con el modo del teléfono.
10. **Estados:** modo avión y tocar actualizar → “Sin conexión” con las cifras atenuadas en Inicio y
    en los widgets; volver a conectar → cifras normales.
11. **Segundo plano:** dejar el teléfono una o dos horas con la pantalla apagada y ver la hora de los
    datos en los widgets (Android puede demorar el refresco en Doze).
12. **Desconectar** (Codex y Claude): igual que en 1.0.x, y además la cuenta debe desaparecer del
    widget Cuentas y de la notificación.
13. **Opcional, tráfico:** con una app como PCAPdroid (sin root) se puede ver a qué hosts se conecta
    la app; deberían aparecer solo `auth.openai.com`, `chatgpt.com`, `platform.claude.com` y
    `api.anthropic.com`.

## 7. Limitaciones conocidas

- **Defectos conocidos de 1.1.0**, corregidos para 1.1.1 (sección 8): en el widget **Cuenta** 3×2 un
  estado largo deja sin lugar al nombre de la cuenta; en Ajustes, en español, “Según el sistema” no
  entra en su botón y “Cuentas en la notificación” ocupa tres renglones.
- **Política de Anthropic:** su documentación de Claude Code dice que no permite a desarrolladores de
  terceros ofrecer el inicio de sesión de Claude.ai ni guardar sus tokens, y que puede aplicarlo sin
  aviso. Esta app usa el cliente de Claude Code y guarda el token; Anthropic podría restringir la
  cuenta (ver `docs/SEGURIDAD.md` y `docs/PRODUCTO-Y-PLAY-STORE.md`). Alternativa sin ese riesgo: no
  conectar Claude y ver el uso en la app oficial o en claude.ai.
- **Permiso mínimo de Claude:** confirmado en el teléfono con 1.0.1: la autorización aceptó
  `user:profile` solo y el endpoint de uso respondió con ese token (sección 5). Claude Code pide
  además `user:inference`; si Anthropic dejara de aceptar `user:profile` solo, la app lo mostraría
  como “Claude aceptó el inicio de sesión pero se negó a informar el consumo”.
- **Cliente de Codex CLI:** OpenAI no autoriza ese cliente para apps comerciales; para una versión
  publicada habría que usar su programa “Sign in with ChatGPT” (ver
  `docs/PRODUCTO-Y-PLAY-STORE.md`).
- **Endpoints no documentados:** `chatgpt.com/backend-api/wham/usage` y `api.anthropic.com/api/oauth/usage`
  no son APIs públicas; pueden cambiar sin aviso. La app lo mostraría como “Datos ilegibles” o
  “Error del proveedor”, nunca como 0 %.
- **Cliente OAuth de Codex CLI:** OpenAI no documenta su uso por apps de terceros.
- **Batería:** la app ya no pide la exención de la optimización de batería (Google Play la reserva
  para otros usos); se puede quitar a mano desde el enlace de Ajustes. Sin eso, el refresco puede
  demorarse con la pantalla apagada.

## 8. Después de 1.1.0: 1.1.1, todavía sin compilar

Commits a779dda a 2a3e7f8: el encabezado del widget **Cuenta** (3×2 en dos renglones, estado
“estado · hora” en todos los tamaños), los dos textos de Ajustes, la limpieza interna de la “cuenta
actual” (preferencias, importación, borrado, lista de cuentas y diagnóstico con el mismo detalle
para cada cuenta) y la versión 1.1.1 (código 5). Ver `CHANGELOG.md`.

Comprobado:

- Arnés JVM local: 693 pruebas, todas correctas salvo 26 que necesitan Robolectric y no corren
  localmente. Incluye las nuevas: el diagnóstico (detalle por cuenta, sin sección de cuenta actual,
  secretos tapados) y la migración que borra la cuenta actual guardada por 1.1.0 sin tocar el resto.
- El código principal, las pruebas y las pruebas instrumentadas compilan contra los stubs locales.

**Pendiente, bloqueado:** GitHub Actions dejó de arrancar trabajos en este repositorio: la CI del
commit 6b33206 (run `39086235209`) falló dos veces en 2–3 segundos sin que se le asignara un
runner y sin registros. Es lo que pasa cuando una cuenta agota los minutos de Actions incluidos para
repositorios privados o tiene un problema de facturación; la causa exacta solo se ve en la página
de la ejecución y en la facturación de GitHub. Hasta que vuelva a funcionar no se pudieron correr,
para 1.1.1: las pruebas con Robolectric (entre ellas las de borrado, importación, lista y renombrado
de cuentas que cambiaron), lint, la compilación release, el emulador (con las capturas nuevas y el
script corregido) ni la exportación para firmar. Por eso se entrega 1.1.0.
