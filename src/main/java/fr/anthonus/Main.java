package fr.anthonus;

import fr.anthonus.utils.SettingsManager;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        // Chargement des paramètres
        SettingsManager.loadSettings();

        File chosenDir = null;
        boolean isPermanent = false;
        boolean isMultiple = false;
        boolean isInFile = false;
        Scanner sc = new Scanner(System.in);

        // récupération du fichier à envoyer
        System.out.println("Veuillez sélectionner les fichiers à envoyer");

        Frame frame = new Frame();
        FileDialog dialog = new FileDialog(frame, "NasUpload", FileDialog.LOAD);
        dialog.setMultipleMode(true);
        dialog.setVisible(true);

        File[] chosenFiles = dialog.getFiles();

        frame.dispose();

        if (chosenFiles.length == 0) {
            System.out.println("Aucun fichier sélectionné.");
            return;
        }
        isMultiple = chosenFiles.length > 1;


        System.out.println("Fichiers sélectionnés :");
        for (File file : chosenFiles) {
            System.out.println("- " + file.getAbsolutePath());
        }
        System.out.println();

        // fichier permanent ou temporaire
        System.out.println("Voulez-vous envoyer ces fichiers de manière permanente ? (O/N)");
        while (true) {
            String answer = sc.nextLine();
            if (answer.equalsIgnoreCase("O")) {
                chosenDir = new File(SettingsManager.shareFolder, "perm");
                isPermanent = true;
                break;
            } else if (answer.equalsIgnoreCase("N")) {
                chosenDir = new File(SettingsManager.shareFolder, "temp");
                isPermanent = false;
                break;
            } else {
                Toolkit.getDefaultToolkit().beep();
            }
        }
        System.out.println();

        if (isMultiple) {
            System.out.println("voulez-vous envoyer ces fichiers dans un dossier ? (O/N)");
            while (true) {
                String answer = sc.nextLine();
                if (answer.equalsIgnoreCase("O")) {
                    isInFile = true;
                    break;
                } else if (answer.equalsIgnoreCase("N")) {
                    isInFile = false;
                    break;
                } else {
                    Toolkit.getDefaultToolkit().beep();
                }
            }
        }
        System.out.println();

        // envoi des fichiers
        System.out.println("Envoi des fichiers...");
        long filesSize = 0;
        for (File file : chosenFiles) filesSize += file.length();
        long bytesCopied = 0;
        int bufferSize = (int) Math.max(4096, Math.min(filesSize / 100, 100 * 1024 * 1024));
        long startTime = System.currentTimeMillis();

        Path destinationPath;
        if (isInFile) {
            chosenDir = new File(chosenDir, randomString(20));
            try {
                Files.createDirectories(chosenDir.toPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        for (File file : chosenFiles) {
            destinationPath = new File(chosenDir, file.getName()).toPath();

            try (var inputStream = Files.newInputStream(file.toPath());
                 var outputStream = Files.newOutputStream(destinationPath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                byte[] buffer = new byte[bufferSize];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    bytesCopied += bytesRead;

                    double progress = (double) bytesCopied / filesSize;
                    double speed = calcSpeed(startTime, bytesCopied);
                    double remainingSeconds = calcTime(filesSize, bytesCopied, speed);

                    System.out.printf("\rProgression : %.2f%%, Vitesse : %.2f Mo/s, Temps restant : %s      ", progress * 100, speed, formatTime(remainingSeconds));
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        // création du htaccess
        if (isInFile){
            File htaccessFile = new File(chosenDir, ".htaccess");
            try {
                htaccessFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String htaccessContent = "Options +Indexes";
            try {
                Files.writeString(htaccessFile.toPath(), htaccessContent, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        System.out.println();
        System.out.println("Fichiers envoyés avec succès.");
        System.out.println();


        // création du lien
        if (isInFile) {
            String encodedName = URLEncoder.encode(chosenDir.getName(), StandardCharsets.UTF_8);
            String lien = SettingsManager.shareURL + (isPermanent ? "/perm/" : "/temp/") + encodedName;
            lien = lien.replaceAll("\\+", "%20");
            System.out.println("Lien : " + lien);
        } else {
            System.out.println("Liens :");
            for (File file : chosenFiles) {
                String encodedName = URLEncoder.encode(file.getName(), StandardCharsets.UTF_8);
                String lien = SettingsManager.shareURL + (isPermanent ? "/perm/" : "/temp/") + encodedName;
                lien = lien.replaceAll("\\+", "%20");
                System.out.println("- " + lien);
            }
        }

        System.out.println("Appuyez sur Entrée pour quitter.");
        sc.nextLine();
        System.exit(0);
    }

    private static String randomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static double calcSpeed(long startTime, long bytesCopied) {
        long elapsedTime = System.currentTimeMillis() - startTime;
        return (bytesCopied / (elapsedTime / 1000.0)) / (1024 * 1024);
    }

    private static double calcTime(long fileSize, long bytesCopied, double speed) {
        double remainingBytes = fileSize - bytesCopied;
        return remainingBytes / (speed * 1024 * 1024);
    }

    private static String formatTime(double seconds) {
        if (seconds >= 3600) {
            int h = (int) (seconds / 3600);
            int m = (int) ((seconds % 3600) / 60);
            return h + "h " + m + "min";
        } else if (seconds >= 60) {
            int m = (int) (seconds / 60);
            int s = (int) (seconds % 60);
            return m + "min " + s + "s";
        } else {
            return String.format("%.0fs", seconds);
        }
    }


}