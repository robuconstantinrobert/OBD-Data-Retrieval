
package OBD14;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

import com.fazecast.jSerialComm.SerialPort;

/**
 * @author Laptop
 *
 */
public class ELM327DataLogger {
	private static final int timeout = 100;
	public static int rpm;
	public static int absBarPres;
	public static int absLoad;
	private static int fuelTankLevelInput;
	private static int engineOilTemp;
	private static int engineCoolantTemp;
	private static int calculatedEngineLoad;
	private static int boostPressureControl;
	private static double fuelRate;
	private static double O2SensorData;
	private static int intakeAirTemp;
	private static int odometer;
	private static int throttlePos;
	private static int timingAdvance;
	private static int vehicleSpeed;


	public static void main(String[] args) throws Exception {

		SerialPort serialPort = SerialPort.getCommPort("COM3");
		serialPort.setBaudRate(38400);
		serialPort.setParity(SerialPort.EVEN_PARITY);
		serialPort.setNumDataBits(8);
		serialPort.setNumStopBits(1);
		serialPort.openPort();
		serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, timeout, 0);
		InputStream inStream = serialPort.getInputStream();
		OutputStream outStream = serialPort.getOutputStream();
		sendCommand(outStream, "AT SP 0" + "\r");
		BufferedReader reader = new BufferedReader(new InputStreamReader(inStream), 16);
		String url = "jdbc:mysql://127.0.0.1:3306/ECU";
		String user = "root";
		String password = "root";
		Connection conn = DriverManager.getConnection(url, user, password);

		String[] commands = { "010C", "0133", "012F", "015C", "0105", "0104", "010B", "015E", "0134", "010F", 
				"0131", "0111", "010E", "010D" };
		for (int i = 0; i < 1000; i++) {

			for (String cmd : commands) {
				System.out.println(cmd);
				sendCommand(outStream, cmd + "\r");

				String response = reader.readLine();

				parseAndStoreResponse(response, conn);
			}
		}
	}

	private static void parseAndStoreResponse(String response, Connection conn) throws SQLException {
		if (response != null) {
			String[] values = response.split(" ");

			if (values.length >= 3 && values[0].equals("41")) {
				String cmd = values[1];

				if (cmd.equals("0C")) {
					rpm = ((Integer.parseInt(values[2], 16) * 256) + Integer.parseInt(values[3], 16)) / 4;

					storeDataInt(conn, rpm, "RPM", "rpm_table");
				} else if (cmd.equals("33")) {
					absBarPres = Integer.parseInt(values[2], 16);
					storeDataInt(conn, absBarPres, "AbsBarPressure", "abs_bar_press");
				} else if (cmd.equals("2F")) {
					fuelTankLevelInput = Integer.parseInt(values[2], 16) * 100 / 255;
					storeDataInt(conn, fuelTankLevelInput, "FuelLevel", "fuel_level_table");
				} else if (cmd.equals("5C")) {
					engineOilTemp = Integer.parseInt(values[2], 16) - 40;
					storeDataInt(conn, engineOilTemp, "OilTemp", "oil_temp_table");
				} else if (cmd.equals("05")) {
					engineCoolantTemp = Integer.parseInt(values[2], 16) - 40;
					storeDataInt(conn, engineCoolantTemp, "CoolantTemp", "coolant_temp_table");
				} else if (cmd.equals("04")) {
					calculatedEngineLoad = (int) ((100.0 / 255.0) * Integer.parseInt(values[2], 16));
					storeDataInt(conn, calculatedEngineLoad, "EngineLoad", "engine_load_table");
				} else if (cmd.equals("0B")) {
					boostPressureControl = Integer.parseInt(values[2], 16) - 125;
					storeDataInt(conn, boostPressureControl, "Boost", "boost_pressure_table");
				}  else if (cmd.equals("5E")) {
					fuelRate = ((Integer.parseInt(values[2], 16) * 256) + Integer.parseInt(values[3], 16)) / 20.0;
					storeDataDouble(conn, fuelRate, "FuelRate", "fuel_rate_table");
				}  else if (cmd.equals("34")) {
					O2SensorData = ((Integer.parseInt(values[2], 16) * 256) + Integer.parseInt(values[3], 16))
							/ 32768.0;
					storeDataDouble(conn, O2SensorData, "O2Sensor", "o2_sensor_table");
				}  else if (cmd.equals("0F")) {
					intakeAirTemp = Integer.parseInt(values[2], 16) - 40;
					storeDataInt(conn, intakeAirTemp, "IntakeTemp", "intake_temp_table");
				}  else if (cmd.equals("31")) {
					odometer = Integer.parseInt(values[2], 16) * 256 + Integer.parseInt(values[3], 16);
					storeDataDouble(conn, odometer, "Odometer", "odometer_table");
				} else if (cmd.equals("11")) {
					throttlePos = Integer.parseInt(values[2], 16) * 100 / 255;
					storeDataInt(conn, throttlePos, "ThrottlePos", "throttle_pos_table");
				} else if (cmd.equals("0E")) {
					timingAdvance = (Integer.parseInt(values[2], 16) - 128) / 2;
					storeDataInt(conn, timingAdvance, "TimingAdvance", "timing_advance_table");
				} else if (cmd.equals("0D")) {
					vehicleSpeed = Integer.parseInt(values[2], 16);
					storeDataInt(conn, vehicleSpeed, "Speed", "speed_table");
				}

			}
		}
	}

	private static void sendCommand(OutputStream outStream, String command) throws Exception {
		outStream.write(command.getBytes());
		Thread.sleep(timeout);
		outStream.flush();
	}
	
	private static void storeDataInt(Connection conn, int attributeValue, String columnName, String tableName) throws SQLException {
	    String query = "INSERT INTO " + tableName + " (" + columnName + ", timestamp) VALUES (?, NOW())";
	    try (PreparedStatement stmt = conn.prepareStatement(query)) {
	        stmt.setInt(1, attributeValue);
	        stmt.executeUpdate();
	    }
	}

	private static void storeDataDouble(Connection conn, double attributeValue, String columnName, String tableName) throws SQLException {
	    String query = "INSERT INTO " + tableName + " (" + columnName + ", timestamp) VALUES (?, NOW())";
	    try (PreparedStatement stmt = conn.prepareStatement(query)) {
	        stmt.setDouble(1, attributeValue);
	        stmt.executeUpdate();
	    }
	}

}