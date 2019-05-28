package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.command.track.TrackInfoCommand;
import io.manebot.plugin.music.command.track.TrackPlayCommand;
import io.manebot.plugin.music.command.track.TrackSearchCommand;

public class TrackCommand extends RoutedCommandExecutor {
    public TrackCommand(Music music, Database database) {
        route("search", new TrackSearchCommand(music, database)).alias("s");
        route("play", new TrackPlayCommand(music, database)).asDefaultRoute();
        route("info", new TrackInfoCommand(music, database)).asNullRoute();
    }

    @Override
    public String getDescription() {
        return "Downloads and plays audio tracks";
    }

}
