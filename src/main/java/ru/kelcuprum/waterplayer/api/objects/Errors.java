package ru.kelcuprum.waterplayer.api.objects;

import com.google.gson.JsonObject;
import ru.kelcuprum.waterplayer.api.config.GsonHelper;

public class Errors {
    public static JsonObject INDEX = GsonHelper.parse("{\"message\":\"Hello, world!\",\"url\":\"https://waterplayer.ru\"}");
    public static JsonObject NOT_FOUND = GsonHelper.parse("{\"error\":{\"code\":404,\"codename\":\"Not found\",\"message\":\"Method not found\"}}");
}
