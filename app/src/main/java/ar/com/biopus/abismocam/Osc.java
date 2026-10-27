package ar.com.biopus.abismocam;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** OSC 1.0, un argumento int32, orden de bytes de red. Sin dependencia de Android. */
public final class Osc {
    private Osc() {}

    public static final class Destination {
        public final String name;
        public final String ip;
        public final int port;
        public final boolean enabled;

        public Destination(String name, String ip, int port, boolean enabled) {
            ipv4(ip);
            if (port < 1 || port > 65535) throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
            this.name = name.trim().isEmpty() ? ip : name.trim();
            this.ip = ip;
            this.port = port;
            this.enabled = enabled;
        }
    }

    public static byte[] ipv4(String text) {
        String[] parts = text.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Ingresá una dirección IPv4, por ejemplo 192.168.1.20.");
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (!parts[i].matches("[0-9]{1,3}")) throw new IllegalArgumentException("La dirección IPv4 no es válida.");
            int value = Integer.parseInt(parts[i]);
            if (value > 255) throw new IllegalArgumentException("La dirección IPv4 no es válida.");
            result[i] = (byte)value;
        }
        return result;
    }

    public static void validateAddress(String address) {
        // MVP: una dirección literal ASCII, no patrones OSC ni espacios.
        if (!address.matches("/(?:[A-Za-z0-9_.-]+/)*[A-Za-z0-9_.-]+") || address.length() > 200)
            throw new IllegalArgumentException("Usá una ruta como /camara/disparo, sin espacios ni comodines.");
    }

    public static byte[] encode(String address, int value) {
        validateAddress(address);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            writeString(output, address);
            writeString(output, ",i");
            output.writeInt(value);
            return bytes.toByteArray();
        } catch (IOException impossible) { throw new AssertionError(impossible); }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] text = value.getBytes(StandardCharsets.US_ASCII);
        output.write(text);
        int zeros = 4 - (text.length % 4);
        for (int i = 0; i < zeros; i++) output.writeByte(0);
    }

    public static final class Result {
        public final int sent;
        public final List<String> errors;
        Result(int sent, List<String> errors) { this.sent = sent; this.errors = errors; }
    }

    public static Result send(List<Destination> destinations, String address, int value) {
        byte[] packet = encode(address, value);
        int sent = 0;
        List<String> errors = new ArrayList<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            try { socket.setTrafficClass(0xB8); } catch (Exception ignored) { }
            for (Destination destination : destinations) {
                if (!destination.enabled) continue;
                try {
                    InetAddress ip = InetAddress.getByAddress(ipv4(destination.ip));
                    socket.send(new DatagramPacket(packet, packet.length, ip, destination.port));
                    sent++;
                } catch (IOException | IllegalArgumentException error) {
                    errors.add(destination.name + ": " + error.getMessage());
                }
            }
        } catch (IOException error) { errors.add(error.getMessage()); }
        return new Result(sent, errors);
    }

    public interface Sink { void send(List<Destination> destinations, String address, int value); }

    /** El 0 siempre usa exactamente los destinos y la ruta del 1, incluso si cambia la configuración. */
    public static final class Hold {
        private final Sink sink;
        private List<Destination> active;
        private String address;
        public Hold(Sink sink) { this.sink = sink; }
        public synchronized boolean press(List<Destination> destinations, String address) {
            if (active != null) return false;
            validateAddress(address);
            List<Destination> selected = new ArrayList<>();
            for (Destination d : destinations) if (d.enabled) selected.add(d);
            if (selected.isEmpty()) return false;
            this.active = Collections.unmodifiableList(selected);
            this.address = address;
            sink.send(active, address, 1);
            return true;
        }
        public synchronized void release() {
            if (active == null) return;
            sink.send(active, address, 0);
            active = null;
        }
        public synchronized boolean isPressed() { return active != null; }
    }
}
