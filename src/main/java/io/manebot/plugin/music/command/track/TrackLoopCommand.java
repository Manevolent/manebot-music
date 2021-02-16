package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentURL;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchResult;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.Play;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;

public class TrackLoopCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackLoopCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Loops a track by its URL", permission = "music.playlist.start")
    public void loop(CommandSender sender)
            throws CommandExecutionException {
        Track track = music.getPlayedTrack(sender.getConversation());
        if (track == null)
            throw new CommandArgumentException("No tracks were played on this channel recently.");

        music.startPlaylist(
                sender.getPlatformUser().getAssociation(),
                sender.getConversation(),
                builder -> builder.setQueue(queueSelector -> queueSelector.from(track, true))
        );
    }

    @Command(description = "Loops a track by its URL", permission = "music.playlist.start")
    public void loop(CommandSender sender,
                     @CommandArgumentURL.Argument() URL url)
            throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        Track track = community.findTrack(url);
        if (track == null)
            throw new CommandArgumentException("Track not found.");

        music.startPlaylist(
                sender.getPlatformUser().getAssociation(),
                sender.getConversation(),
                builder -> builder.setQueue(queueSelector -> queueSelector.from(track, true))
        );
    }

    @Command(description = "Loops a track by its URL", permission = "music.playlist.start")
    public void loop(CommandSender sender,
                     @CommandArgumentSearch.Argument() Search search)
            throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        Track track;

        try {
            track = Track.createSearch(database, community).search(search, sender.getChat().getDefaultPageSize())
                    .getResults()
                    .stream()
                    .reduce((a, b) -> {
                        throw new IllegalArgumentException("More than 1 result found.");
                    })
                    .orElseThrow(() -> new CommandArgumentException("No results found."));
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }

        music.startPlaylist(
                sender.getPlatformUser().getAssociation(),
                sender.getConversation(),
                builder -> builder.setQueue(queueSelector -> queueSelector.from(track, true))
        );
    }
}
