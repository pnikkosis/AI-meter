# Instalar y usar AI Usage Widget

Requisitos: Android 12 o posterior. No hace falta computadora, servidor ni servicios de pago.

## 1. Instalar el APK

1. Copiar `AIUsageWidget-1.0.1.apk` al teléfono (o descargarlo desde donde lo guardes). Si ya está
   instalada la 1.0.0, la 1.0.1 se instala encima como actualización y conserva los datos.
2. Opcional, recomendado: comprobar el SHA-256 del archivo. El de la versión 1.0.1 es
   `250c231b4286f5ca25297a2b14e4c1312aa01321b2d69cbcef042802908181c0` (también en el `.sha256` que
   acompaña al APK y en `docs/COMPILAR.md`).
3. Abrirlo desde el administrador de archivos. Android pedirá permitir “Instalar apps desconocidas”
   para esa app; concederlo solo para esta instalación.
4. Play Protect puede advertir que la app no es conocida: es esperable en una compilación personal.

La app se llama **AI Usage Widget** (`io.github.pnikkosis.aiusagewidget`) y puede convivir con
CodexMeter si estuviera instalado.

## 2. Conectar Codex

La interfaz está en español cuando el teléfono está en español (en otros idiomas, en inglés o
chino). Abajo se citan los textos en español y, entre paréntesis, en inglés.

Antes de empezar, una sola vez: OpenAI exige activar el inicio de sesión con **código de
dispositivo** en la configuración de seguridad de ChatGPT. El enlace de OpenAI lleva a la versión
web (`chatgpt.com/#settings/Security`, abrirla en el navegador); la pantalla Seguridad de la app de
ChatGPT para Android no muestra esa opción. Si no está activada, OpenAI pide el código del
autenticador y después muestra “Activa el inicio de sesión con código de dispositivo para Codex…”.
Con la opción activada, no ingresar nunca un código que no se haya generado uno mismo.

1. Abrir la app → **Agregar cuenta** (*Add account*; también el botón **+** arriba a la derecha en
   la pestaña **Cuentas**) → **Codex**.
2. **Iniciar sesión en Codex**: la app muestra un código (**Copiar** lo copia).
3. **Abrir la página de verificación de Codex** e iniciar sesión en OpenAI en el navegador.
   Introducir el código y aprobar el acceso.
4. Volver a la app: valida la sesión leyendo el consumo y guarda la cuenta.

## 3. Conectar Claude

1. **Agregar cuenta** → **Claude**. Leer el aviso **Antes de continuar** (permiso pedido, cliente
   “Claude Code”, política de Anthropic, sin revocación remota). Ver `docs/SEGURIDAD.md`.
2. **Continuar en el navegador** (*Continue in browser*): se abre claude.ai en una pestaña del
   navegador. Iniciar sesión si hace falta y autorizar.
3. Cuando la página diga “Sesión iniciada en AI Usage Widget” (*Signed in to AI Usage Widget*),
   cerrar esa pestaña (la **X** arriba) para volver a la app. Recién entonces la app termina el
   intercambio (Android no le da red mientras la pestaña la tapa), valida leyendo el consumo y
   guarda la cuenta.
4. Si algo falla, la pantalla dice el motivo (cancelado, permiso rechazado, sin conexión, límite de
   peticiones, etc.) y permite **Reintentar**. No se guarda nada si falla. En particular, si aparece
   “Claude aceptó el inicio de sesión pero se negó a informar el consumo”, lo más probable es que
   Anthropic no acepte el permiso mínimo `user:profile` para leer el uso (ver la sección 7 de
   `docs/INFORME-PRUEBAS.md`).

## 4. Agregar los widgets

1. Mantener presionado el fondo de la pantalla de inicio → **Widgets** → **AI Usage Widget**.
2. Arrastrarlo. Según el launcher se abre la configuración enseguida o el widget aparece como
   “Sin configurar” (*Not configured*); en ese caso mantenerlo presionado → configurar (ícono de
   lápiz o “Ajustes”, según el launcher).
3. En **Configurar AI Usage Widget** elegir la **Cuenta** (Codex o Claude) y los **Campos visibles**
   (ventanas de límite), y **Guardar**.
4. Repetir para tener un widget por cuenta. Cada widget guarda su propia cuenta; se puede cambiar
   después de la misma forma.
5. Tamaños: 3×1 muestra 1 campo, 4×1 dos, 3×2 tres y 4×2 cuatro (redimensionar desde el launcher).

Cómo leer un widget:

- Encabezado: proveedor (y cuenta en los tamaños anchos).
- Línea de estado: estado y hora de los datos, por ejemplo `Normal · Actualizado 14:05`
  (*Normal · Updated 14:05*). Si los datos no son de hoy, incluye el día de la semana (o la fecha,
  si tienen más de una semana); la hora sigue el formato de 12 o 24 horas del teléfono.
- Cada campo: ventana y la palabra **disponible** (`5 horas · disponible`, en inglés
  `5h quota · left`), el porcentaje **disponible** (no el consumido) y el reinicio informado por el
  proveedor (solo la hora si es hoy). Una ventana agotada se ve como `0%` en rojo.
- Si el último intento falló, el estado dice por qué (`Sin conexión`, `Límite de peticiones`,
  `Error del proveedor`, `Datos ilegibles`, `Sesión vencida`) y los números se muestran atenuados
  porque son los últimos conocidos, no los actuales; `Atrasado` y `Desactualizado` indican datos
  viejos sin error. Sin datos, el widget lo dice en lugar de mostrar 0 %.
- Botón ↻ (junto al estado): pide una actualización inmediata en segundo plano.
- Tocar el resto del widget abre la app.

## 5. Actualización en segundo plano

- La app actualiza con WorkManager cada 15 minutos como mínimo (**Ajustes → Actualización →
  Intervalo de actualización** permite elegir 15 o 30 minutos o **Solo manual**).
- Con la pantalla apagada, Android agrupa el trabajo (Doze) y el intervalo real puede ser mayor.
  **Ajustes → Actualización → Mejorar la actualización en segundo plano → Permitir** pide la
  exención de optimización de batería; es opcional.
- En la app se puede actualizar a mano deslizando hacia abajo en Inicio o Cuentas.

## 6. Desconectar una cuenta

Pestaña **Cuentas** → desplegar la cuenta (**Desplegar**) → **Desconectar**. El diálogo explica qué
pasa:

- **Codex:** se borran del teléfono los tokens, el historial y los datos de widgets de la cuenta, y se
  pide a OpenAI que revoque el acceso. Si la revocación falla, los datos locales se borran igual.
- **Claude:** se borran del teléfono los tokens, el historial y los datos de widgets. Anthropic no
  ofrece revocación remota para apps; la sesión de claude.ai del navegador no se toca.

Después aparece un aviso con lo que realmente ocurrió.

## 7. Actualizar la app

Instalar encima el APK nuevo firmado con la misma clave. Los datos se conservan. Un APK firmado con
otra clave no se instala encima (habría que desinstalar primero, perdiendo las cuentas conectadas).
