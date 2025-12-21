/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

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
        String sql = """
        SELECT uc.username, u.full_name, m.content, m.msg_type
        FROM messages m
        JOIN users u ON m.sender_id = u.id
        JOIN user_credentials uc ON uc.user_id = u.id
        WHERE m.group_id = ?
        ORDER BY m.created_at ASC
        """;

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                String email = rs.getString("username");
                String fullName = rs.getString("full_name");
                String content = rs.getString("content");
                String type = rs.getString("msg_type");

                // Nếu là emoji, client có thể render bằng Twemoji
                list.add(email + ":" + fullName + ":" + content );
            }

        } catch (SQLException e) {
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

}
