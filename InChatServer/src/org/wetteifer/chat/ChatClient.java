/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wetteifer.chat;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

/**
 *
 * @author wetteifer
 */
public class ChatClient extends Thread {
    
    private ChatServer server;
    private Socket client;    
    private ObjectOutputStream output;
    private ObjectInputStream input;    
    private String username;
    
    /**
     * Crea un nuevo cliente para el servidor de chat.
     * @param server El servidor de chat.
     * @param client El socket del cliente.
     */
    public ChatClient(ChatServer server, Socket client) {
        this.server = server;
        this.client = client;
    }
    
    /**
     * Cola para los mensajes recibidos del cliente.
     */
    @Override
    public void run() {
        while (!client.isClosed()) {
            // Leemos un mensaje.
            ChatMessage chat = receive();
            
            // Si el mensaje no se pudo leer, perdimos la conexion
            // con el cliente.
            if (chat == null) {
                close();
                break;
            }
            
            // Verificamos el tipo de mensaje recibido.
            switch (chat.getType()) {                
                case MESSAGE:
                case AUDIO:
                case IMAGE:
                    server.send(chat);
                    break;
                case LOGOUT:
                    server.sendLogout(chat);
                    break;
                case CONNECTED_USERS:
                    server.sendConnectedUsers(chat);
                    break;
            }
        }
    }
    
    /**
     * Inicializa los flujos de E/S del cliente.
     * @throws ChatException Si hubo algun error al inicializar los flujos.
     */
    public void init() throws ChatException {
        try {
            output = new ObjectOutputStream(client.getOutputStream());
            input  = new ObjectInputStream(client.getInputStream()); 
        } catch (IOException e) {
            throw new ChatException("No se pudo inicializar el cliente.");
        }
    }
    
    /**
     * Regresa el nombre de usuario del cliente.
     * Este metodo debe de ser el primero en llamarse en el lado del servidor.
     * @return El nombre de usuario del cliente.
     */
    public String getUsername() {
        if (username == null) {
            try {
                // Lee un mensaje.
                ChatMessage chat = receive();
                
                // Verificar que sea valido el mensaje.
                if (chat == null) return null;
                
                // Si el tipo del mensaje es de ingreso, entonces podemos
                // obtener el nombre de usuario.
                if (ChatMessage.Type.LOGIN == chat.getType()) {
                    username = chat.getMessage();  
                }           
            } catch (Exception e) {
                // Se regresara null si se llega aqui.
            }
        }
        return username;
    }
    
    /**
     * Envia un mensaje al cliente.
     * @param chat El mensaje que se enviara.
     * @return true si el mensaje pudo ser enviado, false en caso contrario.
     */
    public boolean send(ChatMessage chat) {
        if (!client.isConnected()) {
            return false;
        }
        
        // Envia el objeto al cliente.
        try {
            output.writeObject(chat);
        } catch (IOException e) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Lee un mensaje del cliente.
     * @return El mensaje leido del cliente o null si no se pudo leer un mensaje.
     */
    private ChatMessage receive() {
        try {
            return (ChatMessage) input.readObject();
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Cierra el cliente y sus flujos de entrada y salida.
     */
    public void close() {
        try {
            client.close();
        } catch (IOException e) {}
    }
    
    @Override
    public String toString() {
        return username;
    }
    
}
