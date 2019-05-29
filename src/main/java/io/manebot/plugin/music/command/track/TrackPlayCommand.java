package io.manebot.plugin.music.command.track;

import com.github.manevolent.ffmpeg4j.FFmpegException;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.ChainPriority;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.command.executor.chained.argument.CommandArgumentURL;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchResult;
import io.manebot.plugin.audio.channel.AudioChannel;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.sql.SQLException;

public class TrackPlayCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackPlayCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Plays a track by its URL", permission = "music.track.play")
    public void play(CommandSender sender,
                     @CommandArgumentURL.Argument() URL url)
            throws CommandExecutionException {
        Track track = null;
        try {
            track = music.play(sender.getPlatformUser().getAssociation(), sender.getConversation(), builder ->
                    builder.setTrack(trackSelector -> trackSelector.find(url)).setExclusive(true)
            ).getTrack();
        } catch (IOException e) {
            throw new CommandExecutionException(e);
        } catch (FFmpegException e) {
            throw new CommandExecutionException(e);
        }

        sender.sendMessage("(Playing \"" + track.getName() + "\")");
    }

    @Command(description = "Plays a track by a query", permission = "music.track.play")
    public void play(CommandSender sender,
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

        Track track;

        try {
            track = music.play(sender.getPlatformUser().getAssociation(), sender.getConversation(), builder ->
                    builder.setTrack(trackSelector -> trackSelector.find(searchResult)).setExclusive(true)
            ).getTrack();
        } catch (IOException | FFmpegException e) {
            throw new CommandExecutionException(e);
        }

        sender.sendMessage("(Playing \"" + track.getName() + "\")");
    }
}
