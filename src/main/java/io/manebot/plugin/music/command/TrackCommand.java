package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.command.track.*;
import io.manebot.plugin.music.database.model.TrackTag;

public class TrackCommand extends RoutedCommandExecutor {
    public TrackCommand(Music music, Database database) {
        route("search", new TrackSearchCommand(music, database)).alias("s");
        route("play", new TrackQueueCommand(music, database)).asDefaultRoute();
        route("queue", new TrackQueueListCommand(music, database)).alias("q");
        route("repeat", new TrackRepeatCommand(music, database)).alias("replay");
        route("info", new TrackInfoCommand(music, database)).asNullRoute();
        route("debug-info", new TrackDebugInfoCommand(music, database));
        route("tag", new TrackTagCommand(music, database));
        route("untag", new TrackUntagCommand(music, database));
        route("random", new TrackRandomCommand(music, database));
        route("delete", new TrackDeleteCommand(music, database));
        route("history", new TrackHistoryCommand(music, database));
        route("loop", new TrackLoopCommand(music, database));
    }

    @Override
    public String getDescription() {
        return "Downloads and plays audio tracks";
    }

}
