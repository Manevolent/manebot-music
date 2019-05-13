package io.manebot.plugin.music;

import io.manebot.artifact.ManifestIdentifier;
import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;
import io.manebot.plugin.PluginType;
import io.manebot.plugin.audio.Audio;
import io.manebot.plugin.java.PluginEntry;
import io.manebot.plugin.music.command.MusicCommand;
import io.manebot.plugin.music.command.TrackCommand;
import io.manebot.plugin.music.database.model.*;

public class Entry implements PluginEntry {
    @Override
    public void instantiate(Plugin.Builder builder) throws PluginException {
        builder.setType(PluginType.FEATURE);

        Database database = builder.addDatabase("music", databaseBuilder -> {
            databaseBuilder.addDependency(databaseBuilder.getSystemDatabase());
            databaseBuilder.registerEntity(Community.class);
            databaseBuilder.registerEntity(TrackRepository.class);
            databaseBuilder.registerEntity(Track.class);
            databaseBuilder.registerEntity(TrackPlay.class);
            databaseBuilder.registerEntity(TrackFile.class);
        });

        Plugin audioPlugin = builder.requirePlugin(ManifestIdentifier.fromString("io.manebot.plugin:audio"));

        MusicManager musicManager = new MusicManager(database);

        if (musicManager.getTrackRepositoryByName("default") == null)
            musicManager.createTrackRepository("default");

        builder.setInstance(
                Music.class,
                plugin -> new Music(plugin, musicManager, audioPlugin.getInstance(Audio.class))
        );

        builder.addCommand("track", future -> new TrackCommand(future.getPlugin().getInstance(Music.class)));
        builder.addCommand("music", future -> new MusicCommand(musicManager));
    }
}
