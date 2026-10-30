package ar.com.biopus.abismocam;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Config {
    String sourceIdentifier = "";
    java.math.BigInteger sourceCounter = java.math.BigInteger.ONE;

    String sourceName() { return SourceIdentity.name(sourceIdentifier, sourceCounter); }

    void renewSource() { sourceCounter = sourceCounter.add(java.math.BigInteger.ONE); }
    String address = "/camara/disparo";
    boolean broadcast = false;
    String broadcastIp = "";
    int broadcastPort = 9000;
    boolean frontCamera = true;
    boolean invertVertical = false;
    int width = 540;
    int height = 960;
    int fps = 30;
    boolean ndiMulticast = false;
    int oscIntervalMs = 50;
    int countdownSeconds = 5;
    final List<Osc.Destination> destinations = new ArrayList<>();

    List<Osc.Destination> targets() {
        if (broadcast) {
            if (broadcastIp.isEmpty()) return Collections.emptyList();
            return Collections.singletonList(new Osc.Destination("Broadcast", broadcastIp, broadcastPort, true));
        }
        return new ArrayList<>(destinations);
    }

    static Config load(Context context) {
        Config config = new Config();
        String json = context.getSharedPreferences("config", Context.MODE_PRIVATE).getString("json", "{}");
        try {
            JSONObject obj = new JSONObject(json);
            try { config.sourceIdentifier = SourceIdentity.identifier(obj.optString("sourceIdentifier", "")); }
            catch (IllegalArgumentException invalidName) { config.sourceIdentifier = ""; }
            config.sourceCounter = SourceIdentity.counter(obj.optString("sourceCounter", "1"));
            config.ndiMulticast = obj.optBoolean("ndiMulticast", false);
            config.oscIntervalMs = OscRepeater.validInterval(obj.optInt("oscIntervalMs", 50));
            int countdown = obj.optInt("countdownSeconds", 5);
            config.countdownSeconds = countdown >= 0 && countdown <= 15 ? countdown : 5;
            config.address = obj.optString("address", config.address);
            Osc.validateAddress(config.address);
            config.broadcast = obj.optBoolean("broadcast", false);
            config.broadcastIp = obj.optString("broadcastIp", "");
            if (!config.broadcastIp.isEmpty()) Osc.ipv4(config.broadcastIp);
            config.broadcastPort = obj.optInt("broadcastPort", 9000);
            if (config.broadcastPort < 1 || config.broadcastPort > 65535) config.broadcastPort = 9000;
            // La primera actualización a la interfaz de selfie empieza con la frontal.
            config.frontCamera = obj.optInt("schemaVersion", 1) < 2 || obj.optBoolean("frontCamera", true);
            config.invertVertical = obj.optBoolean("invertVertical", false);
            if (obj.optInt("schemaVersion", 1) >= 3) {
                int width = obj.optInt("width", 540);
                if (width == 360 || width == 384 || width == 640) config.width = 360;
                else if (width == 720 || width == 768 || width == 1280) config.width = 720;
                else config.width = 540;
                config.height = config.width * 16 / 9;
                config.fps = obj.optInt("fps", 30) == 15 ? 15 : 30;
            }
            JSONArray list = obj.optJSONArray("destinations");
            if (list != null) for (int i = 0; i < list.length(); i++) {
                JSONObject d = list.getJSONObject(i);
                config.destinations.add(new Osc.Destination(d.getString("name"), d.getString("ip"), d.getInt("port"), d.getBoolean("enabled")));
            }
        } catch (JSONException | IllegalArgumentException invalid) { return new Config(); }
        return config;
    }

    void save(Context context) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("schemaVersion", 5).put("sourceName", sourceName())
                .put("sourceIdentifier", sourceIdentifier).put("sourceCounter", sourceCounter.toString())
                .put("address", address).put("broadcast", broadcast)
                .put("broadcastIp", broadcastIp).put("broadcastPort", broadcastPort)
                .put("frontCamera", frontCamera).put("invertVertical", invertVertical).put("width", width).put("fps", fps).put("ndiMulticast", ndiMulticast)
                .put("oscIntervalMs", oscIntervalMs).put("countdownSeconds", countdownSeconds);
            JSONArray list = new JSONArray();
            for (Osc.Destination d : destinations)
                list.put(new JSONObject().put("name", d.name).put("ip", d.ip).put("port", d.port).put("enabled", d.enabled));
            obj.put("destinations", list);
            context.getSharedPreferences("config", Context.MODE_PRIVATE).edit().putString("json", obj.toString()).apply();
        } catch (JSONException impossible) { throw new IllegalStateException(impossible); }
    }
}
