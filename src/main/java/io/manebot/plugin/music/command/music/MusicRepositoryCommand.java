package io.manebot.plugin.music.command.music;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;

import io.manebot.plugin.music.database.model.MusicManager;
import io.manebot.plugin.music.database.model.TrackRepository;

public class MusicRepositoryCommand extends AnnotatedCommandExecutor {
    private final MusicManager musicManager;

    public MusicRepositoryCommand(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Command(description = "Lists repositories", permission = "music.repository.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument() int page) throws CommandExecutionException {
        sender.sendList(
                TrackRepository.class,
                builder -> builder
                        .direct(musicManager.getRepositories())
                        .responder((textBuilder, repository) -> textBuilder.append(repository.getName()))
                        .page(page)
        );
    }

    @Command(description = "Deletes a repository", permission = "music.repository.delete")
    public void delete(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "delete") String delete,
                     @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        TrackRepository repository = musicManager.getTrackRepositoryByName(name);
        if (repository == null) throw new CommandArgumentException("Repository not found.");
        repository.delete();
    }

    @Command(description = "Gets repository information", permission = "music.repository.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        TrackRepository repository = musicManager.getTrackRepositoryByName(name);
        if (repository == null) throw new CommandArgumentException("Repository not found.");
        sender.sendDetails(builder -> builder
                .name("Repository").key(repository.getName())
                .item("Type", repository.getType())
                .item("Format", repository.getFormat() == null ? "(none)" : repository.getFormat().toString())
                .item("Created", repository.getCreatedDate())
                .item("Updated", repository.getUpdatedDate())
        );
    }

    @Command(description = "Renames a repository", permission = "music.repository.rename")
    public void rename(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "rename") String rename,
                       @CommandArgumentString.Argument(label = "name") String name,
                       @CommandArgumentString.Argument(label = "new") String newName) throws CommandExecutionException {
        TrackRepository repository = musicManager.getTrackRepositoryByName(name);
        if (repository == null) throw new CommandArgumentException("Repository not found.");
        String oldName = repository.getName();
        repository.setName(newName);
        sender.sendMessage("Repository \"" + oldName + "\" renamed to \"" + repository.getName() + "\".");
    }

    @Command(description = "Creates a repository", permission = "music.repository.create")
    public void create(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "create") String create,
                       @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        TrackRepository repository = musicManager.getTrackRepositoryByName(name);
        if (repository != null) throw new CommandArgumentException("A repository already exists by that name.");

        repository = musicManager.createTrackRepository(name);

        sender.sendMessage("Repository \"" + repository.getName() + "\" created.");
    }
}
