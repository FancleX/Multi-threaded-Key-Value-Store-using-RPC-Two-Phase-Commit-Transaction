package org.neu.client;

import lombok.extern.slf4j.Slf4j;
import org.neu.api.Service;
import org.neu.protocol.Message;
import org.neu.protocol.Type;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.rmi.AlreadyBoundException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * RMI client.
 */
@Slf4j
public class Client extends UnicastRemoteObject implements org.neu.api.Client {

    // client id
    private static final UUID clientId = UUID.randomUUID();

    // server collection
    private static final Map<Integer, Map.Entry<String, Service>> serverInfo = new HashMap<>();

    // response collector to store the response from the server
    private static final Map<Message, String> resCollector = new HashMap<>();

    // request collector to check if no response for some request, also carry a timestamp of the request sent
    private static final Map<Message, Long> requestCollector = new HashMap<>();

    // thread pool for asynchronous purposes
    private static final Executor executor = Executors.newFixedThreadPool(3);

    // the client port
    private static int clientPort;

    protected Client() throws RemoteException {
        monitor();
    }

    public static void main(String[] args) {
        if (args.length == 11) {
            try {
                log.info("Client started, your ID is " + clientId);
                // get client registry port
                clientPort = Integer.parseInt(args[10]);
                // register the client
                Registry registry = LocateRegistry.createRegistry(clientPort);
                registry.bind("Client", new Client());
                // get the remote interface
                for (int i = 0; i < 5; i++) {
                    Service service = (Service) Naming.lookup("rmi://" + args[i * 2] + ":" + args[i * 2 + 1] + "/Service");
                    String serverId = service.getId();
                    Map.Entry<String, Service> entry = Map.entry(serverId, service);
                    serverInfo.put(i, entry);
                    log.info("Connect to server: " + i + " serverId: " + serverId);
                }

                // start prepopulate thread
                executor.execute(() -> {
                    // get random key value pairs
                    Map<String, String> prepopulate = prepopulate();
                    ArrayList<Map.Entry<String, String>> entries = new ArrayList<>(prepopulate.entrySet());
                    Random random = new Random();
                    log.info("Pre-populating ... ");
                    for (int i = 0; i < 5; i++) {
                        // randomly choose a server to request
                        int num = random.nextInt(4);
                        Service service = serverInfo.get(num).getValue();
                        try {
                            // send put request
                            Message message = new Message(UUID.randomUUID(), Type.PUT, entries.get(i).getKey(), entries.get(i).getValue(), clientId);
                            service.doPut(message, InetAddress.getLocalHost().getHostName(), clientPort);
                            requestCollector.put(message, System.currentTimeMillis());
                        } catch (RemoteException | UnknownHostException e) {
                            log.info("Server " + i + " is not responsible in pre-population");
                        }
                        try {
                            Thread.sleep(3500);
                        } catch (InterruptedException e) {
                            log.info("Pre-populating interrupt");
                        }
                    }
                    log.info("Pre-populate completed");
                });

                // the thread to take care of user interface
                executor.execute(() -> {
                    try {
                        // wait for the pre-populate done
                        Thread.sleep(3600*5);
                        // start ui
                        userInterface();
                    } catch (IOException | InterruptedException e) {
                        log.info("Failed in starting user interface");
                    }
                });
            } catch (NotBoundException | IOException e) {
                System.out.println("Cannot find the stub at the given hostnames and ports, or some server is unavailable");
            } catch (AlreadyBoundException e) {
                System.out.println("Cannot register the client in the given port");
            }
        } else {
            System.out.println("Please run the program: java -jar client.jar" +
                    " <hostname1> <port1> <hostname2> <port2> <hostname3> <port3> <hostname4> <port4> <hostname5> <port5> <client port>");
        }
    }

    /**
     * Pre-populate a map with 5 entries with autogenerate strings.
     *
     * @return the map with pre-populated values
     */
    public static Map<String, String> prepopulate() {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            map.put(autoGenerateString(), autoGenerateString());
        }
        return map;
    }

    /**
     * Autogenerate a string with length of 5.
     *
     * @return autogenerated string
     */
    public static String autoGenerateString() {
        String range = "abcdefghijklmnopqrstuvwxyz1234567890";
        Random random = new Random();
        char[] buffer = new char[5];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = range.charAt(random.nextInt(range.length()));
        }
        return new String(buffer);
    }

    /**
     * user interface
     *
     * @throws IOException error in system read in
     */
    public static void userInterface() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        // user interaction
        while (true) {
            log.info("We have " + serverInfo.keySet().size() + " servers are ready for you: " + Arrays.toString(serverInfo.keySet().toArray()));
            log.info("Please specify one server to use");
            log.info("Please choose a server or quit to exit");
            String serverId = reader.readLine();
            if (serverId.equalsIgnoreCase("quit")) {
                break;
            }
            int id = -1;
            try {
                id = Integer.parseInt(serverId);
                if (id < 0 || id > 4) {
                    throw new InvalidParameterException();
                }
                Map.Entry<String, Service> entry = serverInfo.get(id);
                log.info("Please input: get, put, delete to use service");
                String input = reader.readLine();
                // remove potential space
                input = input.replaceAll("\\s", "");
                // check input and handle
                if ("get".equalsIgnoreCase(input)) {
                    log.info("Please input a key: ");
                    String key = reader.readLine();
                    log.info("Sent Get request: key = " + key + " to server " + id);
                    log.info("Receive response from server " + id + ", message: " + entry.getValue().doGet(clientId, key));
                } else if ("put".equalsIgnoreCase(input)) {
                    log.info("Please input a key: ");
                    String key = reader.readLine();
                    log.info("Please input a value: ");
                    String value = reader.readLine();
                    log.info("Sent Put request: key = " + key + " value = " + value + " to server " + id);
                    Message message = new Message(UUID.randomUUID(), Type.PUT, key, value, clientId);
                    entry.getValue().doPut(message, InetAddress.getLocalHost().getHostName(), clientPort);
                    requestCollector.put(message, System.currentTimeMillis());
                } else if ("delete".equalsIgnoreCase(input)) {
                    log.info("Please input a key: ");
                    String key = reader.readLine();
                    log.info("Sent Delete request: key = " + key + " to server " + id);
                    Message message = new Message(UUID.randomUUID(), Type.DELETE, key, null, clientId);
                    entry.getValue().doDelete(message, InetAddress.getLocalHost().getHostName(), clientPort);
                    requestCollector.put(message, System.currentTimeMillis());
                } else {
                    throw new InvalidParameterException();
                }
            } catch (InvalidParameterException | NumberFormatException e) {
                log.error("Invalid input, please check your input and try again");
            } catch (RemoteException e) {
                log.error("Lost connection with Server with id: " + serverId);
                // remove the unresponsive server when the client send the request to the server
                serverInfo.remove(id);
            }
        }
        // on close
        reader.close();
        log.info("Client exited ...");
        System.exit(0);
    }

    @Override
    public void setResponse(String serverId, String response, Message message) throws RemoteException {
        // server gives response for the request
        // store it in the response collector
        for (Map.Entry<Integer, Map.Entry<String, Service>> entry : serverInfo.entrySet()) {
            if (entry.getValue().getKey().equals(serverId)) {
                resCollector.put(message, "Receive response from server " + entry.getKey() + ", message: " + response);
                break;
            }
        }
    }

    /**
     * Listen to the response collector, if a message presented then log it
     * if a message doesn't get response in 5 seconds it will be marked timeout
     */
    public void monitor() {
        executor.execute(() -> {
            while (true) {
                Iterator<Map.Entry<Message, Long>> iterator = requestCollector.entrySet().iterator();
                while (iterator.hasNext()) {
                    Map.Entry<Message, Long> next = iterator.next();
                    // if the request doesn't get a response in 5 seconds will be logged timeout
                    if (System.currentTimeMillis() - next.getValue() > 5000) {
                        log.error("Cannot receive response for the request: " + next.getKey());
                        iterator.remove();
                    } else {
                        if (!resCollector.isEmpty()) {
                            String result = resCollector.get(next.getKey());
                            if (result != null) {
                                // log the response from server
                                log.info(result);
                                resCollector.remove(next.getKey());
                                iterator.remove();
                            }
                        }
                    }
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    log.info("Monitor interrupted");
                }
            }
        });
    }
}

