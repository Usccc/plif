package top.pigimag.plif.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

import top.pigimag.plif.BasePingResponse;
import top.pigimag.plif.McBedrockPinger;
import top.pigimag.plif.McJavaPinger;

/**
 * A ping utility that attempts both Bedrock and Java edition ping protocols.
 *
 * This class first tries to ping the server using the Bedrock protocol, and if
 * that fails it falls back to the Java edition server list ping.
 *
 * @since 1.0
 * @see top.pigimag.plif.McBedrockPinger
 * @see top.pigimag.plif.McJavaPinger
 */
public class McPingerTri {
    private final McBedrockPinger mbp;
    private final McJavaPinger mjp;

    /**
     * The constructor.
     * 
     * @param host The host in {@link String String}.
     * @param port The port.
     * @throws UnknownHostException if no IP address for the host could be found, or if a scope_id was specified for a global IPv6 address.
     */
    public McPingerTri(String host, int port) throws UnknownHostException {
        mbp = new McBedrockPinger(host, port);
        mjp = new McJavaPinger(host, port);
    }

    /**
     * Construct using an already resolved address.
     *
     * @param host the resolved server address.
     * @param port the server port.
     * @throws UnknownHostException never thrown for this constructor, but kept for compatibility.
     */
    public McPingerTri(InetAddress host, int port) throws UnknownHostException {
        mbp = new McBedrockPinger(host, port);
        mjp = new McJavaPinger(host, port);
    }

    /**
     * Attempt a Bedrock ping first, then fall back to a Java ping if Bedrock fails.
     *
     * @return the successful ping response.
     * @throws IOException if both Bedrock and Java ping attempts fail.
     */
    public BasePingResponse ping() throws IOException {
        IOException bedrockException = null;
        try {
            return mbp.ping();
        } catch (IOException e) {
            bedrockException = e;
        }

        try {
            return mjp.ping();
        } catch (IOException javaException) {
            javaException.addSuppressed(bedrockException);
            throw new IOException("Both Bedrock and Java ping failed", javaException);
        }
    }
}
