/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wetteifer.chat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import javax.xml.bind.DatatypeConverter;

/**
 *
 * @author wetteifer
 */
public class ChatClient extends Thread {
    
    private static final int SERVER_PORT = 8080;
    
    private Socket client;
    private ObjectOutputStream output;
    private ObjectInputStream input;
    private ChatClientListener callback;        
    private String username;
    
    /**
     * Crea un nuevo cliente para el servidor de chat.
     * @param address La direccion del servidor de chat.
     * @throws ChatException Si ocurre un error al conectarse con el servidor.
     */
    public ChatClient(String address) throws ChatException {
        try {
            client = new Socket(address, SERVER_PORT);
        } catch (IOException e) {
            throw new ChatException("No se pudo conectar al servidor.");
        }
    }
    
    /**
     * Cola de mensajes recibidos del servidor.
     */
    @Override
    public void run() {
        while (!client.isClosed()) {
            // Leemos un mensaje.
            ChatMessage chat = receive();

            // Si el mensaje no se pudo leer, perdimos la conexion
            // con el servidor.
            if (chat == null) {                        
                close();
                break;
            }

            // Delegamos la accion a realizar.
            if (callback != null) {
                callback.onMessageReceived(chat);
            }
        }
    }
    
    /**
     * Envia un mensaje a todos los usuarios.
     * @param message El mensaje a enviar.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void sendMessage(String message) throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.MESSAGE, message));
    }
    
    /**
     * Envia un mensaje de audio a todos los usuarios.
     * @param filename La ruta del archivo de audio.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void sendAudio(String filename) throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.AUDIO, filename));
    }
    
    /**
     * Envia un mensaje de imagen a todos los usuarios.
     * @param filename La ruta del archivo de imagen.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void sendImage(String filename) throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.IMAGE, filename));
    }
    
    /**
     * Envia un mensaje privado a un usuario.
     * @param receiver El nombre del usuario receptor.
     * @param message El mensaje a enviar.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void sendPrivateMessage(String receiver, String message) throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.MESSAGE, receiver, message));
    }
    
    /**
     * Envia un mensaje privado de audio a un usuario.
     * @param receiver El nombre del usuario receptor.
     * @param filename La ruta del archivo de audio.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void sendPrivateAudio(String receiver, String filename) throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.AUDIO, receiver, filename));
    }
    
    /**
     * Envia un mensaje privado de imagen a un usuario.
     * @param receiver El nombre del usuario receptor.
     * @param filename La ruta del archivo de audio.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void sendPrivateImage(String receiver, String filename) throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.IMAGE, receiver, filename));
    }
    
    /**
     * Envia un mensaje al servidor solicitandole la lista de usuarios conectados.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void requestConnectedUsers() throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.CONNECTED_USERS));
    }
    
    /**
     * Envia un mensaje para el cierre de sesion.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    public void logout() throws ChatException {
        sendMessage(new ChatMessage(ChatMessage.Type.LOGOUT));
    }
    
    /**
     * Inicia el cliente.
     * @throws ChatException Si ocurre algun error al inicio de sesion.
     */
    public void open() throws ChatException {
        // Iniciamos los flujos de E/S.
        init();
        
        // Tratamos de ingresar al servidor.
        if (!login()) {
            throw new ChatException("No se pudo iniciar sesion.");
        }
        
        // Iniciamos la cola de mensajes del servidor.
        start();
    }
    
    /**
     * Cierra al cliente y sus flujos de E/S.
     */
    public void close() {
        try {
            client.close();
        } catch (IOException e) {}
    }
    
    /**
     * Verifica si el cliente esta cerrado.
     * @return true si el cliente esta cerrado, false si esta abierto.
     */
    public boolean isClosed() {
        return client.isClosed();
    }
    
    /**
     * Establece el oyente al recibir un nuevo mensaje del servidor.
     * @param callback El oyente.
     */
    public void setChatClientListener(ChatClientListener callback) {
        this.callback = callback;
    }
    
    /**
     * Establece el nombre de usuario del cliente.
     * @param username El nombre de usuario.
     */
    public void setUsername(String username) {
        this.username = username;
    }
    
    /**
     * Regresa el nombre de usuario del cliente.
     * @return El nombre de usuario del cliente.
     */
    public String getUsername() {
        return username;
    }
    
    /**
     * Inicializa los flujos de E/S.
     * @throws ChatException Si ocurre un error al inicializar los flujos.
     */
    private void init() throws ChatException {
        try {
            input  = new ObjectInputStream(client.getInputStream());
            output = new ObjectOutputStream(client.getOutputStream());
        } catch (IOException e) {
            throw new ChatException("No se pudo inicializar el cliente.");
        }
    }
    
    /**
     * Inicia sesion dentro del servidor.
     * @param username El nombre de usuario del cliente.
     * @return true si el inicio de sesion fue exitoso, false en caso contrario.
     */
    private boolean login() {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        
        // Enviamos el nombre de usuario.
        try {
            send(new ChatMessage(ChatMessage.Type.LOGIN, username));
        } catch (ChatException e) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Envia un mensaje al servidor.
     * Si el mensaje que se desea enviar es un archivo entonces se encarga
     * de codificarlo en Base64.
     * @param chat El mensaje a enviar.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    private void sendMessage(ChatMessage chat) throws ChatException {
        ChatMessage.Type type = chat.getType();
        
        // Checar si el mensaje que se quiere enviar es un archivo.
        boolean isFile = (ChatMessage.Type.AUDIO == type) ||
                         (ChatMessage.Type.IMAGE == type);
        
        // Checar si estamos enviando un mensaje privado.
        boolean isPrivateMessage = chat.isPrivateMessage();
        
        // Checar si estamos enviando un mensaje normal.
        boolean isMessage = (ChatMessage.Type.MESSAGE == type);
        
        // Si el mensaje es para enviar algun archivo, entonces
        // iniciamos un nuevo hilo que codificará el archivo en Base64, dado
        // que la codificacion puede tomar mucho tiempo.
        if (isFile) {
            startFileEncode(chat);
        }
        
        // Construimos un nuevo mensaje agregando nuestro nombre de usuario.
        else if (isPrivateMessage) {
            send(new ChatMessage(type, username, chat.getReceiver(), chat.getMessage()));
        }
        
        // Idem.
        else if (isMessage) {
            send(new ChatMessage(type, username, null, chat.getMessage()));
        }
        
        // ...
        else {
            send(new ChatMessage(type, username, null, null));
        }
    }
    
    /**
     * Envia un mensaje al servidor.
     * @param chat El mensaje a enviar.
     * @throws ChatException Si ocurre un error al enviar el mensaje.
     */
    private void send(ChatMessage chat) throws ChatException {
        try {
            output.writeObject(chat);
        } catch (IOException e) {
            throw new ChatException("No se pudo enviar el mensaje al servidor.");
        }
    }
    
    /**
     * Recibe un mensaje del servidor.
     * @return El mensaje leido del servidor o null si no se pudo leer un mensaje.
     */
    private ChatMessage receive() {
        try {
            return (ChatMessage) input.readObject();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Codifica el archivo especificado en un nuevo hilo y lo envia al servidor.
     * @param chat Un mensaje de tipo AUDIO o IMAGE, cuyo mensaje sea la ruta del archivo.
     */
    private void startFileEncode(final ChatMessage chat) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Obtenemos los bytes del archivo.
                byte[] bytes = getBytes(chat.getMessage());
                
                if (bytes == null) return;
                
                // Codificamos los bytes en Base64 para poder enviar los datos
                // en texto sin perder informacion.
                String encoded = DatatypeConverter.printBase64Binary(bytes);
                
                // Enviamos el mensaje codificado.
                try {
                    if (chat.isPrivateMessage()) {
                        send(new ChatMessage(chat.getType(), username, chat.getReceiver(), encoded));
                    } else {
                        send(new ChatMessage(chat.getType(), username, null, encoded));
                    }
                } catch (ChatException e) {
                    // No hay mucho que podamos hacer en este punto.
                }
            }
        }).start();
    }
    
    /**
     * Regresa un arreglo de bytes del archivo especificado.
     * @param filename El nombre del archivo.
     * @return El arreglo de bytes del archivo, o null si ocurre algun error de E/S.
     */
    private byte[] getBytes(String filename) {
        InputStream in;
        
        try {
            in = new FileInputStream(filename);
        } catch (FileNotFoundException e) {
            return null;
        }
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int readed;
        
        try {
            while ((readed = in.read()) != -1) {
                out.write(readed);
            }
        } catch (IOException e) {
            return null;
        } finally {
            try {
                out.close();
                 in.close();
            } catch (IOException e) {}
        }
        
        return out.toByteArray();
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        /**
         * Solicitamos el usuario y la direccion del servidor.
         * Evitamos que se ingresen datos nulos o vacios.
         */
        final BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        
        System.out.print("Usuario: ");
        String username = reader.readLine();
        
        if (username == null || (username = username.trim()).isEmpty()) {
            System.out.println("Nombre de usuario invalido.");
            reader.close();
            return;
        }
        
        System.out.print("Direccion IP del servidor: ");
        String address = reader.readLine();
        
        if (address == null || (address = address.trim()).isEmpty()) {
            System.out.println("Direccion invalida.");
            return;
        }
        
        /**
         * Crea un nuevo cliente para el servidor de chat.
         * En la aplicacion grafica, se debe de preguntar la direccion del servidor,
         * ya que esta siempre va a estar variando.
         */
        final ChatClient client = new ChatClient(address);
        
        /**
         * Establece el nombre de usuario con el que ingresara el cliente.
         * El nombre de usuario debe de ser unico entre los demas usuarios;
         * si se ingresa con un nombre de usuario repetido, el servidor
         * rechazara la conexion del cliente.
         */
        client.setUsername(username);
        
        /**
         * Establece un oyente que estara actuando siempre que se reciba un mensaje.
         * En la aplicacion grafica, la ventana del cliente debera implementar
         * la interfaz ChatClientListener y tener en el cuerpo del metodo
         * onMessageReceived un codigo parecido al que se muestra.
         */
        client.setChatClientListener(new ChatClientListener() {
            @Override
            public void onMessageReceived(ChatMessage chat) {
                switch (chat.getType()) {
                    case LOGOUT:
                        /**
                         * El servidor nos regresa el mensaje para indicarnos
                         * que se ha cerrado la sesion.
                         */
                        System.out.println("Se ha cerrado la sesion.");
                        
                        // Se tiene que cerrar el cliente manualmente.
                        client.close();
                        break;
                    case SERVER_CLOSED:
                        /**
                         * El servidor ha sido cerrado.
                         * En la aplicacion grafica, se deberia de deshabilitar cualquier
                         * tipo de control o lo que se requiera.
                         */
                        System.out.println("El servidor ha sido cerrado.");
                        
                        // Se tiene que cerrar el cliente manualmente.
                        client.close();
                        break;
                    case EXIT:
                        /**
                         * El servidor rechazo la conexion del cliente.
                         * Esto es debido a que se trato de ingresar con un
                         * nombre de usuario ya utilizado.
                         */
                        System.out.println("Conexion rechazada.");
                        
                        // Se tiene que cerrar el cliente manualmente.
                        client.close();
                        break;
                    case INFO:
                        /**
                         * El servidor envio una notificacion a todos los usuarios.
                         * Cuando alguien ingresa o sale del chat. En la aplicacion grafica,
                         * cuando se reciba este tipo de mensaje, se debera actualizar
                         * la lista de usuarios conectados solicitandole al servidor
                         * dicha lista enviando un mensaje de tipo CONNECTED_USERS.
                         */
                        System.out.println(chat.getMessage());
                        break;
                    case CONNECTED_USERS:
                        /**
                         * Este cliente solicito la lista de usuarios conectados al servidor.
                         * La lista de usuarios se regresa separada por comas.
                         * Ej: juan, rodrigo, maria, panfila
                         */
                        System.out.println(chat.getMessage());
                        break;                    
                    case MESSAGE:                        
                        /**
                         * Un usuario envio un mensaje a este usuario.
                         * En la aplicacion grafica, al recibir un mensaje privado,
                         * se deberia de abrir una nueva ventana para seguir enviandose mensajes.
                         * Cabe aclarar que cuando se envia un mensaje privado, este mismo
                         * le es regresado al emisor, para que tambien tenga el registro del mensaje.
                         * Para obtener el usuario emisor se usa el metodo getSender().
                         * Para obtener el usuario receptor se usa el metodo getReceiver().
                         */
                        if (chat.isPrivateMessage()) {
                            System.out.println(chat.getMessage());
                        }
                        
                        /**
                         * Un usuario envio un mensaje a todos los usuarios.
                         * En la aplicacion grafica, el mensaje deberia de ser mostrado
                         * en el area de texto.
                         * Para obtener el usuario emisor se usa el metodo getSender().
                         */
                        else {
                            System.out.println(chat.getMessage());
                        }                        
                        break;                    
                    case AUDIO:
                        /**
                         * Se recibio un archivo de audio.
                         * En la aplicacion grafica, se deberia de reproducir este audio.
                         * El mensaje que se obtiene esta codificado en Base64, primero
                         * hay que decodificarlo y convertirlo en un objeto de Audio.
                         */
                        System.out.println(chat.getMessage());
                        break;
                    case IMAGE:
                        /**
                         * Se recibio un archivo de imagen.
                         * En la aplicacion grafica, se deberia de mostrar la imagen en el
                         * area de los mensajes, como si fuera cualquier otro mensaje.
                         * Igualmente, la imagen esta codificada en Base64, primero
                         * hay que decodificarla y convertirla en un objeto de Imagen.
                         */
                        System.out.println(chat.getMessage());
                        break;
                }
            }
        });
        
        /**
         * Inicia el ciclo de mensajes obtenidos del servidor.
         */
        client.open();
        
        /**
         * Creamos una cola para el envio de mensajes.
         */        
        while (true) {
            /*
             * 1. Enviar un mensaje
             * 2. Enviar un mensaje de audio
             * 3. Enviar un mensaje de imagen
             * 4. Enviar un mensaje privado
             * 5. Enviar un mensaje privado de audio.
             * 6. Enviar un mensaje privado de imagen.
             * 7. Solicitar los usuarios conectados
             * 8. Cerrar sesion
             */
            
            if (client.isClosed()) {
                break;
            }
            
            int option;
            
            try {
                option = Integer.parseInt(reader.readLine());
            } catch (Exception e) {
                continue;
            }
            
            if (client.isClosed()) {
                break;
            }
                        
            switch (option) {
                case 1: {
                    System.out.print("> Mensaje: ");
                    String message = reader.readLine();
                    if (message != null) {
                        client.sendMessage(message);
                    }
                    break;
                }
                case 2: {
                    System.out.print("> Ruta del archivo: ");
                    String filename = reader.readLine();
                    if (filename != null) {
                        client.sendAudio(filename);
                    }
                    break;
                }
                case 3: {
                    System.out.print("> Ruta del archivo: ");
                    String filename = reader.readLine();
                    if (filename != null) {
                        client.sendImage(filename);
                    }
                    break;
                }
                case 4: {
                    System.out.print("> Receptor: ");
                    String receiver = reader.readLine();
                    System.out.print("> Mensaje: ");
                    String message = reader.readLine();
                    if (receiver != null && message != null) {
                        client.sendPrivateMessage(receiver, message);
                    }
                    break;
                }
                case 5: {
                    System.out.print("> Receptor: ");
                    String receiver = reader.readLine();
                    System.out.print("> Ruta del archivo: ");
                    String filename = reader.readLine();
                    if (receiver != null && filename != null) {
                        client.sendPrivateAudio(receiver, filename);
                    }
                    break;
                }
                case 6: {
                    System.out.print("> Receptor: ");
                    String receiver = reader.readLine();
                    System.out.print("> Ruta del archivo: ");
                    String filename = reader.readLine();
                    if (receiver != null && filename != null) {
                        client.sendPrivateImage(receiver, filename);
                    }
                    break;
                }
                case 7:
                    client.requestConnectedUsers();
                    break;
                case 8:
                    client.logout();
                    break;
            }
        }
        
        reader.close();
    }
    
}
