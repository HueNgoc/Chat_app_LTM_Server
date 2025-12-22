/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;

import java.io.UnsupportedEncodingException;
import model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import utils.EmailService;
import utils.PasswordUtils;

/**
 *
 * @author Admin
 */
public class UserDao {

    // 1. ĐĂNG KÝ: Transaction + OTP 10 phút
    public boolean registerUser(User user) {
        Connection conn = null;
        PreparedStatement stmtUser = null;
        PreparedStatement stmtCred = null;

        try {
            conn = DatabaseConnection.getConnection();
            conn.setAutoCommit(false); // Bắt đầu Transaction

            // ===== B1: Thêm user vào bảng users =====
            String sqlUser = "INSERT INTO users (full_name, gender, dob, created_at) VALUES (?, ?, ?, NOW())";
            stmtUser = conn.prepareStatement(sqlUser, Statement.RETURN_GENERATED_KEYS);
            stmtUser.setString(1, user.getName());
            stmtUser.setString(2, user.getGender());
            stmtUser.setDate(3, user.getDob());

            int rowsUser = stmtUser.executeUpdate();
            if (rowsUser == 0) {
                throw new SQLException("Lỗi: không thể thêm user.");
            }

            // Lấy ID vừa tạo
            int userId = 0;
            try (ResultSet generatedKeys = stmtUser.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    userId = generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Lỗi: không lấy được ID user.");
                }
            }

            // ===== B2: Thêm user_credentials với password hash và OTP =====
            String otp = EmailService.generateOTP(); // OTP 6 số
            String hashedPassword = PasswordUtils.hash(user.getPassword()); // Hash password

            String sqlCred = "INSERT INTO user_credentials (user_id, username, password_hash, otp_code, otp_expiry, is_verified) "
                    + "VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), FALSE)";
            stmtCred = conn.prepareStatement(sqlCred);
            stmtCred.setInt(1, userId);
            stmtCred.setString(2, user.getEmail());
            stmtCred.setString(3, hashedPassword);
            stmtCred.setString(4, otp);

            int rowsCred = stmtCred.executeUpdate();
            if (rowsCred == 0) {
                throw new SQLException("Lỗi: không thể thêm thông tin đăng nhập.");
            }

            conn.commit(); // Xác nhận Transaction thành công

            // ===== B3: Gửi OTP thực tế qua email =====
            boolean emailSent = EmailService.sendEmail(user.getEmail(), otp);
            if (!emailSent) {
                System.err.println("Cảnh báo: OTP chưa gửi được tới email.");
            }

            System.out.println("Đăng ký thành công! OTP đã gửi tới email: " + user.getEmail());
            return true;

        } catch (SQLException e) {
            System.err.println("SQL Error trong registerUser: " + e.getMessage());
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
        } catch (UnsupportedEncodingException e) {
            System.err.println("Lỗi encoding khi gửi email OTP: " + e.getMessage());
            e.printStackTrace();
            return false;
        } catch (Exception e) {
            System.err.println("Lỗi khác trong registerUser: " + e.getMessage());
            e.printStackTrace();
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            return false;
        } finally {
            // ===== Đóng resource =====
            try {
                if (stmtCred != null) {
                    stmtCred.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                if (stmtUser != null) {
                    stmtUser.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            try {
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // 2. XÁC THỰC OTP
    public boolean verifyOTP(String email, String otpInput) {
        String sql = "SELECT otp_code, otp_expiry FROM user_credentials WHERE username = ?";
        String updateSql = "UPDATE user_credentials SET is_verified = TRUE, otp_code = NULL WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String dbOtp = rs.getString("otp_code");
                Timestamp expiry = rs.getTimestamp("otp_expiry");
                Timestamp now = new Timestamp(System.currentTimeMillis());

                // Logic: OTP phải khớp VÀ thời gian hiện tại chưa vượt quá thời gian hết hạn
                if (dbOtp != null && dbOtp.equals(otpInput) && expiry.after(now)) {
                    // Update trạng thái đã xác thực
                    try (PreparedStatement upStmt = conn.prepareStatement(updateSql)) {
                        upStmt.setString(1, email);
                        upStmt.executeUpdate();
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    // 3. ĐĂNG NHẬP
    public User checkLogin(String email, String password) {

        String sql = """
        SELECT 
            u.id,
            u.full_name,
            u.gender,
            u.dob,
            uc.username,
            uc.password_hash,
            uc.is_verified,
            uc.is_locked
        FROM user_credentials uc
        JOIN users u ON uc.user_id = u.id
        WHERE uc.username = ?
    """;

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {

                if (rs.getBoolean("is_locked")) {
                    return null;
                }
                if (!rs.getBoolean("is_verified")) {
                    return null;
                }

                if (!PasswordUtils.check(password, rs.getString("password_hash"))) {
                    return null;
                }

                return new User(
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        rs.getString("username"), // LẤY TỪ DB
                        rs.getString("gender"),
                        rs.getDate("dob")
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 4. CẬP NHẬT THÔNG TIN
    public boolean updateUserInfo(User user) {
        // Subquery để tìm id từ email (username)
        String sql = "UPDATE users SET full_name = ?, gender = ?, dob = ? "
                + "WHERE id = (SELECT user_id FROM user_credentials WHERE username = ?)";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, user.getName());
            stmt.setString(2, user.getGender());
            stmt.setDate(3, user.getDob());
            stmt.setString(4, user.getEmail());

            return stmt.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String getFullNameByEmail(String email) {
        String sql = """
        SELECT u.full_name
        FROM user_credentials uc
        JOIN users u ON uc.user_id = u.id
        WHERE uc.username = ?
    """;

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getString("full_name");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return email; // fallback
    }

    public int getUserIdByEmail(String email) {
        String sql = """
        SELECT u.id
        FROM user_credentials uc
        JOIN users u ON uc.user_id = u.id
        WHERE uc.username = ?
    """;

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                return rs.getInt("id");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return -1; // Không tìm thấy
    }

    public List<String> getNotFriendUsers(int myId) {
        List<String> list = new ArrayList<>();

        String sql = """
        SELECT u.id, uc.username
        FROM users u
        JOIN user_credentials uc ON u.id = uc.user_id
        WHERE u.id != ?
        AND u.id NOT IN (
            SELECT friend_id FROM friends WHERE user_id = ?
            UNION
            SELECT user_id FROM friends WHERE friend_id = ?
        )
    """;

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, myId);
            ps.setInt(2, myId);
            ps.setInt(3, myId);

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getInt("id") + ":" + rs.getString("username"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return list;
    }

    public boolean addFriend(int userId, int friendId) {
        String sql = """
        INSERT INTO friends(user_id, friend_id, status)
        VALUES (?, ?, 'Pending')
    """;

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setInt(2, friendId);
            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<String> getFriendRequests(int userId) {
        List<String> list = new ArrayList<>();
        String sql = """
        SELECT u.id, u.full_name
        FROM friends f
        JOIN users u ON f.user_id = u.id
        WHERE f.friend_id = ? AND f.status = 'Pending'
    """;

        try (Connection con = DatabaseConnection.getConnection(); PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                list.add(rs.getInt("id") + ":" + rs.getString("full_name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

   public boolean acceptFriend(int myId, int friendId) {
    String sqlUpdate = "UPDATE friends SET status='Accepted' WHERE user_id=? AND friend_id=?";
    String sqlInsertMirror = "INSERT INTO friends(user_id, friend_id, status) VALUES(?, ?, 'Accepted') " +
                             "ON DUPLICATE KEY UPDATE status='Accepted'";
    Connection conn = null;
    PreparedStatement psUpdate = null;
    PreparedStatement psInsert = null;

    try {
        conn = DatabaseConnection.getConnection();
        conn.setAutoCommit(false);

        // Update lời mời
        psUpdate = conn.prepareStatement(sqlUpdate);
        psUpdate.setInt(1, friendId);
        psUpdate.setInt(2, myId);
        int updated = psUpdate.executeUpdate();

        if (updated > 0) {
            // Thêm bản ghi mirror
            psInsert = conn.prepareStatement(sqlInsertMirror);
            psInsert.setInt(1, myId);
            psInsert.setInt(2, friendId);
            psInsert.executeUpdate();

            conn.commit();
            return true;
        } else {
            conn.rollback();
        }

    } catch (SQLException e) {
        e.printStackTrace();
        try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
    } finally {
        try { if (psUpdate != null) psUpdate.close(); } catch (SQLException e) { e.printStackTrace(); }
        try { if (psInsert != null) psInsert.close(); } catch (SQLException e) { e.printStackTrace(); }
        try { if (conn != null) conn.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
    return false;
}


    public boolean rejectFriend(int myId, int friendId) {
        String sql = """
        DELETE FROM friends
        WHERE user_id = ? AND friend_id = ?
    """;

        try (Connection c = DatabaseConnection.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, friendId);
            ps.setInt(2, myId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public List<String> getFriends(int myUserId) {
    List<String> friends = new ArrayList<>();
    String sql = """
        SELECT DISTINCT u.id, u.full_name, uc.username, u.avatar_url
        FROM friends f
        JOIN users u ON (u.id = f.friend_id AND f.user_id = ?)
        JOIN user_credentials uc ON uc.user_id = u.id
        WHERE f.status='Accepted'
        UNION
        SELECT DISTINCT u.id, u.full_name, uc.username, u.avatar_url
        FROM friends f
        JOIN users u ON (u.id = f.user_id AND f.friend_id = ?)
        JOIN user_credentials uc ON uc.user_id = u.id
        WHERE f.status='Accepted'
    """;

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, myUserId);
        ps.setInt(2, myUserId);

        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            int id = rs.getInt("id");
            String name = rs.getString("full_name");
            String email = rs.getString("username");
            String avatar = rs.getString("avatar_url") != null ? rs.getString("avatar_url") : "";

            friends.add(id + ":" + name + ":" + email + ":" + avatar);
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return friends;
}


    public boolean removeFriend(int myId, int friendId) {
        String sql = "DELETE FROM friends WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)";
        try (Connection conn =DatabaseConnection.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, myId);
            ps.setInt(2, friendId);
            ps.setInt(3, friendId);
            ps.setInt(4, myId);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Block một user
public boolean blockUser(int myId, int targetId) {
    String deleteFriendSql = "DELETE FROM friends WHERE (user_id=? AND friend_id=?) OR (user_id=? AND friend_id=?)";
    String insertBlockSql = "INSERT INTO blocks(blocker_id, blocked_id) VALUES (?, ?)";

    Connection conn = null;
    PreparedStatement psDelete = null;
    PreparedStatement psInsert = null;

    try {
        conn = DatabaseConnection.getConnection();
        conn.setAutoCommit(false); // Bắt đầu transaction

        // 1. Xóa khỏi friends nếu đang là friend
        psDelete = conn.prepareStatement(deleteFriendSql);
        psDelete.setInt(1, myId);
        psDelete.setInt(2, targetId);
        psDelete.setInt(3, targetId);
        psDelete.setInt(4, myId);
        psDelete.executeUpdate();

        // 2. Thêm vào bảng blocks
        psInsert = conn.prepareStatement(insertBlockSql);
        psInsert.setInt(1, myId);
        psInsert.setInt(2, targetId);
        psInsert.executeUpdate();

        conn.commit(); // commit transaction
        return true;

    } catch (SQLException e) {
        e.printStackTrace();
        try {
            if (conn != null) conn.rollback(); // rollback nếu lỗi
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return false;

    } finally {
        try { if (psDelete != null) psDelete.close(); } catch (SQLException e) { e.printStackTrace(); }
        try { if (psInsert != null) psInsert.close(); } catch (SQLException e) { e.printStackTrace(); }
        try { if (conn != null) conn.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
}

// Kiểm tra xem user có bị block không
public boolean isBlocked(int myId, int targetId) {
    String sql = "SELECT * FROM blocks WHERE blocker_id=? AND blocked_id=?";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, myId);
        ps.setInt(2, targetId);
        ResultSet rs = ps.executeQuery();
        return rs.next();

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return false;
}

// Lấy danh sách user mà tôi đã block
public List<String> getBlockedUsers(int myId) {
    List<String> list = new ArrayList<>();
    String sql = "SELECT u.id, u.full_name, uc.username FROM blocks b " +
                 "JOIN users u ON b.blocked_id = u.id " +
                 "JOIN user_credentials uc ON uc.user_id = u.id " +
                 "WHERE b.blocker_id=?";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, myId);
        ResultSet rs = ps.executeQuery();
        while (rs.next()) {
            list.add(rs.getInt("id") + ":" + rs.getString("full_name") + ":" + rs.getString("username"));
        }

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return list;
}

// Unblock user
public boolean unblockUser(int myId, int targetId) {
    String sql = "DELETE FROM blocks WHERE blocker_id=? AND blocked_id=?";
    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, myId);
        ps.setInt(2, targetId);
        return ps.executeUpdate() > 0;

    } catch (SQLException e) {
        e.printStackTrace();
    }
    return false;
}

public String getEmailByUserId(int userId) {
    String sql = """
        SELECT uc.username
        FROM user_credentials uc
        WHERE uc.user_id = ?
    """;

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {

        ps.setInt(1, userId);
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            return rs.getString("username");
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return null;
}

public User getUserById(int id) {
    String sql = "SELECT full_name, gender, dob FROM users WHERE id = ?";
    try (Connection c = DatabaseConnection.getConnection();
         PreparedStatement ps = c.prepareStatement(sql)) {

        ps.setInt(1, id);
        ResultSet rs = ps.executeQuery();

        if (rs.next()) {
            User u = new User();
            u.setName(rs.getString("full_name"));
            u.setGender(rs.getString("gender"));
            u.setDob(rs.getDate("dob"));
            return u;
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return null;
}


}
