package io.github.gerbesh.unusualcopper.gameplay;

public enum MiningChunkStatus {
    DISABLED, IDLE, LOADING, READY, LIMIT, PROTECTED, FAILED;

    public boolean waiting() { return this == LOADING || this == LIMIT || this == PROTECTED || this == FAILED; }
}
