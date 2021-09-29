package server;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Set;
import java.net.*;


import java.util.Iterator;
import java.util.LinkedList;

/**
 * Classe serveur pouvant accepter plusieurs utilisateurs
 * Les messages envoyés par les utilisateurs sont broadcastés entre eux
 *
 * @author darjana.gilles | sakiman.rezal
 */
public class Server extends Thread{
    private static ServerUI serverFx ;

    private static Selector selector = null;
    private String ip;
    private int port;
    private static int nbClient = 0;

    private static ServerSocketChannel socket;

    private static LinkedList<SocketChannel> remoteList = new LinkedList<>();


    public Server(ServerUI ui, String ip, int port){
        serverFx = ui;
        this.ip=ip;
        this.port=port;
    }

    public void run(){
        try {
            // Ouverture du selector
            selector = Selector.open();
            //Ouverture du serverSocketChannel 
            socket = ServerSocketChannel.open();
            // Mode non-bloquant
            socket.configureBlocking(false);
            // Permet auserveur de ré-utiliser son adresse lorsque celui-ci se déconnecte et tente de se reconnecter
            socket.socket().setReuseAddress(true);
            socket.bind(new InetSocketAddress(ip, port));

            // Récupère une opération qui peut être effectuée parmi :
            // 1 => OP_ACCEPT   2 => OP_CONNECT
            // 3 => OP_READ     4 => OP_WRITE
            int ops = socket.validOps();
            // Enregistre le socket auprès du sélecteur
            SelectionKey selectionKey = socket.register(selector, ops, null);
            // Tant que le serveur est opérant
            while(socket.isOpen()){
                // Un sélecteur étant bloquant , on lui attribut un timeOut de 2500 ms
                selector.select(2500);
                // Génère un set de clés uniques
                Set<SelectionKey> keys = selector.selectedKeys();
                // Parcours de la collection de clés
                Iterator<SelectionKey> i = keys.iterator();
                while (i.hasNext()) {
                    SelectionKey key = i.next();
                    if (key.isAcceptable()) {
                    //Un nouveau client est accepté
                        handleAccept(socket,key);
                    }
                    else if (key.isReadable()) {
                    //Lis les I/O  
                        handleRead(key);
                    }
                    i.remove();
                }
                // Désinscrit le serveur du selecteur et ferme le serveur
                if (!serverFx.isRunning()){
                    selectionKey.cancel();
                    selector.selectNow();
                    closeConnexion();
                }
            }
        }catch (IOException e) {
            e.printStackTrace();
        }
    }



/**
 * Cette fonction se charge d'accepter et de créer une connexion avec le client
 * @param mySocket  Le socket lié au channel
 * @param key       La clé de sélection
 * @throws IOException
 */
    private static void handleAccept(ServerSocketChannel mySocket,SelectionKey key) throws IOException {

        nbClient+=1;
        System.out.println("Connexion acceptée... | En ligne : "+nbClient);

        // Accepte la connexion et passe en mode non-bloquant
        SocketChannel client = mySocket.accept();
        client.configureBlocking(false);

        // Enregistre le client au selecteur
        client.register(selector, SelectionKey.OP_READ);

        // Ajout du client dans la liste
        remoteList.add(client);
    }

/**
 * Cette fonction se charge de la lecture des données envoyés par les clients
 * @param key       La clé de sélection
 * @throws IOException
 */
    private static void handleRead(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        // Créer un buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        client.read(buffer);
        // Parse des données -> buffer
        String data = new String(buffer.array()).trim();
        // Si le client est déconnecté , on broadcast aux autres user sa déconnexion
        if (client.read(buffer) == -1){
            System.out.println(data+" s'est déco ");
            String deco = data+" s'est déco ";
            // Broadcast
            for(SocketChannel channel : remoteList ){
                if (channel.isConnected()){
                    channel.write(ByteBuffer.wrap(deco.getBytes()));
                }
            }
            // Ferme le socket | retire le client déconnecté de la liste | décremente le nombre de clients
            client.close();
            remoteList.remove(client);
            nbClient-=1;
        }
        // Sinon on récupère le message et on le broadcast
        else if (data.length() > 0) {
            // Broadcast
            for(SocketChannel channel : remoteList ){
                if (channel.isConnected()){
                    channel.write(ByteBuffer.wrap(data.getBytes()));
                }
            }
        }
        buffer.clear();
    }

     /**
      * Cette fonction se charge de fermer tous les sockets et entraîne la fermeture du serveur
     * @throws IOException
      *
      */
      public static void closeConnexion() throws IOException{
        System.out.println("Serveur is closing...");
        // Déconnecte tous les clients encore connectés
        for(SocketChannel channel : remoteList ){
            if (channel.isConnected()){
                channel.close();
            }
        }
        remoteList.clear();
        nbClient = 0;
        socket.close();
        serverFx.log("Fermeture du serveur");
        // Effectue une pause le temps que le serveur se déconnecte
        try {
            sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        serverFx.setNonRunningState();
      }
}






