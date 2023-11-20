import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class FileGenerator {

    public static void main(String[] args) {
        String directoryPath = "src/GeneratedFile"; //the files path
        int numberOfFiles = 20;
        long[] fileSizes = {5 * 1024, 10 * 1024, 15 * 1024,
                20 * 1024, 25 * 1024, 50 * 1024, 100 * 1024, 150 * 1024,
                200 * 1024, 250 * 1024, 500 * 1024, 1 * 1024 * 1024,
                5 * 1024 * 1024, 10 * 1024 * 1024, 20 * 1024 * 1024,
                50 * 1024 * 1024, 70 * 1024 * 1024, 80 * 1024 * 1024,
                90 * 1024 * 1024, 100 * 1024 * 1024};

        try {
            generateFiles(directoryPath, numberOfFiles, fileSizes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void generateFiles(String directoryPath, int numberOfFiles, long[] fileSizes) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        Random random = new Random();
        for (int i = 0; i < numberOfFiles; i++) {
            File file = new File(directoryPath + "/file" + i + ".txt");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                long fileSize = fileSizes[i];
                for (int j = 0; j < fileSize; j++) {
                    char randomCharacter = (char) ('a' + random.nextInt(26));
                    writer.write(randomCharacter);
                }
            }
        }
        System.out.println("Files generated successfully.");
    }
}