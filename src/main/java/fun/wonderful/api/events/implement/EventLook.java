package fun.wonderful.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import fun.wonderful.api.events.Event;

@Getter @Setter @AllArgsConstructor
public class EventLook extends Event {
   private double yaw, pitch;
}