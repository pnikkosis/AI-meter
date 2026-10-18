# AIMeter

App Android gratuita, de código abierto y sin publicidad para ver en la pantalla de inicio cuánto
queda de los límites de uso de las suscripciones de **ChatGPT/Codex** y **Claude**: cada ventana de
límite (5 horas, 7 días, por modelo…), lo disponible y cuándo se reinicia. No mide facturación de
APIs.

Es un fork de [CodexMeter](https://github.com/KyoMio/CodexMeter) 0.2.4 (commit `2f7bf52`, licencia
MIT; ver `LICENSE` y `NOTICE.md`). Funciona solo en el teléfono: sin computadora encendida, sin
servidor propio, sin servicios de pago.

## Qué hace

- Conecta todas las cuentas de Codex y de Claude que se quiera, con inicio de sesión en el navegador
  del teléfono. Ninguna es “la principal”.
- **Inicio** muestra todas las cuentas a la vez; en **Cuentas** se ocultan, se ordenan, se renombran
  y se desconectan.
- Dos widgets: **Cuentas** (varias cuentas en uno, con la cifra que limita a cada una o cada ventana
  con su barra) y **Cuenta** (una cuenta en detalle, de 3×1 a 4×2).
- Notificación persistente opcional con todas las cuentas visibles.
- Indica la hora de los datos y distingue datos actuales, desactualizados, sin conexión, límite de
  peticiones, error del proveedor y sesión vencida. Un error nunca se muestra como 0 %.
- Actualización manual (widget, app) y periódica con WorkManager.
- Desconectar borra los datos locales de la cuenta; en Codex además pide a OpenAI revocar el acceso.
- Interfaz en español, inglés y chino, en modo claro u oscuro.

## Diferencias con CodexMeter

- Nombre, ícono y firma propios; identificador `io.github.pnikkosis.aiusagewidget`.
- Solo Codex y Claude: se quitaron los demás proveedores, la conversión de monedas
  (open.er-api.com) y el comprobador de actualizaciones de GitHub.
- Todas las cuentas a la vez, sin “cuenta actual”; widget y notificación de varias cuentas.
- Claude inicia sesión en el navegador con PKCE y retorno loopback, pidiendo solo `user:profile`, en
  lugar del WebView con User-Agent falso y permisos amplios del original.
- Red limitada a HTTPS hacia los hosts de cada proveedor, sin redirecciones.
- Diseño nuevo (“Sobria”) con la tipografía Geist; sin logos de terceros.

Detalle completo en [`CHANGELOG.md`](CHANGELOG.md).

## Documentación

- [Instalar y usar](docs/INSTALAR.md)
- [Compilar y firmar](docs/COMPILAR.md)
- [Seguridad y privacidad](docs/SEGURIDAD.md) — incluye la limitación de la política de Anthropic
  sobre el inicio de sesión de Claude en apps de terceros.
- [Informe de pruebas](docs/INFORME-PRUEBAS.md)
- [Producto y Google Play](docs/PRODUCTO-Y-PLAY-STORE.md) — qué falta para publicarla.
- [Diseño](DESIGN.md)

`docs/ARCHITECTURE.md`, `docs/SPEC.md`, `docs/PRD.md`, `docs/QA.md` y `docs/research/` son del
proyecto original (en chino) y describen proveedores y funciones que este fork eliminó.

## Aviso

AIMeter es un proyecto independiente, sin relación con OpenAI ni Anthropic, que no cuenta con su
respaldo ni patrocinio. “ChatGPT”, “Codex”, “Claude” y “Claude Code” son marcas de sus dueños y solo
nombran los servicios cuyos límites muestra la app. Los endpoints de uso que consulta la app no
están documentados públicamente y pueden cambiar sin aviso.
