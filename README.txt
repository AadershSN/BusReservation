================================================================================
BUSGO - ONLINE BUS RESERVATION SYSTEM
College-Level Java Project (Pure Java + Semantic HTML5 / CSS3)
================================================================================

1. PROJECT OVERVIEW
--------------------------------------------------------------------------------
BusGo is a browser-based Bus Reservation System built using vanilla Java and 
semantic HTML5/CSS3. It does NOT use any third-party frameworks (no Spring Boot, 
no Maven, no Gradle, no Bootstrap, no Tailwind, no React, no Angular, no Node.js).
All network communication and web serving are handled directly by Java'\''s built-in 
HTTP Server (`com.sun.net.httpserver.HttpServer`).

Fleet details, seat matrices, and booking tickets are maintained in memory using 
standard Java collections (`ArrayList<Bus>`, `ArrayList<Ticket>`).

2. ARCHITECTURE
--------------------------------------------------------------------------------
   Browser (Chrome / Edge / Firefox)
         |
         | Standard HTTP GET / POST (HTML Forms)
         v
   Java Built-in HTTP Server (`com.sun.net.httpserver.HttpServer`)
         |
         | Route Dispatcher & Dynamic HTML Templating
         v
   Java Controller Handler (`BusServer.java`)
         |
         | Business Logic Method Calls
         v
   Fleet Manager (`FleetManager.java`)
         |
         +------------+------------+
         |            |            |
         v            v            v
     Bus.java    Ticket.java   Passenger.java
   (20 seats)    (ID: 1001+)

3. OBJECT-ORIENTED PROGRAMMING (OOP) CONCEPTS IMPLEMENTED
--------------------------------------------------------------------------------
- Encapsulation: All model classes (Bus, Passenger, Ticket, FleetManager) use 
  private fields accessed through public getter/setter methods.
- Object Composition: A Ticket object encapsulates references to a Bus object 
  and a Passenger object.
- State Management: Each Bus instance maintains the availability status of its 
  20 seats in a synchronized `boolean[]` array.
- In-Memory Persistence: FleetManager manages in-memory collections of buses 
  and tickets using Java ArrayLists.
- Defensive Validation: Server-side verification for seat boundaries (1-20), 
  duplicate seat bookings, valid ticket IDs, and sanitized HTML input.

4. SAMPLE BUS DATA PRE-CONFIGURED
--------------------------------------------------------------------------------
Bus #101: Kannur -> Kochi        | Departure: 08:00 AM | Total Seats: 20 | AC: Rs.800 | Non-AC: Rs.500
Bus #102: Kannur -> Kozhikode    | Departure: 10:30 AM | Total Seats: 20 | AC: Rs.600 | Non-AC: Rs.350
Bus #103: Kochi  -> Trivandrum   | Departure: 09:00 AM | Total Seats: 20 | AC: Rs.900 | Non-AC: Rs.600

5. PROJECT FOLDER STRUCTURE
--------------------------------------------------------------------------------
BusReservation/
|
+-- src/
|   +-- Bus.java                 (Bus entity, fares, and seat status)
|   +-- Passenger.java           (Passenger details and validation)
|   +-- Ticket.java              (Confirmed ticket and fare calculation)
|   +-- FleetManager.java        (Fleet operations, book/cancel logic)
|   +-- BusServer.java           (Built-in HttpServer & route handlers)
|   +-- BusReservationSystem.java (Main entry point with sample data)
|
+-- web/
|   +-- index.html               (Home page with search & all bus cards)
|   +-- search.html              (Search results page by route)
|   +-- seats.html               (Visual 20-seat coach matrix)
|   +-- booking.html             (Passenger details submission form)
|   +-- ticket.html              (Boarding pass confirmation & print)
|   +-- view-ticket.html         (Lookup ticket by ticket number)
|   +-- cancel-ticket.html       (Cancel ticket & release seat)
|   +-- style.css                (Custom responsive CSS styling)
|
+-- out/                         (Compiled Java .class bytecode)
|
+-- README.txt                   (Project documentation and guide)

6. COMPILATION INSTRUCTIONS
--------------------------------------------------------------------------------
Prerequisites: JDK 17 or newer installed and available in PATH.

Open Windows Command Prompt (CMD) or PowerShell in this project folder:

Step 1: Compile all Java source files
   javac -d out src/*.java

7. RUNNING THE APPLICATION
--------------------------------------------------------------------------------
Step 2: Run the application
   java -cp out BusReservationSystem

You should see the following console output:
   ==================================================
   BUSGO BUS RESERVATION SYSTEM
   Server started successfully!
   Open your browser:
   http://localhost:8080/
   Press Ctrl+C to stop the server.
   ==================================================

8. HOW TO ACCESS & TEST IN YOUR BROWSER
--------------------------------------------------------------------------------
1. Open your web browser and navigate to:
   http://localhost:8080/

2. Home Page:
   - View all available buses (Bus 101, 102, 103).
   - Enter "Kannur" in Source and "Kochi" in Destination and click "Search Buses".

3. Seat Selection:
   - Click "Select Seats" on Bus 101.
   - See the 20-seat coach layout ([01] to [20]).
   - Click any available green seat (e.g. Seat 07).

4. Passenger Booking:
   - Fill in:
     * Name: Aadersh
     * ID: CSE101
     * Contact: 9876543210
     * Seat Class: AC Class
   - Click "Confirm & Book Ticket".

5. Ticket Confirmation:
   - Ticket #1001 is generated with fare Rs.800.
   - Print or view the ASCII ticket box.

6. View Ticket:
   - Click "View Ticket" in navigation.
   - Enter "1001" to look up the confirmed reservation.

7. Cancel Ticket & Verify Seat Release:
   - Click "Cancel Ticket" in navigation.
   - Enter "1001" and click "Cancel Ticket".
   - Return to Home -> Bus 101 -> Select Seats:
     Seat 07 is now green and available again!

================================================================================
