package de.feckert.bbs.admin;

import de.feckert.bbs.Logger;
import de.feckert.bbs.Main;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class RemoteAdminShell implements Runnable {
	private static RemoteAdminShell singleton;

	private Thread RAS_THREAD;
	private Logger RAS_LOGGER;

	private ServerSocket RAS_SERVER;
	private Socket       CURRENT_CLIENT;

	/*
	* Remote Admin Shell Commands:
	* kick <uuid>-<name>
	* kick <ip>
	* ban <ip>
	* remVerify <public-uuid>-<name>
	* remPost <postNumber>
	* broadcast <message>
	* shutdown
	* restart
	* */

	// Dont let anyone else instantiate a RAS
	private RemoteAdminShell() {
		this.RAS_LOGGER = Logger.create("JERAN:RASH");
	}

	public void start() {
		this.RAS_THREAD = new Thread(this,"JERAN:RASH");
		this.RAS_THREAD.start();
	}

	@Override
	public void run() {
		RAS_LOGGER.info("Started new Thread for RemoteAdminShell");

		try {
			while (Main.SERVER.isAlive()) {
				CURRENT_CLIENT = RAS_SERVER.accept();
			}
		} catch (IOException e) {
			RAS_LOGGER.err("IOEXCEPTION IN REMOTEADMINSHELL");
			e.printStackTrace();
		}
	}

	public static void init(){
		if (singleton != null) return;
		singleton = new RemoteAdminShell();

		// Load Credentials
	}

	public static boolean isInit() {
		return singleton == null;
	}

	public static void startRAS() {
		singleton.start();
	}
}
