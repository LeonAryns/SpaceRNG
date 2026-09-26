package com.spacerng.solrng.platform;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Chat for a Bedrock reader (V223). Bedrock has no hover, so a line that
 * keeps its details in a hover text, like the [stats] tag, showed a Bedrock
 * player only the word. For them the hover text is written out behind the
 * piece it belonged to, in grey and on one line.
 *
 * An item hover is left alone: the item's name is already in the line.
 */
public final class BedrockText {

    private BedrockText() {
    }

    public static Component showHovers(Component component) {
        List<Component> children = new ArrayList<>(component.children().size());
        for (Component child : component.children()) children.add(showHovers(child));
        Component out = component.children(children);
        HoverEvent<?> hover = out.hoverEvent();
        if (hover != null && hover.action() == HoverEvent.Action.SHOW_TEXT
                && hover.value() instanceof Component text) {
            String plain = PlainTextComponentSerializer.plainText().serialize(text)
                    .replace("\n", " · ").trim();
            if (!plain.isEmpty()) {
                out = out.append(Component.text(" (" + plain + ")", NamedTextColor.GRAY));
            }
        }
        return out;
    }
}
