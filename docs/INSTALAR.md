# Instalar y usar AIMeter

Requisitos: Android 12 o posterior. No hace falta computadora, servidor ni servicios de pago.

## 1. Instalar el APK

1. Copiar `AIMeter-1.1.0.apk` al teléfono (o descargarlo desde donde lo guardes). Si ya está
   instalada una versión anterior (1.0.x, que se llamaba “AI Usage Widget”), la 1.1.0 se instala
   encima como actualización y conserva cuentas, sesiones e historial.
2. Opcional, recomendado: comprobar el SHA-256 del archivo con el que figura en el `.sha256` que
   acompaña al APK y en `docs/COMPILAR.md`.
3. Abrirlo desde el administrador de archivos. Android pedirá permitir “Instalar apps desconocidas”
   para esa app; concederlo solo para esta instalación.
4. Play Protect puede advertir que la app no es conocida: es esperable en una compilación propia.

La app se llama **AIMeter**. Su identificador sigue siendo `io.github.pnikkosis.aiusagewidget` (el
de 1.0.x) para que se actualice encima en lugar de instalarse como otra app. Puede convivir con
CodexMeter si estuviera instalado.

## 2. Conectar cuentas

Se pueden conectar todas las cuentas que se quiera, de Codex y de Claude; ninguna es “la principal”.
La interfaz está en español cuando el teléfono está en español (en otros idiomas, en inglés o
chino). Abajo se citan los textos en español y, entre paréntesis, en inglés.

**Agregar cuenta** (*Add account*) está en **Inicio** y al final de la pestaña **Cuentas**.

### Codex

1. **Agregar cuenta** → **Codex**. Leer el aviso **Antes de continuar** (cliente de Codex CLI,
   permisos pedidos, dónde quedan los tokens). Ver `docs/SEGURIDAD.md`.
2. **Continuar en el navegador** (*Continue in browser*): se abre la página de OpenAI
   (`auth.openai.com`) en una pestaña del navegador. Iniciar sesión con la cuenta de ChatGPT y
   aprobar el acceso.
3. Cuando la página diga “Sesión iniciada en AIMeter” (*Signed in to AIMeter*), cerrar esa pestaña
   (la **X** arriba) para volver a la app. Recién entonces la app termina el intercambio, valida
   leyendo el consumo de Codex y guarda la cuenta.
4. Si algo falla, la pantalla dice el motivo y permite **Reintentar**. No se guarda nada si falla.

No hace falta activar nada en ChatGPT para este método. La alternativa **Usar un código de
dispositivo** (*Use a device code instead*), debajo del botón, solo funciona si antes se activó el
inicio de sesión con código de dispositivo en la configuración de seguridad de ChatGPT (versión web,
`chatgpt.com/#settings/Security`). No ingresar nunca un código que no se haya generado uno mismo.

### Claude

1. **Agregar cuenta** → **Claude**. Leer el aviso **Antes de continuar** (permiso pedido, cliente
   “Claude Code”, política de Anthropic, sin revocación remota). Ver `docs/SEGURIDAD.md`.
2. **Continuar en el navegador** (*Continue in browser*): se abre claude.ai en una pestaña del
   navegador. Iniciar sesión si hace falta y autorizar.
3. Cuando la página diga “Sesión iniciada en AIMeter”, cerrar esa pestaña para volver a la app.
   Recién entonces la app termina el intercambio (Android no le da red mientras la pestaña la tapa),
   valida leyendo el consumo y guarda la cuenta.
4. Si algo falla, la pantalla dice el motivo (cancelado, permiso rechazado, sin conexión, límite de
   peticiones, etc.) y permite **Reintentar**. Si aparece “Claude aceptó el inicio de sesión pero se
   negó a informar el consumo”, lo más probable es que Anthropic haya dejado de aceptar el permiso
   mínimo `user:profile` para leer el uso (ver `docs/INFORME-PRUEBAS.md`).

## 3. Inicio

**Inicio** (*Home*) muestra todas las cuentas visibles, en el orden elegido. Cada tarjeta tiene el
proveedor, el nombre de la cuenta y su plan, y cada ventana de límite (5 horas, 7 días, por
modelo…) con lo que queda **disponible** (no lo consumido) en número y barra, y cuándo se reinicia.

- Los colores son los mismos en toda la app, la notificación y los widgets: azul normal, ámbar con
  el umbral de atención o “Escaso”, rojo “Agotado” (0 %). Los umbrales se cambian en **Ajustes →
  Alertas**.
- Si los datos no son actuales, la tarjeta lo dice: “Actualizado <hora>” (posiblemente viejos),
  “… · puede haber cambiado” (vencidos), el motivo de una falla, o que la sesión venció con el botón
  **Volver a conectar**. Una cuenta sin datos dice “Todavía no hay datos”; nunca se muestra 0 %
  por un error.
- El botón ↻ arriba a la derecha (o deslizar hacia abajo) actualiza todas las cuentas.

## 4. Cuentas

En **Cuentas** (*Accounts*) cada cuenta se despliega con su detalle y sus acciones:

- **Mostrar en Inicio** (*Show on Home*): ocultarla la saca de Inicio, de la notificación y del
  widget de cuentas que siga las cuentas visibles; sigue conectada y actualizándose.
- **Subir** / **Bajar**: el orden se usa en Inicio, la notificación y los widgets.
- **Renombrar**, **Volver a iniciar sesión** y **Desconectar** (ver la sección 8).
- **Alertas de límite**: qué ventanas de esa cuenta avisan al cruzar los umbrales.

## 5. Widgets

Hay dos widgets. Se agregan manteniendo presionado el fondo de la pantalla de inicio → **Widgets**
→ **AIMeter**, y se arrastran.

### Cuentas (varias cuentas en uno)

- Al colocarlo muestra todas las cuentas visibles en Inicio, sin configurar nada. Para elegir otras
  (incluidas cuentas ocultas en Inicio): mantenerlo presionado → configurar (ícono de lápiz o
  “Ajustes”, según el launcher) → **Elegir cuentas**.
- Angosto (3 columnas): una fila por cuenta con la cifra que la limita (la ventana con menos
  disponible), el nombre de esa ventana y su barra.
- Ancho (4 o más columnas): cada ventana con su cifra y su barra; si no entran todas, la que limita
  a la cuenta nunca queda afuera.
- Muestra tantas filas como entren en el alto (hasta 8); si faltan, el encabezado dice “+N”. El
  encabezado también tiene la hora de los datos más nuevos y el botón ↻.
- Una cuenta con datos viejos, con una falla o con la sesión vencida lo dice en su fila, y sus
  cifras se ven atenuadas.

### Cuenta (una cuenta en detalle)

1. Al soltarlo se abre **Widget de una cuenta**: elegir la **Cuenta** y los **Campos visibles**
   (ventanas de límite) y **Guardar**.
2. Tamaños: 3×1 muestra 1 campo, 4×1 dos, 3×2 tres y 4×2 cuatro (con su reinicio).
3. Línea de estado: estado y hora de los datos, por ejemplo `Normal · Actualizado 14:05`. Los estados
   con datos al día usan las mismas palabras que Inicio (`Normal`, `Atención`, `Escaso`,
   `Agotado`); con una falla dicen por qué (`Sin conexión`, `Límite de peticiones`, `Error del
   proveedor`, `Datos ilegibles`, `Sesión vencida`) y las cifras se atenúan.

En los dos widgets, ↻ pide una actualización inmediata en segundo plano y tocar el resto abre la
app. Los colores siguen el modo claro u oscuro del teléfono.

## 6. Notificación persistente

**Ajustes → Notificación persistente → Notificación de estado persistente** muestra en el panel de
notificaciones todas las cuentas visibles (o una sola, en **Cuentas en la notificación**):

- Título: lo disponible de la ventana que limita a cada cuenta, por ejemplo
  `Disponible: Codex 0% · Claude 67%` (con el nombre de la cuenta cuando hay dos del mismo
  proveedor).
- Cuerpo (desplegando la notificación): una línea por cuenta con cada ventana, por ejemplo
  `Codex · Personal: 7 días agotado` y `Claude · Trabajo: 5 horas 67%, 7 días 87%`, con
  “puede estar desactualizado”, “no se pudo actualizar” o “hay que volver a conectar la cuenta”
  cuando corresponde.

En Android 13 o posterior hace falta el permiso de notificaciones: si Android las bloquea, el
interruptor se ve apagado y Ajustes lo avisa; al encenderlo, la app explica para qué las usa y
Android pregunta (**Permitir**). Si Android ya no muestra la pregunta, se abren los ajustes de
notificaciones de la app en el sistema.

## 7. Actualización en segundo plano

- La app actualiza con WorkManager cada 15 minutos como mínimo (**Ajustes → Actualización →
  Intervalo de actualización**: 15 o 30 minutos o **Solo manual**). **Último resultado** resume
  todas las cuentas: “Correcta” solo si la última actualización de cada una salió bien.
- Con la pantalla apagada, Android agrupa el trabajo (Doze) y el intervalo real puede ser mayor.
  **Mejorar la actualización en segundo plano → Abrir ajustes** abre la lista de apps con
  optimización de batería del sistema: ahí se puede elegir AIMeter y quitarle la optimización. Es
  opcional; la app ya no pide ese permiso por su cuenta.

## 8. Desconectar una cuenta

Pestaña **Cuentas** → desplegar la cuenta → **Desconectar**. El diálogo explica qué pasa:

- **Codex:** se borran del teléfono los tokens, el historial y los datos de widgets de la cuenta, y se
  pide a OpenAI que revoque el acceso. Si la revocación falla, los datos locales se borran igual.
- **Claude:** se borran del teléfono los tokens, el historial y los datos de widgets. Anthropic no
  ofrece revocación remota para apps; la sesión de claude.ai del navegador no se toca.

Después aparece un aviso con lo que realmente ocurrió. La notificación y los widgets dejan de
mostrar la cuenta enseguida.

## 9. Actualizar la app

Instalar encima el APK nuevo firmado con la misma clave. Los datos se conservan. Un APK firmado con
otra clave no se instala encima (habría que desinstalar primero, perdiendo las cuentas conectadas).

Al pasar de 1.0.x a 1.1.0:

- Si la notificación seguía “la cuenta actual”, ahora muestra todas las cuentas visibles; si tenía
  una cuenta elegida, la conserva.
- Un widget ya colocado conserva su cuenta y sus campos, y toma el diseño nuevo. Hasta la primera
  actualización (abrir la app o esperar la periódica) puede verse con las cifras atenuadas.
