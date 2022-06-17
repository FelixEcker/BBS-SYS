package de.feckert.bbs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;

/**
 * Contains several Utility functions used for the JERAN-BBS SERVER.
 */
public class Util {
	/**
	 * Holds all STATIC_TEXTS loaded at server startup.
	 */
	public static final HashMap<String, String> STATIC_TEXTS = new HashMap<>();

	/**
	 * Gets the directory of the current program
	 *
	 * @param CLAZZ The Class to use to locate the program
	 * @return The Path to the program directory
	 */
	public static String getProgramDirectory(Class<?> CLAZZ) {
		try {
			return new File(CLAZZ
					.getProtectionDomain()
					.getCodeSource()
					.getLocation()
					.toURI()
					.getPath()).getParent();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

		return "\\/";
	}

	/**
	 * Loads the static jtxts from disk.
	 */
	public static void loadTexts() {
		try {
			File directory = new File(getProgramDirectory(Util.class), "jeran/texts");
			File[] textFiles = directory.listFiles();
			for (File textFile : textFiles) {
				if (textFile.getName().endsWith(".jtxt")) {
					String name = textFile.getName().replace(".jtxt", "");
					String content = new String(Files.readAllBytes(textFile.toPath()));

					STATIC_TEXTS.put(name, content);
					System.out.printf("	-> LOADED TEXT \"%s\" successfully! (%s byte(s))\n", name, textFile.length());
					Logger.UNIVERSAL_LOGFILE_WRITER.printf("	-> LOADED TEXT \"%s\" successfully! (%s byte(s))\n", name, textFile.length());
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	/**
	 * Creates and returns a date-time string with the format:
	 * yyyy/MM/dd HH:mm:ss
	 *
	 * @return A Date-Time string.
	 */
	public static String getDateTimeString() {
		DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
		LocalDateTime now = LocalDateTime.now();
		return dtf.format(now);
	}

	/**
	 * Requests input according to the JERAN COM-PROTOCOL.
	 *
	 * @param writer PrintWriter used for requesting input
	 * @param reader BufferedReader used for reading input
	 */
	public static String requestInput(PrintWriter writer, BufferedReader reader) {
		try {
			writer.write(0x00);
			writer.flush();
			char[] buffer = new char[200];
			int chars = 0;
			chars = reader.read(buffer, 0, 200);
			return new String(buffer, 0, chars).replace("\n", "").replace("\r", "");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * Pads a String to a specified length.
	 *
	 * @param in      The String to be padded
	 * @param length  The length to be padded to
	 * @param padding The Character to be padded with
	 * @return The Padded String
	 */
	public static String padToLength(String in, int length, char padding) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			sb.append(padding);
		}

		try {
			return sb.substring(in.length()) + in;
		} catch (StringIndexOutOfBoundsException e) {
			return sb + in;
		}
	}

	/**
	 * Calculates the amount of digits in an Integer
	 *
	 * @param number The Integer to find the digits of
	 * @return The amount of digits in the Integer
	 */
	public static int digitsInInt(int number) {
		if (number == 0) return 1;
		return (int) (Math.log10(number) + 1);
	}
}
