package org.neu.server;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.neu.api.Service;
import org.neu.api.Transaction;
import org.neu.db.DB;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Server initializer
 */
@Slf4j
public class Server {

    /**
     * Construct a server object and set up.
     *
     * @throws RemoteException remote exception
     */
    public Server(String port, String coordinatorHostname, String coordinatorPort) throws RemoteException, AlreadyBoundException, NumberFormatException, MalformedURLException, NotBoundException, UnknownHostException {
        // create a key value storage
        DB db = new DB();
        // get the coordinator api
        Transaction coordinator = (Transaction) Naming.lookup("rmi://"+ InetAddress.getByName(coordinatorHostname).getHostAddress() + ":" + coordinatorPort + "/Transaction");
        // create stub
        String id = generateId(port);
        Service stub = new ServiceImp(db, id, coordinator);
        // bind the stub to registry
        Registry registry = LocateRegistry.createRegistry(Integer.parseInt(port));
        registry.bind("Service", stub);
        log.info("Server started at port: " + port + " with id: " + id);
        // register at coordinator
        coordinator.register(id, InetAddress.getLocalHost().getHostName(), Integer.parseInt(port));
    }

    /**
     * Use md5 to generate a unit id for the server
     *
     * @param port the server port
     * @return server id
     */
    public String generateId(String port) throws UnknownHostException {
        return DigestUtils
                .md5Hex(InetAddress.getLocalHost().getHostAddress() + port).toUpperCase();
    }

}
