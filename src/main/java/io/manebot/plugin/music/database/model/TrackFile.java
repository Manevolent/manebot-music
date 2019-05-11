package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.TimedRow;

import javax.persistence.*;

import java.sql.SQLException;
import java.util.UUID;

@Entity
@Table(
        indexes = {
                @Index(columnList = "trackRepositoryId,uuid", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"trackRepositoryId","uuid"})}
)
public class TrackFile extends TimedRow {
    @Transient
    private final Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int fileId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trackRepositoryId")
    private TrackRepository trackRepository;

    @Column(nullable = false)
    private UUID uuid;

    /**
     * Container format
     */
    @Column(nullable = false)
    private String format;

    public TrackFile(Database database) {
        this.database = database;
    }

    public TrackFile(Database database, TrackRepository trackRepository, UUID uuid, String format) {
        this.database = database;
        this.trackRepository = trackRepository;
        this.uuid = uuid;
        this.format = format;
    }

    public int getFileId() {
        return fileId;
    }

    public TrackRepository getTrackRepository() {
        return trackRepository;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        try {
            this.format = database.executeTransaction(s -> {
                TrackFile trackFile = s.find(TrackFile.class, getFileId());
                trackFile.format = format;
                trackFile.setUpdated(System.currentTimeMillis());
                return trackFile.format;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
