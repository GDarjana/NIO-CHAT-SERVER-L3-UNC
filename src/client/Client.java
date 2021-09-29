package client;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;


/**
 * Classe client pouvant se connecter à un serveur 
 * Peut envoyer des messages au serveur qui seront broadcastés aux autres utilisateurs et à lui-même
 * 
 * @author darjana.gilles | sakiman.rezal
 */

public class Client extends Thread{
    private static ClientUI clientFx;
    
    private static Selector selector;
    private static SocketChannel sc;
    private String ip;
    private int port;
    private static String nickname;

    public Client(ClientUI ui,String ip,int port,String username) {
        clientFx=ui;
        this.ip = ip;
        this.port = port;
        nickname = username;
    }

    public void run(){
        try {
            // Ouverture du selector
            selector = Selector.open();
            // Ouverture du socketChannel 
            sc = SocketChannel.open(new InetSocketAddress(ip, port));
            // Mode non-bloquant
            sc.configureBlocking(false);

            // Récupère une opération qui peut être effectuée parmi :
            // 1 => OP_CONNECT   2 => OP_READ 
            // 3 => OP_WRITE
            int ops = sc.validOps();
            // Enregistre le socket auprès du sélecteur
            sc.register(selector, ops, null);
            System.out.println("Connexion au serveur "+(sc.isConnected()? "réussi ✅" : "échoué 🛑"));
            // Tant que le client est opérant
            while(sc.isConnected()){
                // Si le client se déconnecte , envoi l'information au serveur et ferme son socket
                if(!clientFx.isRunning()){
                    sc.write(ByteBuffer.wrap(nickname.getBytes()));
                    sc.close();
                    clientFx.setDisconnectedState();
                    break;
                }
                // Un sélecteur étant bloquant , on lui attribut un timeOut de 2500 ms
                selector.select(2500);
                // Génère un set de clés uniques
                Set<SelectionKey> keys = selector.selectedKeys();
                // Parcours de la collection de clés
                Iterator<SelectionKey> iterator = keys.iterator();
                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    if(key.isReadable()){
                        //Lis les I/O du client 
                        handleRead(key);
                    }
                    iterator.remove(); 
                }                   
            }
        // Si le client tente de se connecté au serveur qui est hors-ligne
        }catch (ConnectException e) {
            clientFx.setDisconnectedState();
            System.out.println("La connection a été refusé");
            clientFx.appendMessage("La connection a été refusé\n");
        }catch (IOException i){
            i.printStackTrace();
        }finally{
            try {
                if(sc!=null) {
                    sc.close();
                    System.out.println("Je suis hors ligne");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }



/**
 * Cette fonction se charge de la lecture des données envoyés par le serveur
 * @param key
 * @throws IOException
 */
    public static void handleRead(SelectionKey key) throws IOException{
        SocketChannel socketChannel = (SocketChannel) key.channel();
        // Créer un buffer
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        //Lecture
        socketChannel.read(buffer);
        // Parse des données -> buffer
        String data = new String(buffer.array()).trim();
        // Si le serveur est déconnecté , on ferme le socket du client et on stop son interface
        if (socketChannel.read(buffer)==-1){
            System.out.println("Le serveur est hors ligne");
            sc.close();
            clientFx.setDisconnectedState();
        }
        // Si il y a un message en l'affiche
        if (data.length() > 0) {
            System.out.println(data);
            clientFx.appendMessage(data);
        }
        buffer.clear();
    }


    
/**
 * Fonction d'envoi de message
 * @param message
 */
    public  void addMessage(String message) {
        try {
            sc.write(ByteBuffer.wrap((nickname+" : "+message).getBytes()));
        }catch (IOException e) {
            e.printStackTrace();
        }
    }
}

