package ravens4.lag.queue.node.impl;

import ravens4.lag.api.LagRequest;
import ravens4.lag.queue.node.api.AbstractLagNode;
import org.jetbrains.annotations.NotNull;

public final class AddRequestLagNode extends AbstractLagNode {

    private final @NotNull LagRequest request;

    public AddRequestLagNode(@NotNull LagRequest request) {
        this.request = request;
    }

    public @NotNull LagRequest getRequest() {
        return request;
    }

}