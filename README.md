# Monitoreo de Plagas - Guía de instalación y ejecución

Esta guía explica qué versiones descargar, qué configurar en Android Studio y cómo correr el proyecto en emulador o en celular físico.

Repositorio del proyecto:

```bash
git clone https://github.com/Jorge23Leon/Monitoreo-de-vidas.git
cd Monitoreo-de-vidas
```
---

# 1. Versiones necesarias

| Herramienta | Versión necesaria |
|---|---|
| Android Studio | Koala Feature Drop 2024.1.2 o superior |
| JDK | 17 |
| Gradle Wrapper | 8.7 |
| Android Gradle Plugin | 8.6.0 |
| Kotlin | 1.9.0 |
| Compose BOM | 2024.04.01 |
| compileSdk | 35 |
| targetSdk | 35 |
| minSdk | 24 |
| Java Compatibility | 17 |
| Kotlin JVM Target | 17 |

Nota: El proyecto ya trae Gradle Wrapper, por eso no es necesario descargar Gradle manualmente. Android Studio usará Gradle 8.7 automáticamente.

---

# 2. Páginas oficiales para descargar

## 2.1 Android Studio

Página oficial:

```text
https://developer.android.com/studio
```
Se puede descargar la versión actual estable de Android Studio para que no haya ningun problema.

```text
https://developer.android.com/studio/archive
```
Buscar:

```text
Android Studio Koala Feature Drop 2024.1.2
```

## 2.2 Git para Windows

Página oficial:

```text
https://git-scm.com/download/win
```

Descargar e instalar Git for Windows.

Después de instalar, verificar en CMD o Git Bash:

```bash
git --version
```

## 2.3 JDK 17

El instalar es el JDK 17 manualmente, descargar Eclipse Temurin JDK 17:

```text
https://adoptium.net/temurin/releases/?version=17
```

Seleccionar:

```text
Operating System: Windows
Architecture: x64
Package Type: JDK
Version: 17
```

Después de instalar, verificar:

```bash
java -version
```

---

# 3. Configuración necesaria en Android Studio

Abrir Android Studio y entrar a:

```text
File > Settings > Languages & Frameworks > Android SDK
```

## 3.1 SDK Platforms

En la pestaña SDK Platforms instalar:

```text
Android API 35
```

Opcionalmente también se puede instalar:

```text
Android API 34
```

## 3.2 SDK Tools

En la pestaña SDK Tools instalar o actualizar:

```text
Android SDK Build-Tools
Android SDK Platform-Tools
Android SDK Command-line Tools latest
Android Emulator
Google USB Driver
```

---

# 4. Configurar Gradle JDK

En Android Studio entrar a:

```text
File > Settings > Build, Execution, Deployment > Build Tools > Gradle
```

En Gradle JDK seleccionar:

```text
Embedded JDK
```

o seleccionar un JDK 17 instalado.

---

# 5. Descargar el proyecto desde GitHub

Abrir Git Bash, CMD o PowerShell y ejecutar:

```bash
git clone https://github.com/Jorge23Leon/Monitoreo-de-vidas.git
cd Monitoreo-de-vidas
```

---

# 6. Abrir el proyecto correctamente

En Android Studio:

1. Seleccionar Open.
2. Buscar la carpeta Monitoreo-de-vidas.
3. Abrir la carpeta raíz del proyecto.
4. No abrir directamente la carpeta app.
5. Esperar a que Gradle sincronice.
6. Si Android Studio pide descargar SDK, Gradle o dependencias, aceptar.

La carpeta correcta debe contener archivos como:

```text
settings.gradle.kts
build.gradle.kts
gradle.properties
gradlew
gradlew.bat
app/
```

---

# 7. Sincronizar y compilar

Después de abrir el proyecto en Android Studio, ejecutar:

```text
File > Sync Project with Gradle Files
```

Luego:

```text
Build > Clean Project
Build > Rebuild Project
```

También se puede compilar desde terminal en Windows:

```bat
gradlew.bat clean
gradlew.bat assembleDebug
```

---

# 8. Ejecutar en emulador Android

El proyecto sí puede correr en emulador.

## 8.1 Crear un emulador

En Android Studio entrar a:

```text
Tools > Device Manager
```

Seleccionar:

```text
Create Device
```

Recomendado:

```text
Pixel 6
Pixel 7
Pixel 8
```

Después elegir una imagen de sistema:

```text
Android API 35
```

Finalizar la creación del emulador.

## 8.2 Iniciar el emulador

Entrar a:

```text
Tools > Device Manager
```

Presionar el botón de iniciar en el emulador creado.

Esperar a que el emulador cargue completamente.

## 8.3 Correr la app en el emulador

En Android Studio:

1. Seleccionar el emulador como dispositivo.
2. Presionar Run.
3. Esperar a que la app se instale y abra.
4. Aceptar los permisos que solicite la app.

## 8.4 Configurar ubicación en el emulador

Como la app usa GPS, se puede simular una ubicación en el emulador.

Abrir el menú del emulador:

```text
Extended Controls > Location
```

Colocar una latitud y longitud.

Ejemplo:

```text
Latitude: 21.000000
Longitude: -101.000000
```

Presionar:

```text
Set Location
```

Esto sirve para probar el mapa, la ubicación del usuario y los rangos de los puntos de monitoreo.

## Nota sobre emulador

El emulador sirve para probar que la app compile, abra, navegue entre pantallas y muestre el mapa.

Para pruebas reales de GPS, rangos de monitoreo y trabajo en campo, se recomienda usar celular físico.

---

# 9. Ejecutar en celular físico Android

El proyecto también puede correr en un celular físico. Esta es la forma más recomendada para probar la app.

## 9.1 Activar opciones de desarrollador

En el celular entrar a:

```text
Configuración > Acerca del teléfono
```

Buscar:

```text
Número de compilación
```

Tocar varias veces hasta que aparezca el mensaje de que las opciones de desarrollador fueron activadas.

## 9.2 Activar depuración USB

Entrar a:

```text
Configuración > Sistema > Opciones de desarrollador
```

Activar:

```text
Depuración USB
```

## 9.3 Conectar el celular

1. Conectar el celular a la computadora por cable USB.
2. En el celular aceptar el permiso de depuración USB.
3. En Android Studio seleccionar el celular como dispositivo.
4. Presionar Run.

## 9.4 Permisos necesarios en el celular

Cuando la app lo solicite, aceptar:

```text
Ubicación precisa
Ubicación aproximada
Cámara si se solicita
```

También se recomienda tener activado:

```text
GPS
Internet o datos móviles
```

## Nota sobre celular físico

El celular físico es la mejor opción para probar:

- Ubicación GPS real.
- Rangos de puntos de monitoreo.
- Mapa en campo.
- Descarga de reportes CSV.
- Permisos reales del dispositivo.

---

# 10. Permisos usados por la app

La app utiliza estos permisos:

```text
INTERNET
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
ACCESS_NETWORK_STATE
CAMERA
```

La cámara está configurada como opcional, por lo que el dispositivo no necesita tener cámara obligatoriamente para instalar la app.

---

# 11. Errores comunes y solución rápida

## Error: SDK location not found

Solución:

1. Cerrar Android Studio.
2. Volver a abrir el proyecto.
3. Android Studio debe generar local.properties automáticamente.

También revisar:

```text
File > Settings > Languages & Frameworks > Android SDK
```

## Error de Gradle JDK

Solución:

```text
File > Settings > Build, Execution, Deployment > Build Tools > Gradle
```

Seleccionar:

```text
Embedded JDK
```

o:

```text
JDK 17
```

## Error por SDK faltante

Solución:

Instalar Android API 35 desde:

```text
File > Settings > Languages & Frameworks > Android SDK
```

## Error al sincronizar Gradle

Solución:

```text
File > Invalidate Caches
Invalidate and Restart
```

Después:

```text
File > Sync Project with Gradle Files
```

## El mapa no carga

Revisar que exista:

```text
app/src/main/assets/leaflet/
```

con archivos como:

```text
leaflet.css
leaflet.js
```

También revisar que el dispositivo tenga:

```text
Internet activo
GPS activado
Permiso de ubicación permitido
```

---
