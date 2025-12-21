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

    // Lưu message nhóm
    public boolean saveGroupMessage(int senderId, int groupId, String content) {
        String sql = "INSERT INTO messages(sender_id, group_id, content, msg_type, created_at) "
                + "VALUES (?, ?, ?, 'Text', CURRENT_TIMESTAMP)";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, senderId);
            ps.setInt(2, groupId);
            ps.setString(3, content);

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Lấy tất cả message của 1 group
    public List<String> getMessagesByGroup(int groupId) {
        List<String> list = new ArrayList<>();
        String sql = """
        SELECT uc.username, u.full_name, m.content
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
                list.add(email + ":" + fullName + ":" + content);
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
