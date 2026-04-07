import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Recurso REST que expone los procesos locales de esta maquina al resto del sistema.
 *
 * Cada maquina despliega esta clase en Tomcat. Los campos estaticos garantizan
 * que todos los procesos son creados una sola vez (al cargar la clase) y compartidos
 * entre todas las peticiones HTTP que atiende Jersey.
 *
 * Endpoints:
 *   GET /servicio/propuesta?v=X          Fase 1a: propuesta del cliente
 *   GET /servicio/compromiso?valor=X     Fase 1b: compromiso de otro proceso
 *   GET /servicio/comision?valor=X       Fase 2a: comision de otro proceso
 *   GET /servicio/confirmacion?valor=X   Fase 2b: confirmacion de proceso local
 *   GET /servicio/estado                 Tabla de estado de todos los procesos locales
 *   GET /servicio/fallo?id=N             Alterna el modo byzantino del proceso N
 *   GET /servicio/ultimaConfirmacion     Ultimo valor sobre el que hubo quorum de confirmaciones
 */
@Path("/servicio")
public class Servicio1 {

    // -------------------------------------------------------------------------
    // Estado compartido entre todas las instancias de Servicio1 (una por request)
    // -------------------------------------------------------------------------

    // LinkedHashMap para que el estado se muestre siempre en orden de id
    private static final Map<Integer, Proceso> procesos = new LinkedHashMap<>();

    // Contador de confirmaciones recibidas para la ronda en curso (solo activo
    // en la maquina que ejecuta el Cliente)
    private static final AtomicInteger confirmacionesRonda = new AtomicInteger(0);

    // Ultimo valor que alcanzo quorum de confirmaciones; -1 = ninguno todavia
    private static volatile int valorUltimaRonda = -1;

    // Inicializacion: crea y arranca los procesos locales al cargar la clase
    static {
        for (int i = 0; i < Configuracion.PROCESOS_POR_MAQUINA; i++) {
            int id = Configuracion.ID_INICIO + i;
            Proceso p = new Proceso(id);
            procesos.put(id, p);
            p.start();
            System.out.println("[Servicio] Proceso " + id + " arrancado.");
        }
    }

    // -------------------------------------------------------------------------
    // Endpoints PBFT
    // -------------------------------------------------------------------------

    /**
     * Fase 1a - El cliente propone el valor v.
     * Reinicia el contador de confirmaciones y llama a propuesta(v) en todos los
     * procesos locales para que inicien la ronda.
     */
    @GET
    @Path("/propuesta")
    public Response propuesta(@QueryParam("v") int v) {
        confirmacionesRonda.set(0);
        valorUltimaRonda = -1;
        for (Proceso p : procesos.values()) p.propuesta(v);
        return Response.ok("OK").build();
    }

    /**
     * Fase 1b - Llega el compromiso de un proceso remoto con el valor indicado.
     * Se aplica a todos los procesos locales: cada uno decide si ya tiene quorum.
     */
    @GET
    @Path("/compromiso")
    public Response compromiso(@QueryParam("valor") int valor) {
        for (Proceso p : procesos.values()) p.compromiso(valor);
        return Response.ok("OK").build();
    }

    /**
     * Fase 2a - Llega la comision de un proceso remoto con el valor indicado.
     * Se aplica a todos los procesos locales: cada uno decide si ya tiene quorum.
     */
    @GET
    @Path("/comision")
    public Response comision(@QueryParam("valor") int valor) {
        for (Proceso p : procesos.values()) p.comision(valor);
        return Response.ok("OK").build();
    }

    /**
     * Fase 2b - Un proceso local ha alcanzado quorum de comisiones y notifica al cliente.
     * Cuando se acumulan suficientes confirmaciones, se registra el valor como decidido.
     */
    @GET
    @Path("/confirmacion")
    public Response confirmacion(@QueryParam("valor") int valor) {
        int total = confirmacionesRonda.incrementAndGet();
        if (total >= quorum()) {
            valorUltimaRonda = valor;
            System.out.println("[Servicio] Consenso alcanzado: valor=" + valor
                    + " (" + total + "/" + Configuracion.TOTAL_PROCESOS + " confirmaciones)");
        }
        return Response.ok("OK").build();
    }

    /**
     * Devuelve el estado de todos los procesos locales en formato tabla (sin cabecera).
     * Cada linea tiene el formato: id TAB var TAB compromisos TAB error
     */
    @GET
    @Path("/estado")
    @Produces(MediaType.TEXT_PLAIN)
    public Response estado() {
        StringBuilder sb = new StringBuilder();
        for (Proceso p : procesos.values()) sb.append(p.toString()).append("\n");
        return Response.ok(sb.toString()).build();
    }

    /**
     * Alterna el modo byzantino del proceso con el id indicado.
     * Si el proceso no esta en esta maquina devuelve 404; el cliente llama a
     * todos los servicios y solo el que lo tenga responde con 200.
     */
    @GET
    @Path("/fallo")
    public Response fallo(@QueryParam("id") int id) {
        Proceso p = procesos.get(id);
        if (p == null) return Response.status(Response.Status.NOT_FOUND).build();
        p.setBizantino(!p.esBizantino());
        return Response.ok("Proceso " + id + " error=" + p.esBizantino()).build();
    }

    /**
     * Devuelve el ultimo valor que alcanzo quorum de confirmaciones en esta maquina.
     * El Cliente lo consulta periodicamente despues de lanzar una propuesta.
     * Devuelve "-1" si todavia no hay consenso confirmado.
     */
    @GET
    @Path("/ultimaConfirmacion")
    public Response ultimaConfirmacion() {
        return Response.ok(String.valueOf(valorUltimaRonda)).build();
    }

    // -------------------------------------------------------------------------
    // Utilidades
    // -------------------------------------------------------------------------

    private int quorum() {
        return Configuracion.TOTAL_PROCESOS / 2 + 1;
    }
}
