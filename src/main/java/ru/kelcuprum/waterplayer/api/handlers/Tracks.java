package ru.kelcuprum.waterplayer.api.handlers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import express.http.request.Request;
import express.http.response.Response;
import express.utils.Status;
import ru.kelcuprum.waterplayer.api.WaterPlayerAPI;
import ru.kelcuprum.waterplayer.api.Web;
import ru.kelcuprum.waterplayer.api.config.GsonHelper;
import ru.kelcuprum.waterplayer.api.objects.Errors;
import org.apache.commons.codec.binary.Base64;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;

import static ru.kelcuprum.waterplayer.api.objects.Errors.NOT_FOUND;

public class Tracks {

    public static void info(Request req, Response res) {
        if(req.getQuery("author") == null){
            res.setStatus(Status._400);
            res.send("{\"error\": {\n" +
                    "                \"code\": 400,\n" +
                    "                \"codename\": \"Bad Request\",\n" +
                    "                \"message\": \"Нет нужных аргументов\"\n" +
                    "            }");
            return;
        }
        JsonObject jsonObject = getInfo(req.getQuery("album"), req.getQuery("author"));
        if (jsonObject == null) res.send(NOT_FOUND.toString());
        else res.send(jsonObject.toString());
    }

    public static void artwork(Request req, Response res) {
        if(req.getQuery("author") == null){
            res.setStatus(Status._400);
            res.send("{\"error\": {\n" +
                    "                \"code\": 400,\n" +
                    "                \"codename\": \"Bad Request\",\n" +
                    "                \"message\": \"Нет нужных аргументов\"\n" +
                    "            }");
            return;
        }
        JsonObject jsonObject = getInfo(req.getQuery("album"), req.getQuery("author"));
        if (jsonObject == null) res.send(NOT_FOUND.toString());
        res.redirect(jsonObject.get(req.getQuery("album") == null ? "author" : "track").getAsJsonObject().get("artwork").getAsString());
    }

    public static JsonObject getInfo(String album, String author) {
        JsonObject finalResponse = null;
        JsonObject spotify = getSpotifyInfo(album, author);
        if (spotify == null) {
            JsonObject yandex = getYandexInfo(album, author);
            if (yandex != null) finalResponse = yandex;
        } else finalResponse = spotify;
        return finalResponse;
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
            ex.printStackTrace();
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
            ex.printStackTrace();
            WaterPlayerAPI.log(ex);
            return null;
        }
    }

    protected static String uriEncode(String uri) {
        return URLEncoder.encode(uri, StandardCharsets.UTF_8);
    }
}
