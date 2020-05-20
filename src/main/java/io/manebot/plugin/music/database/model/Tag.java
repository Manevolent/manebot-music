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
                @Index(columnList = "tagId"),
                @Index(columnList = "name", unique = true)
        }
)
public class Tag extends TimedRow {
    @Transient
    private final Database database;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int tagId;

    @Column(nullable = false, length = 64)
    private String name;

    @ManyToOne(optional = true)
    @JoinColumn(name = "userId")
    private User user;

    public Tag(Database database) {
        this.database = database;
    }

    public Tag(Database database,
               String name,
               io.manebot.user.User owner) {
        this(database);

        this.name = name;
        this.user = (User) owner;
    }

    public Tag(Database database,
               String name) {
        this(database);

        this.name = name;
    }

    public int getTagId() {
        return tagId;
    }

    public String getName() {
        return name;
    }

    public User getUser() {
        return user;
    }

    public boolean setUser(User user) {
        if (this.user != user) {
            try {
                this.user = database.executeTransaction((s) -> {
                    Tag existingTag = s.find(Tag.class, this.getTagId());
                    return existingTag.user = user;
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return true;
        } else return false;
    }
}
