package cliente;

import comun.Protocolo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;

/**
 * Pantalla de inicio: pide nombre, servidor y puerto en una sola ventana
 * y muestra los errores ahí mismo (sin cerrar el programa).
 */
class DialogoConexion extends JDialog {

    private static final long serialVersionUID = 1L;

    /** Lo que escribió el usuario, ya validado. */
    static class Datos {
        final String nombre;
        final String host;
        final int puerto;

        Datos(String nombre, String host, int puerto) {
            this.nombre = nombre;
            this.host = host;
            this.puerto = puerto;
        }
    }

    private final Estilo.Campo campoNombre = new Estilo.Campo("ej. juan");
    private final Estilo.Campo campoHost = new Estilo.Campo("localhost");
    private final Estilo.Campo campoPuerto = new Estilo.Campo(String.valueOf(Protocolo.PUERTO_POR_DEFECTO));
    private final JLabel lblError = Estilo.etiqueta(" ", Font.PLAIN, 12, Estilo.PELIGRO);
    private Datos resultado;

    /** Muestra la pantalla y espera. Devuelve null si el usuario la cerró. */
    static Datos pedir() {
        DialogoConexion d = new DialogoConexion();
        d.setVisible(true);   // modal: se queda aquí hasta que se cierre
        return d.resultado;
    }

    private DialogoConexion() {
        super((Frame) null, "Chat Java — Conectar", true);
        setIconImages(Estilo.iconosVentana());
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setBackground(Estilo.PANEL);
        contenido.setBorder(new EmptyBorder(28, 32, 26, 32));
        setContentPane(contenido);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;

        // Logo + títulos
        c.anchor = GridBagConstraints.CENTER;
        c.fill = GridBagConstraints.NONE;
        contenido.add(new Logo(), c);
        c.insets = new Insets(14, 0, 0, 0);
        contenido.add(Estilo.etiqueta("Chat Java", Font.BOLD, 22, Estilo.TEXTO), c);
        c.insets = new Insets(4, 0, 22, 0);
        contenido.add(Estilo.etiqueta("Conéctate a un servidor para empezar", Font.PLAIN, 13, Estilo.TEXTO_SUAVE), c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;

        // Nombre
        c.insets = new Insets(0, 0, 6, 0);
        contenido.add(titulo("Tu nombre"), c);
        c.insets = new Insets(0, 0, 14, 0);
        campoNombre.setColumns(22);
        contenido.add(campoNombre, c);

        // Servidor y puerto en la misma fila
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 6, 10);
        c.weightx = 1;
        contenido.add(titulo("Servidor"), c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 6, 0);
        contenido.add(titulo("Puerto"), c);

        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 8, 10);
        campoHost.setText("localhost");
        contenido.add(campoHost, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 8, 0);
        campoPuerto.setColumns(5);
        campoPuerto.setText(String.valueOf(Protocolo.PUERTO_POR_DEFECTO));
        contenido.add(campoPuerto, c);

        // Error + botón
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 2, 12, 0);
        contenido.add(lblError, c);

        Estilo.Boton conectar = new Estilo.Boton("Conectar", null, Estilo.ACENTO, Color.WHITE);
        conectar.setFont(Estilo.fuente(Font.BOLD, 14));
        conectar.addActionListener(e -> intentar());
        c.insets = new Insets(0, 0, 0, 0);
        contenido.add(conectar, c);
        getRootPane().setDefaultButton(conectar);   // Enter en cualquier campo = Conectar

        // Escape cierra
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        pack();
        setLocationRelativeTo(null);
    }

    private static JLabel titulo(String texto) {
        return Estilo.etiqueta(texto, Font.BOLD, 12, Estilo.TEXTO_SUAVE);
    }

    /** Valida todo; si algo está mal lo dice en rojo y se queda abierta. */
    private void intentar() {
        String nombre = campoNombre.getText().trim();
        if (nombre.isEmpty()) {
            error("Escribe tu nombre.", campoNombre);
            return;
        }
        // Igual que el servidor: sin |, sin comas y sin espacios
        if (nombre.contains("|") || nombre.contains(",") || nombre.matches(".*\\s.*")) {
            error("El nombre no puede tener |, comas ni espacios.", campoNombre);
            return;
        }

        String host = campoHost.getText().trim();
        if (host.isEmpty()) {
            host = "localhost";
        }

        String textoPuerto = campoPuerto.getText().trim();
        int puerto;
        if (textoPuerto.isEmpty()) {
            puerto = Protocolo.PUERTO_POR_DEFECTO;
        } else {
            try {
                puerto = Integer.parseInt(textoPuerto);
            } catch (NumberFormatException e) {
                puerto = -1;   // "50a0" no debe conectar a otro puerto sin avisar
            }
        }
        if (puerto < 1 || puerto > 65535) {
            error("El puerto debe ser un número entre 1 y 65535.", campoPuerto);
            return;
        }

        resultado = new Datos(nombre, host, puerto);
        dispose();
    }

    private void error(String texto, JTextField campo) {
        lblError.setText(texto);
        campo.requestFocusInWindow();
        campo.selectAll();
    }

    /** Círculo azul con un globo de chat. */
    private static class Logo extends JComponent {
        private static final long serialVersionUID = 1L;
        private static final int TAM = 56;

        Logo() {
            setPreferredSize(new Dimension(TAM, TAM));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Estilo.suave(g);
            g2.setColor(Estilo.ACENTO);
            g2.fill(new Ellipse2D.Float(0, 0, TAM, TAM));
            g2.translate(TAM * 0.22, TAM * 0.23);
            g2.scale(TAM * 0.56 / 18, TAM * 0.56 / 18);
            g2.setColor(Color.WHITE);
            g2.fill(Estilo.Icono.formaChat());
            g2.dispose();
        }
    }
}
