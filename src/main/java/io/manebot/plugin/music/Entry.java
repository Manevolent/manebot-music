package io.manebot.plugin.music;

import io.manebot.artifact.ManifestIdentifier;
import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;
import io.manebot.plugin.PluginType;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.java.PluginEntry;
import io.manebot.plugin.music.command.TrackCommand;
import io.manebot.plugin.music.database.model.*;

public class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginException {
        builder.setType(PluginType.FEATURE);

        Audio audioPlugin = (Audio) builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));

        Database database = builder.addDatabase("music", databaseBuilder -> {
            databaseBuilder.addDependency(databaseBuilder.getSystemDatabase());
            databaseBuilder.registerEntity(Track.class);
            databaseBuilder.registerEntity(TrackPlay.class);
            databaseBuilder.registerEntity(TrackRepository.class);
            databaseBuilder.registerEntity(Community.class);
            databaseBuilder.registerEntity(TrackFile.class);
        });

        MusicManager musicManager = new MusicManager(database);
        builder.setInstance(Music.class, plugin -> new Music(plugin, musicManager, audioPlugin));
        builder.addCommand("track", new TrackCommand(musicManager));
    }
}
