package top.pigimag.plif;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for pinging a Minecraft Java Edition server and extracting the
 * JSON status response from the server list ping protocol.
 *
 * This implementation is intentionally lightweight and avoids a third-party
 * JSON library by using a minimal string extraction strategy.
 *
 * @since 1.0
 */
public class McJavaPinger {
    private final InetAddress host;
    private final int port;

    /**
     * Construct a Java edition pinger by hostname.
     *
     * @param host the hostname or IP string identifying the server.
     * @param port the TCP port of the server.
     * @throws NullPointerException if the host string is null.
     * @throws IllegalArgumentException if the port is less than 1.
     * @throws UnknownHostException if the host string cannot be resolved.
     * @since 0.1.0
    */
    public McJavaPinger(String host, int port) throws UnknownHostException {
        this(Objects.requireNonNull(InetAddress.getByName(host)), port);
    }

    /**
     * The constructor.
     *
     * @param host the ip to ping.
     * @param port the port to ping.
     *
     * @throws NullPointerException if <code>ip</code> == null.
     * @throws IllegalArgumentException if <code>port</code> is less than 1.
     *
     * @since 0.1.0
     */
    public McJavaPinger(InetAddress ip, int port) {
        this.host = Objects.requireNonNull(ip, "host");
        if (port < 1) {
            throw new IllegalArgumentException("Port must be greater than zero");
        }
        this.port = port;
    }

    /**
     * The class describes the response of ping.
     *
     * @since 1.0
     * @see McJavaPinger
     */
    public static final class PingResponse extends BasePingResponse {
        private static final long serialVersionUID = 1776585528L;

        private final String description;
        private final List<String> playerSample;
        private final String favicon;

        /**
         * The constructor. We suppose all users will use this correctly.
         *
         * @param host The host.
         * @param port The port.
         * @param latencyMillis The latency in millis.
         * @param versionName The name of version.
         * @param protocolVersion The version of protocol.
         * @param description The description.
         * @param onlinePlayers The amount of online players.
         * @param maxPlayers The amount of max players.
         * @param playerSample The sample of online players.
         * @param favicon The favicon.
         * @param rawResponse The raw response.
         *
         * @since 1.0
         */
        public PingResponse(
                String host,
                int port,
                long latencyMillis,
                String versionName,
                int protocolVersion,
                String description,
                int onlinePlayers,
                int maxPlayers,
                List<String> playerSample,
                String favicon,
                String rawResponse) {
            super(host, port, latencyMillis, protocolVersion, versionName, onlinePlayers, maxPlayers, rawResponse);
            this.description = description;
            this.playerSample = playerSample;
            this.favicon = favicon;
        }

        @Override
        public String toString() {
            return String.format(
                    "Java %s:%d latency=%dms version=%s protocol=%d players=%d/%d description=%s",
                    host,
                    port,
                    latencyMillis,
                    versionName,
                    protocolVersion,
                    onlinePlayers,
                    maxPlayers,
                    description);
        }

        public String getDescription() {
            return description;
        }

        public List<String> getPlayerSample() {
            return playerSample;
        }

        public String getFavicon() {
            return favicon;
        }
    }

    /**
     * The ping method.
     *
     * @return A PingResponse
     * @throws IOException if an I/O error occurs during ping
     * @see PingResponse
     * @see McJavaPinger#asyncPing()
     * @since 1.0
     */
    /**
     * Perform a synchronous Java edition ping request.
     *
     * @return parsed ping response details.
     * @throws IOException if an I/O error occurs during the handshake, status query,
     *                     or ping exchange.
     * @since 1.0
     */
    public PingResponse ping() throws IOException {
        try (Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(3000);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Send handshake
            sendHandshake(out);

            // Send status request
            sendStatusRequest(out);

            // Read status response
            String jsonResponse = readStatusResponse(in);

            // Send ping
            long pingId = ThreadLocalRandom.current().nextLong();
            long startTime = System.nanoTime();
            sendPing(out, pingId);

            // Read pong
            readPong(in, pingId);
            long latencyMillis = Math.round((System.nanoTime() - startTime) / 1_000_000.0);

            return parseResponse(jsonResponse, latencyMillis);
        }
    }

    private void sendHandshake(DataOutputStream out) throws IOException {
        // Handshake packet: ID=0, Protocol version, Host, Port, Next state=1 (status)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream packetOut = new DataOutputStream(baos);
        packetOut.writeByte(0x00); // Packet ID
        writeVarInt(packetOut, 754); // Protocol version (example, should be dynamic)
        writeString(packetOut, host.getHostName());
        packetOut.writeShort(port);
        writeVarInt(packetOut, 1); // Next state: status

        byte[] packetData = baos.toByteArray();
        writeVarInt(out, packetData.length);
        out.write(packetData);
    }

    private void sendStatusRequest(DataOutputStream out) throws IOException {
        // Status request packet: ID=0, no data
        out.writeByte(0x01); // Packet length (VarInt)
        out.writeByte(0x00); // Packet ID
    }

    private String readStatusResponse(DataInputStream in) throws IOException {
        int packetLength = readVarInt(in);
        if (packetLength <= 0) {
            throw new IllegalStateException("Invalid status response length: " + packetLength);
        }
        int packetId = readVarInt(in);
        if (packetId != 0x00) {
            throw new IllegalStateException("Unexpected packet ID: " + packetId);
        }
        return readString(in);
    }

    private void sendPing(DataOutputStream out, long pingId) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream packetOut = new DataOutputStream(baos);
        packetOut.writeByte(0x01); // Packet ID
        packetOut.writeLong(pingId);

        byte[] packetData = baos.toByteArray();
        writeVarInt(out, packetData.length);
        out.write(packetData);
    }

    private void readPong(DataInputStream in, long expectedPingId) throws IOException {
        int packetLength = readVarInt(in);
        if (packetLength <= 0) {
            throw new IllegalStateException("Invalid pong packet length: " + packetLength);
        }
        int packetId = readVarInt(in);
        if (packetId != 0x01) {
            throw new IllegalStateException("Unexpected pong packet ID: " + packetId);
        }
        long pingId = in.readLong();
        if (pingId != expectedPingId) {
            throw new IllegalStateException("Ping ID mismatch");
        }
    }

    private PingResponse parseResponse(String json, long latencyMillis) {
        // Simple JSON parsing (in real implementation, use a JSON library)
        // Assuming JSON format: {"version":{"name":"...","protocol":...},"players":{"max":...,"online":...,"sample":[...]},"description":"...","favicon":"..."}
        // For simplicity, extract fields manually
        String versionName = extractJsonValue(json, "version", "name");
        String protocolStr = extractJsonValue(json, "version", "protocol");
        if (protocolStr.isEmpty()) {
            throw new IllegalStateException("Missing protocol version in JSON response: " + json);
        }
        int protocolVersion;
        try {
            protocolVersion = Integer.parseInt(protocolStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid protocol version in JSON response: " + protocolStr, e);
        }
        String description = extractJsonValue(json, "description");
        String maxPlayersStr = extractJsonValue(json, "players", "max");
        if (maxPlayersStr.isEmpty()) {
            throw new IllegalStateException("Missing max players in JSON response: " + json);
        }
        int maxPlayers;
        try {
            maxPlayers = Integer.parseInt(maxPlayersStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid max players in JSON response: " + maxPlayersStr, e);
        }
        String onlinePlayersStr = extractJsonValue(json, "players", "online");
        if (onlinePlayersStr.isEmpty()) {
            throw new IllegalStateException("Missing online players in JSON response: " + json);
        }
        int onlinePlayers;
        try {
            onlinePlayers = Integer.parseInt(onlinePlayersStr);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Invalid online players in JSON response: " + onlinePlayersStr, e);
        }
        List<String> playerSample = extractJsonArray(json, "players", "sample");
        String favicon = extractJsonValue(json, "favicon");

        return new PingResponse(
                host.getHostAddress(),
                port,
                latencyMillis,
                versionName,
                protocolVersion,
                description,
                onlinePlayers,
                maxPlayers,
                playerSample,
                favicon,
                json);
    }

    private String extractJsonValue(String json, String... keys) {
        // Very basic JSON extractor, not robust
        String key = "\"" + keys[0] + "\":";
        int start = json.indexOf(key);
        if (start == -1) return "";
        start += key.length();
        if (keys.length > 1) {
            // Nested
            String nestedKey = "\"" + keys[1] + "\":";
            start = json.indexOf(nestedKey, start);
            if (start == -1) return "";
            start += nestedKey.length();
        }
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;
        int end = start;
        if (json.charAt(start) == '"') {
            // String value
            start++;
            end = json.indexOf('"', start);
            if (end == -1) return "";
        } else {
            // Number or boolean
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != ']') end++;
        }
        String value = json.substring(start, end).trim();
        return value;
    }

    private List<String> extractJsonArray(String json, String... keys) {
        // Basic array extractor for strings
        String key = "\"" + keys[0] + "\":";
        int start = json.indexOf(key);
        if (start == -1) return null;
        start += key.length();
        if (keys.length > 1) {
            String nestedKey = "\"" + keys[1] + "\":";
            start = json.indexOf(nestedKey, start);
            if (start == -1) return null;
            start += nestedKey.length();
        }
        if (json.charAt(start) != '[') return null;
        int end = json.indexOf(']', start);
        if (end == -1) return null;
        String arrayContent = json.substring(start + 1, end).trim();
        if (arrayContent.isEmpty()) return List.of();
        return List.of(arrayContent.split(",")).stream()
                .map(s -> s.trim())
                .map(s -> s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s)
                .toList();
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & -128) != 0) {
            out.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = in.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) {
                throw new RuntimeException("VarInt too big");
            }
        } while ((read & 0b10000000) != 0);
        return result;
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    /**
     * Call the {@link top.pigimag.plif.McJavaPinger#ping() ping()} async.
     *
     * @return A CompletableFuture.
     * @see CompletableFuture
     * @see McJavaPinger#ping()
     * @since 1.0
     */
    /**
     * Perform the ping asynchronously using the common {@link java.util.concurrent.ForkJoinPool}.
     *
     * @return a future containing the ping response.
     * @since 1.0
     */
    public CompletableFuture<PingResponse> asyncPing() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return ping();
            } catch (IOException e) {
                throw new RuntimeException("Asynchronous ping failed", e);
            }
        });
    }
}