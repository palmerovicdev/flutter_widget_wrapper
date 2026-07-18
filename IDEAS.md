# Flutter Widget Wrapper — Análisis y hoja de ruta de ideas

> Documento de trabajo. Revisión del estado actual del plugin, hallazgos
> accionables detectados en el código, e ideas de nuevas funcionalidades
> **realmente útiles**, priorizadas por impacto y por cómo encajan con la
> arquitectura existente.
>
> Fecha de revisión: 2026-07-18 · Versión revisada: 1.2.1

---

## 1. Estado actual del plugin

Lo que el plugin hace hoy:

- **Envuelve un widget** bajo el cursor con wrappers built-in o custom desde el
  menú `Alt+Enter`, respetando reglas de contexto (`allowedParents`,
  `disallowedParents`, `requiresDirectParent`).
- **Envuelve varios widgets** seleccionados a la vez, pero **solo en `Stack`**
  (`WrapSelectionWithStackIntention`).
- **Crea un wrapper custom** a partir de un widget existente
  (`CreateWrapperFromWidgetIntention`), detectando el campo envolvente
  (`child`, `children`, `sliver`, `slivers`).
- **Editor de settings** master-detail: árbol por categorías, activar/desactivar
  built-ins, añadir/editar/duplicar custom, edición JSON, import/export.
- **Detección** mediante **PSI de Dart** (`PsiFlutterWidgetDetector`) con
  fallback a un scanner de texto (`TextFlutterWidgetDetector`).
- Preserva indentación y aplica el formateador del IDE tras envolver.

### Arquitectura (mapa rápido)

| Capa | Archivos clave |
|------|----------------|
| Detección | `detection/FlutterWidgetDetector.kt`, `PsiFlutterWidgetDetector.kt`, `TextFlutterWidgetDetector.kt`, `MultiWidgetSelectionDetector.kt`, `WrappableFieldDetector.kt`, `WidgetNameHeuristics.kt`, `DartLexer.kt` |
| Contexto | `detection/FlutterContextAnalyzer.kt`, `model/FlutterWidgetContext.kt`, `model/DetectedWidget.kt` |
| Modelo de wrapper | `model/WidgetWrapper.kt` |
| Motor de wrapping | `wrappers/WrapperTemplateEngine.kt`, `WrapperContextMatcher.kt` |
| Persistencia / repo | `wrappers/WrapperRepository.kt`, `WrapperJsonCodec.kt`, `WrapperValidator.kt`, `BuiltInWrappers.kt`, `settings/FlutterWrapperSettings.kt` |
| Intentions (Alt+Enter) | `intention/WrapWithWidgetIntention.kt`, `RegisteredWrapWithWidgetIntention.kt`, `WrapIntentionRegistrar.kt`, `WrapSelectionWithStackIntention.kt`, `CreateWrapperFromWidgetIntention.kt` |
| UI settings | `settings/WrapperSettingsConfigurable.kt`, `WrapperFormDialog.kt` |

---

## 2. Hallazgos accionables (durante la revisión)

Dos cosas que conviene arreglar de inmediato, independientes de las ideas nuevas:

### 2.1. `Expanded` se anuncia pero no existe

El `README.md` (línea 14) y `plugin.xml` (`<description>`) listan `Expanded`
entre los wrappers incluidos, pero **`BuiltInWrappers.ALL` solo tiene
`Flexible`**. Es un desfase documentación ↔ código. Además faltan los wraps más
frecuentes del día a día (`Padding`, `Container`, `Center`) — ver idea #6.

**Acción:** añadir `Expanded` (mismo patrón que `Flexible`:
`allowedParents = ["Row", "Column", "Flex"]`, `requiresDirectParent = true`) o
corregir la documentación.

### 2.2. `isInsideStack` se calcula pero nadie lo usa

`FlutterContextAnalyzer` rellena `FlutterWidgetContext.isInsideStack`, pero el
matcher sigue usando `allowedParents` / `requiresDirectParent` (como hace
`Positioned` tras la idea #4). El flag queda redundante salvo que algún wrapper
lo lea directamente.

**Acción:** o bien cablear `isInsideStack` en `WrapperContextMatcher`, o
eliminarlo junto con el cálculo en el analyzer.

---

## 3. Ideas de alto valor (recomendadas)

### Idea 1 — Unwrap / "Remove this widget"

[NO IMPLEMENTAR]

**Qué es.** La operación inversa a envolver: quitar el wrapper externo y
promover su hijo. Ejemplo:

```dart
// Antes (cursor en Padding)      // Después
Padding(                          Text('Hola')
  padding: EdgeInsets.all(8),
  child: Text('Hola'),
)
```

**Por qué es funcional.** Es lo que más echa en falta cualquiera que use "wrap"
a diario: te equivocas de wrapper, o refactorizas y sobra una capa. Hoy hay que
borrarla a mano y arreglar la indentación.

**Cómo encaja.** Reutiliza casi toda la maquinaria existente:
`FlutterWidgetDetector.detect` localiza el widget bajo el cursor y
`WrappableFieldDetector.find` **ya devuelve el rango exacto del valor de
`child:`** (`valueStart`/`valueEnd`). Basta con reemplazar el widget detectado
por ese contenido, re-indentar (lógica ya presente en
`CreateWrapperFromWidgetIntention.normalizeIndent`) y reformatear.

**Consideraciones.**
- Si el campo es `children:` con varios elementos, ofrecerlo solo cuando haya
  exactamente un hijo (o desactivarlo para listas), para no perder código.
- Disponible solo cuando el widget tiene un `child`/`children` claramente
  envolvente.

**Esfuerzo.** Bajo. Nueva intention `UnwrapWidgetIntention` + registro en
`WrapIntentionRegistrar`.

---

### Idea 2 — Live templates: colocar el cursor en el valor editable

[HECHO]

**Qué es.** Tras envolver, saltar automáticamente al primer valor que el usuario
querrá editar, con tab-stops entre varios. Hoy, al envolver con
`Opacity(opacity: 0.5, …)` hay que navegar a mano hasta el `0.5`.

**Por qué es funcional.** Convierte cada wrapper en algo accionable al instante.
Es el patrón que usan los "Wrap with…" nativos y las live templates de IntelliJ,
y marca una diferencia enorme de productividad frente a insertar texto muerto.

**Cómo encaja.** La sintaxis de plantilla admitiría marcadores de tab-stop
(p.ej. `${1:0.5}`, `${2:Alignment.center}`, `$END$`) además del `${widget}`
actual. En `WrapWithWidgetIntention.invoke`, en lugar de `document.replaceString`
+ reformat, construir un `Template` con `TemplateManager` de la plataforma y
lanzarlo sobre el rango. El `WrapperValidator` y el editor de settings tendrían
que conocer la nueva sintaxis.

**Consideraciones.**
- Mantener compatibilidad hacia atrás: un template sin marcadores se comporta
  igual que hoy.
- Conviene un campo o convención clara para no chocar con el `${widget}`.

**Esfuerzo.** Medio. Es el cambio con mejor relación "wow"/esfuerzo.

---

### Idea 3 — Multi-child genérico (no solo Stack)

[IMPLEMENTAR]

**Qué es.** Hoy solo puedes envolver varios widgets seleccionados en `Stack`.
Generalizarlo para envolverlos también en **`Column`, `Row`, `Wrap`, `Flex`,
`ListView`**, etc.

**Por qué es funcional.** El detector multi-selección ya existe y funciona; hoy
está infrautilizado porque solo alimenta un destino. "Selecciono 3 widgets y los
meto en una Column/Wrap" es una operación cotidiana.

**Cómo encaja.** `MultiWidgetSelectionDetector.analyze` ya devuelve los elementos
hermanos y el padre. `WrapSelectionWithStackIntention` está cableado a `Stack`;
se puede parametrizar por wrapper multi-child. En el modelo `WidgetWrapper`, un
flag `multiChild: Boolean` (o `childField`: `child` vs `children`) indicaría qué
wrappers pueden envolver selecciones múltiples y con qué campo de lista.

**Consideraciones.**
- Reutilizar `reindent` que ya tiene `WrapSelectionWithStackIntention`.
- Ofrecer en el menú solo los wrappers multi-child cuando hay selección múltiple.

**Esfuerzo.** Bajo-medio. Refactor de una intention existente + flag en el modelo.

---

[ECHO]

### Idea 4 — `Positioned` dentro de `Stack` (activar el contexto muerto)

[IMPLEMENTAR]

**Qué es.** Añadir un wrapper `Positioned` que se ofrezca **solo** cuando el
widget es hijo directo de un `Stack`.

**Por qué es funcional.** Es el caso de uso real de `Stack` (posicionar hijos), y
de paso da sentido a `isInsideStack`, que hoy se calcula y no se usa (ver §2.2).
Es también una buena demostración del sistema de contexto para los usuarios.

**Cómo encaja.** Trivial con lo que ya hay:

```kotlin
WidgetWrapper(
    name = "Positioned",
    template = listOf(
        "Positioned(",
        "  top: 0,",
        "  left: 0,",
        $$"  child: ${widget},",
        ")",
    ),
    category = "Layout",
    allowedParents = listOf("Stack"),
    requiresDirectParent = true,
)
```

`WrapperContextMatcher` ya soporta `requiresDirectParent` + `allowedParents`, así
que funcionaría sin tocar el motor.

**Esfuerzo.** Muy bajo. Combina de maravilla con la idea 2 (tab-stops en
`top`/`left`).

---

### Idea 5 — Auto-import para wrappers custom

[IMPLEMENTAR]

**Qué es.** Cuando un wrapper necesita un paquete (p.ej. `Consumer` de
`provider`, o un widget de tu design system), insertar el `import`
automáticamente al envolver.

**Por qué es funcional.** Es el dolor #1 al usar wrappers de terceros: envuelves
y te queda el símbolo en rojo hasta que añades el import a mano. Automatizarlo
hace que los wrappers custom "just work".

**Cómo encaja.** Añadir un campo opcional `import: String?` a `WidgetWrapper`
(p.ej. `"package:provider/provider.dart"`). Tras envolver, comprobar vía PSII de
Dart si el import ya está en el `DartFile` y, si no, insertarlo en la zona de
imports. El editor de settings y el `WrapperJsonCodec` tendrían que exponer el
nuevo campo.

**Consideraciones.**
- No duplicar imports existentes.
- Respetar el orden/estilo de imports del proyecto (idealmente delegar en el
  optimizador de imports del IDE si aplica a Dart).

**Esfuerzo.** Medio. El grueso es manejar bien la inserción del import en el PSI.

---

## 4. Ideas complementarias (buen valor, más opcionales)

### Idea 6 — Wrappers built-in que faltan y son los más usados

[NO IMPLEMENTAR]

Añadir los wraps de mayor frecuencia real en Flutter, hoy ausentes:
`Padding`, `Container`, `Center`, `Expanded` (ver §2.1), `SizedBox`,
`ClipRRect`, `AspectRatio`, `Visibility`, `Tooltip`, `Card`, `DecoratedBox`,
`FittedBox`, `ConstrainedBox`, `Hero`.

**Por qué.** `Center`, `Padding` y `Container` son literalmente los wraps más
frecuentes del ecosistema. Su ausencia es llamativa.

**Esfuerzo.** Trivial (entradas en `BuiltInWrappers.ALL`). Combinan muy bien con
la idea 2 para los valores editables (`padding`, `width`, `height`, etc.).

---

### Idea 7 — Chooser único "Wrap with…" (popup buscable)

[IMPLEMENTAR]

**Qué es.** Una sola acción (con atajo asignable) que abra un popup filtrable
mostrando **solo** los wrappers válidos en el contexto actual, en lugar de N
entradas separadas en el menú `Alt+Enter`.

**Por qué es funcional.** Con 9 built-ins + custom, el menú `Alt+Enter` se llena
y cuesta encontrar el wrapper. Un popup con búsqueda por nombre es más rápido y
descubrible; es el patrón del "Wrap with widget" nativo.

**Cómo encaja.** Nueva `AnAction` que reúne `WrapperRepository.all()` filtrado
por `WrapperContextMatcher.matches`, muestra un `JBPopup`/lista, y reutiliza la
lógica de `WrapWithWidgetIntention.invoke`. Puede convivir con las intentions
individuales (o sustituirlas mediante una opción en settings).

**Esfuerzo.** Medio.

---

### Idea 8 — Wrappers por proyecto (no solo a nivel aplicación)

[IMPLEMENTAR]

**Qué es.** Hoy `FlutterWrapperSettings` es `Service.Level.APP`: los wrappers son
globales del IDE. Permitir además wrappers **por proyecto**, versionables en el
repo.

**Por qué es funcional.** Un equipo con design system propio podría compartir sus
wrappers (componentes, tokens de spacing, etc.) versionándolos, de modo que todos
tengan los mismos "Wrap with…" sin configurar nada.

**Cómo encaja.** Añadir un `Service.Level.PROJECT` con `Storage` en `.idea/` o en
un archivo del proyecto, y hacer que `WrapperRepository.all()` fusione
app-level + project-level. Requiere pensar precedencias y el flujo de `sync` de
`WrapIntentionRegistrar` a nivel de proyecto.

**Esfuerzo.** Medio-alto (toca persistencia y ciclo de registro).

---

### Idea 9 — Packs / presets de gestión de estado y librerías

[IMPLEMENTAR]

**Qué es.** Conjuntos de wrappers listos para importar con un clic:
- **Provider/Riverpod:** `Consumer`, `Consumer<T>`, `ChangeNotifierProvider`.
- **Bloc:** `BlocBuilder`, `BlocProvider`, `BlocListener`.
- **GetX:** `Obx`, `GetBuilder`.
- **Material comunes:** `Card`, `ListTile`, `Tooltip`.

**Por qué es funcional.** Reduce a cero la fricción de arranque: en vez de crear
cada wrapper a mano, importas el pack de tu stack. Combina con la idea 5
(auto-import) para que funcionen sin tocar nada.

**Cómo encaja.** Reutiliza el import JSON existente (`WrapperJsonCodec`); los
packs pueden ir empaquetados como recursos y ofrecerse desde el editor de
settings ("Importar pack…").

**Esfuerzo.** Bajo (si son JSON) + medio si se integra con auto-import.

---

## 5. Orden recomendado

Priorizando "valor entregado / esfuerzo" y reutilización de lo ya construido:

1. **Idea 1 — Unwrap** · bajo esfuerzo, altísima demanda, recicla `WrappableFieldDetector`.
2. **Idea 4 — Positioned en Stack** · casi gratis, activa `isInsideStack`.
3. **Idea 3 — Multi-child genérico** · recicla `MultiWidgetSelectionDetector`.
4. **Idea 2 — Live templates / cursor** · el mayor salto de productividad.
5. Después: ideas 5–9 según prioridad de producto.

Arreglos de higiene a hacer en paralelo: **§2.1** (`Expanded`) y **§2.2**
(`isInsideStack` / documentación).

---

## 6. Notas rápidas de implementación

- Las nuevas intentions se registran dinámicamente en
  `WrapIntentionRegistrar.syncRegistrations()` (patrón ya establecido; recordar
  la identidad de implementación distinta como en
  `RegisteredWrapWithWidgetIntention`).
- Cualquier campo nuevo en `WidgetWrapper` (`import`, `multiChild`, tab-stops)
  debe reflejarse en `WrapperJsonCodec`, `WrapperValidator`, `WrapperFormDialog`
  y en los tests correspondientes.
- Mantener la compatibilidad del JSON existente (campos nuevos siempre
  opcionales con default).
