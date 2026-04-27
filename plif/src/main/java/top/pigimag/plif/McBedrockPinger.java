package top.pigimag.plif;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for pinging a Minecraft Bedrock Edition server and parsing the
 * Bedrock status response.
 *
 * The Bedrock ping protocol is a lightweight UDP exchange that returns an
 * answer packet with several status fields separated by semicolons.
 *
 * @since 1.0
 */
public class McBedrockPinger {
    private final InetAddress host;
    private final int port;
    
    /**
     * Construct a Bedrock pinger by hostname.
     *
     * @param host the hostname or IP string identifying the Bedrock server.
     * @param port the UDP port of the Bedrock server.
     * @throws NullPointerException if the host string is null.
     * @throws IllegalArgumentException if the port is less than 1.
     * @throws UnknownHostException if the host name cannot be resolved.
     * @since 0.1.0
    */
    public McBedrockPinger(String host,int port) throws UnknownHostException{
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
    public McBedrockPinger(InetAddress ip, int port) {
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
     * @see McBedrockPinger
     */
    public static final class PingResponse extends BasePingResponse {
        private static final long serialVersionUID = 177658264L;

        private final String motd;
        private final String motd2;
        private final long pingId;
        private final int serverId;
        private final String serverUniqueId;
        private final List<String> playerList;  //This might be null when BE simple ping.
        private final String gamemode;
        private final byte gamemodeID;

        /**
         * The constructor.<strong>We suppose all users will use this correctly.</strong>
         * 
         * @param host The host.
         * @param port The port.
         * @param latencyMillis The latency in millis.
         * @param protocolVersion The version of protocol.
         * @param versionName The name of version.
         * @param motd The MOTD.
         * @param motd2 The MOTD2.
         * @param onlinePlayers The amount of online players.
         * @param maxPlayers The amount of max players
         * @param pingId The ping ID.
         * @param serverId The server ID.
         * @param serverUniqueId The server unique ID.
         * @param playerList The list of online players.
         * @param rawResponse The raw response.
         * @param gamemode The gamemode(e.g. Survial)
         * @param gamemodeID The id of the gamemode(e.g. 1 (-- survial))
         * 
         * @since 1.0
         */
        public PingResponse(
                String host,
                int port,
                long latencyMillis,
                int protocolVersion,
                String versionName,
                String motd,
                String motd2,
                int onlinePlayers,
                int maxPlayers,
                long pingId,
                int serverId,
                String serverUniqueId,
                List<String> playerList,
                String rawResponse,
                String gamemode,
                byte gamemodeID) {
            super(host, port, latencyMillis, protocolVersion, versionName, onlinePlayers, maxPlayers, rawResponse);
            this.motd = motd;
            this.motd2 = motd2;
            this.pingId = pingId;
            this.serverId = serverId;
            this.serverUniqueId = serverUniqueId;
            this.playerList = playerList;
            this.gamemode = gamemode;
            this.gamemodeID = gamemodeID;
        }

        @Override
        public String toString() {
            return String.format(
                    "Bedrock %s:%d latency=%dms version=%s protocol=%d players=%d/%d motd=%s%s serverId=%d serverUniqueId=%s",
                    host,
                    port,
                    latencyMillis,
                    versionName,
                    protocolVersion,
                    onlinePlayers,
                    maxPlayers,
                    motd,
                    motd2.isEmpty() ? "" : " & " + motd2,
                    serverId,
                    serverUniqueId);
        }

        public String getMotd() {
            return motd;
        }

        public String getMotd2() {
            return motd2;
        }

        public long getPingId() {
            return pingId;
        }

        public int getServerId() {
            return serverId;
        }

        public String getServerUniqueId() {
            return serverUniqueId;
        }

        public List<String> getPlayerList() {
            return playerList;
        }

        public String getGamemode() {
            return gamemode;
        }

        public byte getGamemodeID() {
            return gamemodeID;
        }
    }

    /**
     * The ping method.
     * 
     * @return A PingResponse
     * @throws IOException if an I/O error occurs during ping
     * @see PingResponse
     * @see McBedrockPinger#asnycPing()
     * @since 1.0
     */
    /**
     * Perform a Bedrock ping request synchronously and parse the server response.
     *
     * @return parsed ping response details.
     * @throws IOException if a networking error occurs or the UDP payload cannot be read.
     * @since 1.0
     */
    public PingResponse ping() throws IOException {
        byte[] request = buildPingRequest();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);
            DatagramPacket packet = new DatagramPacket(request, request.length, host, port);
            long startTime = System.nanoTime();
            socket.send(packet);

            byte[] buffer = new byte[2048];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            long latencyMillis = Math.round((System.nanoTime() - startTime) / 1_000_000.0);
            return parseResponse(responsePacket, latencyMillis);
        }
    }

    private static final byte PACKET_ID_UNCONNECTED_PING = 0x01;
    private static final byte PACKET_ID_UNCONNECTED_PONG = 0x1C;
    private static final byte[] MAGIC = new byte[] {
            0x00, (byte) 0xFF, (byte) 0xFF, 0x00,
            (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE,
            (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD,
            0x12, 0x34, 0x56, 0x78
    };
    private static final int CLIENT_ID_LENGTH = Integer.BYTES;
    private static final int PING_ID_LENGTH = Long.BYTES;
    private static final int HEADER_LENGTH = 1 + PING_ID_LENGTH + MAGIC.length + CLIENT_ID_LENGTH;

    /**
     * Build the raw Bedrock unconnected ping packet.
     *
     * @return the byte array containing the ping packet.
     */
    private byte[] buildPingRequest() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_LENGTH);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.put(PACKET_ID_UNCONNECTED_PING);
        buffer.putLong(ThreadLocalRandom.current().nextLong());
        buffer.put(MAGIC);
        buffer.putInt(ThreadLocalRandom.current().nextInt());
        return buffer.array();
    }

    /**
     * Parse a Bedrock ping response packet into a structured response object.
     *
     * @param packet the received UDP response packet.
     * @param latencyMillis the round-trip latency measured in milliseconds.
     * @return the parsed ping response.
     * @throws IllegalStateException if the response is malformed or unexpected.
     */
    private PingResponse parseResponse(DatagramPacket packet, long latencyMillis) {
        byte[] data = packet.getData();
        int length = packet.getLength();
        if (length < HEADER_LENGTH) {
            throw new IllegalStateException("Bedrock response too short");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, length).order(ByteOrder.BIG_ENDIAN);
        byte packetId = buffer.get();
        if (packetId != PACKET_ID_UNCONNECTED_PONG) {
            throw new IllegalStateException("Unexpected Bedrock response packet id: " + packetId);
        }

        long pingId = buffer.getLong();
        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        /*if (!Arrays.equals(magic, MAGIC)) {
            throw new IllegalStateException("Invalid Bedrock response magic");
        }*/

        int serverId = buffer.getInt();
        int payloadLength = length - buffer.position();
        if (payloadLength <= 0) {
            throw new IllegalStateException("Missing Bedrock status payload");
        }

        String rawResponse = new String(data, buffer.position(), payloadLength, StandardCharsets.UTF_8);
        String[] parts = rawResponse.split(";", -1);
        // Standard Bedrock format: edition;motd1;motd2;protocol;version;online;max;serverId;serverUniqueId;gameMode;gameModeId;portV4;portV6
        if (parts.length < 13) {
            throw new IllegalStateException("Invalid Bedrock status payload: insufficient parts for standard format: " + rawResponse);
        }

        String edition = parts[0];
        if (!edition.contains("MCPE")) {
            throw new IllegalStateException("Invalid Bedrock status payload: missing MCPE identifier: " + rawResponse);
        }

        String motd = parts[1];
        String motd2 = parts[7];
        int protocolVersion = parseInt(parts[2], "protocolVersion");
        String versionName = parts[3];
        int onlinePlayers = parseInt(parts[4], "onlinePlayers");
        int maxPlayers = parseInt(parts[5], "maxPlayers");
        // parts[7] is serverId (string), but we use the one from header
        String serverUniqueId = parts[6];
        List<String> playerList = Collections.emptyList();
        // Standard format does not include player list in ping response
        String gamemode = parts[8];
        byte gamemodeID = (byte) parseInt(parts[9], "gamemodeID");
        //discard 11&12&13.

        return new PingResponse(
                host.getHostAddress(),
                port,
                latencyMillis,
                protocolVersion,
                versionName,
                motd,
                motd2,
                onlinePlayers,
                maxPlayers,
                pingId,
                serverId,
                serverUniqueId,
                playerList,
                rawResponse,
                gamemode,
                gamemodeID);
    }

    private static int parseInt(String value, String fieldName) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Failed to parse " + fieldName + " from Bedrock response: " + value, e);
        }
    }

    /**
     * Call the {@link top.pigimag.pilf.McBedrockPinger#ping() ping()} async.
     * 
     * @return A CompletableFuture.
     * @see CompletableFuture
     * @see McBedrockPinger#ping()
     * @since 1.0
     */
    public CompletableFuture<PingResponse> asyncPing() {
        return CompletableFuture.supplyAsync(
            ()->{
                try {
                    return ping();
                } catch (IOException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            },
            Executors.newFixedThreadPool(4)
        );
    } 
}
