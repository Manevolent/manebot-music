package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentURL;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.*;
import io.manebot.security.Grant;
import io.manebot.security.Permission;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TrackDeleteCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackDeleteCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Command(description = "Deletes the last played track", permission = "music.track.delete",
            defaultGrant = Grant.ALLOW)
    public void delete(CommandSender sender) throws CommandExecutionException {
        Track track = music.getPlayedTrack(sender.getConversation());

        if (track == null)
            throw new CommandArgumentException("No tracks were played on this channel recently.");

        delete(sender, track);
    }

    @Command(description = "Deletes a track by URL", permission = "music.track.delete",
            defaultGrant = Grant.ALLOW)
    public void delete(CommandSender sender, @CommandArgumentURL.Argument URL url) throws CommandExecutionException {
        Community community = music.getCommunity(sender);
        if (community == null)
            throw new CommandArgumentException("There is no music community associated with this conversation.");

        Track track = community.getTrack(url);
        if (track == null)
            throw new CommandArgumentException("Track not found.");

        delete(sender, track);
    }

    private void delete(CommandSender sender, Track track) throws CommandExecutionException {
        if (track.isDeleted())
            throw new CommandArgumentException("Track is already deleted.");

        if (!Permission.hasPermission("music.track.delete")) {
            if (track.getUser() != null && !track.getUser().equals(sender.getUser())) {
                throw new CommandAccessException("You do not own this track.");
            }
        }

        for (TrackFile file : track.getFiles()) {
            try {
                file.delete();
            } catch (IOException e) {
                throw new CommandExecutionException("Unexpected problem deleting track file.", e);
            }
        }

        track.setDeleted(true);

        sender.sendMessage("Track \"" + track.getName() + "\" deleted.");
    }
}
