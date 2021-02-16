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

public class TrackRepeatCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackRepeatCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Replays the last played track", permission = "music.track.play")
    public void repeat(CommandSender sender)
            throws CommandExecutionException {
        Track track = music.getPlayedTrack(sender.getConversation());
        if (track == null)
            throw new CommandArgumentException("No tracks were played on this channel recently.");

        Play play;
        try {
            play = music.play(
                    sender.getPlatformUser().getAssociation(),
                    sender.getConversation(),
                    builder -> builder
                            .setTrack(trackSelector -> trackSelector.find(track))
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
