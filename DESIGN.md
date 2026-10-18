---
version: alpha
name: AIMeter Sobria
description: Calm, flat Android dashboard for AI subscription limits, built for several accounts at once.
colors:
  primary: "#14171C"
  secondary: "#5B6472"
  tertiary: "#686F79"
  neutral: "#F4F5F7"
  neutralAlt: "#ECEEF1"
  surface: "#FFFFFF"
  surfaceSoft: "#F7F8FA"
  border: "#E2E5EA"
  borderStrong: "#C9CED6"
  track: "#E3E7ED"
  accent: "#2F6BEF"
  accentSoft: "#EAF0FE"
  success: "#187D4F"
  successSoft: "#E6F5EE"
  warning: "#966319"
  warningSoft: "#FBF1E1"
  danger: "#BE3C29"
  dangerSoft: "#FCEBE8"
  darkPrimary: "#EDEFF3"
  darkSecondary: "#A3ABB7"
  darkTertiary: "#7C838F"
  darkNeutral: "#0E1013"
  darkSurface: "#171A1F"
  darkBorder: "#23272E"
  darkTrack: "#2A3039"
  darkAccent: "#6E9BFF"
  darkWarning: "#F2B24C"
  darkDanger: "#FF7A66"
typography:
  display:
    fontFamily: Geist
    fontSize: 28px
    fontWeight: 600
    lineHeight: 34px
    letterSpacing: "0em"
  title:
    fontFamily: Geist
    fontSize: 17px
    fontWeight: 600
    lineHeight: 22px
    letterSpacing: "0em"
  body:
    fontFamily: Geist
    fontSize: 14px
    fontWeight: 400
    lineHeight: 20px
    letterSpacing: "0em"
  label:
    fontFamily: Geist
    fontSize: 13px
    fontWeight: 500
    lineHeight: 18px
    letterSpacing: "0em"
  number:
    fontFamily: Geist
    fontSize: 34px
    fontWeight: 600
    lineHeight: 38px
    letterSpacing: "0em"
rounded:
  xs: 6px
  sm: 12px
  md: 16px
  lg: 22px
  xl: 28px
  pill: 999px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 20px
  xxl: 24px
components:
  app-background:
    backgroundColor: "{colors.neutral}"
    textColor: "{colors.primary}"
  card:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    rounded: "{rounded.lg}"
    padding: 18px
  card-border:
    backgroundColor: "{colors.border}"
    textColor: "{colors.primary}"
    height: 1px
  provider-chip:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.secondary}"
    typography: "{typography.label}"
    rounded: "{rounded.xs}"
  usage-bar-track:
    backgroundColor: "{colors.track}"
    textColor: "{colors.primary}"
    rounded: "{rounded.pill}"
    height: 8px
  usage-bar-normal:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: 8px
  usage-bar-low:
    backgroundColor: "{colors.warning}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: 8px
  usage-bar-depleted:
    backgroundColor: "{colors.danger}"
    textColor: "{colors.surface}"
    rounded: "{rounded.pill}"
    height: 8px
  figure:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.number}"
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.surface}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: 48px
  button-outlined:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: 44px
  button-danger:
    backgroundColor: "{colors.dangerSoft}"
    textColor: "{colors.danger}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: 44px
  add-button-dashed:
    backgroundColor: "{colors.neutral}"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
    rounded: "{rounded.md}"
    height: 52px
  tab-bar:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.secondary}"
    typography: "{typography.label}"
    height: 64px
  tab-active:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    typography: "{typography.label}"
  badge-warning:
    backgroundColor: "{colors.warningSoft}"
    textColor: "{colors.warning}"
    rounded: "{rounded.pill}"
  badge-danger:
    backgroundColor: "{colors.dangerSoft}"
    textColor: "{colors.danger}"
    rounded: "{rounded.pill}"
  widget-surface:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.primary}"
    padding: 14px
  app-background-alt:
    backgroundColor: "{colors.neutralAlt}"
    textColor: "{colors.primary}"
  inset-panel:
    backgroundColor: "{colors.surfaceSoft}"
    textColor: "{colors.secondary}"
    rounded: "{rounded.sm}"
  chip-outline:
    backgroundColor: "{colors.borderStrong}"
    textColor: "{colors.primary}"
    height: 1px
  metadata:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.tertiary}"
    typography: "{typography.label}"
  figure-caution:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.warning}"
    typography: "{typography.number}"
  figure-depleted:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.danger}"
    typography: "{typography.number}"
  selected-option:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.accent}"
    typography: "{typography.label}"
  selected-tint:
    backgroundColor: "{colors.accentSoft}"
    textColor: "{colors.primary}"
    rounded: "{rounded.md}"
  badge-success:
    backgroundColor: "{colors.successSoft}"
    textColor: "{colors.success}"
    rounded: "{rounded.pill}"
  dark-app-background:
    backgroundColor: "{colors.darkNeutral}"
    textColor: "{colors.darkPrimary}"
  dark-card:
    backgroundColor: "{colors.darkSurface}"
    textColor: "{colors.darkPrimary}"
    rounded: "{rounded.lg}"
    padding: 18px
  dark-card-border:
    backgroundColor: "{colors.darkBorder}"
    textColor: "{colors.darkPrimary}"
    height: 1px
  dark-label:
    backgroundColor: "{colors.darkSurface}"
    textColor: "{colors.darkSecondary}"
    typography: "{typography.label}"
  dark-metadata:
    backgroundColor: "{colors.darkSurface}"
    textColor: "{colors.darkTertiary}"
    typography: "{typography.label}"
  dark-usage-bar-track:
    backgroundColor: "{colors.darkTrack}"
    textColor: "{colors.darkPrimary}"
    rounded: "{rounded.pill}"
    height: 8px
  dark-selected-option:
    backgroundColor: "{colors.darkSurface}"
    textColor: "{colors.darkAccent}"
    typography: "{typography.label}"
  dark-figure-caution:
    backgroundColor: "{colors.darkSurface}"
    textColor: "{colors.darkWarning}"
    typography: "{typography.number}"
  dark-figure-depleted:
    backgroundColor: "{colors.darkSurface}"
    textColor: "{colors.darkDanger}"
    typography: "{typography.number}"
---

# AIMeter Design

## Overview

AIMeter muestra cuánto queda de los límites de uso de varias cuentas de Codex y Claude. La dirección
visual se llama **Sobria**: superficies planas, una paleta neutra con un solo azul de acento y
colores de estado reservados para lo que de verdad pide atención. Nada de vidrio, brillos, sombras
decorativas ni logos de terceros.

Principios:

- **Todas las cuentas son iguales.** No hay “cuenta actual”: Inicio, la notificación y el widget de
  cuentas muestran todas las cuentas visibles en el orden que eligió el usuario.
- **La cifra que manda.** Cada cuenta tiene un número: lo que queda de la ventana con menos
  disponible. El resto de las ventanas acompaña.
- **Una sola clasificación.** Normal, Atención, Escaso y Agotado salen de los mismos umbrales
  (Ajustes → Alertas) y usan las mismas palabras y colores en la app, la notificación y los widgets.
- **Nunca un cero falso.** Sin dato se muestra “--” o “Todavía sin datos”, nunca 0 %. Los datos
  viejos o con falla se dicen y se atenúan.
- **Lo disponible, no lo consumido.** Todos los porcentajes son lo que queda, y el texto lo dice
  (“disponible”, “left”).

Este documento rige la interfaz. Los tokens del encabezado son los del código (`ui/theme`); si
cambian, se cambian en los dos lugares.

## Colors

### Paleta

| Token | Claro | Oscuro | Uso |
| --- | --- | --- | --- |
| `primary` | `#14171C` | `#EDEFF3` | Texto principal, cifras |
| `secondary` | `#5B6472` | `#A3ABB7` | Texto secundario, rótulos |
| `tertiary` | `#686F79` | `#7C838F` | Metadatos, cifras sin dato o atenuadas |
| `neutral` | `#F4F5F7` | `#0E1013` | Fondo de la app |
| `surface` | `#FFFFFF` | `#171A1F` | Tarjetas, barra de pestañas, widgets |
| `border` | `#E2E5EA` | `#23272E` | Borde fino de tarjetas, separadores |
| `borderStrong` | `#C9CED6` | `#3A404A` | Contorno de chips y del botón de agregar |
| `track` | `#E3E7ED` | `#2A3039` | Pista de las barras |
| `accent` | `#2F6BEF` | `#6E9BFF` | Única interacción, barras en nivel normal |
| `warning` | `#966319` | `#F2B24C` | Atención y Escaso |
| `danger` | `#BE3C29` | `#FF7A66` | Agotado, sesión vencida, desconectar |
| `success` | `#187D4F` | `#4CC38A` | Confirmaciones puntuales |

Cada color de estado tiene su versión suave (`accentSoft`, `warningSoft`, `dangerSoft`,
`successSoft`) para fondos de insignias.

### Niveles

| Nivel | Cuándo | Cifra | Barra | Palabra |
| --- | --- | --- | --- | --- |
| Normal | Más que el umbral de atención | `primary` | `accent` | Normal |
| Atención | Hasta el umbral de atención (30 % por defecto) | `primary` | `warning` | Atención |
| Escaso | Hasta el umbral de aviso (10 % por defecto) | `warning` | `warning` | Escaso |
| Agotado | 0 % o el proveedor lo marca agotado | `danger` | `danger` | Agotado |
| Sin dato | No hay cifra, o la cifra no es actual | `tertiary` | pista sola o `tertiary` | Todavía sin datos |

## Typography

Una sola familia: **Geist** (variable, SIL Open Font License; el texto completo está en
`licenses/Geist-OFL.txt` y en Ajustes → Acerca de → Licencias). Las cifras usan dígitos tabulares
(`tnum`) para que no bailen al actualizarse.

| Estilo | Tamaño / interlínea | Peso | Uso |
| --- | --- | --- | --- |
| `display` | 28 / 34 | 600 | Título de cada pestaña |
| `title` | 17 / 22 | 600 | Nombre de la cuenta en su tarjeta |
| `body` | 14 / 20 | 400 | Texto corriente |
| `label` | 13 / 18 | 500 | Rótulos, botones, detalles |
| `number` | 34 / 38 | 600 | Cifra principal de una ventana |

Los widgets usan la fuente del sistema (Glance no carga fuentes propias) con los mismos pesos y
jerarquía.

## Layout

- Tres pestañas abajo: **Inicio**, **Cuentas**, **Ajustes**, con íconos vectoriales y el rótulo
  debajo. La barra es plana, en `surface`, con un borde superior fino; la pestaña activa lleva el
  rótulo en `primary` y peso 600.
- Márgenes laterales de 20 dp y separación entre tarjetas de 16 dp (`spacing.lg`).
- El contenido desplazable termina 24 dp por encima de la barra de pestañas: nada queda tapado.
- Encabezado de cada pestaña: título `display`, una línea de contexto en `secondary` y, en Inicio,
  el botón ↻ circular de 44 dp a la derecha.

## Elevation & Depth

Plano. Las tarjetas se separan del fondo por color (`surface` sobre `neutral`) y un borde de 1 dp
(`border`); no llevan sombra. Solo los diálogos del sistema y el diálogo de configuración de widgets
tienen elevación. El indicador de deslizar para actualizar es un disco `surface` con borde fino.

## Shapes

- Tarjetas: 22 dp (`rounded.lg`). Botones: 16 dp (`rounded.md`). Chips de proveedor: 6 dp
  (`rounded.xs`). Barras e insignias: píldora.
- Widgets: el radio del launcher (`system_app_widget_background_radius`).

## Components

### Tarjeta (`AppCard`)

Superficie con borde fino y 18 dp de relleno. Todas las tarjetas de la app son esta.

### Chip de proveedor (`ProviderChip`)

El nombre del proveedor en mayúsculas (“CODEX”, “CLAUDE”), 11 sp, peso 600, contorno
`borderStrong`. Es texto: la app no usa logos de otras empresas.

### Barra de uso (`UsageBar`)

Pista `track` y relleno con el color del nivel; la longitud es lo disponible. 8 dp en la app, 4 dp
en los widgets.

### Botón de agregar (`DashedAddButton`)

Contorno punteado `borderStrong`, ícono + texto en `primary`, 52 dp. Va al final de las listas para
no competir con las tarjetas.

### Insignias de estado

Solo para lo que pide una acción: sesión vencida (`danger`) o cuenta deshabilitada (`warning`).

## Screens

### Inicio

- Una tarjeta por cuenta visible, en el orden elegido: chip del proveedor, nombre, plan.
- Una ventana: rótulo a la izquierda, cifra `number` y “disponible” a la derecha, barra debajo.
  Varias ventanas: grilla de dos columnas con cifra más chica.
- Debajo de cada barra, una línea de detalle: “Reinicio <cuándo>”, “Escaso · reinicio <cuándo>” o
  “Agotado · vuelve <cuándo>”.
- Pie de la tarjeta solo si hace falta: datos viejos (“Actualizado <hora>”), vencidos, falla al
  actualizar (con el motivo) o sesión vencida con el botón **Volver a conectar**.
- Sin cuentas: tarjeta con explicación y **Agregar cuenta**. Todas ocultas: aviso con enlace a
  Cuentas.

### Cuentas

- Tarjetas plegadas con chip, nombre, plan y hora de la última actualización.
- Desplegadas: **Mostrar en Inicio**, detalle de ventanas, alertas de límite y acciones
  (**Subir**, **Bajar**, **Renombrar**, **Volver a iniciar sesión**, **Desconectar** en `danger` a
  todo el ancho).

### Ajustes

Secciones: Apariencia, Notificación persistente, Alertas, Actualización, Datos, Diagnóstico, Acerca
de. Nada de gestión de cuentas acá: eso vive en Cuentas.

### Widget “Cuentas”

- Encabezado: “AIMeter”, “+N” si hay cuentas que no entran, hora de los datos más nuevos y ↻.
- Angosto: una fila por cuenta con chip, nombre, rótulo de la ventana que limita, su cifra y su
  barra. Ancho (≥ 250 dp): nombre y proveedor, y una celda por ventana (rótulo, cifra, barra); la
  ventana que limita nunca queda afuera.
- Filas que no están al día: la barra se reemplaza por el motivo (“Sin conexión · 09:12”, “Sesión
  vencida”) y las cifras van en `tertiary`.
- Hasta 8 filas; los contenedores de Glance admiten 10 hijos como máximo.

### Widget “Cuenta”

Una cuenta, de 3×1 (una cifra) a 4×2 (cuatro ventanas con barra y reinicio). Línea de estado con ↻,
la palabra del nivel o el motivo de la falla, y la hora de los datos.

### Notificación persistente

Título con la cifra que manda en cada cuenta (“Disponible: Codex 0% · Claude 67%”), cuerpo con una
línea por cuenta y cada ventana, y las mismas advertencias que la app. Ícono: el medidor.

## Do's and Don'ts

### Do

- Decir siempre si un número es lo disponible.
- Usar las palabras de nivel compartidas (Normal, Atención, Escaso, Agotado) en todos lados.
- Marcar los datos viejos o con falla, con su hora.
- Mantener el azul solo para la interacción y el nivel normal.
- Probar cada pantalla en claro y oscuro.

### Don't

- No mostrar 0 % cuando no hay dato.
- No usar logos, colores de marca ni nombres que sugieran afiliación con OpenAI o Anthropic.
- No volver a una “cuenta actual” o “principal”.
- No agregar vidrio, degradados, brillos ni sombras decorativas.
- No usar fuentes monoespaciadas para texto corriente.

## Accessibility & i18n

- Toques de 44 dp como mínimo; las filas con interruptor se tocan enteras.
- El color nunca es la única señal: cada nivel tiene su palabra y cada falla su texto.
- Textos en recursos, en español (`values-es`), inglés y chino. El español usa voseo rioplatense.
- Las horas siguen el formato de 12 o 24 horas del teléfono.

## Implementation Notes

- Compose: `ui/theme/CodexMeterColors.kt` (paleta clara y oscura), `CodexMeterTheme.kt` (Geist y
  estilos), `CodexMeterSpacing.kt`; componentes compartidos en `ui/components/AppComponents.kt`.
- Niveles: `domain/quota/QuotaLevel.kt` (`quotaLevel`, `headlineWindow`, `mostUrgent`).
- Widgets: `widget/WidgetStyle.kt` arma los colores día/noche de Glance desde la misma paleta; los
  recursos `widget_*` de `values/colors.xml` y `values-night/colors.xml` (placeholder y vistas
  previas del selector) se comprueban contra la paleta en `WidgetInitialLayoutTest`.
- Ícono: `drawable/ic_launcher_foreground.xml` (medidor sobre `accent`), capa monocromática para
  íconos temáticos y la misma forma en la barra de estado. Versión para la tienda en
  `assets/brand/`.
