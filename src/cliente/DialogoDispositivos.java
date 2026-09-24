package cliente;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Ventana para elegir la cámara y el micrófono (por si hay varios conectados).
 * La cámara se prueba con una foto de muestra y el micrófono con una barra de volumen.
 */
class DialogoDispositivos extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final String AYUDA_MICROFONO = "<html>Habla para probar: la barra debe moverse.<br>"
            + "Si no se mueve, revisa Configuración › Privacidad › Micrófono.</html>";

    /** Lo elegido. null en cualquiera de los dos = el predeterminado del sistema. */
    static class Eleccion {
        final String camara;
        final Mixer.Info microfono;

        Eleccion(String camara, Mixer.Info microfono) {
            this.camara = camara;
            this.microfono = microfono;
        }
    }

    /** Un elemento de la lista desplegable: el texto que se ve y el valor que representa. */
    private static class Opcion<T> {
        final String texto;
        final T valor;

        Opcion(String texto, T valor) {
            this.texto = texto;
            this.valor = valor;
        }

        @Override
        public String toString() {
            return texto;
        }
    }

    private final JComboBox<Opcion<String>> comboCamara = Estilo.combo();
    private final JComboBox<Opcion<Mixer.Info>> comboMicrofono = Estilo.combo();
    private final Estilo.Boton botonProbar =
            new Estilo.Boton("Probar", new Estilo.Icono(Estilo.Icono.Tipo.CAMARA, 15), Estilo.SUPERFICIE_2, Estilo.TEXTO);
    private final Vista vista = new Vista();
    private final Estilo.Medidor medidor = new Estilo.Medidor(320, 8);
    private final JLabel lblMicrofono = Estilo.etiqueta(AYUDA_MICROFONO, Font.PLAIN, 12, Estilo.TEXTO_SUAVE);
    private Audio.Grabacion prueba;          // el micrófono abierto solo para mover la barra
    private final String camaraAnterior;
    private boolean camarasCargadas = false;
    private Eleccion resultado;

    /** Muestra la ventana y espera. Devuelve lo elegido, o null si se canceló. */
    static Eleccion pedir(Frame duenio, String camaraActual, Mixer.Info microfonoActual) {
        DialogoDispositivos d = new DialogoDispositivos(duenio, camaraActual, microfonoActual);
        d.setVisible(true);   // modal: se queda aquí hasta que se cierre
        return d.resultado;
    }

    private DialogoDispositivos(Frame duenio, String camaraActual, Mixer.Info microfonoActual) {
        super(duenio, "Dispositivos", true);
        this.camaraAnterior = camaraActual;
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setBackground(Estilo.PANEL);
        contenido.setBorder(new EmptyBorder(22, 26, 22, 26));
        setContentPane(contenido);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        c.weightx = 1;

        c.insets = new Insets(0, 0, 4, 0);
        contenido.add(Estilo.etiqueta("Dispositivos", Font.BOLD, 18, Estilo.TEXTO), c);
        c.insets = new Insets(0, 0, 18, 0);
        contenido.add(Estilo.etiqueta("Elige qué cámara y qué micrófono usa el chat.",
                Font.PLAIN, 13, Estilo.TEXTO_SUAVE), c);

        // ---- Cámara ----
        c.insets = new Insets(0, 0, 6, 0);
        contenido.add(seccion("CÁMARA", Estilo.Icono.Tipo.CAMARA), c);
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 10, 8);
        contenido.add(comboCamara, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 10, 0);
        contenido.add(botonProbar, c);
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 20, 0);
        contenido.add(vista, c);

        // ---- Micrófono ----
        c.insets = new Insets(0, 0, 6, 0);
        contenido.add(seccion("MICRÓFONO", Estilo.Icono.Tipo.MICROFONO), c);
        c.insets = new Insets(0, 0, 10, 0);
        contenido.add(comboMicrofono, c);
        c.insets = new Insets(0, 0, 6, 0);
        contenido.add(medidor, c);
        c.insets = new Insets(0, 0, 22, 0);
        contenido.add(lblMicrofono, c);

        // ---- Botones ----
        Estilo.Boton cancelar = new Estilo.Boton("Cancelar", null, Estilo.SUPERFICIE, Estilo.TEXTO);
        Estilo.Boton guardar = new Estilo.Boton("Guardar", null, Estilo.ACENTO, Color.WHITE);
        cancelar.addActionListener(e -> dispose());
        guardar.addActionListener(e -> guardar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(guardar);
        c.insets = new Insets(0, 0, 0, 0);
        contenido.add(botones, c);
        getRootPane().setDefaultButton(guardar);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        cargarMicrofonos(microfonoActual);
        cargarCamaras(camaraActual);
        botonProbar.addActionListener(e -> probarCamara());
        comboMicrofono.addActionListener(e -> probarMicrofono());

        // Al cerrar (con Guardar, Cancelar o la X) se suelta el micrófono
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                probarMicrofono();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                soltarMicrofono();
            }
        });

        pack();
        setLocationRelativeTo(duenio);
    }

    private static JLabel seccion(String texto, Estilo.Icono.Tipo icono) {
        JLabel l = Estilo.etiqueta(texto, Font.BOLD, 11, Estilo.TEXTO_SUAVE);
        l.setIcon(new Estilo.Icono(icono, 13));
        l.setIconTextGap(6);
        return l;
    }

    private void guardar() {
        Opcion<String> cam = comboCamara.getItemAt(comboCamara.getSelectedIndex());
        Opcion<Mixer.Info> mic = comboMicrofono.getItemAt(comboMicrofono.getSelectedIndex());
        // Si todavía no terminó de buscar cámaras, se queda la que estaba
        String camara = camarasCargadas ? (cam == null ? null : cam.valor) : camaraAnterior;
        resultado = new Eleccion(camara, mic == null ? null : mic.valor);
        dispose();
    }

    // ================================================================
    //  Cámara
    // ================================================================

    /** Buscar cámaras tarda (carga la librería): se hace en otro hilo y mientras se avisa. */
    private void cargarCamaras(String actual) {
        comboCamara.addItem(new Opcion<>("Buscando cámaras…", null));
        comboCamara.setEnabled(false);
        botonProbar.setEnabled(false);
        vista.mostrarTexto("Buscando cámaras…");
        new Thread(() -> {
            List<String> camaras = Camara.camaras();
            SwingUtilities.invokeLater(() -> {
                comboCamara.removeAllItems();
                camarasCargadas = true;
                if (camaras.isEmpty()) {
                    comboCamara.addItem(new Opcion<>("No se encontró ninguna cámara", null));
                    vista.mostrarTexto("Conecta una cámara y vuelve a abrir esta ventana.");
                    return;
                }
                comboCamara.addItem(new Opcion<>("Predeterminada del sistema", null));
                for (String nombre : camaras) {
                    comboCamara.addItem(new Opcion<>(nombre, nombre));
                    if (nombre.equals(actual)) {
                        comboCamara.setSelectedIndex(comboCamara.getItemCount() - 1);
                    }
                }
                comboCamara.setEnabled(true);
                botonProbar.setEnabled(true);
                vista.mostrarTexto("Pulsa Probar para ver la imagen de la cámara elegida.");
            });
        }, "hilo-buscar-camaras").start();
    }

    private void probarCamara() {
        Opcion<String> elegida = comboCamara.getItemAt(comboCamara.getSelectedIndex());
        String nombre = elegida == null ? null : elegida.valor;
        botonProbar.setEnabled(false);
        comboCamara.setEnabled(false);
        vista.mostrarTexto("Abriendo la cámara…");
        new Thread(() -> {
            BufferedImage foto = Camara.tomarFoto(nombre);
            SwingUtilities.invokeLater(() -> {
                botonProbar.setEnabled(true);
                comboCamara.setEnabled(true);
                if (foto == null) {
                    vista.mostrarTexto("No se pudo abrir esta cámara (¿la está usando otro programa?).");
                } else {
                    vista.mostrarImagen(foto);
                }
            });
        }, "hilo-probar-camara").start();
    }

    // ================================================================
    //  Micrófono
    // ================================================================

    private void cargarMicrofonos(Mixer.Info actual) {
        comboMicrofono.addItem(new Opcion<>("Predeterminado del sistema", null));
        for (Mixer.Info info : Audio.microfonos()) {
            comboMicrofono.addItem(new Opcion<>(info.getName(), info));
            if (actual != null && info.getName().equals(actual.getName())) {
                comboMicrofono.setSelectedIndex(comboMicrofono.getItemCount() - 1);
            }
        }
    }

    /** Abre el micrófono elegido para que la barra muestre si capta sonido. */
    private void probarMicrofono() {
        soltarMicrofono();
        medidor.setNivel(0);
        Opcion<Mixer.Info> elegido = comboMicrofono.getItemAt(comboMicrofono.getSelectedIndex());
        try {
            prueba = Audio.Grabacion.iniciar(elegido == null ? null : elegido.valor,
                    nivel -> SwingUtilities.invokeLater(() -> medidor.setNivel(nivel)));
            lblMicrofono.setForeground(Estilo.TEXTO_SUAVE);
            lblMicrofono.setText(AYUDA_MICROFONO);
        } catch (LineUnavailableException | RuntimeException e) {
            lblMicrofono.setForeground(Estilo.PELIGRO);
            lblMicrofono.setText("No se pudo abrir este micrófono.");
        }
    }

    private void soltarMicrofono() {
        if (prueba != null) {
            prueba.cancelar();
            prueba = null;
        }
    }

    // ================================================================
    //  Recuadro de vista previa
    // ================================================================

    private static class Vista extends JComponent {
        private static final long serialVersionUID = 1L;
        private BufferedImage imagen;
        private String texto = "";

        Vista() {
            setPreferredSize(new Dimension(360, 210));
        }

        void mostrarTexto(String t) {
            texto = t;
            imagen = null;
            repaint();
        }

        void mostrarImagen(BufferedImage img) {
            imagen = img;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = Estilo.suave(g);
            RoundRectangle2D forma = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14);
            g2.setColor(Estilo.FONDO);
            g2.fill(forma);
            if (imagen != null) {
                // Imagen entera, centrada, sin deformar
                double escala = Math.min((double) getWidth() / imagen.getWidth(),
                        (double) getHeight() / imagen.getHeight());
                int w = (int) (imagen.getWidth() * escala);
                int h = (int) (imagen.getHeight() * escala);
                g2.clip(forma);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(imagen, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            } else {
                g2.setColor(Estilo.TEXTO_SUAVE);
                g2.setFont(Estilo.fuente(Font.PLAIN, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(texto, (getWidth() - fm.stringWidth(texto)) / 2,
                        (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            }
            g2.dispose();
        }
    }
}
