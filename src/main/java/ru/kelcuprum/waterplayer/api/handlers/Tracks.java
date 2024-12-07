package ru.kelcuprum.waterplayer.api.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import express.http.request.Request;
import express.http.response.Response;
import express.utils.Status;
import ru.kelcuprum.waterplayer.api.WaterPlayerAPI;
import ru.kelcuprum.waterplayer.api.Web;
import ru.kelcuprum.waterplayer.api.config.Config;
import ru.kelcuprum.waterplayer.api.config.GsonHelper;
import org.apache.commons.codec.binary.Base64;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;

import static org.slf4j.event.Level.ERROR;
import static ru.kelcuprum.waterplayer.api.objects.Objects.BAD_REQUEST;
import static ru.kelcuprum.waterplayer.api.objects.Objects.NOT_FOUND;

public class Tracks {

    public static void info(Request req, Response res) {
        if(req.getQuery("author") == null){
            res.setStatus(Status._400);
            res.json(BAD_REQUEST);
            return;
        }
        JsonObject jsonObject = getInfo(req.getQuery("album"), req.getQuery("author"));
        if (jsonObject == null) res.json(NOT_FOUND);
        else res.json(jsonObject);
    }

    public static void artwork(Request req, Response res) {
        if(req.getQuery("author") == null){
            res.setStatus(Status._400);
            res.json(BAD_REQUEST);
            return;
        }
        JsonObject jsonObject = getInfo(req.getQuery("album"), req.getQuery("author"));
        if (jsonObject == null) res.json(NOT_FOUND);
        res.redirect(jsonObject.get(req.getQuery("album") == null ? "author" : "track").getAsJsonObject().get("artwork").getAsString());
    }
    protected static HashMap<String, JsonObject> cacheRequests = new LinkedHashMap<>();

    public static JsonObject getInfo(String album, String author) {
        String cacheID = (album == null ? author : String.format("%s-%s", author, album)).toLowerCase();
        if(cacheRequests.containsKey(cacheID)) {
            WaterPlayerAPI.log(String.format("| Запрос %s был закэширован.", cacheID));
            return cacheRequests.get(cacheID);
        } else {
            JsonObject finalResponse = null;
            JsonObject spotify = getSpotifyInfo(album, author);
            if (spotify == null) {
                JsonObject yandex = getYandexInfo(album, author);
                if (yandex == null) {
                    JsonObject youtube = getYouTubeInfo(album, author);
                    if(youtube != null) finalResponse = youtube;
                } else finalResponse = yandex;
            } else finalResponse = spotify;
            if(finalResponse != null) {
                cacheRequests.put(cacheID, finalResponse);
                saveCache();
                WaterPlayerAPI.log(String.format("| Запрос %s был добавлен в кэш. Текущее кол-во кэша: %s", cacheID, cacheRequests.size()));
            }
            return finalResponse;
        }
    }

    public static JsonObject getYandexInfo(String album, String author) {
        try {
            String queryForApi = album == null ? author : String.format("%s %s", author, album);
            String searchUrl = String.format("%s/search?text=%s&type=%s&page=0", WaterPlayerAPI.config.getJsonObject("yandex", new JsonObject()).get("apiEndpoint").getAsString(), uriEncode(queryForApi), album == null ? "artist" : "track");
            JsonObject jsonObject = Web.getJsonObject(HttpRequest.newBuilder(URI.create(searchUrl))
                    .header("Accept", "application/json")
                    .header("Authorization", "OAuth " + WaterPlayerAPI.config.getJsonObject("yandex", new JsonObject()).get("token").getAsString())
                    .header("User-Agent", "Yandex-Music-API")
                    .header("X-Yandex-Music-Client", "YandexMusicAndroid/24023621"));
            for (JsonElement element : jsonObject.getAsJsonObject("result").getAsJsonObject(album == null ? "artists" : "tracks").getAsJsonArray("results")) {
                JsonObject resp = new JsonObject();
                JsonObject json = element.getAsJsonObject();
                if (json.has("artists")) {
                    for (JsonElement art : json.getAsJsonArray("artists")) {
                        if (art.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(author)) {
                            if (json.get("title").getAsString().equalsIgnoreCase(album)) {
                                JsonObject track = new JsonObject();
                                track.addProperty("title", json.get("title").getAsString());
                                track.addProperty("artwork", json.has("ogImage") ? "https://"+json.get("ogImage").getAsString().replace("/%%", "/400x400") : null);
                                resp.add("track", track);
                                JsonObject artist = getInfo(null, author);
                                resp.add("author", artist.has("author") ? artist.get("author").getAsJsonObject() : null);
                                return resp;
                            }
                        }
                    }
                } else if (json.get("name").getAsString().equalsIgnoreCase(author)) {
                    JsonObject artist = new JsonObject();
                    artist.addProperty("name", json.get("name").getAsString());
                    artist.addProperty("artwork", json.has("ogImage") ? "https://"+json.get("ogImage").getAsString().replace("/%%", "/400x400") : null);
                    resp.add("author", artist);
                    return resp;
                }
            }
            return null;
        } catch (Exception ex) {
            WaterPlayerAPI.log(ex);
            return null;
        }
    }

    public static JsonObject getSpotifyInfo(String album, String author) {
        String authString = String.format("%s:%s", WaterPlayerAPI.config.getJsonObject("spotify", new JsonObject()).get("clientId").getAsString(), WaterPlayerAPI.config.getJsonObject("spotify", new JsonObject()).get("clientSecret").getAsString());
        String based = new String(Base64.encodeBase64(authString.getBytes(StandardCharsets.UTF_8)));
        try {
            JsonObject authData = GsonHelper.parse(Web.getString(HttpRequest.newBuilder(URI.create(WaterPlayerAPI.config.getJsonObject("spotify", new JsonObject()).get("authEndpoint").getAsString()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Basic " + based)
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
            ));
            String authToken = authData.get("access_token").getAsString();
            String queryForApi = album == null ? author : String.format("%s %s", author, album);
            String searchUrl = String.format("%s/search?q=%s&type=%s&limit=2", WaterPlayerAPI.config.getJsonObject("spotify", new JsonObject()).get("apiEndpoint").getAsString(), uriEncode(queryForApi), album == null ? "artist" : "track");
            JsonObject jsonObject = GsonHelper.parse(Web.getString(HttpRequest.newBuilder(URI.create(searchUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Authorization", "Bearer " + authToken)));
            if(jsonObject.has("error")) return null;
            for (JsonElement element : jsonObject.getAsJsonObject(album == null ? "artists" : "tracks").getAsJsonArray("items")) {
                JsonObject resp = new JsonObject();
                JsonObject json = element.getAsJsonObject();
                if (json.has("artists")) {
                    for (JsonElement art : json.getAsJsonArray("artists")) {
                        if (art.getAsJsonObject().get("name").getAsString().equalsIgnoreCase(author)) {
                            if (json.get("name").getAsString().equalsIgnoreCase(album)) {
                                JsonObject track = new JsonObject();
                                track.addProperty("title", json.get("name").getAsString());
                                track.addProperty("artwork", !json.getAsJsonObject("album").getAsJsonArray("images").isEmpty() ? json.getAsJsonObject("album").getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString() : null);
                                resp.add("track", track);
                                JsonObject artist = getInfo(null, author);
                                resp.add("author", artist.has("author") ? artist.get("author").getAsJsonObject() : null);
                                return resp;
                            }
                        }
                    }
                } else if (json.get("name").getAsString().equalsIgnoreCase(author)) {
                    JsonObject artist = new JsonObject();
                    artist.addProperty("name", json.get("name").getAsString());
                    artist.addProperty("artwork", !json.getAsJsonArray("images").isEmpty() ? json.getAsJsonArray("images").get(0).getAsJsonObject().get("url").getAsString() : null);
                    resp.add("author", artist);
                    return resp;
                }
            }
            return null;
        } catch (Exception ex) {
            WaterPlayerAPI.log(ex);
            return null;
        }
    }

    public static JsonObject getYouTubeInfo(String album, String author) {
        try {
            String queryForApi = album == null ? author : String.format("%s %s", author, album);
            String searchUrl = String.format("%s/youtube/v3/search?part=snippet&q=%s&regionCode=US&type=%s&key=%s", WaterPlayerAPI.config.getJsonObject("youtube", new JsonObject()).get("apiEndpoint").getAsString(), uriEncode(queryForApi), album == null ? "channel" : "video", WaterPlayerAPI.config.getJsonObject("youtube", new JsonObject()).get("apiKey").getAsString());
            JsonObject jsonObject = Web.getJsonObject(HttpRequest.newBuilder(URI.create(searchUrl))
                    .header("Accept", "application/json"));
            for (JsonElement element : jsonObject.getAsJsonArray("items")) {
                JsonObject resp = new JsonObject();
                JsonObject json = element.getAsJsonObject().getAsJsonObject("snippet");
                JsonObject meta = element.getAsJsonObject().getAsJsonObject("id");
                if (meta.get("kind").getAsString().equalsIgnoreCase("youtube#video")) {
                    if (json.get("channelTitle").getAsString().equalsIgnoreCase(author)) {
                        if (json.get("title").getAsString().equalsIgnoreCase(album)) {
                            JsonObject track = new JsonObject();
                            track.addProperty("title", json.get("title").getAsString());
                            if (json.has("thumbnails")) {
                                if(json.getAsJsonObject("thumbnails").has("high")) track.addProperty("artwork", json.getAsJsonObject("thumbnails").getAsJsonObject("high").get("url").getAsString());
                                else if(json.getAsJsonObject("thumbnails").has("medium")) track.addProperty("artwork", json.getAsJsonObject("thumbnails").getAsJsonObject("medium").get("url").getAsString());
                                else if(json.getAsJsonObject("thumbnails").has("default")) track.addProperty("artwork", json.getAsJsonObject("thumbnails").getAsJsonObject("default").get("url").getAsString());

                            }
                            resp.add("track", track);
                            JsonObject artist = getInfo(null, author);
                            resp.add("author", artist.has("author") ? artist.get("author").getAsJsonObject() : null);
                            return resp;
                        }
                    }
                } else if (json.get("channelTitle").getAsString().equalsIgnoreCase(author)) {
                    JsonObject artist = new JsonObject();
                    artist.addProperty("name", json.get("channelTitle").getAsString());
                    if (json.has("thumbnails")) {
                        if(json.getAsJsonObject("thumbnails").has("high")) artist.addProperty("artwork", json.getAsJsonObject("thumbnails").getAsJsonObject("high").get("url").getAsString());
                        else if(json.getAsJsonObject("thumbnails").has("medium")) artist.addProperty("artwork", json.getAsJsonObject("thumbnails").getAsJsonObject("medium").get("url").getAsString());
                        else if(json.getAsJsonObject("thumbnails").has("default")) artist.addProperty("artwork", json.getAsJsonObject("thumbnails").getAsJsonObject("default").get("url").getAsString());
                    }
                    resp.add("author", artist);
                    return resp;
                }
            }
            return null;
        } catch (Exception ex) {
            WaterPlayerAPI.log(ex);
            return null;
        }
    }

    public static void loadCache(){
        JsonObject cache = new Config("./cacheInfo.json").toJSON();
        for(String key : cache.keySet()) cacheRequests.put(key, cache.get(key).getAsJsonObject());
        WaterPlayerAPI.log(String.format("Кэш был загружен! Кол-во: %s", cacheRequests.size()));
    }

    public static void saveCache(){
        JsonObject object = new JsonObject();
        for(String key : cacheRequests.keySet()) object.add(key, cacheRequests.get(key));
        try {
            Files.writeString(Path.of("./cacheInfo.json"), object.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex){
            WaterPlayerAPI.log(ex, ERROR);
        }
    }

    protected static String uriEncode(String uri) {
        return URLEncoder.encode(uri, StandardCharsets.UTF_8);
    }
}
