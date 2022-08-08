package org.neu.api;

import org.neu.protocol.Message;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Client remote api
 */
public interface Client extends Remote {

    /**
     * Use for server to asynchronously give response to the client request
     *
     * @param serverId server id
     * @param response server response
     * @throws RemoteException remote execption
     */
    void setResponse(String serverId, String response, Message message) throws RemoteException;

}
