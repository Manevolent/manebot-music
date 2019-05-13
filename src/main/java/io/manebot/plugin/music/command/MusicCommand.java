package io.manebot.plugin.music.command;

import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.plugin.music.command.music.MusicCommunityCommand;
import io.manebot.plugin.music.command.music.MusicRepositoryCommand;
import io.manebot.plugin.music.database.model.MusicManager;

public class MusicCommand extends RoutedCommandExecutor {
    public MusicCommand(MusicManager manager) {
        route("community", new MusicCommunityCommand(manager));
        route("repository", new MusicRepositoryCommand(manager));
    }
}
