import java.util.Arrays;
import java.util.List;

/**
 * Configuracion estatica del sistema PBFT.
 * Centraliza las URLs de todos los servicios y los parametros del protocolo.
 * Para cambiar el despliegue (maquinas, puertos, numero de procesos)
 * solo hay que modificar esta clase.
 */
public class Configuracion {

    // URLs de los tres servicios REST (uno por maquina)
    public static final List<String> URLS_SERVICIOS = Arrays.asList(
        "http://127.2.1.100:8080/servicio",
        "http://127.2.134.1:8080/servicio",
        "http://127.20.13.4:8080/servicio"
    );

    // URL del servicio que corre en la misma maquina que el cliente
    // Los procesos usaran este endpoint para enviar la confirmacion final
    public static final String URL_CLIENTE = "http://127.20.13.4:8080/servicio";

    // Numero de procesos locales que crea cada Servicio
    public static final int PROCESOS_POR_MAQUINA = 2;

    // Total de procesos en el sistema (usado para calcular el quorum)
    public static final int TOTAL_PROCESOS = URLS_SERVICIOS.size() * PROCESOS_POR_MAQUINA;

    // Primer id de proceso de ESTA maquina.
    // Arrancar Tomcat con: -Did.inicio=1 (maquina 1), -Did.inicio=3 (maquina 2), -Did.inicio=5 (maquina 3)
    public static final int ID_INICIO = Integer.parseInt(System.getProperty("id.inicio", "1"));

    // Timeout en milisegundos para las llamadas HTTP entre procesos
    public static final int TIMEOUT_MS = 2000;
}
