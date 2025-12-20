/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

/**
 *
 * @author Admin
 */
import controller.ClientHandler; // Import class bạn đã viết
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ServerMain {
    private static final int PORT = 12345; // Đảm bảo trùng port với Client

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server đang chạy tại cổng " + PORT + "...");
            System.out.println("Đang chờ kết nối...");

            while (true) {
                // 1. Chấp nhận kết nối
                Socket socket = serverSocket.accept();
                System.out.println("Client mới kết nối: " + socket.getInetAddress());

                // 2. Tạo instance của ClientHandler (của bạn)
                ClientHandler handler = new ClientHandler(socket);

                // 3. Chạy nó trên một luồng riêng (Thread)
                Thread thread = new Thread(handler);
                thread.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}