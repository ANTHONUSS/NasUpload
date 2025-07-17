#include <filesystem>
#include <iostream>
#include "../lib/tinyfiledialogs.h"
#include <fstream>
#include <vector>
#include <string>
#include <sstream>
#include <random>
#include <chrono>
#include <iomanip>
#include <windows.h>
#include <conio.h>

std::string shareFolder;
std::string shareURL;

void loadSettings() {
    std::ifstream settingsFile("settings.txt");

    if (settingsFile.is_open()) {
        std::getline(settingsFile, shareFolder);
        std::getline(settingsFile, shareURL);
        settingsFile.close();

        std::cout << "Ligne 1 (shareFolder) : " << shareFolder << std::endl;
        std::cout << "Ligne 2 (shareURL) : " << shareURL << std::endl;
    } else {
        std::cerr << "Erreur lors de l'ouverture du fichier settings.txt" << std::endl;
        _getch();
        exit(1);
    }
}

std::vector<std::string> splitFiles(const char *files) {
    std::vector<std::string> result;
    std::stringstream ss(files);
    std::string item;
    while (std::getline(ss, item, '|')) {
        if (!item.empty()) {
            result.push_back(item);
        }
    }
    return result;
}

std::string randomName(size_t length) {
    const std::string chars =
        "abcdefghijklmnopqrstuvwxyz"
        "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        "0123456789";
    std::random_device rd;
    std::mt19937 gen(rd());
    std::uniform_int_distribution<> distrib(0, chars.size() - 1);

    std::string result;
    for (size_t i = 0; i < length; ++i) {
        result += chars[distrib(gen)];
    }
    return result;
}

bool isUrlSafeChar(unsigned char c) {
    return std::isalnum(c) || c == '-' || c == '_' || c == '.' || c == '~' || c == '/' || c == ':';
}

std::string urlEncode(const std::string& input) {
    std::ostringstream encoded;
    for (unsigned char c : input) {
        if (isUrlSafeChar(c)) {
            encoded << c;
        } else {
            encoded << '%' << std::uppercase << std::hex << std::setw(2) << std::setfill('0') << (int)c;
        }
    }
    return encoded.str();
}

void copyFileWithProgress(const std::filesystem::path& src, const std::filesystem::path& dest, int currentFileIndex, int totalFiles) { //ça c'est chatGPT
    std::ifstream in(src, std::ios::binary);
    std::ofstream out(dest, std::ios::binary);
    if (!in || !out) throw std::runtime_error("Erreur ouverture fichier");

    const std::size_t bufferSize = 1024 * 1024; // 1 Mo
    std::vector<char> buffer(bufferSize);
    std::uintmax_t totalSize = std::filesystem::file_size(src);
    std::uintmax_t copied = 0;

    auto start = std::chrono::steady_clock::now();
    while (in) {
        in.read(buffer.data(), buffer.size());
        std::streamsize bytes = in.gcount();
        out.write(buffer.data(), bytes);
        copied += bytes;

        auto now = std::chrono::steady_clock::now();
        double elapsed = std::chrono::duration<double>(now - start).count();
        double speed = copied / (1024.0 * 1024.0) / (elapsed > 0 ? elapsed : 1); // Mo/s
        double percent = (double)copied / totalSize * 100.0;
        double remaining = speed > 0 ? (totalSize - copied) / (1024.0 * 1024.0) / speed : 0;

        std::cout << "\r" << std::string(120, ' ') << "\r" << std::flush;
        std::cout << "[Fichier " << currentFileIndex << "/" << totalFiles << "] -> "
                  << std::fixed << std::setprecision(1)
                  << percent << "% | "
                  << speed << " Mo/s | "
                  << "Temps restant: " << (int)remaining << "s   "
                  << std::flush;
    }
    std::cout << std::endl;
}

void copyToClipboard(const std::string& text) {
    if (OpenClipboard(nullptr)) {
        EmptyClipboard();
        HGLOBAL hGlob = GlobalAlloc(GMEM_FIXED, text.size() + 1);
        if (hGlob) {
            memcpy(hGlob, text.c_str(), text.size() + 1);
            SetClipboardData(CF_TEXT, hGlob);
        }
        CloseClipboard();
        GlobalFree(hGlob);
    }
    std::cout << "Liens copiés dans le presse papier." << std::endl;
}

int main() {
    SetConsoleOutputCP(CP_UTF8);

    std::cout << "Chargement des paramètres depuis settings.txt..." << std::endl;
    loadSettings();

    std::cout << "Veuillez choisir le(s) fichier(s) à partager." << std::endl;
    const char *file = tinyfd_openFileDialog(
        "NasUpload - Sélection de fichier",
        "",
        0,
        NULL,
        NULL,
        1
    );

    if (!file) {
        std::cerr << "Aucun fichier sélectionné." << std::endl;
        _getch();
        return 1;
    }

    std::vector<std::string> files = splitFiles(file);
    bool isMultiple;
    if (files.size() > 1) {
        isMultiple = true;
        std::cout << "Fichiers sélectionnés : " << std::endl;
        for (const auto &f: files) {
            std::cout << "- " << f << std::endl;
        }
    } else {
        std::cout << "Fichier sélectionné : " << files[0] << std::endl;
        isMultiple = false;
    }

    int resultPermOrTemp = tinyfd_messageBox(
        "NasUpload",
        (std::string("Voulez vous envoyer ") + (isMultiple ? "les fichiers" : "le fichier") + " de façon permanente ?").c_str(),
        "yesno",
        "question",
        1
    );
    std::string choosenDir;
    bool isPermanent;
    if (resultPermOrTemp == 1) {
        choosenDir = shareFolder + "/perm";
        isPermanent = true;
    } else {
        choosenDir = shareFolder + "/temp";
        isPermanent = false;
    }

    int resultRand = tinyfd_messageBox(
        "NasUpload",
        (std::string("Voulez vous randomizer le nom ") + (isMultiple ? "des fichiers" : "du fichier") + " ?").c_str(),
        "yesno",
        "question",
        1
    );
    bool isRandomized;
    if (resultRand == 1) {
        isRandomized = true;
    } else {
        isRandomized = false;
    }

    bool isInFolder = false;
    if (isMultiple) {
        int resultIsInFolder = tinyfd_messageBox(
            "NasUpload",
            "Voulez vous envoyer les fichiers dans un dossier ?",
            "yesno",
            "question",
            1
        );
        if (resultIsInFolder) {
            choosenDir += "/" + randomName(20);
            isInFolder = true;
        } else {
            isInFolder = false;
        }

    }

    std::filesystem::create_directories(choosenDir);

    std::cout << "Envoi des fichiers..." << std::endl;
    std::vector<std::string> generatedURLs;
    for (size_t i = 0; i < files.size(); ++i) {
        const auto& filePath = files[i];
        std::filesystem::path src(filePath);
        std::string fileName = isRandomized ? randomName(20) + src.extension().string() : src.filename().string();
        std::filesystem::path dest = std::filesystem::path(choosenDir) / fileName;
        try {
            copyFileWithProgress(src, dest, i + 1, files.size());
        } catch (const std::filesystem::filesystem_error& e) {
            std::cerr << "Erreur lors de la copie du fichier " << src << " vers " << dest << ": " << e.what() << std::endl;
            continue;
        }

        std::cout << "Fichier " << src.filename() << " copié vers " << dest << std::endl;
        std::string fileURL = shareURL + (isPermanent ? "/perm/" : "/temp/") + (isInFolder ? dest.parent_path().filename().string() + "/" : "") + fileName;
        fileURL = urlEncode(fileURL);
        generatedURLs.push_back(fileURL);
    }

    if (isInFolder) {
        std::filesystem::path htaccessPath = choosenDir + "/.htaccess";
        std::ofstream htaccessFile(htaccessPath);
        if (htaccessFile.is_open()) {
            htaccessFile << "Options +Indexes";
            htaccessFile.close();
            std::cout << "Fichier .htaccess créé dans " << choosenDir << std::endl;
        } else {
            std::cerr << "Erreur lors de la création du fichier .htaccess dans " << choosenDir << std::endl;
        }
    }

    std::cout << "Fichiers envoyés avec succès !" << std::endl;

    std::string urlsText;
    for (const auto& url : generatedURLs) {
        std::cout << url << std::endl;
        urlsText += url + "\n";
    }
    copyToClipboard(urlsText);

    std::cout << "Appuyez sur une touche pour quitter..." << std::endl;
    _getch();


    return 0;
}
