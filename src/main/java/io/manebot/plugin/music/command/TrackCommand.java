package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.plugin.music.command.track.TrackSearchCommand;
import io.manebot.plugin.music.database.model.MusicManager;

public class TrackCommand extends RoutedCommandExecutor {

    public TrackCommand(MusicManager manager) {
        route("search", new TrackSearchCommand()).alias("s");
    }

    @Override
    public String getDescription() {
        return "Downloads and plays audio tracks";
    }

}
