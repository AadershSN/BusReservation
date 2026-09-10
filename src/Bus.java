import java.util.Arrays;

/**
 * Bus.java
 * Represents an individual bus in the fleet with route, timing, fares,
 * and seat reservation status.
 */
public class Bus {
    private int busNumber;
    private String source;
    private String destination;
    private String departureTime;
    private int totalSeats;
    private boolean[] seats; // false = available, true = booked
    private double acFare;
    private double nonAcFare;

    /**
     * Constructs a Bus instance.
     * 
     * @param busNumber Unique bus number
     * @param source Departure city/station
     * @param destination Arrival city/station
     * @param departureTime Scheduled departure time
     * @param totalSeats Total number of seats (typically 20)
     * @param acFare Ticket fare for AC class
     * @param nonAcFare Ticket fare for Non-AC class
     */
    public Bus(int busNumber, String source, String destination, String departureTime, int totalSeats, double acFare, double nonAcFare) {
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

        this.busNumber = busNumber;
        this.source = source.trim();
        this.destination = destination.trim();
        this.departureTime = departureTime.trim();
        this.totalSeats = totalSeats;
        this.seats = new boolean[totalSeats]; // Initially all false (available)
        this.acFare = acFare;
        this.nonAcFare = nonAcFare;
    }

    public int getBusNumber() {
        return busNumber;
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
     * Calculates fare based on the requested seat class.
     * 
     * @param seatClass "AC" or "Non-AC"
     * @return Corresponding fare
     */
    public double getFare(String seatClass) {
        if (seatClass != null && seatClass.trim().equalsIgnoreCase("AC")) {
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
