package com.dolphy.examples.ble;

import org.json.JSONArray;
import org.json.JSONObject;

public final class BleAnalyzer {
    public String analyze(String payload) {
        try {
            JSONObject source = new JSONObject(payload);
            JSONObject result = new JSONObject(payload);
            int rssi = source.optInt("rssi", -127);
            int txPower = source.optInt("txPower", 0);
            JSONArray services = source.optJSONArray("serviceUuids");
            JSONArray manufacturers = source.optJSONArray("manufacturerData");
            String raw = source.optString("raw", "");
            result.put("signalPercent", signalPercent(rssi));
            result.put("signalLabel", signalLabel(rssi));
            result.put("estimatedDistance", estimatedDistance(rssi, txPower));
            result.put("serviceCount", services == null ? 0 : services.length());
            result.put("manufacturerCount", manufacturers == null ? 0 : manufacturers.length());
            result.put("manufacturerName", manufacturerName(manufacturers));
            result.put("payloadBytes", raw.length() / 2);
            result.put("advertisementType", advertisementType(services, manufacturers));
            return result.toString();
        } catch (Throwable error) {
            return payload;
        }
    }

    private int signalPercent(int rssi) {
        if (rssi >= -45) return 100;
        if (rssi <= -100) return 0;
        return Math.max(0, Math.min(100, (int) Math.round((rssi + 100) * 100.0 / 55.0)));
    }

    private String signalLabel(int rssi) {
        if (rssi >= -55) return "Excellent";
        if (rssi >= -67) return "Good";
        if (rssi >= -78) return "Fair";
        if (rssi >= -90) return "Weak";
        return "Very weak";
    }

    private double estimatedDistance(int rssi, int txPower) {
        if (rssi == 0 || txPower == 0 || txPower == 127) return -1.0;
        double distance = Math.pow(10.0, (txPower - rssi) / 20.0);
        return Math.round(distance * 100.0) / 100.0;
    }

    private String manufacturerName(JSONArray manufacturers) {
        if (manufacturers == null || manufacturers.length() == 0) return "Unknown";
        int id = manufacturers.optJSONObject(0) == null ? -1 : manufacturers.optJSONObject(0).optInt("id", -1);
        if (id == 76) return "Apple";
        if (id == 224) return "Google";
        if (id == 6) return "Microsoft";
        if (id == 117) return "Samsung";
        if (id == 89) return "Nordic Semiconductor";
        return id < 0 ? "Unknown" : "Company ID " + id;
    }

    private String advertisementType(JSONArray services, JSONArray manufacturers) {
        if (manufacturers != null && manufacturers.length() > 0) return "Manufacturer advertisement";
        if (services != null && services.length() > 0) return "Service advertisement";
        return "Generic advertisement";
    }
}
