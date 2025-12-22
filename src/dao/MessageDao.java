/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author Admin
 */
public class MessageDao {

    // Lưu message nhóm, có thể chứa emoji
    public boolean saveGroupMessage(int senderId, int groupId, String content) {
        // content có thể chứa emoji unicode
        String sql = "INSERT INTO messages(sender_id, group_id, content, msg_type, created_at) "
                + "VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, senderId);
            ps.setInt(2, groupId);

            // Chúng ta lưu emoji cùng text
            ps.setString(3, content);

            // Xác định loại message: Text hoặc Emoji
            if (containsEmoji(content)) {
                ps.setString(4, "Emoji");
            } else {
                ps.setString(4, "Text");
            }

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Kiểm tra nội dung có emoji hay không (unicode emoji)
    private boolean containsEmoji(String text) {
        if (text == null) {
            return false;
        }
        int len = text.length();
        for (int i = 0; i < len; i++) {
            int codePoint = text.codePointAt(i);
            if (Character.isSupplementaryCodePoint(codePoint)) {
                // Unicode emoji nằm trong khoảng codePoint > 0x1F000
                if ((codePoint >= 0x1F300 && codePoint <= 0x1F6FF) // Misc symbols & pictographs
                        || (codePoint >= 0x1F600 && codePoint <= 0x1F64F) // Emoticons
                        || (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) // Transport & map symbols
                        || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)) { // Supplemental Symbols & Pictographs
                    return true;
                }
            }
        }
        return false;
    }

    // Lấy message, emoji sẽ được hiển thị đúng nếu client render unicode emoji
    public List<String> getMessagesByGroup(int groupId) {
    List<String> list = new ArrayList<>();

//    String sql = """
//        SELECT m.id, uc.username, u.full_name,
//               m.content, m.msg_type
//        FROM messages m
//        JOIN users u ON m.sender_id = u.id
//        JOIN user_credentials uc ON uc.user_id = u.id
//        WHERE m.group_id = ?
//        ORDER BY m.created_at ASC
//    """;
String sql = """
    SELECT m.id, uc.username, u.full_name,
           m.content, m.msg_type, m.file_path
    FROM messages m
    JOIN users u ON m.sender_id = u.id
    JOIN user_credentials uc ON uc.user_id = u.id
    WHERE m.group_id = ?
    ORDER BY m.created_at ASC
""";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, groupId);
        ResultSet rs = ps.executeQuery();

        while (rs.next()) {
            int msgId = rs.getInt("id");
            String email = rs.getString("username");
            String fullName = rs.getString("full_name");
            String type = rs.getString("msg_type");
            String content = rs.getString("content");
            // file
            String filePath = rs.getString("file_path");
if (filePath == null) filePath = "";
            // format chuẩn để client parse
            //list.add(msgId + "|" + email + "|" + fullName + "|"+content) ;
            // chỉnh 
            // Text/Emoji
list.add(msgId + "|" + email + "|" + fullName + "|" + type + "|" + content + "|" + filePath);


        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return list;
}


    public ResultSet getGroupMessages(int groupId) {
        String sql = """
        SELECT u.full_name, m.content
        FROM messages m
        JOIN users u ON m.sender_id = u.id
        WHERE m.group_id = ?
        ORDER BY m.created_at
    """;

        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, groupId);
            return ps.executeQuery();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    public boolean saveGroupFile(int senderId, int groupId, String path) {
    String sql = """
        INSERT INTO messages(sender_id, group_id, msg_type, file_path)
        VALUES (?, ?, 'File', ?)
    """;

    try (Connection c = DatabaseConnection.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, senderId);
        ps.setInt(2, groupId);
        ps.setString(3, path);
        return ps.executeUpdate() > 0;
    } catch (Exception e) {
        e.printStackTrace();
    }
    return false;
}

public List<String> getPrivateMessages(int userId1, int userId2) {
    List<String> list = new ArrayList<>();
  String sql = """
    SELECT m.id, uc.username, u.full_name,
           m.msg_type, m.content, m.file_path
    FROM messages m
    JOIN users u ON m.sender_id = u.id
    JOIN user_credentials uc ON uc.user_id = u.id
    WHERE (m.sender_id = ? AND m.receiver_id = ?)
       OR (m.sender_id = ? AND m.receiver_id = ?)
    ORDER BY m.created_at ASC
""";


    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, userId1);
        ps.setInt(2, userId2);
        ps.setInt(3, userId2);
        ps.setInt(4, userId1);

        ResultSet rs = ps.executeQuery();
       while (rs.next()) {
    int msgId = rs.getInt("id");
    String email = rs.getString("username");
    String fullName = rs.getString("full_name");
    String type = rs.getString("msg_type");
    String content = rs.getString("content");
    String filePath = rs.getString("file_path");
    if (filePath == null) filePath = "";

    list.add(
        msgId + "|" + email + "|" + fullName + "|" +
        type + "|" + content + "|" + filePath
    );
}

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return list;
}

public boolean savePrivateMessage(int senderId, int receiverId, String content) {
    String sql = "INSERT INTO messages(sender_id, receiver_id, content, msg_type, created_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement pst = conn.prepareStatement(sql)) {

        pst.setInt(1, senderId);
        pst.setInt(2, receiverId);
        pst.setString(3, content);

        // Xác định loại message
        if (containsEmoji(content)) {
            pst.setString(4, "Emoji");
        } else {
            pst.setString(4, "Text");
        }

        int rows = pst.executeUpdate();
        return rows > 0;
    } catch (SQLException e) {
        e.printStackTrace();
        return false;
    }
}

// file 
public void saveFileMessagePrivate(
        int senderId, int receiverId,
        String fileName, String filePath) {

    String sql = """
        INSERT INTO messages
        (sender_id, receiver_id, content, msg_type, file_path)
        VALUES (?, ?, ?, 'File', ?)
    """;

    try (Connection c = DatabaseConnection.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, senderId);
        ps.setInt(2, receiverId);
        ps.setString(3, fileName);
        ps.setString(4, filePath);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}

public void saveFileMessageGroup(
        int senderId, int groupId,
        String fileName, String filePath) {

    String sql = """
        INSERT INTO messages
        (sender_id, group_id, content, msg_type, file_path)
        VALUES (?, ?, ?, 'File', ?)
    """;

    try (Connection c = DatabaseConnection.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, senderId);
        ps.setInt(2, groupId);
        ps.setString(3, fileName);
        ps.setString(4, filePath);
        ps.executeUpdate();

    } catch (Exception e) {
        e.printStackTrace();
    }
}



}
