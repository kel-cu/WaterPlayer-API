package ru.kelcuprum.waterplayer.api.objects;

import com.google.gson.JsonObject;
import ru.kelcuprum.waterplayer.api.config.GsonHelper;

public class Objects {
    public static JsonObject INDEX = GsonHelper.parse("{\"message\":\"Hello, world!\",\"url\":\"https://waterplayer.ru\"}");
    public static JsonObject NOT_FOUND = GsonHelper.parse("{\"error\":{\"code\":404,\"codename\":\"Not found\",\"message\":\"Method not found\"}}");
    public static JsonObject INTERNAL_SERVER_ERROR = GsonHelper.parse("{\"error\":{\"code\":500,\"codename\":\"Internal Server Error\",\"message\":\"\"}}");
    public static JsonObject UNAUTHORIZED = GsonHelper.parse("{\"error\": {\"code\": 401,\"codename\": \"Unauthorized\",\"message\": \"You not authorized\"}}");
    public static JsonObject BAD_REQUEST = GsonHelper.parse("{\"error\": {\"code\": 400,\"codename\": \"Bad Request\",\"message\": \"The required arguments are missing!\"}}");
}
