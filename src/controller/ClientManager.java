package controller;



import controller.ClientHandler;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Admin
 */
public class ClientManager {
    public static Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    public static void add(String email, ClientHandler ch) {
        onlineUsers.put(email, ch);
    }

    public static void remove(String email) {
        onlineUsers.remove(email);
    }

    public static ClientHandler get(String email) {
        return onlineUsers.get(email);
    }
}
