package ar.com.biopus.abismocam;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Diez datagramas por estado. Un nuevo estado cancela las repeticiones anteriores. */
final class OscRepeater implements AutoCloseable {
    static final int COUNT = 10;
    private final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    private final List<ScheduledFuture<?>> pending = new ArrayList<>();
    private final Osc.Sink sink;

    OscRepeater(Osc.Sink sink) {
        this.sink = sink;
        executor.setRemoveOnCancelPolicy(true);
        // Al destruir la actividad, completar el último 0 ya programado.
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(true);
    }

    static int validInterval(int interval) {
        return interval == 10 || interval == 25 || interval == 100 ? interval : 50;
    }

    synchronized void send(List<Osc.Destination> targets, String address, int value, int interval) {
        schedule(targets, address, value, interval, false);
    }

    /** Sin cuenta: 1 en 0..9 intervalos, 0 en 10..19, sin bloquear la captura. */
    synchronized void sendPulse(List<Osc.Destination> targets, String address, int interval) {
        schedule(targets, address, 1, interval, true);
    }

    private void schedule(List<Osc.Destination> targets, String address, int value, int interval, boolean pulse) {
        if (executor.isShutdown()) return;
        for (ScheduledFuture<?> task : pending) task.cancel(false);
        pending.clear();
        List<Osc.Destination> snapshot = new ArrayList<>(targets);
        int gap = validInterval(interval);
        for (int i = 0; i < (pulse ? COUNT * 2 : COUNT); i++) {
            int packetValue = pulse && i >= COUNT ? 0 : value;
            pending.add(executor.schedule(() -> sink.send(snapshot, address, packetValue),
                (long)i * gap, TimeUnit.MILLISECONDS));
        }
    }

    @Override public synchronized void close() { executor.shutdown(); }
}
