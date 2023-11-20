import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.common.AttributeKey;


public class FileClient {

    private static final String SERVER_IP = "127.0.0.1"; //localhost
    private static final int SERVER_PORT = 1964;
    private static final String SOURCE_FOLDER = "src/GeneratedFile";

    private static final Tracer tracer = configureOpenTelemetry().getTracer("FileClientTracer");

    public static void main(String[] args) {
        try (Stream<Path> paths = Files.walk(Paths.get(SOURCE_FOLDER))) {
            paths.filter(Files::isRegularFile).forEach(FileClient::sendFileToServer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static OpenTelemetry configureOpenTelemetry() {
        OtlpGrpcSpanExporter spanExporter = OtlpGrpcSpanExporter.builder()
                .setEndpoint("http://localhost:4317") //localhost used
                .build();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(BatchSpanProcessor.builder(spanExporter).build())
                .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), "FileClientService")))
                .build();

        return OpenTelemetrySdk.builder().setTracerProvider(tracerProvider).buildAndRegisterGlobal();
    }

    private static void sendFileToServer(Path path) {
        Span span = tracer.spanBuilder("sendFileToServer").startSpan();

        try (Scope scope = span.makeCurrent()) {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(SERVER_IP, SERVER_PORT);
                 FileInputStream fis = new FileInputStream(path.toFile());
                 BufferedInputStream bis = new BufferedInputStream(fis);
                 ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 ZipOutputStream zos = new ZipOutputStream(baos);
                 OutputStream os = socket.getOutputStream()) {

                //compress file
                zos.putNextEntry(new java.util.zip.ZipEntry(path.getFileName().toString()));
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = bis.read(buffer)) != -1) {
                    zos.write(buffer, 0, bytesRead);
                }
                zos.closeEntry();
                zos.finish();

                //calculates checksum
                byte[] compressedData = baos.toByteArray();
                MessageDigest md = MessageDigest.getInstance("MD5");
                byte[] checksum = md.digest(compressedData);

                //sends file data
                DataOutputStream dos = new DataOutputStream(os);
                dos.writeUTF(path.getFileName().toString());
                dos.writeUTF(convertToHex(checksum));
                dos.writeLong(compressedData.length);
                dos.write(compressedData);
                dos.flush();

                span.setStatus(StatusCode.OK, "File sent successfully");
            } catch (Exception e) {
                span.recordException(e);
                span.setStatus(StatusCode.ERROR, "Error sending file");
                e.printStackTrace();
            }
        } finally {
            span.end(); //span ends here
        }
    }

    private static String convertToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}