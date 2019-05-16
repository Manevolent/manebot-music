package io.manebot.plugin.music.command;

import io.manebot.Bot;
import io.manebot.command.executor.routed.RoutedCommandExecutor;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.command.music.MusicCommunityCommand;
import io.manebot.plugin.music.command.music.MusicRepositoryCommand;
import io.manebot.plugin.music.database.model.MusicManager;

public class MusicCommand extends RoutedCommandExecutor {
    public MusicCommand(Music music, MusicManager manager, Bot bot) {
        route("community", new MusicCommunityCommand(music, manager, bot));
        route("repository", new MusicRepositoryCommand(manager));
    }
}
