package org.acestream.engine.service.v0;

interface IAceStreamManagerService {
    void startEngine();
    boolean stopEngine();
    int getEngineStatus();
    String getAccessToken();
    void registerClient(String name);
    void unregisterClient(String name);
}
