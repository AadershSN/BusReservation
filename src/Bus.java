import java.util.Arrays;

/**
 * Bus.java
 * Represents an individual bus in the fleet with route, timing, fares,
 * and seat reservation status.
 */
public class Bus {
    private int busNumber;
    private String busType;   // e.g. "AC Super Deluxe", "Express Non-AC"
    private String seatClass; // "AC" or "Non-AC"
    private String source;
    private String destination;
    private String departureTime;
    private int totalSeats;
    private boolean[] seats; // false = available, true = booked
    private double fare;     // Coach ticket fare
    private double acFare;
    private double nonAcFare;

    /**
     * Constructs a Bus instance with a fixed bus type and seat class (Approach 1).
     * 
     * @param busNumber Unique bus number
     * @param busType Service name (e.g. "AC Super Deluxe", "Express Non-AC")
     * @param seatClass Seat category ("AC" or "Non-AC")
     * @param source Departure city/station
     * @param destination Arrival city/station
     * @param departureTime Scheduled departure time
     * @param totalSeats Total number of seats (typically 20)
     * @param fare Ticket fare for this coach
     */
    public Bus(int busNumber, String busType, String seatClass, String source, String destination, String departureTime, int totalSeats, double fare) {
        if (busNumber <= 0) {
            throw new IllegalArgumentException("Bus number must be positive.");
        }
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Source cannot be empty.");
        }
        if (destination == null || destination.trim().isEmpty()) {
            throw new IllegalArgumentException("Destination cannot be empty.");
        }
        if (departureTime == null || departureTime.trim().isEmpty()) {
            throw new IllegalArgumentException("Departure time cannot be empty.");
        }
        if (totalSeats <= 0) {
            throw new IllegalArgumentException("Total seats must be greater than 0.");
        }
        if (fare < 0) {
            throw new IllegalArgumentException("Fare cannot be negative.");
        }

        this.busNumber = busNumber;
        this.busType = (busType != null && !busType.trim().isEmpty()) ? busType.trim() : "Standard Express";
        this.seatClass = (seatClass != null && seatClass.trim().equalsIgnoreCase("AC")) ? "AC" : "Non-AC";
        this.source = source.trim();
        this.destination = destination.trim();
        this.departureTime = departureTime.trim();
        this.totalSeats = totalSeats;
        this.seats = new boolean[totalSeats]; // Initially all false (available)
        this.fare = fare;
        this.acFare = this.seatClass.equals("AC") ? fare : fare * 1.5;
        this.nonAcFare = this.seatClass.equals("Non-AC") ? fare : fare * 0.7;
    }

    /**
     * Legacy constructor for backward compatibility.
     */
    public Bus(int busNumber, String source, String destination, String departureTime, int totalSeats, double acFare, double nonAcFare) {
        this(busNumber, "Express AC Seater", "AC", source, destination, departureTime, totalSeats, acFare);
        this.acFare = acFare;
        this.nonAcFare = nonAcFare;
    }

    public int getBusNumber() {
        return busNumber;
    }

    public String getBusType() {
        return busType;
    }

    public String getSeatClass() {
        return seatClass;
    }

    public boolean isAc() {
        return "AC".equalsIgnoreCase(seatClass);
    }

    public double getFare() {
        return fare;
    }

    public String getSource() {
        return source;
    }

    public String getDestination() {
        return destination;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public double getAcFare() {
        return acFare;
    }

    public double getNonAcFare() {
        return nonAcFare;
    }

    /**
     * Calculates fare for this bus.
     * 
     * @param requestedClass Requested seat class (optional)
     * @return The bus's fixed fare
     */
    public double getFare(String requestedClass) {
        if (fare > 0) {
            return fare;
        }
        if (requestedClass != null && requestedClass.trim().equalsIgnoreCase("AC")) {
            return acFare;
        }
        return nonAcFare;
    }

    /**
     * Checks if a seat number is available for booking.
     * 
     * @param seatNumber Seat number (1-based index: 1 to totalSeats)
     * @return true if valid and free, false otherwise
     */
    public synchronized boolean isSeatAvailable(int seatNumber) {
        if (seatNumber < 1 || seatNumber > totalSeats) {
            return false;
        }
        return !seats[seatNumber - 1];
    }

    /**
     * Reserves the specified seat if currently available.
     * 
     * @param seatNumber Seat number (1 to totalSeats)
     * @return true if successfully reserved, false if already booked or invalid
     */
    public synchronized boolean reserveSeat(int seatNumber) {
        if (!isSeatAvailable(seatNumber)) {
            return false;
        }
        seats[seatNumber - 1] = true;
        return true;
    }

    /**
     * Cancels the reservation for the specified seat, making it available again.
     * 
     * @param seatNumber Seat number (1 to totalSeats)
     * @return true if successfully canceled, false if not booked or invalid
     */
    public synchronized boolean cancelSeat(int seatNumber) {
        if (seatNumber < 1 || seatNumber > totalSeats) {
            return false;
        }
        if (!seats[seatNumber - 1]) {
            return false; // Seat was not booked
        }
        seats[seatNumber - 1] = false;
        return true;
    }

    /**
     * Returns the number of currently available seats.
     */
    public synchronized int getAvailableSeatsCount() {
        int count = 0;
        for (boolean booked : seats) {
            if (!booked) {
                count++;
            }
        }
        return count;
    }

    /**
     * Returns a copy of the seats array.
     */
    public synchronized boolean[] getSeats() {
        return Arrays.copyOf(seats, seats.length);
    }

    @Override
    public String toString() {
        return "Bus #" + busNumber + " [" + source + " -> " + destination + 
               " at " + departureTime + "] (Available: " + getAvailableSeatsCount() + "/" + totalSeats + ")";
    }
}
