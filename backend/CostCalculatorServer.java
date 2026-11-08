
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.Headers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CostCalculatorServer {

    // Construction rates
    private static final double BASE_RATE_GROUND = 1200;
    private static final double BASE_RATE_UPPER = 1500;

    // Material rates
    private static final double BRICK_RATE = 6;
    private static final double CEMENT_RATE = 400;
    private static final double SAND_RATE = 4000;

    public static void main(String[] args) throws IOException {

        // Read cloud hosting port, or use 8081 locally
        int port = Integer.parseInt(
            System.getenv().getOrDefault("PORT", "8081")
        );

        // Listen on all network interfaces
        HttpServer server = HttpServer.create(
            new InetSocketAddress("0.0.0.0", port), 0
        );

        // API endpoints
        server.createContext("/calculate",
            CostCalculatorServer::handleCalculate);

        server.createContext("/signup",
            CostCalculatorServer::handleSignup);

        server.createContext("/login",
            CostCalculatorServer::handleLogin);

        server.setExecutor(null);
        server.start();

        System.out.println("House Price Estimator server started on port " + port);
    }

    // Common CORS configuration
    private static void setCors(HttpExchange ex) {

        Headers headers = ex.getResponseHeaders();

        headers.set("Access-Control-Allow-Origin", "*");
        headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
        headers.set("Access-Control-Allow-Headers", "Content-Type");
        headers.set("Content-Type", "application/json");
    }

    // Handle OPTIONS requests
    private static boolean handleOptions(HttpExchange ex)
            throws IOException {

        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return true;
        }

        return false;
    }

    // Send JSON response
    private static void sendJson(
            HttpExchange ex, int status, String json)
            throws IOException {

        byte[] response = json.getBytes(StandardCharsets.UTF_8);

        ex.sendResponseHeaders(status, response.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(response);
        }
    }

    // Signup API
    private static void handleSignup(HttpExchange ex)
            throws IOException {

        setCors(ex);

        if (handleOptions(ex)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = readRequest(ex);

            String email = getString(body, "email", "");
            String password = getString(body, "password", "");

            if (email.isEmpty() || password.isEmpty()) {
                sendJson(ex, 400,
                    "{\"error\":\"Email and password required\"}");
                return;
            }

            sendJson(ex, 200,
                "{\"success\":true,\"message\":\"Signup successful\"}");

        } catch (Exception e) {
            sendJson(ex, 400,
                "{\"error\":\"Signup failed\"}");
        }
    }

    // Login API
    private static void handleLogin(HttpExchange ex)
            throws IOException {

        setCors(ex);

        if (handleOptions(ex)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = readRequest(ex);

            String email = getString(body, "email", "");
            String password = getString(body, "password", "");

            if (email.isEmpty() || password.isEmpty()) {
                sendJson(ex, 400,
                    "{\"error\":\"Email and password required\"}");
                return;
            }

            // Demo response only; this does not authenticate stored users.
            sendJson(ex, 200,
                "{\"success\":true,\"email\":\""
                + escapeJson(email)
                + "\",\"message\":\"Login successful\"}");

        } catch (Exception e) {
            sendJson(ex, 400,
                "{\"error\":\"Login failed\"}");
        }
    }

    // House price calculation API
    private static void handleCalculate(HttpExchange ex)
            throws IOException {

        setCors(ex);

        if (handleOptions(ex)) return;

        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            String body = readRequest(ex);

            // Read user inputs
            double landSize = getDouble(body, "landSize", 0);
            int numFloors = getInt(body, "numFloors", 1);
            int bedrooms = getInt(body, "bedrooms", 0);
            int kitchens = getInt(body, "kitchens", 0);
            int bathrooms = getInt(body, "bathrooms", 0);

            boolean elevation = getBoolean(body, "elevation");

            // Validate inputs
            if (landSize <= 0 || numFloors <= 0
                    || bedrooms < 0 || kitchens < 0
                    || bathrooms < 0) {

                sendJson(ex, 400,
                    "{\"error\":\"Please enter valid house details\"}");
                return;
            }

            // Selected materials
            boolean useBricks =
                arrayContains(body, "materials", "Bricks");

            boolean useCement =
                arrayContains(body, "materials", "Cement");

            boolean useSand =
                arrayContains(body, "materials", "Sand");

            // Convert cents to square feet
            double landAreaSqFt = landSize * 435.6;

            // Calculate construction cost
            double baseCost = 0;

            baseCost += landAreaSqFt * BASE_RATE_GROUND;

            if (numFloors > 1) {
                baseCost += (numFloors - 1)
                    * landAreaSqFt * BASE_RATE_UPPER;
            }

            // Room costs
            baseCost += bedrooms * 25000;
            baseCost += kitchens * 40000;
            baseCost += bathrooms * 30000;

            // Material costs
            double materialCost = 0;

            if (useBricks) {
                double bricksQty = landSize * numFloors * 1000;
                materialCost += bricksQty * BRICK_RATE;
            }

            if (useCement) {
                double cementBags = numFloors * 200;
                materialCost += cementBags * CEMENT_RATE;
            }

            if (useSand) {
                double sandTrucks = numFloors * 3;
                materialCost += sandTrucks * SAND_RATE;
            }

            // Optional elevation cost
            double elevationCost = elevation ? 100000 : 0;

            // Total estimated price
            double total = Math.round(
                baseCost + materialCost + elevationCost
            );

            // JSON response
            String json = "{"
                + "\"price\":" + (long) total + ","
                + "\"baseCost\":" + Math.round(baseCost) + ","
                + "\"materialCost\":" + Math.round(materialCost) + ","
                + "\"elevationCost\":" + Math.round(elevationCost)
                + "}";

            sendJson(ex, 200, json);

        } catch (Exception e) {
            e.printStackTrace();

            sendJson(ex, 400,
                "{\"error\":\"Unable to calculate house price\"}");
        }
    }

    // Read request body
    private static String readRequest(HttpExchange ex)
            throws IOException {

        try (InputStream in = ex.getRequestBody()) {
            return new String(
                in.readAllBytes(), StandardCharsets.UTF_8
            );
        }
    }

    // Extract integer
    private static int getInt(
            String json, String key, int defaultValue) {

        Matcher m = Pattern.compile(
            "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+)"
        ).matcher(json);

        return m.find()
            ? Integer.parseInt(m.group(1))
            : defaultValue;
    }

    // Extract decimal number
    private static double getDouble(
            String json, String key, double defaultValue) {

        Matcher m = Pattern.compile(
            "\"" + Pattern.quote(key)
            + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)"
        ).matcher(json);

        return m.find()
            ? Double.parseDouble(m.group(1))
            : defaultValue;
    }

    // Extract string
    private static String getString(
            String json, String key, String defaultValue) {

        Matcher m = Pattern.compile(
            "\"" + Pattern.quote(key)
            + "\"\\s*:\\s*\"([^\"]*)\""
        ).matcher(json);

        return m.find() ? m.group(1) : defaultValue;
    }

    // Extract boolean
    private static boolean getBoolean(
            String json, String key) {

        Matcher m = Pattern.compile(
            "\"" + Pattern.quote(key)
            + "\"\\s*:\\s*(true|false)"
        ).matcher(json);

        return m.find() && "true".equals(m.group(1));
    }

    // Check selected material
    private static boolean arrayContains(
            String json, String arrayKey, String word) {

        Matcher m = Pattern.compile(
            "\"" + Pattern.quote(arrayKey)
            + "\"\\s*:\\s*\\[(.*?)\\]",
            Pattern.DOTALL
        ).matcher(json);

        if (m.find()) {
            String inside = m.group(1);

            Pattern p = Pattern.compile(
                "\"" + Pattern.quote(word) + "\""
            );

            return p.matcher(inside).find();
        }

        return false;
    }

    // Escape special JSON characters
    private static String escapeJson(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r");
    }
}
