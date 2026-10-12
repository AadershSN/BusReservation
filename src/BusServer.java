import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * BusServer.java
 * High-performance HTTP server built on Java's built-in com.sun.net.httpserver.HttpServer.
 * Routes HTTP requests to FleetManager, DatabaseManager, manages user authentication/sessions,
 * and serves dynamically rendered HTML pages.
 */
public class BusServer {
    private final FleetManager fleetManager;
    private final int port;
    private HttpServer server;
    private final Path webDir;
    private final Map<String, User> sessions = new ConcurrentHashMap<>();
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

    public BusServer(FleetManager fleetManager, int port) {
        this.fleetManager = fleetManager;
        this.port = port;
        this.webDir = resolveWebDir();
    }

    private static Path resolveWebDir() {
        Path p = Paths.get("web");
        if (Files.exists(p) && Files.isDirectory(p)) {
            return p.toAbsolutePath();
        }
        p = Paths.get("..", "web");
        if (Files.exists(p) && Files.isDirectory(p)) {
            return p.toAbsolutePath();
        }
        return Paths.get(System.getProperty("user.dir"), "web").toAbsolutePath();
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Core Route Handlers
        server.createContext("/", new HomeHandler());
        server.createContext("/search", new SearchHandler());
        server.createContext("/seats", new SeatsHandler());
        server.createContext("/booking", new BookingHandler());
        server.createContext("/book", new BookActionHandler());
        server.createContext("/view-ticket", new ViewTicketHandler());
        server.createContext("/cancel-ticket", new CancelTicketHandler());

        // Authentication & Database Account Routes
        server.createContext("/login", new LoginHandler());
        server.createContext("/register", new RegisterHandler());
        server.createContext("/logout", new LogoutHandler());
        server.createContext("/my-bookings", new MyBookingsHandler());
        server.createContext("/admin", new AdminHandler());

        // Static Assets
        server.createContext("/style.css", new StaticFileHandler("style.css", "text/css"));

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    // ==========================================
    // Session & Auth Helpers
    // ==========================================

    private User getSessionUser(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String c : cookies) {
                String[] parts = c.trim().split("=", 2);
                if (parts.length == 2 && "BUSGO_SESSION".equals(parts[0].trim())) {
                    String token = parts[1].trim();
                    return sessions.get(token);
                }
            }
        }
        return null;
    }

    private void createSession(HttpExchange exchange, User user) {
        String token = UUID.randomUUID().toString();
        sessions.put(token, user);
        exchange.getResponseHeaders().add("Set-Cookie", "BUSGO_SESSION=" + token + "; Path=/; HttpOnly; SameSite=Lax");
    }

    private void clearSession(HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader != null) {
            String[] cookies = cookieHeader.split(";");
            for (String c : cookies) {
                String[] parts = c.trim().split("=", 2);
                if (parts.length == 2 && "BUSGO_SESSION".equals(parts[0].trim())) {
                    sessions.remove(parts[1].trim());
                }
            }
        }
        exchange.getResponseHeaders().add("Set-Cookie", "BUSGO_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
    }

    private String renderAuthNav(User user) {
        if (user == null) {
            return "<a href=\"/login\" class=\"nav-link btn-nav-login\">Login</a>\n" +
                   "<a href=\"/register\" class=\"nav-link btn-nav-register\">Sign Up</a>";
        }
        StringBuilder sb = new StringBuilder();
        if (user.isAdmin()) {
            sb.append("<a href=\"/admin\" class=\"nav-link\">Admin Dashboard</a>\n");
        }
        sb.append("<a href=\"/my-bookings\" class=\"nav-link\">My Bookings</a>\n");
        sb.append("<span class=\"nav-user\">&#128100; ").append(escapeHtml(user.getFullName()));
        if (user.isAdmin()) {
            sb.append(" (Admin)");
        }
        sb.append("</span>\n");
        sb.append("<a href=\"/logout\" class=\"nav-link btn-nav-logout\">Logout</a>");
        return sb.toString();
    }

    // ==========================================
    // HTTP Handlers
    // ==========================================

    private class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (!path.equals("/")) {
                sendErrorPage(exchange, 404, "Page Not Found", "The requested URL was not found on this server.");
                return;
            }

            User user = getSessionUser(exchange);
            String html = readTemplate("index.html");
            ArrayList<Bus> buses = fleetManager.getBuses();
            String busCardsHtml = renderBusCards(buses);

            html = html.replace("{{BUS_CARDS}}", busCardsHtml);
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));
            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class SearchHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            User user = getSessionUser(exchange);
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String source = params.getOrDefault("source", "").trim();
            String destination = params.getOrDefault("destination", "").trim();

            ArrayList<Bus> matchingBuses = fleetManager.searchBus(source, destination);

            String html = readTemplate("search.html");
            html = html.replace("{{SEARCH_SOURCE}}", escapeHtml(source));
            html = html.replace("{{SEARCH_DESTINATION}}", escapeHtml(destination));
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));

            if (matchingBuses.isEmpty()) {
                String noBuses = "<div class=\"empty-state\">" +
                                 "<h3>No buses found for this route</h3>" +
                                 "<p>No active buses found between <strong>" + escapeHtml(source.isEmpty() ? "Any" : source) + 
                                 "</strong> and <strong>" + escapeHtml(destination.isEmpty() ? "Any" : destination) + 
                                 "</strong>. Please try searching other cities.</p>" +
                                 "<a href=\"/\" class=\"btn btn-primary\">View All Available Buses</a>" +
                                 "</div>";
                html = html.replace("{{SEARCH_RESULTS}}", noBuses);
            } else {
                html = html.replace("{{SEARCH_RESULTS}}", renderBusCards(matchingBuses));
            }

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class SeatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            User user = getSessionUser(exchange);
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String busNumStr = params.get("busNumber");

            if (busNumStr == null || busNumStr.trim().isEmpty()) {
                sendRedirect(exchange, "/");
                return;
            }

            int busNumber;
            try {
                busNumber = Integer.parseInt(busNumStr.trim());
            } catch (NumberFormatException e) {
                sendErrorPage(exchange, 400, "Invalid Bus Number", "The provided bus number is not a valid integer.");
                return;
            }

            Bus bus = fleetManager.findBus(busNumber);
            if (bus == null) {
                sendErrorPage(exchange, 404, "Bus Not Found", "Bus #" + busNumber + " does not exist in the system.");
                return;
            }

            String html = readTemplate("seats.html");
            html = html.replace("{{BUS_NUMBER}}", String.valueOf(bus.getBusNumber()));
            html = html.replace("{{BUS_TYPE}}", escapeHtml(bus.getBusType()));
            html = html.replace("{{SEAT_CLASS}}", escapeHtml(bus.getSeatClass()));
            html = html.replace("{{FARE}}", String.valueOf((int)bus.getFare()));
            html = html.replace("{{SOURCE}}", escapeHtml(bus.getSource()));
            html = html.replace("{{DESTINATION}}", escapeHtml(bus.getDestination()));
            html = html.replace("{{DEPARTURE}}", escapeHtml(bus.getDepartureTime()));
            html = html.replace("{{TOTAL_SEATS}}", String.valueOf(bus.getTotalSeats()));
            html = html.replace("{{AVAILABLE_SEATS}}", String.valueOf(bus.getAvailableSeatsCount()));
            html = html.replace("{{AC_FARE}}", String.valueOf((int)bus.getAcFare()));
            html = html.replace("{{NON_AC_FARE}}", String.valueOf((int)bus.getNonAcFare()));
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));

            StringBuilder seatGrid = new StringBuilder();
            seatGrid.append("<div class=\"bus-coach\">\n");
            seatGrid.append("  <div class=\"coach-driver-area\">\n");
            seatGrid.append("    <div class=\"driver-cabin\">Steering Wheel [Driver]</div>\n");
            seatGrid.append("    <div class=\"coach-door\">Entry Door &rarr;</div>\n");
            seatGrid.append("  </div>\n");
            seatGrid.append("  <div class=\"seat-matrix\">\n");

            for (int row = 0; row < 5; row++) {
                seatGrid.append("    <div class=\"seat-row\">\n");
                seatGrid.append("      <div class=\"seat-pair left-side\">\n");
                for (int c = 1; c <= 2; c++) {
                    int seatNo = (row * 4) + c;
                    appendSeatHtml(seatGrid, bus, seatNo);
                }
                seatGrid.append("      </div>\n");
                seatGrid.append("      <div class=\"bus-aisle\"><span>Aisle</span></div>\n");
                seatGrid.append("      <div class=\"seat-pair right-side\">\n");
                for (int c = 3; c <= 4; c++) {
                    int seatNo = (row * 4) + c;
                    appendSeatHtml(seatGrid, bus, seatNo);
                }
                seatGrid.append("      </div>\n");
                seatGrid.append("    </div>\n");
            }

            seatGrid.append("  </div>\n");
            seatGrid.append("</div>\n");

            html = html.replace("{{SEAT_GRID}}", seatGrid.toString());

            String alert = "";
            if (params.containsKey("error")) {
                alert = "<div class=\"alert alert-danger\">" + escapeHtml(params.get("error")) + "</div>";
            }
            html = html.replace("{{ALERT}}", alert);

            sendResponse(exchange, 200, "text/html", html);
        }

        private void appendSeatHtml(StringBuilder sb, Bus bus, int seatNo) {
            boolean isAvailable = bus.isSeatAvailable(seatNo);
            String seatLabel = String.format("%02d", seatNo);

            if (isAvailable) {
                sb.append("        <a href=\"/booking?busNumber=").append(bus.getBusNumber())
                  .append("&seatNumber=").append(seatNo)
                  .append("\" class=\"seat seat-available\" title=\"Seat ").append(seatLabel)
                  .append(" - Available. Click to Select\">")
                  .append(seatLabel).append("</a>\n");
            } else {
                sb.append("        <span class=\"seat seat-booked\" title=\"Seat ").append(seatLabel)
                  .append(" - Already Booked\">")
                  .append(seatLabel).append("</span>\n");
            }
        }
    }

    private class BookingHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            User user = getSessionUser(exchange);
            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String busNumStr = params.get("busNumber");
            String seatNumStr = params.get("seatNumber");

            if (busNumStr == null || seatNumStr == null) {
                sendRedirect(exchange, "/");
                return;
            }

            int busNumber;
            int seatNumber;
            try {
                busNumber = Integer.parseInt(busNumStr.trim());
                seatNumber = Integer.parseInt(seatNumStr.trim());
            } catch (NumberFormatException e) {
                sendErrorPage(exchange, 400, "Invalid Parameters", "Bus number and seat number must be integers.");
                return;
            }

            Bus bus = fleetManager.findBus(busNumber);
            if (bus == null) {
                sendErrorPage(exchange, 404, "Bus Not Found", "Bus #" + busNumber + " was not found.");
                return;
            }

            if (seatNumber < 1 || seatNumber > bus.getTotalSeats()) {
                sendErrorPage(exchange, 400, "Invalid Seat", "Seat " + seatNumber + " is outside valid range (1-" + bus.getTotalSeats() + ").");
                return;
            }

            if (!bus.isSeatAvailable(seatNumber)) {
                sendRedirect(exchange, "/seats?busNumber=" + busNumber + "&error=Seat+" + seatNumber + "+is+already+booked.+Please+select+another+seat.");
                return;
            }

            String html = readTemplate("booking.html");
            html = html.replace("{{BUS_NUMBER}}", String.valueOf(bus.getBusNumber()));
            html = html.replace("{{BUS_TYPE}}", escapeHtml(bus.getBusType()));
            html = html.replace("{{SEAT_CLASS}}", escapeHtml(bus.getSeatClass()));
            html = html.replace("{{SEAT_CLASS_LOWER}}", bus.getSeatClass().toLowerCase().replace("-", ""));
            html = html.replace("{{FARE}}", String.valueOf((int)bus.getFare()));
            html = html.replace("{{SOURCE}}", escapeHtml(bus.getSource()));
            html = html.replace("{{DESTINATION}}", escapeHtml(bus.getDestination()));
            html = html.replace("{{DEPARTURE}}", escapeHtml(bus.getDepartureTime()));
            html = html.replace("{{SEAT_NUMBER}}", String.format("%02d", seatNumber));
            html = html.replace("{{SEAT_NUMBER_RAW}}", String.valueOf(seatNumber));
            html = html.replace("{{AC_FARE}}", String.valueOf((int)bus.getAcFare()));
            html = html.replace("{{NON_AC_FARE}}", String.valueOf((int)bus.getNonAcFare()));
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));

            if (user != null) {
                html = html.replace("{{PREFILL_NAME}}", escapeHtml(user.getFullName()));
                html = html.replace("{{PREFILL_PHONE}}", escapeHtml(user.getPhone()));
            } else {
                html = html.replace("{{PREFILL_NAME}}", "");
                html = html.replace("{{PREFILL_PHONE}}", "");
            }

            String alert = "";
            if (params.containsKey("error")) {
                alert = "<div class=\"alert alert-danger\">" + escapeHtml(params.get("error")) + "</div>";
            }
            html = html.replace("{{ALERT}}", alert);

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class BookActionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            User user = getSessionUser(exchange);
            Map<String, String> form = parseFormData(exchange.getRequestBody());

            String busNumStr = form.get("busNumber");
            String seatNumStr = form.get("seatNumber");
            String passengerName = form.get("passengerName");
            String passengerId = form.get("passengerId");
            String contact = form.get("contact");
            String seatClass = form.get("seatClass");

            if (busNumStr == null || seatNumStr == null || passengerName == null || passengerId == null || contact == null ||
                passengerName.trim().isEmpty() || passengerId.trim().isEmpty() || contact.trim().isEmpty()) {
                sendErrorPage(exchange, 400, "Validation Error", "All fields are required. Please fill in all passenger details.");
                return;
            }

            int busNumber;
            int seatNumber;
            try {
                busNumber = Integer.parseInt(busNumStr.trim());
                seatNumber = Integer.parseInt(seatNumStr.trim());
            } catch (NumberFormatException e) {
                sendErrorPage(exchange, 400, "Invalid Input", "Bus number and seat number must be integers.");
                return;
            }

            Bus bus = fleetManager.findBus(busNumber);
            if (bus == null) {
                sendErrorPage(exchange, 404, "Bus Not Found", "Bus #" + busNumber + " was not found.");
                return;
            }

            if (seatClass == null || seatClass.trim().isEmpty()) {
                seatClass = bus.getSeatClass();
            } else {
                seatClass = seatClass.trim();
            }

            String username = (user != null) ? user.getUsername() : "guest";
            Ticket ticket;
            try {
                Passenger passenger = new Passenger(passengerName, passengerId, contact);
                ticket = fleetManager.bookTicket(busNumber, seatNumber, seatClass, passenger, username);
            } catch (IllegalArgumentException e) {
                sendErrorPage(exchange, 400, "Booking Failed", e.getMessage());
                return;
            } catch (IllegalStateException e) {
                sendErrorPage(exchange, 409, "Seat Already Booked", e.getMessage());
                return;
            }

            String html = readTemplate("ticket.html");
            html = injectTicketData(html, ticket);
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));
            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class ViewTicketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            Map<String, String> params;
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                params = parseFormData(exchange.getRequestBody());
            } else {
                params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            }

            String ticketNumStr = params.get("ticketNumber");
            String html = readTemplate("view-ticket.html");
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));

            if (ticketNumStr == null || ticketNumStr.trim().isEmpty()) {
                html = html.replace("{{TICKET_QUERY}}", "");
                html = html.replace("{{TICKET_RESULT}}", "");
                html = html.replace("{{ALERT}}", "");
                sendResponse(exchange, 200, "text/html", html);
                return;
            }

            html = html.replace("{{TICKET_QUERY}}", escapeHtml(ticketNumStr.trim()));

            int ticketNumber;
            try {
                ticketNumber = Integer.parseInt(ticketNumStr.trim());
            } catch (NumberFormatException e) {
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">Please enter a valid numeric ticket number.</div>");
                html = html.replace("{{TICKET_RESULT}}", "");
                sendResponse(exchange, 200, "text/html", html);
                return;
            }

            Ticket ticket = fleetManager.findTicket(ticketNumber);
            if (ticket == null) {
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">Ticket #" + ticketNumber + " not found.</div>");
                html = html.replace("{{TICKET_RESULT}}", "");
            } else {
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-success\">Ticket found successfully!</div>");
                html = html.replace("{{TICKET_RESULT}}", renderTicketCard(ticket));
            }

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class CancelTicketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User user = getSessionUser(exchange);
            Map<String, String> params;
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                params = parseFormData(exchange.getRequestBody());
            } else {
                params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            }

            String ticketNumStr = params.get("ticketNumber");
            String html = readTemplate("cancel-ticket.html");
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));

            if (ticketNumStr == null || ticketNumStr.trim().isEmpty()) {
                html = html.replace("{{TICKET_QUERY}}", "");
                html = html.replace("{{CANCELLATION_RESULT}}", "");
                html = html.replace("{{ALERT}}", "");
                sendResponse(exchange, 200, "text/html", html);
                return;
            }

            html = html.replace("{{TICKET_QUERY}}", escapeHtml(ticketNumStr.trim()));

            int ticketNumber;
            try {
                ticketNumber = Integer.parseInt(ticketNumStr.trim());
            } catch (NumberFormatException e) {
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">Please enter a valid numeric ticket number.</div>");
                html = html.replace("{{CANCELLATION_RESULT}}", "");
                sendResponse(exchange, 200, "text/html", html);
                return;
            }

            Ticket cancelledTicket = fleetManager.cancelTicket(ticketNumber);
            if (cancelledTicket == null) {
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">Ticket #" + ticketNumber + " not found or already cancelled.</div>");
                html = html.replace("{{CANCELLATION_RESULT}}", "");
            } else {
                String successMsg = "<div class=\"alert alert-success\">Ticket #" + ticketNumber + 
                                    " has been successfully canceled! Seat " + 
                                    String.format("%02d", cancelledTicket.getSeatNumber()) + 
                                    " on Bus #" + cancelledTicket.getBus().getBusNumber() + 
                                    " is now available again for booking.</div>";
                
                String summary = "<div class=\"card confirmation-card\">\n" +
                                 "  <h3>Cancellation Receipt</h3>\n" +
                                 "  <p><strong>Ticket Number:</strong> #" + cancelledTicket.getTicketNumber() + "</p>\n" +
                                 "  <p><strong>Passenger:</strong> " + escapeHtml(cancelledTicket.getPassenger().getPassengerName()) + "</p>\n" +
                                 "  <p><strong>Bus:</strong> #" + cancelledTicket.getBus().getBusNumber() + " (" + 
                                 escapeHtml(cancelledTicket.getBus().getSource()) + " &rarr; " + escapeHtml(cancelledTicket.getBus().getDestination()) + ")</p>\n" +
                                 "  <p><strong>Seat Released:</strong> Seat " + String.format("%02d", cancelledTicket.getSeatNumber()) + "</p>\n" +
                                 "  <p><strong>Refund Amount:</strong> Rs." + (int)cancelledTicket.getFare() + "</p>\n" +
                                 "  <div style=\"margin-top: 15px;\">\n" +
                                 "    <a href=\"/\" class=\"btn btn-primary\">Return to Home</a>\n" +
                                 "  </div>\n" +
                                 "</div>";

                html = html.replace("{{ALERT}}", successMsg);
                html = html.replace("{{CANCELLATION_RESULT}}", summary);
            }

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class LoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User currentUser = getSessionUser(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                if (currentUser != null) {
                    sendRedirect(exchange, currentUser.isAdmin() ? "/admin" : "/my-bookings");
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String html = readTemplate("login.html");
                html = html.replace("{{AUTH_NAV}}", renderAuthNav(null));

                String alert = "";
                if (params.containsKey("error")) {
                    alert = "<div class=\"alert alert-danger\">" + escapeHtml(params.get("error")) + "</div>";
                } else if (params.containsKey("msg")) {
                    alert = "<div class=\"alert alert-success\">" + escapeHtml(params.get("msg")) + "</div>";
                }
                html = html.replace("{{ALERT}}", alert);

                sendResponse(exchange, 200, "text/html", html);
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, String> form = parseFormData(exchange.getRequestBody());
                String username = form.get("username");
                String password = form.get("password");

                if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                    renderLoginPageWithError(exchange, "Please enter both username and password.");
                    return;
                }

                User user = fleetManager.getDatabaseManager().authenticate(username.trim(), password);
                if (user == null) {
                    renderLoginPageWithError(exchange, "Invalid username or password. Please try again.");
                    return;
                }

                createSession(exchange, user);
                if (user.isAdmin()) {
                    sendRedirect(exchange, "/admin");
                } else {
                    sendRedirect(exchange, "/my-bookings");
                }
                return;
            }

            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
        }

        private void renderLoginPageWithError(HttpExchange exchange, String errorMsg) throws IOException {
            String html = readTemplate("login.html");
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(null));
            html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">" + escapeHtml(errorMsg) + "</div>");
            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class RegisterHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            User currentUser = getSessionUser(exchange);
            if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                if (currentUser != null) {
                    sendRedirect(exchange, "/");
                    return;
                }

                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
                String html = readTemplate("register.html");
                html = html.replace("{{AUTH_NAV}}", renderAuthNav(null));

                String alert = "";
                if (params.containsKey("error")) {
                    alert = "<div class=\"alert alert-danger\">" + escapeHtml(params.get("error")) + "</div>";
                }
                html = html.replace("{{ALERT}}", alert);

                sendResponse(exchange, 200, "text/html", html);
                return;
            }

            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                Map<String, String> form = parseFormData(exchange.getRequestBody());
                String fullName = form.get("fullName");
                String username = form.get("username");
                String phone = form.get("phone");
                String password = form.get("password");
                String confirmPassword = form.get("confirmPassword");

                if (fullName == null || username == null || phone == null || password == null ||
                    fullName.trim().isEmpty() || username.trim().isEmpty() || phone.trim().isEmpty() || password.trim().isEmpty()) {
                    renderRegisterPageWithError(exchange, "All fields are required.");
                    return;
                }

                if (password.length() < 4) {
                    renderRegisterPageWithError(exchange, "Password must be at least 4 characters long.");
                    return;
                }

                if (confirmPassword != null && !password.equals(confirmPassword)) {
                    renderRegisterPageWithError(exchange, "Passwords do not match.");
                    return;
                }

                try {
                    User newUser = fleetManager.getDatabaseManager().registerUser(username.trim(), password, fullName.trim(), phone.trim());
                    createSession(exchange, newUser);
                    sendRedirect(exchange, "/my-bookings?msg=Welcome+to+BusGo!+Your+account+has+been+created+successfully.");
                } catch (IllegalArgumentException e) {
                    renderRegisterPageWithError(exchange, e.getMessage());
                }
                return;
            }

            sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
        }

        private void renderRegisterPageWithError(HttpExchange exchange, String errorMsg) throws IOException {
            String html = readTemplate("register.html");
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(null));
            html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">" + escapeHtml(errorMsg) + "</div>");
            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class LogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            clearSession(exchange);
            sendRedirect(exchange, "/login?msg=You+have+been+logged+out+successfully.");
        }
    }

    private class MyBookingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null) {
                sendRedirect(exchange, "/login?error=Please+log+in+to+view+your+bookings.");
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String html = readTemplate("my-bookings.html");
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));
            html = html.replace("{{USER_NAME}}", escapeHtml(user.getFullName()));

            String alert = "";
            if (params.containsKey("msg")) {
                alert = "<div class=\"alert alert-success\">" + escapeHtml(params.get("msg")) + "</div>";
            } else if (params.containsKey("error")) {
                alert = "<div class=\"alert alert-danger\">" + escapeHtml(params.get("error")) + "</div>";
            }
            html = html.replace("{{ALERT}}", alert);

            List<DatabaseManager.TicketRecord> userTickets = fleetManager.getDatabaseManager().getUserTickets(user.getUsername());
            if (userTickets.isEmpty()) {
                String emptyHtml = "<div class=\"empty-state\" style=\"margin-top: 20px;\">\n" +
                                   "  <h3>No Bookings Found</h3>\n" +
                                   "  <p>You haven't made any bus reservations yet. Choose a route and book your first journey!</p>\n" +
                                   "  <a href=\"/\" class=\"btn btn-primary\" style=\"margin-top: 15px;\">Search &amp; Book Buses</a>\n" +
                                   "</div>";
                html = html.replace("{{BOOKINGS_TABLE}}", emptyHtml);
            } else {
                StringBuilder table = new StringBuilder();
                table.append("<table class=\"data-table\">\n");
                table.append("  <thead>\n");
                table.append("    <tr>\n");
                table.append("      <th>Ticket #</th>\n");
                table.append("      <th>Bus &amp; Route</th>\n");
                table.append("      <th>Passenger</th>\n");
                table.append("      <th>Seat</th>\n");
                table.append("      <th>Class</th>\n");
                table.append("      <th>Fare</th>\n");
                table.append("      <th>Booked At</th>\n");
                table.append("      <th>Actions</th>\n");
                table.append("    </tr>\n");
                table.append("  </thead>\n");
                table.append("  <tbody>\n");

                for (DatabaseManager.TicketRecord rec : userTickets) {
                    Bus bus = fleetManager.findBus(rec.busNumber);
                    String routeStr = (bus != null) ? (escapeHtml(bus.getSource()) + " &rarr; " + escapeHtml(bus.getDestination())) : "Bus #" + rec.busNumber;
                    String timeStr = (rec.bookingTime != null) ? rec.bookingTime.format(DISPLAY_DATE_FORMAT) : "N/A";

                    table.append("    <tr>\n");
                    table.append("      <td><strong>#").append(rec.ticketNumber).append("</strong></td>\n");
                    table.append("      <td>Bus #").append(rec.busNumber).append("<br><small class=\"text-muted\">").append(routeStr).append("</small></td>\n");
                    table.append("      <td>").append(escapeHtml(rec.passengerName)).append("<br><small class=\"text-muted\">").append(escapeHtml(rec.contact)).append("</small></td>\n");
                    table.append("      <td><span class=\"seat-highlight\">Seat ").append(String.format("%02d", rec.seatNumber)).append("</span></td>\n");
                    table.append("      <td><span class=\"class-tag\">").append(escapeHtml(rec.seatClass)).append("</span></td>\n");
                    table.append("      <td><strong>Rs.").append((int)rec.fare).append("</strong></td>\n");
                    table.append("      <td><small>").append(timeStr).append("</small></td>\n");
                    table.append("      <td>\n");
                    table.append("        <a href=\"/view-ticket?ticketNumber=").append(rec.ticketNumber).append("\" class=\"btn btn-sm btn-primary\" style=\"margin-right: 4px;\">View</a>\n");
                    table.append("        <a href=\"/cancel-ticket?ticketNumber=").append(rec.ticketNumber).append("\" class=\"btn btn-sm btn-danger\">Cancel</a>\n");
                    table.append("      </td>\n");
                    table.append("    </tr>\n");
                }

                table.append("  </tbody>\n");
                table.append("</table>\n");
                html = html.replace("{{BOOKINGS_TABLE}}", table.toString());
            }

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class AdminHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                sendResponse(exchange, 405, "text/plain", "Method Not Allowed");
                return;
            }

            User user = getSessionUser(exchange);
            if (user == null || !user.isAdmin()) {
                sendErrorPage(exchange, 403, "Access Denied", "Administrator privilege required. Please log in with an administrator account.");
                return;
            }

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String html = readTemplate("admin.html");
            html = html.replace("{{AUTH_NAV}}", renderAuthNav(user));

            String alert = "";
            if (params.containsKey("msg")) {
                alert = "<div class=\"alert alert-success\">" + escapeHtml(params.get("msg")) + "</div>";
            }
            html = html.replace("{{ALERT}}", alert);

            List<User> allUsers = fleetManager.getDatabaseManager().getAllUsers();
            List<DatabaseManager.TicketRecord> allTickets = fleetManager.getDatabaseManager().getAllTicketRecords();
            ArrayList<Bus> buses = fleetManager.getBuses();

            html = html.replace("{{TOTAL_USERS}}", String.valueOf(allUsers.size()));
            html = html.replace("{{TOTAL_BUSES}}", String.valueOf(buses.size()));
            html = html.replace("{{TOTAL_BOOKINGS}}", String.valueOf(allTickets.size()));

            // Render Users Table
            StringBuilder usersTable = new StringBuilder();
            usersTable.append("<div class=\"data-table-container\">\n");
            usersTable.append("<table class=\"data-table\">\n");
            usersTable.append("  <thead>\n");
            usersTable.append("    <tr>\n");
            usersTable.append("      <th>ID</th>\n");
            usersTable.append("      <th>Username</th>\n");
            usersTable.append("      <th>Full Name</th>\n");
            usersTable.append("      <th>Phone</th>\n");
            usersTable.append("      <th>Role</th>\n");
            usersTable.append("    </tr>\n");
            usersTable.append("  </thead>\n");
            usersTable.append("  <tbody>\n");
            for (User u : allUsers) {
                usersTable.append("    <tr>\n");
                usersTable.append("      <td>#").append(u.getId()).append("</td>\n");
                usersTable.append("      <td><strong>").append(escapeHtml(u.getUsername())).append("</strong></td>\n");
                usersTable.append("      <td>").append(escapeHtml(u.getFullName())).append("</td>\n");
                usersTable.append("      <td>").append(escapeHtml(u.getPhone())).append("</td>\n");
                String badgeClass = u.isAdmin() ? "role-admin" : "role-user";
                usersTable.append("      <td><span class=\"role-badge ").append(badgeClass).append("\">").append(escapeHtml(u.getRole())).append("</span></td>\n");
                usersTable.append("    </tr>\n");
            }
            usersTable.append("  </tbody>\n");
            usersTable.append("</table>\n");
            usersTable.append("</div>\n");
            html = html.replace("{{USERS_TABLE}}", usersTable.toString());

            // Render All Bookings Table
            StringBuilder bookingsTable = new StringBuilder();
            if (allTickets.isEmpty()) {
                bookingsTable.append("<div class=\"empty-state\"><p>No ticket reservations currently in database.</p></div>");
            } else {
                bookingsTable.append("<div class=\"data-table-container\">\n");
                bookingsTable.append("<table class=\"data-table\">\n");
                bookingsTable.append("  <thead>\n");
                bookingsTable.append("    <tr>\n");
                bookingsTable.append("      <th>Ticket #</th>\n");
                bookingsTable.append("      <th>Account</th>\n");
                bookingsTable.append("      <th>Passenger</th>\n");
                bookingsTable.append("      <th>Bus &amp; Route</th>\n");
                bookingsTable.append("      <th>Seat</th>\n");
                bookingsTable.append("      <th>Class</th>\n");
                bookingsTable.append("      <th>Fare</th>\n");
                bookingsTable.append("      <th>Booked At</th>\n");
                bookingsTable.append("      <th>Actions</th>\n");
                bookingsTable.append("    </tr>\n");
                bookingsTable.append("  </thead>\n");
                bookingsTable.append("  <tbody>\n");
                for (DatabaseManager.TicketRecord rec : allTickets) {
                    Bus bus = fleetManager.findBus(rec.busNumber);
                    String routeStr = (bus != null) ? (escapeHtml(bus.getSource()) + " &rarr; " + escapeHtml(bus.getDestination())) : "Bus #" + rec.busNumber;
                    String timeStr = (rec.bookingTime != null) ? rec.bookingTime.format(DISPLAY_DATE_FORMAT) : "N/A";

                    bookingsTable.append("    <tr>\n");
                    bookingsTable.append("      <td><strong>#").append(rec.ticketNumber).append("</strong></td>\n");
                    bookingsTable.append("      <td><span class=\"role-badge role-user\">").append(escapeHtml(rec.username)).append("</span></td>\n");
                    bookingsTable.append("      <td>").append(escapeHtml(rec.passengerName)).append("<br><small class=\"text-muted\">").append(escapeHtml(rec.contact)).append("</small></td>\n");
                    bookingsTable.append("      <td>Bus #").append(rec.busNumber).append("<br><small class=\"text-muted\">").append(routeStr).append("</small></td>\n");
                    bookingsTable.append("      <td><span class=\"seat-highlight\">Seat ").append(String.format("%02d", rec.seatNumber)).append("</span></td>\n");
                    bookingsTable.append("      <td><span class=\"class-tag\">").append(escapeHtml(rec.seatClass)).append("</span></td>\n");
                    bookingsTable.append("      <td><strong>Rs.").append((int)rec.fare).append("</strong></td>\n");
                    bookingsTable.append("      <td><small>").append(timeStr).append("</small></td>\n");
                    bookingsTable.append("      <td>\n");
                    bookingsTable.append("        <a href=\"/view-ticket?ticketNumber=").append(rec.ticketNumber).append("\" class=\"btn btn-sm btn-primary\" style=\"margin-right: 4px;\">View</a>\n");
                    bookingsTable.append("        <a href=\"/cancel-ticket?ticketNumber=").append(rec.ticketNumber).append("\" class=\"btn btn-sm btn-danger\">Cancel</a>\n");
                    bookingsTable.append("      </td>\n");
                    bookingsTable.append("    </tr>\n");
                }
                bookingsTable.append("  </tbody>\n");
                bookingsTable.append("</table>\n");
                bookingsTable.append("</div>\n");
            }
            html = html.replace("{{ALL_BOOKINGS_TABLE}}", bookingsTable.toString());

            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class StaticFileHandler implements HttpHandler {
        private final String fileName;
        private final String contentType;

        public StaticFileHandler(String fileName, String contentType) {
            this.fileName = fileName;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Path filePath = webDir.resolve(fileName);
            byte[] fileBytes;
            if (Files.exists(filePath)) {
                fileBytes = Files.readAllBytes(filePath);
            } else {
                try (InputStream is = getClass().getResourceAsStream("/web/" + fileName)) {
                    if (is == null) {
                        sendResponse(exchange, 404, "text/plain", "File Not Found: " + fileName);
                        return;
                    }
                    fileBytes = is.readAllBytes();
                }
            }
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
    }

    private String readTemplate(String filename) throws IOException {
        Path path = webDir.resolve(filename);
        if (Files.exists(path)) {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        try (InputStream is = getClass().getResourceAsStream("/web/" + filename)) {
            if (is != null) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        throw new IOException("Template file not found on disk or classpath: " + filename);
    }

    private String renderBusCards(ArrayList<Bus> buses) {
        if (buses == null || buses.isEmpty()) {
            return "<p class=\"empty-state\">No buses currently available.</p>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"bus-cards-grid\">\n");
        for (Bus bus : buses) {
            int availableSeats = bus.getAvailableSeatsCount();
            boolean isFull = availableSeats == 0;

            sb.append("  <div class=\"bus-card\">\n");
            sb.append("    <div class=\"bus-card-header\">\n");
            sb.append("      <div class=\"badge-group\">\n");
            sb.append("        <span class=\"bus-badge\">Bus #").append(bus.getBusNumber()).append("</span>\n");
            if (bus.isAc()) {
                sb.append("        <span class=\"coach-badge coach-ac\">").append(escapeHtml(bus.getBusType())).append("</span>\n");
            } else {
                sb.append("        <span class=\"coach-badge coach-non-ac\">").append(escapeHtml(bus.getBusType())).append("</span>\n");
            }
            sb.append("      </div>\n");
            sb.append("      <span class=\"bus-time\">&#128337; ").append(escapeHtml(bus.getDepartureTime())).append("</span>\n");
            sb.append("    </div>\n");
            sb.append("    <div class=\"bus-route\">\n");
            sb.append("      <span class=\"city\">").append(escapeHtml(bus.getSource())).append("</span>\n");
            sb.append("      <span class=\"route-arrow\">&rarr;</span>\n");
            sb.append("      <span class=\"city\">").append(escapeHtml(bus.getDestination())).append("</span>\n");
            sb.append("    </div>\n");
            sb.append("    <div class=\"bus-details\">\n");
            sb.append("      <div class=\"detail-item\">\n");
            sb.append("        <span class=\"label\">Available Seats:</span>\n");
            if (isFull) {
                sb.append("        <span class=\"value status-full\">Sold Out (0/").append(bus.getTotalSeats()).append(")</span>\n");
            } else {
                sb.append("        <span class=\"value status-available\">").append(availableSeats).append(" / ").append(bus.getTotalSeats()).append(" Left</span>\n");
            }
            sb.append("      </div>\n");
            sb.append("      <div class=\"detail-item\">\n");
            sb.append("        <span class=\"label\">Ticket Fare:</span>\n");
            sb.append("        <span class=\"value fare-info\">Rs.").append((int)bus.getFare())
              .append(" <small class=\"class-tag\">(").append(escapeHtml(bus.getSeatClass())).append(")</small></span>\n");
            sb.append("      </div>\n");
            sb.append("    </div>\n");
            sb.append("    <div class=\"bus-action\">\n");
            if (isFull) {
                sb.append("      <button class=\"btn btn-disabled\" disabled>Sold Out</button>\n");
            } else {
                sb.append("      <a href=\"/seats?busNumber=").append(bus.getBusNumber())
                  .append("\" class=\"btn btn-primary\">Select Seats</a>\n");
            }
            sb.append("    </div>\n");
            sb.append("  </div>\n");
        }
        sb.append("</div>\n");
        return sb.toString();
    }

    private String injectTicketData(String html, Ticket ticket) {
        Bus bus = ticket.getBus();
        Passenger passenger = ticket.getPassenger();

        html = html.replace("{{TICKET_NUMBER}}", String.valueOf(ticket.getTicketNumber()));
        html = html.replace("{{PASSENGER_NAME}}", escapeHtml(passenger.getPassengerName()));
        html = html.replace("{{PASSENGER_ID}}", escapeHtml(passenger.getPassengerId()));
        html = html.replace("{{CONTACT}}", escapeHtml(passenger.getContact()));
        html = html.replace("{{BUS_NUMBER}}", String.valueOf(bus.getBusNumber()));
        html = html.replace("{{BUS_TYPE}}", escapeHtml(bus.getBusType()));
        html = html.replace("{{ROUTE}}", escapeHtml(bus.getSource() + " \u2192 " + bus.getDestination()));
        html = html.replace("{{DEPARTURE}}", escapeHtml(bus.getDepartureTime()));
        html = html.replace("{{SEAT_NUMBER}}", String.format("%02d", ticket.getSeatNumber()));
        html = html.replace("{{SEAT_CLASS}}", escapeHtml(ticket.getSeatClass()));
        html = html.replace("{{FARE}}", String.valueOf((int)ticket.getFare()));
        html = html.replace("{{BOOKED_TIME}}", escapeHtml(ticket.getFormattedBookingTime()));
        html = html.replace("{{RAW_TICKET_TEXT}}", escapeHtml(ticket.toString()));
        return html;
    }

    private String renderTicketCard(Ticket ticket) {
        Bus bus = ticket.getBus();
        Passenger passenger = ticket.getPassenger();

        return "<div class=\"ticket-card\">\n" +
               "  <div class=\"ticket-header\">\n" +
               "    <h2>BUSGO BOARDING PASS</h2>\n" +
               "    <span class=\"ticket-tag\">CONFIRMED</span>\n" +
               "  </div>\n" +
               "  <div class=\"ticket-body\">\n" +
               "    <div class=\"ticket-row\">\n" +
               "      <div class=\"field\"><label>Ticket Number: </label><span>#" + ticket.getTicketNumber() + "</span></div>\n" +
               "      <div class=\"field\"><label>Booked At: </label><span>" + escapeHtml(ticket.getFormattedBookingTime()) + "</span></div>\n" +
               "    </div>\n" +
               "    <div class=\"ticket-row\">\n" +
               "      <div class=\"field\"><label>Passenger Name: </label><span>" + escapeHtml(passenger.getPassengerName()) + "</span></div>\n" +
               "      <div class=\"field\"><label>Govt ID Proof: </label><span>" + escapeHtml(passenger.getPassengerId()) + "</span></div>\n" +
               "      <div class=\"field\"><label>Contact: </label><span>" + escapeHtml(passenger.getContact()) + "</span></div>\n" +
               "    </div>\n" +
               "    <div class=\"ticket-row highlight-row\">\n" +
               "      <div class=\"field\"><label>Bus Number: </label><span>#" + bus.getBusNumber() + " (" + escapeHtml(bus.getBusType()) + ")</span></div>\n" +
               "      <div class=\"field\"><label>Route: </label><span>" + escapeHtml(bus.getSource()) + " &rarr; " + escapeHtml(bus.getDestination()) + "</span></div>\n" +
               "      <div class=\"field\"><label>Departure Time: </label><span>" + escapeHtml(bus.getDepartureTime()) + "</span></div>\n" +
               "    </div>\n" +
               "    <div class=\"ticket-row\">\n" +
               "      <div class=\"field\"><label>Seat Number: </label><span class=\"seat-highlight\">" + String.format("%02d", ticket.getSeatNumber()) + "</span></div>\n" +
               "      <div class=\"field\"><label>Class: </label><span>" + escapeHtml(ticket.getSeatClass()) + "</span></div>\n" +
               "      <div class=\"field\"><label>Total Fare: </label><span class=\"fare-highlight\">Rs." + (int)ticket.getFare() + "</span></div>\n" +
               "    </div>\n" +
               "  </div>\n" +
               "  <div class=\"ticket-footer\">\n" +
               "    <button onclick=\"window.print()\" class=\"btn btn-secondary\">Print Ticket</button>\n" +
               "    <a href=\"/cancel-ticket?ticketNumber=" + ticket.getTicketNumber() + "\" class=\"btn btn-danger\">Cancel Ticket</a>\n" +
               "  </div>\n" +
               "  <div class=\"ascii-ticket-box\">\n" +
               "    <pre>" + escapeHtml(ticket.toString()) + "</pre>\n" +
               "  </div>\n" +
               "</div>";
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.trim().isEmpty()) {
            return map;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            if (idx > 0) {
                String key = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                String val = (idx < pair.length() - 1) ? URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8) : "";
                map.put(key, val);
            } else if (idx == -1 && !pair.isEmpty()) {
                map.put(URLDecoder.decode(pair, StandardCharsets.UTF_8), "");
            }
        }
        return map;
    }

    private static Map<String, String> parseFormData(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        String formString = baos.toString(StandardCharsets.UTF_8);
        return parseQueryParams(formString);
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String contentType, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendRedirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private static void sendErrorPage(HttpExchange exchange, int statusCode, String title, String message) throws IOException {
        String html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\"><title>" + escapeHtml(title) + 
                      "</title><link rel=\"stylesheet\" href=\"/style.css\"></head><body>" +
                      "<header class=\"site-header\"><div class=\"header-container\"><a href=\"/\" class=\"brand-logo\">&#128652; BusGo</a></div></header>" +
                      "<div class=\"container\"><div class=\"alert alert-danger\" style=\"margin-top: 50px;\">" +
                      "<h2>" + escapeHtml(title) + "</h2><p>" + escapeHtml(message) + "</p>" +
                      "<a href=\"/\" class=\"btn btn-primary\" style=\"margin-top: 15px;\">Return to Home</a>" +
                      "</div></div></body></html>";
        sendResponse(exchange, statusCode, "text/html", html);
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&#39;");
    }
}
