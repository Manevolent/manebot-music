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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class TrackInfoCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackInfoCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Gets track information", permission = "music.track.info")
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
                        throw new IllegalStateException("Query ambiguation: more than 1 result found");
                    })
                    .orElseThrow(() -> new CommandArgumentException("No results found."));
        } catch (SQLException e) {
            throw new CommandExecutionException(e);
        }

        sender.sendDetails(builder -> {
            builder.key("Track").name(track.getName());
            builder.item("User", track.getUser());
            builder.item("URL", track.getUrlString());
            builder.item("Duration", track.getTimeSignature());
            builder.item("Plays", track.getPlays());
            builder.item("Likes",
                    track.getLikes() - track.getDislikes() +
                    " (" + track.getLikes() + " likes | " + track.getDislikes() + " dislikes)"
            );
            builder.item("Tags",
                    track.getTags().stream()
                    .map(trackTag -> trackTag.getTag().getName())
                    .distinct()
                    .collect(Collectors.toList())
            );
        });
    }
}
