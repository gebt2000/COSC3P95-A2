import javax.net.ssl.SSLServerSocketFactory;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.zip.ZipInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileServer {

    private static final int SERVER_PORT = 1964;
    private static final String DESTINATION_FOLDER = "src/GeneratedFile";
    private static final Logger logger = Logger.getLogger(FileServer.class.getName());

    public static void main(String[] args) {
        SSLServerSocketFactory ssf = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
        try (ServerSocket serverSocket = ssf.createServerSocket(SERVER_PORT)) {
            ForkJoinPool pool = new ForkJoinPool();

            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    //span
                    pool.execute(new FileHandler(clientSocket));
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error accepting client connection", e);
                    //error logging
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "ServerSocket initialization error", e);
        }
    }

    private static class FileHandler extends RecursiveAction {
        private final Socket socket;

        public FileHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        protected void compute() {
            try (DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream())) {

                String fileName = dis.readUTF();
                String clientChecksum = dis.readUTF();
                File file = new File(DESTINATION_FOLDER, fileName);

                if (file.exists()) {
                    dos.writeUTF("File already exists on server.");
                    return;
                }

                try (ZipInputStream zis = new ZipInputStream(dis);
                     FileOutputStream fos = new FileOutputStream(file);
                     BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                    zis.getNextEntry();
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    MessageDigest md = MessageDigest.getInstance("MD5");

                    while ((bytesRead = zis.read(buffer)) != -1) {
                        bos.write(buffer, 0, bytesRead);
                        md.update(buffer, 0, bytesRead);
                    }
                    bos.flush();
                    String serverChecksum = new BigInteger(1, md.digest()).toString(16);

                    if (!serverChecksum.equals(clientChecksum)) {
                        file.delete();
                        dos.writeUTF("File transfer failed due to checksum mismatch.");
                    } else {
                        dos.writeUTF("File received and verified successfully.");
                    }
                } catch (NoSuchAlgorithmException e) {
                    logger.log(Level.SEVERE, "No such algorithm for checksum", e);
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Error handling client file transfer", e);

            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Error closing socket", e);
                }
            }
        }
    }
}