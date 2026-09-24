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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * BusServer.java
 * High-performance HTTP server built on Java's built-in com.sun.net.httpserver.HttpServer.
 * Routes HTTP requests to FleetManager and serves dynamically rendered HTML pages.
 */
public class BusServer {
    private final FleetManager fleetManager;
    private final int port;
    private HttpServer server;
    private final Path webDir;

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

        // Register Route Handlers
        server.createContext("/", new HomeHandler());
        server.createContext("/search", new SearchHandler());
        server.createContext("/seats", new SeatsHandler());
        server.createContext("/booking", new BookingHandler());
        server.createContext("/book", new BookActionHandler());
        server.createContext("/view-ticket", new ViewTicketHandler());
        server.createContext("/cancel-ticket", new CancelTicketHandler());
        server.createContext("/style.css", new StaticFileHandler("style.css", "text/css"));

        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
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

            String html = readTemplate("index.html");
            ArrayList<Bus> buses = fleetManager.getBuses();
            String busCardsHtml = renderBusCards(buses);

            html = html.replace("{{BUS_CARDS}}", busCardsHtml);
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

            Map<String, String> params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            String source = params.getOrDefault("source", "").trim();
            String destination = params.getOrDefault("destination", "").trim();

            ArrayList<Bus> matchingBuses = fleetManager.searchBus(source, destination);

            String html = readTemplate("search.html");
            html = html.replace("{{SEARCH_SOURCE}}", escapeHtml(source));
            html = html.replace("{{SEARCH_DESTINATION}}", escapeHtml(destination));

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

            Ticket ticket;
            try {
                Passenger passenger = new Passenger(passengerName, passengerId, contact);
                ticket = fleetManager.bookTicket(busNumber, seatNumber, seatClass, passenger);
            } catch (IllegalArgumentException e) {
                sendErrorPage(exchange, 400, "Booking Failed", e.getMessage());
                return;
            } catch (IllegalStateException e) {
                sendErrorPage(exchange, 409, "Seat Already Booked", e.getMessage());
                return;
            }

            String html = readTemplate("ticket.html");
            html = injectTicketData(html, ticket);
            sendResponse(exchange, 200, "text/html", html);
        }
    }

    private class ViewTicketHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> params;
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                params = parseFormData(exchange.getRequestBody());
            } else {
                params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            }

            String ticketNumStr = params.get("ticketNumber");
            String html = readTemplate("view-ticket.html");

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
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">Ticket not found.</div>");
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
            Map<String, String> params;
            if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                params = parseFormData(exchange.getRequestBody());
            } else {
                params = parseQueryParams(exchange.getRequestURI().getRawQuery());
            }

            String ticketNumStr = params.get("ticketNumber");
            String html = readTemplate("cancel-ticket.html");

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
                html = html.replace("{{ALERT}}", "<div class=\"alert alert-danger\">Ticket not found.</div>");
                html = html.replace("{{CANCELLATION_RESULT}}", "");
            } else {
                String successMsg = "<div class=\"alert alert-success\">Ticket #" + ticketNumber + 
                                    " has been successfully canceled! Seat " + 
                                    String.format("%02d", cancelledTicket.getSeatNumber()) + 
                                    " on Bus #" + cancelledTicket.getBus().getBusNumber() + 
                                    " is now available again for booking.</div>";
                
                String summary = "<div class=\"card confirmation-card\">\n" +
                                 "  <h3>Cancellation Receipt</h3>\n" +
                                 "  <p><strong>Ticket Number:</strong> " + cancelledTicket.getTicketNumber() + "</p>\n" +
                                 "  <p><strong>Passenger:</strong> " + escapeHtml(cancelledTicket.getPassenger().getPassengerName()) + "</p>\n" +
                                 "  <p><strong>Bus:</strong> #" + cancelledTicket.getBus().getBusNumber() + " (" + 
                                 escapeHtml(cancelledTicket.getBus().getSource()) + " &rarr; " + escapeHtml(cancelledTicket.getBus().getDestination()) + ")</p>\n" +
                                 "  <p><strong>Seat Released:</strong> " + String.format("%02d", cancelledTicket.getSeatNumber()) + "</p>\n" +
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
            if (!Files.exists(filePath)) {
                sendResponse(exchange, 404, "text/plain", "File Not Found");
                return;
            }
            byte[] fileBytes = Files.readAllBytes(filePath);
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
            exchange.sendResponseHeaders(200, fileBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
            }
        }
    }

    private String readTemplate(String filename) throws IOException {
        Path path = webDir.resolve(filename);
        if (!Files.exists(path)) {
            throw new IOException("Template file not found: " + path.toAbsolutePath());
        }
        return Files.readString(path, StandardCharsets.UTF_8);
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
                      "<header class=\"site-header\"><div class=\"header-container\"><a href=\"/\" class=\"brand\">BusGo</a></div></header>" +
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
