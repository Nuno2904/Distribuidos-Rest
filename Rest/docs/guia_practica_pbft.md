# Guía para completar la práctica de PBFT

## 1. Qué pide exactamente la práctica

La práctica consiste en implementar un sistema de consenso distribuido basado en PBFT.

El sistema debe tener dos tipos de actores:

- Un cliente, que propone periódicamente o bajo demanda un valor entero a un conjunto de procesos.
- Varios procesos oyentes, que participan en el consenso y que pueden comportarse correctamente o con fallos bizantinos.

El objetivo es que los procesos correctos lleguen a un acuerdo sobre el valor propuesto, incluso cuando uno o varios nodos envíen información incorrecta.

## 2. Qué condiciones debes cumplir

Según el enunciado, la solución debe cumplir estas propiedades:

- Terminación: cada proceso correcto debe acabar fijando una decisión.
- Acuerdo: todos los procesos correctos deben decidir el mismo valor.
- Integridad: si todos los procesos correctos han recibido el mismo valor, ese debe ser el valor decidido.
- No debe haber espera ocupada.
- No debe haber interbloqueos.
- No debe haber inanición.

Además, el enunciado insiste en varios criterios prácticos:

- No repetir código. La repetición se penaliza fuertemente.
- Mantener una arquitectura simple y clara.
- Minimizar el número de clases y de parámetros innecesarios.
- Facilitar el despliegue.
- Permitir cambiar número de nodos o máquinas sin rehacer el programa.

## 3. Cómo se interpreta el algoritmo en esta práctica

Aunque el nombre sea PBFT, el enunciado plantea una versión simplificada en dos fases principales entre procesos, más la confirmación al cliente:

1. Propuesta.
2. Compromiso.
3. Comisión.
4. Confirmación al cliente.

El flujo esperado es este:

1. El cliente propone un valor `v` a todos los procesos.
2. Cada proceso reinicia su estado interno para esa ronda y multidifunde un compromiso.
3. Si el proceso no tiene fallo, compromete el valor correcto `v`.
4. Si el proceso está en modo bizantino, envía valores aleatorios en la fase indicada por el enunciado.
5. Cada proceso recopila los compromisos recibidos.
6. Si detecta quórum para un valor, pasa a la fase de comisión para ese valor.
7. Cada proceso recopila las comisiones recibidas.
8. Si detecta quórum de comisiones, notifica confirmación al cliente.
9. El cliente considera completado el cambio cuando recibe confirmaciones de una mayoría.

## 4. Regla de quórum que debes usar

En los ejemplos del PDF, con 4 procesos el quórum es 3. Eso equivale a mayoría estricta.

En la práctica, lo razonable es usar:

- `quorum = floor(N / 2) + 1`

Con eso:

- Si `N = 4`, el quórum es 3.
- Si `N = 5`, el quórum es 3.
- Si `N = 6`, el quórum es 4.

Si quieres justificarlo en la defensa, puedes decir que el enunciado trabaja con la idea de mayoría y que sus ejemplos usan precisamente ese criterio.

## 5. Diseño recomendado para tu práctica

Con tu entorno Java 1.8 + Eclipse + Tomcat 9 + Jersey 2.39, la opción más clara es una aplicación web REST desplegada en Tomcat, con estas tres clases principales que además coinciden con el PDF:

- `Proceso`
- `Cliente`
- `Servicio`

Puedes añadir clases auxiliares si lo necesitas, pero sin complicar la arquitectura. Lo más razonable es añadir solo algunas estructuras de apoyo, por ejemplo:

- `Configuracion`: puertos, IPs, ids y tamaño del sistema.
- `EstadoRonda` o estructuras internas similares para guardar compromisos y comisiones.
- `RestClient` o utilidad para invocar endpoints REST de otros nodos.

## 6. Responsabilidad de cada clase

### 6.1. Proceso

Debe representar un nodo participante en el consenso.

Sus atributos mínimos, ajustados al enunciado, deberían ser:

- `id`: identificador único del proceso.
- `variable`: valor actual decidido, o un valor especial para indicar "no decidido".
- `error`: indica si el nodo actúa con fallo bizantino.
- `compromisos`: estructura para registrar compromisos recibidos.
- `comisiones`: estructura para registrar comisiones recibidas.

Además, en una implementación práctica conviene añadir:

- `valorPropuestoActual`
- `rondaActual`
- `confirmado` o alguna marca equivalente
- mecanismos de sincronización como `synchronized`, `ConcurrentHashMap` o colecciones seguras

Sus métodos clave deberían ser:

- `propuesta(v)`
- `compromiso(v)`
- `comision(v)`
- algún método de confirmación hacia el cliente
- utilidades para cambiar el modo de fallo y consultar estado

### 6.2. Cliente

Debe ofrecer una interfaz sencilla para:

- Cambiar el estado de fallo de un proceso.
- Proponer un nuevo valor.
- Consultar el estado del sistema.
- Mostrar ayuda.

El enunciado lo describe como una interfaz básica. Puedes implementarlo de dos formas válidas:

- Cliente por consola.
- Cliente REST más una pequeña interfaz o llamadas manuales desde navegador/Postman.

Para clase, lo más simple suele ser un cliente por consola que invoque servicios REST.

### 6.3. Servicio

Su función es exponer endpoints REST para acceder a los procesos que viven en una máquina.

Esto encaja muy bien con Jersey en Tomcat. El servicio puede:

- recibir propuestas del cliente
- recibir compromisos de otros procesos
- recibir comisiones
- devolver estados
- activar o desactivar fallos
- recibir confirmaciones o permitir que el cliente las consulte

## 7. Arquitectura recomendada en tu caso

La opción más sencilla de explicar y defender es esta:

- En cada máquina se despliega una aplicación web en Tomcat.
- Esa aplicación contiene un `Servicio` REST.
- El `Servicio` mantiene la lista de procesos locales de esa máquina.
- El cliente puede estar en una de las máquinas y comunicarse por HTTP con todas.

Si trabajas solo en un único ordenador para desarrollar, puedes simular varias máquinas de dos formas:

- Un Tomcat con varios procesos internos y un solo servicio.
- Varios servicios en distintos puertos.

Para una práctica de clase, la opción más manejable suele ser:

- un único despliegue web
- varios objetos `Proceso` dentro de memoria
- un `Servicio` que redirija cada llamada al proceso correspondiente

Esa solución simplifica el desarrollo, evita complejidad innecesaria y sigue respetando la idea del enunciado.

## 8. Endpoints REST mínimos que deberías tener

No tienen por qué llamarse exactamente así, pero una API mínima y clara sería:

- `POST /servicio/propuesta?idProceso=1&valor=33`
- `POST /servicio/compromiso?idProceso=1&valor=33&ronda=7&idEmisor=2`
- `POST /servicio/comision?idProceso=1&valor=33&ronda=7&idEmisor=2`
- `POST /servicio/fallo?idProceso=1&activo=true`
- `GET /servicio/estado`
- `GET /servicio/estado/{idProceso}`

Opcionalmente:

- `GET /servicio/ayuda`
- `POST /cliente/confirmacion`

## 9. Estructuras de datos recomendadas

Para evitar errores y repetición de código, no guardes compromisos ni comisiones solo en arrays sin estructura. En Java te conviene más algo como esto conceptualmente:

- mapa de valor a conjunto de procesos que han enviado ese valor
- mapa de ronda a estado de consenso

Por ejemplo:

- `Map<Integer, Set<Integer>> compromisosPorValor`
- `Map<Integer, Set<Integer>> comisionesPorValor`

Así puedes responder fácilmente a preguntas como:

- cuántos procesos han apoyado un valor
- si ya se alcanzó quórum
- si un emisor ya fue contado antes

Esto también te ayuda a evitar duplicados y a justificar mejor el diseño.

## 10. Cómo implementar los fallos bizantinos

El PDF indica que la simulación del fallo se hace en la fase 1b.

Eso significa:

- si `error == false`, el proceso reenvía el valor correcto
- si `error == true`, multidifunde valores aleatorios entre 0 y 100

Punto importante del enunciado:

- aunque multidifunda mal, el proceso puede comprometerse localmente bien con el valor propuesto por el cliente

También dice que, si prefieres, puedes hacer la variante inversa, pero debes documentarlo claramente.

La opción más fiel al PDF es esta:

- al recibir propuesta, el nodo guarda el valor correcto
- al enviar mensajes a otros nodos, si está en fallo, manda valores aleatorios
- al procesar internamente su propio compromiso, cuenta el valor correcto

## 11. Secuencia completa que debes programar

### Paso 1. Inicialización del sistema

Debes arrancar el servicio y crear todos los procesos configurados.

Cada proceso debe conocer:

- su identificador
- quiénes son los demás procesos
- cómo contactar con ellos
- su estado de fallo actual

### Paso 2. Propuesta del cliente

El cliente solicita cambiar el valor a `X`.

El sistema debe enviar esa propuesta a todos los procesos participantes.

Cuando un proceso recibe la propuesta:

- limpia compromisos previos
- limpia comisiones previas
- marca la ronda actual
- deja el valor como pendiente o no decidido temporalmente
- emite compromisos al resto

### Paso 3. Fase de compromiso

Cada proceso va recibiendo compromisos de otros procesos.

Por cada compromiso recibido:

- registra el valor y el emisor
- comprueba si algún valor ha alcanzado quórum

Si un valor alcanza quórum:

- el proceso cambia su variable a ese valor
- pasa a emitir comisión para ese valor

### Paso 4. Fase de comisión

Cada proceso registra las comisiones recibidas.

Cuando un valor alcanza quórum también en comisión:

- el proceso considera consolidada la decisión
- envía confirmación al cliente

### Paso 5. Confirmación al cliente

El cliente contabiliza confirmaciones.

Cuando obtiene quórum:

- informa al usuario de que el cambio se ha realizado correctamente

Si no llega a quórum tras un tiempo razonable:

- informa de que no ha habido consenso

## 12. Qué interfaz conviene hacer

El enunciado pide estas operaciones:

- `fN`: alternar el fallo del proceso `N`
- `sX`: proponer el valor `X`
- `s`: ver estado del sistema
- `h`: mostrar ayuda

La implementación más simple para clase es un programa cliente por consola que:

- lea comandos del usuario
- invoque el servicio REST correspondiente
- muestre resultados por pantalla en tabla

La tabla de estado debería mostrar, como mínimo:

- id
- valor actual
- compromisos recibidos
- estado de fallo

Puedes añadir también:

- comisiones recibidas
- ronda actual
- estado de decisión

## 13. Plan de trabajo recomendado para completar la práctica

Este es el orden más seguro para hacerla bien y sin bloquearte.

### Fase 1. Preparación del proyecto

1. Crear un Dynamic Web Project en Eclipse.
2. Asociarlo a Tomcat 9.
3. Añadir Jersey 2.39 al proyecto.
4. Configurar `web.xml` o inicialización equivalente para Jersey.
5. Probar un endpoint REST trivial para confirmar que Tomcat y Jersey funcionan.

### Fase 2. Modelo interno

1. Implementar la clase `Proceso` sin red todavía.
2. Implementar sus atributos y métodos básicos.
3. Probar localmente, con llamadas directas entre objetos, la lógica de propuesta, compromiso y comisión.
4. Verificar que con 4 nodos y 0 fallos todos deciden el mismo valor.

### Fase 3. Simulación de fallos

1. Añadir el atributo `error`.
2. Hacer que el nodo bizantino envíe valores aleatorios en compromiso.
3. Verificar que con 4 nodos y 1 fallo se sigue alcanzando consenso.
4. Verificar que con demasiados fallos no se alcanza consenso.

### Fase 4. Exposición REST

1. Crear la clase `Servicio` con endpoints para propuesta, compromiso, comisión, estado y fallo.
2. Hacer que el servicio localice el proceso destino y delegue en él.
3. Comprobar con peticiones manuales que cada endpoint funciona.

### Fase 5. Cliente

1. Implementar el cliente por consola.
2. Añadir los comandos `fN`, `sX`, `s` y `h`.
3. Hacer que el cliente muestre confirmación positiva o ausencia de consenso.

### Fase 6. Pruebas finales

1. Caso sin fallos.
2. Caso con un fallo bizantino.
3. Caso con más fallos de los tolerables.
4. Cambio de varios valores consecutivos.
5. Activación y desactivación de fallos en caliente.

### Fase 7. Preparación de entrega y defensa

1. Limpiar código repetido.
2. Revisar nombres, comentarios y estructura.
3. Comprobar que el despliegue es sencillo.
4. Preparar una explicación breve de la arquitectura.
5. Preparar una demo de 3 minutos con varios escenarios.

## 14. Qué debes demostrar en la defensa

Conviene que puedas explicar con claridad estas ideas:

- qué representa cada clase
- cómo se inicia una ronda
- cómo se calcula el quórum
- qué ocurre cuando un nodo está en fallo
- por qué con mayoría correcta sí hay consenso
- por qué con demasiados nodos bizantinos no lo hay
- cómo has evitado repetición de código
- cómo podrías escalar a más procesos o más máquinas

## 15. Posible desarrollo del enunciado, redactado de forma clara

Puedes entender el enunciado así:

"Se debe construir un sistema distribuido en el que un cliente proponga cambios de valor a un conjunto de nodos. Cada nodo participará en un protocolo de consenso inspirado en PBFT. El protocolo debe permitir que los nodos correctos alcancen un acuerdo común incluso si alguno de ellos actúa de forma arbitraria enviando datos erróneos. Para ello, cada propuesta iniciará una fase de compromiso, seguida de una fase de comisión, y finalmente una confirmación al cliente. El sistema debe permitir activar y desactivar fallos bizantinos, consultar el estado de los nodos y comprobar experimentalmente cuándo hay consenso y cuándo no lo hay." 

## 16. Recomendaciones concretas para tu stack

Como trabajas con Java 1.8, Eclipse 2018, Tomcat 9 y Jersey 2.39, te conviene:

- usar solo características compatibles con Java 8
- evitar dependencias modernas innecesarias
- mantener la serialización simple, preferiblemente JSON básico
- usar `ClientBuilder` de JAX-RS para las llamadas REST entre nodos o hacia el servicio
- centralizar la configuración de nodos en una sola clase o fichero

## 17. Errores típicos que debes evitar

- Hacer una implementación solo para 4 nodos con código copiado varias veces.
- No distinguir entre ronda actual y mensajes antiguos.
- Contar dos veces el mismo emisor.
- Cambiar la variable antes de tener quórum.
- Bloquear hilos esperando respuestas en bucle activo.
- Acoplar tanto el sistema a localhost que luego sea difícil moverlo a varias máquinas.
- No dejar claro en la defensa cómo se simulan los fallos bizantinos.

## 18. Estrategia práctica recomendada

Si quieres maximizar probabilidad de éxito, hazlo así:

1. Primero implementa todo en memoria, sin red.
2. Después añade REST manteniendo la lógica interna igual.
3. Luego añade el cliente por consola.
4. Finalmente prepara la demostración con varios escenarios.

Ese enfoque reduce errores y te permite depurar el consenso antes de introducir problemas de red o despliegue.

## 19. Qué deberías entregar

Según el PDF, la entrega debe incluir:

- `Proceso`
- `Cliente`
- `Servicio` si aplica en tu despliegue
- cualquier otra clase o script necesario para ejecutar la práctica

No hace falta incluir bibliotecas externas como Jersey.

## 20. Resumen final

Tu objetivo real no es solo que "funcione", sino demostrar estas tres cosas:

- que entiendes el flujo del consenso
- que has implementado correctamente la tolerancia a fallos bizantinos del enunciado
- que tu solución está bien diseñada, sin repetición de código y con despliegue razonable

Si quieres, el siguiente paso puede ser convertir esta guía en la estructura concreta del proyecto Java en Eclipse y dejarte ya creadas las clases `Proceso`, `Cliente` y `Servicio` con una base funcional.