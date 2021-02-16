package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentURL;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.repository.Repository;
import io.manebot.tuple.Pair;

import java.io.IOException;
import java.net.URL;
import java.sql.SQLException;
import java.util.Objects;
import java.util.stream.Collectors;

public class TrackDebugInfoCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackDebugInfoCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Gets track debug information", permission = "music.track.info.debug")
    public void info(CommandSender sender) throws CommandExecutionException {
        Track track = music.getPlayedTrack(sender.getConversation());
        if (track == null)
            throw new CommandArgumentException("No tracks were played on this channel recently.");

        info(sender, track);
    }

    @Command(description = "Gets track debug information", permission = "music.track.info.debug")
    public void info(CommandSender sender, @CommandArgumentURL.Argument URL url) throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        Track track = community.findTrack(url);
        if (track == null)
            throw new CommandArgumentException("Track not found.");

        info(sender, track);
    }

    @Command(description = "Gets track debug information", permission = "music.track.info.debug")
    public void info(CommandSender sender, @CommandArgumentSearch.Argument Search search)
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

        info(sender, track);
    }

    private void info(CommandSender sender, Track track) throws CommandExecutionException {
        sender.sendDetails(builder -> {
            builder.name("Track").key(track.getName());
            builder.item("URL", track.getUrlString());
            builder.item("Community", track.getCommunity().getName());
            builder.item("UUID", track.getUUID());
            builder.item("Resources", track.getFiles().stream()
                    .map(trackFile -> {
                        try {
                            return trackFile.getTrackRepository().getInstance().get(trackFile.getUuid());
                        } catch (Throwable ignored) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .filter(Repository.Resource::exists)
                    .count()
            );
        });
    }
}
