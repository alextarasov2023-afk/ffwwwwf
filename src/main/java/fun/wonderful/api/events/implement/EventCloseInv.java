package fun.wonderful.api.events.implement;

import lombok.AllArgsConstructor;
import fun.wonderful.api.events.Event;

@AllArgsConstructor
public class EventCloseInv extends Event {
    public int windowId;
}

