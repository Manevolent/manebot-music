package io.manebot.plugin.music.command.track;

import io.manebot.command.CommandSender;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.search.CommandArgumentSearch;

public class TrackSearchCommand extends AnnotatedCommandExecutor {
    @Command(description = "Searches tracks")
    public void search(CommandSender sender,
                       @CommandArgumentSearch.Argument CommandArgumentSearch search) {

    }
}
