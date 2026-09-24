package cliente;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * La ventana principal del chat (Swing).
 * Implementa OyenteMensajes para recibir eventos de la red.
 */
public class VentanaChat extends JFrame implements OyenteMensajes {

    private static final long serialVersionUID = 1L;
    private static final String TEXTO_AYUDA_TODOS = "Escribe un mensaje para todos…";

    private final String nombre;
    private final String host;
    private final int puerto;
    private final String tituloBase;
    private final ClienteRed clienteRed;

    // ---- Componentes de la ventana ----
    private PanelMensajes panelMensajes;
    private Estilo.Campo campoTexto;
    private JButton botonEnviar;
    private JButton botonPrivado;
    private JButton botonImagen;
    private JButton botonCamara;
    private DefaultListModel<String> modeloUsuarios;
    private JList<String> listaUsuarios;
    private JLabel lblEstado;
    private JLabel lblConectados;

    private boolean desconectado = false;   // solo se usa en el hilo de la ventana
    private int sinLeer = 0;                // mensajes que llegaron con la ventana en segundo plano

    public VentanaChat(String nombre, String host, int puerto) {
        this.nombre = nombre;
        this.host = host;
        this.puerto = puerto;
        this.tituloBase = "Chat — " + nombre + " @ " + host + ":" + puerto;
        setTitle(tituloBase);

        // Crear la conexión de red (la ventana es el oyente)
        clienteRed = new ClienteRed(this);
        construirInterfaz();
        conectarEnSegundoPlano();
    }

    /** Conectar puede tardar unos segundos: se hace fuera del hilo de la ventana. */
    private void conectarEnSegundoPlano() {
        ponerEstado("Conectando a " + host + ":" + puerto + "…", Estilo.ESPERA);
        setEnvioActivo(false);
        new Thread(() -> {
            boolean ok = clienteRed.conectar(host, puerto, nombre);
            SwingUtilities.invokeLater(() -> {
                if (!ok) {
                    desconectado = true;
                    ponerEstado("Sin conexión", Estilo.PELIGRO);
                    panelMensajes.agregarError("No se pudo conectar a " + host + ":" + puerto
                            + ". ¿Está prendido el servidor?");
                } else if (!desconectado) {   // el servidor pudo cerrar la conexión al instante
                    ponerEstado("En línea · " + host + ":" + puerto, Estilo.EXITO);
                    setEnvioActivo(true);
                    campoTexto.requestFocusInWindow();
                }
            });
        }, "hilo-conexion").start();
    }

    // ================================================================
    //  Interfaz
    // ================================================================

    /** Arma toda la interfaz gráfica */
    private void construirInterfaz() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setIconImages(Estilo.iconosVentana());
        setSize(900, 620);
        setMinimumSize(new Dimension(640, 420));
        setLocationRelativeTo(null);

        JPanel raiz = new JPanel(new BorderLayout());
        raiz.setBackground(Estilo.FONDO);
        setContentPane(raiz);

        panelMensajes = new PanelMensajes(nombre);
        raiz.add(crearCabecera(), BorderLayout.NORTH);
        raiz.add(panelMensajes.getScroll(), BorderLayout.CENTER);
        raiz.add(crearBarraLateral(), BorderLayout.EAST);
        raiz.add(crearBarraEntrada(), BorderLayout.SOUTH);

        // ===== Acciones =====
        botonEnviar.addActionListener(e -> accionEnviar());
        campoTexto.addActionListener(e -> accionEnviar()); // Enter también envía
        botonPrivado.addActionListener(e -> accionPrivado());
        botonImagen.addActionListener(e -> accionEnviarArchivoImagen());
        botonCamara.addActionListener(e -> accionEnviarFoto());

        // Escape: quitar al destinatario del privado
        raiz.registerKeyboardAction(e -> listaUsuarios.clearSelection(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        addWindowListener(new WindowAdapter() {
            // Al cerrar la ventana → desconectar limpiamente
            @Override
            public void windowClosing(WindowEvent e) {
                clienteRed.desconectar();
                dispose();
                System.exit(0);
            }

            // Al volver a la ventana, se da por leído
            @Override
            public void windowActivated(WindowEvent e) {
                sinLeer = 0;
                setTitle(tituloBase);
            }
        });

        setVisible(true);
    }

    /** Arriba: avatar, nombre y estado de la conexión. */
    private JComponent crearCabecera() {
        JPanel cabecera = new JPanel(new BorderLayout(12, 0));
        cabecera.setBackground(Estilo.PANEL);
        cabecera.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Estilo.BORDE),
                new EmptyBorder(12, 18, 12, 18)));

        cabecera.add(new Estilo.Avatar(nombre, 38), BorderLayout.WEST);

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 1));
        textos.setOpaque(false);
        textos.add(Estilo.etiqueta(nombre, Font.BOLD, 15, Estilo.TEXTO));
        lblEstado = Estilo.etiqueta(" ", Font.PLAIN, 12, Estilo.TEXTO_SUAVE);
        lblEstado.setIconTextGap(6);
        textos.add(lblEstado);
        cabecera.add(textos, BorderLayout.CENTER);
        return cabecera;
    }

    /** Derecha: lista de conectados con avatares. */
    private JComponent crearBarraLateral() {
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setBackground(Estilo.PANEL);
        lateral.setPreferredSize(new Dimension(220, 0));
        lateral.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Estilo.BORDE));

        lblConectados = Estilo.etiqueta("EN LÍNEA", Font.BOLD, 11, Estilo.TEXTO_SUAVE);
        lblConectados.setBorder(new EmptyBorder(16, 18, 8, 18));
        lateral.add(lblConectados, BorderLayout.NORTH);

        modeloUsuarios = new DefaultListModel<>();
        listaUsuarios = new JList<>(modeloUsuarios);
        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaUsuarios.setBackground(Estilo.PANEL);
        listaUsuarios.setCellRenderer(new CeldaUsuario());
        listaUsuarios.setFixedCellHeight(44);
        listaUsuarios.addListSelectionListener(e -> actualizarDestinatario());
        lateral.add(Estilo.scroll(listaUsuarios, Estilo.PANEL), BorderLayout.CENTER);

        JTextArea ayuda = new JTextArea("Elige a alguien de la lista y pulsa Privado "
                + "para que solo esa persona lo lea. Esc para quitarlo.");
        ayuda.setEditable(false);
        ayuda.setFocusable(false);
        ayuda.setLineWrap(true);
        ayuda.setWrapStyleWord(true);
        ayuda.setOpaque(false);
        ayuda.setFont(Estilo.fuente(Font.PLAIN, 11));
        ayuda.setForeground(Estilo.TEXTO_SUAVE);
        ayuda.setBorder(new EmptyBorder(10, 18, 14, 18));
        lateral.add(ayuda, BorderLayout.SOUTH);
        return lateral;
    }

    /** Abajo: botones de imagen y cámara, campo de texto, Privado y Enviar. */
    private JComponent crearBarraEntrada() {
        JPanel barra = new JPanel(new BorderLayout(10, 0));
        barra.setBackground(Estilo.PANEL);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Estilo.BORDE),
                new EmptyBorder(12, 14, 12, 14)));

        botonImagen = new Estilo.Boton("", new Estilo.Icono(Estilo.Icono.Tipo.IMAGEN, 18),
                Estilo.SUPERFICIE, Estilo.TEXTO);
        botonImagen.setToolTipText("Enviar una imagen desde un archivo");
        botonCamara = new Estilo.Boton("", new Estilo.Icono(Estilo.Icono.Tipo.CAMARA, 18),
                Estilo.SUPERFICIE, Estilo.TEXTO);
        botonCamara.setToolTipText("Tomar una foto con la cámara y enviarla");

        JPanel izquierda = new JPanel(new GridLayout(1, 2, 6, 0));
        izquierda.setOpaque(false);
        izquierda.add(botonImagen);
        izquierda.add(botonCamara);

        campoTexto = new Estilo.Campo(TEXTO_AYUDA_TODOS);

        botonPrivado = new Estilo.Boton("Privado", new Estilo.Icono(Estilo.Icono.Tipo.CANDADO, 14),
                Estilo.SUPERFICIE, Estilo.PRIVADO);
        botonEnviar = new Estilo.Boton("Enviar", new Estilo.Icono(Estilo.Icono.Tipo.ENVIAR, 15),
                Estilo.ACENTO, Color.WHITE);
        botonEnviar.setToolTipText("Enviar a todos (Enter)");

        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        derecha.setOpaque(false);
        derecha.add(botonPrivado);
        derecha.add(botonEnviar);

        barra.add(izquierda, BorderLayout.WEST);
        barra.add(campoTexto, BorderLayout.CENTER);
        barra.add(derecha, BorderLayout.EAST);
        actualizarDestinatario();
        return barra;
    }

    /** El texto de ayuda y el tooltip de Privado dicen a quién va el privado. */
    private void actualizarDestinatario() {
        if (campoTexto == null || botonPrivado == null) {
            return;
        }
        String elegido = listaUsuarios.getSelectedValue();
        if (elegido == null) {
            campoTexto.setAyuda(TEXTO_AYUDA_TODOS);
            botonPrivado.setToolTipText("Primero elige a alguien en la lista de la derecha");
        } else {
            campoTexto.setAyuda("Enter = a todos  ·  Privado = solo a " + elegido);
            botonPrivado.setToolTipText("Enviar en privado a " + elegido);
        }
    }

    private void ponerEstado(String texto, Color color) {
        lblEstado.setText(texto);
        lblEstado.setIcon(Estilo.punto(color));
    }

    /** Activa o desactiva los botones de envío */
    private void setEnvioActivo(boolean activo) {
        botonEnviar.setEnabled(activo);
        botonPrivado.setEnabled(activo);
        botonImagen.setEnabled(activo);
        botonCamara.setEnabled(activo);
        campoTexto.setEnabled(activo);
    }

    // ================================================================
    //  Acciones de los botones
    // ================================================================

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
            campoTexto.requestFocusInWindow();
            return;
        }
        clienteRed.enviarPrivado(seleccionado, texto);
        // El servidor no devuelve el privado al que lo manda: se pinta aquí para verlo en el chat
        panelMensajes.agregarPrivadoEnviado(seleccionado, texto);
        campoTexto.setText("");
        campoTexto.requestFocusInWindow();
    }

    /** Abre un JFileChooser para seleccionar una imagen y enviarla */
    private void accionEnviarArchivoImagen() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Seleccionar imagen");
        fc.setFileFilter(new FileNameExtensionFilter(
                "Imágenes (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp"));

        int resultado = fc.showOpenDialog(this);
        if (resultado != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = fc.getSelectedFile();
        // Leer y achicar una foto grande tarda: en hilo aparte para no congelar la ventana
        new Thread(() -> {
            try {
                BufferedImage img = ImageIO.read(archivo);
                if (img != null) {
                    clienteRed.enviarImagen(img);
                } else {
                    SwingUtilities.invokeLater(() ->
                        panelMensajes.agregarError("No se pudo leer la imagen " + archivo.getName()));
                }
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() ->
                    panelMensajes.agregarError("Error al leer el archivo: " + ex.getMessage()));
            }
        }, "hilo-imagen").start();
    }

    /** Toma una foto con la cámara y la envía (en hilo aparte para no congelar) */
    private void accionEnviarFoto() {
        // Desactivado mientras se toma la foto: dos clics seguidos abrirían la cámara dos veces a la vez
        botonCamara.setEnabled(false);
        botonCamara.setToolTipText("Tomando la foto…");
        new Thread(() -> {
            BufferedImage foto = Camara.tomarFoto();
            SwingUtilities.invokeLater(() -> {
                botonCamara.setEnabled(campoTexto.isEnabled());
                botonCamara.setToolTipText("Tomar una foto con la cámara y enviarla");
                if (foto == null) {
                    panelMensajes.agregarError("No se encontró cámara o no se pudo tomar la foto.");
                }
            });
            if (foto != null) {
                clienteRed.enviarImagen(foto);
            }
        }, "hilo-camara").start();
    }

    /** Abre una ventanita con la imagen recibida */
    private void mostrarImagen(String de, BufferedImage img) {
        JFrame ventanaImg = new JFrame("Imagen de " + de);
        ventanaImg.setIconImages(Estilo.iconosVentana());
        ventanaImg.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        JLabel label = new JLabel(new ImageIcon(img));
        label.setBorder(new EmptyBorder(12, 12, 12, 12));
        label.setOpaque(true);
        label.setBackground(Estilo.FONDO);
        ventanaImg.add(label);
        ventanaImg.pack();
        ventanaImg.setLocationRelativeTo(this);
        ventanaImg.setVisible(true);
    }

    /** Cuenta los mensajes que llegan sin estar mirando la ventana: "(3) Chat — …". */
    private void avisarNuevo() {
        if (!isActive()) {
            sinLeer++;
            setTitle("(" + sinLeer + ") " + tituloBase);
        }
    }

    // ================================================================
    //  Implementación de OyenteMensajes
    //  ⚠️ Estos métodos se llaman desde el hilo de RED,
    //     así que todo cambio a la ventana va con invokeLater.
    // ================================================================

    @Override
    public void alRecibirMensaje(String de, String texto) {
        SwingUtilities.invokeLater(() -> {
            panelMensajes.agregarMensaje(de, texto);
            avisarNuevo();
        });
    }

    @Override
    public void alRecibirPrivado(String de, String texto) {
        SwingUtilities.invokeLater(() -> {
            panelMensajes.agregarPrivadoRecibido(de, texto);
            avisarNuevo();
        });
    }

    @Override
    public void alRecibirImagen(String de, BufferedImage imagen) {
        SwingUtilities.invokeLater(() -> {
            panelMensajes.agregarImagen(de, imagen, () -> mostrarImagen(de, imagen));
            avisarNuevo();
        });
    }

    @Override
    public void alRecibirInfo(String texto) {
        SwingUtilities.invokeLater(() -> panelMensajes.agregarAviso(texto));
    }

    @Override
    public void alActualizarUsuarios(String[] usuarios) {
        SwingUtilities.invokeLater(() -> {
            // Recordar a quién se había elegido: si no, cada vez que alguien entra o sale
            // se pierde la selección para el privado
            String seleccionado = listaUsuarios.getSelectedValue();
            modeloUsuarios.clear();
            for (String u : usuarios) {
                if (!u.trim().isEmpty()) {
                    modeloUsuarios.addElement(u.trim());
                }
            }
            if (seleccionado != null) {
                listaUsuarios.setSelectedValue(seleccionado, false);
            }
            lblConectados.setText("EN LÍNEA — " + modeloUsuarios.size());
            actualizarDestinatario();
        });
    }

    @Override
    public void alRecibirError(String texto) {
        SwingUtilities.invokeLater(() -> panelMensajes.agregarError("Error del servidor: " + texto));
    }

    @Override
    public void alDesconectarse() {
        SwingUtilities.invokeLater(() -> {
            if (desconectado) {
                return;
            }
            desconectado = true;
            panelMensajes.agregarError("Se perdió la conexión con el servidor.");
            ponerEstado("Desconectado", Estilo.PELIGRO);
            setEnvioActivo(false);
        });
    }

    // ================================================================
    //  Cómo se dibuja cada usuario de la lista
    // ================================================================

    private class CeldaUsuario extends JComponent implements ListCellRenderer<String> {
        private static final long serialVersionUID = 1L;
        private String usuario = "";
        private boolean elegido;

        @Override
        public Component getListCellRendererComponent(JList<? extends String> lista, String valor,
                                                      int indice, boolean seleccionado, boolean foco) {
            usuario = valor;
            elegido = seleccionado;
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Estilo.suave(g);
            int alto = getHeight();
            if (elegido) {
                g2.setColor(Estilo.conAlfa(Estilo.PRIVADO, 40));
                g2.fill(new RoundRectangle2D.Float(8, 3, getWidth() - 16, alto - 6, 10, 10));
            }
            int d = 30;
            int x = 18;
            int y = (alto - d) / 2;
            Estilo.pintarAvatar(g2, usuario, x, y, d);
            // puntito verde de "en línea"
            g2.setColor(Estilo.PANEL);
            g2.fill(new Ellipse2D.Float(x + d - 10, y + d - 10, 12, 12));
            g2.setColor(Estilo.EXITO);
            g2.fill(new Ellipse2D.Float(x + d - 8, y + d - 8, 8, 8));

            g2.setFont(Estilo.fuentePara(usuario, elegido ? Font.BOLD : Font.PLAIN, 13));
            FontMetrics fm = g2.getFontMetrics();
            int base = (alto - fm.getHeight()) / 2 + fm.getAscent();
            int tx = x + d + 12;
            g2.setColor(elegido ? Estilo.PRIVADO : Estilo.TEXTO);
            g2.drawString(usuario, tx, base);
            if (usuario.equals(nombre)) {
                int ancho = fm.stringWidth(usuario);
                g2.setFont(Estilo.fuente(Font.PLAIN, 12));
                g2.setColor(Estilo.TEXTO_SUAVE);
                g2.drawString("(tú)", tx + ancho + 6, base);
            }
            g2.dispose();
        }
    }
}
