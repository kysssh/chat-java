package cliente;

import comun.Protocolo;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Todo lo que tiene que ver con el socket del lado del cliente.
 * NO tiene nada de ventana: solo red.
 */
public class ClienteRed {

    private static final int TIEMPO_CONEXION_MS = 5000;

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
            // Con tiempo límite: a una IP que no responde, new Socket(host, puerto) tarda ~20 s
            // y la ventana se queda congelada todo ese rato
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, puerto), TIEMPO_CONEXION_MS);
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
            desconectar();
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

    /** Envía una imagen codificada en Base64: IMG|base64 */
    public void enviarImagen(java.awt.image.BufferedImage imagen) {
        if (conectado && escritor != null && imagen != null) {
            String base64 = Camara.aBase64(imagen);
            if (base64 != null) {
                escritor.println(Protocolo.armar(Protocolo.IMG, base64));
            }
        }
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
                // IMG|de|base64 → separar en 3
                String[] pImg = Protocolo.partir(linea, 3);
                if (pImg.length >= 3) {
                    java.awt.image.BufferedImage img = Camara.deBase64(pImg[2]);
                    if (img != null) {
                        oyente.alRecibirImagen(pImg[1], img);
                    }
                }
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
