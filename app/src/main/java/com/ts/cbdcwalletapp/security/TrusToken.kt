package com.ts.cbdcwalletapp.security

/**
 * Low-level JNI interface to native smart card / USB token library.
 */
object TrusToken {
    init {
        System.loadLibrary("native-lib")
    }

    /**
     * Initializes the USB connection with the given file descriptor.
     * @return 0 on success, or error code (<0).
     */
    external fun libint(fileDescriptor: Int): Int

    /**
     * Performs PIN-based login on the smart card session.
     * @param tokenPin User's token PIN.
     * @return "Login Success" on success or error description string.
     */
    external fun login(tokenPin: String): String

    /**
     * Closes the active smart card session and logs out.
     * @return Result string (e.g., "Logged out Successfully").
     */
    external fun logout(): String

    /**
     * Reads the X.509 certificate from the token.
     * @return Hex-encoded certificate string or error message.
     */
    external fun readCertificate(): String

    /**
     * Signs data using the private key stored on the smart card.
     * @return Hex-encoded signature string or error message.
     */
    external fun signData(): String

    /**
     * Verifies a signature against plain text.
     */
    external fun verify(signatureHex: String, data: String): String

    /**
     * Encrypts plain data using the smart card.
     */
    external fun encrypt(): String

    /**
     * Decrypts hex encrypted data using the private key on the smart card.
     */
    external fun decrypt(encryptedHex: String): String
}
