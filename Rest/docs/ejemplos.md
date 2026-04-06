# Ejemplos de simulación de PBFT

## 1. Idea general

En todos los ejemplos vamos a usar 4 procesos:

- `P1`
- `P2`
- `P3`
- `P4`

Y un cliente que quiere proponer un valor.

Tomaremos como valor propuesto `8`.

Con 4 procesos, el quórum es `3`. Eso significa que para aceptar un valor hacen falta 3 apoyos coincidiendo.

---

## 2. Ejemplo 1: todos correctos

### Situación inicial

- `P1` correcto
- `P2` correcto
- `P3` correcto
- `P4` correcto

El cliente propone el valor `8`.

### Fase 1a. Propuesta

Todos reciben el valor `8`.

### Fase 1b. Compromiso

Todos los procesos reenvían `8` a los demás.

### Qué ve cada proceso

| Proceso | Recibe compromisos | Decide | Envía después |
|---|---|---|---|
| `P1` | `8, 8, 8, 8` | hay mayoría para `8` | comisión de `8` |
| `P2` | `8, 8, 8, 8` | hay mayoría para `8` | comisión de `8` |
| `P3` | `8, 8, 8, 8` | hay mayoría para `8` | comisión de `8` |
| `P4` | `8, 8, 8, 8` | hay mayoría para `8` | comisión de `8` |

### Fase 2a. Comisión

Todos reciben comisiones para `8`.

### Fase 2b. Confirmación al cliente

Como todos ven mayoría de comisiones, los cuatro envían confirmación al cliente.

### Resultado

Todos aceptan `8` y el cliente recibe confirmaciones suficientes. Hay consenso sin ninguna dificultad.

---

## 3. Ejemplo 2: un solo proceso bizantino

### Situación inicial

- `P1` correcto
- `P2` correcto
- `P3` correcto
- `P4` bizantino

El cliente propone el valor `8`.

### Fase 1a. Propuesta

Todos arrancan la ronda con la propuesta `8`.

### Fase 1b. Compromiso

Los procesos correctos envían `8`.

`P4`, como es bizantino, envía valores falsos y distintos:

- a `P1` le manda `20`
- a `P2` le manda `3`
- a `P3` le manda `99`

### Qué ve cada proceso

| Proceso | Recibe compromisos | Decide | Envía después |
|---|---|---|---|
| `P1` | `8, 8, 8, 20` | mayoría para `8` | comisión de `8` |
| `P2` | `8, 8, 8, 3` | mayoría para `8` | comisión de `8` |
| `P3` | `8, 8, 8, 99` | mayoría para `8` | comisión de `8` |
| `P4` | puede ver cualquier combinación | su comportamiento no es fiable | puede mandar cualquier cosa |

### Qué hace exactamente cada proceso

`P1`
- recibe la propuesta `8`
- observa 3 compromisos válidos para `8`
- decide que el valor correcto es `8`
- envía comisión de `8`
- cuando ve mayoría de comisiones, confirma al cliente

`P2`
- hace lo mismo que `P1`

`P3`
- hace lo mismo que `P1`

`P4`
- participa, pero enviando mensajes erróneos
- no consigue romper el consenso porque está en minoría

### Cuándo envían mensajes al cliente

No envían mensaje al cliente en cuanto reciben la propuesta.

Tampoco envían mensaje al cliente en cuanto ven compromisos.

Solo envían mensaje al cliente cuando ya han recibido quórum de comisiones. Es decir, cuando el acuerdo está suficientemente reforzado.

Por eso, en este ejemplo:

- `P1` confirma al cliente
- `P2` confirma al cliente
- `P3` confirma al cliente
- `P4` puede no confirmar, o hacerlo de forma incoherente

### Resultado

Aunque `P4` mienta, `P1`, `P2` y `P3` forman mayoría. Por tanto, el valor `8` sale adelante y el cliente entiende que hay consenso.

---

## 4. Ejemplo 3: qué pasa exactamente con `P4`

En el ejemplo anterior, `P4` no tiene por qué ser el que “mande” nada al cliente para que el sistema funcione. Su papel es el de un nodo más dentro del grupo de procesos.

Si `P4` es correcto:

- recibe la propuesta
- manda compromiso correcto
- ve mayoría
- manda comisión
- confirma al cliente

Si `P4` es bizantino:

- recibe la propuesta
- manda mensajes falsos a otros procesos
- puede mandar valores distintos a cada uno
- puede no ayudar a formar mayoría
- puede no confirmar al cliente o confirmar algo inconsistente

La razón por la que no rompe el sistema es que los demás procesos correctos sí coinciden entre ellos.

---

## 5. Ejemplo 4: dos procesos bizantinos

### Situación inicial

- `P1` correcto
- `P2` correcto
- `P3` bizantino
- `P4` bizantino

El cliente propone otra vez el valor `8`.

### Fase 1a. Propuesta

Todos arrancan la ronda.

### Fase 1b. Compromiso

Los correctos mandan:

- `P1` manda `8`
- `P2` manda `8`

Los bizantinos meten ruido:

- `P3` puede mandar `2` a un proceso y `50` a otro
- `P4` puede mandar `7` a un proceso y `31` a otro

### Qué ve cada proceso correcto

| Proceso | Recibe compromisos | Decide | Envía después |
|---|---|---|---|
| `P1` | `8, 8, 2, 31` | no hay mayoría de `3` | no envía comisión válida |
| `P2` | `8, 8, 50, 7` | no hay mayoría de `3` | no envía comisión válida |
| `P3` | comportamiento no fiable | no se puede usar como referencia | cualquier cosa |
| `P4` | comportamiento no fiable | no se puede usar como referencia | cualquier cosa |

### Qué ocurre entonces

Como ni `P1` ni `P2` llegan a ver 3 apoyos iguales para el valor `8`, no pueden pasar correctamente a la fase de comisión.

Si no hay comisión válida de una mayoría, tampoco habrá confirmación suficiente al cliente.

### Resultado

El cliente no recibe confirmaciones suficientes.

Por tanto:

- no se acepta el valor `8`
- no hay consenso

Esto es lo esperado. Con 2 bizantinos de 4, los procesos correctos ya no son mayoría suficiente.

---

## 6. Resumen visual rápido

### Caso A: 1 bizantino

| Proceso | Tipo | Mensaje en compromiso | ¿Hay mayoría para `8`? | ¿Confirma al cliente? |
|---|---|---|---|---|
| `P1` | correcto | `8` | sí | sí |
| `P2` | correcto | `8` | sí | sí |
| `P3` | correcto | `8` | sí | sí |
| `P4` | bizantino | falso o aleatorio | no importa para la mayoría | no necesariamente |

Resultado: sí hay consenso.

### Caso B: 2 bizantinos

| Proceso | Tipo | Mensaje en compromiso | ¿Hay mayoría para `8`? | ¿Confirma al cliente? |
|---|---|---|---|---|
| `P1` | correcto | `8` | no | no |
| `P2` | correcto | `8` | no | no |
| `P3` | bizantino | falso o aleatorio | no fiable | no fiable |
| `P4` | bizantino | falso o aleatorio | no fiable | no fiable |

Resultado: no hay consenso.

---

## 7. Idea final

Los procesos no avisan al cliente nada más empezar. Primero tienen que ver suficiente apoyo entre ellos.

El orden correcto es:

1. recibir propuesta
2. intercambiar compromisos
3. detectar mayoría
4. enviar comisión
5. recibir mayoría de comisiones
6. confirmar al cliente

Por eso, el cliente solo recibe mensajes al final, cuando ya hay base suficiente para afirmar que el consenso se ha alcanzado.