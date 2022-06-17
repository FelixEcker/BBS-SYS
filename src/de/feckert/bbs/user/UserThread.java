package de.feckert.bbs.user;

import java.util.Base64;

import de.feckert.bbs.Main;
import de.feckert.bbs.Server;
import de.feckert.bbs.Util;
import de.feckert.bbs.security.UserVerifier;
import de.feckert.bbs.security.Verifier;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.*;
import java.util.UUID;

/**
 * A UserThread handles Server-Client interaction with a singular
 * client.
 *
 * @author Felix Eckert
 */
public class UserThread implements Runnable {
	// Identifying Variables
	private Socket socket;
	private UUID uuid;
	private String name;
	private UserVerifier verifier = null;

	// Client Thread
	private Thread thread;

	// IO Variables
	private BufferedReader reader;
	private PrintWriter writer;
	private int delay;

	private boolean disconnected = false;

	private Server SERVER;

	// End To End Variables
	private boolean CONNECTION_ECRYPTED = false;
	private KeyPair endToEndKP;
	private PrivateKey privateKey;
	private PublicKey publicKey;
	private Cipher endToEndEncryptCipher;
	private Cipher endToEndDecryptCipher;

	/**
	 * @param uuid The UUID of the User to which this thread "belongs"
	 * @param sock The Socket of the User.
	 */
	public UserThread(UUID uuid, Socket sock) {
		this.uuid = uuid;
		this.socket = sock;
		this.thread = new Thread(this, "JERAN:USER_" + uuid.toString());
		this.thread.start();
	}

	public void run() {
		String response = "";
		this.SERVER = Main.SERVER;

		try {
			// SETUP IO
			writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
			reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

			// Do User Login/Pre-Login actions
			if (!loginUser()) {
				SERVER.LOGGER.infof("Login failed for USER:UUID %s!\n", uuid.toString());
				thread.join();
				return;
			}
			SERVER.LOGGER.infof("Registered user %s with name %s!\n", uuid, name);

			String invoke;
			String[] params;
			String[] split;

			// Command Loop
			startEndToEnd();
			while (!disconnected) {
				writer.write((byte) 0x00);
				writer.flush();

				response = readResponse();
				if (response.matches("")) continue;

				// Parse Command
				split = response.split(" ");
				params = new String[split.length - 1];
				invoke = split[0];
				System.arraycopy(split, 1, params, 0, split.length - 1);

				// Execute
				executeAction(invoke, params);
			}

		} catch (IOException | InterruptedException e) {
			if (e instanceof java.net.SocketException) {
				if (e.getMessage().matches("Connection reset")) {
					SERVER.LOGGER.infof("Lost connection for user %s (IP: %s)\n", getName() + "(" + getUuid() + ")", getIP());
				}
			} else {
				e.printStackTrace();
			}
		}
	}

	/***
	 * Handles the process of logging in a new user.
	 *
	 * @return Was login successful
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private boolean loginUser() throws IOException, InterruptedException {
		// Negotiate Write Delay
		writer.write((byte) 0x02);
		writer.flush();
		delay = Integer.parseInt(readResponse());

		// Load greeting text and insert MOTD, print it.
		String greetText = Util.STATIC_TEXTS.get("GREET").replaceAll("<MOTD>", Util.STATIC_TEXTS.get("MOTD"));
		writeText(delay, greetText, false, "\n");

		boolean welcomePrompt = true;
		while (welcomePrompt) {
			writer.write((byte) 0x00);
			writer.flush();

			String response = readResponse();
			switch (response) {
				case "exit":
					disconnect();
					break;
				case "enter":
					while (!disconnected) { // Loops until a valid username was received
						writeText(delay, "Please enter a Username (Max. 7 Characters)", false, "\n");
						writer.flush();
						writer.write((byte) 0x00);
						writer.flush();
						response = readResponse().split(" ")[0];
						response = response.substring(0, response.length() > 7 ? 7 : response.length());

						if (!SERVER.NAMES.contains(response) || !response.matches("ADMIN")) {
							this.name = response;
							SERVER.NAMES.add(name);
							break;
						}
						printMessage("Username Taken!");
					}

					writeText(delay, Util.STATIC_TEXTS.get("WELCOME"), false, "\n");
					printlnMessage(String.format("\nWelcome %s! Your UUID is %s", name, uuid));

					welcomePrompt = false;
					break;
				case "info":
					writeText(delay, Util.STATIC_TEXTS.get("INFO"), false, "\n");
					break;
			}
		}

		return true;
	}

	private void executeAction(String invoke, String[] params) throws IOException, InterruptedException {
		switch (invoke) {
			case "exit":
				disconnect();
				break;
			case "posts": // Gives a list of posts
				if (params.length < 2) {
					printlnMessage(Posts.generatePostList(0, Posts.POST_HISTORY.size() - 1));
				} else {
					try {
						printlnMessage(Posts.generatePostList(Integer.parseInt(params[0]), Integer.parseInt(params[1])));
					} catch (NumberFormatException e) {
						printMessage("Invalid NumberFormat!");
					}
				}
				break;
			case "post": // Create a new Post
				Posts.Post.constructPost(writer, reader, uuid, verifier);
				break;
			case "reply": // Creates a Reply to another post
				printMessage("What Post do you want to respond to (Post Number)?");
				try {
					// Parse Number
					int postNumber = Integer.parseInt(requestInput());
					if (postNumber >= Posts.POST_HISTORY.size()) {
						printMessage("Invalid PostNumber (to large!)");
						break;
					}
					// Construct post
					Posts.ResponsePost.constructPost(writer, reader, uuid, verifier, Posts.POST_HISTORY.get(postNumber));
				} catch (NumberFormatException e) {
					printMessage("Invalid NumberFormat!");
				}
				break;
			case "recent": // Prints the 10 most recent posts
				printlnMessage(Posts.generatePostList(Posts.POST_HISTORY.size() - 10, Posts.POST_HISTORY.size()));
				break;
			case "read": // Reads a specified post
				if (params.length < 1) {
					printMessage("Command read requires argument \"POST-NUMBER\" (int)!");
					break;
				}
				try {
					int postNumber = Integer.parseInt(params[0]);

					printlnMessage(Posts.POST_HISTORY.get(postNumber).toString());
				} catch (NumberFormatException e) {
					printMessage("Invalid NumberFormat!");
					break;
				}
				break;
			case "verify":
				if (params.length == 0) {
					printMessage("Expected atleast 1 Argument: create/login");
				} else if (params[0].matches("login")) {

					printMessage("Username (7 Chars): ");
					String nm = requestInput().split(" ")[0];
					nm = nm.substring(0, nm.length() > 7 ? 7 : nm.length());
					printMessage("Password: ");
					String pw = requestInput().split(" ")[0];
					this.verifier = Verifier.verify(nm, pw);

					if (verifier != null) {
						SERVER.LOGGER.infof("User [%s] verified as %s [pk=%s]\n", SERVER.getUserName(uuid), verifier.getName(), verifier.getUuid());
						printMessage("Welcome " + nm + "!");
					} else {
						printMessage("Invalid Username/Password!");
						break;
					}

				} else if (params[0].matches("create")) {

					// Get Credentials
					printMessage("Username (7 Chars): ");
					String nm = requestInput().split(" ")[0];
					nm = nm.substring(0, nm.length() > 7 ? 7 : nm.length());
					printMessage("Password: ");
					String pw = requestInput().split(" ")[0];

					// Create Verifier
					Verifier.createVerifier(nm, pw);

				} else {
					printMessage(String.format("Invalid 1st Argument \"%s\"! Expected login or create!", params[0]));
				}
				break;
			case "help":
				writeText(delay, Util.STATIC_TEXTS.get("HELP"), false, "\n");
				break;
			case "msg":
				if (params.length < 2) {
					printMessage("Expect atleast two Arguments: UUID; Message!");
					break;
				}
				try{
					UserThread receiver = SERVER.USERS.get(UUID.fromString(params[0]));
					StringBuilder message = new StringBuilder();
					for (int i = 1; i < params.length; i++) message.append(params[i]);
					receiver.userMessage(uuid, message.toString());
				} catch (IllegalArgumentException e){
					printMessage("Invalid UUID Format!");
				}
				break;
		}
	}

	public void userMessage(UUID sender, String message) {
		if (sender == this.uuid) return;
		printMessage("Message from User "+ SERVER.getUserName(sender)+": ");
		printMessage(message);
		writer.flush();
	}

	/**
	 * Sets up receival of encrypted messages with the client
	 * using RSA.
	 *
	 * @see java.security.KeyPairGenerator
	 * @see UserThread#stopEndToEnd()
	 * */
	private void startEndToEnd() {
		if (endToEndKP != null) stopEndToEnd();

		KeyPairGenerator generator = null;
		try {
			generator = KeyPairGenerator.getInstance("RSA");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			printMessage("Server Failed to generate End To End Key Pair!");
			return;
		}
		generator.initialize(2048);
		this.endToEndKP = generator.generateKeyPair();
		this.privateKey = endToEndKP.getPrivate();
		this.publicKey  = endToEndKP.getPublic();
		try {
			endToEndEncryptCipher = Cipher.getInstance("RSA");
			endToEndDecryptCipher = Cipher.getInstance("RSA");
			endToEndEncryptCipher.init(Cipher.ENCRYPT_MODE, publicKey);
			endToEndDecryptCipher.init(Cipher.DECRYPT_MODE, privateKey);
		} catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException e) {
			e.printStackTrace();
			printMessage("Server Failed to generate End To End Ciphers!");
			return;
		}

		CONNECTION_ECRYPTED = true;

		// Tell the client that it should send encrypted messages
		writer.write(0x03);
		writer.flush();

		writer.write(Base64.getEncoder().encodeToString(publicKey.getEncoded())+"\n");//.replaceAll("(?:\\r\\n|\\n\\r|\\n|\\r)", "")); // Give the client the public key
		writer.flush();
	}

	/**
	 * Stops the client from sending only encrypted messages.
	 * @see UserThread#startEndToEnd()
	 * */
	private void stopEndToEnd() {
		writer.write(0x04);
		writer.flush();
		endToEndDecryptCipher = null;
		endToEndEncryptCipher = null;
		privateKey = null;
		publicKey  = null;
		endToEndKP = null;
		CONNECTION_ECRYPTED = false;
	}

	/**
	 * "Safely" disconnect the user.
	 *
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void disconnect() throws IOException, InterruptedException {
		SERVER.LOGGER.infof("Disconnecting User [IP: %s; UUID: %s]\n", getIP(), uuid);
		writer.write((byte) 0xff); // SEND CONNECTION_CLOSE CODE
		writer.flush();
		socket.close();
		disconnected = true;
	}

	/**
	 * Writes a Message to the client with no additional LF/RC.
	 *
	 * @param message Message to be written
	 */
	public void printMessage(String message) {
		try {
			writeText(0, message, false, "");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Writes a Message to the client with a Line-Feed at the end
	 *
	 * @param message Message to be written
	 */
	public void printlnMessage(String message) {
		try {
			writeText(0, message, true, "\n");
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Writes a text Char-by-Char to the user
	 *
	 * @param delay     How many milliseconds should be inbetween each char
	 * @param text      The text to be written
	 * @param lf        Add a Line-Feed after each segment
	 * @param splitChar Char to segment the text by
	 * @throws InterruptedException
	 */
	private void writeText(int delay, String text, boolean lf, String splitChar) throws InterruptedException {
		if (socket.isClosed() || disconnected) return;
		for (String s : text.split(splitChar)) {
			writer.write((byte) 0x01);
			writer.print(s + (lf ? "\n" : ""));
			writer.flush();
			Thread.sleep(delay);
		}
		writer.write((byte) 0x01);
		writer.println();
		writer.flush();
	}

	/**
	 * Requests input from the user
	 * @return Last input as a String
	 * */
	private String requestInput() throws IOException {
		if (socket.isClosed() || disconnected) return "";
		writer.write(0x00);
		writer.flush();
		return readResponse();
	}

	/**
	 * Reads a response from the socket
	 *
	 * @return Newest message
	 * @throws IOException
	 */
	private String readResponse() throws IOException {
		if (socket.isClosed() || disconnected) return "";
		String message = reader.readLine();

		if (CONNECTION_ECRYPTED) {
			// Decrypt the message
			try {
				message = new String(endToEndDecryptCipher.doFinal(Base64.getDecoder().decode(message
						.replace("\n", "").replace("\r", ""))));
			} catch (IllegalBlockSizeException | BadPaddingException  e) {
				e.printStackTrace();
				printMessage("Failed to Decrypt Your Message! Regenerating keys");
				return "";
			}
		}

		return message.replace("\n", "").replace("\r", "");
	}

	/**
	 * @return The name of the User
	 * */
	public String getName() {
		return this.name;
	}

	/**
	 * @return The users UUID
	 * */
	public UUID getUuid() {
		return this.uuid;
	}

	/**
	 * @return The users IP
	 * */
	public String getIP() {
		return ((InetSocketAddress)this.socket.getRemoteSocketAddress()).getAddress().toString();
	}
}
