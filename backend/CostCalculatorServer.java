import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CostCalculatorServer {

    // Normal “market” rates
    private static final double BASE_RATE_GROUND = 1200; // ₹/sqft ground
    private static final double BASE_RATE_UPPER  = 1500; // ₹/sqft upper floors
    private static final double BRICK_RATE  = 6;          // ₹/brick
    private static final double CEMENT_RATE = 400;        // ₹/bag
    private static final double SAND_RATE   = 4000;       // ₹/truck

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8081), 0);

        // CORS-friendly endpoint
        server.createContext("/calculate", CostCalculatorServer::handleCalculate);

        System.out.println("Server started at http://localhost:8081");
        server.start();
    }

    private static void handleCalculate(HttpExchange ex) throws IOException {
        // CORS
        Headers h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "POST, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(405, -1);
            return;
        }

        // Read JSON body
        InputStream in = ex.getRequestBody();
        String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);

        try {
            // ----- parse inputs from JSON (simple regex parsing) -----
            double landSize = getDouble(body, "landSize", 0);     // in cents
            int numFloors   = getInt(body, "numFloors", 1);
            int bedrooms    = getInt(body, "bedrooms", 0);
            int kitchens    = getInt(body, "kitchens", 0);
            int bathrooms   = getInt(body, "bathrooms", 0);
            boolean elevation = getBoolean(body, "elevation");

            boolean useBricks = arrayContains(body, "materials", "Bricks");
            boolean useCement = arrayContains(body, "materials", "Cement");
            boolean useSand   = arrayContains(body, "materials", "Sand");

            // ----- calculation -----
            double landAreaSqFt = landSize * 435.6; // 1 cent ≈ 435.6 sqft

            double baseCost = 0;
            if (numFloors >= 1) baseCost += landAreaSqFt * BASE_RATE_GROUND;
            if (numFloors > 1)  baseCost += (numFloors - 1) * landAreaSqFt * BASE_RATE_UPPER;

            // (optional) tiny addons for room counts – feel free to tweak or remove
            baseCost += bedrooms * 25000 + kitchens * 40000 + bathrooms * 30000;

            double materialCost = 0;
            if (useBricks) {
                double bricksQty = landSize * numFloors * 1000; // rough bricks estimate
                materialCost += bricksQty * BRICK_RATE;
            }
            if (useCement) {
                double cementBags = numFloors * 200; // rough per-floor bags
                materialCost += cementBags * CEMENT_RATE;
            }
            if (useSand) {
                double sandTrucks = numFloors * 3; // rough per-floor trucks
                materialCost += sandTrucks * SAND_RATE;
            }

            double elevationCost = elevation ? 100000 : 0;

            double total = Math.round(baseCost + materialCost + elevationCost);

            String json = "{"
                    + "\"price\":" + (long) total + ","
                    + "\"baseCost\":" + (long) Math.round(baseCost) + ","
                    + "\"materialCost\":" + (long) Math.round(materialCost) + ","
                    + "\"elevationCost\":" + (long) Math.round(elevationCost)
                    + "}";

            byte[] out = json.getBytes(StandardCharsets.UTF_8);
            h.add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        } catch (Exception e) {
            String err = "{\"error\":\"" + e.getMessage().replace("\"","'") + "\"}";
            byte[] out = err.getBytes(StandardCharsets.UTF_8);
            h.add("Content-Type", "application/json");
            ex.sendResponseHeaders(400, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        }
    }

    // ---------- tiny JSON helpers (regex-based; good enough for this demo) ----------
    private static int getInt(String json, String key, int defVal) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)").matcher(json);
        return m.find() ? Integer.parseInt(m.group(1)) : defVal;
    }
    private static double getDouble(String json, String key, double defVal) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)").matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : defVal;
    }
    private static boolean getBoolean(String json, String key) {
        Matcher m = Pattern.compile("\"" + key + "\"\\s*:\\s*(true|false)").matcher(json);
        return m.find() && "true".equals(m.group(1));
    }
    private static boolean arrayContains(String json, String arrayKey, String word) {
        // Looks for: "materials":[ ... "Bricks" ... ]
        Matcher m = Pattern.compile("\"" + arrayKey + "\"\\s*:\\s*\\[(.*?)\\]", Pattern.DOTALL).matcher(json);
        if (m.find()) {
            String inside = m.group(1);
            Pattern p = Pattern.compile("\"" + Pattern.quote(word) + "\"");
            return p.matcher(inside).find();
        }
        return false;
    }
}
