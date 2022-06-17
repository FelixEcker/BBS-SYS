package de.feckert.bbs.user;

import de.feckert.bbs.Logger;
import de.feckert.bbs.Main;
import de.feckert.bbs.Util;
import de.feckert.bbs.security.UserVerifier;

import java.io.*;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Posts {
	public  static       ArrayList<Post>		  POST_HISTORY        = new ArrayList<>();
	private static final ScheduledExecutorService SCHEDULED_EXECUTER  = Executors.newSingleThreadScheduledExecutor();
	private static       ScheduledFuture<?> 	  POST_SAVING_SERVICE = null;
	public  static       File  					  POST_SAVE_DIRECTORY;
	private static final Logger				      POST_MANAGER_LOGGER = Logger.create("JERAN:POST");
	private static       String					  LAST_POST_SAVE      = "";

	/**
	 * Sets up the PostSaveService which periodically and on shutdown
	 * saves all currently registered posts to file.
	 *
	 * @param period The Amount of time between each save
	 * @param timeUnitName The Time Unit for the period
	 * */
	public static void setupPostSaveService(long period, String timeUnitName) {
		POST_MANAGER_LOGGER.info("Setting up PostSaveService...");
		POST_SAVING_SERVICE = SCHEDULED_EXECUTER.scheduleAtFixedRate(new PostSaveServiceThread(false), period, period, TimeUnit.valueOf(timeUnitName));
		Runtime.getRuntime().addShutdownHook(new PostSaveServiceThread(true));

		// FOR DEBUGGING PURPOSES, REMOVE FOR RELEASE
		if (Main.DEBUG) {
			for (int i = 0; i < 50; i++) {
				new Post(UUID.randomUUID(), "Post " + i, "Test Post", true, null);
			}
		}
	}

	/**
	 * Resets the PostHandler to its initial State when it
	 * is loaded into the JRE.
	 * */
	public void reset() {
		POST_MANAGER_LOGGER.info("Resetting PostHandler!");
		POST_SAVING_SERVICE.cancel(false);
		POST_HISTORY.clear();
		LAST_POST_SAVE = "";
		POST_SAVE_DIRECTORY = null;
	}

	private static class PostSaveServiceThread extends Thread {
		private boolean asShutdownHook;

		public PostSaveServiceThread(boolean asShutdownHook) {
			super("JERAN:PSST");
			this.asShutdownHook = asShutdownHook;
		}

		@Override
		@SuppressWarnings("ResultOfMethodCallIgnored")
		public void run() {
			String[] out = {""};

			if (asShutdownHook) POST_MANAGER_LOGGER.info("Executing PostSaveService ShutdownHook!");

			// File Name Format: POST BACKUP-YYYY-MM-DD-HH-MM-SS.txt
			String fileName = "POST BACKUP-"+Util.getDateTimeString().
					replaceAll("[_/: ]","-")+".txt";

			// Create File Content
			POST_HISTORY.forEach((v) -> out[0] += v.toString()+"\n");
			if (LAST_POST_SAVE.matches(out[0])) {
				POST_MANAGER_LOGGER.info("Averted Post-Save because post history is identical!");
				return;
			}
			LAST_POST_SAVE = out[0];

			POST_MANAGER_LOGGER.info("Starting Post-Save!");

			// Write File
			try {
				if (!POST_SAVE_DIRECTORY.exists()) POST_SAVE_DIRECTORY.mkdirs();
				File outFile      = new File(POST_SAVE_DIRECTORY, fileName);
				if (!outFile.createNewFile()) {
					throw new IOException("Failed to create Post-File! ["+outFile.getAbsolutePath()+"]");
				}

				FileWriter myWriter = new FileWriter(outFile);
				myWriter.write(out[0]);
				myWriter.close();
				POST_MANAGER_LOGGER.infof("Successfully saved Posts! [%s]\n", fileName);
			} catch (IOException e) {
				POST_MANAGER_LOGGER.err("Failed to save Post-History (IOException)");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Constructs a nicely formatted list of all currently loaded posts.
	 *
	 * @param startingPoint Start Index for post list
	 * @param depth         How long the list should be
	 * @return A nicely formatted post list
	 */
	public static String generatePostList(int startingPoint, int depth) {
		if (POST_HISTORY.size() == 0) return "There are currently no Posts!\n";

		// Create Table Header
		StringBuilder out = new StringBuilder(Util.padToLength("ID", Util.digitsInInt(POST_HISTORY.size() < 3 ? 4 : POST_HISTORY.size() - 1), ' ') + " | ");
		out.append(Util.padToLength("Date-Time", 19, ' ')).append(" | ");
		out.append(Util.padToLength("Poster", 48, ' ')).append(" | ");
		out.append("Title\n");

		Post v; // Construct list below
		for (int i = startingPoint; i < POST_HISTORY.size() && i < depth + 1; i++) {
			v = POST_HISTORY.get(i);

			// Format Post ID
			out.append(Util.padToLength(String.valueOf(i), Util.digitsInInt(POST_HISTORY.size() < 3 ? 4 : POST_HISTORY.size() - 1), ' '));
			out.append(" | ");

			// Date-Time String
			out.append(v.getDateTime()).append(" | ");

			// Format Poster Information
			if (v.getVerifier() != null) { // Use the UserVerifier information if the post has it
				out.append("V ").append(v.getVerifier().toString()).append(" | ");
			} else { // Else use the "temp" user information
				out.append("N ").append(String.format("%s (%s)", Main.SERVER.USERS.containsKey(v.getPoster()) ?
						Main.SERVER.USERS.get(v.getPoster()).getName() : "unknown", v.getPoster())).append(" | ");
			}
			out.append(v.getTitle()).append("\n");
		}

		return out.toString();
	}

	/**
	 * The Post Class is used for representing a post at
	 * Runtime.
	 * */
	public static class Post {
		protected String title;
		protected String text;
		protected UUID poster;
		protected boolean saveable;
		protected String dateTime;
		protected int id;
		protected UserVerifier verifier;

		public Post(UUID poster, String title, String text, boolean saveable, UserVerifier verifier) {
			this.title = title;
			this.text = text;
			this.poster = poster;
			this.saveable = saveable;
			this.dateTime = Util.getDateTimeString();
			POST_HISTORY.add(this);
			this.id = POST_HISTORY.indexOf(this);
			this.verifier = verifier;
		}

		@Override
		public String toString() {
			String out = "==============================\n";
			out += title + "\n";
			if (verifier != null) {
				out += "Author: " + verifier + "\n";
			} else {
				out += "Author: " + Main.SERVER.getUserName(poster) + "\n";
			}
			out += "Posted on: " + dateTime + "\n\n";
			out += text;
			out += "\n==============================";

			return out;
		}

		public String getTitle() {
			return this.title;
		}

		public String getText() {
			return this.text;
		}

		public UUID getPoster() {
			return this.poster;
		}

		public boolean getSaveable() {
			return this.saveable;
		}

		public String getDateTime() {
			return this.dateTime;
		}

		public int getId() {
			return this.id;
		}

		public UserVerifier getVerifier() {
			return this.verifier;
		}

		////////////////////////////////////

		/**
		 * Constructs a new Post with user input.
		 *
		 * @param writer       The PrintWriter for the user
		 * @param reader       The BufferedReader for the user
		 * @param poster       The UUID of the user
		 * @param userVerifier UserVerifier object to verify the identity of the poster (OPTIONAL)
		 */
		public static void constructPost(PrintWriter writer, BufferedReader reader, UUID poster, UserVerifier userVerifier) {
			String postText = "";
			String postTitle;
			boolean saveable;

			// Construct Title
			writer.write((byte) 0x01);
			writer.println("Post Title (30 Characters): ");
			writer.flush();
			postTitle = Util.requestInput(writer, reader).replace("|", "");

			// Construct Body
			writer.write((byte) 0x01);
			writer.println("Write (type !exit! to exit):");
			writer.flush();
			while (true) {
				String in = Util.requestInput(writer, reader);
				if (in.matches("!exit!")) break;
				postText += in + "\n";
			}

			// Ask for archival
			writer.write((byte) 0x01);
			writer.println("Are we allowed to archive our post (Y/N)");
			writer.flush();
			saveable = Util.requestInput(writer, reader).matches("Y");

			new Post(poster, postTitle, postText, saveable, userVerifier);
		}
	}

	public static class ResponsePost extends Posts.Post {
		protected Post originalPost;

		public ResponsePost(Post originalPost, UUID poster, String title, String text, boolean saveable, UserVerifier verifier) {
			super(poster, title, text, saveable, verifier);
			this.originalPost = originalPost;
		}


		public static void constructPost(PrintWriter writer, BufferedReader reader, UUID poster, UserVerifier userVerifier, Post originalPost) {
			String postText = "";
			String postTitle;
			boolean saveable;

			// Construct Title
			writer.write((byte) 0x01);
			writer.println("Post Title (30 Characters): ");
			writer.flush();
			postTitle = Util.requestInput(writer, reader).replace("|", "");

			// Construct Body
			writer.write((byte) 0x01);
			writer.println("Write (type !exit! to exit):");
			writer.flush();
			while (true) {
				String in = Util.requestInput(writer, reader);
				if (in.matches("!exit!")) break;
				postText += in + "\n";
			}

			// Ask for archival
			writer.write((byte) 0x01);
			writer.println("Are we allowed to archive our post (Y/N)");
			writer.flush();
			saveable = Util.requestInput(writer, reader).matches("Y");

			new ResponsePost(originalPost, poster, postTitle, postText, saveable, userVerifier);
		}

		@Override
		public String toString() {
			String out = super.toString();
			out = out.replace("==============================",
					String.format("RE [%s]: %s\n", originalPost.getId(), originalPost.getTitle()));
			out += "Title: " + title + "\n";
			if (verifier != null) {
				out += "Author: " + verifier + "\n";
			} else {
				out += "Author: " + Main.SERVER.getUserName(poster) + "\n";
			}
			out += "Posted on: " + dateTime + "\n\n";
			out += text;
			out += "\n==============================";

			return out;
		}
	}
}
