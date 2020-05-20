package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.database.Database;
import io.manebot.database.model.User;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Tag;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.plugin.music.database.model.TrackTag;
import io.manebot.security.Grant;
import io.manebot.security.Permission;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class TrackTagCommand implements CommandExecutor {
    private final Music music;
    private final Database database;

    public TrackTagCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @Override
    public void execute(CommandSender sender, String label, String[] tags) throws CommandExecutionException {
        Permission.checkPermission("music.track.tag", Grant.DENY);

        if (tags.length <= 0)
            throw new CommandArgumentException("No tags supplied.");

        Track track = music.getPlayedTrack(sender.getConversation());

        if (track == null)
            throw new CommandArgumentException("No tracks were played on this channel recently.");

        tag(sender, track, new HashSet<>(Arrays.asList(tags)));
    }

    private void tag(CommandSender sender, Track track, Set<String> tagNames) throws CommandArgumentException {
        tagNames = tagNames.stream().map(String::toLowerCase).collect(Collectors.toSet());

        Collection<String> nonAlphanumericTagNames =
                tagNames.stream().filter(tagName -> !tagName.matches("^[a-zA-Z0-9_.]+$")).collect(Collectors.toSet());
        if (nonAlphanumericTagNames.size() > 0) {
            throw new CommandArgumentException("Tag name(s) not alphanumeric: " +
                    String.join(", ", nonAlphanumericTagNames) + ".");
        }

        Collection<String> longTagNames =
                tagNames.stream().filter(tagName -> tagName.length() > 12).collect(Collectors.toSet());
        if (longTagNames.size() > 0) {
            throw new CommandArgumentException("Tag name(s) more than 12 characters: " +
                    String.join(", ", longTagNames) + ".");
        }

        Set<Tag> tags = music.getMusicManager().getOrCreateTags(tagNames);
        Set<TrackTag> added = track.addTags(tags);

        if (added.size() > 0) {
            sender.sendMessage("Track tagged with " +
                    String.join(", ", added.stream().map(TrackTag::getTag)
                            .map(Tag::getName).collect(Collectors.toSet())) + ".");
        } else {
            throw new CommandArgumentException("Track already tagged with " +
                    String.join(", ", tags.stream().map(Tag::getName).collect(Collectors.toSet())) + ".");
        }
    }
}
