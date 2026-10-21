# Plan de optimización — Flutter Widget Wrapper

Revisión completa del plugin (2026-10-20). Plugin Verifier 1.410 offline contra
IU-262.10968.63: sin usos de API interna, experimental ni deprecated. Las clases de Dart
usadas no llevan `@ApiStatus` ni `@Deprecated`.

## Reglas para cada arreglo

- Solo API pública de la plataforma y del plugin Dart; nada de `impl` interno nuevo.
- Sin pérdida de funcionalidad: el comportamiento observable no cambia salvo donde el
  arreglo corrige un bug (A1, A2, C1).
- Un arreglo por paso, con su test, sin tocar los cambios pendientes ajenos al arreglo.
- Verificación de cada paso, sin descargar IDEs:
  `./gradlew --offline check "-PlocalIdePath=$HOME/Applications/IntelliJ IDEA.app"`
- A1 y A2 llevan entrada `### Fixed` en `CHANGELOG.md` (Unreleased).

## A. Bugs

### A1. Ctrl+Z tras un wrap con tab-stops deja el código roto — [x] hecho

- **Problema:** el wrap con live template ocupa 3 pasos de undo; tras el segundo queda
  `void f() { ; }`, sin el widget. Reproducido con un test temporal; el wrap directo
  ocupa 1 paso.
- **Dónde:** `LiveTemplateWrapEngine.startWrap` (`LiveTemplateWrapEngine.kt:53-63`).
- **Causa:** el borrado va en un `WriteCommandAction` y `startTemplate` abre otro comando.
- **Arreglo:** un solo `CommandProcessor.executeCommand(project, { … }, nombre, null)`
  con `WriteAction.run { …ediciones… }` y después
  `TemplateManager.startTemplate(editor, template, false, null, null)`
  (`inSeparateCommand = false`). Nombre del comando: `intention.text.wrap`.
  Ya probado: deja de haber estado intermedio roto.
- **Verificación:** test en `WrapWithLiveTemplateTest` que, tras envolver con Opacity,
  deshace hasta el original y comprueba que ningún paso intermedio pierde `Text('hi')`.

### A2. Descarga dinámica del plugin deja intenciones "zombis" — [x] hecho

- **Problema:** el verifier da el plugin por dinámico, así que el IDE lo descarga en
  caliente al actualizar o desactivar. `IntentionManagerImpl` solo quita al descargar
  las intenciones registradas por extension point; las de `addAction` se quedan. Eso
  retiene el classloader y, tras actualizar, el registro ve los mismos ids y no añade
  las nuevas (deducido del bytecode, no reproducido).
- **Otras retenciones del classloader:**
  - `ProjectWrappers.CACHE`: un objeto del plugin en el user data de cada `Project`
    (`ProjectWrappers.kt:22,34`).
  - `ThreadLocal<Label>` en `WrapSelectionIntention.kt:26-30`: una clase del plugin en
    hilos del pool.
- **Arreglo:**
  - `WrapIntentionRegistrar.unregisterAll()`: da de baja las intenciones propias (ids de
    `RegisteredWrapWithWidgetIntention` y las cuatro singleton).
  - `ProjectWrappers.clearCaches()`: limpia el user data de los proyectos abiertos.
  - Listener `DynamicPluginListener` en `<applicationListeners>`: en
    `beforePluginUnload`, si el id es el nuestro, llama a los dos anteriores.
  - `WrapSelectionIntention`: guardar en el ThreadLocal el `String` ya resuelto en vez
    de `Label`.
- **Verificación:** test de `unregisterAll()` (no queda ninguna intención propia y
  `syncRegistrations()` las repone) y del texto de `WrapSelectionIntention`. Manual con
  `runIde`: desactivar y reactivar el plugin en Settings › Plugins sin reiniciar.

## B. Rendimiento (de más a menos impacto)

### B1. Detección PSI en cada movimiento del caret — [ ] pendiente

- **Problema:** `widgetClassName` usa `element.text.substringBefore('(')`
  (`PsiFlutterWidgetDetector.kt:65`). En un nodo compuesto `getText()` recorre todo el
  subárbol, así que subir desde un `Text` hasta el `Scaffold` reconstruye casi todo el
  `build()` una vez por nivel. Se repite en `:25`, `:30` y en `slotOf` (`:44`).
- **Arreglo:**
  - Leer solo las hojas hasta el primer `(` (`PsiTreeUtil.firstChild` / `nextLeaf`). Da
    exactamente el mismo string, también en cadenas como `Foo(a).bar(b)`.
  - Una sola pasada en `detect` que recoja nombre, ancestros y slot.
- **Verificación:** tests de detección existentes, más casos nuevos: `Foo(a).bar(b)`,
  `Foo<void Function()>()`, `const`/`new`, `ListView.builder` y `prefix.Widget`.

### B2. Las wrap intentions se evalúan en ficheros que no son Dart — [ ] pendiente

- **Problema:** el IDE consulta las intenciones de `addAction` en cualquier lenguaje.
  `WrapWithWidgetIntention.isAvailable` (`WrapWithWidgetIntention.kt:44-50`) hace
  `WrapperRepository.byName(…, project)` (`guessProjectDir` + `findChild`) antes de
  comprobar `.dart`, y eso por cada wrapper.
- **Arreglo:** `if (!file.name.endsWith(".dart")) return false` como primera
  comprobación.
- **Verificación:** test de que no está disponible en un fichero `.kt`; tests existentes.

### B3. Postfix — [ ] pendiente

- **Problema:**
  - `getTemplates()` recrea todas las plantillas y recorre los proyectos abiertos en
    cada Tab o completion en Dart (`WrapperPostfixTemplateProvider.kt:29-33`).
  - `widgetEndingAt` (`:49-60`) lanza una detección completa por cada nivel del árbol.
- **Arreglo:**
  - Cachear el `Set` mientras no cambie la lista de nombres; los wrappers nuevos siguen
    apareciendo sin reiniciar.
  - En `widgetEndingAt`, llamar a `detect` solo si `isWidgetExpression(element)`; mismo
    resultado.
- **Verificación:** tests de `widgetEndingAt` en `RoadmapFeaturesTest`. Test de que
  `getTemplates()` devuelve las mismas instancias sin cambios y que incluye un custom
  recién añadido.

### B4. `file.text` recorre el AST entero — [ ] pendiente

- **Dónde:**
  - `MultiWidgetSelectionDetector.kt:56`: en cada pasada de intenciones con selección.
  - `ConstContext.kt:36`
  - `WidgetSurroundDescriptor.kt:47`
- **Arreglo:** `file.viewProvider.contents` (el texto del documento, sin coste);
  `trimSelection` pasa a recibir `CharSequence`.
- **Opcional:** `WidgetLibrary.kt:29` (solo al invocar; `DartImports` trabaja con
  `String`).
- **Verificación:** `MultiWidgetSelectionDetectorTest`, `WrapPreviewAndConstTest`,
  `WrapChooserAndSurroundTest`.

### B5. `pubspec.yaml` en cada `plan()` — [ ] pendiente

- **Problema:** `WrapApplier.kt:72` busca y parsea el pubspec en cada wrap y en cada
  preview del Alt+Enter. Ocurre aunque `autoImport` esté desactivado o el import ya
  exista.
- **Arreglo:** dependencias con `lazy`, que solo se evalúa cuando falta algún import.
  Sobrecarga de `WrapperImports.missing` con proveedor perezoso; la firma actual se
  mantiene para los tests.
- **Verificación:** `AutoImportTest`, `WrapperImportsTest`.

### B6. Settings: `isModified()` vuelve a parsear el JSON — [ ] pendiente

- **Problema:** Gson se ejecuta cada vez que el diálogo de Settings consulta
  `isModified()` (`WrapperSettingsConfigurable.kt:556-561`).
- **Arreglo:** cachear la lista parseada junto con el JSON del que sale.
- **Verificación:** `WrapperSettingsConfigurableTest`.

### B7. Listener VFS calcula la ruta de cada evento — [ ] pendiente

- **Problema:** `ProjectWrappersListener.kt:10` llama a `event.path` para cada evento,
  en el hilo de UI y dentro de la write action.
- **Arreglo:** filtrar por nombre: `childName` en creación y copia, `oldValue` /
  `newValue` en renames y `file.name` en el resto. Además detecta el rename *desde*
  `.flutter-wrappers.json`.
- **Verificación:** test creando, renombrando y borrando el fichero si el fixture lo
  permite; si no, manual con `runIde`.

## C. Limpieza sin cambio de comportamiento

### C1. `skipString` duplicado — [ ] pendiente

- `WrappableFieldDetector.kt:238` tiene su propio `skipString`, que no maneja raw strings
  (`r'C:\'`) ni interpolación. Pasar a `DartLexer.skipString`, que es más correcto.
- **Verificación:** `WrappableFieldDetectorTest` más un caso con raw string.

### C2. Salto de comentarios repetido en 6 escáneres — [ ] pendiente

- Un `DartLexer.skipComment` compartido por `TextFlutterWidgetDetector`,
  `splitTopLevel`, `MultiWidgetSelectionDetector`, `WrappableFieldDetector` y
  `TemplateLint`.
- `DartImports` se queda como está: sus comentarios de bloque anidan a propósito.
- **Verificación:** suite completa.

### C3. `wrapperNames()` vuelve a parsear los settings — [ ] pendiente

- `WrapIntentionRegistrar.kt:76-91` → `WrapperRepository.all()`, que da los mismos
  nombres en el mismo orden.

### C4. Campos sin uso en `FlutterWidgetContext` — [ ] pendiente

- `isDirectChildOfFlex` e `isInsideStack` (`FlutterContextAnalyzer.kt:15-16`) solo se
  leen en tests. Quitarlos y ajustar los tres tests que los construyen.

### C5. `WrapperContextMatcher` crea una lista por llamada — [ ] pendiente

- `WrapperContextMatcher.kt:29-33` crea `chain` en cada llamada. Sustituir por
  `parentWidgetName in allowed || ancestors.any { it in allowed }`.
- Se mantiene la comprobación del padre: los tests construyen contextos en los que
  `ancestors` no lo incluye.

### C6. Comprobación redundante en `CreateWrapperFromWidgetIntention` — [ ] pendiente

- En `CreateWrapperFromWidgetIntention.kt:45`, `fieldName in WRAPPABLE_FIELDS` siempre
  es verdadero.
- Además, `isAvailable` no limpia `cachedText` al empezar: añadir `cachedText.remove()`.

### C7. Servicio registrado dos veces — [ ] pendiente

- `plugin.xml:63` registra `FlutterWrapperSettings`, que ya es light service
  (`@Service`). Quitar la línea del XML.
- **Verificación:** `WrapperSettingsConfigurableTest`; manual: los ajustes persisten
  tras reiniciar `runIde`.

### C8. `shippedImport` duplicado — [ ] pendiente

- `WidgetLibrary.kt:64-65` reimplementa `WrapperImports.shipped`. Misma precedencia: el
  built-in gana al preset.

## Descartado

- Registrar las intenciones por extension point con filtro de lenguaje: rompe el
  registro dinámico o exige `IntentionActionWrapper` (interno).
- Cachear "wrappers disponibles en el caret": mucha invalidación para poca ganancia una
  vez hecho B2.
