/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package controller;

import dao.GroupDao;
import dao.MessageDao;
import dao.UserDao;
import model.User;
import java.io.*;
import java.net.Socket;
import java.sql.Date; // Thư viện để xử lý ngày tháng (yyyy-MM-dd)
import java.util.List;

/**
 * Class này chịu trách nhiệm xử lý từng kết nối của Client gửi lên. Mỗi Client
 * kết nối sẽ chạy trên 1 luồng (Thread) riêng biệt.
 */
public class ClientHandler implements Runnable {

    private Socket socket;
    private UserDao userDAO;
    private GroupDao groupDAO;
    private BufferedReader in;
    private PrintWriter out;
    private MessageDao messageDAO;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        this.userDAO = new UserDao(); // Khởi tạo DAO để làm việc với Database
        this.groupDAO = new GroupDao();
        this.messageDAO = new MessageDao();
    }

    @Override
    public void run() {
        try {
            // Mở luồng nhập/xuất dữ liệu
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            String request;
            // Vòng lặp liên tục lắng nghe yêu cầu từ Client
            while ((request = in.readLine()) != null) {
                System.out.println("Client Request: " + request); // Ghi log ra màn hình server

                // Cắt chuỗi theo dấu chấm phẩy ";"
                // Ví dụ: "LOGIN;admin@gmail.com;123456" -> ["LOGIN", "admin@gmail.com", "123456"]
                String[] parts = request.split(";");
                String command = parts[0];

                String response = "";

                try {
                    switch (command) {
                        // 1. XỬ LÝ ĐĂNG KÝ
                        case "REGISTER":
                            // Cấu trúc: REGISTER;email;pass;name;gender;dob
                            if (parts.length < 6) {
                                response = "REGISTER_FAIL;Missing Data";
                            } else {
                                User newUser = new User();
                                newUser.setEmail(parts[1]);
                                newUser.setPassword(parts[2]); // Password raw, DAO sẽ hash sau
                                newUser.setName(parts[3]);
                                newUser.setGender(parts[4]);
                                // Chuyển String "yyyy-MM-dd" sang java.sql.Date
                                newUser.setDob(Date.valueOf(parts[5]));

                                boolean isRegistered = userDAO.registerUser(newUser);
                                response = isRegistered ? "REGISTER_SUCCESS" : "REGISTER_FAIL;Email Exists";
                            }
                            break;

                        // 2. XỬ LÝ XÁC THỰC OTP
                        case "VERIFY_OTP":
                            // Cấu trúc: VERIFY_OTP;email;otpCode
                            if (parts.length < 3) {
                                response = "OTP_FAIL";
                            } else {
                                String email = parts[1];
                                String otp = parts[2];
                                boolean isVerified = userDAO.verifyOTP(email, otp);
                                response = isVerified ? "OTP_SUCCESS" : "OTP_FAIL";
                            }
                            break;

                        // 3. XỬ LÝ ĐĂNG NHẬP
                        case "LOGIN":
                            // Cấu trúc: LOGIN;email;pass
                            if (parts.length < 3) {
                                response = "LOGIN_FAIL";
                            } else {
                                String email = parts[1];
                                String pass = parts[2];
                                User user = userDAO.checkLogin(email, pass);

                                if (user != null) {
                                    String dob = user.getDob() != null ? user.getDob().toString() : "";
                                    response = "LOGIN_SUCCESS;"
                                            + user.getEmail() + ";"
                                            + user.getName() + ";"
                                            + user.getGender() + ";"
                                            + user.getDob().toString();

                                } else {
                                    response = "LOGIN_FAIL";
                                }
                            }
                            break;

                        // 4. XỬ LÝ CẬP NHẬT THÔNG TIN
                        case "UPDATE_INFO":
                            // Cấu trúc: UPDATE_INFO;email;name;gender;dob
                            if (parts.length < 5) {
                                response = "UPDATE_FAIL";
                            } else {
                                User upUser = new User();
                                upUser.setEmail(parts[1]); // Dùng email để định danh user cần sửa
                                upUser.setName(parts[2]);
                                upUser.setGender(parts[3]);
                                upUser.setDob(Date.valueOf(parts[4]));

                                boolean isUpdated = userDAO.updateUserInfo(upUser);
                                response = isUpdated ? "UPDATE_SUCCESS" : "UPDATE_FAIL";
                            }
                            break;

                        case "CREATE_GROUP": {
                            String email = parts[1];
                            String groupName = parts[2];

                            int userId = userDAO.getUserIdByEmail(email);
                            int groupId = groupDAO.createGroup(groupName, userId);

                            response = (groupId != -1)
                                    ? "CREATE_GROUP_SUCCESS;" + groupId + ";" + groupName
                                    : "CREATE_GROUP_FAIL";
                            break;
                        }

                        case "JOIN_GROUP": {
                            int userId = userDAO.getUserIdByEmail(parts[1]);
                            int groupId = Integer.parseInt(parts[2]);

                            response = groupDAO.joinGroup(groupId, userId)
                                    ? "JOIN_GROUP_SUCCESS"
                                    : "JOIN_GROUP_FAIL";
                            break;
                        }

                        case "LEAVE_GROUP": {
                            int userId = userDAO.getUserIdByEmail(parts[1]);
                            int groupId = Integer.parseInt(parts[2]);

                            response = groupDAO.leaveGroup(groupId, userId)
                                    ? "LEAVE_GROUP_SUCCESS"
                                    : "LEAVE_GROUP_FAIL";
                            break;
                        }

                        case "GET_GROUPS": {
                            int userId = userDAO.getUserIdByEmail(parts[1]);
                            List<String> groups = groupDAO.getGroups(userId);
                            response = "GROUP_LIST;" + String.join(";", groups);
                            break;
                        }

                        case "SEND_GROUP": {
                            // SEND_GROUP;email;groupId;content
                            int userId = userDAO.getUserIdByEmail(parts[1]);
                            int groupId = Integer.parseInt(parts[2]);
                            String content = parts[3];

                            boolean ok = messageDAO.saveGroupMessage(userId, groupId, content);

                            response = ok ? "SEND_GROUP_SUCCESS" : "SEND_GROUP_FAIL";
                            break;
                        }
                        case "GET_GROUP_MESSAGES": {
                            
                            int groupId = Integer.parseInt(parts[1]);

                            
                            List<String> messages = messageDAO.getMessagesByGroup(groupId);

                            
                            response = "GROUP_MESSAGES;" + String.join(";", messages);
                            break;
                        }

                        default:
                            response = "UNKNOWN_COMMAND";
                            break;
                    }
                } catch (IllegalArgumentException e) {
                    // Lỗi này thường do Date.valueOf sai định dạng ngày tháng
                    System.err.println("Lỗi định dạng dữ liệu: " + e.getMessage());
                    response = "ERROR;Invalid Data Format";
                } catch (Exception e) {
                    e.printStackTrace();
                    response = "ERROR;Server Exception";
                }

                // Gửi phản hồi về Client
                out.println(response);
            }

        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } finally {
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
