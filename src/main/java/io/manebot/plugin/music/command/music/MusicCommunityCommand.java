package io.manebot.plugin.music.command.music;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.plugin.music.database.model.Community;
import io.manebot.plugin.music.database.model.MusicManager;
import io.manebot.plugin.music.database.model.TrackRepository;

public class MusicCommunityCommand extends AnnotatedCommandExecutor {
    private final MusicManager musicManager;

    public MusicCommunityCommand(MusicManager musicManager) {
        this.musicManager = musicManager;
    }

    @Command(description = "Lists communities", permission = "music.community.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument() int page) throws CommandExecutionException {
        sender.sendList(
                Community.class,
                builder -> builder
                        .direct(musicManager.getCommunities())
                        .responder((textBuilder, community) -> textBuilder.append(community.getName()))
                        .page(page)
        );
    }

    @Command(description = "Deletes a community", permission = "music.community.delete")
    public void delete(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "delete") String delete,
                     @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        community.delete();
    }

    @Command(description = "Gets community information", permission = "music.community.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        sender.sendDetails(builder -> builder
                .key("Community").name(community.getName())
                .item("Repository", community.getRepository() != null ? community.getRepository().getName() : "(none)")
                .item("Created", community.getCreatedDate())
                .item("Updated", community.getUpdatedDate())
        );
    }

    @Command(description = "Renames a community", permission = "music.community.rename")
    public void rename(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "rename") String rename,
                       @CommandArgumentString.Argument(label = "name") String name,
                       @CommandArgumentString.Argument(label = "new") String newName) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community == null) throw new CommandArgumentException("Community not found.");

        String oldName = community.getName();

        community.setName(newName);

        sender.sendMessage("Community \"" + oldName + "\" renamed to \"" + community.getName() + "\".");
    }

    @Command(description = "Creates a community", permission = "music.community.create")
    public void create(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "create") String create,
                       @CommandArgumentString.Argument(label = "name") String name) throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community != null) throw new CommandArgumentException("A community already exists by that name.");

        community = musicManager.createCommunity(name);

        sender.sendMessage("Community \"" + community.getName() + "\" created.");
    }

    @Command(description = "Creates a community with a repository", permission = "music.community.create")
    public void create(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "create") String create,
                       @CommandArgumentString.Argument(label = "name") String name,
                       @CommandArgumentString.Argument(label = "repository") String repositoryId)
            throws CommandExecutionException {
        Community community = musicManager.getCommunityByName(name);
        if (community != null) throw new CommandArgumentException("A community already exists by that name.");

        TrackRepository repository = musicManager.getTrackRepositoryByName(repositoryId);
        if (repository == null) throw new CommandArgumentException("Repository not found.");

        community = musicManager.createCommunity(name, repository);

        sender.sendMessage("Community \"" + community.getName() + "\" created using repository \"" +
                repository.getName() + "\".");
    }
}
