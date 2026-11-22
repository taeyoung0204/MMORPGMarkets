package market.web;

import java.io.IOException;
import java.util.Map;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.Strictness;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public final class Json {
    private static final Gson GSON = new GsonBuilder()
            .serializeNulls().setStrictness(Strictness.STRICT).create();

    private Json() {
    }

    public static JsonObject read(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null
                || !contentType.split(";", 2)[0].trim().equalsIgnoreCase("application/json")) {
            throw new IllegalArgumentException("Content-Type은 application/json이어야 합니다.");
        }
        try {
            JsonObject body = GSON.fromJson(request.getReader(), JsonObject.class);
            if (body != null) {
                return body;
            }
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("올바른 JSON 객체가 필요합니다.");
        }
        throw new IllegalArgumentException("JSON 본문이 필요합니다.");
    }

    public static String requiredString(JsonObject body, String field) {
        JsonElement value = body.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isEmpty()) {
            throw new IllegalArgumentException(field + "는 비어 있지 않은 문자열이어야 합니다.");
        }
        return value.getAsString();
    }

    public static void send(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        GSON.toJson(body, response.getWriter());
    }

    public static void error(HttpServletResponse response, int status, String message) throws IOException {
        send(response, status, Map.of("error", message));
    }
}
