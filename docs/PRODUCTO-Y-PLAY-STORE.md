# De app personal a producto: Google Play y términos de los proveedores

Investigación del 17 de octubre de 2026, para decidir si la app (entonces AI Usage Widget, desde 1.1.0
AIMeter) puede publicarse en Google Play y qué habría que cambiar. Las citas son textuales. Cuando una página no se pudo abrir desde el
entorno de trabajo (openai.com, support.google.com y play.google.com estaban bloqueados), el texto
viene de extractos de buscadores y está marcado **(extracto)**. Lo que es interpretación propia está
marcado **(interpretación)**. No es asesoramiento legal.

## 1. Resumen

- **Para uso personal** la app funciona: Codex y Claude conectados en el teléfono, con widgets y
  notificación (`docs/INFORME-PRUEBAS.md`).
- **Publicarla tal como está no es viable.** El inicio de sesión usa los clientes OAuth de Codex CLI y
  Claude Code, guarda los tokens y lee endpoints de consumo no documentados. Anthropic lo prohíbe
  explícitamente a desarrolladores de terceros, OpenAI no lo autoriza para apps comerciales, y Google
  Play no admite apps que usen un servicio en contra de sus términos.
- **Lo que sí se puede hacer ya:** el rediseño (todas las cuentas a la vez, widgets de varias
  cuentas), sacar logos de terceros y el permiso de batería, y preparar el empaquetado para Play.
  En paralelo, pedir acceso oficial a OpenAI y permiso a Anthropic.
- **Estado en 1.1.0 (18/10/2026):** el rediseño, los logos y el permiso de batería ya están (puntos
  2 y 3 de la sección 4). Sigue bloqueando la publicación el acceso oficial de los proveedores.

## 2. Términos de los proveedores

### Anthropic (Claude)

- Claude Code, “Legal and compliance” (https://code.claude.com/docs/en/legal-and-compliance, leída
  el 17/10/2026): “Anthropic does not permit third-party developers to offer Claude.ai login into
  their own applications, or to route requests through Free, Pro, or Max plan credentials on behalf
  of their users. Moreover, developers may not collect, store, or intermediate Claude.ai credentials
  or session tokens — sign-in to a Claude account must complete through Anthropic's own flow.” Puede
  aplicarlo “without prior notice”. Esa frase sobre guardar tokens apareció el 22/08/2026 (según un
  espejo diario de esa documentación).
- Ayuda de Claude (https://support.claude.com/en/articles/13189465): “If you're building a product,
  application, or tool for others, use API key authentication through Claude Console or a supported
  cloud provider.”
- Términos de consumo (https://www.anthropic.com/legal/consumer-terms, vigentes desde 08/10/2025):
  prohíben acceder a los servicios “through automated or non-human means” salvo con API key o permiso
  explícito, y usar sus nombres o logos sin permiso escrito.
- Antecedentes: el 09/01/2026 Anthropic bloqueó del lado del servidor el uso de la suscripción en
  herramientas de terceros como OpenCode (prensa); OpenCode quitó su integración con Anthropic el
  19/03/2026 por pedido legal (https://github.com/anomalyco/opencode/pull/18186).
- No existe una forma oficial para que una app de terceros lea los límites de 5 horas y semanal de
  una suscripción. Las APIs de uso y costos de Anthropic son para organizaciones de la API, con
  claves de administrador, y no informan esos límites.

**Consecuencia:** Claude solo podría estar en una versión publicada con permiso escrito de Anthropic.
Incluso en uso personal, Anthropic puede restringir una cuenta que use esta app (la app ya lo avisa
antes de conectar).

### OpenAI (Codex)

- Documentación de Codex App Server (https://developers.openai.com/codex/app-server) **(extracto)**:
  “App-server authentication has never been permitted for commercial or hosted services.”
- “Sign in with ChatGPT” (https://developers.openai.com/siwc) **(extracto)**: inicio de sesión de
  identidad desde el 29/07/2026 y uso del plan desde el 29/09/2026. “Currently available to selected
  commercial partners through a limited trial. ChatGPT plan usage is available to all open-source
  partners and selected private clients.” Las apps pagas o alojadas deben completar un formulario
  de interés. El flujo para código abierto registra un cliente propio y usa retorno a 127.0.0.1,
  pero sirve para pedidos a la API de modelos y pide “do not point it at ChatGPT's backend-api
  endpoints”, que es donde está el consumo que lee esta app. No se encontró una forma documentada de
  leer los límites de 5 horas y semanal.
- Términos de “Sign in with ChatGPT” **(extracto)**: “Do not impersonate OpenAI, another application,
  or another open-source project.” Hoy la app se presenta como Codex CLI.
- Términos de uso (versión 11/12/2024; la vigente no se pudo abrir): prohíben extraer datos de forma
  automática o programática y usar su nombre y logo fuera de sus guías de marca.

**Consecuencia:** para publicar Codex hay que pedir acceso por el formulario de “Sign in with ChatGPT”
y preguntar específicamente por la lectura de los límites del plan desde una app Android.

## 3. Requisitos de Google Play

| Tema | Qué pide | Fuente |
| --- | --- | --- |
| Cuenta | Cuenta personal: US$ 25 una vez, mayor de 18, verificación de identidad (perfil de pagos, documento si hace falta, correo y teléfono) y verificación en un teléfono Android 10+ sin root **(extracto)** | support.google.com/googleplay/android-developer/answer/13634885 |
| Prueba cerrada | Cuentas personales creadas después del 13/11/2023: al menos **12 testers** inscriptos durante **14 días seguidos** antes de pedir acceso a producción **(extracto)** | …/answer/14151465 |
| Nivel de API | Desde el 31/08/2026, apps nuevas y actualizaciones deben apuntar a Android 16 (API 36). La app ya usa targetSdk 36 | developer.android.com/google/play/requirements/target-sdk |
| Formato y firma | Android App Bundle (.aab) y Play App Signing obligatorios. Se puede subir la clave actual (RSA 4096) como clave de firma de la app antes del primer lanzamiento abierto o de producción, con una clave de subida aparte; así quien ya la tiene instalada puede actualizar desde Play **(interpretación)** | developer.android.com/studio/publish/app-signing; …/answer/9842756 |
| Privacidad | Política de privacidad pública (página web, no PDF) enlazada en Play Console y en la app, y formulario de Seguridad de los datos. Los tokens que salen del teléfono hacia el proveedor se declararían como “User IDs” para funcionamiento de la app, cifrados en tránsito y borrables **(interpretación)** | …/answer/10144311; …/answer/10787469 |
| Batería | `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` solo para casos como mensajería sin FCM, seguridad o automatización; un widget de consumo no califica. Hay que quitarlo y, si acaso, abrir la pantalla general de ajustes de batería | developer.android.com/training/monitoring-device-state/doze-standby |
| Uso de servicios | “Apps that access or use a service or API in a manner that violates its terms of service” están prohibidas **(extracto)** | …/answer/16559646 |
| Marcas | No usar logos de otras empresas ni dar a entender afiliación (ícono, nombre, ficha, capturas y la app) **(extracto)** | …/answer/9888374; …/answer/9888072 |
| Revisión | Si la app necesita iniciar sesión, hay que dar a los revisores credenciales siempre válidas y sin códigos de dos pasos **(extracto)**. Con suscripciones pagas de terceros es un problema práctico; habría que evaluar un modo de demostración | …/answer/15748846 |
| Cobro | Compras y suscripciones con Google Play Billing; para compradores en Argentina, 15 % hasta el primer US$ 1 millón anual (esquema vigente hasta el 30/09/2027) **(extracto)** | …/answer/10281818; …/answer/112622 |
| APK fuera de Play | La verificación de desarrolladores para apps instaladas fuera de Play llega a todo el mundo en 2027 (fecha sin anunciar); hoy rige en Brasil, Indonesia, Singapur y Tailandia | developer.android.com/developer-verification |

Además: declaraciones obligatorias de funciones financieras y de salud aunque la respuesta sea
“ninguna”, y desde marzo de 2026 Play puede advertir sobre apps que retienen wake locks mucho tiempo
(WorkManager incluido) **(extracto)**.

## 4. Cambios en la app para una versión publicable

1. Inicio de sesión con acceso oficial de cada proveedor (o sin ese proveedor) y sin presentarse como
   Codex CLI ni Claude Code. **Pendiente:** depende de OpenAI y Anthropic.
2. Sin logos de OpenAI ni Anthropic; nombre e ícono propios; aclaración de que no está afiliada.
   **Hecho en 1.1.0:** nombre AIMeter, ícono propio, proveedores nombrados con texto, aviso en
   Ajustes → Acerca de.
3. Sin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. **Hecho en 1.1.0:** el aviso abre la lista de apps
   optimizadas del sistema.
4. App Bundle, Play App Signing con la clave actual y una clave de subida nueva. **Pendiente.**
5. Política de privacidad y formulario de Seguridad de los datos. **Pendiente.**
6. Modo de demostración para la revisión, si Google lo acepta. **Pendiente.**

## 5. Plan propuesto

1. **Viabilidad (bloquea la publicación):** formulario de interés de “Sign in with ChatGPT” y pedido de
   permiso a Anthropic. Mientras tanto la app sigue como APK personal.
2. **Rediseño (sirve en cualquier caso):** todas las cuentas a la vez en Inicio, sin “cuenta actual”;
   mostrar, ocultar, ordenar y renombrar cuentas; nueva tipografía y estética; widgets de varias
   cuentas; notificación con varias cuentas; sin logos de terceros. **Hecho en 1.1.0.**
3. **Preparación para Play:** los puntos de la sección 4 que no dependen de los proveedores.
   **En curso:** hechos el 2 y el 3; faltan el 4, el 5 y el 6.
4. **Publicación:** prueba cerrada con 12 testers durante 14 días, ficha de la tienda y producción.

## 6. Decisiones del dueño

Tomadas después de esta investigación:

- Dirección visual: A, “Sobria” (descripta en `DESIGN.md`).
- Nombre: **AIMeter**.
- Gratis y de código abierto. Para eso falta hacer público el repositorio (hoy es privado).

Pendiente:

- Si se piden los accesos oficiales a OpenAI y Anthropic, y con qué textos.
