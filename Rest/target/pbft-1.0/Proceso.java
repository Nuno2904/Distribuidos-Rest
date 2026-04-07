import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Nodo participante en el consenso PBFT.
 * Extiende Thread: cada proceso es un hilo con su propio ciclo de vida.
 *
 * Ciclo del protocolo que gestiona este proceso:
 *   1. propuesta(v)   -> reinicia estado, programa multicast de compromiso
 *   2. compromiso(v)  -> acumula compromisos; si hay quorum, programa multicast de comision
 *   3. comision(v)    -> acumula comisiones; si hay quorum, acepta valor y notifica al cliente
 *
 * Las llamadas HTTP salientes (multicast) se encolan en colaTrabajo y las
 * ejecuta el hilo del propio proceso, evitando espera ocupada y bloqueos.
 */
public class Proceso extends Thread {

    // --- Atributos de identidad y estado ---
    private final int idProceso;
    private volatile Integer valorAceptado;  // null = no decidido
    private volatile boolean bizantino;
    private volatile boolean activo;

    // --- Estado PBFT de la ronda actual ---
    private final List<Integer> compromisos;  // valores de compromiso recibidos
    private final List<Integer> comisiones;   // valores de comision recibidos
    private boolean comisionEmitida;          // evita emitir comision mas de una vez por ronda
    private boolean confirmacionEmitida;      // evita confirmar mas de una vez por ronda

    // --- Mecanismo de trabajo asincrono ---
    private final BlockingQueue<String> colaTrabajo;
    private final Random random;

    // Constructor
    public Proceso(int idProceso) {
        this.idProceso = idProceso;
        this.valorAceptado = null;
        this.bizantino = false;
        this.activo = true;
        this.compromisos = new ArrayList<>();
        this.comisiones  = new ArrayList<>();
        this.comisionEmitida     = false;
        this.confirmacionEmitida = false;
        this.colaTrabajo = new LinkedBlockingQueue<>();
        this.random = new Random();
    }

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public int getIdProceso() {
        return idProceso;
    }

    public Integer getValorAceptado() {
        return valorAceptado;
    }

    public boolean esBizantino() {
        return bizantino;
    }

    public synchronized List<Integer> getCompromisos() {
        return new ArrayList<>(compromisos);
    }

    // -------------------------------------------------------------------------
    // Setters
    // -------------------------------------------------------------------------

    public void setBizantino(boolean b) {
        this.bizantino = b;
    }

    // -------------------------------------------------------------------------
    // Metodos PBFT (llamados por Servicio desde sus endpoints REST)
    // -------------------------------------------------------------------------

    /**
     * Fase 1a - Propuesta del cliente.
     * Reinicia el estado de la ronda y encola el multicast de compromiso.
     */
    public synchronized void propuesta(int v) {
        compromisos.clear();
        comisiones.clear();
        comisionEmitida     = false;
        confirmacionEmitida = false;
        valorAceptado = null;
        colaTrabajo.offer("MULTICAST_COMPROMISO:" + v);
    }

    /**
     * Fase 1b - Recepcion de un compromiso de otro proceso.
     * Si se alcanza quorum para algun valor, se encola el multicast de comision.
     */
    public synchronized void compromiso(int v) {
        compromisos.add(v);
        if (!comisionEmitida && contarOcurrencias(compromisos, v) >= quorum()) {
            comisionEmitida = true;
            colaTrabajo.offer("MULTICAST_COMISION:" + v);
        }
    }

    /**
     * Fase 2a - Recepcion de una comision de otro proceso.
     * Si se alcanza quorum, acepta el valor y encola la confirmacion al cliente.
     */
    public synchronized void comision(int v) {
        comisiones.add(v);
        if (!confirmacionEmitida && contarOcurrencias(comisiones, v) >= quorum()) {
            confirmacionEmitida = true;
            valorAceptado = v;
            colaTrabajo.offer("CONFIRMACION:" + v);
        }
    }

    // -------------------------------------------------------------------------
    // Control del hilo
    // -------------------------------------------------------------------------

    public void detenerProceso() {
        activo = false;
        interrupt();
    }

    // -------------------------------------------------------------------------
    // Hilo principal: procesa la cola de trabajo sin espera ocupada
    // -------------------------------------------------------------------------

    @Override
    public void run() {
        while (activo) {
            try {
                String trabajo = colaTrabajo.take(); // bloquea hasta que haya trabajo
                String[] partes = trabajo.split(":", 2);
                int valor = Integer.parseInt(partes[1]);
                switch (partes[0]) {
                    case "MULTICAST_COMPROMISO": procesarMulticastCompromiso(valor); break;
                    case "MULTICAST_COMISION":   procesarMulticastComision(valor);   break;
                    case "CONFIRMACION":          procesarConfirmacion(valor);        break;
                    default: System.err.println("[P" + idProceso + "] Trabajo desconocido: " + trabajo);
                }
            } catch (InterruptedException e) {
                if (!activo) break;
            }
        }
        System.out.println("[P" + idProceso + "] detenido.");
    }

    // -------------------------------------------------------------------------
    // Logica de envio HTTP (ejecutada por el hilo del proceso)
    // -------------------------------------------------------------------------

    /**
     * Multidifunde el compromiso a todos los servicios.
     * Si el proceso es bizantino, envia un valor aleatorio (0-100) en lugar del correcto.
     */
    private void procesarMulticastCompromiso(int v) {
        for (String url : Configuracion.URLS_SERVICIOS) {
            int valorAEnviar = bizantino ? random.nextInt(101) : v;
            llamarEndpoint(url + "/compromiso?valor=" + valorAEnviar);
        }
    }

    /**
     * Multidifunde la comision a todos los servicios.
     * Los procesos byzantinos no alteran esta fase (ya se han portado mal antes).
     */
    private void procesarMulticastComision(int v) {
        for (String url : Configuracion.URLS_SERVICIOS) {
            llamarEndpoint(url + "/comision?valor=" + v);
        }
    }

    /**
     * Notifica al cliente que este proceso ha confirmado el valor v.
     */
    private void procesarConfirmacion(int v) {
        llamarEndpoint(Configuracion.URL_CLIENTE + "/confirmacion?valor=" + v);
    }

    /**
     * Realiza una peticion GET al endpoint indicado con timeout.
     * Los errores de red se registran pero no interrumpen el proceso.
     */
    private void llamarEndpoint(String urlStr) {
        try {
            HttpURLConnection conexion = (HttpURLConnection) new URL(urlStr).openConnection();
            conexion.setRequestMethod("GET");
            conexion.setConnectTimeout(Configuracion.TIMEOUT_MS);
            conexion.setReadTimeout(Configuracion.TIMEOUT_MS);
            conexion.getResponseCode(); // lanza la peticion y espera respuesta
            conexion.disconnect();
        } catch (IOException e) {
            System.err.println("[P" + idProceso + "] Error al llamar " + urlStr + ": " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private int quorum() {
        return Configuracion.TOTAL_PROCESOS / 2 + 1;
    }

    private int contarOcurrencias(List<Integer> lista, int valor) {
        int n = 0;
        for (int x : lista) if (x == valor) n++;
        return n;
    }

    /**
     * Formato de fila para la tabla de estado del Cliente:
     *   id  var  compromisos  error
     */
    @Override
    public synchronized String toString() {
        return idProceso + "\t"
            + (valorAceptado != null ? valorAceptado : "-") + "\t"
            + listaAString(compromisos) + "\t"
            + bizantino;
    }

    private String listaAString(List<Integer> lista) {
        if (lista.isEmpty()) return "-";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lista.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(lista.get(i));
        }
        return sb.toString();
    }
}