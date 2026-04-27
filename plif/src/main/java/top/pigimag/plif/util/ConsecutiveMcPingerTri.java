package top.pigimag.plif.util;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;

import top.pigimag.plif.BasePingResponse;

/**
 * A wrapper around {@link McPingerTri} that executes multiple consecutive pings.
 *
 * This class is useful for measuring average latency and comparing high/low
 * values when the server responds consistently.
 *
 * @since 0.1.0
 */
public class ConsecutiveMcPingerTri extends McPingerTri {
    private static final int DEFAULT_PING_COUNT = 10;
    private final int pingNum;
    private static final int DEFAULT_GAP = 3000;
    private final int gap;

    public ConsecutiveMcPingerTri(String host, int port) throws UnknownHostException {
        super(host, port);
        this.pingNum = DEFAULT_PING_COUNT;
        this.gap = DEFAULT_GAP;
    }

    public ConsecutiveMcPingerTri(InetAddress host, int port) throws UnknownHostException {
        super(host, port);
        this.pingNum = DEFAULT_PING_COUNT;
        this.gap = DEFAULT_GAP;
    }

    public ConsecutiveMcPingerTri(InetAddress host, int port, int num) throws UnknownHostException {
        super(host, port);
        if (num < 1) {
            throw new IllegalArgumentException("Ping count must be at least 1");
        }
        this.pingNum = num;
        this.gap = DEFAULT_GAP;
    }

    public ConsecutiveMcPingerTri(String host, int port, int num,int gap) throws UnknownHostException {
        super(host, port);
        if (num < 1) {
            throw new IllegalArgumentException("Ping count must be at least 1");
        }
        this.pingNum = num;
        this.gap = DEFAULT_GAP;
    }

    public ConsecutiveMcPingerTri(InetAddress host, int port, int num,int gap) throws UnknownHostException {
        super(host, port);
        if (num < 1) {
            throw new IllegalArgumentException("Ping count must be at least 1");
        }
        this.pingNum = num;
        this.gap = gap;
    }

    public ConsecutiveMcPingerTri(String host, int port, int num) throws UnknownHostException {
        super(host, port);
        if (num < 1) {
            throw new IllegalArgumentException("Ping count must be at least 1");
        }
        this.pingNum = num;
        this.gap = DEFAULT_GAP;
    }

    /**
     * Execute the configured number of pings consecutively and return the results.
     *
     * @param c Used to callback.
     * 
     * @return void
     * @throws IOException if any ping attempt fails.
     */
    public void pings(Function<Optional<BasePingResponse>,Void> c,boolean async) throws IOException {
        if (asnyc) {
            var t = new Thread(()->{
                for (int i = 0; i < pingNum; i++) {
                    try {
                        c.apply(Optional.of(ping()));
                        Thread.sleep(gap);
                    } catch (IOException e) {
                        c.apply(Optional.empty()); 
                    } catch (InterruptedException e){
                        Thread.currentThread().interrupt();
                    }
                }
            });
            t.setName("ConsecutiveMcPinger-pingsasync-"+t.threadId());
            t.setDaemon(true);
            t.start();
        }
        for (int i = 0; i < pingNum; i++) {
            c.apply(Optional.of(ping()));
            try {
                Thread.sleep(gap);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
