package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.command.track.TrackPlayCommand;
import io.manebot.plugin.music.command.track.TrackSearchCommand;

public class TrackCommand extends RoutedCommandExecutor {

    public TrackCommand(Music music) {
        route("search", new TrackSearchCommand()).alias("s");
        route("play", new TrackPlayCommand(music)).asDefaultRoute();
    }

    @Override
    public String getDescription() {
        return "Downloads and plays audio tracks";
    }

}
