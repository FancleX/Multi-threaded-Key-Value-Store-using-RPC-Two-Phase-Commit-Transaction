package org.neu.coordinator;

import lombok.extern.slf4j.Slf4j;
import org.neu.api.Transaction;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Coordinator initializer
 */
@Slf4j
public class Coordinator  {

    /**
     * Construct a server object and set up.
     *
     * @throws RemoteException remote exception
     */
    public Coordinator(String port) throws RemoteException, AlreadyBoundException, NumberFormatException, UnknownHostException {
        // create skeleton
        Transaction stub = new CoordinatorImp();
        // bind the stub to registry
        Registry registry = LocateRegistry.createRegistry(Integer.parseInt(port));
        registry.bind("Transaction", stub);
        log.info("Coordinator started at host: " + InetAddress.getLocalHost().getHostName() + " port: " + port);
    }

}
