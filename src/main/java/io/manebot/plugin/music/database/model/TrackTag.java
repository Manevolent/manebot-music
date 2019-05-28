package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.Conversation;
import io.manebot.database.model.TimedRow;
import io.manebot.database.model.User;

import javax.persistence.*;
import java.sql.SQLException;

@Entity
@Table(
        indexes = {
                @Index(columnList = "trackId,tagId,userId", unique = true),
                @Index(columnList = "tagId"),
                @Index(columnList = "userId"),
                @Index(columnList = "created")
        }
)
public class TrackTag extends TimedRow {
    @Transient
    private final Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int trackTagId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trackId")
    private Track track;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tagId")
    private Tag tag;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private User user;

    public TrackTag(Database database) {
        this.database = database;
    }

    public TrackTag(Database database,
                    Track track,
                    Tag tag,
                    io.manebot.user.User user) {
        this(database);

        this.track = track;
        this.user = (User) user;
        this.tag = tag;
    }

    public int getTrackTagId() {
        return trackTagId;
    }

    public Track getTrack() {
        return track;
    }

    public Tag getTag() {
        return tag;
    }

    public User getUser() {
        return user;
    }

    public void delete() {
        try {
            database.executeTransaction(s -> {
                s.remove(s.find(TrackTag.class, getTrackTagId()));
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
