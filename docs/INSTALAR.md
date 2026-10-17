# Instalar y usar AI Usage Widget

Requisitos: Android 12 o posterior. No hace falta computadora, servidor ni servicios de pago.

## 1. Instalar el APK

1. Copiar `AIUsageWidget-1.0.0.apk` al teléfono (o descargarlo desde donde lo guardes).
2. Opcional, recomendado: comprobar el SHA-256 del archivo con el publicado junto al APK.
3. Abrirlo desde el administrador de archivos. Android pedirá permitir “Instalar apps desconocidas”
   para esa app; concederlo solo para esta instalación.
4. Play Protect puede advertir que la app no es conocida: es esperable en una compilación personal.

La app se llama **AI Usage Widget** (`io.github.pnikkosis.aiusagewidget`) y puede convivir con
CodexMeter si estuviera instalado.

## 2. Conectar Codex

La interfaz de la app está en inglés (o en chino si el teléfono está en chino); abajo se citan los
textos tal como aparecen.

1. Abrir la app → **Add account** (o el botón **+** de la barra inferior) → **Codex**.
2. Tocar el botón para empezar: la app muestra un código.
3. Abrir la página de verificación (botón de la app) e iniciar sesión en OpenAI en el navegador.
   Introducir el código y aprobar el acceso.
4. Volver a la app: valida la sesión leyendo el consumo y guarda la cuenta.

## 3. Conectar Claude

1. **Add account** → **Claude**. Leer el aviso (permiso pedido, cliente “Claude Code”, política de Anthropic,
   sin revocación remota). Ver `docs/SEGURIDAD.md`.
2. **Continuar en el navegador**: se abre claude.ai en una pestaña del navegador. Iniciar sesión si
   hace falta y autorizar.
3. Cuando la página diga “Signed in to AI Usage Widget”, cerrar esa pestaña (la **X** arriba) para
   volver a la app. La app termina el intercambio, valida leyendo el consumo y guarda la cuenta.
4. Si algo falla, la pantalla dice el motivo (cancelado, permiso rechazado, sin conexión, límite de
   peticiones, etc.) y permite reintentar. No se guarda nada si falla.

## 4. Agregar los widgets

1. Mantener presionado el fondo de la pantalla de inicio → **Widgets** → **AI Usage Widget**.
2. Arrastrarlo. Según el launcher se abre la configuración enseguida o el widget aparece como
   “Not configured”; en ese caso mantenerlo presionado → configurar (ícono de lápiz o “Settings”).
3. En la configuración elegir la **cuenta** (Codex o Claude) y los **campos** (ventanas de límite) a
   mostrar, y guardar.
4. Repetir para tener un widget por cuenta. Cada widget guarda su propia cuenta; se puede cambiar
   después de la misma forma.
5. Tamaños: 3×1 muestra 1 campo, 4×1 dos, 3×2 tres y 4×2 cuatro (redimensionar desde el launcher).

Cómo leer un widget:

- Encabezado: proveedor (y cuenta en los tamaños anchos).
- Línea de estado: estado y hora de los datos, por ejemplo `Normal · Updated 14:05`.
- Cada campo: ventana y la palabra **left** (`5h quota · left`), el porcentaje **disponible** y la hora
  de reinicio informada por el proveedor.
- Si el último intento falló, el estado dice por qué (`No connection`, `Rate limited`,
  `Provider error`, `Unreadable data`, `Sign in again`) y los números se muestran atenuados porque
  son los últimos conocidos, no los actuales. Sin datos, el widget lo dice en lugar de mostrar 0 %.
- Botón ↻: pide una actualización inmediata en segundo plano.
- Tocar el resto del widget abre la app.

## 5. Actualización en segundo plano

- La app actualiza con WorkManager cada 15 minutos como mínimo (**Settings → Refresh → Refresh
  interval** permite cambiar el intervalo o dejarlo manual).
- Con la pantalla apagada, Android agrupa el trabajo (Doze) y el intervalo real puede ser mayor.
  **Settings → Refresh → Improve background reliability → Allow** pide la exención de optimización
  de batería; es opcional.
- En la app se puede actualizar a mano deslizando hacia abajo en Inicio o Cuenta.

## 6. Desconectar una cuenta

Pestaña de cuentas → desplegar la cuenta (**Expand**) → **Disconnect**. El diálogo explica qué pasa:

- **Codex:** se borran del teléfono los tokens, el historial y los datos de widgets de la cuenta, y se
  pide a OpenAI que revoque el acceso. Si OpenAI no responde, los datos locales se borran igual.
- **Claude:** se borran del teléfono los tokens, el historial y los datos de widgets. Anthropic no
  ofrece revocación remota para apps; la sesión de claude.ai del navegador no se toca.

Después aparece un aviso con lo que realmente ocurrió.

## 7. Actualizar la app

Instalar encima el APK nuevo firmado con la misma clave. Los datos se conservan. Un APK firmado con
otra clave no se instala encima (habría que desinstalar primero, perdiendo las cuentas conectadas).
