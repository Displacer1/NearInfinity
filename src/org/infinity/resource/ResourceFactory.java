// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import java.awt.Component;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.filechooser.FileSystemView;

import org.infinity.NearInfinity;
import org.infinity.datatype.PriTypeBitmap;
import org.infinity.datatype.SecTypeBitmap;
import org.infinity.datatype.Song2daBitmap;
import org.infinity.datatype.Summon2daBitmap;
import org.infinity.gui.BrowserMenuBar;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.IdsBrowser;
import org.infinity.resource.are.AreResource;
import org.infinity.resource.bcs.BafResource;
import org.infinity.resource.bcs.BcsResource;
import org.infinity.resource.bcs.Compiler;
import org.infinity.resource.chu.ChuResource;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.dlg.DlgResource;
import org.infinity.resource.gam.GamResource;
import org.infinity.resource.graphics.BamResource;
import org.infinity.resource.graphics.GraphicsResource;
import org.infinity.resource.graphics.MosResource;
import org.infinity.resource.graphics.PltResource;
import org.infinity.resource.graphics.PvrzResource;
import org.infinity.resource.graphics.TisResource;
import org.infinity.resource.itm.ItmResource;
import org.infinity.resource.key.BIFFResourceEntry;
import org.infinity.resource.key.FileResourceEntry;
import org.infinity.resource.key.Keyfile;
import org.infinity.resource.key.ResourceEntry;
import org.infinity.resource.key.ResourceTreeFolder;
import org.infinity.resource.key.ResourceTreeModel;
import org.infinity.resource.mus.MusResource;
import org.infinity.resource.other.EffResource;
import org.infinity.resource.other.FntResource;
import org.infinity.resource.other.TtfResource;
import org.infinity.resource.other.UnknownResource;
import org.infinity.resource.other.VvcResource;
import org.infinity.resource.other.WfxResource;
import org.infinity.resource.pro.ProResource;
import org.infinity.resource.sav.SavResource;
import org.infinity.resource.sound.SoundResource;
import org.infinity.resource.spl.SplResource;
import org.infinity.resource.src.SrcResource;
import org.infinity.resource.sto.StoResource;
import org.infinity.resource.text.PlainTextResource;
import org.infinity.resource.to.TohResource;
import org.infinity.resource.to.TotResource;
import org.infinity.resource.var.VarResource;
import org.infinity.resource.vef.VefResource;
import org.infinity.resource.video.MveResource;
import org.infinity.resource.video.WbmResource;
import org.infinity.resource.wed.WedResource;
import org.infinity.resource.wmp.WmpResource;
import org.infinity.util.Decryptor;
import org.infinity.util.DynamicArray;
import org.infinity.util.IdsMapCache;
import org.infinity.util.Misc;
import org.infinity.util.StringResource;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

/**
 * Handles game-specific resource access.
 */
public final class ResourceFactory
{
  private static ResourceFactory instance;

  private JFileChooser fc;
  private Keyfile keyfile;
  private ResourceTreeModel treeModel;

  public static Keyfile getKeyfile()
  {
    if (getInstance() != null) {
      return getInstance().keyfile;
    } else {
      return null;
    }
  }

  public static Resource getResource(ResourceEntry entry)
  {
    return getResource(entry, null);
  }

  public static Resource getResource(ResourceEntry entry, String forcedExtension)
  {
    Resource res = null;
    try {
      String ext = (forcedExtension != null) ? forcedExtension : entry.getExtension();
      if (ext.equalsIgnoreCase("BAM")) {
        res = new BamResource(entry);
      } else if (ext.equalsIgnoreCase("TIS")) {
        res = new TisResource(entry);
      } else if (ext.equalsIgnoreCase("BMP") || ext.equalsIgnoreCase("PNG")) {
        res = new GraphicsResource(entry);
      } else if (ext.equalsIgnoreCase("MOS")) {
        res = new MosResource(entry);
      } else if (ext.equalsIgnoreCase("WAV") || ext.equalsIgnoreCase("ACM")) {
        res = new SoundResource(entry);
      } else if (ext.equalsIgnoreCase("MUS")) {
        res = new MusResource(entry);
      } else if (ext.equalsIgnoreCase("IDS") || ext.equalsIgnoreCase("2DA") ||
                 ext.equalsIgnoreCase("BIO") || ext.equalsIgnoreCase("RES") ||
                 ext.equalsIgnoreCase("INI") || ext.equalsIgnoreCase("TXT") ||
                 (ext.equalsIgnoreCase("SRC") && Profile.getEngine() == Profile.Engine.IWD2) ||
                 (Profile.isEnhancedEdition() && (ext.equalsIgnoreCase("SQL") ||
                                                  ext.equalsIgnoreCase("GUI") ||
                                                  ext.equalsIgnoreCase("LUA") ||
                                                  ext.equalsIgnoreCase("MENU") ||
                                                  ext.equalsIgnoreCase("GLSL")))) {
        res = new PlainTextResource(entry);
      } else if (ext.equalsIgnoreCase("MVE")) {
        res = new MveResource(entry);
      } else if (ext.equalsIgnoreCase("WBM")) {
        res = new WbmResource(entry);
      } else if (ext.equalsIgnoreCase("PLT") && ext.equals(forcedExtension)) {
        res = new PltResource(entry);
      } else if (ext.equalsIgnoreCase("BCS") || ext.equalsIgnoreCase("BS")) {
        res = new BcsResource(entry);
      } else if (ext.equalsIgnoreCase("ITM")) {
        res = new ItmResource(entry);
      } else if (ext.equalsIgnoreCase("EFF")) {
        res = new EffResource(entry);
      } else if (ext.equalsIgnoreCase("VEF")) {
          res = new VefResource(entry);
      } else if (ext.equalsIgnoreCase("VVC")) {
        res = new VvcResource(entry);
      } else if (ext.equalsIgnoreCase("SRC")) {
        res = new SrcResource(entry);
      } else if (ext.equalsIgnoreCase("DLG")) {
        res = new DlgResource(entry);
      } else if (ext.equalsIgnoreCase("SPL")) {
        res = new SplResource(entry);
      } else if (ext.equalsIgnoreCase("STO")) {
        res = new StoResource(entry);
      } else if (ext.equalsIgnoreCase("WMP")) {
        res = new WmpResource(entry);
      } else if (ext.equalsIgnoreCase("CHU")) {
        res = new ChuResource(entry);
      } else if (ext.equalsIgnoreCase("CRE") || ext.equalsIgnoreCase("CHR")) {
        res = new CreResource(entry);
      } else if (ext.equalsIgnoreCase("ARE")) {
        res = new AreResource(entry);
      } else if (ext.equalsIgnoreCase("WFX")) {
        res = new WfxResource(entry);
      } else if (ext.equalsIgnoreCase("PRO")) {
        res = new ProResource(entry);
      } else if (ext.equalsIgnoreCase("WED")) {
        res = new WedResource(entry);
      } else if (ext.equalsIgnoreCase("GAM")) {
        res = new GamResource(entry);
      } else if (ext.equalsIgnoreCase("SAV")) {
        res = new SavResource(entry);
      } else if (ext.equalsIgnoreCase("VAR")) {
        res = new VarResource(entry);
      } else if (ext.equalsIgnoreCase("BAF")) {
        res = new BafResource(entry);
      } else if (ext.equalsIgnoreCase("TOH")) {
        res = new TohResource(entry);
      } else if (ext.equalsIgnoreCase("TOT")) {
        res = new TotResource(entry);
      } else if (ext.equalsIgnoreCase("PVRZ") && Profile.isEnhancedEdition()) {
        res = new PvrzResource(entry);
      } else if (ext.equalsIgnoreCase("FNT") && Profile.isEnhancedEdition()) {
        res = new FntResource(entry);
      } else if (ext.equalsIgnoreCase("TTF") && Profile.isEnhancedEdition()) {
        res = new TtfResource(entry);
      } else {
        res = detectResource(entry);
        if (res == null) {
          res = new UnknownResource(entry);
        }
      }
    } catch (Exception e) {
      if (NearInfinity.getInstance() != null && !BrowserMenuBar.getInstance().ignoreReadErrors()) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(),
                                      "Error reading " + entry + '\n' + e.getMessage(),
                                      "Error", JOptionPane.ERROR_MESSAGE);
      } else {
        final String msg = String.format("Error reading %1$s @ %2$s - %3$s",
                                         entry, entry.getActualPath(), e);
        NearInfinity.getInstance().getStatusBar().setMessage(msg);
      }
      System.err.println("Error reading " + entry);
      e.printStackTrace();
    }
    return res;
  }

  /**
   * Attempts to detect the resource type from the data itself
   * and returns the respective resource class instance, or {@code null} on failure.
   */
  public static Resource detectResource(ResourceEntry entry)
  {
    Resource res = null;
    if (entry != null) {
      try {
        int[] info = entry.getResourceInfo();
        if (info.length == 2) {
          res = getResource(entry, "TIS");
        } else if (info.length == 1) {
          if (info[0] > 4) {
            byte[] data = new byte[Math.min(info[0], 24)];
            try (InputStream is = entry.getResourceDataAsStream()) {
              StreamUtils.readBytes(is, data);
            }
            String sig = DynamicArray.getString(data, 0, 4);
            if ("2DA ".equalsIgnoreCase(sig)) {
              res = getResource(entry, "2DA");
            } else if ("ARE ".equals(sig)) {
              res = getResource(entry, "ARE");
            } else if ("BAM ".equals(sig) || "BAMC".equals(sig)) {
              res = getResource(entry, "BAM");
            } else if ("CHR ".equals(sig)) {
              res = getResource(entry, "CHR");
            } else if ("CHUI".equals(sig)) {
              res = getResource(entry, "CHU");
            } else if ("CRE ".equals(sig)) {
              res = getResource(entry, "CRE");
            } else if ("DLG ".equals(sig)) {
              res = getResource(entry, "DLG");
            } else if ("EFF ".equals(sig)) {
              res = getResource(entry, "EFF");
            } else if ("GAME".equals(sig)) {
              res = getResource(entry, "GAM");
            } else if ("IDS ".equalsIgnoreCase(sig)) {
              res = getResource(entry, "IDS");
            } else if ("ITM ".equals(sig)) {
              res = getResource(entry, "ITM");
            } else if ("MOS ".equals(sig) || "MOSC".equals(sig)) {
              res = getResource(entry, "MOS");
            } else if ("PLT ".equals(sig)) {
              res = getResource(entry, "PLT");
            } else if ("PRO ".equals(sig)) {
              res = getResource(entry, "PRO");
            } else if ("SAV ".equals(sig)) {
              res = getResource(entry, "SAV");
            } else if ("SPL ".equals(sig)) {
              res = getResource(entry, "SPL");
            } else if ("STOR".equals(sig)) {
              res = getResource(entry, "STO");
            } else if ("TIS ".equals(sig)) {
              res = getResource(entry, "TIS");
            } else if ("VEF ".equals(sig)) {
              res = getResource(entry, "VEF");
            } else if ("VVC ".equals(sig)) {
              res = getResource(entry, "VVC");
            } else if ("WAVC".equals(sig) || "RIFF".equals(sig) || "OggS".equals(sig)) {
              res = getResource(entry, "WAV");
            } else if ("WED ".equals(sig)) {
              res = getResource(entry, "WED");
            } else if ("WFX ".equals(sig)) {
              res = getResource(entry, "WFX");
            } else if ("WMAP".equals(sig)) {
              res = getResource(entry, "WMP");
            } else {
              if ((Arrays.equals(new byte[]{0x53, 0x43, 0x0a}, Arrays.copyOfRange(data, 0, 3)) ||  // == "SC\n"
                   Arrays.equals(new byte[]{0x53, 0x43, 0x0d, 0x0a}, Arrays.copyOfRange(data, 0, 4)))) { // == "SC\r\n"
                res = getResource(entry, "BCS");
              } else if (data.length > 6 && "BM".equals(new String(data, 0, 2)) &&
                         DynamicArray.getInt(data, 2) == info[0]) {
                res = getResource(entry, "BMP");
              } else if (data.length > 18 && "Interplay MVE File".equals(new String(data, 0, 18))) {
                res = getResource(entry, "MVE");
              } else if (Arrays.equals(new byte[]{(byte)0x1a, (byte)0x45, (byte)0xdf, (byte)0xa3},
                                       Arrays.copyOfRange(data, 0, 4))) {
                res = getResource(entry, "WBM");
              } else if (data.length > 6 && data[3] == 0 && data[4] == 0x78) {  // just guessing...
                res = getResource(entry, "PVRZ");
              } else if (data.length > 4 && data[0] == 0x89 &&
                         data[1] == 0x50 && data[2] == 0x4e && data[3] == 0x47) {
                res = getResource(entry, "PNG");
              } else if (DynamicArray.getInt(data, 0) == 0x00000100) {  // wild guess...
                res = getResource(entry, "TTF");
              }
            }
          }
        } else {
          throw new Exception(entry.getResourceName() + ": Unable to determine resource type");
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return res;
  }

  public static void exportResource(ResourceEntry entry, Component parent)
  {
    if (getInstance() != null) {
      try {
        getInstance().exportResourceInternal(entry, parent, null);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void exportResource(ResourceEntry entry, ByteBuffer buffer, String filename, Component parent)
  {
    if (getInstance() != null) {
      try {
        getInstance().exportResourceInternal(entry, buffer, filename, parent, null);
      } catch (Exception e) {
        e.printStackTrace();
        JOptionPane.showMessageDialog(parent, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  /** Exports "entry" to "output" without any user interaction. */
  public static void exportResource(ResourceEntry entry, Path output) throws Exception
  {
    if (getInstance() != null && output != null) {
      getInstance().exportResourceInternal(entry, null, output);
    }
  }

  /**
   * Returns whether the specified resource exists.
   * @param resourceName The resource filename.
   * @return {@code true} if the resource exists in BIFF archives or override folders,
   *         {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName)
  {
    return (getResourceEntry(resourceName, false) != null);
  }

  /**
   * Returns whether the specified resource exists.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @return {@code true} if the resource exists in BIFF archives or override folders,
   *         {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName, boolean searchExtraDirs)
  {
    return (getResourceEntry(resourceName, searchExtraDirs) != null);
  }

  /**
   * Returns whether the specified resource exists.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @param extraDirs       A list of File entries pointing to additional folders to search, not
   *                        covered by the default override folder list (e.g. "install:/music").
   * @return {@code true} if the resource exists in BIFF archives or override folders,
   *         {@code false} otherwise.
   */
  public static boolean resourceExists(String resourceName, boolean searchExtraDirs, List<Path> extraDirs)
  {
    return (getResourceEntry(resourceName, searchExtraDirs, extraDirs) != null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   * @param resourceName The resource filename.
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not
   *         available.
   */
  public static ResourceEntry getResourceEntry(String resourceName)
  {
    return getResourceEntry(resourceName, false, null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not
   *         available.
   */
  public static ResourceEntry getResourceEntry(String resourceName, boolean searchExtraDirs)
  {
    return getResourceEntry(resourceName, searchExtraDirs, null);
  }

  /**
   * Returns a ResourceEntry instance of the given resource name.
   * @param resourceName The resource filename.
   * @param searchExtraDirs If {@code true}, all supported override folders will be searched.
   *                        If {@code false}, only the default 'override' folders will be searched.
   * @param extraDirs       A list of File entries pointing to additional folders to search, not
   *                        covered by the default override folder list (e.g. "install:/music").
   * @return A ResourceEntry instance of the given resource filename, or {@code null} if not
   *         available.
   */
  public static ResourceEntry getResourceEntry(String resourceName, boolean searchExtraDirs, List<Path> extraDirs)
  {
    if (getInstance() != null) {
      ResourceEntry entry = getInstance().treeModel.getResourceEntry(resourceName);

      // checking default override folder list
      if (searchExtraDirs && (entry == null)) {
        List<Path> extraFolders = Profile.getOverrideFolders(false);
        if (extraFolders != null) {
          Path file = FileManager.query(extraFolders, resourceName);
          if (file != null && Files.isRegularFile(file)) {
            entry = new FileResourceEntry(file);
          }
        }
      }

      // checking custom folder list
      if (extraDirs != null && (entry == null)) {
        Path file = FileManager.query(extraDirs, resourceName);
        if (file != null && Files.isRegularFile(file)) {
          entry = new FileResourceEntry(file);
        }
      }

      return entry;
    } else {
      return null;
    }
  }

  public static ResourceTreeModel getResources()
  {
    if (getInstance() != null) {
      return getInstance().treeModel;
    } else {
      return null;
    }
  }

  public static List<ResourceEntry> getResources(String type)
  {
    return getResources(type, Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS));
  }

  public static List<ResourceEntry> getResources(String type, List<Path> extraDirs)
  {
    if (getInstance() != null) {
      return getInstance().getResourcesInternal(type, extraDirs);
    } else {
      return null;
    }
  }

  public static void loadResources() throws Exception
  {
    if (getInstance() != null) {
      getInstance().loadResourcesInternal();
    }
  }

  public static void saveCopyOfResource(ResourceEntry entry)
  {
    if (getInstance() != null) {
      getInstance().saveCopyOfResourceInternal(entry);
    }
  }

  public static boolean saveResource(Resource resource, Component parent)
  {
    if (getInstance() != null) {
      return getInstance().saveResourceInternal(resource, parent);
    } else {
      return false;
    }
  }

  /**
   * Returns a list of available game language directories for the current game in Enhanced Edition games.
   * Returns an empty list otherwise.
   */
  public static List<Path> getAvailableGameLanguages()
  {
    List<Path> list = new ArrayList<>();

    if (Profile.isEnhancedEdition()) {
      Path langPath = Profile.getProperty(Profile.Key.GET_GAME_LANG_FOLDER_BASE);
      if (langPath != null && Files.isDirectory(langPath)) {
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(langPath,
            (Path entry) -> {
              return Files.isDirectory(entry) &&
                     entry.getFileName().toString().matches("[a-z]{2}_[A-Z]{2}") &&
                     Files.isRegularFile(FileManager.query(entry, Profile.getProperty(Profile.Key.GET_GLOBAL_DIALOG_NAME)));
              })) {
          dstream.forEach((path) -> list.add(path));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return list;
  }

  /**  Return the game language specified in the given baldur.ini if found. Returns {@code en_US} otherwise. */
  public static String autodetectGameLanguage(Path iniFile)
  {
    final String langDefault = "en_US";   // using default language, if no language entry found
    if (Profile.isEnhancedEdition() && iniFile != null && Files.isRegularFile(iniFile)) {
      // Attempt to autodetect game language
      try (BufferedReader br = Files.newBufferedReader(iniFile, Misc.CHARSET_ASCII)) {
        String line;
        while ((line = br.readLine()) != null) {
          if (line.contains("'Language'")) {
            String[] entries = line.split(",");
            if (entries.length == 3) {
              // Note: replace operation is compatible with both baldur.ini and baldur.lua
              String lang = entries[2].replaceFirst("^[^']*'", "");
              lang = lang.replaceFirst("'.*$", "");
              if (lang.matches("[A-Za-z]{2}_[A-Za-z]{2}")) {
                Path path = FileManager.query(Profile.getGameRoot(), "lang", lang);
                if (path != null && Files.isDirectory(path)) {
                  try {
                    // try to fetch the actual path name to ensure correct case
                    return path.toRealPath().getFileName().toString();
                  } catch (Exception e) {
                    return lang;
                  }
                }
              }
            }
          }
        }
      } catch (IOException e) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error parsing " + iniFile.getFileName() +
                                      ". Using language defaults.", "Error", JOptionPane.ERROR_MESSAGE);
      }
    }
    return langDefault;
  }

  /** Attempts to find the home folder of an Enhanced Edition game. */
  static Path getHomeRoot()
  {
    if (Profile.hasProperty(Profile.Key.GET_GAME_HOME_FOLDER_NAME)) {
      final Path EE_DOC_ROOT = FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
      final String EE_DIR = Profile.getProperty(Profile.Key.GET_GAME_HOME_FOLDER_NAME);
      Path userPath = FileManager.query(EE_DOC_ROOT, EE_DIR);
      if (userPath != null && Files.isDirectory(userPath)) {
        return userPath;
      } else {
        // fallback solution
        String userPrefix = System.getProperty("user.home");
        userPath = null;
        String osName = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if (osName.contains("windows")) {
          try {
            Process p = Runtime.getRuntime().exec("reg query \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v personal");
            p.waitFor();
            InputStream in = p.getInputStream();
            byte[] b = new byte[in.available()];
            in.read(b);
            in.close();
            String[] splitted = new String(b).split("\\s\\s+");
            userPrefix = splitted[splitted.length-1];
            userPath = FileManager.resolve(userPrefix, EE_DIR);
          } catch (Throwable t) {
            return null;
          }
        } else if (osName.contains("mac") || osName.contains("darwin")) {
          userPath = FileManager.resolve(FileManager.resolve(userPrefix, "Documents", EE_DIR));
        } else if (osName.contains("nix") || osName.contains("nux") || osName.contains("bsd")) {
          userPath = FileManager.resolve(FileManager.resolve(userPrefix, ".local", "share", EE_DIR));
        }
        if (userPath != null && Files.isDirectory(userPath)) {
          return userPath;
        }
      }
    }
    return null;
  }

  /** Attempts to find folders containing BIFF archives. */
  static List<Path> getBIFFDirs()
  {
    List<Path> dirList = new ArrayList<>();

    if (Profile.isEnhancedEdition()) {
      // adding supported base biff folders
      Path langRoot = Profile.getLanguageRoot();
      if (langRoot != null) {
        dirList.add(langRoot);
      }
      List<Path> dlcList = Profile.getProperty(Profile.Key.GET_GAME_DLC_FOLDERS_AVAILABLE);
      if (dlcList != null) {
        dlcList.forEach((path) -> dirList.add(path));
      }
      dirList.add(Profile.getGameRoot());
    } else {
      // fetching the CD folders in a game installation
      Path iniFile = Profile.getProperty(Profile.Key.GET_GAME_INI_FILE);
      List<Path> rootFolders = Profile.getRootFolders();
      if (iniFile != null && Files.isRegularFile(iniFile)) {
        try (BufferedReader br = Files.newBufferedReader(iniFile)) {
          String line;
          while ((line = br.readLine()) != null) {
            if (line.contains(":=")) {
              String[] items = line.split(":=");
              if (items.length > 1) {
                int p = items[1].indexOf(';');
                if (p >= 0) {
                  line = items[1].substring(0, p).trim();
                } else {
                  line = items[1].trim();
                }
                if (line.endsWith(":")) {
                  line = line.replace(':', '/');
                }
                // Try to handle Mac relative paths
                Path path;
                if (line.charAt(0) == '/') {
                  path = FileManager.query(rootFolders, line);
                } else {
                  path = FileManager.resolve(line);
                }
                if (Files.isDirectory(path)) {
                  dirList.add(path);
                }
              }
            }
          }
        } catch (Exception e) {
          e.printStackTrace();
          dirList.clear();
        }
      }

      if (dirList.isEmpty()) {
        // Don't panic if an .ini-file cannot be found or contains errors
        Path path;
        for (int i = 1; i < 7; i++) {
          path = FileManager.query(rootFolders, "CD" + i);
          if (Files.isDirectory(path)) {
            dirList.add(path);
          }
        }
        // used in certain games
        path = FileManager.query(rootFolders, "CDALL");
        if (Files.isDirectory(path)) {
          dirList.add(path);
        }
      }
    }
    return dirList;
  }

  // Returns the currently used language of an Enhanced Edition game.
  static String fetchGameLanguage(Path iniFile)
  {
    final String langDefault = "en_US";   // using default language, if no language entry found

    if (Profile.isEnhancedEdition() && iniFile != null && Files.isRegularFile(iniFile)) {
      String lang = BrowserMenuBar.getInstance().getSelectedGameLanguage();

      if (lang == null || lang.isEmpty()) {
        return autodetectGameLanguage(iniFile);
      } else {
        // Using user-defined language
        if (lang.matches("[A-Za-z]{2}_[A-Za-z]{2}")) {
          Path path = FileManager.query(Profile.getGameRoot(), "lang", lang);
          if (path != null && Files.isDirectory(path)) {
            String retVal;
            try {
              // try to fetch the actual path name to ensure correct case
              retVal = path.toRealPath().getFileName().toString();
            } catch (Exception e) {
              retVal = lang;
            }
            return retVal;
          }
        }
      }
    }

    // falling back to default language
    return langDefault;
  }

  /** Used internally by the Profile class to open and initialize a new game. */
  static void openGame(Path keyFile)
  {
    closeGame();
    new ResourceFactory(keyFile);
  }

  /** Closes the current game configuration. */
  private static void closeGame()
  {
    if (instance != null) {
      instance.close();
      instance = null;
    }
  }

  private static ResourceFactory getInstance()
  {
    return instance;
  }


  private ResourceFactory(Path keyFile)
  {
    instance = this;
    try {
      // initializing primary key file
      this.keyfile = new Keyfile(keyFile);

      // adding DLC key files if available
      List<Path> keyList = Profile.getProperty(Profile.Key.GET_GAME_DLC_KEYS_AVAILABLE);
      if (keyList != null) {
        for (final Path key: keyList) {
          this.keyfile.addKeyfile(key);
        }
      }

      loadResourcesInternal();
    } catch (Exception e) {
      JOptionPane.showMessageDialog(null, "No Infinity Engine game found", "Error",
                                    JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  // Cleans up resources
  private void close()
  {
    // nothing to do yet...
  }

  private void exportResourceInternal(ResourceEntry entry, Component parent, Path output) throws Exception
  {
    try {
      ByteBuffer buffer = entry.getResourceBuffer();
      final String ext = entry.getExtension();
      if (ext.equalsIgnoreCase("IDS") || ext.equalsIgnoreCase("2DA") ||
          ext.equalsIgnoreCase("BIO") || ext.equalsIgnoreCase("RES") ||
          ext.equalsIgnoreCase("INI") || ext.equalsIgnoreCase("SET") ||
          ext.equalsIgnoreCase("TXT") ||
          (Profile.getEngine() == Profile.Engine.IWD2 && ext.equalsIgnoreCase("SRC")) ||
          (Profile.isEnhancedEdition() && (ext.equalsIgnoreCase("GUI") ||
                                           ext.equalsIgnoreCase("SQL") ||
                                           ext.equalsIgnoreCase("GLSL")))) {
        if (buffer.getShort(0) == -1) {
          exportResourceInternal(entry, Decryptor.decrypt(buffer, 2), entry.toString(), parent, output);
        } else {
          buffer.position(0);
          exportResourceInternal(entry, buffer, entry.toString(), parent, output);
        }
      } else {
        exportResourceInternal(entry, buffer, entry.toString(), parent, output);
      }
    } catch (Exception e) {
      throw new Exception("Can't read " + entry);
    }
  }

  private void exportResourceInternal(ResourceEntry entry, ByteBuffer buffer, String fileName,
                                      Component parent, Path output) throws Exception
  {
    // ask for output file path if needed
    boolean interactive = (output == null);
    if (interactive) {
      if (fc == null) {
        fc = new JFileChooser(Profile.getGameRoot().toFile());
        fc.setDialogTitle("Export resource");
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      }
      fc.setSelectedFile(new File(fc.getCurrentDirectory(), fileName));
      if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) {
        output = fc.getSelectedFile().toPath();
        if (Files.exists(output)) {
          final String options[] = {"Overwrite", "Cancel"};
          if (JOptionPane.showOptionDialog(parent, output + " exists. Overwrite?", "Export resource",
                                           JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                                           null, options, options[0]) != 0) {
            return;
          }
        }
      }
    }

    // exporting resource
    if (output != null) {
      try {
        try (OutputStream os = StreamUtils.getOutputStream(output, true)) {
          StreamUtils.writeBytes(os, buffer);
        }
        if (interactive) {
          JOptionPane.showMessageDialog(parent, "File exported to " + output, "Export complete",
                                        JOptionPane.INFORMATION_MESSAGE);
        }
      } catch (IOException e) {
        throw new Exception("Error while exporting " + entry);
      }
    }
  }

  private void loadResourcesInternal() throws Exception
  {
    treeModel = new ResourceTreeModel();

    // Get resources from keyfile
    NearInfinity.advanceProgress("Loading BIFF resources...");
    keyfile.populateResourceTree(treeModel);
    StringResource.init(Profile.getProperty(Profile.Key.GET_GAME_DIALOG_FILE));

    // Add resources from extra folders
    NearInfinity.advanceProgress("Loading extra resources...");
    List<Path> extraPaths = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    extraPaths.forEach((path) -> {
      if (Files.isDirectory(path)) {
        treeModel.addDirectory((ResourceTreeFolder)treeModel.getRoot(), path, false);
      }
    });

    NearInfinity.advanceProgress("Loading override resources...");
    final boolean overrideInOverride = (BrowserMenuBar.getInstance() != null &&
                                        BrowserMenuBar.getInstance().getOverrideMode() == BrowserMenuBar.OVERRIDE_IN_OVERRIDE);
    String overrideFolder = Profile.getOverrideFolderName();
    List<Path> overridePaths = Profile.getOverrideFolders(false);
    for (final Path overridePath: overridePaths) {
      if (Files.isDirectory(overridePath)) {
        try (DirectoryStream<Path> dstream = Files.newDirectoryStream(overridePath)) {
          dstream.forEach((path) -> {
            if (Files.isRegularFile(path)) {
              ResourceEntry entry = getResourceEntry(path.getFileName().toString());
              if (entry == null) {
                FileResourceEntry fileEntry = new FileResourceEntry(path, true);
                treeModel.addResourceEntry(fileEntry, fileEntry.getTreeFolder(), true);
              } else if (entry instanceof BIFFResourceEntry) {
                ((BIFFResourceEntry)entry).setOverride(true);
                if (overrideInOverride) {
                  treeModel.removeResourceEntry(entry, entry.getExtension());
                  treeModel.addResourceEntry(new FileResourceEntry(path, true), overrideFolder, true);
                }
              }
            }
          });
        }
      }
    }
    treeModel.sort();
  }

  private List<ResourceEntry> getResourcesInternal(String type, List<Path> extraDirs)
  {
    List<ResourceEntry> list;
    ResourceTreeFolder bifNode = treeModel.getFolder(type);
    if (bifNode != null) {
      list = new ArrayList<ResourceEntry>(bifNode.getResourceEntries());
    } else {
      list = new ArrayList<ResourceEntry>();
    }
    int initsize = list.size();

    // include extra folders
    if (extraDirs == null) {
      extraDirs = Profile.getProperty(Profile.Key.GET_GAME_EXTRA_FOLDERS);
    }
    extraDirs.forEach((path) -> {
      ResourceTreeFolder extraNode = treeModel.getFolder(path.getFileName().toString());
      if (extraNode != null) {
        list.addAll(extraNode.getResourceEntries(type));
      }
    });

    // include override folders
    if (BrowserMenuBar.getInstance() != null && !BrowserMenuBar.getInstance().ignoreOverrides()) {
      ResourceTreeFolder overrideNode = treeModel.getFolder(Profile.getOverrideFolderName());
      if (overrideNode != null) {
        list.addAll(overrideNode.getResourceEntries(type));
      }
    }

    if (list.size() > initsize) {
      Collections.sort(list);
    }
    return list;
  }

  private void saveCopyOfResourceInternal(ResourceEntry entry)
  {
    String fileName;
    do {
      fileName = JOptionPane.showInputDialog(NearInfinity.getInstance(), "Enter new filename",
                                             "Add copy of " + entry.toString(),
                                             JOptionPane.QUESTION_MESSAGE);
      if (fileName != null) {
        if (fileName.indexOf(".") == -1) {
          fileName += '.' + entry.getExtension();
        }
        if (fileName.lastIndexOf('.') > 8) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(),
                                        "Filenames can only be up to 8 characters long (not including the file extension).",
                                        "Error", JOptionPane.ERROR_MESSAGE);
          fileName = null;
        }
        if (resourceExists(fileName)) {
          JOptionPane.showMessageDialog(NearInfinity.getInstance(), "File already exists!",
                                        "Error", JOptionPane.ERROR_MESSAGE);
          fileName = null;
        }
      } else {
        return;
      }
    } while (fileName == null);

    // creating override folder in game directory if it doesn't exist

    Path outPath = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName().toLowerCase(Locale.ENGLISH));
    if (!Files.isDirectory(outPath)) {
      try {
        Files.createDirectory(outPath);
      } catch (IOException e) {
        JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Could not create " + outPath + ".",
                                      "Error", JOptionPane.ERROR_MESSAGE);
        e.printStackTrace();
        return;
      }
    }

    Path outFile = outPath.resolve(fileName);
    if (entry.getExtension().equalsIgnoreCase("bs")) {
      outFile = FileManager.query(Profile.getGameRoot(), "Scripts", fileName);
    }

    if (Files.exists(outFile)) {
      String options[] = {"Overwrite", "Cancel"};
      if (JOptionPane.showOptionDialog(NearInfinity.getInstance(), outFile + " exists. Overwrite?",
                                       "Save resource", JOptionPane.YES_NO_OPTION,
                                       JOptionPane.WARNING_MESSAGE, null, options, options[0]) != 0)
        return;
    }
    try {
      ByteBuffer bb = entry.getResourceBuffer();
      try (OutputStream os = StreamUtils.getOutputStream(outFile, true)) {
        WritableByteChannel wbc = Channels.newChannel(os);
        wbc.write(bb);
      }
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), entry.toString() + " copied to " + outFile,
                                    "Copy complete", JOptionPane.INFORMATION_MESSAGE);
      ResourceEntry newEntry = new FileResourceEntry(outFile, !entry.getExtension().equalsIgnoreCase("bs"));
      treeModel.addResourceEntry(newEntry, newEntry.getTreeFolder(), true);
      treeModel.sort();
      NearInfinity.getInstance().showResourceEntry(newEntry);
    } catch (Exception e) {
      JOptionPane.showMessageDialog(NearInfinity.getInstance(), "Error while copying " + entry,
                                    "Error", JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
    }
  }

  private boolean saveResourceInternal(Resource resource, Component parent)
  {
    if (!(resource instanceof Writeable)) {
      JOptionPane.showMessageDialog(parent, "Resource not savable", "Error", JOptionPane.ERROR_MESSAGE);
      return false;
    }
    ResourceEntry entry = resource.getResourceEntry();
    if (entry == null) {
      return false;
    }
    Path outPath;
    if (entry instanceof BIFFResourceEntry) {
      Path overridePath = FileManager.query(Profile.getGameRoot(), Profile.getOverrideFolderName());
      if (!Files.isDirectory(overridePath)) {
        try {
          Files.createDirectory(overridePath);
        } catch (IOException e) {
          JOptionPane.showMessageDialog(parent, "Unable to create override folder.",
                                        "Error", JOptionPane.ERROR_MESSAGE);
          e.printStackTrace();
          return false;
        }
      }
      outPath = FileManager.query(overridePath, entry.toString());
      ((BIFFResourceEntry)entry).setOverride(true);
    } else {
      outPath = entry.getActualPath();
      // extra step for saving resources from a read-only medium (such as DLCs)
      if (!FileManager.isDefaultFileSystem(outPath)) {
        outPath = Profile.getGameRoot().resolve(outPath.subpath(0, outPath.getNameCount()).toString());
        if (outPath != null && !Files.exists(outPath.getParent())) {
          try {
            Files.createDirectories(outPath.getParent());
          } catch (IOException e) {
            JOptionPane.showMessageDialog(parent, "Unable to create folder: " + outPath.getParent(),
                                          "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            return false;
          }
        }
      }
    }
    if (Files.exists(outPath)) {
      outPath = outPath.toAbsolutePath();
      String options[] = {"Overwrite", "Cancel"};
      if (JOptionPane.showOptionDialog(parent, outPath + " exists. Overwrite?", "Save resource",
                                       JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE,
                                       null, options, options[0]) == 0) {
        if (BrowserMenuBar.getInstance().backupOnSave()) {
          try {
            Path bakPath = outPath.getParent().resolve(outPath.getFileName() + ".bak");
            if (Files.isRegularFile(bakPath)) {
              Files.delete(bakPath);
            }
            if (!Files.exists(bakPath)) {
              Files.move(outPath, bakPath);
            }
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      } else {
        return false;
      }
    }
    try (OutputStream os = StreamUtils.getOutputStream(outPath, true)) {
      ((Writeable)resource).write(os);
    } catch (IOException e) {
      JOptionPane.showMessageDialog(parent, "Error while saving " + resource.getResourceEntry().toString(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
      e.printStackTrace();
      return false;
    }
    JOptionPane.showMessageDialog(parent, "File saved to \"" + outPath.toAbsolutePath() + '\"',
                                  "Save complete", JOptionPane.INFORMATION_MESSAGE);
    if (resource.getResourceEntry().getExtension().equals("IDS")) {
      IdsMapCache.cacheInvalid(resource.getResourceEntry());
      IdsBrowser idsbrowser = (IdsBrowser)ChildFrame.getFirstFrame(IdsBrowser.class);
      if (idsbrowser != null) {
        idsbrowser.refreshList();
      }
      Compiler.restartCompiler();
    } else if (resource.getResourceEntry().toString().equalsIgnoreCase(Song2daBitmap.getTableName())) {
      Song2daBitmap.resetSonglist();
    } else if (resource.getResourceEntry().toString().equalsIgnoreCase(Summon2daBitmap.getTableName())) {
      Summon2daBitmap.resetSummonTable();
    } else if (resource.getResourceEntry().toString().equalsIgnoreCase(PriTypeBitmap.getTableName())) {
      PriTypeBitmap.resetTypeTable();
    } else if (resource.getResourceEntry().toString().equalsIgnoreCase(SecTypeBitmap.getTableName())) {
      SecTypeBitmap.resetTypeTable();
    }
    return true;
  }
}
