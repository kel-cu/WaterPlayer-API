package ru.kelcuprum.waterplayer.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import express.http.request.Request;
import express.http.response.Response;
import express.utils.Status;
import ru.kelcuprum.waterplayer.api.WaterPlayerAPI;
import ru.kelcuprum.waterplayer.api.Web;
import ru.kelcuprum.waterplayer.api.objects.Objects;

import java.net.URI;
import java.net.http.HttpRequest;
import java.util.HashMap;

public class User {
    protected static HashMap<String, JsonObject> users = new HashMap<>();
    public static HashMap<String, Boolean> moderators = new HashMap<>();
    public static String MOJANG_API = "https://api.minecraftservices.com/minecraft/profile";
    public static void loadModerators(){
        JsonArray jsonArray = WaterPlayerAPI.config.getJsonArray("moderators", new JsonArray());
        for(JsonElement element : jsonArray)
            moderators.put(element.getAsString(), true);
    }
    public static void getUser(Request req, Response res){
        if(req.getHeader("Authorization").isEmpty()){
            res.setStatus(Status._401);
            res.json(Objects.UNAUTHORIZED);
            return;
        }
        JsonObject user = User.getUser(req.getHeader("Authorization").get(0));
        if(user == null){
            res.setStatus(Status._401);
            res.json(Objects.UNAUTHORIZED);
        } else res.json(user.toString());
    }
    public static void verify(Request req, Response res){
        if(req.getHeader("Authorization").isEmpty() && WaterPlayerAPI.config.getBoolean("VERIFY", true)){
            res.setStatus(Status._401);
            res.json(Objects.UNAUTHORIZED);
            return;
        }
        JsonObject user = User.getUser(req.getHeader("Authorization").get(0));
        if(user == null && WaterPlayerAPI.config.getBoolean("VERIFY", true)){
            res.setStatus(Status._401);
            res.json(Objects.UNAUTHORIZED);
        } else res.json("{\"message\": \"ok\", \"ok\": true}");
    }
    public static JsonObject getUser(String token){
        if(users.containsKey(token)) return users.get(token);
        JsonObject user = null;
        try{
             JsonObject object = Web.getJsonObject(HttpRequest.newBuilder(URI.create(MOJANG_API)).header("Authorization", token));
             if(object.has("id")){
                user = new JsonObject();
                user.addProperty("name", object.get("name").getAsString());
                user.addProperty("moderator", moderators.getOrDefault(object.get("name").getAsString(), false));
                WaterPlayerAPI.log(String.format("| Пользователь: %s, Модератор: %s", object.get("name").getAsString(), moderators.getOrDefault(object.get("name").getAsString(), false)));
             }
        } catch (Exception ignored){}
        users.put(token, user);
        return user;
    }
}
