package comun;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ayuda para saber en qué IP está trabajando esta PC. La usan el servidor (para decir a qué
 * dirección conectarse) y el cliente (para mostrarla en la ventana).
 */
public final class Red {

    private Red() {}

    /**
     * Las IPv4 de esta PC en la red (Wi-Fi, cable…), con el nombre del adaptador.
     * No incluye 127.0.0.1 ni las 169.254.x.x (las que Windows pone cuando no hay red).
     * La principal (ver ipPrincipal) va primero.
     */
    public static Map<String, String> ipsLocales() {
        Map<String, String> ips = new LinkedHashMap<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                        ips.put(a.getHostAddress(), ni.getDisplayName());
                    }
                }
            }
        } catch (SocketException e) {
            // sin permiso para ver los adaptadores: se devuelve lo que haya
        }

        String principal = ipPrincipal();
        if (ips.containsKey(principal)) {
            Map<String, String> ordenadas = new LinkedHashMap<>();
            ordenadas.put(principal, ips.get(principal));
            ordenadas.putAll(ips);
            return ordenadas;
        }
        return ips;
    }

    /**
     * La IP con la que esta PC sale a la red (la del adaptador que se usa de verdad).
     * Truco: "conectar" un socket UDP a una IP pública no envía nada, pero el sistema elige
     * el adaptador y así sabemos su IP. Si no hay red, devuelve 127.0.0.1.
     */
    public static String ipPrincipal() {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress a = s.getLocalAddress();
            if (a != null && !a.isAnyLocalAddress() && !a.isLoopbackAddress()) {
                return a.getHostAddress();
            }
        } catch (Exception e) {
            // sin ruta hacia afuera (sin WiFi, por ejemplo): se prueba con la lista
        }
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                        return a.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignorada) {
            // no se pudo leer la lista
        }
        return "127.0.0.1";
    }
}
