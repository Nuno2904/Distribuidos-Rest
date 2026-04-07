# Explicación de Servicio1.java

## Qué es esta clase

`Servicio1` es el recurso REST que cada máquina expone en Tomcat. Es la puerta de entrada del sistema: recibe peticiones HTTP de otros servicios y del cliente, y las traduce en llamadas a los métodos PBFT de los procesos locales.

---

## Por qué no extiende Thread ni implementa nada especial

`Servicio1` no es un actor del protocolo, no toma decisiones de consenso y no tiene estado propio que evolucione con el tiempo. Solo enruta mensajes. Por eso es un recurso REST puro (`@Path("/servicio")`), sin herencia ni lógica de hilo.

---

## Por qué los campos son static

```java
private static final Map<Integer, Proceso> procesos = new LinkedHashMap<>();
private static final AtomicInteger confirmacionesRonda = new AtomicInteger(0);
private static volatile int valorUltimaRonda = -1;
```

Jersey, por defecto, crea una nueva instancia de la clase recurso por cada petición HTTP. Sin `static`, cada petición vería un mapa de procesos diferente y vacío.

Al declarar los campos como `static`, son compartidos entre todas las instancias que Jersey cree. Los procesos se crean una sola vez (en el bloque `static { }`) y sobreviven durante toda la vida del servidor.

---

## Por qué se usa LinkedHashMap en vez de HashMap

`LinkedHashMap` mantiene los procesos en orden de inserción, que coincide con el orden de IDs. Así la tabla que devuelve `/estado` siempre muestra los procesos en orden (P1, P2, etc.), lo que facilita la lectura durante la defensa.

Con `HashMap` el orden sería arbitrario y podría cambiar entre llamadas.

---

## El bloque static y por qué ID_INICIO

```java
static {
    for (int i = 0; i < Configuracion.PROCESOS_POR_MAQUINA; i++) {
        int id = Configuracion.ID_INICIO + i;
        Proceso p = new Proceso(id);
        procesos.put(id, p);
        p.start();
    }
}
```

Cada máquina necesita procesos con IDs distintos. Si las tres máquinas crearan procesos con IDs 1 y 2, el comando `f3` (toggle proceso 3) nunca encontraría nada.

`ID_INICIO` se lee de una propiedad del sistema (`-Did.inicio=N`) que se pasa al arrancar Tomcat:
- Máquina 1: `-Did.inicio=1` → crea P1, P2
- Máquina 2: `-Did.inicio=3` → crea P3, P4
- Máquina 3: `-Did.inicio=5` → crea P5, P6

De este modo el código compilado es idéntico en las tres máquinas y solo cambia el parámetro de arranque.

---

## Endpoints y su papel en PBFT

### `/propuesta?v=X` — Fase 1a

```java
confirmacionesRonda.set(0);
valorUltimaRonda = -1;
for (Proceso p : procesos.values()) p.propuesta(v);
```

Cuando el cliente quiere proponer un nuevo valor, llama a este endpoint en todos los servicios. Antes de iniciar la ronda se resetean el contador de confirmaciones y el valor del consenso anterior, para que datos de rondas pasadas no contaminen la nueva.

Luego llama a `propuesta(v)` en todos los procesos locales, que a su vez enolan el multicast de compromiso.

### `/compromiso?valor=X` — Fase 1b

Recibe el compromiso que un proceso remoto multidifunde a todas las máquinas. Lo aplica a todos los procesos locales. Cada proceso actualiza su contador interno y, si detecta quórum, encola el multicast de comisión.

No hay que indicar "¿de qué proceso viene?": el protocolo no lo necesita. Cada proceso solo cuenta cuántas veces aparece cada valor, sin importar quién lo mandó.

### `/comision?valor=X` — Fase 2a

Igual que compromiso pero para la segunda fase. Los procesos locales acumulan comisiones y, si hay quórum, enolan la confirmación al cliente.

### `/confirmacion?valor=X` — Fase 2b

```java
int total = confirmacionesRonda.incrementAndGet();
if (total >= quorum()) {
    valorUltimaRonda = valor;
}
```

Solo recibe peticiones el servicio que está en la misma máquina que el cliente (`URL_CLIENTE`). Los procesos, cuando llegan a quórum de comisiones, llaman a este endpoint.

`AtomicInteger` es necesario porque varias confirmaciones pueden llegar al mismo tiempo desde hilos de proceso distintos. Si se usara un `int` normal, dos hilos podrían leer el mismo valor, incrementarlo ambos a N+1 en vez de N+2, y nunca llegar al quórum aunque hayan llegado los mensajes suficientes.

`volatile` en `valorUltimaRonda` garantiza que cuando el cliente consulta `/ultimaConfirmacion` (desde otro hilo/petición), ve el valor que escribió el hilo de confirmación, no un valor cacheado.

### `/estado` — Tabla de estado

```java
@Produces(MediaType.TEXT_PLAIN)
```

Devuelve las filas de la tabla sin cabecera (la cabecera la pone el cliente). El formato de cada fila viene del `toString()` de `Proceso`.

`@Produces(MediaType.TEXT_PLAIN)` le dice a Jersey que responda con `Content-Type: text/plain`, evitando que intente serializar el String como JSON u otro formato por defecto.

### `/fallo?id=N` — Toggle byzantino

```java
Proceso p = procesos.get(id);
if (p == null) return Response.status(Response.Status.NOT_FOUND).build();
```

Si el proceso no está en esta máquina devuelve 404. El cliente llama a todos los servicios y solo el que tenga ese proceso responde con 200. Los 404 son parte del diseño, no errores.

### `/ultimaConfirmacion` — Sondeo del cliente

Permite al cliente preguntar "¿ya hay consenso?" sin tener que implementar un sistema de notificación push. El cliente hace polling cada 300 ms durante 5 segundos hasta que el valor deja de ser `-1`.

Es una solución deliberadamente simple: no hay callbacks, no hay websockets, no hay colas de eventos. Para una práctica con un escenario controlado es suficiente.

---

## Por qué el quórum se calcula igual que en Proceso

```java
private int quorum() {
    return Configuracion.TOTAL_PROCESOS / 2 + 1;
}
```

El Servicio necesita el quórum para saber cuántas confirmaciones son suficientes. Es la misma fórmula que en `Proceso` y en `Configuracion`. Podría moverse a `Configuracion` como constante, pero al ser un único método pequeño es más claro mantenerlo local a quien lo usa.
