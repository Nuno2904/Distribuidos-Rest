## Progreso del proyecto PBFT

### 1. Objetivo del trabajo

El objetivo es construir una práctica de consenso distribuido inspirada en PBFT con tres clases principales:

- `Proceso`
- `Servicio`
- `Cliente`

La idea de despliegue que estamos siguiendo es esta:

- En cada máquina corre un `Servicio`.
- Cada `Servicio` aloja varios objetos `Proceso`.
- El `Cliente` puede ejecutarse en cualquiera de las máquinas.
- El consenso lo realizan los `Proceso`, no las máquinas como tal.

Esto permite repartir los procesos entre varias máquinas sin rehacer el programa entero.

### 2. Decisiones importantes que ya hemos tomado

#### 2.1. `Proceso` extiende de `Thread`

Esto no ha sido una elección libre de diseño, sino una adaptación al enunciado. Si el enunciado fija que `Proceso` es una clase que hereda de `Thread`, entonces cada proceso es un hilo con vida propia.

Consecuencia directa:

- un `Proceso` tiene su propio ciclo de ejecución en `run()`
- puede esperar trabajo
- puede procesar mensajes o tareas de manera concurrente
- puede detenerse de forma controlada

#### 2.2. `idProceso` no es lo mismo que `Thread.getId()`

Como `Proceso` hereda de `Thread`, Java ya le da un identificador interno de hilo con `getId()`. Ese identificador no es el que nos interesa para el protocolo.

Por eso usamos un atributo propio:

- `idProceso`: identificador lógico del nodo PBFT

Ese identificador es el que se usará para hablar de proceso 1, proceso 2, proceso 3, etc.

#### 2.3. Se mantiene `Servicio`

`Servicio` no sustituye a `Proceso`. Su función es:

- crear procesos locales
- arrancarlos
- localizarlos por id
- enviarles trabajo
- detenerlos
- actuar más adelante como puerta de entrada de las peticiones de red

Separación de responsabilidades:

- `Cliente` pide acciones
- `Servicio` coordina y expone acceso
- `Proceso` ejecuta la lógica del nodo

### 3. Estado actual de la clase `Proceso`

Ahora mismo `Proceso` ya tiene una base concurrente válida:

- `idProceso`: id lógico del proceso
- `valorAceptado`: valor decidido o aceptado por el proceso
- `bizantino`: indica si el proceso se comporta mal
- `activo`: indica si el hilo debe seguir ejecutándose
- `colaTrabajo`: cola bloqueante para trabajos pendientes

También tiene:

- constructor
- getters básicos
- setters básicos
- `agregarTrabajo(String trabajo)`
- `detenerProceso()`
- `run()`
- `toString()`

#### 3.1. Qué hace ahora `run()`

La idea actual del `run()` es que el hilo espere a recibir trabajo y lo procese en orden.

Todavía no estamos procesando mensajes PBFT reales. Por ahora el objetivo es tener el mecanismo de ejecución concurrente funcionando antes de meter lógica del protocolo.

### 4. Explicación clara de las `BlockingQueue`

#### 4.1. Qué es una `BlockingQueue`

Una `BlockingQueue` es una cola preparada para trabajo concurrente entre hilos. Sirve para que un hilo productor meta tareas y otro hilo consumidor las saque.

En nuestro caso:

- `Servicio` u otra parte del programa mete trabajo en la cola del proceso
- el hilo `Proceso` saca el trabajo de esa cola en su `run()`

#### 4.2. Por qué se usa aquí

El enunciado dice explícitamente que no debe haber:

- espera ocupada
- interbloqueos
- inanición

Una `BlockingQueue` ayuda especialmente con lo primero.

Si el hilo hace esto:

```java
while (activo) {
     String trabajo = colaTrabajo.take();
     // procesar trabajo
}
```

entonces `take()` deja al hilo bloqueado mientras no haya nada en la cola. Eso significa que el hilo no está consumiendo CPU constantemente preguntando si ya llegó trabajo.

Eso evita la espera ocupada.

#### 4.3. Diferencia con espera ocupada

Esto estaría mal:

```java
while (activo) {
     if (!colaTrabajo.isEmpty()) {
          String trabajo = colaTrabajo.poll();
     }
}
```

Ese código está revisando la cola sin parar. Aunque no haya trabajo, el hilo sigue ejecutándose continuamente y consumiendo CPU.

Con `BlockingQueue`, en cambio, el hilo se duerme hasta que haya algo que hacer.

#### 4.4. Operaciones que nos interesan

- `offer(...)`: añade un elemento a la cola
- `take()`: saca un elemento; si no hay ninguno, espera de forma bloqueante

En esta práctica, de momento estamos usando la cola con `String` para representar trabajo pendiente. Más adelante lo correcto será sustituir ese `String` por un tipo de mensaje más claro.

#### 4.5. Qué ventaja tiene frente a `wait()` y `notify()`

También se podría hacer con `wait()` y `notify()`, pero `BlockingQueue` tiene varias ventajas:

- el código es más simple
- hay menos riesgo de errores de sincronización
- es más fácil evitar espera ocupada
- es más fácil de explicar y mantener

Por eso hemos decidido quedarnos con `BlockingQueue`.

### 5. Estado actual de la clase `Servicio`

`Servicio` ya se ha orientado a gestionar procesos-hilo.

Responsabilidades actuales:

- crear los procesos locales
- arrancarlos con `start()`
- devolver un proceso concreto por id
- marcar un proceso como bizantino o no
- enviar trabajo a un proceso concreto
- enviar trabajo a todos los procesos
- detener todos los procesos
- obtener una vista del estado de todos ellos

Esto deja a `Servicio` como el gestor local de los procesos de una máquina.

### 6. Qué hemos hecho y por qué

#### 6.1. Primera fase: base de `Proceso`

Se creó `Proceso` con sus atributos básicos porque necesitábamos primero representar el nodo lógico del consenso.

Decisiones tomadas:

- no existe `setId`, porque el identificador del proceso debe ser estable
- `valorAceptado` se dejó como `Integer` para poder representar que todavía no hay valor decidido
- `bizantino` se dejó como `boolean` porque solo puede ser verdadero o falso

#### 6.2. Segunda fase: adaptación a hilos

Se adaptó `Proceso` para que heredase de `Thread` porque el enunciado lo exige. Esto obligó a separar:

- el id lógico del proceso
- el id interno del hilo Java

#### 6.3. Tercera fase: trabajo pendiente bloqueante

Se añadió una cola bloqueante porque el hilo necesita esperar trabajo sin hacer espera ocupada.

#### 6.4. Cuarta fase: gestor local con `Servicio`

Se mantuvo `Servicio` porque necesitamos una clase que administre todos los procesos de una máquina. Sin `Servicio`, la lógica de arranque, parada y envío de trabajo quedaría mal repartida entre `Cliente` y `Proceso`.

### 7. Hacia dónde vamos ahora

Todavía no está implementado el protocolo PBFT. Lo que sí tenemos es la infraestructura mínima para hacerlo de forma limpia.

Los siguientes pasos recomendados son:

1. Sustituir la cola de `String` por un mensaje más estructurado.
    Ejemplo: propuesta, compromiso, comisión, parada.

2. Hacer que `Proceso.run()` procese tipos de trabajo reales.
    Ahora mismo solo procesa trabajo genérico. Debe pasar a entender mensajes del protocolo.

3. Añadir en `Proceso` el estado necesario para PBFT.
    Ejemplo:
    - ronda actual
    - propuesta actual
    - compromisos recibidos
    - comisiones recibidas

4. Hacer que `Servicio` entregue mensajes PBFT a los procesos correctos.

5. Construir `Cliente` para que pueda:
    - proponer valores
    - marcar procesos como bizantinos
    - consultar estado

6. Después, adaptar la comunicación entre máquinas.
    En ese momento `Servicio` será la fachada para exponer operaciones por red.

### 8. Cómo debe seguir trabajando el compañero que lea este documento

Si otra persona retoma el proyecto, no debería empezar escribiendo PBFT completo de golpe. El orden correcto de trabajo es este:

1. Entender bien la separación entre `Cliente`, `Servicio` y `Proceso`.

2. Verificar que `Proceso` ya es un hilo y que recibe trabajo mediante cola bloqueante.

3. No romper esa decisión salvo que haya un motivo muy claro.
    La cola bloqueante es la base para cumplir el requisito de no hacer espera ocupada.

4. El siguiente desarrollo debe centrarse en definir un tipo de mensaje o tarea mejor que `String`.

5. Solo después meter la lógica PBFT por fases.
    Primero propuesta, luego compromiso, luego comisión, luego confirmación.

6. Mantener la responsabilidad de cada clase clara.
    - `Proceso` procesa
    - `Servicio` coordina
    - `Cliente` solicita acciones

### 9. Resumen corto

El proyecto ya tiene una base correcta para crecer:

- `Proceso` es un hilo
- `Proceso` puede esperar trabajo sin espera ocupada
- `Servicio` gestiona el conjunto de procesos locales
- falta todavía implementar el protocolo PBFT real y el cliente de control

El punto técnico más importante que ya está decidido es el uso de `BlockingQueue`, porque simplifica la concurrencia y ayuda a cumplir los requisitos del enunciado.