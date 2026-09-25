# Roadmap — Flutter Widget Wrapper

Revisión de UX, UI y funcionalidades (2026-09-25). Todo son propuestas; nada está
implementado todavía salvo que se indique.

## 1. Fallos visibles (prioridad alta)

| # | Problema | Dónde |
|---|---|---|
| 1 | **El campo `warning` nunca se muestra.** Se guarda y edita, pero nada lo lee: el aviso de InkWell ("needs a Material ancestor") no llega al usuario. | `BuiltInWrappers.kt`, `WrapperFormDialog.kt` |
| 2 | **Sin preview en Alt+Enter para casi todos los wrappers.** Los que tienen tab-stops devuelven `startInWriteAction() = false`, y la plataforma no genera diff: AnimatedSize, GestureDetector, InkWell, Align, Positioned y Opacity no tienen vista previa. Implementar `generatePreview()` con `TabStops.stripToDefaults()`. | `WrapWithWidgetIntention.kt` |
| 3 | **Envolver dentro de un padre `const` rompe la compilación.** `const Column(children: [Text('a')])` envuelto con GestureDetector → "Invalid constant value". Quitar `const` del ancestro más cercano y propagarlo a los hermanos. | `WrapWithWidgetIntention.invoke` |
| 4 | **No se puede sobrescribir un built-in desde la UI.** `WrapperRepository.merge()` lo soporta (custom con el mismo nombre gana), pero el diálogo lo rechaza como duplicado; solo se consigue importando JSON. | `WrapperFormDialog.doValidate` |
| 5 | **Textos fuera del bundle.** "Wrap with $wrapperName" está hardcodeado aunque existe `intention.text.wrap`; igual los textos de Stack, errores de import/export y el mensaje "Wrapper created". | varios |
| 6 | **Import sobrescribe sin avisar** los wrappers con el mismo nombre y descarta en silencio los inválidos (sin decir cuántos). | `WrapperSettingsConfigurable.onImport` |

## 2. UX del menú Alt+Enter

- **Saturación del menú.** Más de 10 entradas propias "Wrap with X", más las del plugin de
  Flutter, y aparecen con el cursor en *cualquier* punto del widget (incluso dentro de un
  string). Propuestas:
  - Ajuste "solo cuando el cursor está sobre el nombre del constructor".
  - Opción de agruparlas en una única entrada **"Wrap with…"** que abra un popup con
    búsqueda, categorías, iconos y preview.
- **Orden por contexto** con `PriorityAction`: dentro de un `Stack`, Positioned arriba; en
  `Row`/`Column`, Flexible arriba.
- **Submenú (flecha →)** por wrapper: "Editar wrapper…" y "Ocultar este wrapper", sin pasar
  por Settings.
- **Iconos** en las entradas (`Iconable`) para distinguirlas de las del plugin de Flutter.
- **Crear wrapper desde un widget:** sustituir el `Messages.showInfoMessage` modal por una
  notificación no bloqueante con acción "Deshacer / Editar".

## 3. UI de ajustes

- **Editor de plantilla:** cambiar el `JBTextArea` por un `EditorTextField` con tipo de
  fichero Dart (resaltado, cierre de paréntesis, buscar/reemplazar).
- **Preview en vivo en el diálogo** mientras se escribe; hoy solo existe en el panel de
  detalle después de guardar.
- **Mejor validación:**
  - Nombres reservados (`widget`, `end`).
  - Marcadores con llaves mal formadas.
  - Paréntesis descompensados.
  - Idealmente, parsear la preview con `PsiFileFactory` y marcar errores de Dart.
- **Campos del formulario:**
  - Categoría: combo editable con las categorías existentes en vez de texto libre.
  - Padres permitidos/prohibidos: autocompletado o chips en vez de texto separado por comas.
- **Árbol:**
  - Búsqueda por texto (`TreeSpeedSearch`).
  - Doble clic para editar.
  - "Customize" en built-ins, que crea un override con el mismo nombre.
  - Botón "Reset built-ins" (la clave `settings.button.reset` existe, el botón no).
- **Export:** poder elegir qué wrappers exportar, no solo "todos los custom".

## 4. Nuevas funcionalidades (ordenadas por valor)

1. **Envolver varios widgets seleccionados con cualquier wrapper con lista de hijos**, no
   solo `Stack`: Column, Row, Wrap, ListView y customs con `[${widget}]`. El detector de
   selección múltiple ya existe; hay que generalizar `WrapSelectionWithStackIntention`.
2. **Surround With (⌥⌘T)** vía `lang.surroundDescriptor` para Dart. Es el atajo que la gente
   ya usa y encaja con la selección múltiple.
3. **Cambiar un wrapper por otro manteniendo el hijo**: Flexible ↔ Expanded, Align → Center,
   GestureDetector ↔ InkWell. El plugin de Flutter tiene "Remove this widget", pero no esto.
4. **Wrappers a nivel de proyecto** (p. ej. `.idea/flutterWidgetWrapper.json` o un fichero en
   la raíz) para compartirlos con el equipo por git.
5. **Tab-stops con opciones**: `${alignment:Alignment.center|Alignment.topLeft|…}` que muestre
   un lookup en la live template (Curves, Alignment, MainAxisAlignment…).
6. **Reglas por *slot***: dentro de `slivers:`, ofrecer solo wrappers sliver y sugerir
   `SliverToBoxAdapter` si el widget no lo es.
7. **Volver a añadir `Expanded`.** Se quitó porque se sugería mal, pero ahora la detección es
   por PSI y `requiresDirectParent` funciona igual que en Flexible. (`CLAUDE.md` aún lo cita.)
8. **Catálogo de presets importables**, desactivados por defecto:
   - Estado: Consumer (Riverpod), BlocBuilder, ValueListenableBuilder, AnimatedBuilder.
   - Transformación/visibilidad: Hero, Visibility, IgnorePointer, ClipRRect, Transform.
   - Otros: Tooltip, Semantics, RepaintBoundary, Dismissible, AspectRatio, FittedBox,
     ConstrainedBox, LayoutBuilder.
9. **Postfix templates**, p. ej. `Text('a').opacity` + Tab.
10. **Traducción al español** (`FlutterWidgetWrapperBundle_es.properties`), tras centralizar
    los textos (fallo 1.5).

## 5. Rendimiento y robustez

- **`isAvailable` repite trabajo en cada movimiento del cursor.** Cada wrapper llama a
  `WrapperRepository.byName()` (reparsea JSON, valida, mezcla) y luego a
  `FlutterWidgetDetector.detect()`: N parseos y N recorridos de PSI cada vez que se calcula la
  bombilla. Cachear la lista mezclada (invalidar en `apply()`) y la última detección por
  `(file.modificationStamp, offset)`.
- **Estado compartido.** `cachedText`/`cachedCount` son campos mutables en instancias globales
  compartidas entre proyectos y editores. Calcular el texto sin estado o usar `setText` justo
  antes de mostrarlo.
- **Registros que no se limpian.** Al renombrar o borrar customs, sus intentions siguen
  registradas: invisibles, pero se acumulan durante la sesión.

## 6. Plan de versiones

- **1.3:** fallos de la sección 1, preview en todos los wrappers, caché de rendimiento,
  textos al bundle y traducción al español.
- **1.4:** popup "Wrap with…" con búsqueda, Surround With, selección múltiple con cualquier
  wrapper, editor de plantilla con resaltado y preview en vivo.
- **1.5:** wrappers de proyecto, cambiar wrapper, tab-stops con opciones, reglas por slot y
  catálogo de presets.

## Notas de entorno

- Probar con la IDE instalada (`/Applications/IntelliJ IDEA.app`, 2026.2.2) o las ya cacheadas
  en Gradle (2026.1.5, 262.10315.125); no descargar IDEs nuevas.
- Pendiente: tarea `runIdeLocal` en `build.gradle.kts` que use la IDE instalada.
