package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentURL;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchResult;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.Play;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.playlist.Playlist;
import io.manebot.tuple.Pair;
import io.manebot.user.UserAssociation;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TrackQueueCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackQueueCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Plays or queues a track by its URL", permission = "music.track.play")
    public void queue(CommandSender sender,
                     @CommandArgumentURL.Argument() URL url)
            throws CommandExecutionException {
        Play play;

        try {
            play = music.play(
                    sender.getPlatformUser().getAssociation(),
                    sender.getConversation(),
                    builder -> builder
                            .setTrack(trackSelector -> trackSelector.find(url))
                            .setBehavior(Play.Behavior.QUEUED)
            );
        } catch (IOException e) {
            throw new CommandExecutionException(e);
        }

        if (play.wasQueued())
            sender.sendMessage("(Queued \"" + play.getTrack().getName() + "\")");
        else
            sender.sendMessage("(Playing \"" + play.getTrack().getName() + "\")");
    }

    @Command(description = "Plays or queues a track by a query", permission = "music.track.play")
    public void queue(CommandSender sender,
                     @CommandArgumentSearch.Argument() Search search) throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        SearchResult<Track> searchResult;

        try {
            searchResult = Track.createSearch(database, community).search(search, sender.getChat().getDefaultPageSize());
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }

        Play play;

        try {
            play = music.play(
                    sender.getPlatformUser().getAssociation(),
                    sender.getConversation(),
                    builder -> builder
                            .setTrack(trackSelector -> trackSelector.find(searchResult))
                            .setBehavior(Play.Behavior.QUEUED)
            );
        } catch (IOException e) {
            throw new CommandExecutionException(e);
        }

        if (play.wasQueued())
            sender.sendMessage("(Queued \"" + play.getTrack().getName() + "\")");
        else
            sender.sendMessage("(Playing \"" + play.getTrack().getName() + "\")");
    }
}
