/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.ArrayList;
/**
 *
 * @author Admin
 */
public class GroupDao {
    // Tạo nhóm mới
public int createGroup(String groupName, int creatorId) {
    String sqlGroup = "INSERT INTO chat_groups(group_name, created_by) VALUES (?, ?)";
    String sqlMember = "INSERT INTO group_members(group_id, user_id) VALUES (?, ?)";

    Connection conn = null;

    try {
        conn = DatabaseConnection.getConnection();
        conn.setAutoCommit(false);

        PreparedStatement ps = conn.prepareStatement(
            sqlGroup, Statement.RETURN_GENERATED_KEYS
        );
        ps.setString(1, groupName);
        ps.setInt(2, creatorId);
        ps.executeUpdate();

        ResultSet rs = ps.getGeneratedKeys();
        if (rs.next()) {
            int groupId = rs.getInt(1);

            PreparedStatement ps2 = conn.prepareStatement(sqlMember);
            ps2.setInt(1, groupId);
            ps2.setInt(2, creatorId);
            ps2.executeUpdate();

            conn.commit();
            return groupId;
        }

        conn.rollback();
    } catch (Exception e) {
        try {
            if (conn != null) conn.rollback();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        e.printStackTrace();
    }
    return -1;
}


    // Gia nhập nhóm
    public boolean joinGroup(int groupId, int userId) {
        String sql = "INSERT IGNORE INTO group_members(group_id, user_id) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { }
        return false;
    }

    // Rời nhóm
    public boolean leaveGroup(int groupId, int userId) {
        String sql = "DELETE FROM group_members WHERE group_id=? AND user_id=?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, groupId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) { }
        return false;
    }

    // Lấy danh sách nhóm của user
    public List<String> getGroups(int userId) {
        List<String> list = new ArrayList<>();
        String sql = """
            SELECT g.id, g.group_name
            FROM chat_groups g
            JOIN group_members gm ON g.id = gm.group_id
            WHERE gm.user_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getInt("id") + ":" + rs.getString("group_name"));
            }
        } catch (Exception e) { }
        return list;
    }
}
