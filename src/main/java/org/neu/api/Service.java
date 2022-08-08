package org.neu.api;

import org.neu.protocol.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.UUID;

/**
 * The RMI remote interface, it provides methods for get, put, delete requests.
 */
public interface Service extends Remote {

    /**
     * Process get request.
     *
     * @param key the key to be found in storage
     * @return the value of the key if the key is present, otherwise give error
     * @throws RemoteException remote exception
     */
    String doGet(UUID clientId, String key) throws RemoteException;

    /**
     * Process put request.
     *
     * @param message message
     * @throws RemoteException remote exception
     */
    void doPut(Message message, String hostname, int port) throws RemoteException;

    /**
     * Process delete request.
     *
     * @param message message
     * @throws RemoteException remote exception
     */
    void doDelete(Message message, String hostname, int port) throws RemoteException;

    /**
     * Receive a call from the coordinator and prepare for the transaction
     *
     * @param message message to be transacted
     * @throws RemoteException remote exception
     */
    void prepare(Message message) throws RemoteException;

    /**
     * Receive a call from the coordinator and commit the data in this transaction
     *
     * @param message message to be committed
     * @throws RemoteException remote exception
     */
    void commit(Message message) throws RemoteException;

    /**
     * Receive a call from the coordinator and abort the data in this transaction
     *
     * @param message message to be aborted
     * @throws RemoteException remote exception
     */
    void abort(Message message) throws RemoteException;

    /**
     * Use for client to get the identity of the server
     *
     * @return id of the server
     * @throws RemoteException remote exception
     */
    String getId() throws RemoteException;

    /**
     * Use for the server to synchronize data with the coordinator when the server is absent in transactions
     *
     * @param data the transaction data to be synchronized
     * @throws RemoteException remote exception
     */
    void sync(Map<String, String> data) throws RemoteException;
}

