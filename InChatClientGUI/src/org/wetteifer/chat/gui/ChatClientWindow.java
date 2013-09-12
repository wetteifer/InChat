/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wetteifer.chat.gui;

import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.bind.DatatypeConverter;
import org.wetteifer.chat.ChatClient;
import org.wetteifer.chat.ChatClientListener;
import org.wetteifer.chat.ChatException;
import org.wetteifer.chat.ChatMessage;

/**
 *
 * @author wetteifer
 */
public class ChatClientWindow extends javax.swing.JFrame implements ChatClientListener {
    
    private static final long serialVersionUID = -4805116788154458072L;
    
    private static final String WINDOW_TITLE = "InChat";
    
    // Formato para el nombre de un archivo recibido.
    private static final SimpleDateFormat FILENAME_FORMAT = new SimpleDateFormat("yyyyMMdd-HHmmss");
    
    // Directorio donde se guardaran los archivos enviados.
    private static final File ATTACHMENTS_FOLDER = new File(
            System.getProperty("user.dir")       +
            System.getProperty("file.separator") +
            "attachments");

    private ChatClient client;
    private Map<String, ChatPrivateDialog> conversations;
    private String username;
    private String address;
    private boolean closing;

    /**
     * Creates new form ChatClientWindow.
     */
    public ChatClientWindow(String username, String address) throws ChatException {
        initComponents();
        initClient(username, address);
        setLocationRelativeTo(null);        
    }
    
    /**
     * Inicializa el cliente.
     * @param user El nombre de usuario del cliente.
     * @param addr La direccion del servidor.
     * @throws ChatException Si ocurre un error al conectarse al servidor.
     */
    private void initClient(String user, String addr) throws ChatException {
        username = user;
        address  = addr;
        
        client = new ChatClient(address);
        client.setUsername(username);
        client.setChatClientListener(this);
        client.open();
        
        conversations = new HashMap<String, ChatPrivateDialog>();
        
        setTitle(WINDOW_TITLE + " - Sesión iniciada como " + username);
    }
    
    /**
     * Se encarga de recibir los mensajes del servidor.
     * @param chat El mensaje recibido.
     */
    @Override
    public void onMessageReceived(ChatMessage chat) {
        switch (chat.getType()) {
            case LOGOUT:
                onReceiveLogout();
                break;
            case SERVER_CLOSED:
                onReceiveServerClosed();
                break;
            case EXIT:
                onReceiveExit();
                break;
            case INFO:
                onReceiveInfo(chat);
                break;
            case MESSAGE:
                onReceiveMessage(chat);
                break;
            case CONNECTED_USERS:
                onReceiveConnectedUsers(chat);
                break;
            case AUDIO:
                onReceiveAudio(chat);
                break;
            case IMAGE:
                onReceiveImage(chat);
                break;
        }
    }
    
    /**
     * Accion a realizar cuando se recibe el mensaje de cierre de sesion del servidor.
     */
    private void onReceiveLogout() {
        close();
        enableControls(false);
        if (closing) System.exit(0);
    }
    
    /**
     * Accion a realizar cuando el servidor ha sido cerrado.
     */
    private void onReceiveServerClosed() {
        close();
        enableControls(false);
    }
    
    /**
     * Accion a realizar cuando la conexion al servidor ha sido rechazada.
     */
    private void onReceiveExit() {
        close();
        enableControls(false);
        showErrorDialog("Conexión rechazada.");
    }
    
    /**
     * Accion a realizar cuando se recibe un mensaje de informacion del servidor.
     * @param chat El mensaje recibido.
     */
    private void onReceiveInfo(ChatMessage chat) {
        append(chat.getMessage());
        
        // Evitar solicitar la lista de usuarios conectados si el cliente esta cerrado.
        if (client.isClosed()) {
            return;
        }
        
        try {
            client.requestConnectedUsers();
        } catch (ChatException e) {
            // Esta excepcion ocurrira cuando el usuario cierre sesion,
            // puesto que el servidor envia un mensaje de tipo INFO avisando
            // a los clientes de la desconexion de un usuario. Dado que siempre
            // que se reciba este tipo de mensaje se solicita la lista de usuarios
            // conectados, la solicitud fallara debido a que el servidor
            // ha cerrado nuestra conexion.
        }
    }
    
    /**
     * Accion a realizar cuando se recibe un mensaje de los usuarios.
     * @param chat El mensaje recibido.
     */
    private void onReceiveMessage(ChatMessage chat) {
        String message = chat.getMessage();
        
        // Nos llego un mensaje privado.
        if (chat.isPrivateMessage()) {
            ChatPrivateDialog dialog;
            String sender = chat.getSender();

            // Si somos el emisor, entonces ya se debio haber creado
            // una ventana con la conversacion privada.
            if (sender.equals(username)) {
                dialog = conversations.get(chat.getReceiver());
                dialog.setVisible(true);
                dialog.toFront();
                dialog.append(message);
            }

            // Un usuario nos envio un mensaje privado. 
            // Se debe verificar si ya se ha creado una ventana para la conversacion.
            else {
                // Obtenemos la ventana de conversacion, si es que existe.
                dialog = conversations.get(sender);

                // La ventana no existe; la creamos.
                if (dialog == null) {
                    dialog = new ChatPrivateDialog(client, sender);

                    // Agregamos la ventana de conversacion a la lista.
                    conversations.put(sender, dialog);

                    dialog.setVisible(true);
                    dialog.toFront();
                    dialog.append(message);
                }

                // La ventana existe; le agregamos el mensaje.
                else {
                    dialog.setVisible(true);
                    dialog.toFront();
                    dialog.append(message);
                }
            }
        }

        // Nos llego un mensaje normal.
        else {
            append(message);
        }
    }
    
    /**
     * Accion a realizar cuando se recibe la lista de usuarios conectados.
     * @param chat El mensaje recibido.
     */
    private void onReceiveConnectedUsers(ChatMessage chat) {
        lstConnectedUsers.setListData(chat.getMessage().split(", "));
    }
    
    /**
     * Accion a realizar cuando se recibe un mensaje de audio.
     * @param chat El mensaje recibido.
     */
    private void onReceiveAudio(ChatMessage chat) {
        startFileDecode(ChatMessage.Type.AUDIO, chat);
    }
    
    /**
     * Accion a realizar cuando se recibe un mensaje de imagen.
     * @param chat El mensaje recibido.
     */
    private void onReceiveImage(ChatMessage chat) {
        startFileDecode(ChatMessage.Type.IMAGE, chat);
    }
    
    /**
     * Inserta el mensaje al final del area de mensajes.
     * @param message El mensaje a insertar.
     */
    private void append(String message) {
        txtMessages.append(message);
        txtMessages.append("\n");
    }
    
    /**
     * Envia un mensaje a todos los usuarios conectados.
     */
    private void sendMessage() {
        if (client.isClosed()) {
            return;
        }
        
        String message = txtSend.getText();

        // Verificar que sea valido el mensaje.
        if (message == null) return;        
        message = message.trim();        
        if (message.isEmpty()) return;

        // Enviar el mensaje.
        try {
            client.sendMessage(message);
        } catch (ChatException e) {
            showErrorDialog(e);
            return;
        }

        // Borramos el mensaje de la entrada.
        txtSend.setText("");
        txtSend.requestFocus();
    }
    
    /**
     * Solicita el cierre de sesion del cliente.
     */
    private void requestLogout() {
        if (client.isClosed()) {
            return;
        }
        
        int option = JOptionPane.showConfirmDialog(
                this,
                "¿Realmente desea cerrar sesión?",
                WINDOW_TITLE,
                JOptionPane.YES_NO_OPTION);
        
        if (JOptionPane.YES_OPTION == option) {
            try {
                client.logout();
            } catch (ChatException e) {
                showErrorDialog(e);
            }
        } else {
            closing = false;
        }
    }
    
    /**
     * Cierra el cliente y todas sus conversaciones privadas.
     */
    private void close() {
        client.close();
        conversations.clear();
    }
    
    /**
     * Crea una ventana para iniciar la conversacion privada con un usuario.
     */
    private void startPrivateConversation() {
        // Evitar crear ventanas si el cliente esta cerrado.
        if (client.isClosed()) {
            return;
        }
        
        // Obtenemos el usuario seleccionado.
        String selectedUser = (String) lstConnectedUsers.getSelectedValue();
        
        // Asegurarnos de no dar doble click en nuestro nombre de usuario.
        if (!selectedUser.equals(username)) {
            ChatPrivateDialog dialog = conversations.get(selectedUser);
            
            // Crear una nueva ventana para iniciar la conversacion privada.
            if (dialog == null) {
                dialog = new ChatPrivateDialog(client, selectedUser);
                
                // Agregamos la ventana a nuestras conversaciones privadas.
                conversations.put(selectedUser, dialog);
                
                dialog.setVisible(true);
                dialog.toFront();
            }
            
            // Volvemos a abrir la ventana para seguir conversando.
            else {
                dialog.setVisible(true);
                dialog.toFront();
            }
        }
    }

    /**
     * Muestra un cuadro de dialogo para adjuntar algun archivo de imagen o audio.
     * @param type El tipo de archivo a adjuntar.
     */
    private void attach(ChatMessage.Type type) {
        JFileChooser chooser = new JFileChooser();
        
        // Agregamos los filtros de extension de archivo.
        switch (type) {
            case AUDIO:
                chooser.setFileFilter(new FileNameExtensionFilter("Sonido WAV", "wav"));
                break;
            case IMAGE:
                chooser.setFileFilter(new FileNameExtensionFilter("Imagen PNG", "png"));
                break;
            default:
                return;
        }

        // Quitar la opcion de agregar cualquier archivo.
        chooser.setAcceptAllFileFilterUsed(false);

        // Mostramos el cuadro de dialogo.
        int option = chooser.showOpenDialog(this);

        // Si se dio click en abrir.
        if (JFileChooser.APPROVE_OPTION == option) {
            sendAttachMessage(type, chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    /**
     * Envia un mensaje adjunto.
     * @param type El tipo de mensaje a enviar.
     * @param filename La ruta del archivo.
     */
    private void sendAttachMessage(ChatMessage.Type type, String filename) {
        try {
            switch (type) {
                case AUDIO:
                    client.sendAudio(filename);
                    break;
                case IMAGE:
                    client.sendImage(filename);
                    break;
            }
        } catch (ChatException e) {
            showErrorDialog(e);
        }
    }
    
    /**
     * Inicia el procedo de decodificacion de un archivo.
     * @param type El tipo de archivo a decodificar.
     * @param chat El mensaje obtenido.
     */
    private void startFileDecode(final ChatMessage.Type type, final ChatMessage chat) {
        String sender = chat.getSender();
        
        // Evitar decodificar el archivo si nosotros lo estamos mandando.
        if (sender.equals(username)) {
            return;
        }
        
        // Notificar al usuario del envio del archivo.
        append(sender + " ha mandado un archivo.");
        
        // Crear el directorio de mensajes adjuntos si aun no existe.
        if (!ATTACHMENTS_FOLDER.exists()) {
            ATTACHMENTS_FOLDER.mkdir();
        }
        
        // Creamos un nuevo hilo para decodificar y crear el archivo recibido.
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] bytes = DatatypeConverter.parseBase64Binary(chat.getMessage());                
                switch (type) {
                    case AUDIO:
                        saveAudio(bytes);
                        break;
                    case IMAGE:
                        saveImage(bytes);
                        break;
                }
            }
        }).start();
    }
    
    /**
     * Guarda la imagen en el disco duro.
     * @param bytes El arreglo de bytes de la imagen.
     */
    private void saveImage(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            final File file = new File(createFilename() + ".png");
            ImageIO.write(image, "png", file);
            image.flush();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    append("Se ha guardado la imagen en la siguiente ruta: " + file.getAbsolutePath());
                }
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showErrorDialog("No se pudo guardar la imagen.");
                }
            });
        }
    }
    
    /**
     * Guarda el audio en el disco duro.
     * @param bytes El arreglo de bytes del audio.
     */
    private void saveAudio(byte[] bytes) {
        try {
            final File file = new File(createFilename() + ".wav");
            BufferedOutputStream output = new BufferedOutputStream(new FileOutputStream(file));
            output.write(bytes, 0, bytes.length);
            output.close();
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    append("Se ha guardado el audio en la siguiente ruta: " + file.getAbsolutePath());
                }
            });
        } catch (IOException e) {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    showErrorDialog("No se pudo guardar el audio.");
                }
            });
        }
    }
    
    /**
     * Crea un nombre aleatorio para un nuevo archivo.
     * @return El nombre para un nuevo archivo.
     */
    private String createFilename() {
        return ATTACHMENTS_FOLDER.getAbsolutePath() +
               System.getProperty("file.separator") +
               FILENAME_FORMAT.format(new Date());
    }
    
    /**
     * Muestra un mensaje de error.
     * @param message El mensaje de error.
     */
    private void showErrorDialog(String message) {
        JOptionPane.showMessageDialog(this, message, WINDOW_TITLE, JOptionPane.ERROR_MESSAGE);
    }
    
    /**
     * Muestra un mensaje de error.
     * @param e La excepcion ocurrida.
     */
    private void showErrorDialog(Exception e) {
        showErrorDialog(e.getMessage());
    }
    
    /**
     * Establece el estado especificado a los componentes de la ventana.
     * @param state true si debe estar habilitado, false en caso contrario.
     */
    private void enableControls(boolean state) {
        lstConnectedUsers.setEnabled(state);
        btnAttachImage.setEnabled(state);
        btnAttachAudio.setEnabled(state);
        btnLogout.setEnabled(state);
        txtSend.setEnabled(state);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jScrollPane1 = new javax.swing.JScrollPane();
        jList1 = new javax.swing.JList();
        lbLogo = new javax.swing.JLabel();
        jSplitPane1 = new javax.swing.JSplitPane();
        jScrollPane4 = new javax.swing.JScrollPane();
        lstConnectedUsers = new javax.swing.JList();
        jScrollPane2 = new javax.swing.JScrollPane();
        txtMessages = new javax.swing.JTextArea();
        jToolBar1 = new javax.swing.JToolBar();
        btnAttachImage = new javax.swing.JButton();
        btnAttachAudio = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        btnLogout = new javax.swing.JButton();
        jScrollPane5 = new javax.swing.JScrollPane();
        txtSend = new javax.swing.JTextArea();

        jList1.setModel(new javax.swing.AbstractListModel() {
            String[] strings = { "Item 1", "Item 2", "Item 3", "Item 4", "Item 5" };
            public int getSize() { return strings.length; }
            public Object getElementAt(int i) { return strings[i]; }
        });
        jScrollPane1.setViewportView(jList1);

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle(WINDOW_TITLE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        lbLogo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        lbLogo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/logo.png"))); // NOI18N
        lbLogo.setToolTipText(WINDOW_TITLE);

        jSplitPane1.setDividerLocation(100);

        lstConnectedUsers.setToolTipText("Usuarios conectados");
        lstConnectedUsers.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lstConnectedUsersMouseClicked(evt);
            }
        });
        jScrollPane4.setViewportView(lstConnectedUsers);

        jSplitPane1.setLeftComponent(jScrollPane4);

        txtMessages.setEditable(false);
        ((javax.swing.text.DefaultCaret) txtMessages.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.ALWAYS_UPDATE);
        jScrollPane2.setViewportView(txtMessages);

        jSplitPane1.setRightComponent(jScrollPane2);

        jToolBar1.setFloatable(false);
        jToolBar1.setRollover(true);

        btnAttachImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/image.png"))); // NOI18N
        btnAttachImage.setText("Enviar imagen");
        btnAttachImage.setToolTipText("Enviar imagen");
        btnAttachImage.setFocusable(false);
        btnAttachImage.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnAttachImage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAttachImageActionPerformed(evt);
            }
        });
        jToolBar1.add(btnAttachImage);

        btnAttachAudio.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/audio.png"))); // NOI18N
        btnAttachAudio.setText("Enviar audio");
        btnAttachAudio.setToolTipText("Enviar audio");
        btnAttachAudio.setFocusable(false);
        btnAttachAudio.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnAttachAudio.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAttachAudioActionPerformed(evt);
            }
        });
        jToolBar1.add(btnAttachAudio);
        jToolBar1.add(jSeparator1);

        btnLogout.setIcon(new javax.swing.ImageIcon(getClass().getResource("/images/logout.png"))); // NOI18N
        btnLogout.setText("Cerrar sesión");
        btnLogout.setToolTipText("Cerrar sesión");
        btnLogout.setFocusable(false);
        btnLogout.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnLogout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLogoutActionPerformed(evt);
            }
        });
        jToolBar1.add(btnLogout);

        txtSend.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                txtSendKeyPressed(evt);
            }
        });
        ((javax.swing.text.DefaultCaret) txtSend.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.ALWAYS_UPDATE);
        jScrollPane5.setViewportView(txtSend);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addComponent(lbLogo, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jToolBar1, javax.swing.GroupLayout.DEFAULT_SIZE, 476, Short.MAX_VALUE)
                    .addComponent(jScrollPane5))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(lbLogo)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jSplitPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 203, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jToolBar1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 60, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents
    
    private void lstConnectedUsersMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lstConnectedUsersMouseClicked
        if (evt.getClickCount() == 2) startPrivateConversation();
    }//GEN-LAST:event_lstConnectedUsersMouseClicked

    private void btnAttachImageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAttachImageActionPerformed
        attach(ChatMessage.Type.IMAGE);
    }//GEN-LAST:event_btnAttachImageActionPerformed

    private void btnAttachAudioActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAttachAudioActionPerformed
        attach(ChatMessage.Type.AUDIO);
    }//GEN-LAST:event_btnAttachAudioActionPerformed

    private void btnLogoutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLogoutActionPerformed
        requestLogout();
    }//GEN-LAST:event_btnLogoutActionPerformed

    private void txtSendKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtSendKeyPressed
        boolean enterPressed = (KeyEvent.VK_ENTER == evt.getKeyCode());
        
        if (evt.isShiftDown() && enterPressed) {
            txtSend.append("\n");
            return;
        }
        
        if (enterPressed) {
            sendMessage();
            evt.consume();
        }
    }//GEN-LAST:event_txtSendKeyPressed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (client.isClosed()) {
            System.exit(0);
        } else {
            // Indicar que estamos cerrando desde la ventana.
            closing = true;

            // Cerrar sesion.
            requestLogout();
        }
    }//GEN-LAST:event_formWindowClosing
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAttachAudio;
    private javax.swing.JButton btnAttachImage;
    private javax.swing.JButton btnLogout;
    private javax.swing.JList jList1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JLabel lbLogo;
    private javax.swing.JList lstConnectedUsers;
    private javax.swing.JTextArea txtMessages;
    private javax.swing.JTextArea txtSend;
    // End of variables declaration//GEN-END:variables

}
