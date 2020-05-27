package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchResult;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.io.IOException;
import java.sql.SQLException;

public class TrackRandomCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackRandomCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Plays a random track by query", permission = "music.track.play")
    public void play(CommandSender sender, @CommandArgumentSearch.Argument Search search)
            throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        SearchResult<Track> searchResult;

        try {
            searchResult = Track.createSearch(database, community).random(search, sender.getChat().getDefaultPageSize());
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }

        Track track;

        try {
            track = music.play(sender.getPlatformUser().getAssociation(), sender.getConversation(), builder ->
                    builder.setTrack(trackSelector -> trackSelector.findFirst(searchResult)).setExclusive(true)
            ).getTrack();
        } catch (IOException e) {
            throw new CommandExecutionException(e);
        }

        sender.sendMessage("(Playing \"" + track.getName() + "\")");
    }
}
