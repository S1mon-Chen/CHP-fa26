import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        
        // 测试密码
        String password = "password";
        
        // 生成密码哈希
        String encodedPassword = encoder.encode(password);
        System.out.println("密码: " + password);
        System.out.println("生成的哈希: " + encodedPassword);
        
        // 测试匹配
        boolean matches = encoder.matches(password, encodedPassword);
        System.out.println("匹配结果: " + matches);
        
        // 测试数据库中的哈希
        String dbPassword = "$2a$10$EixZaYVK1fsbw1ZfbX3OXePaWxn96p36WQoeG6Lruj3vjPGga31lW";
        boolean dbMatches = encoder.matches(password, dbPassword);
        System.out.println("数据库哈希匹配结果: " + dbMatches);
    }
}