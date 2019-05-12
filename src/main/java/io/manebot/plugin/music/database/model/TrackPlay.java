package io.manebot.plugin.music.database.model;

import io.manebot.database.Database;
import io.manebot.database.model.Conversation;
import io.manebot.database.model.TimedRow;
import io.manebot.database.model.User;

import javax.persistence.*;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        indexes = {
                @Index(columnList = "trackId"),
                @Index(columnList = "conversationId"),
                @Index(columnList = "userId"),
                @Index(columnList = "start"),
                @Index(columnList = "end"),
                @Index(columnList = "length"),
                @Index(columnList = "percent")
        }
)
public class TrackPlay extends TimedRow {
    @Transient
    private final Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int playId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "trackId")
    private Track track;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversationId")
    private Conversation conversation;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private User user;

    @Column(nullable = true)
    private double start;

    @Column(nullable = false)
    private double end;

    @Column(nullable = false)
    private double length;

    @Column(nullable = false)
    private double percent;

    public TrackPlay(Database database) {
        this.database = database;
    }

    public TrackPlay(Database database,
                     Track track,
                     io.manebot.conversation.Conversation conversation,
                     io.manebot.user.User user,
                     double start,
                     double end) {
        this(database);

        this.track = track;
        this.conversation = (Conversation) conversation;
        this.user = (User) user;
        this.start = start;
        this.end = end;
        this.length = Math.max(0D, end - start);
        this.percent = Math.max(0D, Math.min(1D, start / end));
    }

    public int getPlayId() {
        return playId;
    }

    public Track getTrack() {
        return track;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public User getUser() {
        return user;
    }

    public double getStart() {
        return start;
    }

    public double getEnd() {
        return end;
    }

    public double getLength() {
        return length;
    }

    public double getPercent() {
        return percent;
    }
}
