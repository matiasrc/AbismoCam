package ar.com.biopus.abismocam;

import org.junit.Test;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

public class OscRepeaterTest {
    @Test public void immediateCapturePulseSendsFiveOnesThenFiveZerosEvenAfterClose() throws Exception {
        for (int interval : new int[] {10, 25, 50, 100}) {
            try (DatagramSocket receiver = new DatagramSocket(0, InetAddress.getLoopbackAddress());
                 OscRepeater repeater = new OscRepeater((targets, path, value) -> Osc.send(targets, path, value))) {
                receiver.setSoTimeout(3000);
                long start = System.nanoTime();
                repeater.sendPulse(Collections.singletonList(new Osc.Destination("QA", "127.0.0.1", receiver.getLocalPort(), true)), "/test", interval);
                repeater.close();
                for (int i = 0; i < 20; i++) {
                    assertEquals(i < 10 ? 1 : 0, receive(receiver));
                    if (i == 10) assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 10L * interval - 15);
                }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= 19L * interval - 15);
                receiver.setSoTimeout(100);
                assertThrows(java.net.SocketTimeoutException.class, () -> receive(receiver));
            }
        }
    }
    @Test public void fiveUdpPacketsForBothValuesAtEveryIntervalAndEveryEnabledTarget() throws Exception {
        for (int interval : new int[] {10, 25, 50, 100}) {
            try (DatagramSocket first = new DatagramSocket(0, InetAddress.getLoopbackAddress());
                 DatagramSocket second = new DatagramSocket(0, InetAddress.getLoopbackAddress());
                 DatagramSocket disabled = new DatagramSocket(0, InetAddress.getLoopbackAddress());
                 OscRepeater repeater = new OscRepeater((targets, path, value) -> Osc.send(targets, path, value))) {
                first.setSoTimeout(2500); second.setSoTimeout(2500); disabled.setSoTimeout(40);
                for (int value : new int[] {1, 0}) {
                    long start = System.nanoTime();
                    repeater.send(Arrays.asList(
                        new Osc.Destination("A", "127.0.0.1", first.getLocalPort(), true),
                        new Osc.Destination("B", "127.0.0.1", second.getLocalPort(), true),
                        new Osc.Destination("Off", "127.0.0.1", disabled.getLocalPort(), false)), "/test", value, interval);
                    for (int i = 0; i < 10; i++) {
                        assertEquals(value, receive(first)); assertEquals(value, receive(second));
                    }
                    long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
                    assertTrue("Ráfaga demasiado corta: " + elapsed, elapsed >= 9L * interval - 15);
                    first.setSoTimeout(40); second.setSoTimeout(40);
                    assertThrows(java.net.SocketTimeoutException.class, () -> receive(first));
                    assertThrows(java.net.SocketTimeoutException.class, () -> receive(second));
                    assertThrows(java.net.SocketTimeoutException.class, () -> receive(disabled));
                    first.setSoTimeout(2500); second.setSoTimeout(2500);
                }
            }
        }
    }

    @Test public void newZeroCancelsOldOnesAndFinishesAfterClose() throws Exception {
        LinkedBlockingQueue<Integer> received = new LinkedBlockingQueue<>();
        CountDownLatch started = new CountDownLatch(1), unblock = new CountDownLatch(1);
        try (OscRepeater repeater = new OscRepeater((targets, path, value) -> {
            if (value == 1) {
                started.countDown();
                try { unblock.await(2, TimeUnit.SECONDS); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            received.add(value);
        })) {
            repeater.send(Collections.emptyList(), "/test", 1, 100);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            repeater.send(Collections.emptyList(), "/test", 0, 25);
            repeater.close();
            unblock.countDown();
            assertEquals(Integer.valueOf(1), received.poll(2, TimeUnit.SECONDS));
            for (int i = 0; i < 10; i++) assertEquals(Integer.valueOf(0), received.poll(2, TimeUnit.SECONDS));
            assertNull(received.poll(450, TimeUnit.MILLISECONDS));
        } finally { unblock.countDown(); }
    }

    private static int receive(DatagramSocket socket) throws Exception {
        DatagramPacket packet = new DatagramPacket(new byte[128], 128);
        socket.receive(packet);
        return ByteBuffer.wrap(packet.getData(), packet.getLength() - 4, 4).getInt();
    }
}
