package cliente;

import comun.Protocolo;

import java.io.*;
import java.net.Socket;

/**
 * Todo lo que tiene que ver con el socket del lado del cliente.
 * NO tiene nada de ventana: solo red.
 */
public class ClienteRed {

    private final OyenteMensajes oyente;
    private Socket socket;
    private PrintWriter escritor;
    private BufferedReader lector;
    private volatile boolean conectado = false;

    public ClienteRed(OyenteMensajes oyente) {
        this.oyente = oyente;
    }

    /**
     * Abre la conexión, envía NOMBRE|nombre y lanza el hilo de escucha.
     * Devuelve false si no pudo conectar.
     */
    public boolean conectar(String host, int puerto, String nombre) {
        try {
            socket = new Socket(host, puerto);
            lector = Protocolo.lector(socket);
            escritor = Protocolo.escritor(socket);
            conectado = true;

            // Primera línea del protocolo: presentarse
            escritor.println(Protocolo.armar(Protocolo.NOMBRE, nombre));

            // Hilo que escucha lo que manda el servidor
            Thread hiloEscucha = new Thread(this::escuchar, "hilo-escucha");
            hiloEscucha.setDaemon(true);
            hiloEscucha.start();

            return true;
        } catch (IOException e) {
            conectado = false;
            return false;
        }
    }

    /** Envía un mensaje público: MSG|texto */
    public void enviarMensaje(String texto) {
        if (conectado && escritor != null) {
            escritor.println(Protocolo.armar(Protocolo.MSG, texto));
        }
    }

    /** Envía un mensaje privado: PRIV|para|texto */
    public void enviarPrivado(String para, String texto) {
        if (conectado && escritor != null) {
            escritor.println(Protocolo.armar(Protocolo.PRIV, para, texto));
        }
    }

    /** Envía una imagen (Fase 3) — por ahora el método existe pero no hace nada */
    public void enviarImagen(java.awt.image.BufferedImage imagen) {
        // Se implementará en Fase 3 cuando Camara.java esté disponible
    }

    /** Envía SALIR y cierra el socket */
    public void desconectar() {
        if (conectado && escritor != null) {
            escritor.println(Protocolo.SALIR);
        }
        conectado = false;
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignorada) {
        }
    }

    /**
     * Bucle de escucha: lee líneas del servidor y las procesa.
     * Corre en su propio hilo.
     */
    private void escuchar() {
        try {
            String linea;
            while (conectado && (linea = lector.readLine()) != null) {
                procesarLinea(linea);
            }
        } catch (IOException ignorada) {
            // El socket se cerró (por desconexión o error)
        } finally {
            conectado = false;
            oyente.alDesconectarse();
        }
    }

    /**
     * Mira el tipo del mensaje y llama al método del oyente que corresponda.
     */
    private void procesarLinea(String linea) {
        // Separar en máximo 2 partes para saber el tipo
        String[] partes = Protocolo.partir(linea, 2);
        String tipo = partes[0];

        switch (tipo) {
            case Protocolo.MSG: {
                // MSG|de|texto → separar en 3
                String[] p = Protocolo.partir(linea, 3);
                if (p.length >= 3) {
                    oyente.alRecibirMensaje(p[1], p[2]);
                }
                break;
            }
            case Protocolo.PRIV: {
                // PRIV|de|texto → separar en 3
                String[] p = Protocolo.partir(linea, 3);
                if (p.length >= 3) {
                    oyente.alRecibirPrivado(p[1], p[2]);
                }
                break;
            }
            case Protocolo.IMG: {
                // IMG|de|base64 → separar en 3 (Fase 3)
                // Por ahora se ignora hasta que Camara.java esté listo
                break;
            }
            case Protocolo.INFO: {
                // INFO|texto
                if (partes.length >= 2) {
                    oyente.alRecibirInfo(partes[1]);
                }
                break;
            }
            case Protocolo.USUARIOS: {
                // USUARIOS|a,b,c
                if (partes.length >= 2) {
                    String[] usuarios = partes[1].split(",");
                    oyente.alActualizarUsuarios(usuarios);
                }
                break;
            }
            case Protocolo.ERROR: {
                // ERROR|texto
                if (partes.length >= 2) {
                    oyente.alRecibirError(partes[1]);
                }
                break;
            }
            default:
                // Tipo desconocido: se ignora
                break;
        }
    }
}
