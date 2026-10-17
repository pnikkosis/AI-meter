# AI Usage Widget

App Android personal, gratuita y sin publicidad para ver en la pantalla de inicio el consumo de las
suscripciones de **ChatGPT/Codex** y **Claude**: cuánto queda de cada ventana de límite y cuándo se
reinicia. No mide facturación de APIs.

Es un fork de [CodexMeter](https://github.com/KyoMio/CodexMeter) 0.2.4 (commit `2f7bf52`, licencia
MIT; ver `LICENSE` y `NOTICE.md`). Funciona solo en el teléfono: sin computadora encendida, sin
servidor propio, sin servicios de pago.

## Qué hace

- Conecta una cuenta de Codex y una de Claude (inicio de sesión en el navegador del teléfono).
- Muestra el porcentaje **disponible** de cada ventana (5 horas, 7 días, por modelo…) y su reinicio.
- Widgets redimensionables; cada widget se asigna a una cuenta y a los campos elegidos.
- Indica la hora de los datos y distingue datos actuales, desactualizados, sin conexión, límite de
  peticiones, error del proveedor y sesión vencida. Un error nunca se muestra como 0 %.
- Actualización manual (botón del widget, deslizar en la app) y periódica con WorkManager.
- Desconectar borra los datos locales de la cuenta; en Codex además pide a OpenAI revocar el acceso.
- Interfaz en español (y en inglés o chino según el idioma del teléfono).

## Diferencias con CodexMeter

- Nombre e identificador propios (`io.github.pnikkosis.aiusagewidget`), firma propia.
- Solo Codex y Claude: se quitaron los demás proveedores, la conversión de monedas
  (open.er-api.com) y el comprobador de actualizaciones de GitHub.
- Claude inicia sesión en el navegador con PKCE y retorno loopback, pidiendo solo `user:profile`, en
  lugar del WebView con User-Agent falso y permisos amplios del original.
- Red limitada a HTTPS hacia los hosts de cada proveedor, sin redirecciones.
- Estados de error diferenciados en la app y el widget; ventanas agotadas visibles como 0 %.

Detalle completo en [`CHANGELOG.md`](CHANGELOG.md).

## Documentación

- [Instalar y usar](docs/INSTALAR.md)
- [Compilar y firmar](docs/COMPILAR.md)
- [Seguridad y privacidad](docs/SEGURIDAD.md) — incluye la limitación de la política de Anthropic
  sobre el inicio de sesión de Claude en apps de terceros.
- [Informe de pruebas](docs/INFORME-PRUEBAS.md)

`docs/ARCHITECTURE.md`, `docs/SPEC.md`, `docs/PRD.md`, `docs/QA.md` y `docs/research/` son del
proyecto original (en chino) y describen proveedores y funciones que este fork eliminó.

## Aviso

Proyecto personal no oficial, sin relación con OpenAI ni Anthropic. “ChatGPT”, “Codex”, “Claude” y
“Claude Code” son marcas de sus dueños. Los endpoints de uso que consulta la app no están documentados
públicamente y pueden cambiar sin aviso.
