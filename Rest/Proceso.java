import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Proceso extends Thread {

    //Atributos:
    private final int idProceso;
    private Integer valorAceptado;  //valor actual aceptado por el proceso
    private boolean bizantino; //indica si el proceso es bizantino 
    private boolean activo; //indica si el proceso está activo o ha sido eliminado
    private final BlockingQueue<String> colaTrabajo; //cola bloqueante para tareas pendientes
    //Constructor:
    public Proceso(int idProceso)
    {
        this.idProceso = idProceso;
        this.valorAceptado = null; //inicialmente no se ha aceptado ningún valor
        this.bizantino = false; //por defecto, el proceso no es bizantino
        this.activo = true; //por defecto, el proceso está activo
        this.colaTrabajo = new LinkedBlockingQueue<String>();
    }
    //Getters y Setters:

    public int getIdProceso()
    {
        return this.idProceso;
    }

    public Integer getValorAceptado()
    {
        return this.valorAceptado;
    }

    public boolean esBizantino()
    {
        return this.bizantino;
    }

    public void setValorAceptado(Integer valorAceptado)
    {
        this.valorAceptado = valorAceptado;
    }

    public void setBizantino(boolean bizantino)
    {
        this.bizantino = bizantino;
    }

    //Metodos:

    public void agregarTrabajo(String trabajo)
    {
        this.colaTrabajo.offer(trabajo);
    }

    public void detenerProceso()
    {
        this.activo = false;
        this.interrupt(); //interrumpe el hilo si está esperando en la cola
    }



    @Override
    public void run()
    {
        while (activo)
        {
            try
            {
                String trabajo = colaTrabajo.take(); //espera a que haya un trabajo disponible
                System.out.println("Proceso " + idProceso + " ejecutando: " + trabajo);
                // Simular tiempo de procesamiento
                Thread.sleep(1000);
            }
            catch (InterruptedException e)
            {
                if (!activo) {
                    System.out.println("Proceso " + idProceso + " detenido.");
                    break; //salir del bucle si el proceso ha sido detenido
                }
            }
        }
    }

//metodo tostring()
    @Override
    public String toString() { //solo para depurar
        return "Proceso{" +
                "idProceso=" + idProceso +
                ", valorAceptado=" + valorAceptado +
                ", bizantino=" + bizantino +
                '}';
    }
}