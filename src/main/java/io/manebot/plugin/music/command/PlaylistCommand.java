package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.command.playlist.PlaylistInfoCommand;
import io.manebot.plugin.music.command.playlist.PlaylistStartCommand;
import io.manebot.plugin.music.command.track.TrackInfoCommand;
import io.manebot.plugin.music.command.track.TrackPlayCommand;
import io.manebot.plugin.music.command.track.TrackSearchCommand;

public class PlaylistCommand extends RoutedCommandExecutor {
    public PlaylistCommand(Music music, Database database) {
        route("start", new PlaylistStartCommand(music, database)).asDefaultRoute();
        route("info", new PlaylistInfoCommand(music, database)).asNullRoute();
    }

    @Override
    public String getDescription() {
        return "Starts and manages playlists";
    }

}
