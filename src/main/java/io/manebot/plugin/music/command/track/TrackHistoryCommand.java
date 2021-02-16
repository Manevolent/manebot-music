package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.sql.SQLException;
import java.util.List;

public class TrackHistoryCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackHistoryCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Lists recently played tracks", permission = "music.track.search")
    public void search(CommandSender sender, @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        sender.sendList(
                Track.class,
                builder -> builder.page(page)
                        .responder(Track.FORMATTER)
                        .direct(music.getPlayedTracks(sender.getConversation(), 50, Integer.MAX_VALUE))
        );
    }
}
