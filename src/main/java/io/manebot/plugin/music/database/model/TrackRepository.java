package io.manebot.plugin.music.database.model;

import com.fasterxml.jackson.databind.*;
import com.google.gson.*;
import io.manebot.database.Database;
import io.manebot.database.model.TimedRow;
import io.manebot.plugin.music.config.AudioDownloadFormat;
import io.manebot.plugin.music.repository.Repository;

import javax.persistence.*;
import java.io.*;
import java.sql.SQLException;
import java.util.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "name", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"name"})}
)
public class TrackRepository extends TimedRow {
    @Transient
    private final Database database;

    @Transient
    private Repository instance;
    @Transient
    private final Object instanceLock = new Object();

    public TrackRepository(Database database) {
        this.database = database;
    }

    public TrackRepository(Database database, String name, String type, String format, String properties) {
        this.database = database;
        this.name = name;
        this.type = type;
        this.format = format;
        this.properties = properties;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int repositoryId;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = true)
    private String format;

    @Column(nullable = true)
    private String properties;

    public int getRepositoryId() {
        return repositoryId;
    }

    public JsonObject getProperties() {
        if (properties == null) throw new NullPointerException("properties");
        return new JsonParser().parse(properties).getAsJsonObject();
    }

    public void setProperties(JsonObject object) {
        String json = object.toString();

        try {
            this.properties = database.executeTransaction(s -> {
                TrackRepository repository = s.find(TrackRepository.class, getRepositoryId());
                repository.properties = json;
                repository.setUpdated(System.currentTimeMillis());
                return repository.properties;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        try {
            this.type = database.executeTransaction(s -> {
                TrackRepository repository = s.find(TrackRepository.class, getRepositoryId());
                repository.type = type;
                repository.setUpdated(System.currentTimeMillis());
                return repository.type;
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
                TrackRepository repository = s.find(TrackRepository.class, getRepositoryId());
                repository.name = name;
                repository.setUpdated(System.currentTimeMillis());
                return repository.name;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public AudioDownloadFormat getFormat() {
        try {
            return format == null ? null : new ObjectMapper().readValue(format, AudioDownloadFormat.class);
        } catch (IOException e) {
            throw new RuntimeException("Problem parsing JSON: " + getClass().getName() + ".format", e);
        }
    }

    public void setFormat(AudioDownloadFormat format) {
        try {
            this.format = database.executeTransaction(s -> {
                TrackRepository repository = s.find(TrackRepository.class, getRepositoryId());
                repository.format = new ObjectMapper().writeValueAsString(format);
                repository.setUpdated(System.currentTimeMillis());
                return repository.format;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TrackFile getFile(UUID uuid) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + TrackFile.class.getName() + " x " +
                            "INNER JOIN x.trackRepository c " +
                            "WHERE c.repositoryId = :repositoryId AND x.uuid=:uuid",
                    TrackFile.class
            ).setParameter("repositoryId", getRepositoryId()).setParameter("uuid", uuid)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public TrackFile createFile(TrackRepository trackRepository, UUID uuid, String format) {
        try {
            return database.executeTransaction(s -> {
                TrackFile trackFile = new TrackFile(database, trackRepository, uuid, format);
                s.persist(trackFile);
                return trackFile;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    public Repository getInstance() {
        synchronized (instanceLock) {
            if (instance == null) {
                try {
                    Class clazz = Class.forName(getType());
                    instance = (Repository) clazz.getConstructor(TrackRepository.class).newInstance(this);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(
                            "Problem instantiating " + Repository.class.getName() + " instance for " +
                            name
                            + " (" + getType() + ")",
                            e
                    );
                }
            }
        }

        return instance;
    }

    public long countFiles() {
        return database.execute(s -> (Long) s.createQuery(
                "SELECT COUNT(x) " +
                        "FROM " + TrackFile.class.getName() + " x " +
                        "INNER JOIN x.trackRepository c " +
                        "WHERE c.repositoryId = :repositoryId"
        ).setParameter("repositoryId", getRepositoryId()).getSingleResult());
    }

    public void delete() {
        try {
            database.executeTransaction(s -> {
                s.remove(s.find(TrackRepository.class, getRepositoryId()));
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
