# Seguridad y privacidad de AI Usage Widget

Este documento describe qué guarda la app, a dónde se conecta, qué permisos pide a cada proveedor
y qué pasa al desconectar una cuenta. Describe el código de este repositorio; no es una auditoría
externa ni una garantía.

## Resumen

- No hay servidor propio, publicidad, analíticas ni telemetría. La app solo habla con las APIs de
  OpenAI (Codex) y Anthropic (Claude).
- Los tokens se guardan cifrados en el teléfono con AES-256-GCM y una clave del Android Keystore.
- El inicio de sesión de ambos proveedores ocurre en el navegador del teléfono, nunca en un WebView
  de la app. La app no ve contraseñas ni cookies.
- Desconectar borra todo lo local de esa cuenta. Para Codex, además, se pide a OpenAI que revoque el
  acceso; Anthropic no ofrece una revocación que la app pueda usar.

## Qué se guarda en el teléfono

| Dato | Dónde | Protección |
| --- | --- | --- |
| Tokens de Codex (access, refresh, id) | `files/sessions/` de la app | AES-256-GCM, clave del Keystore propia de Codex |
| Tokens de Claude (access, refresh) | `files/sessions/` de la app | AES-256-GCM, clave del Keystore propia |
| Cuenta (nombre visible, email, id de la cuenta del proveedor) | Base Room local | Almacenamiento privado de la app |
| Historial de consumo, intentos de actualización | Base Room local | Almacenamiento privado de la app |
| Estado de los widgets | DataStore de Glance | Almacenamiento privado de la app |

- Las claves del Keystore se generan en el dispositivo y no se pueden exportar. No se exige el
  desbloqueo del teléfono para usarlas, porque el refresco en segundo plano necesita leer los tokens
  con el teléfono bloqueado.
- `allowBackup="false"` y reglas de copia/transferencia que excluyen base de datos, preferencias y
  archivos: los tokens no van a copias de seguridad en la nube ni a transferencias entre
  dispositivos.
- Los objetos que contienen tokens tienen `toString()` redactado y no se escriben en registros. Los
  diagnósticos que se pueden copiar desde Ajustes pasan por un redactor de secretos.
- No se implementó criptografía propia: se usan `javax.crypto` (AES/GCM/NoPadding) y el Android
  Keystore.

## Conexiones de red

Todas las peticiones salen por un único cliente OkHttp que:

- solo acepta `https://` (la configuración de seguridad de red prohíbe el tráfico en claro y usa
  los certificados del sistema, sin certificados ni pines propios);
- no sigue redirecciones (una respuesta 3xx se trata como error, así un token nunca se reenvía a otra
  URL);
- tiene una lista cerrada de hosts **por proveedor**: los clientes de Codex solo pueden llamar a
  `auth.openai.com` y `chatgpt.com`; los de Claude solo a `platform.claude.com` y `api.anthropic.com`.
  Un token de un proveedor no puede enviarse al otro.

| Host | Para qué | Credencial enviada |
| --- | --- | --- |
| `auth.openai.com` | Código de dispositivo, intercambio y renovación de tokens, revocación al desconectar | Código de dispositivo / refresh token de Codex |
| `chatgpt.com` | `GET /backend-api/wham/usage` (consumo de Codex) | Access token de Codex |
| `platform.claude.com` | `POST /v1/oauth/token` (intercambio y renovación) | Código de autorización / refresh token de Claude |
| `api.anthropic.com` | `GET /api/oauth/usage` (consumo de Claude) | Access token de Claude |

Fuera de la app, el navegador abre `auth.openai.com/codex/device` (Codex) y
`claude.com/cai/oauth/authorize` → `claude.ai` (Claude). Esas páginas las carga el navegador con sus
propias cookies.

El único socket que abre la app es el receptor del retorno de Claude: escucha solo en `127.0.0.1` y
`::1` (nunca en la red Wi-Fi), en un puerto efímero, durante el inicio de sesión.

Las peticiones se identifican con el User-Agent propio `AIUsageWidget/1.0.0 (Android)`; la app no se
hace pasar por Codex CLI ni por Claude Code.

La única librería de terceros fuera de AndroidX/Kotlin/OkHttp es `com.qmdeve.liquidglass:core`
(efecto visual, MIT): 16 clases, sin permisos y sin código de red.

## Codex (OpenAI)

- **Inicio de sesión:** flujo de código de dispositivo de OpenAI, el mismo que usa Codex CLI. La app
  muestra un código; el usuario lo aprueba en `https://auth.openai.com/codex/device` en su navegador.
  La app solo abre esa URL exacta (se valida antes de abrirla).
- **Permisos (scopes):** `openid profile email offline_access`, los de Codex CLI. Permiten leer la
  identidad de la cuenta y renovar el acceso. El token resultante es el de la cuenta de ChatGPT para
  Codex: **no es de solo lectura**; la app solo lo usa para leer el consumo.
- **Cliente OAuth:** el cliente público de Codex CLI (`app_EMoamEEZ73f0CkXaXp7hrann`). OpenAI no
  documenta su uso por apps de terceros.
- **Desconectar:** se envía a `https://auth.openai.com/oauth/revoke` la misma revocación que hace
  `codex logout` (RFC 7009: refresh token + client id, o el access token si no hay refresh token),
  con un límite de 10 segundos. Después se borran los datos locales, aunque la revocación falle. La
  app informa si OpenAI aceptó el pedido (respuesta 2xx) o si no lo confirmó (sin conexión o
  respuesta de error). Según RFC 7009 el servidor responde 200 también para tokens ya inválidos, y
  si un access token ya emitido deja de valer enseguida depende de OpenAI: “aceptado” no garantiza
  que no quede ningún token vigente hasta su vencimiento.

## Claude (Anthropic)

- **Inicio de sesión:** autorización OAuth 2.0 con PKCE (S256) en el navegador del teléfono (Custom
  Tab), con retorno a `http://localhost:<puerto>/callback`, como hace Claude Code. El receptor:
  - valida el `state` (32 bytes aleatorios) en tiempo constante e ignora retornos con `state`
    incorrecto, repetidos o con parámetros duplicados, sin abortar el intento;
  - solo acepta `GET /callback`; responde páginas `no-store` con una CSP que bloquea todo recurso y
    nunca repite el código en la respuesta;
  - se cierra al terminar o a los 10 minutos.
  El código se intercambia junto con el verificador PKCE, que nunca sale del teléfono hasta ese
  momento.
- **Permisos (scopes):** solo `user:profile`. La app **no** pide `user:inference` (enviar mensajes
  con el plan) ni `org:create_api_key` (crear claves de API), que el WebView del proyecto original sí
  pedía. La app usa el token solo para leer el uso (`GET /api/oauth/usage`) y renovarlo.
  - Anthropic no documenta qué permite cada scope. Claude Code usa el scope `user:profile` para leer
    el perfil (email, organización, plan) y el uso, y con sus tokens también llama a endpoints de
    cuenta que **modifican** datos (por ejemplo `PATCH /api/oauth/account/settings` para la
    preferencia de uso de datos). No se pudo comprobar cuáles de esos aceptan un token con solo
    `user:profile`, así que **no debe considerarse un token de solo lectura**: es una credencial de
    la cuenta de Claude.
  - Claude Code (2.1.292) solo consulta el uso cuando su token tiene `user:inference` **y**
    `user:profile`; que el servidor acepte un token con solo `user:profile` para el endpoint de uso
    está pendiente de comprobar con una cuenta real (ver `docs/INFORME-PRUEBAS.md`). Si no lo
    acepta, la app lo dice al conectar y no guarda nada.
- **Cliente OAuth:** el cliente público de Claude Code (`9d1c250a-e61b-44d9-88ed-5944d1962f5e`); la
  página de autorización lo mostrará como “Claude Code”.
- **Desconectar:** Anthropic no publica un endpoint de revocación y Claude Code también borra solo sus
  credenciales locales al cerrar sesión. La app borra los datos locales e indica que no hubo
  revocación remota. El access token restante expira solo; el refresh token queda borrado del
  teléfono.

### Limitación importante: política de Anthropic

Los términos de Anthropic para Claude Code
(<https://code.claude.com/docs/en/legal-and-compliance>) dicen que la autenticación OAuth está
pensada para el uso normal de Claude Code y de las apps nativas de Anthropic, que los desarrolladores
externos no pueden ofrecer el inicio de sesión de Claude.ai en sus apps ni guardar o intermediar
credenciales o tokens de Claude.ai, y que Anthropic puede tomar medidas sin aviso.

Esta app es para uso personal, usa el flujo oficial en el navegador, pide el permiso mínimo y no usa
el plan para generar contenido, pero **sí guarda un token de Claude.ai** usando el cliente de Claude
Code. Eso puede quedar fuera de lo que Anthropic permite y existe el riesgo de que Anthropic restrinja
la cuenta. La pantalla de conexión lo advierte antes de abrir el navegador.

Alternativas sin ese riesgo:

1. No conectar Claude en la app y ver el consumo en la app oficial de Claude o en claude.ai
   (Ajustes → Uso). Los widgets de Codex funcionan igual.
2. Usar solo Codex en los widgets.

## Lo que la app no hace

- No envía datos a servidores del desarrollador (no los hay) ni a terceros.
- No muestra publicidad ni incluye SDK de analíticas, crash reporting o telemetría.
- No comprueba actualizaciones ni descarga APK: el comprobador del proyecto original se eliminó. Las
  actualizaciones son compilaciones propias firmadas con la misma clave.
- No borra ni modifica las sesiones del navegador del usuario.
