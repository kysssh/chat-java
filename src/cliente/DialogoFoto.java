package cliente;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * El "espejo" antes de la foto: la cámara en vivo para acomodarse, un botón para tomarla,
 * y la foto congelada para revisarla antes de enviarla (o repetirla).
 * Lo que se ve al revisar es exactamente lo que se envía.
 */
class DialogoFoto extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final int CUADROS_POR_SEGUNDO = 20;

    /** Lo que devuelve el diálogo. foto es null si se canceló. */
    static class Resultado {
        final BufferedImage foto;
        final String camara;   // la cámara que quedó elegida (null = predeterminada)

        Resultado(BufferedImage foto, String camara) {
            this.foto = foto;
            this.camara = camara;
        }
    }

    private final VistaVideo vista = new VistaVideo(560, 420, 16, false);
    private final JComboBox<DialogoDispositivos.Opcion<String>> comboCamara = Estilo.combo();
    private final Estilo.Boton botonEspejo =
            new Estilo.Boton("Espejo", new Estilo.Icono(Estilo.Icono.Tipo.ESPEJO, 15), Estilo.SUPERFICIE_2, Estilo.TEXTO);
    private final Estilo.Boton botonTomar =
            new Estilo.Boton("Tomar foto", new Estilo.Icono(Estilo.Icono.Tipo.CAMARA, 16), Estilo.ACENTO, Color.WHITE);
    private final Estilo.Boton botonRepetir =
            new Estilo.Boton("Repetir", null, Estilo.SUPERFICIE, Estilo.TEXTO);
    private final Estilo.Boton botonEnviar =
            new Estilo.Boton("Enviar foto", new Estilo.Icono(Estilo.Icono.Tipo.ENVIAR, 15), Estilo.ACENTO, Color.WHITE);
    private final JLabel lblAyuda = Estilo.etiqueta(" ", Font.PLAIN, 13, Estilo.TEXTO_SUAVE);
    private final JPanel botones = new JPanel(new CardLayout());

    private Camara.EnVivo camara;
    private String camaraActual;
    private boolean espejo = true;
    private volatile boolean revisando = false;
    private volatile BufferedImage ultimoCuadro;   // lo último que mandó la cámara
    private BufferedImage fotoTomada;               // la que se enviaría (ya volteada si hay espejo)
    private boolean camarasCargadas = false;
    private Resultado resultado;

    /** Muestra el espejo y espera. Devuelve la foto elegida, o un Resultado con foto null si se canceló. */
    static Resultado pedir(Frame duenio, String camaraElegida) {
        DialogoFoto d = new DialogoFoto(duenio, camaraElegida);
        d.setVisible(true);   // modal: se queda aquí hasta que se cierre
        return d.resultado != null ? d.resultado : new Resultado(null, d.camaraActual);
    }

    private DialogoFoto(Frame duenio, String camaraElegida) {
        super(duenio, "Tomar foto", true);
        this.camaraActual = camaraElegida;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setIconImages(Estilo.iconosVentana());

        JPanel contenido = new JPanel(new BorderLayout(0, 14));
        contenido.setBackground(Estilo.PANEL);
        contenido.setBorder(new EmptyBorder(20, 22, 20, 22));
        setContentPane(contenido);

        // ---- Arriba: título y ayuda ----
        JPanel arriba = new JPanel(new GridLayout(2, 1, 0, 2));
        arriba.setOpaque(false);
        arriba.add(Estilo.etiqueta("Tomar foto", Font.BOLD, 18, Estilo.TEXTO));
        arriba.add(lblAyuda);
        contenido.add(arriba, BorderLayout.NORTH);

        // ---- Centro: la cámara ----
        vista.setEspejo(espejo);
        vista.setMinimumSize(new Dimension(320, 240));
        contenido.add(vista, BorderLayout.CENTER);

        // ---- Abajo: cámara + espejo a la izquierda, acciones a la derecha ----
        JPanel abajo = new JPanel(new BorderLayout(12, 0));
        abajo.setOpaque(false);

        JPanel opciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        opciones.setOpaque(false);
        comboCamara.setPreferredSize(new Dimension(230, comboCamara.getPreferredSize().height + 6));
        comboCamara.setToolTipText("Elige la cámara");
        opciones.add(comboCamara);
        opciones.add(botonEspejo);
        abajo.add(opciones, BorderLayout.WEST);

        Estilo.Boton cancelar = new Estilo.Boton("Cancelar", null, Estilo.SUPERFICIE, Estilo.TEXTO);
        JPanel enVivo = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        enVivo.setOpaque(false);
        enVivo.add(cancelar);
        enVivo.add(botonTomar);
        JPanel revisar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        revisar.setOpaque(false);
        revisar.add(botonRepetir);
        revisar.add(botonEnviar);
        botones.setOpaque(false);
        botones.add(enVivo, "vivo");
        botones.add(revisar, "revisar");
        abajo.add(botones, BorderLayout.EAST);
        contenido.add(abajo, BorderLayout.SOUTH);

        // ---- Acciones ----
        cancelar.addActionListener(e -> dispose());
        botonTomar.addActionListener(e -> tomar());
        botonRepetir.addActionListener(e -> repetir());
        botonEnviar.addActionListener(e -> enviar());
        botonEspejo.addActionListener(e -> alternarEspejo());
        comboCamara.addActionListener(e -> {
            if (camarasCargadas && !revisando) {
                DialogoDispositivos.Opcion<String> o = comboCamara.getItemAt(comboCamara.getSelectedIndex());
                cambiarCamara(o == null ? null : o.valor);
            }
        });
        actualizarEspejo();

        // Espacio = tomar, Enter = tomar o enviar, Esc = cancelar
        getRootPane().registerKeyboardAction(e -> (revisando ? botonEnviar : botonTomar).doClick(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> {
            if (!revisando) {
                botonTomar.doClick();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Al cerrar (Enviar, Cancelar o la X) se apaga la cámara
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                apagarCamara();
            }
        });

        cargarCamaras();
        cambiarCamara(camaraActual);

        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(duenio);
    }

    // ================================================================
    //  Cámara en vivo
    // ================================================================

    private void cambiarCamara(String nombre) {
        apagarCamara();
        camaraActual = nombre;
        ultimoCuadro = null;
        botonTomar.setEnabled(false);
        vista.sinImagen(null, "Abriendo la cámara…");
        lblAyuda.setText("Acomódate: así va a salir la foto.");
        camara = Camara.EnVivo.iniciar(nombre, CUADROS_POR_SEGUNDO, this::alCuadro, this::alErrorCamara);
    }

    /** Desde el hilo de la cámara. */
    private void alCuadro(BufferedImage cuadro) {
        ultimoCuadro = cuadro;
        if (!revisando) {
            vista.mostrar(cuadro);
            if (!botonTomar.isEnabled()) {
                SwingUtilities.invokeLater(() -> botonTomar.setEnabled(!revisando));
            }
        }
    }

    /** Desde el hilo de la cámara. */
    private void alErrorCamara(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            botonTomar.setEnabled(false);
            vista.sinImagen(null, mensaje);
            lblAyuda.setText("Prueba con otra cámara de la lista, o cierra otros programas que la usen.");
        });
    }

    private void apagarCamara() {
        if (camara != null) {
            camara.detener();
            camara = null;
        }
    }

    private void cargarCamaras() {
        comboCamara.addItem(new DialogoDispositivos.Opcion<>("Buscando cámaras…", null));
        comboCamara.setEnabled(false);
        new Thread(() -> {
            List<String> camaras = Camara.camaras();
            SwingUtilities.invokeLater(() -> {
                comboCamara.removeAllItems();
                comboCamara.addItem(new DialogoDispositivos.Opcion<>("Cámara predeterminada", null));
                for (String nombre : camaras) {
                    comboCamara.addItem(new DialogoDispositivos.Opcion<>(nombre, nombre));
                    if (nombre.equals(camaraActual)) {
                        comboCamara.setSelectedIndex(comboCamara.getItemCount() - 1);
                    }
                }
                comboCamara.setEnabled(camaras.size() > 0 && !revisando);
                camarasCargadas = true;   // recién ahora el combo cambia de cámara al elegir
            });
        }, "hilo-buscar-camaras").start();
    }

    // ================================================================
    //  Espejo, tomar, repetir, enviar
    // ================================================================

    private void alternarEspejo() {
        espejo = !espejo;
        actualizarEspejo();
    }

    private void actualizarEspejo() {
        vista.setEspejo(espejo);
        botonEspejo.setFondo(espejo ? Estilo.conAlfa(Estilo.ACENTO, 90) : Estilo.SUPERFICIE_2);
        botonEspejo.setToolTipText(espejo
                ? "Espejo activado: te ves (y sales) como en un espejo. Clic para desactivarlo."
                : "Espejo desactivado: sales como te ven los demás. Clic para activarlo.");
    }

    private void tomar() {
        BufferedImage cuadro = ultimoCuadro;
        if (cuadro == null) {
            return;
        }
        // La foto sale igual que se ve en la vista: volteada si el espejo está activo
        fotoTomada = espejo ? Camara.espejo(cuadro) : Camara.copia(cuadro);
        revisando = true;
        vista.setEspejo(false);   // fotoTomada ya viene volteada
        vista.mostrar(fotoTomada);
        vista.destellar();
        comboCamara.setEnabled(false);
        botonEspejo.setEnabled(false);
        lblAyuda.setText("¿Te gusta? Así la verán los demás.");
        ((CardLayout) botones.getLayout()).show(botones, "revisar");
        botonEnviar.requestFocusInWindow();
    }

    private void repetir() {
        fotoTomada = null;
        revisando = false;
        vista.setEspejo(espejo);
        BufferedImage cuadro = ultimoCuadro;
        if (cuadro != null) {
            vista.mostrar(cuadro);
        }
        comboCamara.setEnabled(camarasCargadas && comboCamara.getItemCount() > 1);
        botonEspejo.setEnabled(true);
        lblAyuda.setText("Acomódate: así va a salir la foto.");
        ((CardLayout) botones.getLayout()).show(botones, "vivo");
        botonTomar.setEnabled(cuadro != null);
        botonTomar.requestFocusInWindow();
    }

    private void enviar() {
        if (fotoTomada == null) {
            return;
        }
        resultado = new Resultado(fotoTomada, camaraActual);
        dispose();
    }
}
