package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;
import io.manebot.plugin.music.database.model.Track;
import io.manebot.tuple.Pair;
import io.manebot.user.UserAssociation;

import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TrackQueueListCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public TrackQueueListCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }

    @AnnotatedCommandExecutor.Command(description = "Shows the queue")
    public void queue(CommandSender sender, @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        Queue<Pair<UserAssociation, Track>> queue = music.getQueue(sender.getChat());
        List<Track> tracks = Stream.of(queue.toArray(new Pair[0]))
                .map(pair -> (Pair<UserAssociation, Track>) pair)
                .map(Pair::getRight)
                .collect(Collectors.toList());
        sender.sendList(Track.class, builder -> {
            builder.responder(Track.FORMATTER);
            builder.direct(tracks);
            builder.page(page);
        });
    }

}
