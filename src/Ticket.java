import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Ticket.java
 * Represents a confirmed bus booking ticket.
 */
public class Ticket {
    private int ticketNumber;
    private Bus bus;
    private Passenger passenger;
    private int seatNumber;
    private String seatClass;
    private double fare;
    private LocalDateTime bookingTime;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm a");

    /**
     * Constructs a Ticket instance.
     * 
     * @param ticketNumber Unique sequential ticket number starting at 1001
     * @param bus The reserved Bus instance
     * @param passenger The Passenger instance
     * @param seatNumber Reserved seat number (1 to totalSeats)
     * @param seatClass Seat category ("AC" or "Non-AC")
     * @param fare Final ticket price calculated by Bus.getFare()
     * @param bookingTime Date and time of booking
     */
    public Ticket(int ticketNumber, Bus bus, Passenger passenger, int seatNumber, String seatClass, double fare, LocalDateTime bookingTime) {
        if (ticketNumber <= 0) {
            throw new IllegalArgumentException("Ticket number must be positive.");
        }
        if (bus == null) {
            throw new IllegalArgumentException("Bus cannot be null.");
        }
        if (passenger == null) {
            throw new IllegalArgumentException("Passenger cannot be null.");
        }
        if (seatClass == null || (!seatClass.equalsIgnoreCase("AC") && !seatClass.equalsIgnoreCase("Non-AC"))) {
            throw new IllegalArgumentException("Seat class must be either AC or Non-AC.");
        }
        if (fare < 0) {
            throw new IllegalArgumentException("Fare cannot be negative.");
        }

        this.ticketNumber = ticketNumber;
        this.bus = bus;
        this.passenger = passenger;
        this.seatNumber = seatNumber;
        this.seatClass = seatClass.equalsIgnoreCase("AC") ? "AC" : "Non-AC";
        this.fare = fare;
        this.bookingTime = (bookingTime != null) ? bookingTime : LocalDateTime.now();
    }

    public int getTicketNumber() {
        return ticketNumber;
    }

    public Bus getBus() {
        return bus;
    }

    public Passenger getPassenger() {
        return passenger;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public String getSeatClass() {
        return seatClass;
    }

    public double getFare() {
        return fare;
    }

    public LocalDateTime getBookingTime() {
        return bookingTime;
    }

    public String getFormattedBookingTime() {
        return bookingTime.format(FORMATTER);
    }

    @Override
    public String toString() {
        return "================================================\n" +
               "BUSGO TICKET\n" +
               "Ticket Number : " + ticketNumber + "\n" +
               "Passenger     : " + passenger.getPassengerName() + "\n" +
               "Govt ID Proof : " + passenger.getPassengerId() + "\n" +
               "Contact       : " + passenger.getContact() + "\n" +
               "Bus Number    : " + bus.getBusNumber() + "\n" +
               "Route         : " + bus.getSource() + " -> " + bus.getDestination() + "\n" +
               "Departure     : " + bus.getDepartureTime() + "\n" +
               String.format("Seat Number   : %02d\n", seatNumber) +
               "Seat Class    : " + seatClass + "\n" +
               "Fare          : Rs." + (int)fare + "\n" +
               "Booked At     : " + getFormattedBookingTime() + "\n" +
               "================================================";
    }
}
