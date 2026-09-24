package servidor;

import comun.Protocolo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Conexión con OTRO servidor (un "vecino"). Corre en su propio hilo y se pasa
 * con él las líneas MSG, INFO, IMG y AUDIO tal cual.
 */
public class ConexionServidor implements Runnable {

    private final Socket socket;
    private final BufferedReader lector;
    private final PrintWriter escritor;
    private final Servidor servidor;
    // AtomicBoolean y no synchronized: cerrar() llama al servidor, y un lock propio aquí
    // podría bloquearse contra el lock del servidor (que llama a enviar()).
    private final AtomicBoolean cerrado = new AtomicBoolean(false);

    public ConexionServidor(Socket socket, BufferedReader lector, PrintWriter escritor, Servidor servidor) {
        this.socket = socket;
        this.lector = lector;
        this.escritor = escritor;
        this.servidor = servidor;
    }

    /** Por cada línea que llega del vecino: se reparte a los clientes locales y a los demás vecinos. */
    @Override
    public void run() {
        try {
            String linea;
            while ((linea = lector.readLine()) != null) {
                String tipo = Protocolo.partir(linea, 2)[0];
                // Solo estos viajan entre servidores; USUARIOS, PRIV y ERROR son locales
                if (tipo.equals(Protocolo.MSG) || tipo.equals(Protocolo.INFO)
                        || tipo.equals(Protocolo.IMG) || tipo.equals(Protocolo.AUDIO)) {
                    servidor.difundir(linea, this);
                }
            }
        } catch (IOException e) {
            // el vecino se cayó o cerró: solo se pierde esa conexión
        } finally {
            cerrar();
        }
    }

    /** synchronized para que dos hilos no escriban mezclado en el mismo socket. */
    public synchronized void enviar(String linea) {
        escritor.println(linea);
    }

    /** Se ejecuta una sola vez, sin importar cuántas veces se llame. */
    private void cerrar() {
        if (!cerrado.compareAndSet(false, true)) {
            return;
        }
        servidor.quitarVecino(this);
        try {
            socket.close();
        } catch (IOException ignorada) {
            // ya estaba cerrado
        }
    }
}
