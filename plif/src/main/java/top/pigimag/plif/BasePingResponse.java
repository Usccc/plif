package top.pigimag.plif;

import java.io.Serializable;

/**
 * Base class for ping responses, containing common fields.
 *
 * @since 1.0
 */
public abstract class BasePingResponse implements Serializable {
    private static final long serialVersionUID = 1776586437L;

    protected final String host;
    protected final int port;
    protected final long latencyMillis;
    protected final int protocolVersion;
    protected final String versionName;
    protected final int onlinePlayers;
    protected final int maxPlayers;
    protected final String rawResponse;

    protected BasePingResponse(
            String host,
            int port,
            long latencyMillis,
            int protocolVersion,
            String versionName,
            int onlinePlayers,
            int maxPlayers,
            String rawResponse) {
        this.host = host;
        this.port = port;
        this.latencyMillis = latencyMillis;
        this.protocolVersion = protocolVersion;
        this.versionName = versionName;
        this.onlinePlayers = onlinePlayers;
        this.maxPlayers = maxPlayers;
        this.rawResponse = rawResponse;
    }

    /**
     * Get the target host used for the ping request.
     *
     * @return the host name or IP address.
     */
    public String getHost() {
        return host;
    }

    /**
     * Get the target port used for the ping request.
     *
     * @return the port number.
     */
    public int getPort() {
        return port;
    }

    /**
     * Get the measured ping latency for this response.
     *
     * @return the latency in milliseconds.
     */
    public long getLatencyMillis() {
        return latencyMillis;
    }

    /**
     * Get the protocol version reported by the server.
     *
     * @return the server protocol version number.
     */
    public int getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Get the server version name reported by the ping response.
     *
     * @return the version name string.
     */
    public String getVersionName() {
        return versionName;
    }

    /**
     * Get the number of players currently online.
     *
     * @return the number of online players.
     */
    public int getOnlinePlayers() {
        return onlinePlayers;
    }

    /**
     * Get the maximum number of players allowed by the server.
     *
     * @return the maximum player count.
     */
    public int getMaxPlayers() {
        return maxPlayers;
    }

    /**
     * Get the raw server response payload that was received during the ping.
     *
     * @return the raw response string from the server.
     */
    public String getRawResponse() {
        return rawResponse;
    }

    @Override
    public abstract String toString();
}