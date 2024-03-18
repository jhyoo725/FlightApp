package flightapp;

import java.io.IOException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;


/**
 * Runs queries against a back-end database
 */
public class Query extends QueryAbstract {
  //
  // Canned queries
  //
  private static final String FLIGHT_CAPACITY_SQL = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement flightCapacityStmt;

  //
  // Instance variables
  //
  private static final String deleteTable = "DELETE FROM Reservations_jhyoo725; DELETE FROM Users_jhyoo725;";
  private PreparedStatement clear;

  private static final String user_password = "SELECT u1.password FROM Users_jhyoo725 AS u1 where u1.username = ?";
  private PreparedStatement login;
  private boolean check_login;
  private String curr_username;

  private static final String exist_user = "SELECT COUNT(*) AS num FROM Users_jhyoo725 WHERE username = ?";
  private PreparedStatement exist;

  private static final String create_user = "INSERT INTO Users_jhyoo725 VALUES (?, ?, ?)";
  private PreparedStatement create;

  private static final String search_flight = "SELECT TOP (?) fid, day_of_month,carrier_id,flight_num,origin_city,dest_city,actual_time,capacity,price "
                                              + "FROM Flights "
                                              + "WHERE origin_city = ? "
                                              + "AND dest_city = ? "
                                              + "AND day_of_month = ? "
                                              + "AND canceled = 0 "
                                              + "ORDER BY actual_time ASC";
  private PreparedStatement search;

  private static final String double_hop_search = "SELECT TOP (?) f1.fid AS f1_fid, f1.day_of_month AS f1_day_of_month, f1.carrier_id AS f1_carrier_id, "
                                                  + "f1.flight_num AS f1_flight_num, f1.origin_city AS f1_origin_city, f1.dest_city AS f1_overlap_city, "
                                                  + "f1.actual_time AS f1_actual_time, f1.capacity AS f1_capacity, f1.price AS f1_price, f2.fid AS f2_fid, "
                                                  + "f2.carrier_id AS f2_carrier_id, f2.flight_num AS f2_flight_num, f2.dest_city AS f2_dest_city, "
                                                  + "f2.actual_time AS f2_actual_time, f2.capacity AS f2_capacity, f2.price AS f2_price, (f1.actual_time + f2.actual_time) AS total_time "
                                                  + "FROM Flights AS f1, Flights AS f2 "
                                                  + "WHERE f1.dest_city = f2.origin_city "
                                                  + "AND f1.origin_city = ? "
                                                  + "AND f2.dest_city = ? "
                                                  + "AND f1.day_of_month = ? "
                                                  + "AND f2.day_of_month = ? "
                                                  + "AND f1.canceled = 0 "
                                                  + "AND f2.canceled = 0 "
                                                  + "ORDER BY total_time ASC";
  private PreparedStatement double_search;
  private List<Itinerary> list;

  private static final String exist_reserve = "SELECT COUNT(*) AS num FROM Reservations_jhyoo725 AS r, Flights AS f WHERE r.fid1 = f.fid AND r.users = ? AND f.day_of_month = ?";
  private PreparedStatement check_reserve;

  private static final String capacity_f1 = "SELECT COUNT(*) AS num FROM Reservations_jhyoo725 WHERE fid1 = ?";
  private PreparedStatement cap1;

  private static final String capacity_f2 = "SELECT COUNT(*) AS num FROM Reservations_jhyoo725 WHERE fid2 = ?";
  private PreparedStatement cap2;

  private static final String reserve_user = "INSERT INTO Reservations_jhyoo725 VALUES (?, 0, ?, ?, ?)"; 
  private PreparedStatement book;

  private static final String check_balance = "SELECT balance AS num FROM Users_jhyoo725 WHERE username = ?";
  private PreparedStatement balance;

  private static final String price_checker1 = "SELECT f.price AS price1, r.paid AS paid, r.fid2 AS f2_id FROM Reservations_jhyoo725 AS r, Flights AS f WHERE r.fid1 = f.fid AND r.rid = ?";
  private PreparedStatement price1;

  private static final String price_checker2 = "SELECT f.price AS price2 FROM Reservations_jhyoo725 AS r, Flights AS f WHERE r.fid2 = f.fid AND r.rid = ?";
  private PreparedStatement price2;

  private static final String update_balance = "UPDATE Users_jhyoo725 SET balance = ? WHERE username = ?";
  private PreparedStatement update1;

  private static final String update_paid = "UPDATE Reservations_jhyoo725 SET paid = 1 WHERE rid = ?";
  private PreparedStatement update2;

  private static final String reservation_ID = "SELECT COUNT(*) AS num FROM Reservations_jhyoo725";
  private PreparedStatement res_id;

  private static final String reserve_rows = "SELECT * FROM Reservations_jhyoo725 WHERE users = ?";
  private PreparedStatement reserve;

  private static final String find_flight = "SELECT f.day_of_month, f.carrier_id, f.flight_num, f.origin_city, f.dest_city, f.actual_time, f.capacity, f.price FROM Flights AS f WHERE f.fid = ?";
  private PreparedStatement find;

  protected Query() throws SQLException, IOException {
    prepareStatements();
    check_login = false;
  }

  /**
   * Clear the data in any custom tables created.
   * 
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
      clear.executeUpdate();

    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    flightCapacityStmt = conn.prepareStatement(FLIGHT_CAPACITY_SQL);

    clear = conn.prepareStatement(deleteTable);
    login = conn.prepareStatement(user_password);
    exist = conn.prepareStatement(exist_user);
    create = conn.prepareStatement(create_user);
    search = conn.prepareStatement(search_flight);
    double_search = conn.prepareStatement(double_hop_search);
    book = conn.prepareStatement(reserve_user);
    check_reserve = conn.prepareStatement(exist_reserve);
    cap1 = conn.prepareStatement(capacity_f1);
    cap2 = conn.prepareStatement(capacity_f2);
    balance = conn.prepareStatement(check_balance);
    price1 = conn.prepareStatement(price_checker1);
    price2 = conn.prepareStatement(price_checker2);
    update1 = conn.prepareStatement(update_balance);
    update2 = conn.prepareStatement(update_paid);
    res_id = conn.prepareStatement(reservation_ID);
    reserve = conn.prepareStatement(reserve_rows);
    find = conn.prepareStatement(find_flight);
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_login(String username, String password) {
    try {
      if (check_login) {
        return "User already logged in\n";
      }

      login.clearParameters();
      String name = username.toLowerCase();
      login.setString(1, name);

      ResultSet rs = login.executeQuery();
      rs.next();
      // check gives the password that is already in the database.
      byte[] db_password = rs.getBytes("password");
      rs.close();

      PasswordUtils pu = new PasswordUtils();
      boolean check = pu.plaintextMatchesSaltedHash(password, db_password);

      if (check) {
        check_login = true;
        curr_username = name;
        return "Logged in as " + name + "\n";
      }
      return "Login failed\n";
    } catch (SQLException e) {
      return "Login failed\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      if (initAmount < 0) {
        return "Failed to create user\n";
      }

      exist.clearParameters();
      String name = username.toLowerCase();
      exist.setString(1, name);

      ResultSet rs = exist.executeQuery();
      rs.next();

      int check = rs.getInt("num");
      if (check != 0) {
        return "Failed to create user\n";
      }
      rs.close();

      create.clearParameters();
      create.setString(1, name);

      PasswordUtils pu = new PasswordUtils();
      byte[] byte_password = pu.saltAndHashPassword(password);

      create.setBytes(2, byte_password);
      create.setInt(3, initAmount);

      create.executeUpdate();
      return "Created user " + name + "\n";

    } catch (SQLException e) {
      return "Failed to create user\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_search(String originCity, String destinationCity, 
                                   boolean directFlight, int dayOfMonth,
                                   int numberOfItineraries) {
    // WARNING: the below code is insecure (it's susceptible to SQL injection attacks) AND only
    // handles searches for direct flights.  We are providing it *only* as an example of how
    // to use JDBC; you are required to replace it with your own secure implementation.

    StringBuffer sb = new StringBuffer();
    list = new ArrayList<>();

    try {
      // one hop itineraries
      search.clearParameters();
      search.setInt(1, numberOfItineraries);
      search.setString(2, originCity);
      search.setString(3, destinationCity);
      search.setInt(4, dayOfMonth);

      ResultSet oneHopResults = search.executeQuery();

      int countDirect = 0;
      while (oneHopResults.next() && countDirect < numberOfItineraries) {
        int result_id = oneHopResults.getInt("fid");
        int result_dayOfMonth = oneHopResults.getInt("day_of_month");
        String result_carrierId = oneHopResults.getString("carrier_id");
        String result_flightNum = oneHopResults.getString("flight_num");
        String result_originCity = oneHopResults.getString("origin_city");
        String result_destCity = oneHopResults.getString("dest_city");
        int result_time = oneHopResults.getInt("actual_time");
        int result_capacity = oneHopResults.getInt("capacity");
        int result_price = oneHopResults.getInt("price");

        Flight direct = new Flight(result_id, result_dayOfMonth, result_carrierId, result_flightNum, result_originCity, result_destCity, result_time, result_capacity, result_price);
        Itinerary oneHop = new Itinerary(direct);
        list.add(oneHop);

        countDirect++;
      }
      oneHopResults.close();

      int check_doubleHop = numberOfItineraries - countDirect;
      if (!directFlight && check_doubleHop != 0) {
        double_search.clearParameters();
        double_search.setInt(1, check_doubleHop);
        double_search.setString(2, originCity);
        double_search.setString(3, destinationCity);
        double_search.setInt(4, dayOfMonth);
        double_search.setInt(5, dayOfMonth);

        ResultSet doubleHopResults = double_search.executeQuery();

        while (doubleHopResults.next() && check_doubleHop != 0) {
          int f1_id = doubleHopResults.getInt("f1_fid");
          int result_dayOfMonth = doubleHopResults.getInt("f1_day_of_month");
          String f1_carrierId = doubleHopResults.getString("f1_carrier_id");
          String f1_flightNum = doubleHopResults.getString("f1_flight_num");
          String f1_origin_city = doubleHopResults.getString("f1_origin_city");
          String result_overlapCity = doubleHopResults.getString("f1_overlap_city");
          int f1_time = doubleHopResults.getInt("f1_actual_time");
          int f1_capacity = doubleHopResults.getInt("f1_capacity");
          int f1_price = doubleHopResults.getInt("f1_price");
          Flight f1 = new Flight(f1_id, result_dayOfMonth, f1_carrierId, f1_flightNum, f1_origin_city, result_overlapCity, f1_time, f1_capacity, f1_price);

          int f2_id = doubleHopResults.getInt("f2_fid");
          String f2_carrierId = doubleHopResults.getString("f2_carrier_id");
          String f2_flightNum = doubleHopResults.getString("f2_flight_num");
          String f2_dest_city = doubleHopResults.getString("f2_dest_city");
          int f2_time = doubleHopResults.getInt("f2_actual_time");
          int f2_capacity = doubleHopResults.getInt("f2_capacity");
          int f2_price = doubleHopResults.getInt("f2_price");

          Flight f2 = new Flight(f2_id, result_dayOfMonth, f2_carrierId, f2_flightNum, result_overlapCity, f2_dest_city, f2_time, f2_capacity, f2_price);
          Itinerary doubleHop = new Itinerary(f1, f2);
          list.add(doubleHop);

          check_doubleHop--;
        }
        doubleHopResults.close();
      }

      if (list.isEmpty()) {
        return "No flights match your selection\n";
      }

      Collections.sort(list);
      for (int i = 0; i < list.size(); i++) {
        sb.append("Itinerary " + i + list.get(i).toString());
      }
    } catch (SQLException e) {
      e.printStackTrace();
      return "Failed to search\n";
    }
    return sb.toString();
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_book(int itineraryId) {
    if (!check_login) {
      return "Cannot book reservations, not logged in\n";
    }

    try {
      if (list == null || list.size() <= itineraryId) {
        return "No such itinerary " + itineraryId + "\n";
      }

      conn.setAutoCommit(false);
      Itinerary curr = list.get(itineraryId);
      int curr_date = curr.f1.dayOfMonth;
      check_reserve.clearParameters();
      check_reserve.setString(1, curr_username);
      check_reserve.setInt(2, curr_date);

      ResultSet check_flight = check_reserve.executeQuery();
      check_flight.next();
      if (check_flight.getInt("num") != 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "You cannot book two flights in the same day\n";
      }
      check_flight.close();

      cap1.clearParameters();
      cap1.setInt(1, curr.f1.fid);
      ResultSet capacityOne = cap1.executeQuery();
      capacityOne.next();
      int check_capacity1 = capacityOne.getInt("num") - curr.f1.capacity;
      capacityOne.close();

      int check_capacity2 = 1;
      if (curr.f2 != null) {
        cap2.clearParameters();
        cap2.setInt(1, curr.f2.fid);
        ResultSet capacityTwo = cap2.executeQuery();
        capacityTwo.next();
        check_capacity2 = capacityTwo.getInt("num") - curr.f2.capacity;
      }

      if (check_capacity1 == 0 || check_capacity2 == 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Booking failed\n";
      }

      ResultSet check_id = res_id.executeQuery();
      check_id.next();
      int id = check_id.getInt("num") + 1;
      check_id.close();

      book.clearParameters();
      book.setInt(1, id);
      book.setString(2, curr_username);
      book.setInt(3, curr.f1.fid);
      if (curr.f2 == null) {
        book.setNull(4, java.sql.Types.INTEGER);
      } else {
        book.setInt(4, curr.f2.fid);
      }

      book.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return "Booked flight(s), reservation ID: " + id + "\n";
    } catch (SQLException e) {
      try {
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_book(itineraryId);
        }
      } catch (SQLException except) {
        except.printStackTrace();
      }
      e.printStackTrace();
      return "Booking failed\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_pay(int reservationId) {
    if (!check_login) {
      return "Cannot pay, not logged in\n";
    }

    try{
      conn.setAutoCommit(false);
      ResultSet check_id = res_id.executeQuery();
      check_id.next();
      if (check_id.getInt("num") < reservationId) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "Cannot find unpaid reservation " + reservationId + " under user: " + curr_username + "\n";
      }
      check_id.close();

      price1.clearParameters();
      price1.setInt(1, reservationId);

      ResultSet p1 = price1.executeQuery();
      p1.next();
      int total_price = p1.getInt("price1");
      int check_paid = p1.getInt("paid");
      boolean check_f2 = (p1.getObject("f2_id") == null);
      p1.close();

      if (check_paid == 1) {
        conn.rollback();
        conn.setAutoCommit(true);
         return "Cannot find unpaid reservation " + reservationId + " under user: " + curr_username + "\n";
      }

      if (!check_f2) {
        price2.clearParameters();
        price2.setInt(1, reservationId);
        ResultSet p2 = price2.executeQuery();
        p2.next();
        total_price += p2.getInt("price2");
        p2.close();
      }

      balance.clearParameters();
      balance.setString(1, curr_username);

      ResultSet bc = balance.executeQuery();
      bc.next();
      int user_balance = bc.getInt("num");
      bc.close();

      if (user_balance < total_price) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "User has only " + user_balance + " in account but itinerary costs " + total_price +"\n";
      }

      int remain = user_balance - total_price;
      update1.clearParameters();
      update1.setInt(1, remain);
      update1.setString(2, curr_username);
      update1.executeUpdate();
      
      update2.clearParameters();
      update2.setInt(1, reservationId);
      update2.executeUpdate();
      conn.commit();
      conn.setAutoCommit(true);
      return "Paid reservation: " + reservationId + " remaining balance: " + remain + "\n";
    } catch (SQLException e) {
      try {
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_pay(reservationId);
        }
      } catch (SQLException except) {
        except.printStackTrace();
      }
      e.printStackTrace();
      return "Failed to pay for reservation " + reservationId + "\n";
    }
  }

  /* See QueryAbstract.java for javadoc */
  public String transaction_reservations() {
    if (!check_login) {
      return "Cannot view reservations, not logged in\n";
    }
    StringBuffer sb = new StringBuffer();

    try {
      conn.setAutoCommit(false);
      reserve.clearParameters();
      reserve.setString(1, curr_username);
      ResultSet r = reserve.executeQuery();

      while(r.next()) {
        //Reservation [reservation ID] paid: [true or false]:\n
        boolean result = (r.getInt("paid") == 1) ? true : false;
        sb.append("Reservation " + r.getInt("rid") + " paid: " + result + ":\n");

        int idOne = r.getInt("fid1");
        find.clearParameters();
        find.setInt(1, idOne);
        ResultSet f1 = find.executeQuery();
        f1.next();

        int f1_dayOfMonth = f1.getInt("day_of_month");
        String f1_carrierId = f1.getString("carrier_id");
        String f1_flightNum = f1.getString("flight_num");
        String f1_originCity = f1.getString("origin_city");
        String f1_destCity = f1.getString("dest_city");
        int f1_actualTime = f1.getInt("actual_time");
        int f1_capacity = f1.getInt("capacity");
        int f1_price = f1.getInt("price");
        f1.close();

        Flight fOne = new Flight(idOne, f1_dayOfMonth, f1_carrierId, f1_flightNum, f1_originCity, f1_destCity, f1_actualTime, f1_capacity, f1_price);
        sb.append(fOne.toString() + "\n");

        boolean check_f2 = (r.getObject("fid2") == null);
        if (!check_f2) {
          int idTwo = r.getInt("fid2");
          find.clearParameters();
          find.setInt(1, idTwo);
          ResultSet f2 = find.executeQuery();
          f2.next();

          int f2_dayOfMonth = f2.getInt("day_of_month");
          String f2_carrierId = f2.getString("carrier_id");
          String f2_flightNum = f2.getString("flight_num");
          String f2_originCity = f2.getString("origin_city");
          String f2_destCity = f2.getString("dest_city");
          int f2_actualTime = f2.getInt("actual_time");
          int f2_capacity = f2.getInt("capacity");
          int f2_price = f2.getInt("price");
          f2.close();

          Flight fTwo = new Flight(idTwo, f2_dayOfMonth, f2_carrierId, f2_flightNum, f2_originCity, f2_destCity, f2_actualTime, f2_capacity, f2_price);
          sb.append(fTwo.toString() + "\n");
        }
      }
      r.close();

      if (sb.length() == 0) {
        conn.rollback();
        conn.setAutoCommit(true);
        return "No reservations found\n";
      }
      conn.commit();
      conn.setAutoCommit(true);
      return sb.toString();
    } catch (SQLException e) {
      try {
        conn.rollback();
        conn.setAutoCommit(true);
        if (isDeadlock(e)) {
          return transaction_reservations();
        }
      } catch (SQLException except) {
        except.printStackTrace();
      }
      e.printStackTrace();
      return "Failed to retrieve reservations\n";
    }
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    flightCapacityStmt.clearParameters();
    flightCapacityStmt.setInt(1, fid);

    ResultSet results = flightCapacityStmt.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Utility function to determine whether an error was caused by a deadlock
   */
  private static boolean isDeadlock(SQLException e) {
    return e.getErrorCode() == 1205;
  }

  /**
   * A class to store information about a single flight
   *
   * TODO(hctang): move this into QueryAbstract
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    Flight(int id, int day, String carrier, String fnum, String origin, String dest, int tm,
           int cap, int pri) {
      fid = id;
      dayOfMonth = day;
      carrierId = carrier;
      flightNum = fnum;
      originCity = origin;
      destCity = dest;
      time = tm;
      capacity = cap;
      price = pri;
    }
    
    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
          + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
          + " Capacity: " + capacity + " Price: " + price;
    }
  }

  class Itinerary implements Comparable<Itinerary>{
    // sb.append("Itinerary " + itineraryID + ": 1 flight(s), " + result_time + " minutes\n"
    //               + "ID: " + result_id + " Day: " + result_dayOfMonth + " Carrier: " + result_carrierId
    //               + " Number: " + result_flightNum + " Origin: " + result_originCity + " Dest: "
    //               + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity
    //               + " Price: " + result_price + "\n");
    public Flight f1;
    public Flight f2;
    public int time;

    public Itinerary(Flight f) {
      this.f1 = f;
      this.f2 = null;
      this.time = f.time;
    }

    public Itinerary(Flight f1, Flight f2) {
      this.f1 = f1;
      this.f2 = f2;
      this.time = f1.time + f2.time;
    }

    @Override
    public String toString() {
      if (f2 == null) {
        return (": 1 flight(s), " + time + " minutes\n" + f1.toString() + "\n");
      } else {
        return (": 2 flight(s), " + time + " minutes\n" + f1.toString() + "\n" + f2.toString() + "\n");
      }
    }

    @Override
    public int compareTo(Itinerary other) {
      if (this.time < other.time) {
        return -1;
      } else if (other.time < this.time) {
        return 1;
      } else {
        if (this.f2 != null && other.f2 != null) {
          if (this.f1 == other.f1) {
            return this.f2.fid - other.f2.fid;
          }
        }
        return this.f1.fid - other.f1.fid;
      }
    }
  }
}
