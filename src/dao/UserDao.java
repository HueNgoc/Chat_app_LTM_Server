/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package dao;
import java.io.UnsupportedEncodingException;
import model.User;
import java.sql.*;
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
        if (rowsUser == 0) throw new SQLException("Lỗi: không thể thêm user.");

        // Lấy ID vừa tạo
        int userId = 0;
        try (ResultSet generatedKeys = stmtUser.getGeneratedKeys()) {
            if (generatedKeys.next()) userId = generatedKeys.getInt(1);
            else throw new SQLException("Lỗi: không lấy được ID user.");
        }

        // ===== B2: Thêm user_credentials với password hash và OTP =====
        String otp = EmailService.generateOTP(); // OTP 6 số
        String hashedPassword = PasswordUtils.hash(user.getPassword()); // Hash password

        String sqlCred = "INSERT INTO user_credentials (user_id, username, password_hash, otp_code, otp_expiry, is_verified) " +
                         "VALUES (?, ?, ?, ?, DATE_ADD(NOW(), INTERVAL 10 MINUTE), FALSE)";
        stmtCred = conn.prepareStatement(sqlCred);
        stmtCred.setInt(1, userId);
        stmtCred.setString(2, user.getEmail());
        stmtCred.setString(3, hashedPassword);
        stmtCred.setString(4, otp);

        int rowsCred = stmtCred.executeUpdate();
        if (rowsCred == 0) throw new SQLException("Lỗi: không thể thêm thông tin đăng nhập.");

        conn.commit(); // Xác nhận Transaction thành công

        // ===== B3: Gửi OTP thực tế qua email =====
        boolean emailSent = EmailService.sendEmail(user.getEmail(), otp);
        if (!emailSent) System.err.println("Cảnh báo: OTP chưa gửi được tới email.");

        System.out.println("Đăng ký thành công! OTP đã gửi tới email: " + user.getEmail());
        return true;

    } catch (SQLException e) {
        System.err.println("SQL Error trong registerUser: " + e.getMessage());
        e.printStackTrace();
        try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        return false;
    } catch (UnsupportedEncodingException e) {
        System.err.println("Lỗi encoding khi gửi email OTP: " + e.getMessage());
        e.printStackTrace();
        return false;
    } catch (Exception e) {
        System.err.println("Lỗi khác trong registerUser: " + e.getMessage());
        e.printStackTrace();
        try { if (conn != null) conn.rollback(); } catch (SQLException ex) { ex.printStackTrace(); }
        return false;
    } finally {
        // ===== Đóng resource =====
        try { if (stmtCred != null) stmtCred.close(); } catch (SQLException e) { e.printStackTrace(); }
        try { if (stmtUser != null) stmtUser.close(); } catch (SQLException e) { e.printStackTrace(); }
        try { if (conn != null) conn.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
}


    // 2. XÁC THỰC OTP
    public boolean verifyOTP(String email, String otpInput) {
        String sql = "SELECT otp_code, otp_expiry FROM user_credentials WHERE username = ?";
        String updateSql = "UPDATE user_credentials SET is_verified = TRUE, otp_code = NULL WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
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
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    // 3. ĐĂNG NHẬP
    public User checkLogin(String email, String password) {
        // Join 2 bảng để lấy đủ thông tin
        String sql = "SELECT u.id, u.full_name, u.gender, u.dob, uc.password_hash, uc.is_verified, uc.is_locked " +
                     "FROM user_credentials uc JOIN users u ON uc.user_id = u.id WHERE uc.username = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                boolean isVerified = rs.getBoolean("is_verified");
                boolean isLocked = rs.getBoolean("is_locked");
                String dbHash = rs.getString("password_hash");

                if (isLocked) return null; // Tài khoản bị khóa
                if (!isVerified) return null; // Chưa nhập OTP

                // Check password (PasswordUtils.hash(password).equals(dbHash))
                if (PasswordUtils.check(password, dbHash)) { // Tạm thời so sánh thường để test
                    return new User(
                        rs.getInt("id"),
                        rs.getString("full_name"),
                        email,
                        rs.getString("gender"),
                        rs.getDate("dob")
                    );
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }
    
    // 4. CẬP NHẬT THÔNG TIN
    public boolean updateUserInfo(User user) {
        // Subquery để tìm id từ email (username)
        String sql = "UPDATE users SET full_name = ?, gender = ?, dob = ? " +
                     "WHERE id = (SELECT user_id FROM user_credentials WHERE username = ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
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
}
