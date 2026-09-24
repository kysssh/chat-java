package cliente;

import javax.swing.*;

/**
 * Punto de entrada del cliente.
 * Pide nombre, host y puerto en la pantalla de conexión
 * y lanza la VentanaChat.
 */
public class AppCliente {

    public static void main(String[] args) {
        // Intentar usar el look and feel del sistema operativo (para diálogos y el selector de archivos)
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignorada) {
        }
        // Tooltips con el tema oscuro
        UIManager.put("ToolTip.background", Estilo.SUPERFICIE_2);
        UIManager.put("ToolTip.foreground", Estilo.TEXTO);
        UIManager.put("ToolTip.border", BorderFactory.createEmptyBorder(4, 8, 4, 8));
        UIManager.put("ToolTip.font", Estilo.fuente(java.awt.Font.PLAIN, 12));

        SwingUtilities.invokeLater(() -> {
            DialogoConexion.Datos datos = DialogoConexion.pedir();
            if (datos == null) {
                return; // Canceló
            }
            new VentanaChat(datos.nombre, datos.host, datos.puerto);
        });
    }
}
