package de.feckert.bbs;

import de.feckert.bbs.admin.RemoteAdminShell;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class Main {
	public static Logger LOGGER;
	public static final Server SERVER = new Server();
	public static boolean DEBUG;
	private static String[] ARGS;

	public static void main(String[] args) throws IOException, InterruptedException {
		System.out.println("BBS-SYS SERVER VERSION 0 (BETA); (C) 2022 FELIX ECKERT");
		System.out.println("https://github.com/FelixEcker/BBS-SYS\n\n");

		ARGS = args; // Save Args for Restarts

		if (args.length > 0) {
			DEBUG = args[0].matches("debug");
		} else {
			DEBUG = false;
		}

		// Pre-Init
		Logger.setup();
		File file = new File(
				Util.getProgramDirectory(Main.class), String.format("JERAN/logs/log_%s.log",
				Util.getDateTimeString().replaceAll("[_/: ]", "-"))
		);
		if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
		Logger.UNIVERSAL_LOGFILE_WRITER = new PrintWriter(new FileWriter(file), true);

		// Init
		LOGGER = Logger.create("JERAN:GNRL");
		LOGGER.info("Starting JERAN//1");
		LOGGER.info("Initialising Server...");
		SERVER.init();
		if (SERVER.CONFIGURATION.getBoolean("ADMIN", "REMOTE_SHELL"))
			RemoteAdminShell.init();


		LOGGER.info("Starting Sever...");
		SERVER.start();
		if (RemoteAdminShell.isInit())
			RemoteAdminShell.startRAS();

		while (SERVER.isAlive()) {
			Thread.sleep(1000);
		}

		LOGGER.info("BBS-SYS Stopped!");
	}

	// Used by unimplemented RAS
	public static void restart() {
		LOGGER.info("Restarting BBS-SYS!");
		SERVER.saveStop();
		SERVER.POST_HANDLER.reset();
		Util.STATIC_TEXTS.clear();
		try {
			Logger.reset();
			System.out.println();
			System.out.println();
			System.out.println("==============================================================");
			main(ARGS);
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}
