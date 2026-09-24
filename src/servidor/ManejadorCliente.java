package servidor;

import comun.Protocolo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uno por cada cliente conectado. Corre en su propio hilo y escucha lo que ese cliente manda.
 */
public class ManejadorCliente implements Runnable {

    private final Socket socket;
    private final Servidor servidor;
    private volatile BufferedReader lector;
    private volatile PrintWriter escritor;
    private volatile String nombre;
    // true solo si el servidor aceptó el nombre (así un "nombre en uso" no borra al dueño real)
    private volatile boolean registrado = false;
    // AtomicBoolean y no synchronized: cerrar() llama al servidor, y un lock propio aquí
    // podría bloquearse contra el lock del servidor (que llama a enviar()).
    private final AtomicBoolean cerrado = new AtomicBoolean(false);

    public ManejadorCliente(Socket socket, Servidor servidor) {
        this.socket = socket;
        this.servidor = servidor;
    }

    @Override
    public void run() {
        try {
            lector = Protocolo.lector(socket);
            escritor = Protocolo.escritor(socket);

            // 1) La primera línea debe ser NOMBRE|x
            String primera = lector.readLine();
            if (primera == null) {
                return;
            }
            String[] partes = Protocolo.partir(primera, 2);

            // Si es otro servidor (SERVIDOR|id), esta conexión pasa a ser de un vecino
            if (partes[0].equals(Protocolo.SERVIDOR)) {
                ConexionServidor conexion = new ConexionServidor(socket, lector, escritor, servidor);
                servidor.agregarVecino(conexion);
                new Thread(conexion).start();
                // el socket ahora es de ConexionServidor: que el finally no lo cierre
                cerrado.set(true);
                return;
            }

            if (!partes[0].equals(Protocolo.NOMBRE) || partes.length < 2) {
                enviar(Protocolo.armar(Protocolo.ERROR, "se esperaba NOMBRE|nombre"));
                return;
            }
            String candidato = partes[1];
            if (!nombreValido(candidato)) {
                enviar(Protocolo.armar(Protocolo.ERROR, "nombre inválido"));
                return;
            }

            // 2) Registrarse; si el nombre ya existe, error y a cerrar
            nombre = candidato;
            if (!servidor.registrarCliente(nombre, this)) {
                enviar(Protocolo.armar(Protocolo.ERROR, "nombre en uso"));
                return;
            }
            registrado = true;

            // 3) Bienvenida y avisos a todos
            enviar(Protocolo.armar(Protocolo.INFO, "Bienvenido, " + nombre));
            servidor.difundir(Protocolo.armar(Protocolo.INFO, nombre + " se unió al chat"), null);
            servidor.difundirLocal(servidor.listaUsuarios());

            // 4) Bucle principal
            String linea;
            while (!cerrado.get() && (linea = lector.readLine()) != null) {
                procesarLinea(linea);
            }
        } catch (IOException e) {
            // el cliente cerró de golpe: solo termina este hilo, el servidor sigue vivo
        } finally {
            cerrar();
        }
    }

    private void procesarLinea(String linea) {
        String[] p = Protocolo.partir(linea, 2);
        switch (p[0]) {
            case Protocolo.MSG:
                if (p.length > 1 && !p[1].isEmpty()) {
                    servidor.difundir(Protocolo.armar(Protocolo.MSG, nombre, p[1]), null);
                }
                break;

            case Protocolo.PRIV:
                String[] q = Protocolo.partir(linea, 3);   // PRIV|para|texto
                if (q.length < 3) {
                    enviar(Protocolo.armar(Protocolo.ERROR, "formato: PRIV|para|texto"));
                } else if (!servidor.enviarPrivado(nombre, q[1], q[2])) {
                    enviar(Protocolo.armar(Protocolo.ERROR, "usuario no encontrado"));
                }
                break;

            case Protocolo.IMG:
            case Protocolo.AUDIO:   // IMG|base64 y AUDIO|base64 se reparten igual: TIPO|de|base64
                if (p.length > 1 && !p[1].isEmpty()) {
                    servidor.difundir(Protocolo.armar(p[0], nombre, p[1]), null);
                }
                break;

            case Protocolo.SALIR:
                cerrar();
                break;

            default:
                enviar(Protocolo.armar(Protocolo.ERROR, "comando desconocido"));
        }
    }

    /** Sin | , ni espacios, y no vacío (B también lo valida, pero el servidor no confía). */
    private static boolean nombreValido(String n) {
        if (n.isEmpty()) {
            return false;
        }
        for (char c : n.toCharArray()) {
            if (c == '|' || c == ',' || Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    /** synchronized para que dos hilos no escriban mezclado en el mismo socket. */
    public synchronized void enviar(String linea) {
        PrintWriter w = escritor;
        if (w != null) {
            w.println(linea);
        }
    }

    public String getNombre() {
        return nombre;
    }

    /** Se ejecuta una sola vez, sin importar cuántas veces se llame. */
    private void cerrar() {
        if (!cerrado.compareAndSet(false, true)) {
            return;
        }
        if (registrado) {
            servidor.quitarCliente(nombre);
            servidor.difundir(Protocolo.armar(Protocolo.INFO, nombre + " salió del chat"), null);
            servidor.difundirLocal(servidor.listaUsuarios());
        }
        try {
            socket.close();
        } catch (IOException ignorada) {
            // ya estaba cerrado
        }
    }
}
