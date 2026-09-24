/**
 * BusReservationSystem.java
 * Main entry point for the BusGo Bus Reservation System.
 * Sets up initial fleet data and starts the embedded HTTP Server.
 */
public class BusReservationSystem {
    public static void main(String[] args) {
        FleetManager fleetManager = new FleetManager();

        // Initialize fleet with Approach 1 (Each bus is dedicated AC or Non-AC with realistic varied timings):
        // Bus 101: Early morning AC Super Deluxe
        fleetManager.addBus(new Bus(101, "AC Super Deluxe", "AC", "Kannur", "Kochi", "06:15 AM", 20, 750.0));

        // Bus 102: Morning regional Non-AC Express
        fleetManager.addBus(new Bus(102, "Express Non-AC", "Non-AC", "Kannur", "Kozhikode", "08:45 AM", 20, 280.0));

        // Bus 103: Afternoon Volvo AC Multi-Axle
        fleetManager.addBus(new Bus(103, "Volvo AC Multi-Axle", "AC", "Kochi", "Trivandrum", "01:30 PM", 20, 620.0));

        // Bus 104: Evening commuter Non-AC Deluxe
        fleetManager.addBus(new Bus(104, "Deluxe Non-AC", "Non-AC", "Kozhikode", "Kochi", "05:20 PM", 20, 380.0));

        // Bus 105: Night long-distance Scania AC
        fleetManager.addBus(new Bus(105, "Scania AC Express", "AC", "Kannur", "Trivandrum", "09:45 PM", 20, 1150.0));

        // Bus 106: Late night Super Fast Non-AC
        fleetManager.addBus(new Bus(106, "Super Fast Non-AC", "Non-AC", "Kochi", "Kannur", "11:10 PM", 20, 540.0));

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
