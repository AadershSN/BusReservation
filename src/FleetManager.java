import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * FleetManager.java
 * Central controller managing the fleet of buses and persistent database bookings.
 */
public class FleetManager {
    private final ArrayList<Bus> buses = new ArrayList<>();
    private final ArrayList<Ticket> tickets = new ArrayList<>();
    private final DatabaseManager databaseManager;
    private int nextTicketNumber = 1001;

    public FleetManager() {
        this(new DatabaseManager());
    }

    public FleetManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager != null ? databaseManager : new DatabaseManager();
        loadDatabaseState();
    }

    private synchronized void loadDatabaseState() {
        // Load persistent buses if they exist in DB
        if (databaseManager.hasStoredBuses()) {
            List<Bus> storedBuses = databaseManager.loadBuses();
            buses.clear();
            buses.addAll(storedBuses);
        }

        // Restore tickets and nextTicketNumber from DB
        List<DatabaseManager.TicketRecord> storedTickets = databaseManager.getAllTicketRecords();
        tickets.clear();
        for (DatabaseManager.TicketRecord rec : storedTickets) {
            Bus bus = findBus(rec.busNumber);
            if (bus != null) {
                Passenger p = new Passenger(rec.passengerName, rec.govtId, rec.contact);
                Ticket t = new Ticket(rec.ticketNumber, bus, p, rec.seatNumber, rec.seatClass, rec.fare, rec.bookingTime);
                tickets.add(t);
            }
        }

        int maxNum = databaseManager.getMaxTicketNumber();
        this.nextTicketNumber = Math.max(1001, maxNum + 1);
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    /**
     * Adds a bus to the fleet and persists it.
     */
    public synchronized void addBus(Bus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("Bus cannot be null.");
        }
        // Avoid duplicate by busNumber if already loaded from DB
        for (int i = 0; i < buses.size(); i++) {
            if (buses.get(i).getBusNumber() == bus.getBusNumber()) {
                return; // already exists
            }
        }
        buses.add(bus);
        databaseManager.saveBuses(buses);
    }

    public synchronized ArrayList<Bus> getBuses() {
        return new ArrayList<>(buses);
    }

    public synchronized ArrayList<Ticket> getTickets() {
        return new ArrayList<>(tickets);
    }

    public synchronized Bus findBus(int busNumber) {
        for (Bus b : buses) {
            if (b.getBusNumber() == busNumber) {
                return b;
            }
        }
        return null;
    }

    public synchronized ArrayList<Bus> searchBus(String source, String destination) {
        ArrayList<Bus> results = new ArrayList<>();
        String srcQuery = (source != null) ? source.trim().toLowerCase() : "";
        String dstQuery = (destination != null) ? destination.trim().toLowerCase() : "";

        for (Bus b : buses) {
            boolean matchesSource = srcQuery.isEmpty() || b.getSource().toLowerCase().contains(srcQuery);
            boolean matchesDestination = dstQuery.isEmpty() || b.getDestination().toLowerCase().contains(dstQuery);

            if (matchesSource && matchesDestination) {
                results.add(b);
            }
        }
        return results;
    }

    public synchronized Ticket bookTicket(int busNumber, int seatNumber, String seatClass, Passenger passenger) {
        return bookTicket(busNumber, seatNumber, seatClass, passenger, "guest");
    }

    public synchronized Ticket bookTicket(int busNumber, int seatNumber, String seatClass, Passenger passenger, String username) {
        if (passenger == null) {
            throw new IllegalArgumentException("Passenger information is required.");
        }

        Bus bus = findBus(busNumber);
        if (bus == null) {
            throw new IllegalArgumentException("Bus #" + busNumber + " does not exist.");
        }

        if (seatNumber < 1 || seatNumber > bus.getTotalSeats()) {
            throw new IllegalArgumentException("Invalid seat number " + seatNumber + ". Valid seats: 1 to " + bus.getTotalSeats() + ".");
        }

        if (!bus.isSeatAvailable(seatNumber)) {
            throw new IllegalStateException("Seat " + seatNumber + " on Bus #" + busNumber + " is already booked.");
        }

        if (seatClass == null || seatClass.trim().isEmpty()) {
            seatClass = bus.getSeatClass();
        } else if (!seatClass.equalsIgnoreCase("AC") && !seatClass.equalsIgnoreCase("Non-AC")) {
            throw new IllegalArgumentException("Seat class must be either AC or Non-AC.");
        }
        seatClass = seatClass.equalsIgnoreCase("AC") ? "AC" : "Non-AC";

        // Reserve the seat on the bus
        boolean reserved = bus.reserveSeat(seatNumber);
        if (!reserved) {
            throw new IllegalStateException("Failed to reserve seat " + seatNumber + ". It may have just been booked.");
        }

        // Calculate fare using bus logic
        double fare = bus.getFare(seatClass);

        // Generate Ticket
        int assignedTicketNumber = nextTicketNumber++;
        Ticket ticket = new Ticket(assignedTicketNumber, bus, passenger, seatNumber, seatClass, fare, LocalDateTime.now());
        tickets.add(ticket);

        // Persist to Database permanently!
        databaseManager.saveTicket(ticket, username);
        databaseManager.saveBuses(buses);

        return ticket;
    }

    public synchronized Ticket cancelTicket(int ticketNumber) {
        Ticket ticket = findTicket(ticketNumber);
        if (ticket == null) {
            return null;
        }

        // Free the seat on the corresponding bus
        Bus bus = ticket.getBus();
        if (bus != null) {
            bus.cancelSeat(ticket.getSeatNumber());
        }

        tickets.remove(ticket);

        // Update Database permanently!
        databaseManager.deleteTicket(ticketNumber);
        databaseManager.saveBuses(buses);

        return ticket;
    }

    public synchronized Ticket findTicket(int ticketNumber) {
        for (Ticket t : tickets) {
            if (t.getTicketNumber() == ticketNumber) {
                return t;
            }
        }
        return null;
    }
}
