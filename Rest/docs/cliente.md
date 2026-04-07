# Explicación de Cliente1.java

## Qué es esta clase

`Cliente1` es la interfaz de consola del sistema PBFT. No participa en el protocolo de consenso: su único papel es traducir los comandos del usuario en llamadas REST a los servicios y mostrar la respuesta de forma legible.

---

## Por qué es una clase de consola y no una interfaz gráfica o web

El enunciado pide "una interfaz sencilla con las siguientes opciones". Una consola es la opción más simple, más fácil de demostrar en la defensa (no requiere abrir navegador ni configurar nada extra) y ajusta bien al entorno Linux del laboratorio.

---

## El bucle principal y por qué no usa espera ocupada

```java
while (sc.hasNextLine()) {
    String linea = sc.nextLine().trim();
    ...
}
```

`sc.nextLine()` bloquea el hilo hasta que el usuario pulsa Enter. El proceso no consume CPU mientras espera. Si se hubiera usado `sc.ready()` en un `while (true)`, estaría comprobando continuamente si hay entrada, lo que sería espera ocupada prohibida por el enunciado.

---

## Cómo se parsean los comandos

```java
if (linea.startsWith("f") && linea.length() > 1 && esNumero(linea.substring(1)))
if (linea.startsWith("s") && linea.length() > 1 && esNumero(linea.substring(1)))
```

El orden de las comprobaciones importa: `s` (solo) tiene que verificarse antes de `sX` (con número) para que un `s` suelto no intente parsear una cadena vacía como entero.

La función `esNumero` comprueba dígito a dígito en lugar de usar un `try/catch` sobre `Integer.parseInt`. Es más limpio y no usa excepciones como control de flujo.

---

## toggleFallo — por qué llama a todos los servicios

```java
for (String url : Configuracion.URLS_SERVICIOS) {
    String resp = get(url + "/fallo?id=" + id);
    if (resp != null) { System.out.println(resp); encontrado = true; }
}
```

El cliente no sabe en qué máquina está el proceso N. En lugar de mantener un mapa "proceso → máquina" aquí, simplemente pregunta a todas. El Servicio que tiene ese proceso responde 200; los demás responden 404 y el método `get` devuelve `null` en ese caso, guardando silencio.

De esta forma añadir más máquinas al sistema solo requiere actualizar `Configuracion.URLS_SERVICIOS`, sin tocar el cliente.

---

## proponer — por qué después de proponer espera y no termina

```java
for (String url : Configuracion.URLS_SERVICIOS) {
    get(url + "/propuesta?v=" + v);
}
esperarConfirmacion();
```

Si el cliente se limitara a enviar la propuesta y mostrar el prompt `>` inmediatamente, el usuario no sabría si el consenso se llegó o no. `esperarConfirmacion` cierra ese ciclo: lanza la propuesta, espera la respuesta del sistema, e informa al usuario del resultado.

---

## esperarConfirmacion — por qué polling y no push

```java
long limite = System.currentTimeMillis() + 5000;
while (System.currentTimeMillis() < limite) {
    String resp = get(Configuracion.URL_CLIENTE + "/ultimaConfirmacion");
    if (resp != null && !resp.equals("-1")) {
        System.out.println("Consenso alcanzado. Valor aceptado: " + resp);
        return;
    }
    Thread.sleep(300);
}
```

La alternativa push requeriría que el cliente exponga un endpoint REST propio (que los procesos llamarían cuando confirman). Eso obligaría a tener otro servidor HTTP corriendo en la máquina del cliente, lo que complica el despliegue.

El polling con 300 ms de intervalo es suficiente para una práctica: el consenso tarda del orden de segundos (llamadas HTTP en LAN) y el usuario ve el resultado con menos de 300 ms de retraso.

El timeout de 5 segundos corresponde al escenario donde no hay quórum (demasiados procesos byzantinos). En ese caso el sistema no llegaría a consenso nunca, así que la espera infinita no tiene sentido.

El `Thread.sleep` con manejo de `InterruptedException` sigue las buenas prácticas: restaura el flag de interrupción con `Thread.currentThread().interrupt()` para no silenciar interrupciones del sistema.

---

## mostrarEstado — por qué la cabecera la pone el cliente

```java
System.out.println("id\tvar\tcompromisos\terror");
for (String url : Configuracion.URLS_SERVICIOS) {
    String resp = get(url + "/estado");
    if (resp != null) System.out.print(resp);
}
```

Cada servicio devuelve solo sus filas de datos (sin cabecera). El cliente imprime la cabecera una sola vez antes de pedir datos a todos los servicios. Si cada servicio devolviera su propia cabecera, la tabla aparecería con tres cabeceras intercaladas.

El formato de cada fila (id, var, compromisos, error separados por tabulador) coincide exactamente con la tabla del enunciado y viene del `toString()` de `Proceso`.

---

## El método get — por qué silencia los 404 del comando fallo

```java
if (!urlStr.contains("/fallo")) {
    System.err.println("[Cliente] Error al llamar " + urlStr + ": " + e.getMessage());
}
```

Cuando el cliente busca un proceso con `/fallo?id=N`, las dos máquinas que no tienen ese proceso responden 404. Esos 404 son comportamiento correcto, no errores. Mostrarlos al usuario generaría ruido innecesario y confusión durante la defensa.

Para cualquier otro endpoint, un error de red o de servidor sí es relevante y se muestra.

---

## esNumero — por qué no usar try/catch

```java
private static boolean esNumero(String s) {
    for (char c : s.toCharArray()) if (!Character.isDigit(c)) return false;
    return true;
}
```

Usar `try { Integer.parseInt(s); return true; } catch (NumberFormatException e) { return false; }` funciona, pero hace un uso indebido de las excepciones: las excepciones deben usarse para situaciones excepcionales, no para validar entrada del usuario. Este método es más claro en su intención y más eficiente.

---

## Por qué el cliente no instancia Proceso ni Servicio directamente

`Cliente1` no crea ni manipula objetos `Proceso` ni `Servicio1`. Toda comunicación va por HTTP. Esto corresponde exactamente al despliegue real: el cliente corre en una máquina y los procesos en otras. Aunque en pruebas locales todo corra en el mismo PC, la arquitectura es la del sistema distribuido real.
