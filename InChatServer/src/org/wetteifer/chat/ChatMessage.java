/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wetteifer.chat;

import java.io.Serializable;

/**
 *
 * @author wetteifer
 */
public class ChatMessage implements Serializable {
    
    private static final long serialVersionUID = 3694248025927259702L;
    
    public enum Type {
        
        /**
         * Para el inicio de sesion.
         * No necesita mensaje.
         */
        LOGIN,
        
        /**
         * Para cuando la conexion del cliente ha sido rechazada.
         * No necesita mensaje.
         */
        EXIT,
        
        /**
         * Para un mensaje enviado del servidor a los clientes avisando del
         * inicio o cierre de sesion de un usuario.
         * Mensaje requerido.
         */
        INFO,
        
        /**
         * Para avisarle a los clientes que el servidor ha sido cerrado.
         * No necesita mensaje.
         */
        SERVER_CLOSED,
        
        /**
         * Para solicitar al servidor una lista con los usuarios conectados.
         * No necesita mensaje.
         */
        CONNECTED_USERS,
        
        /**
         * Para un mensaje enviado de un cliente a hacia uno o todos los clientes.
         * Mensaje requerido.
         */
        MESSAGE,
        
        /**
         * Para un mensaje de audio.
         * Mensaje requerido. Debe de ser la ruta del audio.
         */
        AUDIO,
        
        /**
         * Para un mensaje con imagen.
         * Mensaje requerido. Debe de ser la ruta de la imagen.
         */
        IMAGE,
        
        /**
         * Para el cierre de sesion.
         * No necesita mensaje.
         */
        LOGOUT
        
    }
    
    private ChatMessage.Type type;
    private String sender;
    private String receiver;
    private String message;
    
    /**
     * Contructor para enviar un mensaje privado a un usuario.
     * @param type El tipo de mensaje que se enviara.
     * @param sender El nombre de usuario del cliente que envia el mensaje.
     * @param receiver El nombre de usuario del cliente al que se le enviara el mensaje.
     * @param message El mensaje a enviar.
     */
    public ChatMessage(ChatMessage.Type type, String sender, String receiver, String message) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
    }
    
    /**
     * Contructor para enviar un mensaje privado a un usuario.
     * @param type El tipo de mensaje que se enviara.
     * @param receiver El nombre de usuario del cliente al que se le enviara el mensaje.
     * @param message El mensaje a enviar.
     */
    public ChatMessage(ChatMessage.Type type, String receiver, String message) {
        this(type, null, receiver, message);
    }
    
    /**
     * Constructor para enviar un mensaje normal.
     * @param type El tipo de mensaje que se enviara.
     * @param message El mensaje a enviar.
     */
    public ChatMessage(ChatMessage.Type type, String message) {
        this(type, null, null, message);
    }
    
    /**
     * Constructor para enviar un mensaje solicitando una accion al servidor.
     * @param type El tipo de mensaje que se enviara.
     */
    public ChatMessage(ChatMessage.Type type) {
        this(type, null, null, null);
    }
    
    public ChatMessage.Type getType() {
        return type;
    }
    
    public void setType(ChatMessage.Type type) {
        this.type = type;
    }
    
    public String getSender() {
        return sender;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public String getReceiver() {
        return receiver;
    }
    
    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public boolean isPrivateMessage() {
        return receiver != null;
    }
    
}
