package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.TimedRow;

import javax.persistence.*;
import java.net.URL;
import java.sql.SQLException;
import java.util.function.Consumer;

@javax.persistence.Entity
@Table(
        uniqueConstraints = {@UniqueConstraint(columnNames ={"name"})}
)
public class Community extends TimedRow {
    @Transient
    private final Database database;

    public Community(Database database) {
        this.database = database;
    }

    public Community(Database database, String name, TrackRepository trackRepository) {
       this(database);

       this.name = name;
       this.repository = trackRepository;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int communityId;

    @Column(length = 64, nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "repositoryId")
    private TrackRepository repository;

    public int getCommunityId() {
        return communityId;
    }

    public long countTracks() {
        return database.execute(s -> (Long) s.createQuery(
                "SELECT COUNT(x) FROM " + Track.class.getName() + " x " +
                        "INNER JOIN x.community c " +
                        "WHERE c.communityId = :communityId"
        ).setParameter("communityId", getCommunityId()).getSingleResult());
    }

    public Track getTrack(URL url) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + Track.class.getName() + " x " +
                            "INNER JOIN x.community c " +
                            "WHERE c.communityId = :communityId AND x.url=:url",
                    Track.class
            ).setParameter("communityId", communityId).setParameter("url", url.toExternalForm())
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public Track createTrack(URL url, Consumer<Track.Builder> constructor) {
        Track.Builder builder = new Track.DefaultBuilder(this, url);
        constructor.accept(builder);

        try {
            return database.executeTransaction(s -> {
                Track track = new Track(database);
                track.setCommunity(builder.getCommunity());
                track.setLength(builder.getLength());
                track.setName(builder.getName());
                s.persist(track);

                // TODO: tag track

                return track;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TrackRepository getRepository() {
        return repository;
    }

    public void setRepository(TrackRepository repository) {
        try {
            this.repository = database.executeTransaction(s -> {
                Community community = s.find(Community.class, getCommunityId());
                community.repository = repository;
                community.setUpdated(System.currentTimeMillis());
                return repository;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        try {
            this.name = database.executeTransaction(s -> {
                Community community = s.find(Community.class, getCommunityId());
                community.name = name;
                community.setUpdated(System.currentTimeMillis());
                return name;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete() {

        try {
            database.executeTransaction(s -> {
                s.remove(s.find(Community.class, getCommunityId()));
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
