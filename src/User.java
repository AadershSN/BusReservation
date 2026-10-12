import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * User.java
 * Represents a registered user in the Bus Reservation System.
 */
public class User {
    private int id;
    private String username;
    private String passwordHash;
    private String fullName;
    private String phone;
    private String role; // "USER" or "ADMIN"

    public User(int id, String username, String passwordHash, String fullName, String phone, String role) {
        this.id = id;
        this.username = username != null ? username.trim() : "";
        this.passwordHash = passwordHash != null ? passwordHash : "";
        this.fullName = fullName != null ? fullName.trim() : "";
        this.phone = phone != null ? phone.trim() : "";
        this.role = (role != null && role.equalsIgnoreCase("ADMIN")) ? "ADMIN" : "USER";
    }

    public static String hashPassword(String password) {
        if (password == null) password = "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(password.hashCode());
        }
    }

    public boolean verifyPassword(String rawPassword) {
        return this.passwordHash.equals(hashPassword(rawPassword));
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(role); }
}
