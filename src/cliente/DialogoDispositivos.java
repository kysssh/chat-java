package cliente;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;

/**
 * Ventana para elegir la cámara y el micrófono (por si hay varios conectados).
 * La cámara elegida se ve en vivo y el micrófono mueve una barra de volumen.
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
    static class Opcion<T> {   // también la usan DialogoFoto y VentanaLlamada
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
    private final VistaVideo vista = new VistaVideo(360, 240, 14, false);
    private Camara.EnVivo camaraPrueba;      // la cámara prendida solo para verla aquí
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
        c.insets = new Insets(0, 0, 10, 0);
        contenido.add(comboCamara, c);
        c.insets = new Insets(0, 0, 20, 0);
        vista.setEspejo(true);   // uno se ve como en un espejo
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
        comboCamara.addActionListener(e -> {
            if (camarasCargadas) {
                verCamara();
            }
        });
        comboMicrofono.addActionListener(e -> probarMicrofono());

        // Al cerrar (con Guardar, Cancelar o la X) se sueltan el micrófono y la cámara
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                probarMicrofono();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                soltarMicrofono();
                soltarCamara();
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
        vista.sinImagen(null, "Buscando cámaras…");
        new Thread(() -> {
            List<String> camaras = Camara.camaras();
            SwingUtilities.invokeLater(() -> {
                comboCamara.removeAllItems();
                if (camaras.isEmpty()) {
                    camarasCargadas = true;
                    comboCamara.addItem(new Opcion<>("No se encontró ninguna cámara", null));
                    vista.sinImagen(null, "Conecta una cámara y vuelve a abrir esta ventana.");
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
                camarasCargadas = true;   // desde aquí, elegir en la lista cambia la vista
                verCamara();
            });
        }, "hilo-buscar-camaras").start();
    }

    /** Prende en vivo la cámara elegida en la lista (y apaga la que se estaba viendo). */
    private void verCamara() {
        soltarCamara();
        Opcion<String> elegida = comboCamara.getItemAt(comboCamara.getSelectedIndex());
        vista.sinImagen(null, "Abriendo la cámara…");
        camaraPrueba = Camara.EnVivo.iniciar(elegida == null ? null : elegida.valor, 15, vista::mostrar,
                mensaje -> SwingUtilities.invokeLater(() -> vista.sinImagen(null, mensaje)));
    }

    private void soltarCamara() {
        if (camaraPrueba != null) {
            camaraPrueba.detener();
            camaraPrueba = null;
        }
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
}
