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
import model.GroupInfo;
import model.GroupMember;

/**
 * Class này chịu trách nhiệm xử lý từng kết nối của Client gửi lên. Mỗi Client
 * kết nối sẽ chạy trên 1 luồng (Thread) riêng biệt.
 */
public class ClientHandler implements Runnable {

    private Socket socket;
    private UserDao userDAO;
    private GroupDao groupDAO;
    private DataInputStream dis;
    private DataOutputStream dos;

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
            dis = new DataInputStream(socket.getInputStream());
            dos = new DataOutputStream(socket.getOutputStream());

            // Vòng lặp liên tục lắng nghe yêu cầu từ Client
            while (true) {
                String request = dis.readUTF();
                System.out.println("Client Request: " + request); // Ghi log ra màn hình server

                // file  
                if (request.startsWith("SEND_FILE_PRIVATE")
                        || request.startsWith("SEND_FILE_GROUP")) {

                    handleSendFile(request);
                    continue; // QUAN TRỌNG: không cho rơi xuống switch
                }

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
                                            + user.getId() + ";"
                                            + user.getEmail() + ";"
                                            + user.getName() + ";"
                                            + user.getGender() + ";"
                                            + user.getDob().toString();
                                    //ClientManager.add(email, this);
                                    String clientIP = socket.getInetAddress().getHostAddress();
                                     ClientManager.add(
                    user.getEmail(),
                    user.getId(),
                    clientIP,
                    this
            );
                                  
                                    /////////////
//
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

//                        case "SEND_GROUP": {
//                            // SEND_GROUP;email;groupId;content
//                            int userId = userDAO.getUserIdByEmail(parts[1]);
//                            int groupId = Integer.parseInt(parts[2]);
//                            String content = parts[3];
//
//                            boolean ok = messageDAO.saveGroupMessage(userId, groupId, content);
//
//                            response = ok ? "SEND_GROUP_SUCCESS" : "SEND_GROUP_FAIL";
//                            break;
//                        }
                        case "SEND_GROUP": {
                            // SEND_GROUP;email;groupId;content
                            int userId = userDAO.getUserIdByEmail(parts[1]);
                            int groupId = Integer.parseInt(parts[2]);

                            // Nối tất cả phần còn lại nếu người dùng gửi nhiều dấu ; trong content
                            StringBuilder sb = new StringBuilder();
                            for (int i = 3; i < parts.length; i++) {
                                sb.append(parts[i]);
                                if (i != parts.length - 1) {
                                    sb.append(";");
                                }
                            }
                            String content = sb.toString();

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

                        case "GET_NOT_JOINED_GROUPS": {
                            // GET_NOT_JOINED_GROUPS;email
                            if (parts.length < 2) {
                                response = "GROUP_LIST";
                                break;
                            }

                            String email = parts[1];
                            int userId = userDAO.getUserIdByEmail(email);

                            List<String> groups = groupDAO.getNotJoinedGroups(userId);

                            response = "GROUP_LIST";
                            for (String g : groups) {
                                response += ";" + g;
                            }
                            break;
                        }

// Join group
                        case "JOIN_GROUP": {
                            // JOIN_GROUP;email;groupId
                            if (parts.length < 3) {
                                response = "JOIN_GROUP_FAIL";
                                break;
                            }

                            String email = parts[1];
                            int groupId = Integer.parseInt(parts[2]);

                            int userId = userDAO.getUserIdByEmail(email);

                            boolean ok = groupDAO.joinGroup(groupId, userId);
                            response = ok ? "JOIN_GROUP_SUCCESS" : "JOIN_GROUP_FAIL";
                            break;
                        }
                        case "GET_NOT_FRIEND_USERS": {
                            // GET_NOT_FRIEND_USERS;email
                            if (parts.length < 2) {
                                response = "USER_LIST";
                                break;
                            }

                            String email = parts[1];
                            int myId = userDAO.getUserIdByEmail(email);

                            List<String> users = userDAO.getNotFriendUsers(myId);

                            response = "USER_LIST";
                            for (String u : users) {
                                response += ";" + u; // id:email
                            }
                            break;
                        }
                        case "ADD_FRIEND": {
                            // ADD_FRIEND;email;friendId
                            if (parts.length < 3) {
                                response = "ADD_FRIEND_FAIL";
                                break;
                            }

                            String email = parts[1];
                            int friendId = Integer.parseInt(parts[2]);

                            int myId = userDAO.getUserIdByEmail(email);

                            boolean ok = userDAO.addFriend(myId, friendId);
                            response = ok ? "ADD_FRIEND_SUCCESS" : "ADD_FRIEND_FAIL";
                            break;
                        }

                        case "GET_FRIEND_REQUESTS": {
                            int userId = userDAO.getUserIdByEmail(parts[1]);

                            List<String> reqs = userDAO.getFriendRequests(userId);

                            response = "FRIEND_REQUESTS";
                            for (String r : reqs) {
                                response += ";" + r; // id:full_name
                            }
                            break;
                        }

//                        case "ACCEPT_FRIEND": {
//                            // ACCEPT_FRIEND;myEmail;friendId
//                            int myId = userDAO.getUserIdByEmail(parts[1]);
//                            int friendId = Integer.parseInt(parts[2]);
//
//                            boolean ok = userDAO.acceptFriend(myId, friendId);
//                            response = ok ? "ACCEPT_SUCCESS" : "ACCEPT_FAIL";
//                            break;
//                        }
                        case "ACCEPT_FRIEND": {
                            String myEmail = parts[1];
                            int myId = userDAO.getUserIdByEmail(myEmail);
                            int friendId = Integer.parseInt(parts[2]);

                            boolean ok = userDAO.acceptFriend(myId, friendId);

                            if (ok) {
                                response = "ACCEPT_SUCCESS";

                                // ===== PUSH REALTIME =====
                                String myName = UserDao.getFullNameByEmail(myEmail);
                                String friendEmail = userDAO.getEmailByUserId(friendId);

                                ClientHandler friendHandler = ClientManager.get(friendEmail);
                                if (friendHandler != null) {
                                    friendHandler.send(
                                            "FRIEND_ACCEPTED;" + myEmail + ";" + myName
                                    );
                                }
                            } else {
                                response = "ACCEPT_FAIL";
                            }
                            break;
                        }

                        case "REJECT_FRIEND": {
                            // REJECT_FRIEND;myEmail;friendId
                            int myId = userDAO.getUserIdByEmail(parts[1]);
                            int friendId = Integer.parseInt(parts[2]);

                            boolean ok = userDAO.rejectFriend(myId, friendId);
                            response = ok ? "REJECT_SUCCESS" : "REJECT_FAIL";
                            break;
                        }

                        case "BLOCK_FRIEND": {
                            if (parts.length < 3) {
                                response = "BLOCK_FAIL";
                                break;
                            }
                            int myId = userDAO.getUserIdByEmail(parts[1]);
                            int targetId = Integer.parseInt(parts[2]);
                            boolean ok = userDAO.blockUser(myId, targetId);
                            response = ok ? "BLOCK_SUCCESS" : "BLOCK_FAIL";
                            break;
                        }

                        case "GET_BLOCKED_FRIENDS": {
                            if (parts.length < 2) {
                                response = "BLOCKED_LIST";
                                break;
                            }

                            String email = parts[1];
                            int myId = userDAO.getUserIdByEmail(email);

                            List<String> blocked = userDAO.getBlockedUsers(myId);

                            response = "BLOCKED_LIST";
                            for (String u : blocked) {
                                response += ";" + u; // id:name:email
                            }
                            break;
                        }

                        case "UNBLOCK_USER": {
                            // UNBLOCK_USER;myEmail;blockedId
                            if (parts.length < 3) {
                                response = "UNBLOCK_FAIL";
                                break;
                            }

                            String myEmail = parts[1];
                            int blockedId = Integer.parseInt(parts[2]);

                            int myId = userDAO.getUserIdByEmail(myEmail);

                            boolean ok = userDAO.unblockUser(myId, blockedId);
                            response = ok ? "UNBLOCK_SUCCESS" : "UNBLOCK_FAIL";
                            break;
                        }

                        case "GET_FRIENDS": {
                            // Cấu trúc: GET_FRIENDS;email
                            if (parts.length < 2) {
                                response = "FRIEND_LIST";
                                break;
                            }

                            String email = parts[1];
                            int myId = userDAO.getUserIdByEmail(email);

                            List<String> friends = userDAO.getFriends(myId);

                            // Trả về dạng: FRIEND_LIST;id:name:email:avatar;id:name:email:avatar;...
                            response = "FRIEND_LIST";
                            for (String f : friends) {
                                response += ";" + f;
                            }
                            break;
                        }

                        case "GET_PRIVATE_MESSAGES": {
                            // Cấu trúc: GET_PRIVATE_MESSAGES;myEmail;friendId
                            String myEmail = parts[1];
                            int friendId = Integer.parseInt(parts[2]);

                            int myId = userDAO.getUserIdByEmail(myEmail);

                            List<String> messages = messageDAO.getPrivateMessages(myId, friendId);

                            response = "PRIVATE_MESSAGES;" + String.join(";", messages);
                            break;
                        }

                        case "SEND_PRIVATE": {
                            // Cấu trúc: SEND_PRIVATE;senderEmail;receiverId;content
                            if (parts.length < 4) {
                                response = "SEND_PRIVATE_FAIL;Missing Data";
                                break;
                            }

                            String senderEmail = parts[1];
                            int receiverId = Integer.parseInt(parts[2]);

                            // Nối tất cả phần còn lại nếu content có dấu ;
                            StringBuilder sb = new StringBuilder();
                            for (int i = 3; i < parts.length; i++) {
                                sb.append(parts[i]);
                                if (i != parts.length - 1) {
                                    sb.append(";");
                                }
                            }
                            String content = sb.toString();

                            // Lấy senderId từ email
                            int senderId = userDAO.getUserIdByEmail(senderEmail);

                            // Lưu vào bảng messages
                            boolean ok = messageDAO.savePrivateMessage(senderId, receiverId, content);

                            response = ok ? "SEND_PRIVATE_SUCCESS" : "SEND_PRIVATE_FAIL";
                            break;
                        }

                        case "REMOVE_FRIEND": {
                            // REMOVE_FRIEND;myEmail;friendId
                            if (parts.length < 3) {
                                response = "REMOVE_FRIEND_FAIL";
                                break;
                            }

                            String myEmail = parts[1];
                            int myId = userDAO.getUserIdByEmail(myEmail);
                            int friendId = Integer.parseInt(parts[2]);

                            boolean ok = userDAO.removeFriend(myId, friendId);

                            if (ok) {
                                response = "REMOVE_FRIEND_SUCCESS";

                                // ===== REAL-TIME PUSH =====
                                // Lấy email + tên của người hủy
                                String myName = UserDao.getFullNameByEmail(myEmail);

                                // Lấy email của friend bị hủy
                                String friendEmail = userDAO.getEmailByUserId(friendId);

                                // Nếu friend đang online → gửi ngay
                                ClientHandler friendHandler = ClientManager.get(friendEmail);
                                if (friendHandler != null) {
                                    friendHandler.send(
                                            "FRIEND_REMOVED;" + myEmail + ";" + myName
                                    );
                                }
                            } else {
                                response = "REMOVE_FRIEND_FAIL";
                            }
                            break;
                        }

                        case "GET_USER_PROFILE": {
                            // GET_USER_PROFILE;friendId
                            if (parts.length < 2) {
                                response = "USER_PROFILE_FAIL";
                                break;
                            }

                            int friendId = Integer.parseInt(parts[1]);

                            User u = userDAO.getUserById(friendId); // cần hàm này

                            if (u != null) {
                                response = "USER_PROFILE;"
                                        + u.getName() + ";"
                                        + u.getGender() + ";"
                                        + u.getDob();
                            } else {
                                response = "USER_PROFILE_FAIL";
                            }
                            break;
                        }

                        case "GET_GROUP_INFO": {
                            int groupId = Integer.parseInt(parts[1]);

                            GroupInfo g = groupDAO.getGroupInfo(groupId);
                            if (g == null) {
                                response = "GROUP_INFO_FAIL";
                                break;
                            }

                            List<GroupMember> members = groupDAO.getGroupMembers(groupId);

                            StringBuilder sb = new StringBuilder();
                            sb.append("GROUP_INFO;")
                                    .append(g.getName()).append(";")
                                    .append(g.getCreatedBy()).append(";")
                                    .append(g.getMemberCount());

                            for (GroupMember m : members) {
                                sb.append(";")
                                        .append(m.getId()).append(":")
                                        .append(m.getName()).append(":")
                                        .append(m.getRole());
                            }

                            response = sb.toString();
                            break;
                        }

                        //////////// gọi
                        case "GET_FRIEND_IP": {
                            // GET_FRIEND_IP;friendId
                            if (parts.length < 2) {
                                response = "FRIEND_OFFLINE";
                                break;
                            }

                            int friendId = Integer.parseInt(parts[1]);

                            String ip = ClientManager.getIP(friendId);

                            if (ip != null) {
                                response = "FRIEND_IP;" + ip;
                            } else {
                                response = "FRIEND_OFFLINE";
                            }
                            break;
                        }

                        case "REMOVE_GROUP_MEMBER": {
                            int groupId = Integer.parseInt(parts[1]);
                            int adminId = Integer.parseInt(parts[2]);
                            int targetId = Integer.parseInt(parts[3]);

                            // ✅ Check quyền Admin
                            if (!groupDAO.isAdmin(groupId, adminId)) {
                                response = "NOT_ADMIN";
                                break;
                            }

                            boolean ok = groupDAO.removeMember(groupId, targetId);
                            response = ok ? "REMOVE_MEMBER_SUCCESS" : "REMOVE_MEMBER_FAIL";
                            break;
                        }

                        /////////////////////
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
                if (!response.isEmpty()) {
                    dos.writeUTF(response);
                    dos.flush();
                }

            }

        } catch (IOException e) {
            System.out.println("Client disconnected.");
        } catch (Exception ex) {
            System.getLogger(ClientHandler.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
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

    public synchronized void send(String msg) {
        try {
            dos.writeUTF(msg);
            dos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // file
    private void handleSendFile(String header) throws Exception {

        String[] p = header.split(";");
        String type = p[0];
        String email = p[1];
        int targetId = Integer.parseInt(p[2]);
        String fileName = p[3];
        long fileSize = Long.parseLong(p[4]);

        int senderId = userDAO.getUserIdByEmail(email);

        File dir = new File("uploads");
        if (!dir.exists()) {
            dir.mkdir();
        }

        File file = new File(dir, System.currentTimeMillis() + "_" + fileName);
        FileOutputStream fos = new FileOutputStream(file);

        byte[] buffer = new byte[4096];
        long remaining = fileSize;

        while (remaining > 0) {
            int read = dis.read(buffer, 0,
                    (int) Math.min(buffer.length, remaining));

            if (read == -1) {
                break;
            }

            fos.write(buffer, 0, read);
            remaining -= read;
        }

        fos.close();

        // Lưu DB
        if (type.equals("SEND_FILE_GROUP")) {
            messageDAO.saveFileMessageGroup(senderId, targetId, fileName, file.getPath());
        } else {
            messageDAO.saveFileMessagePrivate(senderId, targetId, fileName, file.getPath());
        }

        dos.writeUTF("SEND_FILE_SUCCESS");
        dos.flush();
    }

    private void handleDownloadFile(String header) {
        try {
            // Cấu trúc: DOWNLOAD_FILE;filePath
            String[] parts = header.split(";", 2);
            if (parts.length < 2) {
                dos.writeUTF("DOWNLOAD_FAIL;Missing File Path");
                dos.flush();
                return;
            }

            String filePath = parts[1];
            File file = new File(filePath);

            if (!file.exists() || !file.isFile()) {
                dos.writeUTF("DOWNLOAD_FAIL;File Not Found");
                dos.flush();
                return;
            }

            // Gửi thông tin file cho client trước: kích thước file
            dos.writeUTF("FILE_DATA;" + file.getName() + ";" + file.length());
            dos.flush();

            // Gửi file theo buffer 4KB
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, read);
                }
            }

            dos.flush();
            System.out.println("File sent: " + file.getName());

        } catch (IOException e) {
            try {
                dos.writeUTF("DOWNLOAD_FAIL;Server Error");
                dos.flush();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            e.printStackTrace();
        }
    }

}
