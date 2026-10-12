import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DatabaseManager.java
 * High-performance, persistent file-based relational database manager.
 * Stores tables (Users, Buses, Bookings) permanently in the data/ directory.
 * Requires zero external database server setup or 3rd-party JAR dependencies.
 */
public class DatabaseManager {
    private static final Path DATA_DIR = Paths.get("data");
    private static final Path USERS_FILE = DATA_DIR.resolve("users.db");
    private static final Path BUSES_FILE = DATA_DIR.resolve("buses.db");
    private static final Path TICKETS_FILE = DATA_DIR.resolve("tickets.db");

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DatabaseManager() {
        initDatabase();
    }

    /**
     * Initializes the database directory and tables if they do not exist.
     * Seeds initial admin and demo user accounts and default fleet.
     */
    public synchronized void initDatabase() {
        try {
            if (!Files.exists(DATA_DIR)) {
                Files.createDirectories(DATA_DIR);
            }

            // 1. Initialize Users Table
            if (!Files.exists(USERS_FILE)) {
                Files.createFile(USERS_FILE);
                // Seed default admin and demo user
                addUserRecord(new User(1, "admin", User.hashPassword("admin123"), "System Administrator", "9876543210", "ADMIN"));
                addUserRecord(new User(2, "demo", User.hashPassword("demo123"), "Rahul Sharma", "9876543210", "USER"));
            }

            // 2. Initialize Buses Table
            if (!Files.exists(BUSES_FILE)) {
                Files.createFile(BUSES_FILE);
            }

            // 3. Initialize Tickets Table
            if (!Files.exists(TICKETS_FILE)) {
                Files.createFile(TICKETS_FILE);
            }
        } catch (IOException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }
    }

    // =========================================================================
    // USER AUTHENTICATION & MANAGEMENT
    // =========================================================================

    public synchronized List<User> getAllUsers() {
        List<User> list = new ArrayList<>();
        if (!Files.exists(USERS_FILE)) return list;
        try {
            List<String> lines = Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length >= 6) {
                    list.add(new User(Integer.parseInt(p[0]), p[1], p[2], p[3], p[4], p[5]));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading users: " + e.getMessage());
        }
        return list;
    }

    public synchronized User findUserByUsername(String username) {
        if (username == null) return null;
        for (User u : getAllUsers()) {
            if (u.getUsername().equalsIgnoreCase(username.trim())) {
                return u;
            }
        }
        return null;
    }

    public synchronized User authenticate(String username, String password) {
        User u = findUserByUsername(username);
        if (u != null && u.verifyPassword(password)) {
            return u;
        }
        return null;
    }

    public synchronized User registerUser(String username, String password, String fullName, String phone) {
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Username and password cannot be empty.");
        }
        if (findUserByUsername(username) != null) {
            throw new IllegalArgumentException("Username '" + username + "' is already registered.");
        }

        List<User> all = getAllUsers();
        int nextId = 1;
        for (User u : all) {
            if (u.getId() >= nextId) nextId = u.getId() + 1;
        }

        User newUser = new User(nextId, username, User.hashPassword(password), fullName, phone, "USER");
        addUserRecord(newUser);
        return newUser;
    }

    private synchronized void addUserRecord(User user) {
        try {
            String record = String.format("%d|%s|%s|%s|%s|%s%n",
                    user.getId(),
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getFullName(),
                    user.getPhone(),
                    user.getRole());
            Files.writeString(USERS_FILE, record, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write user: " + e.getMessage());
        }
    }

    // =========================================================================
    // BUS PERSISTENCE
    // =========================================================================

    public synchronized boolean hasStoredBuses() {
        return Files.exists(BUSES_FILE) && BUSES_FILE.toFile().length() > 0;
    }

    public synchronized List<Bus> loadBuses() {
        List<Bus> buses = new ArrayList<>();
        if (!hasStoredBuses()) return buses;
        try {
            List<String> lines = Files.readAllLines(BUSES_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length >= 8) {
                    int busNum = Integer.parseInt(p[0]);
                    String type = p[1];
                    String seatClass = p[2];
                    String src = p[3];
                    String dst = p[4];
                    String time = p[5];
                    int totalSeats = Integer.parseInt(p[6]);
                    double fare = Double.parseDouble(p[7]);
                    Bus bus = new Bus(busNum, type, seatClass, src, dst, time, totalSeats, fare);

                    // Occupied seats
                    if (p.length >= 9 && !p[8].trim().isEmpty()) {
                        String[] seatTokens = p[8].trim().split(",");
                        for (String st : seatTokens) {
                            try {
                                int s = Integer.parseInt(st.trim());
                                bus.reserveSeat(s);
                            } catch (Exception ignored) {}
                        }
                    }
                    buses.add(bus);
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading buses.db: " + e.getMessage());
        }
        return buses;
    }

    public synchronized void saveBuses(List<Bus> buses) {
        try {
            StringBuilder sb = new StringBuilder();
            for (Bus bus : buses) {
                StringBuilder seatsStr = new StringBuilder();
                boolean first = true;
                for (int i = 1; i <= bus.getTotalSeats(); i++) {
                    if (!bus.isSeatAvailable(i)) {
                        if (!first) seatsStr.append(",");
                        seatsStr.append(i);
                        first = false;
                    }
                }
                sb.append(String.format("%d|%s|%s|%s|%s|%s|%d|%.2f|%s%n",
                        bus.getBusNumber(),
                        bus.getBusType(),
                        bus.getSeatClass(),
                        bus.getSource(),
                        bus.getDestination(),
                        bus.getDepartureTime(),
                        bus.getTotalSeats(),
                        bus.getFare(),
                        seatsStr.toString()));
            }
            Files.writeString(BUSES_FILE, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to save buses.db: " + e.getMessage());
        }
    }

    // =========================================================================
    // TICKET / BOOKING PERSISTENCE
    // =========================================================================

    public static class TicketRecord {
        public int ticketNumber;
        public int busNumber;
        public String passengerName;
        public String govtId;
        public String contact;
        public int seatNumber;
        public String seatClass;
        public double fare;
        public LocalDateTime bookingTime;
        public String username;

        public TicketRecord(int ticketNumber, int busNumber, String passengerName, String govtId, String contact, int seatNumber, String seatClass, double fare, LocalDateTime bookingTime, String username) {
            this.ticketNumber = ticketNumber;
            this.busNumber = busNumber;
            this.passengerName = passengerName;
            this.govtId = govtId;
            this.contact = contact;
            this.seatNumber = seatNumber;
            this.seatClass = seatClass;
            this.fare = fare;
            this.bookingTime = bookingTime;
            this.username = username;
        }
    }

    public synchronized void saveTicket(Ticket ticket, String username) {
        try {
            String userTag = (username != null && !username.trim().isEmpty()) ? username.trim() : "guest";
            String timeStr = ticket.getBookingTime().format(DATE_FORMATTER);
            String line = String.format("%d|%d|%s|%s|%s|%d|%s|%.2f|%s|%s%n",
                    ticket.getTicketNumber(),
                    ticket.getBus().getBusNumber(),
                    ticket.getPassenger().getPassengerName(),
                    ticket.getPassenger().getPassengerId(),
                    ticket.getPassenger().getContact(),
                    ticket.getSeatNumber(),
                    ticket.getSeatClass(),
                    ticket.getFare(),
                    timeStr,
                    userTag);
            Files.writeString(TICKETS_FILE, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write ticket record: " + e.getMessage());
        }
    }

    public synchronized void deleteTicket(int ticketNumber) {
        List<TicketRecord> all = getAllTicketRecords();
        try {
            StringBuilder sb = new StringBuilder();
            for (TicketRecord r : all) {
                if (r.ticketNumber != ticketNumber) {
                    sb.append(String.format("%d|%d|%s|%s|%s|%d|%s|%.2f|%s|%s%n",
                            r.ticketNumber, r.busNumber, r.passengerName, r.govtId, r.contact,
                            r.seatNumber, r.seatClass, r.fare, r.bookingTime.format(DATE_FORMATTER), r.username));
                }
            }
            Files.writeString(TICKETS_FILE, sb.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to delete ticket: " + e.getMessage());
        }
    }

    public synchronized List<TicketRecord> getAllTicketRecords() {
        List<TicketRecord> list = new ArrayList<>();
        if (!Files.exists(TICKETS_FILE)) return list;
        try {
            List<String> lines = Files.readAllLines(TICKETS_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length >= 10) {
                    LocalDateTime dt;
                    try {
                        dt = LocalDateTime.parse(p[8], DATE_FORMATTER);
                    } catch (Exception e) {
                        dt = LocalDateTime.now();
                    }
                    list.add(new TicketRecord(
                            Integer.parseInt(p[0]),
                            Integer.parseInt(p[1]),
                            p[2], p[3], p[4],
                            Integer.parseInt(p[5]),
                            p[6],
                            Double.parseDouble(p[7]),
                            dt,
                            p[9]
                    ));
                }
            }
        } catch (Exception e) {
            System.err.println("Error reading tickets.db: " + e.getMessage());
        }
        return list;
    }

    public synchronized List<TicketRecord> getUserTickets(String username) {
        List<TicketRecord> res = new ArrayList<>();
        if (username == null) return res;
        for (TicketRecord r : getAllTicketRecords()) {
            if (username.equalsIgnoreCase(r.username)) {
                res.add(r);
            }
        }
        return res;
    }

    public synchronized int getMaxTicketNumber() {
        int max = 1000;
        for (TicketRecord r : getAllTicketRecords()) {
            if (r.ticketNumber > max) {
                max = r.ticketNumber;
            }
        }
        return max;
    }
}
