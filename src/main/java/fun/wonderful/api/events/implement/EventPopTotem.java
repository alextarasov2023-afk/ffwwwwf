package fun.wonderful.api.events.implement;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.entity.player.PlayerEntity;
import fun.wonderful.api.events.Event;

@AllArgsConstructor @Getter
public class EventPopTotem extends Event {
    private final PlayerEntity player;
}