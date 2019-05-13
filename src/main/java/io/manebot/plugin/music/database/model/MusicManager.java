package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.plugin.music.repository.NullRepository;
import io.manebot.plugin.music.repository.Repository;

import java.net.URL;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class MusicManager {
    private final Database database;

    public MusicManager(Database database) {
        this.database = database;
    }

    public Collection<Track> getTracksByURL(URL url) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Track.class.getName() + " x " +
                    "WHERE x.url = :url", Track.class)
                    .setParameter("url", url.toExternalForm())
                    .getResultList();
        });
    }

    public TrackRepository getTrackRepositoryByName(String name) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + TrackRepository.class.getName() + " x " +
                    "WHERE x.name = :name", TrackRepository.class)
                    .setParameter("name", name)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public TrackRepository createTrackRepository(String name) {
        try {
            return database.executeTransaction(s -> {
                TrackRepository community = new TrackRepository(
                        database,
                        name,
                        NullRepository.class.getName(),
                        null,
                        null
                );

                s.persist(community);
                return community;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Community getCommunityByName(String name) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Community.class.getName() + " x " +
                    "WHERE x.name = :name", Community.class)
                    .setParameter("name", name)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public Community createCommunity(String name) {
        return createCommunity(name, Objects.requireNonNull(getTrackRepositoryByName("default")));
    }

    public Community createCommunity(String name, TrackRepository repository) {
        try {
            return database.executeTransaction(s -> {
                Community community = new Community(
                        database,
                        name,
                        s.find(TrackRepository.class, repository.getRepositoryId())
                );

                s.persist(community);
                return community;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Community> getCommunities() {
        return database.execute(s -> {
            return s.createQuery("SELECT x FROM " + Community.class.getName() + " x", Community.class).getResultList();
        });
    }

    public List<TrackRepository> getRepositories() {
        return database.execute(s -> {
            return s.createQuery("SELECT x FROM " + TrackRepository.class.getName() + " x", TrackRepository.class)
                    .getResultList();
        });
    }
}
