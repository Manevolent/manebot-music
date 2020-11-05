package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.command.playlist.PlaylistInfoCommand;
import io.manebot.plugin.music.command.playlist.PlaylistSkipCommand;
import io.manebot.plugin.music.command.playlist.PlaylistStartCommand;

public class PlaylistCommand extends RoutedCommandExecutor {
    public PlaylistCommand(Music music, Database database) {
        route("start", new PlaylistStartCommand(music, database)).asDefaultRoute();
        route("info", new PlaylistInfoCommand(music, database)).asNullRoute();
        route("skip", new PlaylistSkipCommand(music));
    }

    @Override
    public String getDescription() {
        return "Starts and manages playlists";
    }

}
