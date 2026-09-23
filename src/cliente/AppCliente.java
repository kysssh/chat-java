package cliente;

import comun.Protocolo;

import javax.swing.*;

/**
 * Punto de entrada del cliente.
 * Pide nombre, host y puerto con ventanas emergentes
 * y lanza la VentanaChat.
 */
public class AppCliente {

    public static void main(String[] args) {
        // Intentar usar el look and feel del sistema operativo
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
        }

        SwingUtilities.invokeLater(() -> {
            // --- Pedir nombre ---
            String nombre = pedirTexto("Tu nombre de usuario:",
                    "Conectar al chat", "");
            if (nombre == null) return; // Canceló
            nombre = nombre.trim();

            // Validar: no vacío, sin |, sin , , sin espacios (igual que el servidor)
            if (nombre.isEmpty() || nombre.contains("|")
                    || nombre.contains(",") || nombre.matches(".*\\s.*")) {
                JOptionPane.showMessageDialog(null,
                    "El nombre no puede estar vacío ni tener |, comas o espacios.",
                    "Nombre inválido", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // --- Pedir host ---
            String host = pedirTexto("Host del servidor:",
                    "Conectar al chat", "localhost");
            if (host == null) return; // Canceló
            host = host.trim();
            if (host.isEmpty()) {
                host = "localhost";
            }

            // --- Pedir puerto ---
            String puertoStr = pedirTexto("Puerto del servidor:",
                    "Conectar al chat",
                    String.valueOf(Protocolo.PUERTO_POR_DEFECTO));
            if (puertoStr == null) return; // Canceló
            int puerto;
            try {
                puerto = Integer.parseInt(puertoStr.trim());
            } catch (NumberFormatException e) {
                puerto = Protocolo.PUERTO_POR_DEFECTO;
            }
            if (puerto < 1 || puerto > 65535) {
                JOptionPane.showMessageDialog(null,
                    "El puerto debe estar entre 1 y 65535.",
                    "Puerto inválido", JOptionPane.ERROR_MESSAGE);
                return;
            }

            // --- Lanzar la ventana ---
            new VentanaChat(nombre, host, puerto);
        });
    }

    /**
     * Muestra un diálogo con un campo de texto y un valor por defecto.
     * Devuelve null si el usuario cancela.
     */
    private static String pedirTexto(String mensaje, String titulo,
                                     String valorDefecto) {
        return (String) JOptionPane.showInputDialog(null,
                mensaje, titulo, JOptionPane.PLAIN_MESSAGE,
                null, null, valorDefecto);
    }
}
