package org.neu.server;

import lombok.extern.slf4j.Slf4j;
import org.neu.api.Client;
import org.neu.api.Service;
import org.neu.api.Transaction;
import org.neu.db.DB;
import org.neu.protocol.Message;
import org.neu.protocol.Type;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;

/**
 * Server implementation
 */
@Slf4j
public class ServiceImp extends UnicastRemoteObject implements Service {

    // the database
    private final DB db;

    // server id
    private final String id;

    // the coordinator api
    private final Transaction coordinator;

    // the current client that sent requests to this server
    private final Map<String, Integer> currentClient;

    protected ServiceImp(DB db, String id, Transaction coordinator) throws RemoteException {
        this.db = db;
        this.id = id;
        this.coordinator = coordinator;
        this.currentClient = new HashMap<>();
    }

    @Override
    public String doGet(UUID clientId, String key) throws RemoteException {
        log.info("Received the GET request from client id: " + clientId + ": key: " + key);
        // query the key in db
        String value = db.get(key);
        if (value != null) {
            log.info("Sent response for the GET request to client id: " + clientId + ": key: " + key + " value: " + value);
            return value;
        }
        String result = "key: " + key + " is not found";
        log.info("Sent response for the GET request to client id: " + clientId + ": " + result);
        return result;
    }

    @Override
    public void doPut(Message message, String hostname, int port) throws RemoteException {
        log.info("Received the PUT request from client id: " + message.getClientId() + ": key: " + message.getKey() + " value: " + message.getValue());
        currentClient.put(hostname, port);
        // call coordinator to start a transaction
        coordinator.requirePrepare(id ,message);
    }

    @Override
    public void doDelete(Message message, String hostname, int port) throws RemoteException {
        log.info("Received the DELETE request from client id: " + message.getClientId() + ": key: " + message.getKey());
        currentClient.put(hostname, port);
        // call coordinator to start a transaction
        coordinator.requirePrepare(id ,message);
    }

    @Override
    public void prepare(Message message) throws RemoteException {
        log.info("Prepare for message: " + message);
        // query locally
        if (message.getType().equals(Type.PUT)) {
            if (!db.isContain(message.getKey())) {
                // call accept
                log.info("Vote for COMMIT");
                coordinator.accept(id, message);
            } else {
                // call reject
                log.info("Vote for ABORT");
                coordinator.reject(id, message);
            }
        } else {
            // opposite logic for delete
            if (db.isContain(message.getKey())) {
                // call accept
                log.info("Vote for COMMIT");
                coordinator.accept(id, message);
            } else {
                // call reject
                log.info("Vote for ABORT");
                coordinator.reject(id, message);
            }
        }
    }

    @Override
    public void commit(Message message) throws RemoteException {
        String result = null;
        // send ack
        coordinator.ackCommit(id, message);
        log.info("Message committed, the message: " + message);
        switch (message.getType()) {
            case PUT:
                // do operation
                db.put(message.getKey(), message.getValue());
                result = "key: " + message.getKey() + " value: " + message.getValue() + " has been stored";
                break;
            case DELETE:
                // do operation
                db.delete(message.getKey());
                result = "key: " + message.getKey() + " has been deleted";
                break;
        }
        // send response to the client
        responseTo(result, message);
    }

    @Override
    public void abort(Message message) throws RemoteException {
        String result;
        log.info("Message aborted, the message: " + message);
        // send ack
        coordinator.ackAbort(id, message);
        if (message.getType().equals(Type.PUT)) {
            result = "key: " + message.getKey() + " value: " + db.get(message.getKey()) + " is immutable";
        } else {
            result = "key: " + message.getKey() +  " is not found";
        }
        // send the response to the client
        responseTo(result, message);
    }

    /**
     * Connect to the client and send the response to the client
     *
     * @param result the result of the request
     * @param message the message of the request
     */
    private void responseTo(String result, Message message) {
        if (!currentClient.isEmpty()) {
            String clientHostname = new ArrayList<>(currentClient.keySet()).get(0);
            Integer clientPort = new ArrayList<>(currentClient.values()).get(0);
            currentClient.clear();
            try {
                Client client = (Client) Naming.lookup("rmi://"+ InetAddress.getByName(clientHostname).getHostAddress() + ":" + clientPort + "/Client");
                client.setResponse(id, result, message);
                log.info("Response sent: " + result);
            } catch (NotBoundException | MalformedURLException | UnknownHostException | RemoteException e) {
                // log if the client lost connection
                log.error("Client with id: " + message.getClientId() + " lost connection in sending response of the result: " + result);
            }
        }
    }

    @Override
    public String getId() throws RemoteException {
        return id;
    }

    @Override
    public void sync(Map<String, String> data) throws RemoteException {
        // synchronize the data the with the coordinator if the server was absent in transactions
        db.addAll(data);
        log.info("Synchronized the data from the coordinator");
    }

}
