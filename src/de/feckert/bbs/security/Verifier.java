package de.feckert.bbs.security;

import de.feckert.bbs.Logger;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.UUID;

/**
 * Used to create new UserVerifiers and for Logging-In to a User-Verifier.
 * Passwords are encrypted using "PBKDF2WithHmacSHA1" and a 16-byte salt.
 * <p>
 * The VERIFIER-DATABASE is stored in a configurable file as a java-object
 * with the file extension ".hmdb" (Hash-Map Database).
 *
 * @author Felix Eckert
 */
public class Verifier {
	private static final Logger VERIFIER_LOGGER = Logger.create("JERAN:VERI");

	private static HashMap<String[], String[]> VERIFIER_DB = new HashMap<>();
	public static File DB_PATH;

	/**
	 * Creates a new User-Verifier and stores it to the VERIFIER_DB.
	 * The Public-Key is auto-generated.
	 *
	 * @param name     The User-Name for the Verifier
	 * @param password The Password for the Verifier
	 */
	public static void createVerifier(String name, String password) {
		VERIFIER_LOGGER.info("Creating new UserVerifier!");

		String[] keys = new String[2];
		String[] values = new String[2];

		// Encrypt Password
		SecureRandom random = new SecureRandom();
		byte[] salt = new byte[16];
		random.nextBytes(salt);

		byte[] hash;
		try {
			KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);
			SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");

			hash = factory.generateSecret(spec).getEncoded();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			VERIFIER_LOGGER.err("Failed to hash new private key!");
			e.printStackTrace();
			return;
		}

		// Write Data
		keys[0] = Base64.getEncoder().encodeToString(hash);
		keys[1] = Base64.getEncoder().encodeToString(salt);
		while (ensureNonDuplicatePK(values[0])) {
			values[0] = UUID.randomUUID().toString();
		} // Create Public Key & Make Sure its not duplicate
		values[1] = name;

		// Save to DB & Save the DB
		VERIFIER_DB.put(keys, values);
		VERIFIER_LOGGER.info("Registered new UserVerifier to Database!");
		saveDataBase();
	}

	/**
	 * Verifies User-Verifier credentials and returns a {@link de.feckert.bbs.security.UserVerifier}
	 * object matching to the User-Verifier info.
	 *
	 * @param uname    The Username to be verified
	 * @param password The Password to be verified
	 * @return A UserVerifier object containing the information matching to the given credentials, NULL if invalid
	 */
	public static UserVerifier verify(String uname, String password) {
		UserVerifier[] verifier = {null};

		// Setup KeyFactory
		SecretKeyFactory factory;
		try {
			factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
		} catch (NoSuchAlgorithmException e) {
			VERIFIER_LOGGER.err("Failed to Verify! NSAE thrown!");
			e.printStackTrace();
			return null;
		}

		// Verify Credentials here
		VERIFIER_DB.forEach((k, v) -> {
			String nm = v[1];
			if (nm.matches(uname)) {
				try {
					// Copy Credentials from database and decode from B64
					byte[] hash = Base64.getDecoder().decode(k[0]);
					byte[] salt = Base64.getDecoder().decode(k[1]);
					KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 128);

					// Hash the given Password to be able to compare
					byte[] nhash = factory.generateSecret(spec).getEncoded();
					boolean verified = false;
					if (hash.length == nhash.length) {
						verified = true;
						for (int i = 0; i < hash.length; i++) { // Compare bytes of the hash
							if (hash[i] != nhash[i]) verified = false;
						}
					}

					if (verified) {
						verifier[0] = new UserVerifier(nm, v[0]); // Create User-Verifier
					}
				} catch (InvalidKeySpecException e) {
					VERIFIER_LOGGER.err("Failed to Verify! IKSE thrown!");
					e.printStackTrace();
				}
			}
		});

		return verifier[0];
	}

	/**
	 * Saves the Database to file specified in the field {@link de.feckert.bbs.security.Verifier#DB_PATH}.
	 */
	public static void saveDataBase() {
		try {
			FileOutputStream fileOut = new FileOutputStream(DB_PATH.getAbsolutePath());
			ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
			objectOut.writeObject(VERIFIER_DB);
			objectOut.close();
			VERIFIER_LOGGER.info("Backed-Up VERIFIER_DB!");
		} catch (IOException e) {
			VERIFIER_LOGGER.err("Failed to backup VERIFIER_DB!");
			e.printStackTrace();
		}
	}

	/**
	 * Loads the Database from file specified in the field {@link de.feckert.bbs.security.Verifier#DB_PATH}.
	 */
	@SuppressWarnings("unchecked")
	public static void loadVerifierDB() {
		try {
			FileInputStream fileIn = new FileInputStream(DB_PATH.getAbsolutePath());
			ObjectInputStream objectIn = new ObjectInputStream(fileIn);

			VERIFIER_DB = (HashMap<String[], String[]>) objectIn.readObject();

			objectIn.close();
			VERIFIER_LOGGER.info("Successfully loaded VERIFIER_DB!");
		} catch (IOException | ClassNotFoundException e) {
			VERIFIER_LOGGER.info("Failed to load VERIFIER_DB!");
			e.printStackTrace();
			saveDataBase();
		}
	}

	/**
	 * Ensures that there is no Public-Key registered that is
	 * the same as the specified one.
	 *
	 * @param key The key to be checked for duplicates.
	 * @return If the key has duplicates or not
	 */
	public static boolean ensureNonDuplicatePK(String key) {
		boolean[] returnValue = {true};

		// This function has fucked my brain
		// I don't understand why I hvae to
		// do some things I just have to.
		// - 0x1905
		if (key == null) return true;
		if (VERIFIER_DB == null || VERIFIER_DB.size() == 0) return false;
		VERIFIER_DB.values().forEach((v) -> {
			try {
				if (v[0].matches(key)) returnValue[0] = false;
			} catch (NullPointerException e) {

			}
		});

		return !returnValue[0];
	}
}
