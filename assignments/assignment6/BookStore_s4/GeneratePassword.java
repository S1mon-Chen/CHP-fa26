import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class GeneratePassword {
    public static void main(String[] args) {
        // 简单的密码哈希生成（用于测试）
        String password = "password";
        System.out.println("原始密码: " + password);
        
        // 注意：这里只是为了演示，实际应该使用BCrypt
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            System.out.println("SHA-256哈希: " + hexString.toString());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        
        // 提示使用BCrypt
        System.out.println("\n注意：实际应用中应使用BCrypt密码编码器");
        System.out.println("建议在Spring Boot应用中使用：");
        System.out.println("BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();");
        System.out.println("String encodedPassword = encoder.encode(\"password\");");
    }
}