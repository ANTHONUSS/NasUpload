package fr.anthonus.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;

public class SettingsManager {
    private static final File settingsFile = new File("conf/settings.json");
    public static File shareFolder;
    public static String shareURL;

    public static void loadSettings() {
        verifySettingsFile();

        System.out.println("Chargement des paramètres depuis le fichier settings.json...");
        try (FileReader reader = new FileReader(settingsFile)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

            if (!json.has("shareFolder") || !json.has("shareURL")) {
                throw new RuntimeException("Le fichier settings.json doit contenir les clés 'shareFolder' et 'shareURL'.");
            }

            if (json.get("shareFolder").isJsonNull() || json.get("shareURL").isJsonNull()) {
                throw new RuntimeException("Les valeurs des clés 'shareFolder' et 'shareURL' ne doivent pas être nulles.");
            }

            String dir = json.get("shareFolder").getAsString();
            String url = json.get("shareURL").getAsString();

            if (dir.isEmpty() || url.isEmpty()) {
                throw new RuntimeException("Les valeurs des clés 'shareFolder' et 'shareURL' ne doivent pas être vides.");
            }

            shareFolder = new File(dir);
            System.out.println("Dossier de partage : " + shareFolder.getAbsolutePath());
            shareURL = url;
            System.out.println("URL de partage : " + shareURL);

            System.out.println("Paramètres chargés avec succès.");
            System.out.println();

            System.out.println("Chargement / création du dossier de partage...");

            File permDir = new File(shareFolder, "perm");
            File tempDir = new File(shareFolder, "temp");
            if (!permDir.exists()) permDir.mkdirs();
            if (!tempDir.exists()) tempDir.mkdirs();

            System.out.println("Dossier de partage chargé avec succès.");
            System.out.println();

        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chargement des paramètres : " + e.getMessage(), e);
        }
    }

    private static void verifySettingsFile() {
        System.out.println("Vérification du fichier settings.json...");
        if (!settingsFile.exists()) {
            throw new RuntimeException("Le fichier settings.json n'existe pas");
        }
        if (!settingsFile.canRead()) {
            throw new RuntimeException("Le fichier settings.json n'est pas lisible");
        }
        System.out.println("Le fichier settings.json est valide.");
        System.out.println();
    }
}
