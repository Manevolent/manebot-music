package io.manebot.plugin.music.command.track;

import io.manebot.chat.TextStyle;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.sql.SQLException;
import java.util.EnumSet;

public class TrackSearchCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackSearchCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Searches tracks", permission = "music.track.search")
    public void search(CommandSender sender, @CommandArgumentSearch.Argument Search search)
            throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        try {
            sender.sendList(
                    Track.class,
                    Track.createSearch(database, community).search(search, sender.getChat().getDefaultPageSize()),
                    (textBuilder, o) -> textBuilder
                            .append("\"" + o.getName() + "\"", EnumSet.of(TextStyle.ITALICS))
                            .append(" (")
                            .appendUrl(o.getUrlString())
                            .append(") (")
                            .append(Integer.toString(o.getLikes() - o.getDislikes()))
                            .append(" likes)")
            );
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }
    }
}
