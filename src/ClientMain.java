import java.util.Base64;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.EncodedKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Scanner;

//TODO(all): Make a good client

/**
 * Hasty implementation for a JERAN Client.
 * */
public class ClientMain {
	// I know that its not best practice to do this, but this is just for testing and not a client
	// implementation meant to be broadly used.
	public static void main(String[] args) throws IOException, InterruptedException, InvalidKeySpecException,
			NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
		Socket sock = new Socket("localhost", 3103);
		PrintWriter out = new PrintWriter(sock.getOutputStream(), true);
		BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));

		int msgType = in.read();
		if (msgType == 2) {
			out.println("25");
		}

		Scanner scanner = new Scanner(System.in);
		PublicKey publicKey;
		Cipher encryptionCipher = Cipher.getInstance("RSA");
		boolean encryptedMessages = false;
		while (sock.isConnected()) {
			//////////////////////////////////////////////////
			// JERAN COM-PROTOCOL MESSAGE TYPES             //
			// ---------------------------------------------//
			// 0x00 Request user input                      //
			// 0x01 Write to user output                    //
			// -> Followed by text to be output             //
			// 0x02 Negotiate writing speed                 //
			// 0x03 Start Encrypted Communication (one-way) //
			// -> Followed by Base64 encoded public key     //
			// 0x04 Stop Encrypted Communication            //
			//////////////////////////////////////////////////
			msgType = in.read();

			if (msgType == 1) {
				String response = in.readLine();
				response = response.replaceAll("" + (char) 0x01, "");
				System.out.println(response);
			} else if (msgType == 0) {
				boolean[] inputSent = {false};

				new Thread(() -> {
					while (!inputSent[0]) {
						String response = null;
						try {
							response = in.readLine();
						} catch (IOException e) {
							e.printStackTrace();
						}
						response = response.replaceAll("" + (char) 0x01, "");
						System.out.println("\n"+response);
					}
				}).start();

				System.out.print("> ");
				String input = scanner.nextLine();
				if (encryptedMessages) {
					String encrypted = Base64.getEncoder().encodeToString(
							encryptionCipher.doFinal(input.getBytes()));
					out.println(encrypted);
					continue;
				}
				out.println(input);
				System.out.println();
				inputSent[0] = true;
			} else if (msgType == 3) {
				// Get Public Key
				KeyFactory keyFactory = KeyFactory.getInstance("RSA");
				EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(Base64.getDecoder().decode(in.readLine()));
				publicKey = keyFactory.generatePublic(publicKeySpec);

				// Create Encryption Cipher
				encryptionCipher.init(Cipher.ENCRYPT_MODE, publicKey);
				encryptedMessages = true;
			} else if (msgType == 4) {
				encryptedMessages = false;
				encryptionCipher = Cipher.getInstance("RSA");
			}
		}
	}
}
