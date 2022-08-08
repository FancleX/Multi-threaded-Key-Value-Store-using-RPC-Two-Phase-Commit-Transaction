package org.neu.api;

import org.neu.protocol.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Transaction extends Remote {

    /**
     * Called when a server requires to start a transaction
     *
     * @param serverId id of the server
     * @param message message to be transacted
     * @throws RemoteException remote exception
     */
    void requirePrepare(String serverId, Message message) throws RemoteException;

    /**
     * Called when a server accepts the prepare request
     *
     * @param serverId id of the server
     * @param message message of the prepare request
     * @throws RemoteException remote exception
     */
    void accept(String serverId, Message message) throws RemoteException;

    /**
     * Called when a server reject the prepare request
     *
     * @param serverId id of the server
     * @param message message of the prepare request
     * @throws RemoteException remote exception
     */
    void reject(String serverId, Message message) throws RemoteException;

    /**
     * Called when a server committed the data
     *
     * @param serverId id of the server
     * @param message committed message
     * @throws RemoteException remote exception
     */
    void ackCommit(String serverId, Message message) throws RemoteException;

    /**
     * Called when a server aborted the data
     *
     * @param serverId id of the server
     * @param message aborted message
     * @throws RemoteException remote exception
     */
    void ackAbort(String serverId, Message message) throws RemoteException;

    /**
     * Use for server register itself on the coordinator, if the server is found absent in transactions
     * it will trigger the synchronization thread
     *
     * @param serverId id of the server
     * @param ip ip of the server
     * @param port port of the server
     * @throws RemoteException remote exception
     */
    void register(String serverId, String ip, int port) throws RemoteException;

}
