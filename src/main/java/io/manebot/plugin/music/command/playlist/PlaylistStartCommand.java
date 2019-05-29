package io.manebot.plugin.music.command.playlist;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.plugin.music.Music;

public class PlaylistStartCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public PlaylistStartCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Starts a playlist by a query", permission = "music.playlist.start")
    public void start(CommandSender sender,
                     @CommandArgumentSearch.Argument() Search search) throws CommandExecutionException {
        music.startPlaylist(
                sender.getPlatformUser().getAssociation(), sender.getConversation(),
                builder -> builder.setQueue(queueSelector -> queueSelector.from(search))
        );
    }
}
