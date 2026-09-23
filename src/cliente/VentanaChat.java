package cliente;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;

/**
 * La ventana principal del chat (Swing).
 * Implementa OyenteMensajes para recibir eventos de la red.
 */
public class VentanaChat extends JFrame implements OyenteMensajes {

    private final String nombre;
    private final ClienteRed clienteRed;

    // ---- Componentes de la ventana ----
    private JTextArea areaMensajes;
    private JTextField campoTexto;
    private JButton botonEnviar;
    private JButton botonPrivado;
    private DefaultListModel<String> modeloUsuarios;
    private JList<String> listaUsuarios;

    public VentanaChat(String nombre, String host, int puerto) {
        super("Chat — " + nombre + " @ " + host + ":" + puerto);
        this.nombre = nombre;

        construirInterfaz();

        // Crear la conexión de red (la ventana es el oyente)
        clienteRed = new ClienteRed(this);
        boolean ok = clienteRed.conectar(host, puerto, nombre);

        if (!ok) {
            JOptionPane.showMessageDialog(this,
                "No se pudo conectar a " + host + ":" + puerto,
                "Error de conexión", JOptionPane.ERROR_MESSAGE);
            desactivarEnvio();
        }
    }

    /** Arma toda la interfaz gráfica */
    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(700, 500);
        setMinimumSize(new Dimension(500, 350));
        setLocationRelativeTo(null);

        // --- Colores y fuentes ---
        Color fondoOscuro    = new Color(30, 30, 30);
        Color fondoPanel     = new Color(40, 40, 40);
        Color fondoInput     = new Color(50, 50, 50);
        Color colorTexto     = new Color(220, 220, 220);
        Color colorAccent    = new Color(88, 166, 255);
        Color colorPrivado   = new Color(255, 140, 50);
        Color colorBorde     = new Color(60, 60, 60);
        Font  fuenteChat     = new Font("Consolas", Font.PLAIN, 14);
        Font  fuenteInput    = new Font("Segoe UI", Font.PLAIN, 14);
        Font  fuenteBoton    = new Font("Segoe UI", Font.BOLD, 13);
        Font  fuenteUsuarios = new Font("Segoe UI", Font.PLAIN, 13);

        getContentPane().setBackground(fondoOscuro);
        setLayout(new BorderLayout(0, 0));

        // ===== Área de mensajes (centro-izquierda) =====
        areaMensajes = new JTextArea();
        areaMensajes.setEditable(false);
        areaMensajes.setLineWrap(true);
        areaMensajes.setWrapStyleWord(true);
        areaMensajes.setFont(fuenteChat);
        areaMensajes.setBackground(fondoPanel);
        areaMensajes.setForeground(colorTexto);
        areaMensajes.setCaretColor(colorTexto);
        areaMensajes.setMargin(new Insets(10, 10, 10, 10));

        JScrollPane scrollMensajes = new JScrollPane(areaMensajes);
        scrollMensajes.setBorder(BorderFactory.createLineBorder(colorBorde));
        scrollMensajes.getVerticalScrollBar().setUnitIncrement(16);

        // ===== Lista de usuarios (derecha) =====
        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setFont(fuenteUsuarios);
        listaUsuarios.setBackground(fondoPanel);
        listaUsuarios.setForeground(colorTexto);
        listaUsuarios.setSelectionBackground(colorAccent);
        listaUsuarios.setSelectionForeground(Color.WHITE);
        listaUsuarios.setFixedCellHeight(28);

        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        scrollUsuarios.setPreferredSize(new Dimension(150, 0));
        scrollUsuarios.setBorder(BorderFactory.createLineBorder(colorBorde));

        // Etiqueta "Conectados" arriba de la lista
        JLabel lblConectados = new JLabel("  Conectados", SwingConstants.LEFT);
        lblConectados.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblConectados.setForeground(colorAccent);
        lblConectados.setOpaque(true);
        lblConectados.setBackground(fondoPanel);
        lblConectados.setPreferredSize(new Dimension(150, 30));
        lblConectados.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, colorBorde));

        JPanel panelDerecho = new JPanel(new BorderLayout());
        panelDerecho.add(lblConectados, BorderLayout.NORTH);
        panelDerecho.add(scrollUsuarios, BorderLayout.CENTER);

        // Split entre mensajes y lista de usuarios
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                scrollMensajes, panelDerecho);
        splitPane.setDividerLocation(500);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(3);
        splitPane.setBorder(null);
        add(splitPane, BorderLayout.CENTER);

        // ===== Panel inferior: campo de texto + botones =====
        JPanel panelInferior = new JPanel(new BorderLayout(5, 0));
        panelInferior.setBackground(fondoOscuro);
        panelInferior.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

        campoTexto = new JTextField();
        campoTexto.setFont(fuenteInput);
        campoTexto.setBackground(fondoInput);
        campoTexto.setForeground(colorTexto);
        campoTexto.setCaretColor(colorTexto);
        campoTexto.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(colorBorde),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));

        botonEnviar = crearBoton("Enviar", colorAccent, fuenteBoton);
        botonPrivado = crearBoton("Privado", colorPrivado, fuenteBoton);

        JPanel panelBotones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        panelBotones.setBackground(fondoOscuro);
        panelBotones.add(botonPrivado);
        panelBotones.add(botonEnviar);

        panelInferior.add(campoTexto, BorderLayout.CENTER);
        panelInferior.add(panelBotones, BorderLayout.EAST);
        add(panelInferior, BorderLayout.SOUTH);

        // ===== Acciones =====
        botonEnviar.addActionListener(e -> accionEnviar());
        campoTexto.addActionListener(e -> accionEnviar()); // Enter también envía
        botonPrivado.addActionListener(e -> accionPrivado());

        // Al cerrar la ventana → desconectar limpiamente
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                clienteRed.desconectar();
                dispose();
                System.exit(0);
            }
        });

        setVisible(true);
        campoTexto.requestFocusInWindow();
    }

    /** Crea un botón con estilo dark */
    private JButton crearBoton(String texto, Color colorFondo, Font fuente) {
        JButton btn = new JButton(texto);
        btn.setFont(fuente);
        btn.setForeground(Color.WHITE);
        btn.setBackground(colorFondo);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setPreferredSize(new Dimension(90, 34));

        // Hover
        Color hover = colorFondo.brighter();
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(hover);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(colorFondo);
            }
        });
        return btn;
    }

    /** Enviar mensaje público */
    private void accionEnviar() {
        String texto = campoTexto.getText().trim();
        if (!texto.isEmpty()) {
            clienteRed.enviarMensaje(texto);
            campoTexto.setText("");
        }
        campoTexto.requestFocusInWindow();
    }

    /** Enviar mensaje privado al usuario seleccionado en la lista */
    private void accionPrivado() {
        String seleccionado = listaUsuarios.getSelectedValue();
        if (seleccionado == null) {
            JOptionPane.showMessageDialog(this,
                "Selecciona un usuario de la lista para enviar un privado.",
                "Sin destinatario", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String texto = campoTexto.getText().trim();
        if (texto.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                "Escribe un mensaje antes de enviar.",
                "Mensaje vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }
        clienteRed.enviarPrivado(seleccionado, texto);
        campoTexto.setText("");
        campoTexto.requestFocusInWindow();
    }

    /** Agrega una línea al área de mensajes y baja el scroll */
    private void agregarLinea(String texto) {
        areaMensajes.append(texto + "\n");
        // Auto-scroll al final
        areaMensajes.setCaretPosition(areaMensajes.getDocument().getLength());
    }

    /** Desactiva los botones de envío */
    private void desactivarEnvio() {
        botonEnviar.setEnabled(false);
        botonPrivado.setEnabled(false);
        campoTexto.setEnabled(false);
    }

    // ================================================================
    //  Implementación de OyenteMensajes
    //  ⚠️ Estos métodos se llaman desde el hilo de RED,
    //     así que todo cambio a la ventana va con invokeLater.
    // ================================================================

    @Override
    public void alRecibirMensaje(String de, String texto) {
        SwingUtilities.invokeLater(() -> agregarLinea(de + ": " + texto));
    }

    @Override
    public void alRecibirPrivado(String de, String texto) {
        SwingUtilities.invokeLater(() ->
            agregarLinea("(privado) " + de + ": " + texto));
    }

    @Override
    public void alRecibirImagen(String de, BufferedImage imagen) {
        // Fase 3: se implementará cuando Camara.java esté listo
        SwingUtilities.invokeLater(() -> agregarLinea("[imagen de " + de + "]"));
    }

    @Override
    public void alRecibirInfo(String texto) {
        SwingUtilities.invokeLater(() -> agregarLinea("[INFO] " + texto));
    }

    @Override
    public void alActualizarUsuarios(String[] usuarios) {
        SwingUtilities.invokeLater(() -> {
            modeloUsuarios.clear();
            for (String u : usuarios) {
                if (!u.trim().isEmpty()) {
                    modeloUsuarios.addElement(u.trim());
                }
            }
        });
    }

    @Override
    public void alRecibirError(String texto) {
        SwingUtilities.invokeLater(() ->
            JOptionPane.showMessageDialog(this, texto,
                "Error del servidor", JOptionPane.ERROR_MESSAGE));
    }

    @Override
    public void alDesconectarse() {
        SwingUtilities.invokeLater(() -> {
            agregarLinea("[INFO] Se perdió la conexión con el servidor.");
            desactivarEnvio();
        });
    }
}
