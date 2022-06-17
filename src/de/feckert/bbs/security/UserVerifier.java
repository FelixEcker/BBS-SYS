package de.feckert.bbs.security;

/**
 * The UserVerifier is used inorder to verify the indentity of user
 * in a post. This is optional if a user wants to be known. Verification
 * is achieved using the {@link de.feckert.bbs.security.Verifier}.
 * This Class holds the Username and Public Verification-Key (UUID String).
 *
 * @author Felix Eckert
 */
public class UserVerifier {
	private String name;
	private String uuid;

	public UserVerifier(String name, String uuid) {
		this.name = name;
		this.uuid = uuid;
	}

	@Override
	public String toString() {
		return name + " (" + uuid + ")";
	}

	public String getName() {
		return this.name;
	}

	public String getUuid() {
		return this.uuid;
	}
}
