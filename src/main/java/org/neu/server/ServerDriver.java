package org.neu.server;


import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;

/**
 * Server driver
 */
public class ServerDriver {

    public static void main(String[] args) {
        if (args.length == 3) {
            String port = args[0];
            String coordinatorHostname = args[1];
            String coordinatorPort = args[2];
            try {
                // start server
                new Server(port, coordinatorHostname, coordinatorPort);
            } catch (RemoteException | AlreadyBoundException | NumberFormatException e) {
                System.out.println(e.getMessage());
                System.out.println("Cannot register at the given port, please try again");
                System.exit(1);
            } catch (MalformedURLException | NotBoundException | UnknownHostException e) {
                System.out.println("Cannot connect to the coordinator, please make sure the coordinator hostname and port are correct");
                System.exit(1);
            }
        } else {
            System.out.println("Please run the program: java -jar ServerDriver.jar <server port> <coordinator hostname> <coordinator port>");
        }
    }
}
