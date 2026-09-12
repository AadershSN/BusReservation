/**
 * Passenger.java
 * Represents a passenger in the Bus Reservation System.
 */
public class Passenger {
    private String passengerName;
    private String passengerId;
    private String contact;

    /**
     * Constructs a new Passenger.
     * 
     * @param passengerName Full name of the passenger
     * @param passengerId Unique identification / Government ID proof number (e.g. Aadhaar / Voter ID / Passport)
     * @param contact Phone number or contact details
     */
    public Passenger(String passengerName, String passengerId, String contact) {
        if (passengerName == null || passengerName.trim().isEmpty()) {
            throw new IllegalArgumentException("Passenger name cannot be empty.");
        }
        if (passengerId == null || passengerId.trim().isEmpty()) {
            throw new IllegalArgumentException("Passenger ID cannot be empty.");
        }
        if (contact == null || contact.trim().isEmpty()) {
            throw new IllegalArgumentException("Contact number cannot be empty.");
        }
        this.passengerName = passengerName.trim();
        this.passengerId = passengerId.trim();
        this.contact = contact.trim();
    }

    public String getPassengerName() {
        return passengerName;
    }

    public void setPassengerName(String passengerName) {
        this.passengerName = passengerName;
    }

    public String getPassengerId() {
        return passengerId;
    }

    public void setPassengerId(String passengerId) {
        this.passengerId = passengerId;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    @Override
    public String toString() {
        return "Passenger{" +
                "passengerName='" + passengerName + '\'' +
                ", passengerId='" + passengerId + '\'' +
                ", contact='" + contact + '\'' +
                '}';
    }
}
