package org.neu.coordinator;

import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;

/**
 * Coordinator driver
 */
public class CoordinatorDriver {

    public static void main(String[] args) {
        if (args.length > 0) {
            String port = args[0];
            try {
                // start coordinator
                new Coordinator(port);
            } catch (RemoteException | AlreadyBoundException | NumberFormatException | UnknownHostException e) {
                System.out.println("Cannot register at the given port, please try again");
                System.exit(1);
            }
        } else {
            System.out.println("Please run the program: java -jar CoordinatorDriver.jar <port>");
        }
    }
}
