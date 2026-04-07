import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

/**
 * Interfaz de consola para interactuar con el sistema PBFT.
 *
 * Comandos:
 *   fN   Alternar fallo byzantino del proceso N      (ej: f2)
 *   sX   Proponer el valor X como nuevo consenso     (ej: s42)
 *   s    Mostrar tabla de estado de todos los nodos
 *   h    Mostrar ayuda
 *   q    Salir
 *
 * El cliente no toca los procesos directamente: toda comunicacion pasa
 * por los endpoints REST de los Servicios.
 */
public class Cliente1 {

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        mostrarAyuda();
        System.out.print("> ");
        while (sc.hasNextLine()) {
            String linea = sc.nextLine().trim();
            if (linea.isEmpty()) {
                System.out.print("> ");
                continue;
            }
            procesarComando(linea);
            System.out.print("> ");
        }
    }

    // -------------------------------------------------------------------------
    // Procesamiento de comandos
    // -------------------------------------------------------------------------

    private static void procesarComando(String linea) {
        if (linea.equals("h")) {
            mostrarAyuda();
        } else if (linea.equals("s")) {
            mostrarEstado();
        } else if (linea.equals("q")) {
            System.out.println("Saliendo...");
            System.exit(0);
        } else if (linea.startsWith("f") && linea.length() > 1 && esNumero(linea.substring(1))) {
            toggleFallo(Integer.parseInt(linea.substring(1)));
        } else if (linea.startsWith("s") && linea.length() > 1 && esNumero(linea.substring(1))) {
            proponer(Integer.parseInt(linea.substring(1)));
        } else {
            System.out.println("Comando no reconocido. Escribe 'h' para ver la ayuda.");
        }
    }

    // -------------------------------------------------------------------------
    // Comandos
    // -------------------------------------------------------------------------

    /**
     * fN: alterna el estado byzantino del proceso N.
     * Llama a /fallo?id=N en todos los servicios. Solo el que tenga el proceso
     * responde con 200; los demas devuelven 404 y se ignoran silenciosamente.
     */
    private static void toggleFallo(int id) {
        boolean encontrado = false;
        for (String url : Configuracion.URLS_SERVICIOS) {
            String resp = get(url + "/fallo?id=" + id);
            if (resp != null) {
                System.out.println(resp);
                encontrado = true;
            }
        }
        if (!encontrado) {
            System.out.println("Proceso " + id + " no encontrado en ningun servicio.");
        }
    }

    /**
     * sX: propone el valor X a todos los servicios e inicia el protocolo PBFT.
     * Despues espera hasta 5 segundos a que el servicio local confirme el consenso.
     */
    private static void proponer(int v) {
        System.out.println("Proponiendo valor " + v + " ...");
        for (String url : Configuracion.URLS_SERVICIOS) {
            get(url + "/propuesta?v=" + v);
        }
        esperarConfirmacion();
    }

    /**
     * s: solicita el estado a cada servicio y muestra la tabla combinada.
     * La cabecera la imprime el cliente; cada servicio devuelve sus filas de datos.
     */
    private static void mostrarEstado() {
        System.out.println("id\tvar\tcompromisos\terror");
        System.out.println("--\t---\t-----------\t-----");
        for (String url : Configuracion.URLS_SERVICIOS) {
            String resp = get(url + "/estado");
            if (resp != null && !resp.isEmpty()) {
                System.out.print(resp);
                if (!resp.endsWith("\n")) System.out.println();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Espera de confirmacion
    // -------------------------------------------------------------------------

    /**
     * Consulta /ultimaConfirmacion en el servicio local cada 300 ms durante 5 segundos.
     * Cuando el valor deja de ser -1, el servicio local ha acumulado quorum de
     * confirmaciones y el consenso esta completo.
     */
    private static void esperarConfirmacion() {
        long limite = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < limite) {
            String resp = get(Configuracion.URL_CLIENTE + "/ultimaConfirmacion");
            if (resp != null && !resp.equals("-1")) {
                System.out.println("Consenso alcanzado. Valor aceptado: " + resp);
                return;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("Sin confirmacion en el tiempo esperado "
                + "(puede que no haya quorum suficiente).");
    }

    // -------------------------------------------------------------------------
    // Ayuda
    // -------------------------------------------------------------------------

    private static void mostrarAyuda() {
        System.out.println("=== Cliente PBFT ===");
        System.out.println("  fN   Alternar fallo del proceso N   (ej: f2)");
        System.out.println("  sX   Proponer el valor X            (ej: s42)");
        System.out.println("  s    Mostrar estado del sistema");
        System.out.println("  h    Mostrar esta ayuda");
        System.out.println("  q    Salir");
    }

    // -------------------------------------------------------------------------
    // HTTP GET
    // -------------------------------------------------------------------------

    /**
     * Realiza un GET al endpoint indicado y devuelve el cuerpo como String.
     * Devuelve null si el servidor responde con error o si hay fallo de red.
     * Los errores 404 (proceso no encontrado en ese servicio) son silenciosos.
     */
    private static String get(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(Configuracion.TIMEOUT_MS);
            conn.setReadTimeout(Configuracion.TIMEOUT_MS);
            int code = conn.getResponseCode();
            if (code == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String l;
                    while ((l = br.readLine()) != null) sb.append(l).append("\n");
                }
                conn.disconnect();
                return sb.toString().trim();
            }
            conn.disconnect();
        } catch (IOException e) {
            // Solo mostrar si no es un 404 esperado (fallo?id en maquina equivocada)
            if (!urlStr.contains("/fallo")) {
                System.err.println("[Cliente] Error al llamar " + urlStr + ": " + e.getMessage());
            }
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private static boolean esNumero(String s) {
        if (s == null || s.isEmpty()) return false;
        for (char c : s.toCharArray()) if (!Character.isDigit(c)) return false;
        return true;
    }
}
