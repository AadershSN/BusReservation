import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * FleetManager.java
 * Central controller managing the fleet of buses and in-memory booking tickets.
 */
public class FleetManager {
    private final ArrayList<Bus> buses = new ArrayList<>();
    private final ArrayList<Ticket> tickets = new ArrayList<>();
    private int nextTicketNumber = 1001;

    /**
     * Adds a bus to the fleet.
     * 
     * @param bus The bus instance to add
     */
    public synchronized void addBus(Bus bus) {
        if (bus == null) {
            throw new IllegalArgumentException("Bus cannot be null.");
        }
        buses.add(bus);
    }

    /**
     * Retrieves all buses in the fleet.
     * 
     * @return A list copy of all registered buses
     */
    public synchronized ArrayList<Bus> getBuses() {
        return new ArrayList<>(buses);
    }

    /**
     * Retrieves all booked tickets.
     * 
     * @return A list copy of all active tickets
     */
    public synchronized ArrayList<Ticket> getTickets() {
        return new ArrayList<>(tickets);
    }

    /**
     * Finds a bus by its unique bus number.
     * 
     * @param busNumber The bus number to search for
     * @return The Bus object if found, null otherwise
     */
    public synchronized Bus findBus(int busNumber) {
        for (Bus b : buses) {
            if (b.getBusNumber() == busNumber) {
                return b;
            }
        }
        return null;
    }

    /**
     * Searches buses by source and destination.
     * Performs case-insensitive matching.
     * 
     * @param source Departure city (optional/partial match)
     * @param destination Arrival city (optional/partial match)
     * @return List of matching buses
     */
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

    /**
     * Books a seat on a bus and generates a new Ticket.
     * 
     * @param busNumber Bus number
     * @param seatNumber Seat number (1 to totalSeats)
     * @param seatClass "AC" or "Non-AC"
     * @param passenger Passenger details
     * @return The generated Ticket instance
     * @throws IllegalArgumentException if parameters are invalid or bus is not found
     * @throws IllegalStateException if the seat is already booked
     */
    public synchronized Ticket bookTicket(int busNumber, int seatNumber, String seatClass, Passenger passenger) {
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

        return ticket;
    }

    /**
     * Cancels an existing ticket by its ticket number.
     * Frees the assigned seat on the bus and removes the ticket from memory.
     * 
     * @param ticketNumber The ticket number to cancel
     * @return The cancelled Ticket if found and cancelled, null if ticket does not exist
     */
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
        return ticket;
    }

    /**
     * Looks up an active ticket by its ticket number.
     * 
     * @param ticketNumber Ticket number
     * @return Ticket instance or null if not found
     */
    public synchronized Ticket findTicket(int ticketNumber) {
        for (Ticket t : tickets) {
            if (t.getTicketNumber() == ticketNumber) {
                return t;
            }
        }
        return null;
    }
}
