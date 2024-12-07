package ru.kelcuprum.waterplayer.api;

import com.google.gson.JsonObject;
import express.Express;
import express.utils.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import ru.kelcuprum.waterplayer.api.config.Config;
import ru.kelcuprum.waterplayer.api.config.GsonHelper;
import ru.kelcuprum.waterplayer.api.handlers.Playlists;
import ru.kelcuprum.waterplayer.api.handlers.Tracks;
import ru.kelcuprum.waterplayer.api.handlers.User;
import ru.kelcuprum.waterplayer.api.objects.Objects;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class WaterPlayerAPI {
    public static Express server;
    public static Config config = new Config("config.json");
    public static Config publicConfig = new Config("public_config.json");
    public static Config release = new Config(new JsonObject());

    public static void main(String[] args) {
        log("Hello, world!");
        try {
            InputStream releaseFile = WaterPlayerAPI.class.getResourceAsStream("/release.json");
            release = new Config(GsonHelper.parse(new String(releaseFile.readAllBytes(), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            log(e, Level.DEBUG);
        }
        User.loadModerators();
        Tracks.loadCache();
        Tracks.loadAltNames();
        server = new Express();
        server.use((req, res) -> log(String.format("%s сделал запрос на %s", req.getIp(), req.getPath())));
        server.all("/", (req, res) -> res.json(Objects.INDEX));
        server.all("/release", (req, res) -> res.json(release.toJSON()));
        server.all("/favicon.ico", (req, res) -> res.redirect("https://waterplayer.ru/favicon.ico"));
        server.all("/public_config", (req, res) -> res.json(publicConfig.toJSON()));
        server.all("/ping", (req, res) -> {
            JsonObject jsonObject = new JsonObject();
            jsonObject.addProperty("message", "Pong!");
            jsonObject.addProperty("time", System.currentTimeMillis());
            res.json(jsonObject);
        });
        //
        server.get("/playlist/:id", Playlists::getPlaylist);
        server.delete("/playlist/:id", Playlists::deletePlaylist);
        server.post("/upload", Playlists::uploadPlaylist);
        server.get("/search", Playlists::search);
        server.get("/user", User::getUser);
        server.get("/verify", User::verify);
        server.get("/info", Tracks::info);
        server.get("/artwork", Tracks::artwork);
        //
        server.get("/v2/playlist/:id", Playlists::getPlaylist);
        server.delete("/v2/playlist/:id", Playlists::deletePlaylist);
        server.post("/v2/upload", Playlists::uploadPlaylist);
        server.get("/v2/search", Playlists::search);
        server.get("/v2/user", User::getUser);
        server.get("/v2/verify", User::verify);
        server.get("/v2/info", Tracks::info);
        server.get("/v2/artwork", Tracks::artwork);
        //
        server.get("/:id", Playlists::getPlaylist);
        server.all((req, res) -> {
            res.setStatus(Status._404);
            res.json(Objects.NOT_FOUND);
        });
        server.listen(config.getNumber("port", 4500).intValue());
        log("-=-=-=-=-=-=-=-=-=-=-=-=-");
        log("WaterPlayer API запущен!");
        log(String.format("Порт: %s", config.getNumber("port", 4500).intValue()));
        log("-=-=-=-=-=-=-=-=-=-=-=-=-");
    }

    // Упрощение работы с ответами
    public static JsonObject getErrorObject(Exception ex){
      JsonObject object = Objects.INTERNAL_SERVER_ERROR;
      object.get("error").getAsJsonObject().addProperty("message", ex.getMessage() == null ? ex.getClass().toString() : ex.getMessage());
      return object;
    }

    // LOG
    public static Logger LOG = LoggerFactory.getLogger("WaterPlayer");

    public static void log(String message) {
        log(message, Level.INFO);
    }

    public static void log(String message, Level level) {
        switch (level) {
            case INFO -> LOG.info(message);
            case WARN -> LOG.warn(message);
            case ERROR -> LOG.error(message);
            case DEBUG -> LOG.debug(message);
            case TRACE -> LOG.trace(message);
        }

    }

    public static void log(Exception message) {
        log(message, Level.ERROR);
    }

    public static void log(Exception message, Level level) {
        switch (level) {
            case INFO -> LOG.info(message.getLocalizedMessage(), message.fillInStackTrace());
            case WARN -> LOG.warn(message.getLocalizedMessage(), message.fillInStackTrace());
            case ERROR -> LOG.error(message.getLocalizedMessage(), message.fillInStackTrace());
            case DEBUG -> LOG.debug(message.getLocalizedMessage(), message.fillInStackTrace());
            case TRACE -> LOG.trace(message.getLocalizedMessage(), message.fillInStackTrace());
        }
    }

    public static String makeIDPlaylist(int length){
        StringBuilder result = new StringBuilder();
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        int charactersLength = characters.length();
        int counter = 0;
        while (counter < length) {
            result.append(characters.charAt((int) Math.floor(Math.random() * charactersLength)));
            counter += 1;
        }
        if (new File("./playlists/"+result+".json").exists()) return makeIDPlaylist(length);
        return result.toString();
    }
}