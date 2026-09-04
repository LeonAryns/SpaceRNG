package com.spacerng.solrng.farming;

import org.bukkit.Material;

/**
 * One crop a player can grow on the shared farm. The material is what the
 * plot is rendered as for whoever picked it; tokens/shards are what one
 * harvest pays.
 *
 * requiresNode is a skill tree node id. Empty means the crop is available
 * from the start (wheat); anything else stays locked until that node is
 * bought, so new crops can be hung off new skill nodes later without
 * touching this class.
 */
public class CropType {

    private final String id;
    private final String display;
    private final Material material;
    private final long tokens;
    private final long shards;
    private final String requiresNode;
    private final int order;

    public CropType(String id, String display, Material material, long tokens, long shards,
                    String requiresNode, int order) {
        this.id = id;
        this.display = display;
        this.material = material;
        this.tokens = tokens;
        this.shards = shards;
        this.requiresNode = requiresNode;
        this.order = order;
    }

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public Material getMaterial() {
        return material;
    }

    public long getTokens() {
        return tokens;
    }

    public long getShards() {
        return shards;
    }

    public String getRequiresNode() {
        return requiresNode;
    }

    public int getOrder() {
        return order;
    }

    public boolean isFree() {
        return requiresNode == null || requiresNode.isEmpty();
    }
}
