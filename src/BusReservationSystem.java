/**
 * BusReservationSystem.java
 * Main entry point for the BusGo Bus Reservation System.
 * Sets up initial fleet data and starts the embedded HTTP Server.
 */
public class BusReservationSystem {
    public static void main(String[] args) {
        FleetManager fleetManager = new FleetManager();

        // Initialize sample buses as required:
        // Bus 101: Kannur -> Kochi, 08:00 AM, 20 seats, AC Rs.800, Non-AC Rs.500
        fleetManager.addBus(new Bus(101, "Kannur", "Kochi", "08:00 AM", 20, 800.0, 500.0));

        // Bus 102: Kannur -> Kozhikode, 10:30 AM, 20 seats, AC Rs.600, Non-AC Rs.350
        fleetManager.addBus(new Bus(102, "Kannur", "Kozhikode", "10:30 AM", 20, 600.0, 350.0));

        // Bus 103: Kochi -> Trivandrum, 09:00 AM, 20 seats, AC Rs.900, Non-AC Rs.600
        fleetManager.addBus(new Bus(103, "Kochi", "Trivandrum", "09:00 AM", 20, 900.0, 600.0));

        int port = 8080;
        try {
            BusServer server = new BusServer(fleetManager, port);
            server.start();

            System.out.println("==================================================");
            System.out.println("BUSGO BUS RESERVATION SYSTEM");
            System.out.println("Server started successfully!");
            System.out.println("Open your browser:");
            System.out.println("http://localhost:8080/");
            System.out.println("Press Ctrl+C to stop the server.");
            System.out.println("==================================================");
        } catch (Exception e) {
            System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}
