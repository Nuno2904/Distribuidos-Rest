 La práctica va, en lo básico, de simular un grupo de procesos que tienen que ponerse de acuerdo sobre un mismo valor, aunque alguno de ellos se comporte mal.

El funcionamiento general es este: un cliente propone un valor, por ejemplo un número. Ese valor les llega a varios procesos. A partir de ahí, los procesos empiezan a comunicarse entre ellos para comprobar si la mayoría ha recibido y apoya el mismo valor. Si suficientes procesos coinciden, entonces aceptan ese valor como correcto. La idea principal de la práctica es demostrar que el sistema puede seguir llegando a un acuerdo aunque haya errores en algunos nodos.

Los procesos hacen tres cosas muy simples:
1. Reciben un valor propuesto.
2. Lo comunican a los demás procesos y escuchan lo que dicen los otros.
3. Deciden si hay suficiente acuerdo como para aceptar ese valor.

En la práctica hay dos tipos de procesos.

1. Procesos correctos.
Estos funcionan bien. Reciben el valor y lo comunican de forma normal al resto. Su objetivo es colaborar para que todos los procesos correctos acaben con la misma decisión.

2. Procesos con fallo bizantino.
Estos son los que “se portan mal”. En vez de enviar siempre la información correcta, pueden mandar valores aleatorios o distintos a cada proceso. No fallan porque se apaguen, sino porque pueden mentir o actuar de forma arbitraria. Esa es la gracia de la práctica: comprobar si el sistema sigue siendo capaz de llegar a un acuerdo cuando algunos nodos mandan información incorrecta.

La idea importante es esta: si hay pocos procesos erróneos y la mayoría funciona bien, los correctos deberían acabar decidiendo el mismo valor. Si hay demasiados procesos defectuosos, entonces ya no se puede garantizar el consenso y puede que no se llegue a ninguna decisión.

En resumen, la práctica trata de ver cómo un conjunto de procesos intenta alcanzar consenso en un sistema distribuido, distinguiendo entre procesos honestos y procesos bizantinos, y comprobando hasta qué punto el sistema aguanta errores sin romper el acuerdo.

Si quieres, te lo puedo reescribir todavía más simple, en formato “explicación para contar en clase en 30 segundos”.



cómo se decide cuántos procesos tienen fallos?

Se decide como parte de la simulación. No hay un número “mágico” que venga fijado por la práctica, sino que tú eliges cuántos procesos vas a marcar como defectuosos para probar qué ocurre.

La idea es jugar con distintos escenarios. Por ejemplo, si tienes 4 procesos, puedes probar:
1. `0` con fallo: todos funcionan bien y debería haber consenso sin problemas.
2. `1` con fallo: el sistema todavía debería poder llegar a un acuerdo si la mayoría sigue siendo correcta.
3. `2` con fallo: aquí ya puede romperse el consenso, porque hay demasiados procesos enviando información errónea.

En esta práctica, los procesos con fallo son los “bizantinos”, es decir, los que mandan valores aleatorios o inconsistentes. Normalmente se activan manualmente, cambiando el estado de un proceso para indicar que ahora falla.

La regla intuitiva es esta: mientras la mayoría de procesos sean correctos, el sistema suele poder decidir. Cuando los procesos con fallo dejan de ser minoría, ya no puedes confiar en que el acuerdo salga bien.


## Cómo funciona el consenso distribuido en PBFT

Tomando como referencia el esquema de la imagen, el algoritmo se entiende como una secuencia de 4 pasos muy simples.

### 1a. Propuesta

El cliente envía un valor a todos los procesos. Ese valor es el que se quiere acordar, por ejemplo cambiar la variable a `3`.

En esta fase todavía no hay consenso. Solo se está lanzando la propuesta inicial a todos los nodos.

### 1b. Compromiso

Cuando cada proceso recibe la propuesta, la reenvía a los demás procesos indicando con qué valor se compromete.

Aquí está una de las claves del PBFT de la práctica: los procesos ya no solo escuchan al cliente, sino también a todos los demás procesos.

Eso permite comparar mensajes y ver si la mayoría está apoyando el mismo valor.

Si un proceso es correcto, enviará el valor esperado.

Si un proceso tiene fallo bizantino, puede enviar valores falsos o distintos a cada nodo.

### 2a. Comisión

Cada proceso va contando los compromisos que recibe.

Si observa que una mayoría de procesos coincide en el mismo valor, entonces considera que ese valor ya tiene suficiente apoyo y da el siguiente paso: comete la actualización.

Eso significa que acepta ese valor como válido y lo comunica otra vez al resto de procesos.

Dicho de forma simple: en esta fase el proceso ya no dice solo “yo he recibido esto”, sino “yo ya considero que este valor es el correcto porque la mayoría lo respalda”.

### 2b. Confirmación

Cuando un proceso recibe comisiones de una mayoría, da la operación por buena y responde al cliente confirmando que el valor se ha aceptado.

El cliente no se fía de una sola respuesta. Solo considera que el consenso se ha alcanzado cuando recibe confirmaciones de una mayoría de procesos.

## Qué están haciendo realmente los procesos

Visto de forma muy básica, todos los procesos están haciendo siempre lo mismo:

1. Reciben un valor.
2. Lo comparan con lo que dicen los demás.
3. Comprueban si hay mayoría.
4. Si hay mayoría, lo aceptan.
5. Finalmente notifican al cliente que el acuerdo se ha logrado.

## Qué tipos de procesos hay en ese esquema

En el dibujo aparecen dos comportamientos posibles.

### Procesos correctos

Son los que colaboran bien en el consenso.

- reciben la propuesta
- reenvían el valor correcto
- cuentan lo que reciben
- aceptan el valor mayoritario
- confirman correctamente al cliente

### Procesos con fallo bizantino

Son los que pueden alterar el proceso enviando información incorrecta.

- pueden mandar valores aleatorios
- pueden mandar un valor a un nodo y otro distinto a otro
- pueden intentar romper el acuerdo general

La utilidad de la práctica está precisamente en comprobar si, aun existiendo alguno de esos procesos defectuosos, el resto puede seguir llegando a una decisión común.

## Idea clave del algoritmo

La idea central es que un proceso no decide porque se lo diga el cliente una vez, sino porque ve que muchos procesos coinciden en lo mismo.

Por eso el sistema resiste ciertos fallos: aunque algún nodo mienta, si la mayoría de nodos correctos sigue enviando el mismo valor, los demás pueden detectar cuál es el valor que realmente domina y decidir ese.

## Resumen muy corto

El cliente propone un valor, los procesos lo comentan entre sí, observan si hay una mayoría clara, y solo cuando esa mayoría existe se confirma la decisión. Los procesos correctos ayudan a alcanzar el acuerdo y los procesos bizantinos intentan distorsionarlo enviando mensajes erróneos.
