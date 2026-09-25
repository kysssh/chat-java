package servidor;

import comun.Protocolo;
import comun.Red;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
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
    private final List<ConexionServidor> vecinos = new ArrayList<>();

    /** Uso: Servidor [puertoPropio [hostVecino puertoVecino]] */
    public static void main(String[] args) {
        if (args.length == 2 || args.length > 3) {
            System.err.println("Uso: Servidor [puertoPropio [hostVecino puertoVecino]]");
            return;
        }
        int puerto = Protocolo.PUERTO_POR_DEFECTO;
        int puertoVecino = 0;
        try {
            if (args.length >= 1) {
                puerto = Integer.parseInt(args[0]);
            }
            if (args.length == 3) {
                puertoVecino = Integer.parseInt(args[2]);
            }
        } catch (NumberFormatException e) {
            System.err.println("Puerto inválido: " + e.getMessage());
            return;
        }
        if (!puertoValido(puerto) || (args.length == 3 && !puertoValido(puertoVecino))) {
            System.err.println("Los puertos deben estar entre 1 y 65535");
            return;
        }

        // Primero se abre el puerto propio: si está ocupado no tiene sentido conectarse al vecino
        // (antes el servidor quedaba colgado, sin puerto pero conectado al vecino)
        ServerSocket serverSocket;
        try {
            serverSocket = new ServerSocket(puerto);
        } catch (IOException e) {
            System.err.println("No se pudo abrir el puerto " + puerto + ": " + e.getMessage());
            return;
        }

        Servidor servidor = new Servidor(puerto);
        if (args.length == 3) {
            try {
                servidor.conectarAVecino(args[1], puertoVecino);
            } catch (IOException e) {
                System.err.println("No se pudo conectar al servidor " + args[1] + ":" + puertoVecino
                        + " (" + e.getMessage() + ")");
                try {
                    serverSocket.close();
                } catch (IOException ignorada) {
                    // se va a cerrar el programa igual
                }
                return;
            }
        }
        servidor.iniciar(serverSocket);
    }

    private static boolean puertoValido(int p) {
        return p >= 1 && p <= 65535;
    }

    public Servidor(int puerto) {
        this.puerto = puerto;
    }

    /** Espera clientes para siempre en el puerto ya abierto; uno por hilo. */
    public void iniciar(ServerSocket abierto) {
        try (ServerSocket serverSocket = abierto) {
            System.out.println("Servidor escuchando en el puerto " + puerto);
            mostrarDirecciones();
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
            System.err.println("Se cerró el puerto " + puerto + ": " + e.getMessage());
        }
    }

    /** Dice en consola a qué IP se deben conectar los clientes (y los otros servidores). */
    private void mostrarDirecciones() {
        System.out.println("Los clientes pueden conectarse a:");
        for (Map.Entry<String, String> ip : Red.ipsLocales().entrySet()) {
            System.out.println("   " + ip.getKey() + " : " + puerto + "   (" + ip.getValue() + ")");
        }
        System.out.println("   localhost : " + puerto + "   (desde esta misma PC)");
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
     * Envía la línea a los clientes locales y a todos los servidores vecinos
     * menos a 'origen' (el que nos la mandó; null si vino de un cliente local).
     */
    public synchronized void difundir(String linea, ConexionServidor origen) {
        difundirLocal(linea);
        for (ConexionServidor vecino : vecinos) {
            if (vecino != origen) {
                vecino.enviar(linea);
            }
        }
    }

    /** Envía PRIV|de|texto a 'para'. Devuelve false si ese usuario no está. */
    public boolean enviarPrivado(String de, String para, String texto) {
        return reenviar(Protocolo.PRIV, de, para, texto);
    }

    /**
     * Envía tipo|de|contenido solo a 'para' (privados y videollamada). Devuelve false si no está.
     * Se busca con el candado del servidor, pero se envía fuera de él: los cuadros de video
     * llegan muchas veces por segundo y no deben frenar al resto del chat.
     */
    public boolean reenviar(String tipo, String de, String para, String contenido) {
        ManejadorCliente destino;
        synchronized (this) {
            destino = clientes.get(para);
        }
        if (destino == null) {
            return false;
        }
        destino.enviar(Protocolo.armar(tipo, de, contenido));
        return true;
    }

    /** Devuelve "USUARIOS|ana,juan,pedro" con los conectados a este servidor. */
    public synchronized String listaUsuarios() {
        return Protocolo.armar(Protocolo.USUARIOS, String.join(",", clientes.keySet()));
    }

    // ---- Varios servidores (Fase 2) ----

    /** Nos conectamos a un servidor que ya existe: le decimos SERVIDOR|miPuerto y quedamos como vecinos. */
    public void conectarAVecino(String host, int puertoVecino) throws IOException {
        Socket socket = new Socket(host, puertoVecino);
        try {
            BufferedReader lector = Protocolo.lector(socket);
            PrintWriter escritor = Protocolo.escritor(socket);
            escritor.println(Protocolo.armar(Protocolo.SERVIDOR, String.valueOf(puerto)));

            ConexionServidor conexion = new ConexionServidor(socket, lector, escritor, this);
            agregarVecino(conexion);
            new Thread(conexion).start();
            System.out.println("Conectado al servidor vecino " + host + ":" + puertoVecino);
        } catch (IOException | RuntimeException e) {
            socket.close();
            throw e;
        }
    }

    public synchronized void agregarVecino(ConexionServidor c) {
        vecinos.add(c);
    }

    public synchronized void quitarVecino(ConexionServidor c) {
        vecinos.remove(c);
    }
}
