# Compilar AIMeter

## Base del código

- Proyecto original: [KyoMio/CodexMeter](https://github.com/KyoMio/CodexMeter), versión 0.2.4,
  commit `2f7bf52167967aab97f7cb1f1e7d9af7ac3a9977` (licencia MIT, ver `LICENSE` y `NOTICE.md`).
- Este fork conserva toda la historia del original; `git diff 2f7bf52 HEAD` muestra cada cambio.

## Herramientas y versiones

| Herramienta | Versión |
| --- | --- |
| JDK | 21 (Temurin 21.0.12 en CI; el bytecode apunta a Java 17) |
| Gradle (wrapper) | 9.5.1 (`gradle-wrapper.jar` SHA-256 `497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7`) |
| Android Gradle Plugin | 9.2.1 |
| Kotlin / KSP | 2.3.21 / 2.3.8 |
| Android SDK | `platforms;android-37.0`, `build-tools;37.0.0` |
| minSdk / targetSdk | 31 (Android 12) / 36 |

Las versiones de las librerías están fijadas en `gradle/libs.versions.toml`.

## Opción A: compilar y firmar en una computadora con el SDK de Android

1. Instalar JDK 21 y el SDK de Android con los paquetes de la tabla; exportar `ANDROID_HOME`.
2. Guardar la clave de firma **fuera del repositorio** y crear un archivo de propiedades, por
   ejemplo `~/.android/aiusagewidget-release.properties`:

   ```properties
   storeFile=/ruta/segura/aiusagewidget-release.p12
   storePassword=...
   keyAlias=aiusagewidget
   keyPassword=...
   ```

   (Otra ruta: `export AIUW_RELEASE_PROPERTIES=/ruta/al/archivo.properties`.)
3. Ejecutar:

   ```bash
   scripts/build-release.sh            # pruebas unitarias + APK release firmado
   scripts/build-release.sh --skip-tests
   ```

   El resultado queda en `dist/AIMeter-<versión>.apk` con su `.sha256` (hasta 1.0.2 se llamaba
   `AIUsageWidget-<versión>.apk`), y el script muestra la verificación de `apksigner` y la huella
   SHA-256 del certificado.

## Opción B: compilar en GitHub Actions y firmar localmente

Sirve cuando la máquina que tiene la clave no puede descargar el SDK de Android (fue el caso al
preparar la versión 1.0.0).

1. En GitHub → Actions → **Export unsigned release (manual)** → *Run workflow* sobre la rama/commit
   deseado. El workflow compila el APK release **sin firmar**, imprime su SHA-256 y escribe en los
   registros de sus jobs el APK (en 8 partes) y el `apksigner.jar` de build-tools 37.0.0, codificados
   en base64. No usa secretos.
2. Obtener los dos archivos: desde un navegador, descargar el artefacto `export` de la ejecución
   (se conserva 3 días); si la descarga no es posible, guardar los registros de los jobs
   *Print part 0* a *Print part 8* y reconstruirlos con

   ```bash
   python3 -I scripts/extract-exported-apk.py export/ part0.log part1.log ... part8.log
   ```

   Comprobar que los SHA-256 coinciden con los impresos por el paso *Describe outputs* del job de
   compilación.
3. Firmar y verificar con la clave local:

   ```bash
   scripts/sign-exported-apk.sh app-release-unsigned.apk apksigner.jar <sha256-del-apk-sin-firmar>
   ```

   El script se niega a firmar si el SHA-256 no coincide, pasa las contraseñas a `apksigner` por
   variables de entorno y genera `dist/AIMeter-<versión>.apk` + `.sha256`.

El workflow **CI** (en cada push) ejecuta las pruebas unitarias, lint, compila el APK debug y el
release sin firmar, y publica en el registro los totales de pruebas, el resumen de lint, los
permisos del APK y sus dependencias de ejecución.

El workflow manual **Emulator check (manual)** arranca un emulador Android en el runner (API 35 en
español y API 31 en inglés), renderiza los estados de los dos widgets, instala el release (procesado
por R8 y firmado con una clave descartable creada en el job, nunca la de release) y recorre la app
con `scripts/ci/emulator_smoke.py`. Las capturas quedan en el registro del job en base64.

## Compilación reproducible

La compilación no incluye marcas de tiempo ni datos de la máquina (`BuildConfig` solo lleva versión
y tipo de build). Sí incluye el commit del que se compiló: el Android Gradle Plugin escribe
`META-INF/version-control-info.textproto` con su hash. Por eso el mismo commit compilado con las
versiones de la tabla produce el mismo APK sin firmar, y cada commit uno distinto. Para comprobarlo,
comparar el SHA-256 de `app-release-unsigned.apk` impreso por dos ejecuciones distintas del mismo
commit (por ejemplo CI y el workflow de exportación).

La firma se agrega después. Con la clave RSA de este fork la firma es determinista: firmar dos
veces el mismo APK da el mismo archivo. La firma v2/v3 no modifica las entradas del ZIP, así que un
APK firmado se puede comparar con uno compilado por cualquiera desde el mismo commit:

```bash
entradas() { unzip -v "$1" | awk '$7 ~ /^[0-9a-f]{8}$/ {print $7, $8}' | sort -k2; }
diff <(entradas app-release-unsigned.apk) <(entradas AIMeter-1.1.1.apk) && echo "mismo contenido"
unzip -p AIMeter-1.1.1.apk META-INF/version-control-info.textproto   # commit de origen
```

## Verificar un APK

```bash
sha256sum AIMeter-1.1.1.apk
apksigner verify --verbose --print-certs AIMeter-1.1.1.apk
```

La huella SHA-256 del certificado de firma de este fork es:

```
1C:F0:5E:0E:20:64:70:29:AE:42:8B:93:DD:7D:02:EB:3B:84:56:D3:4F:89:D6:BF:69:98:F0:E0:D6:FF:F3:8C
```

Toda actualización tiene que estar firmada con ese mismo certificado; Android rechaza instalar una
versión firmada con otra clave encima de la existente. Por eso el certificado sigue diciendo
`CN=AI Usage Widget, O=Personal build`, el nombre de 1.0.x: cambiarlo exigiría otra clave.

`apksigner` informa “Verified using v3 scheme: true” y v2 en `false`: el APK lleva las dos firmas,
pero con minSdk 31 solo se verifica la v3, que es la que usa Android 12 o posterior
(`--min-sdk-version 24` muestra también la v2).

## Versiones entregadas

Desde 1.1.0 el APK se llama `AIMeter-<versión>.apk`; hasta 1.0.2, `AIUsageWidget-<versión>.apk`.

| Dato | 1.1.1 (actual) | 1.1.0 | 1.0.2 | 1.0.1 | 1.0.0 |
| --- | --- | --- | --- | --- | --- |
| Commit de compilación | `f6353d1f43f6904366539ac671a4bfb1ea5b17b0` | `69d75f082fb143643feeb606e50bfd3d879439ac` | `d0c28d718a69a3f6b3955e01bb18904bc452b676` | `e5a37954844f57f8970d7be144164114ddf03e45` | `d0d3d0651573012e321f6bad15dde643d7a26b9d` |
| Ejecución de exportación | n.º 6 (run `39087777923`) | n.º 5 (run `39083934120`) | n.º 4 (run `39045893487`) | n.º 3 (run `39040667471`) | n.º 2 (run `39023263155`) |
| SHA-256 del APK sin firmar | `63b26a06442a810c7e0c132dc2f0702bd805fa19bfb7da4f3264d92b7f60b274` | `8e773777a0910c75c344a1b5031c3351af70a8353513627bb7635e1dced5234a` | `7175085e142648e88fb300f19f083b1aeae5e9461b4bc8621b6fc5c713eb54a3` | `d2dd62a49594ddf045e36b46aeb1400a912adb1438a5b90ac16fcfd6e8e80045` | `f4b3534aecf273773084d9ca7e11a58adc1fe3ff1ab11ce7424c38b2ea1ba472` |
| SHA-256 del APK firmado | `a2bdb7d8f8a13661241c1a9aff140b504feb2aef19623af6e9ff6c7b53b1eb81` | `e635a841d2b2b961a3080cabfc36a987165aa01c752bb2e8d2abdbe0d09188a2` | `d8ed32ff167039fbfcfd3917e3a1d3fb51a4938461433553893e8e613e2fe605` | `250c231b4286f5ca25297a2b14e4c1312aa01321b2d69cbcef042802908181c0` | `c17d3e26b6d7029a4b80472c0c2271479bf6caeb17d4e55cd99a830e69ca4581` |

En las cinco versiones, `apksigner.jar` de build-tools 37.0.0 tiene SHA-256
`2defad215d7ff52968a409cde528cdaef7918b115e276b8e3378ca7a178e4180`, y el mismo SHA-256 sin firmar
salió también de la CI y de las corridas del emulador del mismo commit.

## Clave de firma

- Archivo: `aiusagewidget-release.p12` (PKCS12, RSA 4096, SHA256withRSA, válida hasta 2056, alias
  `aiusagewidget`) y su archivo `aiusagewidget-release.properties` con la contraseña.
- La clave se generó para la versión 1.0.0 fuera del repositorio, en el entorno temporal donde se
  firmó el APK, y se entregó a su dueño junto con el archivo de propiedades; la 1.0.1, la 1.0.2, la
  1.1.0 y la 1.1.1 se firmaron con la misma (el nombre del archivo y el alias siguen siendo los de
  entonces). No está en GitHub: ningún workflow la recibe (el CI y la exportación solo producen APK
  sin firmar).
- Nunca se suben al repositorio (`.gitignore` excluye `*.p12`, `*.jks`, `*.keystore` y el archivo de
  propiedades) ni aparecen en registros: los scripts pasan las contraseñas a `apksigner` por
  variables de entorno.
- Guardar ambos en un gestor de contraseñas o almacenamiento cifrado, con una copia de respaldo. Si se
  pierden, no se podrá actualizar la app instalada: habría que desinstalarla (borrando sus datos) e
  instalar una nueva firmada con otra clave.
- Para una nueva versión: subir `appVersionCode` y `appVersionName` en `app/build.gradle.kts`,
  compilar con la opción A o B y firmar con la misma clave. Al copiar el archivo de propiedades a
  otra máquina, actualizar `storeFile` con la ruta nueva del `.p12`.
- Para confirmar que una clave es la correcta sin firmar nada:
  `keytool -list -v -storetype PKCS12 -keystore aiusagewidget-release.p12` (pide la contraseña sin
  mostrarla) y comparar la huella SHA-256 con la de arriba.
