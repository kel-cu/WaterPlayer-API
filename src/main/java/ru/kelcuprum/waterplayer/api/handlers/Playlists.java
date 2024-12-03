package ru.kelcuprum.waterplayer.api.handlers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import express.http.request.Request;
import express.http.response.Response;
import express.utils.Status;
import org.slf4j.event.Level;
import ru.kelcuprum.waterplayer.api.WaterPlayerAPI;
import ru.kelcuprum.waterplayer.api.config.GsonHelper;
import ru.kelcuprum.waterplayer.api.objects.Objects;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Playlists {
    public static File file = new File("playlists");

    public static void checkFolder() {
        try {
            if (!file.exists()) Files.createDirectory(file.toPath());
        } catch (Exception ex) {
            WaterPlayerAPI.log(ex, Level.ERROR);
        }
    }

    public static void getPlaylist(Request req, Response res) {
        checkFolder();
        Path path = file.toPath().resolve(req.getParam("id") + ".json");
        try {
            if (path.toFile().exists())
                res.send(Files.readString(path));
            else res.send(Objects.NOT_FOUND.toString());
        } catch (Exception ex) {
            WaterPlayerAPI.log(ex, Level.ERROR);
            res.setStatus(Status._500);
            res.send(WaterPlayerAPI.getErrorObject(ex).toString());
        }
    }

    public static void deletePlaylist(Request req, Response res) {
        if (req.getHeader("Authorization").isEmpty()) {
            res.setStatus(Status._401);
            res.send(Objects.UNAUTHORIZED.toString());
            return;
        }
        JsonObject user = User.getUser(req.getHeader("Authorization").get(0));
        Path path = file.toPath().resolve(req.getParam("id") + ".json");
        try {
            if (path.toFile().exists()) {
                JsonObject playlist = GsonHelper.parse(Files.readString(path));
                if (playlist.get("data").getAsJsonObject().get("author") == user.get("name") || user.get("moderator").getAsBoolean()) {
                    boolean state = path.toFile().delete();
                    res.send("{ \"id\": \"" + req.getParam("id") + "\",\"status\": " + state + "}");
                } else {
                    res.setStatus(Status._403);
                    res.send("{\"error\": {\"code\": 403,\"codename\": \"Forbidden\",\"message\": \"You not moderator or author\"},\"status\": false}");
                }
            } else throw new RuntimeException("File not exists");
        } catch (Exception ex) {
            res.setStatus(Status._500);
            res.send(WaterPlayerAPI.getErrorObject(ex).toString());
        }

    }

    public static void uploadPlaylist(Request req, Response res) {
        checkFolder();
        if (!WaterPlayerAPI.config.getBoolean("allow_publish", true)) {
            res.setStatus(Status._403);
            res.send("{\"error\":{\"code\": 403, \"codename\": \"Forbidden\", \"message\":\"You can't post playlists at this time!\"}}");
        }
        if (req.getHeader("Authorization").isEmpty() && WaterPlayerAPI.config.getBoolean("VERIFY", true)) {
            res.setStatus(Status._401);
            res.send(Objects.UNAUTHORIZED.toString());
            return;
        }
        try {
            InputStream IS = req.getBody();
            String isString = IS == null ? "{}" : isToString(IS);
            if (IS == null || !GsonHelper.parse(isString).has("id")) {
                res.setStatus(Status._400);
                res.send(Objects.BAD_REQUEST.toString());
                return;
            }
            boolean allowPublish = !WaterPlayerAPI.config.getBoolean("VERIFY", true);
            if (!allowPublish) {
                JsonObject user = User.getUser(req.getHeader("Authorization").get(0));
                if (user == null) {
                    res.setStatus(Status._401);
                    res.send(Objects.UNAUTHORIZED.toString());
                    return;
                }
            }
            JsonObject data = GsonHelper.parse(isString);
            JsonObject playlist = data.has("data") ? data.getAsJsonObject("data") : new JsonObject();

            if (playlist.get("title").getAsString().equalsIgnoreCase("example title")) {
                res.setStatus(Status._400);
                res.send("{\"error\": {\"code\": 400,\"codename\": \"Bad Request\",\"message\": \"Invalid playlist title\"}}");
            } else if (java.util.Objects.equals(playlist.getAsJsonArray("urls").get(0).getAsString(), "https://www.youtube.com/watch?v=2bjBl-nX1oc") || (!playlist.get("public").getAsBoolean() && playlist.getAsJsonArray("urls").isEmpty()) || (playlist.get("public").getAsBoolean() && playlist.getAsJsonArray("urls").size() < 3)) {
                res.setStatus(Status._400);
                res.send("{\"error\": {\"code\": 400,\"codename\": \"Bad Request\",\"message\": \"Invalid playlist urls\"}}");
            } else if (playlist.get("public").getAsBoolean() && !playlist.has("icon")) {
                res.setStatus(Status._400);
                res.send("{\"error\": {\"code\": 400,\"codename\": \"Bad Request\",\"message\": \"Invalid playlist icon\"}}");
            }

            String id = WaterPlayerAPI.makeIDPlaylist(7);
            for (File file : file.listFiles()) {
                JsonObject filePlaylist = GsonHelper.parse(Files.readString(file.toPath()));
                if (playlist.toString().equals(filePlaylist.get("data").getAsJsonObject().toString())) {
                    id = filePlaylist.get("url").getAsString();
                    break;
                }
            }

            JsonObject pubPlaylist = new JsonObject();
            pubPlaylist.addProperty("id", data.get("id").getAsString());
            pubPlaylist.add("data", playlist);
            pubPlaylist.addProperty("url", id);
            WaterPlayerAPI.log(String.format("| Опубликован плейлист \"%s\" под ID \"%S\"", playlist.get("title").getAsString(), id));
            Files.writeString(file.toPath().resolve(id + ".json"), pubPlaylist.toString(), StandardCharsets.UTF_8);
            res.send(pubPlaylist.toString());
        } catch (Exception ex) {
            WaterPlayerAPI.log(ex, Level.ERROR);
            res.setStatus(Status._500);
            res.send(WaterPlayerAPI.getErrorObject(ex).toString());
        }
    }

    public static String isToString(InputStream is) throws IOException {
        byte[] requestBodyBytes = is.readAllBytes();
        WaterPlayerAPI.log("IS = " + new String(requestBodyBytes));
        return new String(requestBodyBytes);
    }

    public static void search(Request req, Response res) {
        String query = req.getQuery("query") == null ? "" : req.getQuery("query");
        boolean helloModerator = false;
        if (!req.getHeader("Authorization").isEmpty()) {
            JsonObject user = User.getUser(req.getHeader("Authorization").get(0));
            if (user != null) helloModerator = user.get("moderator").getAsBoolean();
        }
        int total = 0;
        JsonArray results = new JsonArray();
        for (File file : file.listFiles()) {
            try {
                JsonObject playlist = GsonHelper.parse(Files.readString(file.toPath()));
                if ((playlist.get("id").getAsString().toLowerCase().contains(query) || playlist.get("url").getAsString().toLowerCase().contains(query)
                        || playlist.get("data").getAsJsonObject().get("title").getAsString().toLowerCase().contains(query)
                        || playlist.get("data").getAsJsonObject().get("author").getAsString().toLowerCase().contains(query)) &&
                        ((!playlist.get("data").getAsJsonObject().has("public") || playlist.get("data").getAsJsonObject().get("public").getAsBoolean())
                                || helloModerator)) {
                    total++;
                    results.add(playlist);
                }
            } catch (Exception ex) {
                WaterPlayerAPI.log(ex, Level.DEBUG);
            }
        }
        JsonObject resp = new JsonObject();
        resp.addProperty("total", total);
        resp.add("results", results);
        res.send(resp.toString());

    }
}
