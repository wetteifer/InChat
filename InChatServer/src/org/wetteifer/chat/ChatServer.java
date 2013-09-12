/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wetteifer.chat;

import java.io.IOException;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 *
 * @author wetteifer
 */
public class ChatServer extends Thread {
    
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
    private static final String MESSAGE_PADDING = "    ";
    private static final String SERVER_USERNAME = "Servidor InChat";
    private static final int SERVER_PORT = 8080;
    
    private final ServerSocket server;
    private final Map<String, ChatClient> clients;
    
    private ChatServerListener callback;
    
    /**
     * Crea el servidor de chat en el puerto 8080.
     * @throws ChatException Si no se pudo iniciar el servidor.
     */
    public ChatServer() throws ChatException {
        try {
            server = new ServerSocket(SERVER_PORT);
            clients = Collections.synchronizedMap(new HashMap<String, ChatClient>());
        } catch (IOException e) {
            throw new ChatException("No se pudo iniciar el servidor.");
        }
    }
    
    /**
     * Cola para el ingreso de nuevos clientes.
     */
    @Override
    public void run() {
        info("Servidor iniciado en el puerto " + SERVER_PORT + ".");
        
        while (!server.isClosed()) {
            ChatClient client;
            
            // Esperar a obtener una conexion.
            try {
                client = new ChatClient(this, server.accept());
            } catch (IOException e) {
                // Si ocurre un error al aceptar un nuevo cliente,
                // debe ser porque el servidor fue cerrado; continuamos
                // para que el ciclo verifique el estado del servidor.
                continue;
            }
            
            try {
                // Iniciamos los flujos de E/S del cliente.
                client.init();
                
                // Verificar que el cliente tenga un nombre de usuario 
                // disponible (no repetido con algun otro cliente).
                if (!verify(client)) {
                    continue;
                }
                
                // Agregamos el cliente a la lista de clientes.
                clients.put(client.getUsername(), client);
                
                // Inicia el hilo receptor de mensajes.
                client.start();
                
                // Notificar a los usuarios del nuevo ingreso.
                alert("El usuario [" + client + "] se ha conectado.");
            } catch (ChatException e) {
                error(e);
            }
        }
    }
    
    /**
     * Envia un mensaje en modo broadcast o unicast.
     * @param chat El mensaje a enviar.
     */
    public void send(ChatMessage chat) {
        if (ChatMessage.Type.MESSAGE == chat.getType()) {
            chat.setMessage(formatClientMessage(chat));
        }
        
        if (chat.isPrivateMessage()) {
            unicast(chat);
        }
        
        else {
            broadcast(chat);
        }
    }
    
    /**
     * Procesa el cierre de sesion de un usuario.
     * @param chat El mensaje que envio el usuario solicitante.
     */
    public void sendLogout(ChatMessage chat) {
        // Obtenemos el nombre del emisor.
        String username = chat.getSender();
        
        // Obtenemos el cliente.
        ChatClient client = clients.get(username);
        
        if (client == null) return;
        
        // Notificamos a todos los usuarios.
        alert("El usuario [" + username + "] se ha desconectado.");
        
        // Enviamos el mensaje de regreso al emisor.
        client.send(new ChatMessage(ChatMessage.Type.LOGOUT));
        
        // Cerramos y eliminamos al cliente.
        client.close();
        clients.remove(username);
    }
    
    /**
     * Envia la lista de usuarios conectados al usuario solicitante.
     * @param chat El mensaje que envio el usuario solicitante.
     */
    public void sendConnectedUsers(ChatMessage chat) {
        ChatClient client = clients.get(chat.getSender());
        
        if (client == null) return;
        
        client.send(new ChatMessage(ChatMessage.Type.CONNECTED_USERS, getConnectedUsers()));
    }
    
    /**
     * Envia un mensaje normal a todos los clientes.
     * @param message El mensaje a enviar.
     */
    public void chat(String message) {
        broadcast(ChatMessage.Type.MESSAGE, formatServerMessage(message));
    }
    
    /**
     * Cierra al servidor y a todos sus clientes.
     * @throws ChatException Si ocurre un error al cerrar el servidor.
     */
    public void close() throws ChatException {
        try {
            // Enviamos un mensaje de informacion para los clientes.
            broadcast(ChatMessage.Type.MESSAGE, formatServerInfo("El servidor se ha cerrado."));
            
            // Notificamos a los clientes que el servidor se ha cerrado.
            ChatMessage closed = new ChatMessage(ChatMessage.Type.SERVER_CLOSED);
            
            // Cerramos a todos los clientes.
            synchronized (clients) {                
                for (ChatClient client : clients.values()) {
                    client.send(closed);
                    client.close();
                }
            }
            
            // Eliminamos a todos los clientes.
            clients.clear();
            
            // Cierra el servidor.
            server.close();
        } catch (IOException e) {
            throw new ChatException("No se pudo cerrar el servidor.");
        }
    }
    
    /**
     * Verifica si el servidor esta cerrado.
     * @return true si el servidor esta cerrado, false si esta abierto.
     */
    public boolean isClosed() {
        return server.isClosed();
    }
    
    /**
     * Establece un oyente al enviar un mensaje a los clientes.
     * @param callback El oyente.
     */
    public void setChatServerListener(ChatServerListener callback) {
        this.callback = callback;
    }
    
    /**
     * Verifica que el cliente tenga un nombre de usuario no repetido
     * con algun otro usuario conectado.
     * @param client El cliente a validar.
     * @return true si el nombre esta disponible, false en caso contrario.
     * @throws ChatException Si ocurre un error al enviar un mensaje al cliente.
     */
    private boolean verify(ChatClient client) throws ChatException {
        String username = client.getUsername();
        
        // Si no se obtuvo el nombre de usuario, entonces
        // no es valido.
        if (username == null) {
            return false;
        }
        
        // Buscar que el nombre de usuario no sea repetido.
        if (clients.keySet().contains(username)) {
            // Si el nombre de usuario es repetido, enviamos un mensaje
            // al cliente indicandole que no se acepto su conexion.
            client.send(new ChatMessage(ChatMessage.Type.EXIT));
            return false;
        }
        
        return true;
    }
    
    /**
     * Envia un mensaje de alerta cuando un usuario inicia o cierra sesion.
     * @param message El mensaje a enviar.
     */
    private void alert(String message) {
        broadcast(ChatMessage.Type.INFO, formatServerInfo(message));
    }
    
    /**
     * Envia un mensaje del servidor a todos los clientes conectados.
     * @param type El tipo de mensaje a enviar.
     * @param message El mensaje a enviar.
     */
    private void broadcast(ChatMessage.Type type, String message) {
        broadcast(new ChatMessage(type, SERVER_USERNAME, null, message));
    }
    
    /**
     * Envia un mensaje a todos los clientes conectados.
     * @param chat El mensaje a enviar.
     */
    private void broadcast(ChatMessage chat) {
        ChatMessage.Type type = chat.getType();
        
        // Verificar si debemos enviar una respuesta al emisor.
        boolean isAttachment = (ChatMessage.Type.AUDIO == type) ||
                               (ChatMessage.Type.IMAGE == type);
        
        // Enviamos una respuesta al emisor.
        if (isAttachment) {
            ChatClient sender = clients.get(chat.getSender());
            ChatMessage response = new ChatMessage(ChatMessage.Type.MESSAGE);
            
            switch (type) {
                case AUDIO:
                    response.setMessage("Haz enviado un mensaje de audio.");
                    break;
                case IMAGE:
                    response.setMessage("Haz enviado una imagen.");
                    break;
            }
            
            sender.send(response);
        }
        
        // Verificar si debemos registrar el mensaje en el servidor.
        boolean isMessage = (ChatMessage.Type.INFO    == type) ||
                            (ChatMessage.Type.MESSAGE == type);
                
        // Registramos el mensaje en el servidor.
        if (isMessage) {
            info(chat.getMessage(), false);
        }
        
        // Enviamos el mensaje a todos los clientes.
        synchronized (clients) {            
            for (Iterator<Map.Entry<String, ChatClient>> iterator = clients.entrySet().iterator(); iterator.hasNext();) {
                Map.Entry<String, ChatClient> entry = iterator.next();

                ChatClient client = entry.getValue();

                if (!client.send(chat)) {
                    client.close();
                    iterator.remove();
                }
            }
        }
    }
    
    /**
     * Envia un mensaje privado de un cliente a otro cliente.
     * @param chat El mensaje que se va a enviar.
     */
    private void unicast(ChatMessage chat) {
        // Obtenemos el receptor
        ChatClient receiver = clients.get(chat.getReceiver());
        
        if (receiver == null) return;
        
        // Obtenemos el emisor.
        ChatClient sender = clients.get(chat.getSender());
        
        if (sender == null) return;
        
        // Creamos una mensaje de respuesta para el emisor.
        ChatMessage response = new ChatMessage(ChatMessage.Type.MESSAGE, sender.getUsername(), receiver.getUsername(), null);
        
        switch (chat.getType()) {
            case MESSAGE:
                response.setMessage(chat.getMessage());
                break;
            case AUDIO:
                response.setMessage("Haz enviado un mensaje de audio.");
                break;
            case IMAGE:
                response.setMessage("Haz enviado una imagen.");
                break;
        }
        
        // Enviamos el mensaje de respuesta al emisor.
        sender.send(response);
        
        // Enviamos el mensaje al receptor.
        if (!receiver.send(chat)) {
            receiver.close();
            clients.remove(receiver.getUsername());
        }
    }
    
    /**
     * Solicita una lista de usuarios conectados.
     * @return La lista de usuarios conectados.
     */
    private String getConnectedUsers() {
        // Obtenemos el numero de clientes.
        int size = clients.size();
        
        // Obtenemos una copia de los nombres de usuario.
        String[] users = clients.keySet().toArray(new String[size]);
        
        // Ordenamos la lista.
        Arrays.sort(users);
        
        // Creamos una cadena con todos los nombres de usuario.
        StringBuilder builder = new StringBuilder(users[0]);
        
        for (int i = 1; i < size; ++i) {
            builder.append(", ").append(users[i]);
        }
        
        return builder.toString();
    }
    
    /**
     * Imprime un mensaje de informacion.
     * @param message El mensaje a enviar.
     * @param format true si se desea formatear el mensaje, false en caso contrario.
     */
    private void info(String message, boolean format) {
        if (callback != null) {
            callback.onMessageSent(format ? formatServerInfo(message) : message);
        }
    }
    
    /**
     * Imprime un mensaje de informacion.
     * @param message El mensaje a enviar.
     */
    private void info(String message) {
        info(message, true);
    }
    
    /**
     * Imprime un mensaje de erorr.
     * @param error El error ocurrido.
     */
    private void error(String error) {
        if (callback != null) {
            callback.onMessageSent(formatServerError(error));
        }
    }
    
    /**
     * Imprime un mensaje de error.
     * @param error La excepcion ocurrida.
     */
    private void error(Exception error) {
        error(error.getMessage());
    }
    
    /**
     * Realiza el formato de una hora.
     * @param time La hora que se va a formatear.
     * @return La hora formateada.
     */
    private String formatTime(Date time) {
        return "(" + TIME_FORMAT.format(time) + ") > ";
    }
    
    /**
     * Realiza el formato de los saltos de linea de un mensaje..
     * @param message El mensaje que se va a formatear.
     * @return El mensaje con los saltos de lineas formateadas.
     */
    private String formatNewlines(String message) {
        return message.replaceAll("\n", "\n" + MESSAGE_PADDING);
    }
    
    /**
     * Realiza el formato de un mensaje de un cliente.
     * @param chat El mensaje que se va a formatear.
     * @return El mensaje formateado.
     */
    private String formatClientMessage(ChatMessage chat) {
        return formatTime(new Date()) + "[" + chat.getSender() + "] dice:" + formatNewlines("\n" + chat.getMessage());
    }
    
    /**
     * Realiza el formato de un mensaje del servidor.
     * @param message El mensaje que se va a formatear.
     * @return El mensaje formateado.
     */
    private String formatServerMessage(String message) {
        return formatTime(new Date()) + "[" + SERVER_USERNAME + "]" + formatNewlines("\n" + message);
    }
    
    /**
     * Realiza el formato de un mensaje de informacion del servidor.
     * @param message El mensaje que se va a formatear.
     * @return El mensaje formateado.
     */
    private String formatServerInfo(String message) {
        return formatTime(new Date()) + message;
    }
    
    /**
     * Realiza el formato de un mensaje de error del servidor.
     * @param error El mensaje que se va a formatear.
     * @return El mensaje formateado.
     */
    private String formatServerError(String error) {
        return formatTime(new Date()) + "ERROR: " + error;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws ChatException {
        ChatServer server = new ChatServer();
        server.setChatServerListener(new ChatServerListener() {
            @Override
            public void onMessageSent(String message) {
                System.out.println(message);
            }            
        });
        server.start();
    }
    
}
