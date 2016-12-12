package org.hspconsortium.sandboxmanager.model;

public abstract class AbstractSandboxItem extends AbstractItem {
    protected Sandbox sandbox;

    public abstract Sandbox getSandbox();
    public abstract void setSandbox(Sandbox sandbox);
}
