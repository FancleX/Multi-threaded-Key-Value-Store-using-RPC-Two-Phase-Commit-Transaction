package org.neu.protocol;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The message class use for data transfer in two-phase transaction
 */
@Data
@AllArgsConstructor
@ToString
public class Message implements Serializable {

    private static final long serialVersionUID = 1234567L;

    private UUID messageId;

    // operation type
    private Type type;

    // key
    private String key;

    // value
    private String value;

    // client id
    private UUID clientId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message)) return false;
        Message message = (Message) o;
        return messageId.equals(message.getMessageId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId);
    }
}
