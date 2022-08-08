package org.neu.coordinator;

import lombok.extern.slf4j.Slf4j;
import org.neu.api.Service;
import org.neu.api.Transaction;
import org.neu.db.DB;
import org.neu.protocol.Message;
import org.neu.protocol.Type;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Coordinator implementation
 */
@Slf4j
public class CoordinatorImp extends UnicastRemoteObject implements Transaction {

    // store server connections
    private final Map<String, Service> serverInfo;

    // cache the crash servers
    private final Set<String> cache;

    // collect response of vote result
    // indicate if some server is absent and vote for aborting
    private final Map<String, Boolean> responseCollector;

    // ack message collector
    // indicate if some server is absent
    private final List<String> ackCollector;

    // cache each transaction data
    private final DB cacheData;

    // thread pool for timeout handling and sync data with crash servers
    private final Executor executor = Executors.newFixedThreadPool(10);

    // response lock
    private boolean isInitialRes = false;

    // ack lock
    private boolean isInitialAck = false;

    protected CoordinatorImp() throws RemoteException {
        this.serverInfo = new HashMap<>();
        this.cache = new HashSet<>();
        this.responseCollector = new HashMap<>();
        this.ackCollector = new ArrayList<>();
        this.cacheData = new DB();
    }

    @Override
    public void requirePrepare(String serverId, Message message) throws RemoteException {
        // log
        log.info("Server with id: " + serverId + " tries to start a transaction for message: " + message);
        // send the message to all the server
        serverInfo.forEach((key, value) -> {
            try {
                value.prepare(message);
            } catch (RemoteException e) {
                // handle if some server crash
                log.error("Server with id: " + key + " is unreachable in " + CacheType.REQ_PREPARE + ", try reconnection");
            }
        });
    }

    @Override
    public void accept(String serverId, Message message) throws RemoteException {
        log.info("Server with id: " + serverId + " accepted");
        // record which server accepted
        responseCollector.put(serverId, true);
        // determine if collect responses from all servers
        resultAnalyzer(message);
    }

    @Override
    public void reject(String serverId, Message message) throws RemoteException {
        log.info("Server with id: " + serverId + " rejected");
        // record which server rejected
        responseCollector.put(serverId, false);
        // determine if collect responses from all servers
        resultAnalyzer(message);
    }

    @Override
    public void ackCommit(String serverId, Message message) throws RemoteException {
        log.info("Received ack commit from server with id: " + serverId);
        // record the ack
        ackCollector.add(serverId);
        ackAnalyzer(CacheType.ACK_COMMIT, message);
    }

    @Override
    public void ackAbort(String serverId, Message message) throws RemoteException {
        log.info("Received ack abort from server with id: " + serverId);
        // record the ack
        ackCollector.add(serverId);
        ackAnalyzer(CacheType.ACK_ABORT, message);
    }

    @Override
    public void register(String serverId, String ip, int port) throws RemoteException {
        // get the server api
        try {
            Service server = (Service) Naming.lookup("rmi://" + ip + ":" + port + "/Service");
            // add the server to the server info
            serverInfo.put(serverId, server);
            log.info("Server with id: " + serverId + " is registered");
            log.info("The number of currently connected servers: " + serverInfo.size());
            // if the server recovered from crash
            executor.execute(() -> {
                // if the server is presented on the crash server set and reconnected with the coordinator
                // start data recovery
                boolean isContain = cache.contains(serverId);
                if (isContain) {
                    log.info("Server with id: " + serverId + " reconnected");
                    try {
                        // wait for connection being stable
                        Thread.sleep(5000);
                        // sync data with the server
                        server.sync(cacheData.getDB());
                        log.info("Server with id: " + serverId + " is now synchronized");
                        cache.remove(serverId);
                    } catch (RemoteException e) {
                        log.error("Server with id: " + serverId + " lost connection in SYNC");
                    } catch (InterruptedException e) {
                        log.info("Sync thread interrupted");
                    }
                }
            });
        } catch (NotBoundException | MalformedURLException e) {
            log.error("Unknown server with id: " + serverId + " ip: " + ip + " port: " + port + " requested for connection");
        }
    }

    /**
     * determine if there is a false in the response map
     *
     * @return true if there is at least one false in the map
     */
    public boolean resultAnalyzer() {
        // determine if there is a false in the response map
        boolean value = responseCollector.containsValue(false);
        // clear the collector for next time use
        responseCollector.clear();
        return value;
    }

    /**
     * analyze if the message should commit or abort
     *
     * @param message the message
     */
    private void resultAnalyzer(Message message) {
        // when received the first response of commit or abort from one server
        // start timing
        // if we cannot receive responses from all server, then timeout
        // continue to the transaction
        if (!isInitialRes) {
            // set the response lock when first response arrive
            isInitialRes = true;
            executor.execute(() -> {
                try {
                    // timeout for 1 second
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.info("Response collecting interrupted");
                }
                // if after 1 second, we don't receive responses from all server, those servers will be marked as unresponsive servers
                if (responseCollector.size() == serverInfo.size()) {
                    // if abort
                    if (resultAnalyzer()) {
                        // call abort
                        log.info("Abort message sent, the message: " + message);
                        serverInfo.forEach((key, value) -> {
                            try {
                                value.abort(message);
                            } catch (RemoteException e) {
                                // handle if some server crash
                                log.error("Server with id: " + key + " is unreachable in " + CacheType.REJECT + ", try reconnection");
                            }
                        });
                    } else {
                        log.info("Received res from all servers with type: " + "COMMIT");
                        // call commit
                        log.info("Commit message sent, the message: " + message);
                        serverInfo.forEach((key, value) -> {
                            try {
                                value.commit(message);
                            } catch (RemoteException e) {
                                // handle if some server crash
                                log.error("Server with id: " + key + " is unreachable in " + CacheType.ACCEPT + ", try reconnection");
                            }
                        });
                    }
                } else {
                    // cache the unresponsive server
                    // check which servers are unresponsive
                    Set<String> unresponsiveServers = new HashSet<>();
                    for (Map.Entry<String, Service> entry : serverInfo.entrySet()) {
                        if (!responseCollector.containsKey(entry.getKey())) {
                            unresponsiveServers.add(entry.getKey());
                        }
                    }
                    // continue to send message to the alive servers
                    serverInfo.forEach((key, value) -> {
                        if (!unresponsiveServers.contains(key)) {
                            if (!resultAnalyzer()) {
                                log.info("Commit message sent to the server with id: " + key + ", the message: " + message);
                                try {
                                    value.commit(message);
                                } catch (RemoteException ex) {
                                    log.error("Server with id: " + key + " is unreachable in " + CacheType.ACCEPT + ", try reconnection");
                                }
                            } else {
                                log.info("Abort message sent to the server with id: " + key + ", the message: " + message);
                                try {
                                    value.abort(message);
                                } catch (RemoteException ex) {
                                    log.error("Server with id: " + key + " is unreachable in " + CacheType.REJECT + ", try reconnection");
                                }
                            }
                        }
                    });
                    unresponsiveServers.clear();
                }
                // clear the ack collector
                responseCollector.clear();
                isInitialRes = false;
            });
        }
    }

    /**
     * Analyze if receive ack from all servers in 1.5 seconds
     * record the result of the transaction
     *
     * @param type    the type of message ack abort or ack commit
     * @param message the message of the transaction
     */
    public void ackAnalyzer(CacheType type, Message message) {
        // when received the first response of commit or abort from one server
        // start timing
        // if we cannot receive ack from all server, then timeout
        if (!isInitialAck) {
            isInitialAck = true;
            // backup the data in coordinator side to sync with the crashed servers
            if (message.getType().equals(Type.PUT)) {
                cacheData.put(message.getKey(), message.getValue());
            } else {
                cacheData.delete(message.getKey());
            }
            log.info("Backup data: " + message);
            executor.execute(() -> {
                try {
                    Thread.sleep(2200);
                } catch (InterruptedException e) {
                    log.info("ACK analyzing interrupted");
                }
                if (ackCollector.size() == serverInfo.size()) {
                    log.info("Received ack from all servers with type: " + type);
                } else {
                    // cache the unresponsive server
                    // check which servers are unresponsive
                    Set<String> unresponsiveServers = new HashSet<>();
                    for (Map.Entry<String, Service> entry : serverInfo.entrySet()) {
                        if (!ackCollector.contains(entry.getKey())) {
                            unresponsiveServers.add(entry.getKey());
                        }
                    }
                    // do cache
                    unresponsiveServers.forEach((key) -> {
                        log.error("Server with id: " + key + " is unreachable in " + type + ", try reconnection");
                        setCache(key);
                    });
                }
                // clear the ack collector
                ackCollector.clear();
                isInitialAck = false;
            });
        }
    }

    /**
     * Set cache
     *
     * @param serverId the server id
     */
    public void setCache(String serverId) {
        cache.add(serverId);
    }
}
