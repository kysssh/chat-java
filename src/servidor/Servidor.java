package servidor;

import comun.Protocolo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * El "jefe" del chat: guarda la lista de clientes conectados y reparte los mensajes.
 * Cada cliente que llega se atiende en su propio hilo (ver ManejadorCliente).
 */
public class Servidor {

    private final int puerto;
    // TreeMap para que la lista de usuarios salga siempre ordenada (ana,juan,pedro)
    private final Map<String, ManejadorCliente> clientes = new TreeMap<>();
    private final List<ConexionServidor> vecinos = new ArrayList<>();   // Fase 2

    public static void main(String[] args) {
        int puerto = Protocolo.PUERTO_POR_DEFECTO;
        if (args.length >= 1) {
            try {
                puerto = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido: " + args[0]);
                return;
            }
        }
        new Servidor(puerto).iniciar();
    }

    public Servidor(int puerto) {
        this.puerto = puerto;
    }

    /** Abre el puerto y espera clientes para siempre; uno por hilo. */
    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(puerto)) {
            System.out.println("Servidor escuchando en el puerto " + puerto);
            while (true) {
                try {
                    Socket socket = serverSocket.accept();
                    new Thread(new ManejadorCliente(socket, this)).start();
                } catch (IOException e) {
                    // un cliente que falla al conectarse no debe tumbar el servidor
                    System.err.println("Error al aceptar un cliente: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo abrir el puerto " + puerto + ": " + e.getMessage());
        }
    }

    /** Guarda al cliente. Devuelve false si ya hay alguien con ese nombre. */
    public synchronized boolean registrarCliente(String nombre, ManejadorCliente m) {
        if (clientes.containsKey(nombre)) {
            return false;
        }
        clientes.put(nombre, m);
        return true;
    }

    public synchronized void quitarCliente(String nombre) {
        clientes.remove(nombre);
    }

    /** Envía la línea a todos los clientes de ESTE servidor. */
    public synchronized void difundirLocal(String linea) {
        for (ManejadorCliente m : clientes.values()) {
            m.enviar(linea);
        }
    }

    /**
     * Envía la línea a los clientes locales (y, en la Fase 2, a los servidores vecinos
     * menos a 'origen'). Por ahora 'origen' siempre es null.
     */
    public synchronized void difundir(String linea, ConexionServidor origen) {
        difundirLocal(linea);
    }

    /** Envía PRIV|de|texto a 'para'. Devuelve false si ese usuario no está. */
    public synchronized boolean enviarPrivado(String de, String para, String texto) {
        ManejadorCliente destino = clientes.get(para);
        if (destino == null) {
            return false;
        }
        destino.enviar(Protocolo.armar(Protocolo.PRIV, de, texto));
        return true;
    }

    /** Devuelve "USUARIOS|ana,juan,pedro" con los conectados a este servidor. */
    public synchronized String listaUsuarios() {
        return Protocolo.armar(Protocolo.USUARIOS, String.join(",", clientes.keySet()));
    }
}
