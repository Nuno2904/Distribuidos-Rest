//Clase que gestiona el servicio y el resto de procesos.

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Servicio {

    //Atributos:
    private final Map<Integer, Proceso> procesos; //mapa de procesos, donde la clave es el id del proceso


    public Servicio(int numeroProcesos)
    {
        if (numeroProcesos < 1) {
            throw new IllegalArgumentException("El número de procesos debe ser al menos 1.");
        }

        this.procesos = new HashMap<Integer, Proceso>();

        //Crear procesos locales
        for (int i = 1; i <= numeroProcesos; i++)
        {
            Proceso proceso = new Proceso(i);
            procesos.put(i, proceso);
        }

        iniciarProcesos();
    }

    //Metodos

    private void iniciarProcesos()
    {
        for (Proceso proceso : procesos.values())
        {
            proceso.start();
        }
    }

    public void detenerProcesos()
    {
        for (Proceso proceso : procesos.values())
        {
            proceso.detenerProceso();
        }
    }

    public void marcarBizantino(int id, boolean valor)
    {
        getProceso(id).setBizantino(valor);
    }

    public void enviarTrabajoAProceso(int id, String trabajo)
    {
        getProceso(id).agregarTrabajo(trabajo);
    }

    public void enviarTrabajoATodos(String trabajo)
    {
        for (Proceso proceso : procesos.values())
        {
            proceso.agregarTrabajo(trabajo);
        }
    }

    public String getEstadoProcesos()
    {
        StringBuilder estado = new StringBuilder();

        for (Proceso proceso : procesos.values())
        {
            estado.append(proceso.toString()).append(System.lineSeparator());
        }

        return estado.toString();
    }

    //Getter y Setters:

    public Collection<Proceso> getProcesos()
    {
        return this.procesos.values();
    }

    public Proceso getProceso(int id)
    {
        Proceso proceso = procesos.get(id);

        if (proceso == null)
        {
            throw new IllegalArgumentException("No existe un proceso con el id: " + id);
        }

        return proceso;
    }
}