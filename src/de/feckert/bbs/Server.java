package de.feckert.bbs;

import de.gansgruppe.formats.LST;
import de.feckert.bbs.security.Verifier;
import de.feckert.bbs.user.FileUploads;
import de.feckert.bbs.user.Posts;
import de.feckert.bbs.user.UserThread;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class Server implements Runnable {
	public Logger LOGGER;

	public  LST CONFIGURATION;
	private int port;

	private ServerSocket serverSocket;
	private Thread serverThread;

	public HashMap<UUID, UserThread> USERS = new HashMap<>();
	public ArrayList<String> NAMES = new ArrayList<>();
	public Posts POST_HANDLER;
	public FileUploads FILE_HANDLER;

	public static boolean SHUTDOWN = false;

	public Server() {
		this.LOGGER = Logger.create("JERAN:SERV");
	}

	public void init() {
		// LOAD CONFIGURATION
		CONFIGURATION = new LST();
		try {
			File configFile = new File(Util.getProgramDirectory(Main.class), "JERAN/config.lst");
			if (!configFile.exists()) {
				LOGGER.err("NO CONFIGURATION FILE PRESENT; CREATE ONE WITH THE REQUIRED PROPERTIES AND RELAUNCH!");
				System.exit(1);
			}
			CONFIGURATION.load(configFile.getAbsolutePath());
		} catch (IOException e) {
			e.printStackTrace();
		}

		LOGGER.info("DEBUG: SERVER CONFIGURATION");
		System.out.println(CONFIGURATION);

		// Initialize server fields
		this.port = CONFIGURATION.getInteger("SERVER", "PORT");
		this.serverThread = new Thread(this, "JERAN:SERV");
		this.POST_HANDLER = new Posts();
		this.FILE_HANDLER = new FileUploads();

		// Load Texts
		LOGGER.info("Loading static texts!");
		Util.loadTexts();

		Verifier.DB_PATH = new File(Util.getProgramDirectory(Main.class), CONFIGURATION.getString("SERVER", "VERIFIER_DB"));
		Verifier.loadVerifierDB();

		// Initialise PostSaveService
		Posts.POST_SAVE_DIRECTORY = new File(
				CONFIGURATION.getString("POST_HANDLER", "POST_SAVE_DIR")
						.replace("%server_dir%", Util.getProgramDirectory(Main.class)));
		Posts.setupPostSaveService(CONFIGURATION.getLong("POST_HANDLER", "SAVE_SERVICE_EXEC_PERIOD"),
				CONFIGURATION.getString("POST_HANDLER", "SAVE_SERVICE_TIME_UNIT"));
	}

	public void start() {
		this.serverThread.start();
		LOGGER.info("Server-Thread Started!");
	}

	public void saveStop() {
		LOGGER.info("Save-Stopping Server");
		// Disconnect Each User
		USERS.forEach((k, v) -> {
			try {
				v.disconnect();
			} catch (IOException | InterruptedException e) {
				e.printStackTrace();
			}
		});

		// Close Server Socket to force exit the thread
		try {
			this.serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		USERS.clear();
	}

	public void run() {
		try {
			this.serverSocket = new ServerSocket(port);
			LOGGER.infof("Opened ServerSocket under port %s!\n", port);

			while (!Server.SHUTDOWN) {
				try {
					// Prepare for next User
					UUID uuid = UUID.randomUUID();
					Socket socket = serverSocket.accept();
					USERS.put(uuid, new UserThread(uuid, socket)); // Register User
					LOGGER.infof("Accepted new Client [IP: %s]!\n", ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostString());
				} catch (SocketException e) {
					break;
				}
			}

			if (!this.serverSocket.isClosed())
				this.serverSocket.close();
			LOGGER.info("Server Stopped!");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param uuid UUID for the User
	 * @return A Username and UUID in the format "%username% (%uuid%)"
	 */
	public String getUserName(UUID uuid) {
		if (!USERS.containsKey(uuid)) return "unknown (" + uuid + ")";
		return USERS.get(uuid).getName() + "(" + uuid + ")";
	}

	public boolean isAlive() {
		return serverThread.isAlive();
	}
}
