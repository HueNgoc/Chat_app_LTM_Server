package utils;

import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.Random;
import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

public class EmailService {

    // ====== CẤU HÌNH EMAIL ADMIN ======
    private static final String FROM_EMAIL = "hueltn.23it@vku.udn.vn";
    private static final String APP_PASSWORD = "axha nprk borb psew"; 
    // App Password 16 ký tự (KHÔNG dùng mật khẩu Gmail)

    // ====== SINH OTP 6 SỐ ======
    public static String generateOTP() {
        Random rnd = new Random();
        int number = rnd.nextInt(1_000_000); // 0 → 999999
        return String.format("%06d", number);
    }

    // ====== GỬI EMAIL OTP ======
    public static boolean sendEmail(String toEmail, String otpCode) throws UnsupportedEncodingException {

        // Cấu hình SMTP Gmail
        Properties prop = new Properties();
        prop.put("mail.smtp.host", "smtp.gmail.com");
        prop.put("mail.smtp.port", "587");
        prop.put("mail.smtp.auth", "true");
        prop.put("mail.smtp.starttls.enable", "true");

        // Xác thực Gmail
        Session session = Session.getInstance(prop, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(FROM_EMAIL, APP_PASSWORD);
            }
        });

        try {
            MimeMessage message = new MimeMessage(session);

            // Người gửi
            message.setFrom(new InternetAddress(FROM_EMAIL, "App Chat Admin"));

            // Người nhận
            message.setRecipients(
                    Message.RecipientType.TO,
                    InternetAddress.parse(toEmail)
            );

            // Tiêu đề
            message.setSubject("Mã xác thực đăng ký App Chat", "UTF-8");

            // Nội dung
            message.setText(
                    "Xin chào,\n\n"
                    + "Cảm ơn bạn đã đăng ký tài khoản.\n"
                    + "Mã OTP của bạn là: " + otpCode + "\n\n"
                    + "Mã có hiệu lực trong 10 phút.\n"
                    + "Vui lòng KHÔNG chia sẻ mã này cho bất kỳ ai.",
                    "UTF-8"
            );

            // Gửi email
            Transport.send(message);
            System.out.println("Đã gửi OTP tới: " + toEmail);
            return true;

        } catch (MessagingException e) {
            System.err.println("Lỗi gửi email OTP!");
            e.printStackTrace();
            return false;
        }
    }
}
