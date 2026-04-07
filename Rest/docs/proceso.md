# Explicación de Proceso.java

## Qué es esta clase

`Proceso` representa un nodo participante en el consenso PBFT. Extiende `Thread` porque el enunciado lo exige explícitamente y porque tiene sentido: cada nodo tiene su propio ciclo de vida, espera mensajes y los procesa de forma independiente al resto.

---

## Por qué extiende Thread

El enunciado dice literalmente "clase que extiende de Thread". Pero hay una razón de fondo: el proceso necesita estar siempre escuchando y reaccionando a mensajes que le llegan de otros nodos. Si no fuera un hilo, habría que llamarlo desde fuera cada vez que llega algo, lo que complicaría la sincronización. Al ser un hilo, simplemente espera trabajo en su cola y lo procesa cuando llega.

---

## Atributos y por qué cada uno

### `idProceso`

Es el identificador lógico del nodo dentro del sistema PBFT. No es el mismo que el `getId()` que Java le da a todo `Thread`. Ese identificador interno de Java no tiene significado para el protocolo. El `idProceso` es el que usamos para saber si estamos hablando del proceso 1, 2, 3, etc.

Es `final` porque no tiene sentido cambiarlo una vez creado el proceso.

### `valorAceptado`

Guarda el valor sobre el que este proceso ha llegado a consenso. Empieza en `null` para indicar "no decidido todavía". Se actualiza en la fase de comisión, cuando el proceso detecta que hay mayoría de comisiones.

Es `volatile` porque puede ser leído desde el hilo del Servicio (para consultar el estado) mientras el hilo del proceso lo está modificando. `volatile` garantiza visibilidad entre hilos sin necesidad de un `synchronized` completo.

### `bizantino`

Indica si este proceso actúa con fallo bizantino. Cuando está a `true`, en la fase de compromiso envía un valor aleatorio en lugar del correcto. El enunciado pide esta simulación.

Es `volatile` por la misma razón que `valorAceptado`: puede cambiarse desde el hilo del Servicio (cuando el cliente pide activar/desactivar el fallo) mientras el hilo del proceso lo está leyendo.

### `activo`

Controla si el hilo debe seguir ejecutándose. Cuando se llama a `detenerProceso()`, se pone a `false` y se interrumpe el hilo. El `run()` comprueba este valor al salir de la espera bloqueante.

Sin este atributo, el hilo no podría detenerse de forma limpia.

### `compromisos: List<Integer>`

Lista de los valores de compromiso que este proceso ha recibido de los demás durante la ronda actual. Se acumulan todos para poder contar cuántas veces aparece cada valor y decidir si hay quórum.

El enunciado muestra exactamente esta estructura en la tabla de estado del cliente:
```
id  var  compromisos       error
1   6    6,6,6,32,1        true
```

Al guardar todos los valores (no solo los distintos), la tabla refleja exactamente lo que ha recibido el proceso, incluyendo duplicados y valores falsos de nodos bizantinos.

### `comisiones: List<Integer>`

Igual que `compromisos` pero para la fase de comisión. Se acumulan los valores de comisión recibidos para detectar el quórum de la segunda fase.

### `comisionEmitida` y `confirmacionEmitida`

Estas dos banderas evitan que el proceso emita más de una vez el mismo tipo de mensaje por ronda.

Sin ellas, si llegaran compromisos para el valor `v` desde varios procesos al mismo tiempo, podría ocurrir que el proceso detectara quórum varias veces y lanzara múltiples multicasts de comisión, contaminando la red con mensajes duplicados e inconsistentes.

### `colaTrabajo: BlockingQueue<String>`

Es la pieza central de la arquitectura de este hilo. El enunciado prohíbe explícitamente la espera ocupada. La `BlockingQueue` resuelve esto: el método `take()` bloquea el hilo sin consumir CPU hasta que haya algo en la cola.

Cuando llega un mensaje REST al Servicio, este llama a `propuesta()`, `compromiso()` o `comision()` en el proceso. Esos métodos no hacen el trabajo pesado (las llamadas HTTP): solo actualizan el estado interno y meten una tarea en la cola. El hilo del proceso la saca y la ejecuta después.

Esto evita que el hilo REST del servidor quede bloqueado haciendo llamadas HTTP a otros nodos.

### `random: Random`

Usado únicamente por los procesos bizantinos para generar valores aleatorios entre 0 y 100 en la fase de compromiso. Se crea en el constructor para no instanciarlo cada vez.

---

## Métodos PBFT y por qué son synchronized

Los tres métodos del protocolo (`propuesta`, `compromiso`, `comision`) están marcados como `synchronized`.

La razón es que estos métodos los pueden llamar varios hilos a la vez. Por ejemplo, si varios procesos remotos envían su compromiso casi al mismo tiempo, el servidor REST puede recibir esas peticiones en hilos paralelos, y todos intentarían llamar a `compromiso(v)` sobre el mismo objeto `Proceso`. Sin `synchronized`, dos hilos podrían leer `comisionEmitida == false` al mismo tiempo, pasar la comprobación ambos, y emitir comisión dos veces.

Con `synchronized`, solo un hilo a la vez puede estar dentro del método, garantizando que la comprobación del quórum y la actualización del estado son atómicas.

### `propuesta(v)`

Reinicia el estado de la ronda: vacía listas, resetea banderas, pone `valorAceptado` a `null`. Luego encola `MULTICAST_COMPROMISO:v` para que el hilo del proceso envíe el compromiso a todos.

El reinicio es necesario para que los mensajes de rondas anteriores no contaminen la ronda nueva.

### `compromiso(v)`

Añade el valor recibido a la lista de compromisos. Luego cuenta cuántas veces aparece ese valor. Si llega al quórum y aún no se ha emitido comisión, marca `comisionEmitida = true` y encola `MULTICAST_COMISION:v`.

La bandera `comisionEmitida` se marca antes de encolar para evitar condiciones de carrera: si dos hilos llegaran aquí simultáneamente con el mismo valor que completa el quórum, solo el primero que la lea como `false` y la cambie a `true` publicará la comisión.

### `comision(v)`

Igual que `compromiso` pero para la segunda fase. Cuando detecta quórum de comisiones, actualiza `valorAceptado` y encola `CONFIRMACION:v`.

---

## El hilo run() y por qué funciona así

```java
String trabajo = colaTrabajo.take(); // bloquea sin consumir CPU
String[] partes = trabajo.split(":", 2);
```

El formato de los mensajes en la cola es `TIPO:valor`, por ejemplo `MULTICAST_COMPROMISO:8`. El `split(":", 2)` divide en exactamente dos partes: tipo y valor. El `2` es importante porque evita que valores que contengan `:` se partan mal (aunque en este caso los valores son enteros y no lo necesitamos, es una buena práctica).

El `switch` delega en métodos privados para que el `run()` sea limpio y legible.

---

## Los métodos de envío HTTP y por qué se separan

### `procesarMulticastCompromiso(v)`

Aquí se hace la comprobación del fallo bizantino. El proceso lee `byzantine` y decide el valor a enviar. Si es bizantino, envía un aleatorio distinto a cada servicio (porque el siguiente `random.nextInt(101)` en el bucle genera un valor diferente para cada iteración).

El proceso no altera la fase de comisión aunque sea bizantino, siguiendo la decisión de diseño mencionada en el enunciado.

### `llamarEndpoint(String urlStr)`

Centraliza toda la lógica de conexión HTTP. Usa `HttpURLConnection` de la biblioteca estándar de Java (sin dependencias externas). El timeout viene de `Configuracion.TIMEOUT_MS` para que sea fácil de ajustar.

Los errores de red se imprimen pero no lanzan excepciones: si un nodo remoto no responde, el protocolo simplemente no recibe ese mensaje, lo que es exactamente la situación que PBFT está diseñado para tolerar.

---

## Cálculo del quórum

```java
private int quorum() {
    return Configuracion.TOTAL_PROCESOS / 2 + 1;
}
```

La mayoría estricta. Con 6 procesos el quórum es 4. Con 4 procesos el quórum es 3. Coincide exactamente con los ejemplos del PDF del enunciado.

Esta fórmula garantiza que si hay quórum para un valor, no puede haber quórum para otro valor diferente al mismo tiempo (dos mayorías estrictas no pueden coexistir). Esa es la propiedad que hace que el acuerdo sea único.

---

## toString() y por qué devuelve ese formato

```java
return idProceso + "\t" + (valorAceptado != null ? valorAceptado : "-") + "\t" + listaAString(compromisos) + "\t" + bizantino;
```

El enunciado especifica exactamente cómo debe verse la tabla de estado en el cliente:

```
id  var  compromisos       error
1   6    6,6,6,32,1        true
```

El `toString()` de cada proceso produce una fila de esa tabla. El cliente solo tiene que imprimir la cabecera y luego llamar a `toString()` de cada proceso. Separar con tabuladores facilita que se alinee bien en consola.

El `synchronized` en `toString()` es necesario porque el cliente puede pedir el estado mientras el hilo del proceso está modificando la lista de compromisos.
