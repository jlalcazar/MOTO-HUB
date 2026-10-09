# CFMOTO 800MT: revisión de batería y fluidez

Revisión de la rama `feature/cmoto`. Objetivo: gastar menos batería en una CFMOTO 800MT, o que la imagen
en la pantalla TFT vaya más fluida sin gastar más batería.

## De qué depende el consumo en la 800MT

La 800MT usa el perfil `CFDL26_LANDSCAPE` (`modelId 37426`, panel táctil de 800x480,
`requiresSockAuth = true`). Este perfil no define `encoderKeyframeIntervalSeconds`, así que el
vídeo que se envía a la pantalla es **todo-intra**: cada frame es un IDR completo, como si fuera un JPEG por
frame. Ese formato aguanta bien que se pierdan frames, pero gasta mucho más ancho de banda que un
GOP.

Por orden de importancia, la batería se va en:

1. **La pantalla del teléfono**, que sigue encendida durante el mirroring.
2. **La radio Wi-Fi**, que va con los bloqueos `LOW_LATENCY` y `HIGH_PERF` (es decir, sin
   ahorro de energía) y transmite el vídeo todo-intra.
3. **El encoder H.264 y la GPU**: el compositor en Android Auto y el VirtualDisplay en el mirroring.
4. **El calor**: si el teléfono se calienta, Android lo limita y la imagen pierde fluidez.

## Cambios aplicados en esta rama

### 1. El modo de energía ya baja los FPS en el mirroring todo-intra (bug)

`AvcEncoder.shouldForwardFrame` dejaba pasar siempre los keyframes, para que una pantalla que se
reconecta no se quede esperando imagen. En un stream todo-intra **todos** los frames son
keyframes, así que el límite de FPS no tenía ningún efecto. En la 800MT, al hacer mirroring
del teléfono, pasaba esto:

- *Ahorro* (20 fps) y *Equilibrado* (24 fps) solo bajaban el bitrate. La radio seguía enviando
  30 IDR por segundo.
- El límite térmico (20, 15 y 12 fps) y el retroceso por pérdidas en el enlace tampoco bajaban los fps.

**Cambio:** la nueva clase `EncodedFramePacer` también limita el ritmo de los streams todo-intra:
cada frame se decodifica por sí solo, así que descartar uno es seguro. Solo se saltan el límite el
primer keyframe del stream y el que pide la pantalla con `requestSyncFrame`, que es el caso que la
regla antigua protegía. Los streams GOP no cambian: se siguen limitando en la entrada.
Además, el ritmo se calcula a partir del hueco previsto y no de "ahora", con 2 ms de margen.
Sin esto, una fuente de 30 fps limitada a 20 se quedaba en 15.

*Efecto esperado:* en Ahorro, alrededor de un 33 % menos de datos por Wi-Fi y de trabajo de
decodificación en la pantalla. En Equilibrado, alrededor de un 20 %. Con el teléfono caliente o
con el enlace perdiendo frames, los fps ya bajan de verdad.

*Coste conocido:* si se descarta justo el último frame de una animación y la pantalla queda
estática, la TFT muestra el frame anterior hasta que el encoder repite (como máximo 0,9 s).
Esto solo ocurre con un límite por debajo de 30 fps; en *Suave* y en *Auto* sin calor no cambia nada.

Tests: `EncodedFramePacerTest`.

### 2. El encoder deja de codificar frames de más en teléfonos de 90/120 Hz

El VirtualDisplay del mirroring sigue la frecuencia de refresco del teléfono. En un teléfono de
120 Hz, el encoder recibía hasta 4 veces más frames de los negociados con la moto. Todos se
codificaban (como IDR completos en todo-intra) y después la mayoría se descartaba.

**Cambio:** el encoder se configura con `KEY_MAX_FPS_TO_ENCODER = frameRate`. El códec descarta los
frames sobrantes **antes** de codificarlos, lo que también es seguro con GOP. Es la misma clave que
usa scrcpy para su `--max-fps`. Solo se añade en las configuraciones "completas" de la escalera de
configuración; si un códec la rechaza, la versión mínima (`bare`) no la incluye.

*Efecto esperado:* menos uso del encoder y de la GPU, y menos calor, en teléfonos con pantalla de
alta frecuencia mientras hay animaciones o desplazamiento.

### 3. Android Auto: menos tirones sin gastar más batería

`AaCompositor` agrupaba los frames que llegaban antes de su hueco y no los dibujaba hasta el
siguiente tick de keep-alive, que es cada **150 ms**, o hasta que llegaba el siguiente frame de
la fuente. Con una fuente de 30 fps algo irregular, un frame que llegaba 1 ms antes se
retrasaba hasta 5 frames o se perdía, y en la TFT se veían tirones.

**Cambio:** el frame pendiente se dibuja en cuanto se abre su hueco, con un `postDelayed` del
tiempo que falta. Los dibujados por segundo siguen limitados por el límite de FPS, así que el
trabajo de GPU y encoder no supera lo que ya estaba permitido.

## Ajustes recomendados para un piloto con 800MT (sin tocar código)

| Ajuste | Valor | Por qué |
| --- | --- | --- |
| Atenuar la pantalla del teléfono | **Activado** (necesita el permiso de superposición) | La pantalla del teléfono es lo que más gasta. Viene **desactivado** por defecto. |
| Modo de energía | **Equilibrado** (24 fps) o **Ahorro** (20 fps) | Con el cambio 1 ya bajan los fps de verdad en el mirroring. |
| Calidad de vídeo | **Más fluida** (×0,7 bitrate) | Menos datos por Wi-Fi. En 800x480 apenas se nota. |
| Resolución de Android Auto | 800x480 (la que ya tiene el perfil) | Coincide con el panel; más resolución solo gasta más. |
| Optimización de batería | Excluir MOTO-HUB | Evita que Android corte la sesión y obligue a reconectar, que gasta más. |
| Soporte del teléfono | Ventilado, sin sol directo | Si se calienta, el límite térmico baja la fluidez. |

## Propuestas siguientes (no aplicadas)

Ordenadas por beneficio y riesgo. Las que cambian lo que se envía a la pantalla necesitan una prueba
real en una 800MT, con el informe de diagnóstico de la app.

1. **GOP con intra-refresh para `CFDL26_LANDSCAPE` (experimento opcional).** Con
   `encoderKeyframeIntervalSeconds = 1`, un mapa estático pasaría de enviar IDR completos a
   P-frames de pocos bytes: es probablemente el **mayor ahorro posible de Wi-Fi**, varias veces
   menos datos. Riesgos: no se sabe si el decodificador del CFDL26 lo soporta, y en la MTX800 hubo
   macrobloques verdes con un códec sin intra-refresh. Ese caso ya lo cubre
   `effectiveKeyframeIntervalSeconds`, que vuelve a todo-intra. Debe empezar como opción de
   `TBoxWireLadder` o de un perfil manual, nunca activado por defecto.
2. **Aplicar también el modo Ahorro o Equilibrado en la entrada del encoder.** Hoy el
   `KEY_MAX_FPS_TO_ENCODER` usa el frameRate de la sesión (30). Si se usara el del modo elegido
   (20 o 24), se ahorraría también la codificación, no solo la transmisión. Para hacerlo hay que
   pasar el modo al `EncoderProfile` en `ProjectionSessionService`, `AndroidAutoSessionService`
   e `IpcBridgeService`.
3. **Atenuación activada por defecto, o sugerida al emparejar**, cuando ya existe el permiso de
   superposición.
4. **Revisar los dos bloqueos Wi-Fi.** `WIFI_MODE_FULL_LOW_LATENCY` y `WIFI_MODE_FULL_HIGH_PERF` se
   piden a la vez. Conviene medir si con solo `LOW_LATENCY` (Android 10+) el enlace sigue igual de
   estable; si es así, se puede quitar el segundo.
5. **Bitrate base para todo-intra a 800x480.** El valor por defecto (2,5 Mbps, ×1,6 en "Más nítida")
   viene de la negociación. Un registro de `[adaptive]` en un viaje real diría si la 800MT puede bajar
   a 1,8–2 Mbps sin que se note.

## Cómo comprobarlo en la moto

1. Haz una sesión de mirroring de 20 minutos en *Ahorro*, primero con la versión actual y después
   con esta rama. Compara el % de batería gastado y la temperatura (Ajustes → Batería).
2. En el informe de diagnóstico, las líneas `[adaptive] … fps=20` deben coincidir ahora con frames
   enviados a ese ritmo, no a 30.
3. En Android Auto, desplaza el mapa con los botones del manillar: el movimiento debería verse más
   continuo, sin saltos.

## Verificación

Los tests de `EncodedFramePacerTest` se han comprobado reproduciendo la misma lógica fuera de
Gradle. En este entorno no se podía compilar la app: el SDK de Android y Maven Central no estaban
accesibles. El CI del repositorio (`Android lint, test, and build`) debe pasar antes de integrar
la rama.
