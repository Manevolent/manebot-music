package io.manebot.plugin.music.command.playlist;

import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.database.Database;
import io.manebot.plugin.music.Music;

public class PlaylistStartCommand extends AnnotatedCommandExecutor {
    private final Music music;
    private final Database database;

    public PlaylistStartCommand(Music music, Database database) {
        this.music = music;
        this.database = database;
    }
}
